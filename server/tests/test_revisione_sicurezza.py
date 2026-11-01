"""I buchi trovati dalla revisione, uno per test.

Ogni test qui sotto fallirebbe sulla versione di prima. Non e un elenco di
regressioni generiche: sono i punti in cui la revisione ha mostrato uno
scenario concreto, e il test serve a non ritrovarsi lo stesso scenario fra sei
mesi perche "sembrava equivalente".
"""

from __future__ import annotations

from pathlib import Path
from typing import Any

import pytest
from fastapi.testclient import TestClient

from app import auth, cli, db

from .conftest import PAROLA, UTENTE


# ---------------------------------------------------------------------------
# CSRF
# ---------------------------------------------------------------------------


def test_senza_csrf_la_scrittura_e_403(account: TestClient) -> None:
    """Il controllo c'e davvero: e il motivo per cui la web UI deve mandarlo.

    La web UI non lo mandava, quindi ogni scrittura prendeva 403 e la pagina
    sembrava rotta senza dire perche. I test passavano perche l'intestazione
    la metteva a mano conftest: questo test esiste per ricordare che il
    controllo e vero e che il front-end ci deve fare i conti.
    """
    risposta = account.post("/api/v1/auth/login", json={"utente": UTENTE, "password": PAROLA})
    assert risposta.status_code == 200
    # Nessun x-csrf-token in testa, di proposito: il cookie di sessione c'e.
    assert "x-csrf-token" not in account.headers
    senza = account.post("/api/v1/tablets", json={"nome": "non deve nascere"})
    assert senza.status_code == 403, senza.text


def test_il_csrf_sta_anche_in_un_cookie_leggibile(account: TestClient) -> None:
    """La pagina deve poterlo leggere, quindi quel cookie non e httponly.

    Vale anche come promemoria per nginx: con "proxy_cookie_flags ~" la
    regola prendeva ogni cookie, httponly compreso, e la web UI non lo
    trovava piu. Nel vhost il cookie va nominato.
    """
    risposta = account.post("/api/v1/auth/login", json={"utente": UTENTE, "password": PAROLA})
    assert risposta.status_code == 200
    atteso = risposta.json()["csrf"]
    assert account.cookies.get(auth.NOME_COOKIE_CSRF) == atteso
    intestazioni = risposta.headers.get_list("set-cookie")
    csrf = [c for c in intestazioni if c.startswith(auth.NOME_COOKIE_CSRF + "=")]
    assert csrf, intestazioni
    assert "HttpOnly" not in csrf[0]
    # Il cookie di sessione invece deve restare httponly.
    sessione = [c for c in intestazioni if c.startswith(auth.NOME_COOKIE + "=")]
    assert sessione and "HttpOnly" in sessione[0]


def test_un_csrf_non_ascii_e_403_non_500(genitore: TestClient) -> None:
    """compare_digest su due str alza TypeError se non sono ASCII.

    Prima finiva nel gestore generico: 500 e un traceback nel journal, per una
    richiesta che meritava un 403.
    """
    # In byte, perche il client si rifiuta di codificare in ascii una stringa
    # che non lo e: le intestazioni HTTP sono byte, e questo e esattamente il
    # caso che arrivava al server da fuori.
    risposta = genitore.post(
        "/api/v1/tablets",
        json={"nome": "neanche questo"},
        headers={"x-csrf-token": "passèparola".encode("utf-8")},
    )
    assert risposta.status_code == 403, risposta.text


# ---------------------------------------------------------------------------
# Attesa crescente
# ---------------------------------------------------------------------------


