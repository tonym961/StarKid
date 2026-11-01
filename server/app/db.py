"""Accesso a SQLite e schema del database.

Perche sqlite3 della libreria standard e non un database vero: la macchina ha
954 MB di RAM con 414 MB di swap gia in uso. PostgreSQL ne vorrebbe 150-200
residenti solo per restare pronto, e qui i dati sono un account, una manciata
di tablet e dieci revisioni per tablet. Un file su disco con WAL e la scelta
giusta, non un compromesso.

Tutto e sincrono di proposito. Gli endpoint sono `def`, quindi FastAPI li
esegue nel threadpool: ogni thread tiene la sua connessione, SQLite serializza
le scritture da solo e non serve ne un driver asincrono ne un pool.
"""

from __future__ import annotations

import os
import sqlite3
import threading
import time
from contextlib import contextmanager
from pathlib import Path
from typing import Any, Iterator, Sequence

PERCORSO_PREDEFINITO = "/var/lib/starkid/starkid.db"

# Cache per connessione: mezzo MB. La cache si paga PER CONNESSIONE, e di
# connessioni ce n'e una per thread del pool, aperta per sempre (vedi
# connessione()). A 2 MB e otto thread sono 16 MB di sola cache dentro un
# cgroup da 192 MB, e il pool prima era da quaranta. Su questa macchina il
# vincolo e la RAM, non la velocita di una query su sei tabelle piccole che
# il sistema operativo si tiene comunque in cache.
CACHE_KB = 500


def percorso() -> Path:
    """Dove sta il file. Si rilegge ogni volta, cosi i test possono spostarlo."""
    return Path(os.environ.get("STARKID_DB", PERCORSO_PREDEFINITO))


def adesso() -> int:
    """Secondi unix UTC. Tutti gli istanti nel database sono interi."""
    return int(time.time())


_locale = threading.local()
_registro: set[sqlite3.Connection] = set()
_lucchetto = threading.Lock()
_generazione = 0


def _apri(p: Path) -> sqlite3.Connection:
    if str(p) != ":memory:":
        p.parent.mkdir(parents=True, exist_ok=True)
    # check_same_thread=False serve solo per poter chiudere le connessioni da
    # un altro thread allo spegnimento (e fra un test e l'altro): ogni
    # connessione resta comunque usata da un thread solo, perche vive in una
    # threading.local.
    conn = sqlite3.connect(
        str(p), timeout=5.0, isolation_level=None, check_same_thread=False
    )
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode = WAL")
    conn.execute("PRAGMA synchronous = NORMAL")
    conn.execute("PRAGMA foreign_keys = ON")
    conn.execute("PRAGMA busy_timeout = 5000")
    conn.execute("PRAGMA cache_size = -%d" % CACHE_KB)
    # Niente mmap: su una macchina che swappa, le pagine mappate sono memoria
    # che sembra gratis e non lo e.
    conn.execute("PRAGMA mmap_size = 0")
    return conn


def connessione() -> sqlite3.Connection:
    """La connessione di questo thread, aperta al bisogno."""
    p = percorso()
    conn: sqlite3.Connection | None = getattr(_locale, "conn", None)
    if (
        conn is not None
        and getattr(_locale, "percorso", None) == p
        and getattr(_locale, "generazione", None) == _generazione
    ):
        return conn
    if conn is not None:
        _scorda(conn)
    conn = _apri(p)
    _locale.conn = conn
    _locale.percorso = p
    _locale.generazione = _generazione
    with _lucchetto:
        _registro.add(conn)
    return conn


def _scorda(conn: sqlite3.Connection) -> None:
    with _lucchetto:
        _registro.discard(conn)
    try:
        conn.close()
    except sqlite3.Error:
        pass


def chiudi_tutte() -> None:
    """Chiude ogni connessione aperta: allo spegnimento e fra un test e l'altro.

    Alza la generazione, cosi un thread che tiene ancora in mano una
    connessione chiusa se ne accorge e la riapre invece di lavorarci sopra.
    """
    global _generazione
    with _lucchetto:
        aperte = list(_registro)
        _registro.clear()
        _generazione += 1
    for conn in aperte:
        try:
            conn.close()
        except sqlite3.Error:
            pass


