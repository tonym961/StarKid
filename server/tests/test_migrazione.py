"""La migrazione dello schema, provata su un database che esiste gia.

PERCHE QUESTO FILE ESISTE. Prima non c'era nessun test dello schema del
database: test_schema.py parla del documento di configurazione, non delle
tabelle. La migrazione sarebbe andata in produzione senza che nessuno
l'avesse mai vista girare su dati veri.

PERCHE LO SCHEMA v1 E COPIATO QUI ALLA LETTERA invece di essere letto da
db.MIGRAZIONI[0]. Un passo pubblicato non si tocca piu - lo dice il commento
sopra MIGRAZIONI - ma un test che legge la lista viva smette di provare
qualcosa il giorno in cui qualcuno la modifica lo stesso: proverebbe che
"migrare dallo schema attuale allo schema attuale funziona", che e vero
sempre. Qui sotto c'e il v1 com'era quando e stato pubblicato, congelato.
Se un giorno questo test fallisce perche qualcuno ha cambiato il passo 1,
il test ha fatto il suo lavoro.
"""

from __future__ import annotations

import sqlite3
from pathlib import Path

import pytest

from app import db

# ---------------------------------------------------------------------------
# Lo schema v1, congelato. Non si aggiorna.
# ---------------------------------------------------------------------------

V1 = (
    """
    CREATE TABLE utente (
        id            INTEGER PRIMARY KEY CHECK (id = 1),
        utente        TEXT    NOT NULL UNIQUE,
        password      TEXT    NOT NULL,
        creato_il     INTEGER NOT NULL,
        aggiornato_il INTEGER NOT NULL
    )
    """,
    """
    CREATE TABLE sessione (
        hash_token TEXT    PRIMARY KEY,
        csrf       TEXT    NOT NULL,
        creato_il  INTEGER NOT NULL,
        visto_il   INTEGER NOT NULL,
        scade_il   INTEGER NOT NULL
    )
    """,
    """
    CREATE TABLE tentativo (
        id      INTEGER PRIMARY KEY AUTOINCREMENT,
        chiave  TEXT    NOT NULL,
        istante INTEGER NOT NULL
    )
    """,
    "CREATE INDEX idx_tentativo ON tentativo (chiave, istante)",
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
)