def test_il_nome_utente_sbagliato_non_chiude_fuori_il_genitore(account: TestClient) -> None:
    """C'e un account solo: contare l'attesa sul nome lo blocca per tutti.

    Prima bastava indovinare il nome utente (uno, e il README non suggerisce
    niente di casuale) e mandare otto password sbagliate: il genitore non
    entrava piu da nessun indirizzo, e il rimedio stava via SSH.
    """
    for _ in range(8):
        account.post("/api/v1/auth/login", json={"utente": UTENTE, "password": "sbagliata"})

    # L'attesa sulla chiave dell'indirizzo c'e, ed e giusto che ci sia.
    chiave_ip = "ip:" + "testclient"
    assert auth.attesa_residua([chiave_ip]) > 0
    # Quella sul nome e contata ma non applicata: serve a vedere nei log che
    # qualcuno lo sta provando, non a sprangare la porta.
    assert auth.attesa_residua(["utente:" + UTENTE]) > 0

    auth.azzera_tentativi([chiave_ip])
    buono = account.post("/api/v1/auth/login", json={"utente": UTENTE, "password": PAROLA})
    assert buono.status_code == 200, buono.text


def test_le_verifiche_di_password_sono_serializzate() -> None:
    """Due alla volta, non quaranta.

    Ogni verifica costa 16 MiB e si paga anche con un nome utente inesistente.
    Nel threadpool da quaranta posti erano centinaia di MB sopra un cgroup da
    192: il kernel uccideva il servizio e systemd lo riavviava in ciclo.
    """
    assert auth._VERIFICHE._value == 2


# ---------------------------------------------------------------------------
# Salute del servizio
# ---------------------------------------------------------------------------


def test_la_salute_del_servizio_vuole_la_sessione(cliente: TestClient) -> None:
    assert cliente.get("/api/v1/salute").status_code == 401


def test_la_salute_del_servizio_dice_le_quattro_cose(genitore: TestClient) -> None:
    """La pagina "Come sta" chiamava una rotta che non esisteva."""
    risposta = genitore.get("/api/v1/salute")
    assert risposta.status_code == 200, risposta.text
    dati = risposta.json()
    assert dati["versione"]
    assert isinstance(dati["da"], int)
    assert dati["database_byte"] > 0
    # Nessun backup ancora: deve dirlo, non ometterlo.
    assert dati["ultimo_backup"] is None


def test_healthz_non_dice_niente_in_piu(cliente: TestClient) -> None:
    """Quella rotta non ha autenticazione: tutto quello che dice, lo dice al mondo."""
    dati = cliente.get("/healthz").json()
    assert dati == {"stato": "ok"}


# ---------------------------------------------------------------------------
# Revoca del token di un dispositivo
# ---------------------------------------------------------------------------


def test_riarruola_revoca_il_token_e_tiene_lo_storico(
    genitore: TestClient, dispositivo: dict[str, Any], capsys: pytest.CaptureFixture[str]
) -> None:
    """Prima la revoca non esisteva: l'unica via era cancellare il tablet.

    Scenario della revisione: il tablet venduto, o il figlio piu grande che si
    legge il token dal bundle di provisioning. Chi lo aveva continuava a
    leggere ogni nuova revisione - gli orari, i giorni, il limite, le app -
    per sempre, e il proprietario non aveva nessuna leva.
    """
    prima = genitore.get("/api/v1/device/config", headers=dispositivo["intestazioni"])
    assert prima.status_code == 200

    assert cli.principale(["riarruola", dispositivo["id"]]) == 0
    stampato = capsys.readouterr().out

    # Il token di prima non vale piu.
    dopo = genitore.get("/api/v1/device/config", headers=dispositivo["intestazioni"])
    assert dopo.status_code == 401, dopo.text

    # Id e storico sono rimasti dov'erano.
    riga = db.leggi_uno("SELECT * FROM tablet WHERE id = ?", (dispositivo["id"],))
    assert riga is not None
    assert riga["hash_dispositivo"] is None
    assert riga["hash_arruolamento"] is not None
    assert riga["revisione"] >= 1

    # Il token nuovo si vede una volta sola, e funziona.
    righe = [r.strip() for r in stampato.splitlines() if r.strip()]
    token = righe[-2] if righe[-1].startswith("Mettilo") else righe[-1]
    nuovo = genitore.post("/api/v1/device/enroll", json={"token_arruolamento": token})
    assert nuovo.status_code == 201, nuovo.text
    assert nuovo.json()["id_dispositivo"] == dispositivo["id"]