@contextmanager
def scrittura() -> Iterator[sqlite3.Connection]:
    """Transazione in scrittura.

    BEGIN IMMEDIATE e non BEGIN: chi legge un valore per scriverne uno
    derivato (il prossimo numero di revisione) deve prendere il lucchetto
    subito, altrimenti due richieste nel threadpool leggono la stessa
    revisione e una delle due muore al COMMIT.
    """
    conn = connessione()
    conn.execute("BEGIN IMMEDIATE")
    try:
        yield conn
    except BaseException:
        try:
            conn.execute("ROLLBACK")
        except sqlite3.Error:
            pass
        raise
    else:
        conn.execute("COMMIT")


def leggi_uno(sql: str, parametri: Sequence[Any] = ()) -> sqlite3.Row | None:
    return connessione().execute(sql, parametri).fetchone()


def leggi_tutti(sql: str, parametri: Sequence[Any] = ()) -> list[sqlite3.Row]:
    return connessione().execute(sql, parametri).fetchall()


def esegui(sql: str, parametri: Sequence[Any] = ()) -> sqlite3.Cursor:
    """Scrittura singola, in autocommit. Per piu scritture insieme: scrittura()."""
    return connessione().execute(sql, parametri)


# ---------------------------------------------------------------------------
# Migrazioni
#
# Semplici di proposito: una lista di passi numerati e PRAGMA user_version come
# segnaposto. Niente Alembic, che sarebbe una dipendenza e una tabella in piu
# per un database di sei tabelle; e niente CREATE TABLE IF NOT EXISTS sparsi
# nel codice, che e il modo classico di ritrovarsi due macchine con lo schema
# diverso. Per cambiare schema si aggiunge un passo in fondo: un passo gia
# pubblicato non si tocca mai.
# ---------------------------------------------------------------------------