@pytest.fixture
def vecchio(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> Path:
    """Un database alla versione 1, con dentro roba che non si deve perdere.

    Non si usa il codice del progetto per riempirlo: si scrive con sqlite3 e
    basta. Cosi il test non dipende da auth.py o config_doc.py, che cambiano
    proprio in questa modifica.
    """
    percorso = tmp_path / "vecchio.db"
    conn = sqlite3.connect(str(percorso))
    conn.execute("PRAGMA foreign_keys = ON")
    for sql in V1:
        conn.execute(sql)
    conn.execute(
        "INSERT INTO utente (id, utente, password, creato_il, aggiornato_il) "
        "VALUES (1, 'antonino', 'scrypt$finto', 1000, 1000)"
    )
    conn.execute(
        "INSERT INTO sessione (hash_token, csrf, creato_il, visto_il, scade_il) "
        "VALUES ('abc', 'csrf', 1000, 1000, 9999999999)"
    )
    conn.execute(
        "INSERT INTO tablet (id, nome, creato_il, hash_dispositivo, revisione, visto_il) "
        "VALUES ('aaaa1111', 'Giuseppe', 1000, 'hash-del-dispositivo', 3, 2000)"
    )
    conn.execute(
        "INSERT INTO tablet (id, nome, creato_il, hash_arruolamento, arruolamento_scade) "
        "VALUES ('bbbb2222', 'Da arruolare', 1100, 'hash-arruolamento', 9999999999)"
    )
    for n in (1, 2, 3):
        conn.execute(
            "INSERT INTO revisione (id_tablet, revisione, documento, creato_il, origine) "
            "VALUES ('aaaa1111', ?, '{}', ?, 'modifica')",
            (n, 1000 + n),
        )
    conn.execute("INSERT INTO tentativo (chiave, istante) VALUES ('ip:1.2.3.4', 500)")
    conn.execute("PRAGMA user_version = 1")
    conn.commit()
    conn.close()

    monkeypatch.setenv("STARKID_DB", str(percorso))
    db.chiudi_tutte()
    yield percorso
    db.chiudi_tutte()


def _conn(percorso: Path) -> sqlite3.Connection:
    c = sqlite3.connect(str(percorso))
    c.row_factory = sqlite3.Row
    return c


def test_si_parte_davvero_dalla_versione_1(vecchio: Path) -> None:
    """Se il fixture non costruisse un v1, tutti i test sotto non provano niente."""
    conn = _conn(vecchio)
    assert conn.execute("PRAGMA user_version").fetchone()[0] == 1
    conn.close()


def test_la_migrazione_arriva_in_fondo(vecchio: Path) -> None:
    arrivo = db.migra()
    assert arrivo == db.MIGRAZIONI[-1][0]
    conn = _conn(vecchio)
    assert conn.execute("PRAGMA user_version").fetchone()[0] == arrivo
    conn.close()


def test_lo_storico_delle_revisioni_non_sparisce(vecchio: Path) -> None:
    """La trappola: ricostruire tablet scatena l'ON DELETE CASCADE di revisione.

    Con foreign_keys accese, un DROP TABLE tablet fa un DELETE implicito che
    porta via tutto lo storico di configurazione, in silenzio. Per questo su
    tablet si fa solo ADD COLUMN. Se un giorno qualcuno ricostruisce quella
    tabella dentro migra(), e questo test a dirlo.
    """
    db.migra()
    conn = _conn(vecchio)
    quante = conn.execute(
        "SELECT COUNT(*) FROM revisione WHERE id_tablet = 'aaaa1111'"
    ).fetchone()[0]
    conn.close()
    assert quante == 3, "lo storico delle revisioni e stato cancellato dalla migrazione"


def test_i_tablet_e_i_loro_token_restano(vecchio: Path) -> None:
    """Un hash_dispositivo perso vuol dire un tablet che si becca 401.

    E un tablet che si becca 401 e un giro nelle camere dei bambini: il
    codice di arruolamento si porta a mano.
    """
    db.migra()
    conn = _conn(vecchio)
    righe = {r["id"]: r for r in conn.execute("SELECT * FROM tablet")}
    conn.close()
    assert set(righe) == {"aaaa1111", "bbbb2222"}
    assert righe["aaaa1111"]["hash_dispositivo"] == "hash-del-dispositivo"
    assert righe["aaaa1111"]["revisione"] == 3
    assert righe["bbbb2222"]["hash_arruolamento"] == "hash-arruolamento"


def test_l_account_che_c_era_diventa_admin(vecchio: Path) -> None:
    """Chi c'era prima e l'amministratore: non deve ritrovarsi chiuso fuori."""
    db.migra()
    conn = _conn(vecchio)
    riga = conn.execute("SELECT * FROM utente WHERE utente = 'antonino'").fetchone()
    conn.close()
    assert riga is not None, "l'account e sparito nella migrazione"
    assert riga["ruolo"] == "admin"
    assert riga["attivo"] == 1
    assert riga["id_padre"] is None
    assert riga["password"] == "scrypt$finto", "la password e stata toccata"


def test_i_tablet_che_c_erano_vanno_all_admin(vecchio: Path) -> None:
    """Senza questo, dopo la migrazione nessuno vede piu nessun tablet."""
    db.migra()
    conn = _conn(vecchio)
    concessioni = {
        r["id_tablet"] for r in conn.execute("SELECT id_tablet FROM accesso_tablet WHERE id_utente = 1")
    }
    proprietari = {
        r["id"]: r["id_utente"] for r in conn.execute("SELECT id, id_utente FROM tablet")
    }
    conn.close()
    assert concessioni == {"aaaa1111", "bbbb2222"}
    assert proprietari == {"aaaa1111": 1, "bbbb2222": 1}


def test_il_vincolo_dell_account_unico_e_sparito(vecchio: Path) -> None:
    """Il CHECK (id = 1) era una regola del database: finche c'e, niente ruoli."""
    db.migra()
    conn = _conn(vecchio)
    conn.execute("PRAGMA foreign_keys = ON")
    conn.execute(
        "INSERT INTO utente (utente, password, creato_il, aggiornato_il, ruolo, attivo) "
        "VALUES ('secondo', 'x', 1, 1, 'genitore', 1)"
    )
    conn.commit()
    quanti = conn.execute("SELECT COUNT(*) FROM utente").fetchone()[0]
    conn.close()
    assert quanti == 2


def test_le_sessioni_aperte_vengono_chiuse(vecchio: Path) -> None:
    """Una sessione senza proprietario non si puo autorizzare.

    Si cancellano invece di inventare a chi appartengono: costa un rientro a
    ogni genitore, e toglie di mezzo la domanda.
    """
    db.migra()
    conn = _conn(vecchio)
    quante = conn.execute("SELECT COUNT(*) FROM sessione").fetchone()[0]
    conn.close()
    assert quante == 0


def test_la_sessione_adesso_sa_di_chi_e(vecchio: Path) -> None:
    """Era il buco principale: il cookie diceva "qualcuno", non "chi"."""
    db.migra()
    conn = _conn(vecchio)
    colonne = {r["name"]: r for r in conn.execute("PRAGMA table_info(sessione)")}
    conn.close()
    assert "id_utente" in colonne
    assert colonne["id_utente"]["notnull"] == 1, "una sessione senza utente non deve esistere"


def test_le_chiavi_esterne_reggono_ancora(vecchio: Path) -> None:
    """Una ricostruzione fatta male lascia riferimenti rotti che nessuno vede."""
    db.migra()
    conn = _conn(vecchio)
    conn.execute("PRAGMA foreign_keys = ON")
    rotte = conn.execute("PRAGMA foreign_key_check").fetchall()
    integrita = conn.execute("PRAGMA integrity_check").fetchone()[0]
    conn.close()
    assert rotte == [], "ci sono chiavi esterne rotte: %r" % (rotte,)
    assert integrita == "ok"


def test_cancellare_un_utente_non_porta_via_i_tablet(vecchio: Path) -> None:
    """Le concessioni seguono l'utente; i tablet no.

    Un admin che cancella un genitore non deve far sparire i tablet di quella
    famiglia: sparisce solo il permesso di vederli.
    """
    db.migra()
    conn = _conn(vecchio)
    conn.execute("PRAGMA foreign_keys = ON")
    conn.execute(
        "INSERT INTO utente (id, utente, password, creato_il, aggiornato_il, ruolo, attivo) "
        "VALUES (2, 'altro', 'x', 1, 1, 'genitore', 1)"
    )
    conn.execute(
        "INSERT INTO accesso_tablet (id_utente, id_tablet, creato_il) VALUES (2, 'aaaa1111', 1)"
    )
    conn.commit()
    conn.execute("DELETE FROM utente WHERE id = 2")
    conn.commit()
    tablet = conn.execute("SELECT COUNT(*) FROM tablet").fetchone()[0]
    concessioni = conn.execute(
        "SELECT COUNT(*) FROM accesso_tablet WHERE id_utente = 2"
    ).fetchone()[0]
    conn.close()
    assert tablet == 2, "cancellare un utente ha portato via dei tablet"
    assert concessioni == 0, "le concessioni di un utente cancellato restano appese"


def test_si_puo_rieseguire_a_vuoto(vecchio: Path) -> None:
    """migra() deve essere ripetibile: la lancia ogni comando della CLI."""
    primo = db.migra()
    secondo = db.migra()
    assert primo == secondo
    conn = _conn(vecchio)
    assert conn.execute("SELECT COUNT(*) FROM utente").fetchone()[0] == 1
    conn.close()