def test_riarruola_su_un_id_che_non_esiste(genitore: TestClient) -> None:
    assert cli.principale(["riarruola", "0" * 16]) == 1


# ---------------------------------------------------------------------------
# Log
# ---------------------------------------------------------------------------


def test_il_documento_illeggibile_non_finisce_nel_log(
    genitore: TestClient, tablet: dict[str, Any], caplog: pytest.LogCaptureFixture
) -> None:
    """Nel journal vanno i nomi dei campi, non i valori.

    Il messaggio di pydantic 2 porta con se "input_value=...": sono orari e
    nomi di pacchetto, non segreti, ma e l'unico punto in cui il contenuto
    della configurazione uscirebbe dal suo file, per finire in un log con una
    ritenzione diversa.
    """
    # Si storpia la revisione a mano, come se il file si fosse corrotto.
    db.esegui(
        "UPDATE revisione SET documento = ? WHERE id_tablet = ?",
        ('{"limite_giornaliero": "un sacco di minuti"}', tablet["id"]),
    )
    with caplog.at_level("WARNING"):
        risposta = genitore.get("/api/v1/tablets/%s/history" % tablet["id"])
    assert risposta.status_code == 200, risposta.text
    scritto = caplog.text + risposta.text
    assert "un sacco di minuti" not in scritto
    assert "input_value" not in scritto


# ---------------------------------------------------------------------------
# Threadpool
# ---------------------------------------------------------------------------


def test_il_threadpool_e_limitato(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch, caplog: pytest.LogCaptureFixture
) -> None:
    """Il default di anyio e quaranta, e nessuno lo limitava.

    Ogni thread tiene aperta la sua connessione a SQLite con la sua cache: a
    quaranta posti si sfondava MemoryMax=192M e il servizio veniva ucciso e
    riavviato ogni cinque secondi.

    Il limite si legge solo da dentro il ciclo di eventi che lo ha impostato,
    percio qui si guarda la riga che il ciclo di vita scrive all'avvio: se il
    limite non fosse stato messo, al suo posto ci sarebbe un warning.
    """
    from app.main import THREAD_MASSIMI, app

    assert THREAD_MASSIMI <= 8
    monkeypatch.setenv("STARKID_DB", str(tmp_path / "pool.db"))
    db.chiudi_tutte()
    with caplog.at_level("INFO", logger="starkid"):
        with TestClient(app, base_url="https://testserver"):
            pass
    db.chiudi_tutte()
    assert "threadpool limitato a %d" % THREAD_MASSIMI in caplog.text
    assert "non ho potuto limitare" not in caplog.text


def test_la_cache_per_connessione_e_corta() -> None:
    """Si paga per connessione, e di connessioni ce n'e una per thread."""
    assert db.CACHE_KB <= 500


# ---------------------------------------------------------------------------
# Deploy
# ---------------------------------------------------------------------------

DEPLOY = Path(__file__).resolve().parent.parent / "deploy"


def test_install_cerca_la_cartella_che_esiste() -> None:
    """Lo script si fermava al primo controllo: cercava static/, c'e web/."""
    testo = (DEPLOY / "install.sh").read_text(encoding="utf-8")
    assert '"$SRC/web"' in testo
    assert '"$SRC/static"' not in testo
    assert (Path(__file__).resolve().parent.parent / "web").is_dir()


def test_il_vhost_non_accende_http2() -> None:
    """http2 e un parametro del socket: lo accende per tutti e dodici i siti.

    Dove ci sono tre vhost che proxano WebSocket verso casa, accenderlo di
    sponda da un vhost nuovo le rompe (l'upgrade non esiste in HTTP/2).
    """
    testo = (DEPLOY / "starkid.nginx.conf").read_text(encoding="utf-8")
    righe = [r.strip() for r in testo.splitlines() if r.strip().startswith("listen")]
    assert righe
    assert not any("http2" in r for r in righe), righe