MIGRAZIONI: list[tuple[int, tuple[str, ...]]] = [
    (
        1,
        (
            # Un solo account, per decisione del proprietario: il CHECK (id = 1)
            # rende l'unicita una regola del database e non una buona
            # intenzione del codice. Si crea da riga di comando (app/cli.py).
            """
            CREATE TABLE utente (
                id            INTEGER PRIMARY KEY CHECK (id = 1),
                utente        TEXT    NOT NULL UNIQUE,
                password      TEXT    NOT NULL,
                creato_il     INTEGER NOT NULL,
                aggiornato_il INTEGER NOT NULL
            )
            """,
            # Il token di sessione sta qui solo come hash: chi legge il file del
            # database non puo rubare una sessione aperta. Il token CSRF invece
            # e in chiaro, perche da solo non autentica niente.
            """
            CREATE TABLE sessione (
                hash_token TEXT    PRIMARY KEY,
                csrf       TEXT    NOT NULL,
                creato_il  INTEGER NOT NULL,
                visto_il   INTEGER NOT NULL,
                scade_il   INTEGER NOT NULL
            )
            """,
            # Tentativi falliti, per l'attesa crescente. Si tiene l'istante e la
            # chiave (nome utente oppure indirizzo), non il valore provato.
            """
            CREATE TABLE tentativo (
                id      INTEGER PRIMARY KEY AUTOINCREMENT,
                chiave  TEXT    NOT NULL,
                istante INTEGER NOT NULL
            )
            """,
            "CREATE INDEX idx_tentativo ON tentativo (chiave, istante)",
            # Nessun campo identifica il bambino: 'nome' e il nome che il
            # genitore da al tablet ("quello di camera"), non al figlio. Anche
            # i token dei dispositivi stanno solo come hash.
            """
            CREATE TABLE tablet (
                id                 TEXT    PRIMARY KEY,
                nome               TEXT    NOT NULL,
                creato_il          INTEGER NOT NULL,
                hash_arruolamento  TEXT    UNIQUE,
                arruolamento_scade INTEGER,
                arruolato_il       INTEGER,
                hash_dispositivo   TEXT    UNIQUE,
                revisione          INTEGER NOT NULL DEFAULT 1,
                visto_il           INTEGER,
                consegnata         INTEGER,
                ack_revisione      INTEGER,
                ack_esito          TEXT,
                ack_campi          TEXT,
                ack_il             INTEGER,
                salute             TEXT,
                salute_il          INTEGER
            )
            """,
            """
            CREATE TABLE revisione (
                id_tablet TEXT    NOT NULL REFERENCES tablet (id) ON DELETE CASCADE,
                revisione INTEGER NOT NULL,
                documento TEXT    NOT NULL,
                creato_il INTEGER NOT NULL,
                origine   TEXT    NOT NULL,
                PRIMARY KEY (id_tablet, revisione)
            )
            """,
        ),
    ),
    (
        2,
        (
            # ---------------------------------------------------------------
            # Da un account solo a piu utenti, con ruoli e sottoutenti.
            #
            # L'ORDINE QUI SOTTO NON E CASUALE. Tutti i passi girano dentro un
            # solo BEGIN IMMEDIATE (vedi migra()), con PRAGMA foreign_keys
            # acceso e non spegnibile: dentro una transazione quel PRAGMA non
            # fa niente. Da cui tre regole che valgono per sempre:
            #
            #  1. "utente" e "sessione" si possono RICOSTRUIRE, perche nessuna
            #     tabella le riferisce. L'unica REFERENCES di tutto lo schema
            #     e revisione.id_tablet -> tablet(id).
            #  2. Su "tablet" si fa SOLO ADD COLUMN. Un DROP TABLE tablet
            #     scatena l'ON DELETE CASCADE di revisione e porta via tutto
            #     lo storico di configurazione, in silenzio; e un
            #     ALTER TABLE tablet RENAME, con le chiavi accese, riscrive la
            #     clausola REFERENCES di revisione. Tutti e due gli ordini
            #     sono rotti.
            #  3. "utente" si ricostruisce PRIMA che "sessione" la riferisca,
            #     se no il DROP della vecchia e bloccato.
            # ---------------------------------------------------------------

            # Le sessioni aperte vanno via per prime. Una sessione senza
            # proprietario non si puo autorizzare, e inventare a chi
            # appartiene sarebbe peggio: si cancellano, e ogni genitore
            # rientra una volta. Prima della ricostruzione, cosi la tabella
            # nuova nasce vuota e id_utente puo essere NOT NULL.
            "DELETE FROM sessione",

            # --- utente: via il CHECK (id = 1), dentro ruolo e parentela ----
            """
            CREATE TABLE utente_nuova (
                id            INTEGER PRIMARY KEY AUTOINCREMENT,
                utente        TEXT    NOT NULL UNIQUE COLLATE NOCASE,
                password      TEXT    NOT NULL,
                creato_il     INTEGER NOT NULL,
                aggiornato_il INTEGER NOT NULL,
                -- admin: tutto, utenti compresi. genitore: i tablet che gli
                -- sono stati dati, e puo fare sottoutenti sui propri.
                -- osservatore: guarda e basta.
                ruolo         TEXT    NOT NULL DEFAULT 'genitore'
                              CHECK (ruolo IN ('admin', 'genitore', 'osservatore')),
                -- Il sottoutente: di chi e figlio. Cancellando il padre
                -- spariscono anche i suoi, che e quello che ci si aspetta.
                id_padre      INTEGER REFERENCES utente_nuova (id) ON DELETE CASCADE,
                -- Disattivare invece di cancellare: le revisioni restano
                -- firmate da qualcuno che si puo ancora nominare.
                attivo        INTEGER NOT NULL DEFAULT 1 CHECK (attivo IN (0, 1)),
                creato_da     INTEGER REFERENCES utente_nuova (id) ON DELETE SET NULL,
                CHECK (id_padre IS NULL OR id_padre <> id)
            )
            """,
            # COLLATE NOCASE sul nome: "Antonino" e "antonino" sono la stessa
            # persona, e due account che si distinguono per una maiuscola sono
            # un inganno, non una funzione.
            """
            INSERT INTO utente_nuova
                (id, utente, password, creato_il, aggiornato_il, ruolo, id_padre, attivo, creato_da)
            SELECT id, utente, password, creato_il, aggiornato_il, 'admin', NULL, 1, NULL
            FROM utente
            """,
            "DROP TABLE utente",
            "ALTER TABLE utente_nuova RENAME TO utente",

            # --- sessione: adesso sa di chi e -------------------------------
            """
            CREATE TABLE sessione_nuova (
                hash_token TEXT    PRIMARY KEY,
                id_utente  INTEGER NOT NULL REFERENCES utente (id) ON DELETE CASCADE,
                csrf       TEXT    NOT NULL,
                creato_il  INTEGER NOT NULL,
                visto_il   INTEGER NOT NULL,
                scade_il   INTEGER NOT NULL
            )
            """,
            "DROP TABLE sessione",
            "ALTER TABLE sessione_nuova RENAME TO sessione",
            # Serve a chiudere tutte le sessioni di un utente in un colpo:
            # e il gesto che si fa quando si cambia una password o si
            # disattiva qualcuno, ed e quello che deve essere veloce.
            "CREATE INDEX idx_sessione_utente ON sessione (id_utente)",

            # --- tablet: solo ADD COLUMN, vedi la regola 2 ------------------
            # Nullable per forza: ALTER TABLE ADD COLUMN con REFERENCES non
            # ammette un default diverso da NULL. Il riempimento e il passo
            # dopo, nella stessa transazione.
            "ALTER TABLE tablet ADD COLUMN id_utente INTEGER REFERENCES utente (id)",

            # --- chi vede cosa ---------------------------------------------
            # Una tabella di concessioni e non una colonna sola su tablet:
            # "i genitori vedono certi tablet" e molti-a-molti, due genitori
            # guardano lo stesso tablet di casa, e un sottoutente ne vede un
            # sottoinsieme. ON DELETE CASCADE su tutte e due i lati: tolto
            # l'utente sparisce il permesso, non il tablet.
            """
            CREATE TABLE accesso_tablet (
                id_utente INTEGER NOT NULL REFERENCES utente (id) ON DELETE CASCADE,
                id_tablet TEXT    NOT NULL REFERENCES tablet (id) ON DELETE CASCADE,
                creato_il INTEGER NOT NULL,
                PRIMARY KEY (id_utente, id_tablet)
            )
            """,
            "CREATE INDEX idx_accesso_tablet ON accesso_tablet (id_tablet)",

            # --- chi ha scritto una revisione ------------------------------
            # NULL per quelle di prima: non si inventa un autore. E una
            # colonna, NON un valore nuovo di "origine": quel vocabolario si
            # legge da schema/config.schema.json ed e chiuso per progetto.
            "ALTER TABLE revisione ADD COLUMN id_utente INTEGER REFERENCES utente (id)",

            # --- riempimento, nella stessa transazione ----------------------
            # Senza questi due, dopo la migrazione l'admin non vedrebbe piu
            # nessun tablet: esisterebbero senza che nessuno abbia il permesso.
            """
            UPDATE tablet SET id_utente = (SELECT MIN(id) FROM utente)
            WHERE id_utente IS NULL AND EXISTS (SELECT 1 FROM utente)
            """,
            """
            INSERT OR IGNORE INTO accesso_tablet (id_utente, id_tablet, creato_il)
            SELECT (SELECT MIN(id) FROM utente), id, creato_il FROM tablet
            WHERE EXISTS (SELECT 1 FROM utente)
            """,
        ),
    ),
]


def versione() -> int:
    riga = leggi_uno("PRAGMA user_version")
    return int(riga[0]) if riga is not None else 0


def migra() -> int:
    """Porta il database all'ultima versione. Si puo rieseguire a vuoto."""
    corrente = versione()
    for numero, passi in MIGRAZIONI:
        if numero <= corrente:
            continue
        with scrittura() as conn:
            for sql in passi:
                conn.execute(sql)
            # Il numero di versione non si puo passare come parametro.
            conn.execute("PRAGMA user_version = %d" % int(numero))
        corrente = numero
    return corrente


def inizializza() -> int:
    """Apre il file e applica le migrazioni. All'avvio e dalla CLI."""
    return migra()