def test_il_vhost_nomina_il_cookie_da_marcare() -> None:
    """Con la tilde vuota prendeva anche il cookie CSRF, che va letto dalla pagina."""
    testo = (DEPLOY / "starkid.nginx.conf").read_text(encoding="utf-8")
    assert "proxy_cookie_flags starkid_sessione" in testo
    assert "proxy_cookie_flags ~" not in testo


def test_healthz_in_chiaro_ha_i_suoi_limiti() -> None:
    """Senza TLS da pagare e senza limiti era la via piu economica a Python.

    Il blocco su 443 eredita "limit_conn" dal suo server, quello su 80 non ha
    niente da ereditare: percio li si vuole scritto, insieme al log acceso -
    senza righe, fail2ban non ha niente da bloccare.
    """
    testo = (DEPLOY / "starkid.nginx.conf").read_text(encoding="utf-8")
    blocchi = testo.split("location = /healthz")
    assert len(blocchi) == 3, "attesi due blocchi /healthz, uno per porta"
    for corpo in (b.split("}")[0] for b in blocchi[1:]):
        assert "limit_req" in corpo, corpo
    ottanta = blocchi[1].split("}")[0]
    assert "limit_conn" in ottanta, ottanta
    assert "access_log off" not in ottanta, ottanta


def test_i_log_hanno_un_tetto_di_dimensione() -> None:
    """Ruotati ogni giorno ma senza maxsize: fra due rotazioni nessun limite.

    Ogni rifiuto di limit_req scrive due righe; a disco pieno si fermano
    nginx, journald e fail2ban per tutti i siti, non solo per StarKid.
    """
    # La rotazione e un timer nostro e non logrotate, che su quella macchina
    # non e nemmeno installato (vedi il commento in install.sh): si controlla
    # che ci sia il tetto in megabyte e che il timer giri piu spesso di una
    # volta al giorno, perche il tetto e sulla dimensione e cento megabyte si
    # scrivono in pochi minuti sotto un flusso di richieste rifiutate.
    rotazione = (DEPLOY / "starkid-ruota-log.sh").read_text(encoding="utf-8")
    assert "TETTO_MB=100" in rotazione
    timer = (DEPLOY / "starkid-ruota-log.timer").read_text(encoding="utf-8")
    assert "OnCalendar=hourly" in timer
    assert not (DEPLOY / "starkid-logrotate.conf").exists()
    vhost = (DEPLOY / "starkid.nginx.conf").read_text(encoding="utf-8")
    # In una cartella propria: il logrotate di nginx prende /var/log/nginx/*.log
    # e due file che elencano lo stesso percorso fanno "duplicate log entry".
    assert "/var/log/starkid/" in vhost
    assert "/var/log/nginx/starkid" not in vhost


def test_il_pip_gira_sotto_un_tetto_di_memoria() -> None:
    """L'OOM killer e globale: colpisce il processo piu grosso della macchina."""
    testo = (DEPLOY / "install.sh").read_text(encoding="utf-8")
    assert "systemd-run" in testo
    assert "MemoryMax=" in testo


def test_non_si_genera_un_segreto_che_nessuno_legge() -> None:
    """STARKID_SECRET_KEY c'era nell'env e in nessuna riga di codice.

    Un segreto finto e peggio di nessun segreto: fra un anno qualcuno lo ruota
    convinto di invalidare le sessioni, e non invalida niente. Si guardano le
    righe eseguibili, non i commenti: nel commento ci sta, e spiega perche e
    stato tolto.
    """
    righe = (DEPLOY / "install.sh").read_text(encoding="utf-8").splitlines()
    codice = [r for r in righe if not r.lstrip().startswith("#")]
    assert not [r for r in codice if "STARKID_SECRET_KEY" in r], codice
    # E nessun file dell'applicazione la legge, che era il punto.
    app_dir = Path(__file__).resolve().parent.parent / "app"
    for sorgente in app_dir.glob("*.py"):
        assert "STARKID_SECRET_KEY" not in sorgente.read_text(encoding="utf-8")


def test_install_copia_tutto_quello_che_serve_per_partire() -> None:
    """Il server non parte senza schema/: l'installazione deve copiarlo.

    Dimenticato alla prima installazione vera sulla VPS. Il server si e
    rifiutato di partire ("il server non parte senza il suo vocabolario") e
    install.sh si e fermato prima di toccare nginx: i due controlli hanno
    funzionato, ma nessuno dei due e un sostituto del copiare il file.
    """
    testo = (DEPLOY / "install.sh").read_text(encoding="utf-8")
    assert '"$SRC/schema"' in testo, "install.sh non copia la cartella schema/"
    assert '"$SRC/schema/config.schema.json"' in testo, "non e fra i file controllati prima"
    # Tutto quello che il codice legge dal proprio albero va copiato: se un
    # domani si aggiunge una cartella, questo test non la vede, ma almeno
    # ricorda che il controllo esiste.
    for cartella in ("app", "web", "schema"):
        assert 'cp -a "$SRC/%s"' % cartella in testo, cartella


# ---------------------------------------------------------------------------
# Il marchio
# ---------------------------------------------------------------------------

SERVER = Path(__file__).resolve().parent.parent
PROGETTO = SERVER.parent

# Sulla VPS esiste solo server/: il resto del progetto (docs/, l'app Android)
# non ci viene copiato, e non deve esserci. I pezzi di marchio che stanno
# fuori da server/ si controllano quindi solo dove il progetto e intero, e
# quando non lo e il test lo DICE invece di passare in silenzio.
PROGETTO_INTERO = (PROGETTO / "docs").is_dir() and (PROGETTO / "app/src/main/res").is_dir()


def test_il_marchio_e_una_stella_sola_dappertutto() -> None:
    """Gli otto file del marchio portano la STESSA stella, carattere per carattere.

    Prima il logo era un path di dieci vertici messi a occhio, ricopiato in
    quattro posti con quattro misure diverse: la favicon, la testata, le
    stelline dei giudizi e l'icona Android erano quattro stelle imparentate
    alla lontana. Adesso il path lo calcola design/stella.sh e lo scrive
    design/genera.sh, una volta sola, in tutti i file. Questo test fallisce
    appena qualcuno ne ritocca uno a mano - che e esattamente il modo in cui
    un sistema di segni torna a essere un mucchio di disegni.
    """
    web = SERVER / "web"
    drawable = PROGETTO / "app/src/main/res/drawable"
    file_marchio = [web / "stella.svg", web / "marchio.svg", web / "icona.svg", web / "app.css"]
    if PROGETTO_INTERO:
        file_marchio += [
            PROGETTO / "docs/logo.svg",
            drawable / "ic_launcher_foreground.xml",
            drawable / "ic_launcher_monochrome.xml",
        ]
    # La prima curva della stella basta a riconoscerla: e il vertice della
    # punta in alto, e cambia appena si tocca il raggio o l'arrotondamento.
    impronta = "M-8.111 -72.887Q0 -91.723 8.111 -72.887"
    for f in file_marchio:
        assert f.is_file(), f"manca {f}"
        assert impronta in f.read_text(encoding="utf-8"), (
            f"{f.name} non ha la stella generata: rilancia ./design/genera.sh"
        )


def test_i_file_generati_dicono_di_esserlo() -> None:
    """Chi apre uno di questi file deve capire subito che non va modificato li."""
    generati = [
        SERVER / "web/stella.svg",
        SERVER / "web/parola.svg",
        SERVER / "web/marchio.svg",
        SERVER / "web/icona.svg",
    ]
    if PROGETTO_INTERO:
        generati += [
            PROGETTO / "docs/logo.svg",
            PROGETTO / "app/src/main/res/drawable/ic_launcher_foreground.xml",
            PROGETTO / "app/src/main/res/drawable/ic_launcher_background.xml",
            PROGETTO / "app/src/main/res/drawable/ic_launcher_monochrome.xml",
        ]
    for f in generati:
        assert "design/genera.sh" in f.read_text(encoding="utf-8"), f.name


def test_il_nome_del_prodotto_non_e_piu_testo() -> None:
    """Un marchio scritto con un font di sistema cambia disegno da un telefono
    all'altro, e su Android Verdana non c'e proprio. Le cinque pagine devono
    mettere l'immagine, e il foglio di stile non deve piu avere la pila di
    caratteri del marchio.
    """
    web = SERVER / "web"
    css = (web / "app.css").read_text(encoding="utf-8")
    assert "--font-marchio:" not in css, "la pila Verdana del marchio e tornata"
    for pagina in ("galleria.html", "nuovo.html", "salute.html", "tablet.html", "tablets.html"):
        testo = (web / pagina).read_text(encoding="utf-8")
        assert 'class="marchio"' in testo, pagina
        assert 'src="marchio.svg"' in testo, f"{pagina}: il marchio non e l'immagine"
        assert "Star<span>Kid</span>" not in testo, f"{pagina}: marchio ancora scritto a mano"
    accesso = (web / "index.html").read_text(encoding="utf-8")
    assert 'src="stella.svg"' in accesso and 'src="parola.svg"' in accesso
    # Il nome resta nella pagina per chi legge con la voce.
    assert ">StarKid</span>" in accesso, "il nome non c'e piu nemmeno per i lettori di schermo"


def test_il_comando_per_creare_account_dice_da_dove_lanciarlo() -> None:
    """`python -m app.cli` cerca il pacchetto nella cartella da cui lo lanci.

    Il README e install.sh dicevano `sudo -u starkid /opt/starkid/venv/bin/python
    -m app.cli crea-utente`, senza cartella: lanciato dalla home risponde
    `No module named app`, ed e successo davvero alla prima persona che ha
    provato a creare il proprio account. Il servizio non se ne accorgeva
    perche systemd gli mette gia WorkingDirectory=/opt/starkid.
    """
    readme = (SERVER / "README.md").read_text(encoding="utf-8")
    install = (DEPLOY / "install.sh").read_text(encoding="utf-8")
    for dove, testo in (("README.md", readme), ("install.sh", install)):
        for riga in testo.splitlines():
            # Solo le righe che qualcuno copierebbe e incollerebbe: quelle
            # che invocano l interprete per davvero. La prosa che NOMINA il
            # comando per spiegarlo non va toccata.
            if "-m app.cli" not in riga or "bin/python" not in riga:
                continue
            assert "env -C" in riga or "cd " in riga, (
                f"{dove}: `{riga.strip()}` non dice da quale cartella lanciarlo"
            )


def test_install_copia_il_database_prima_di_riavviare() -> None:
    """Il riavvio fa partire codice nuovo su un database vecchio.

    Finche si aggiungono campi al documento non succede niente, ma il giorno
    in cui una versione cambia le tabelle l'unica rete sarebbe la copia del
    timer, cioe fino a ventiquattro ore prima. E la copia va fatta con
    "sqlite3 .backup": salva_copia() dello script fa "cp -a", e un database
    in WAL copiato a freddo puo venire via a meta.
    """
    testo = (DEPLOY / "install.sh").read_text(encoding="utf-8")
    prima = testo.split("systemctl restart starkid.service")[0]
    assert "starkid-backup.sh" in prima, (
        "install.sh riavvia il servizio senza aver copiato il database"
    )
    # E deve usare quello script, non cp: lo script e l'unico posto in cui
    # sta il ".backup" con il controllo di integrita.
    copia = (DEPLOY / "starkid-backup.sh").read_text(encoding="utf-8")
    assert ".backup" in copia and "integrity_check" in copia
