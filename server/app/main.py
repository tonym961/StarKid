"""L'applicazione: rotte, errori, intestazioni.

Forma generale:

- un solo worker uvicorn su 127.0.0.1, nginx davanti. Niente porte nuove verso
  l'esterno: il server non e raggiungibile se non passando da nginx.
- endpoint `def` e non `async def`: il lavoro e tutto SQLite sincrono, e
  FastAPI li manda nel threadpool. Un `async def` che chiama sqlite3
  bloccherebbe l'event loop, che e il modo piu comune di rendere lento un
  server piccolo.
- CORS **chiuso**: nessun middleware CORS, quindi nessuna intestazione
  Access-Control-Allow-Origin. La web UI la serve lo stesso nginx sullo stesso
  dominio, quindi non c'e nessuna origine da autorizzare; e senza
  autorizzazioni un'altra pagina non puo leggere le risposte di questa API
  anche se ha il cookie del genitore.
- la documentazione automatica e spenta: un elenco di rotte e di modelli e
  un regalo a chi passa a vedere cosa gira su una macchina. Si accende con
  STARKID_DOCS=1 quando serve lavorarci.
"""

from __future__ import annotations

import json
import logging
import os
import secrets
import sqlite3
from contextlib import asynccontextmanager
from typing import Any, AsyncIterator

import anyio.to_thread
from fastapi import Depends, FastAPI, HTTPException, Path, Request, Response, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from . import VERSIONE_API, auth, config_doc, db
from .models import (
    DocumentoConfig,
    RichiestaAck,
    RichiestaArruolamento,
    RichiestaLogin,
    RichiestaNuovoTablet,
    RichiestaRipristino,
    RichiestaSalute,
)

registro = logging.getLogger("starkid")


def _prepara_log() -> None:
    """Fa arrivare i nostri log nel journal, che e dove li si va a cercare.

    uvicorn configura solo i propri logger e non tocca la radice: senza questa
    funzione i nostri messaggi finiscono sul gestore d'emergenza di logging,
    che e fermo a WARNING. Il risultato era che le righe d'avvio - fra cui
    quella che dice se il threadpool e stato limitato - non si vedevano da
    nessuna parte, e quella e un'informazione che serve proprio quando il
    servizio si comporta male.

    Se qualcuno ha gia configurato logging (i test, o un domani un file di
    configurazione di uvicorn), non si tocca niente: si lascia propagare.
    """
    registro.setLevel(os.environ.get("STARKID_LOG_LEVEL", "INFO").upper())
    if registro.handlers or logging.getLogger().handlers:
        return
    gestore = logging.StreamHandler()
    gestore.setFormatter(logging.Formatter("%(levelname)s: %(name)s: %(message)s"))
    registro.addHandler(gestore)


_prepara_log()

# Un corpo piu grande di questo non e una configurazione, e un tentativo. Il
# documento piu grosso possibile (200 pacchetti) sta in una decina di
# kilobyte. Su una macchina che swappa, leggere corpi enormi e il modo piu
# facile di farla cadere.
LIMITE_CORPO = 64 * 1024

# Un token di arruolamento vale una settimana: il tempo di preparare un
# tablet, non il tempo di dimenticarselo in giro.
DURATA_ARRUOLAMENTO = 7 * 86400

# Un tablet si considera "visto" se ha chiamato negli ultimi quindici minuti.
FINESTRA_PRESENZA = 15 * 60

ID_TABLET = r"^[0-9a-f]{16}$"


# Quanti thread del pool. Gli endpoint sono "def", quindi FastAPI li manda nel
# threadpool di anyio, che di default ne ha quaranta e nessuno li limita. Ogni
# thread si apre la sua connessione a SQLite e non la chiude piu, e ogni
# verifica di password costa 16 MiB: con quaranta posti si sfonda il cgroup da
# 192 MB (MemoryMax in starkid.service) e il servizio viene ucciso e riavviato
# in ciclo. Per un genitore e una manciata di tablet, otto sono sovrabbondanti.
THREAD_MASSIMI = 8


@asynccontextmanager
async def ciclo_di_vita(app: FastAPI) -> AsyncIterator[None]:
    try:
        anyio.to_thread.current_default_thread_limiter().total_tokens = THREAD_MASSIMI
        registro.info("threadpool limitato a %d thread", THREAD_MASSIMI)
    except Exception:  # pragma: no cover - dipende dalla versione di anyio
        registro.warning(
            "non ho potuto limitare il threadpool: con il default (40) si rischia "
            "di sfondare MemoryMax. Controlla la versione di anyio."
        )
    db.inizializza()
    if auth.utente_unico() is None:
        registro.warning(
            "nessun account: crealo con "
            "'python -m app.cli crea-utente <nome>' prima di usare la web UI"
        )
    try:
        yield
    finally:
        db.chiudi_tutte()


def crea_app() -> FastAPI:
    mostra_docs = os.environ.get("STARKID_DOCS", "0") == "1"
    return FastAPI(
        title="StarKid",
        version=VERSIONE_API,
        lifespan=ciclo_di_vita,
        docs_url="/api/docs" if mostra_docs else None,
        redoc_url=None,
        openapi_url="/api/openapi.json" if mostra_docs else None,
    )


app = crea_app()


# ---------------------------------------------------------------------------
# Intestazioni e limite del corpo
# ---------------------------------------------------------------------------


@app.middleware("http")
async def guardia(request: Request, call_next: Any) -> Response:
    lunghezza = request.headers.get("content-length")
    if lunghezza is not None:
        try:
            if int(lunghezza) > LIMITE_CORPO:
                return JSONResponse(
                    {"errore": "corpo troppo grande"},
                    status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
                    headers=auth.intestazioni_sicurezza(),
                )
        except ValueError:
            return JSONResponse(
                {"errore": "richiesta malformata"},
                status_code=status.HTTP_400_BAD_REQUEST,
                headers=auth.intestazioni_sicurezza(),
            )
    risposta = await call_next(request)
    for nome, valore in auth.intestazioni_sicurezza().items():
        risposta.headers.setdefault(nome, valore)
    return risposta


# ---------------------------------------------------------------------------
# Errori
#
# Un corpo solo per tutti: {"errore": "..."} e, quando serve, "dettagli".
# Il 422 non rimanda indietro quello che e stato mandato: ripetere l'input di
# chi chiama e il modo in cui un messaggio d'errore diventa un megafono.
# ---------------------------------------------------------------------------


def _risposta(codice: int, errore: str, **extra: Any) -> JSONResponse:
    corpo: dict[str, Any] = {"errore": errore}
    corpo.update(extra)
    return JSONResponse(corpo, status_code=codice, headers=auth.intestazioni_sicurezza())


@app.exception_handler(HTTPException)
async def errore_http(request: Request, errore: HTTPException) -> JSONResponse:
    risposta = _risposta(errore.status_code, str(errore.detail))
    for nome, valore in (errore.headers or {}).items():
        risposta.headers[nome] = valore
    return risposta


@app.exception_handler(RequestValidationError)
async def errore_validazione(request: Request, errore: RequestValidationError) -> JSONResponse:
    dettagli = []
    for guasto in errore.errors():
        posizione = [str(p) for p in guasto.get("loc", ()) if p not in ("body", "query")]
        dettagli.append(
            {
                "campo": ".".join(posizione) or "corpo",
                "motivo": str(guasto.get("msg", "valore non valido")),
            }
        )
    # Lo stesso elenco con due nomi: "dettagli" e come si chiama qui,
    # "campi" e il nome che la web UI cerca per accendere il campo sbagliato
    # nel modulo. Sono gli stessi dati, non c'e niente in piu da sapere.
    return _risposta(
        status.HTTP_422_UNPROCESSABLE_ENTITY,
        "richiesta non valida",
        dettagli=dettagli[:40],
        campi=dettagli[:40],
    )


@app.exception_handler(config_doc.ConflittoRevisione)
async def errore_conflitto(
    request: Request, errore: config_doc.ConflittoRevisione
) -> JSONResponse:
    return _risposta(
        status.HTTP_409_CONFLICT,
        "la configurazione e cambiata mentre la modificavi: ricarica e riprova",
        revisione_corrente=errore.corrente,
    )


@app.exception_handler(config_doc.RevisioneInesistente)
async def errore_revisione(
    request: Request, errore: config_doc.RevisioneInesistente
) -> JSONResponse:
    return _risposta(status.HTTP_404_NOT_FOUND, str(errore))


@app.exception_handler(config_doc.DocumentoNonValido)
async def errore_documento(
    request: Request, errore: config_doc.DocumentoNonValido
) -> JSONResponse:
    registro.error("documento salvato non valido: %s", errore)
    return _risposta(
        status.HTTP_500_INTERNAL_SERVER_ERROR,
        "la configurazione salvata non e piu valida: salvala di nuovo dalla pagina",
    )


@app.exception_handler(Exception)
async def errore_interno(request: Request, errore: Exception) -> JSONResponse:
    # Il motivo resta nel log di systemd, non nella risposta: un traceback
    # racconta percorsi, versioni e struttura del codice.
    registro.exception("errore non gestito su %s", request.url.path)
    return _risposta(status.HTTP_500_INTERNAL_SERVER_ERROR, "errore interno")


# ---------------------------------------------------------------------------
# Monitor
# ---------------------------------------------------------------------------


@app.get("/healthz")
def salute() -> Response:
    """Per un monitor esterno. Non dice niente che non sia 'sono in piedi'.

    Nessuna versione, nessun percorso, nessun conteggio: questa rotta non ha
    autenticazione, quindi tutto quello che dice lo dice al mondo.
    """
    try:
        db.leggi_uno("SELECT 1")
    except sqlite3.Error:
        return JSONResponse(
            {"stato": "non pronto"},
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            headers=auth.intestazioni_sicurezza(),
        )
    return JSONResponse({"stato": "ok"}, headers=auth.intestazioni_sicurezza())


# Momento in cui questo processo e stato importato: serve solo a dire al
# genitore da quanto il servizio e in piedi.
AVVIATO_IL = db.adesso()

# Dove sta il certificato, per contare i giorni che restano. Il servizio gira
# con ProtectSystem=strict e /etc/letsencrypt non e fra le sue ReadWritePaths:
# molto probabilmente non riesce a leggerlo, ed e giusto cosi. Quando non ci
# riesce il campo non viene restituito e la pagina scrive "il server non lo
# dice", invece di inventare un numero o di dare un errore.
CERTIFICATO = os.environ.get(
    "STARKID_CERT", "/etc/letsencrypt/live/starkid.antoninomiccio.com/fullchain.pem"
)


def _giorni_al_certificato() -> int | None:
    """Giorni che restano, letti dal notAfter del certificato in formato PEM.

    Si fa a mano e non con una libreria: aggiungere cryptography a questo
    server vuol dire un altro binario da scompattare in 446 MB di RAM, per
    leggere una data.
    """
    try:
        with open(CERTIFICATO, "rb") as f:
            pem = f.read()
    except OSError:
        return None
    try:
        import base64
        import datetime

        inizio = pem.index(b"-----BEGIN CERTIFICATE-----")
        fine = pem.index(b"-----END CERTIFICATE-----", inizio)
        der = base64.b64decode(pem[inizio + 27 : fine])
        # Le due date stanno nel campo Validity, in UTCTime (tag 0x17) o
        # GeneralizedTime (0x18). La seconda che compare e il notAfter.
        date: list[datetime.datetime] = []
        posizione = 0
        while posizione < len(der) - 2 and len(date) < 2:
            if der[posizione] in (0x17, 0x18):
                lunghezza = der[posizione + 1]
                grezzo = der[posizione + 2 : posizione + 2 + lunghezza].decode("ascii")
                forma = "%y%m%d%H%M%SZ" if der[posizione] == 0x17 else "%Y%m%d%H%M%SZ"
                try:
                    date.append(
                        datetime.datetime.strptime(grezzo, forma).replace(
                            tzinfo=datetime.timezone.utc
                        )
                    )
                except ValueError:
                    pass
                posizione += 2 + lunghezza
                continue
            posizione += 1
        if len(date) < 2:
            return None
        resta = date[1] - datetime.datetime.now(datetime.timezone.utc)
        return int(resta.total_seconds() // 86400)
    except (ValueError, IndexError, UnicodeDecodeError):
        return None


@app.get("/api/v1/salute")
def salute_del_servizio(
    sessione: sqlite3.Row = Depends(auth.sessione_attiva),
) -> dict[str, Any]:
    """Le quattro cose che il genitore guarda nella pagina "Come sta".

    Dietro la sessione, non come /healthz: qui ci sono una versione e la
    dimensione del database, cioe roba che a chi passa non si racconta.
    """
    voce: dict[str, Any] = {"versione": VERSIONE_API, "da": AVVIATO_IL}

    giorni = _giorni_al_certificato()
    if giorni is not None:
        voce["certificato_giorni"] = giorni

    try:
        voce["database_byte"] = db.percorso().stat().st_size
    except OSError:
        pass

    # L'ultimo backup si legge dalla cartella, non da una tabella: chi scrive
    # le copie e un timer di systemd, che nel database non entra.
    try:
        copie = sorted(
            (p for p in (db.percorso().parent / "backup").iterdir() if p.is_file()),
            key=lambda p: p.stat().st_mtime,
        )
        voce["ultimo_backup"] = int(copie[-1].stat().st_mtime) if copie else None
    except OSError:
        voce["ultimo_backup"] = None

    return voce


# ---------------------------------------------------------------------------
# Lato tablet
# ---------------------------------------------------------------------------


@app.post("/api/v1/device/enroll", status_code=status.HTTP_201_CREATED)
def arruola(dati: RichiestaArruolamento, request: Request) -> dict[str, Any]:
    """Scambia il token di arruolamento con il token del dispositivo.

    Il token di arruolamento vale una volta sola: appena speso viene
    cancellato. Un QR fotografato da qualcun altro dopo che il tablet e stato
    preparato non vale niente.
    """
    chiave = ["arruolamento:" + auth.indirizzo(request)]
    auth.vieta_se_in_attesa(chiave)
    atteso = auth.hash_token(dati.token_arruolamento)
    ora = db.adesso()
    token = auth.nuovo_token()
    try:
        with db.scrittura() as conn:
            riga = conn.execute(
                "SELECT id, arruolamento_scade FROM tablet WHERE hash_arruolamento = ?",
                (atteso,),
            ).fetchone()
            if riga is None or (riga["arruolamento_scade"] or 0) < ora:
                raise HTTPException(
                    status_code=status.HTTP_401_UNAUTHORIZED,
                    detail="token di arruolamento non valido",
                )
            conn.execute(
                "UPDATE tablet SET hash_dispositivo = ?, arruolato_il = ?, "
                "hash_arruolamento = NULL, arruolamento_scade = NULL, visto_il = ? "
                "WHERE id = ?",
                (auth.hash_token(token), ora, ora, riga["id"]),
            )
            id_tablet = str(riga["id"])
    except HTTPException:
        auth.registra_fallimento(chiave)
        raise
    registro.info("tablet %s arruolato", id_tablet)
    return {"id_dispositivo": id_tablet, "token_dispositivo": token}


@app.get("/api/v1/device/config")
def configurazione_dispositivo(
    tablet: sqlite3.Row = Depends(auth.dispositivo),
) -> dict[str, Any]:
    documento = config_doc.leggi(str(tablet["id"]))
    db.esegui(
        "UPDATE tablet SET consegnata = ? WHERE id = ?",
        (documento["revisione"], tablet["id"]),
    )
    return documento


@app.post("/api/v1/device/ack")
def conferma(
    dati: RichiestaAck, tablet: sqlite3.Row = Depends(auth.dispositivo)
) -> dict[str, Any]:
    """Il tablet dice cosa ha fatto della configurazione.

    E l'unica cosa che il dispositivo racconta del documento, ed e per questo
    che il kiosk puo essere asimmetrico: "kiosk" fra i campi rifiutati
    significa "non l'ho acceso, qui nessuno ha confermato col PIN".
    """
    if dati.revisione > int(tablet["revisione"]):
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="revisione sconosciuta: chiedi di nuovo la configurazione",
        )
    campi = [c.model_dump() for c in dati.campi_rifiutati]
    db.esegui(
        "UPDATE tablet SET ack_revisione = ?, ack_esito = ?, ack_campi = ?, ack_il = ? "
        "WHERE id = ?",
        (
            dati.revisione,
            dati.esito,
            json.dumps(campi, ensure_ascii=False),
            db.adesso(),
            tablet["id"],
        ),
    )
    return {"esito": "registrato"}


@app.post("/api/v1/device/health")
def salute_dispositivo(
    dati: RichiestaSalute, tablet: sqlite3.Row = Depends(auth.dispositivo)
) -> dict[str, Any]:
    """Le cinque spie dei permessi. Nessun dato del bambino, per questo basta un bool."""
    db.esegui(
        "UPDATE tablet SET salute = ?, salute_il = ? WHERE id = ?",
        (json.dumps(dati.model_dump(), ensure_ascii=False), db.adesso(), tablet["id"]),
    )
    return {"esito": "registrato"}


# ---------------------------------------------------------------------------
# Lato web: accesso
# ---------------------------------------------------------------------------


@app.post("/api/v1/auth/login")
def login(dati: RichiestaLogin, request: Request, response: Response) -> dict[str, Any]:
    """Accesso del genitore. Un solo account, nome utente e non email.

    Il messaggio di errore e lo stesso per un nome che non esiste e per una
    password sbagliata, e i due casi costano lo stesso tempo: un messaggio
    diverso o una risposta piu veloce direbbero a chi prova se sta almeno
    indovinando il nome.
    """
    # Si contano due chiavi, ma l'attesa si applica **solo** a quella
    # dell'indirizzo. C'e un account solo: contando anche la chiave del nome
    # utente, chi lo indovina manda otto password sbagliate e poi una ogni
    # quarto d'ora, e il genitore non entra piu da nessun indirizzo - col
    # rimedio che sta via SSH. La chiave del nome resta perche serve a vedere
    # nei log che qualcuno lo sta provando, e nginx piu fail2ban coprono il
    # resto.
    chiave_ip = "ip:" + auth.indirizzo(request)
    chiavi = [chiave_ip, "utente:" + dati.utente.lower()]
    auth.vieta_se_in_attesa([chiave_ip])
    if not auth.accedi(dati.utente, dati.password):
        auth.registra_fallimento(chiavi)
        registro.warning("accesso rifiutato da %s", auth.indirizzo(request))
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED, detail="credenziali non valide"
        )
    auth.azzera_tentativi(chiavi)
    token, csrf = auth.crea_sessione()
    auth.imposta_cookie(response, token)
    auth.imposta_cookie_csrf(response, csrf)
    return {"csrf": csrf, "utente": dati.utente}


@app.post("/api/v1/auth/logout")
def logout(
    request: Request,
    response: Response,
    sessione: sqlite3.Row = Depends(auth.sessione_scrivibile),
) -> dict[str, Any]:
    token = request.cookies.get(auth.NOME_COOKIE)
    if token:
        auth.elimina_sessione(token)
    auth.togli_cookie(response)
    auth.togli_cookie_csrf(response)
    return {"esito": "uscito"}


# ---------------------------------------------------------------------------
# Lato web: tablet
# ---------------------------------------------------------------------------


def _tablet(id_tablet: str) -> sqlite3.Row:
    riga = db.leggi_uno("SELECT * FROM tablet WHERE id = ?", (id_tablet,))
    if riga is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND, detail="questo tablet non esiste"
        )
    return riga


def _riassunto(tablet: sqlite3.Row) -> dict[str, Any]:
    ora = db.adesso()
    voce: dict[str, Any] = {
        "id": tablet["id"],
        "nome": tablet["nome"],
        "creato_il": tablet["creato_il"],
        "arruolato": tablet["hash_dispositivo"] is not None,
        "arruolato_il": tablet["arruolato_il"],
        "attesa_arruolamento": tablet["hash_arruolamento"] is not None
        and (tablet["arruolamento_scade"] or 0) >= ora,
        "visto_il": tablet["visto_il"],
        "visto_di_recente": bool(
            tablet["visto_il"] and ora - int(tablet["visto_il"]) <= FINESTRA_PRESENZA
        ),
        "revisione": tablet["revisione"],
        "revisione_consegnata": tablet["consegnata"],
        "revisione_confermata": tablet["ack_revisione"],
        "ultimo_esito": tablet["ack_esito"],
        "campi_rifiutati": config_doc.campi_rifiutati(tablet),
        "allineato": tablet["ack_revisione"] == tablet["revisione"]
        and tablet["ack_esito"] == "applied",
    }
    # "dichiarata" non e pignoleria di nomi. Le spie e l'esito dell'ack sono
    # quello che il tablet DICE di se: il server non ha modo di verificarli, e
    # chi avesse il token di un tablet puo mandare cinque spie verdi e un ack
    # "applied" piu spesso di quanto lo fa il tablet vero, sovrascrivendolo. Il
    # nome del campo deve dirlo, altrimenti la pagina del genitore mostra come
    # fatto quello che e un'autodichiarazione del sospettato.
    salute = tablet["salute"]
    if salute:
        try:
            voce["salute_dichiarata"] = json.loads(salute)
        except (ValueError, TypeError):
            voce["salute_dichiarata"] = None
    else:
        voce["salute_dichiarata"] = None
    voce["salute_il"] = tablet["salute_il"]
    try:
        documento = config_doc.leggi(str(tablet["id"]))
    except (config_doc.DocumentoNonValido, config_doc.RevisioneInesistente):
        # Un tablet con la configurazione illeggibile non deve far sparire
        # l'elenco: si vede che c'e e che va risistemato.
        voce["kiosk"] = "sconosciuto"
        voce["bloccato"] = None
        voce["configurazione_leggibile"] = False
    else:
        voce["kiosk"] = config_doc.stato_kiosk(tablet, documento)
        # Nell'elenco e non solo nella scheda: un tablet chiuso si deve vedere
        # senza doverlo aprire, perche e la cosa che si cerca di fretta.
        # "pubblicato" e non "applicato": e quello che il server ha deciso,
        # non quello che il tablet ha confermato - per sapere se e arrivato
        # c'e gia "allineato".
        voce["bloccato"] = bool(documento.get("bloccato"))
        voce["configurazione_leggibile"] = True
    return voce


@app.get("/api/v1/tablets")
def elenco_tablet(sessione: sqlite3.Row = Depends(auth.sessione_attiva)) -> dict[str, Any]:
    righe = db.leggi_tutti("SELECT * FROM tablet ORDER BY creato_il")
    # La chiave si chiama "tablets" come la rotta: la web UI legge questa.
    return {"tablets": [_riassunto(r) for r in righe]}


@app.post("/api/v1/tablets", status_code=status.HTTP_201_CREATED)
def crea_tablet(
    dati: RichiestaNuovoTablet, sessione: sqlite3.Row = Depends(auth.sessione_scrivibile)
) -> dict[str, Any]:
    """Crea un tablet e restituisce il token di arruolamento.

    Il token si vede **adesso e basta**: nel database ne resta solo l'hash. Il
    QR lo costruisce il browser con questo token, l'indirizzo del server, il
    nome del bambino e, se serve, la password del Wi-Fi: le ultime due cose non
    passano da qui e non ci devono passare.
    """
    id_tablet = secrets.token_hex(8)
    token = auth.nuovo_token()
    ora = db.adesso()
    scade = ora + DURATA_ARRUOLAMENTO
    with db.scrittura() as conn:
        conn.execute(
            "INSERT INTO tablet (id, nome, creato_il, hash_arruolamento, "
            "arruolamento_scade, revisione) VALUES (?, ?, ?, ?, ?, 1)",
            (id_tablet, dati.nome, ora, auth.hash_token(token), scade),
        )
        documento = config_doc.salva_prima_revisione(conn, id_tablet)
    registro.info("tablet %s creato", id_tablet)
    return {
        "id": id_tablet,
        "nome": dati.nome,
        "creato_il": ora,
        "token_arruolamento": token,
        "arruolamento_scade": scade,
        "configurazione": documento,
        "avviso": "il token di arruolamento si vede solo adesso: il server ne tiene solo l'hash",
    }


@app.get("/api/v1/tablets/{id_tablet}/config")
def leggi_config(
    id_tablet: str = Path(pattern=ID_TABLET),
    sessione: sqlite3.Row = Depends(auth.sessione_attiva),
) -> dict[str, Any]:
    _tablet(id_tablet)
    return config_doc.leggi(id_tablet)


@app.put("/api/v1/tablets/{id_tablet}/config")
def scrivi_config(
    documento: DocumentoConfig,
    id_tablet: str = Path(pattern=ID_TABLET),
    sessione: sqlite3.Row = Depends(auth.sessione_scrivibile),
) -> dict[str, Any]:
    """Nuova revisione. Il documento arriva intero, non a pezzi.

    Niente PATCH di proposito: una configurazione applicata a meta e la cosa
    peggiore che possa arrivare sul tablet di un bambino. Chi scrive manda
    tutto il documento, e con la revisione da cui era partito.
    """
    tablet = _tablet(id_tablet)
    prima = config_doc.leggi(id_tablet)
    nuovo = config_doc.scrivi(id_tablet, documento)
    if config_doc.kiosk_e_una_richiesta(bool(prima["kiosk"]), bool(nuovo["kiosk"])):
        # Si dice subito com'e: il kiosk acceso da qui e una richiesta, e
        # diventa vero solo quando il tablet risponde di averlo applicato.
        nuovo = dict(nuovo)
        nuovo["kiosk_stato"] = "in attesa"
        nuovo["avviso"] = (
            "il kiosk si accende solo confermando col PIN sul tablet: "
            "da qui e una richiesta"
        )
    registro.info("tablet %s: revisione %s", id_tablet, nuovo["revisione"])
    return nuovo


@app.get("/api/v1/tablets/{id_tablet}/history")
def storico_config(
    id_tablet: str = Path(pattern=ID_TABLET),
    sessione: sqlite3.Row = Depends(auth.sessione_attiva),
) -> dict[str, Any]:
    _tablet(id_tablet)
    return {
        "massimo": config_doc.MASSIMO_STORICO,
        "revisioni": config_doc.storico(id_tablet),
    }


@app.post("/api/v1/tablets/{id_tablet}/revert")
def ripristina_config(
    dati: RichiestaRipristino,
    id_tablet: str = Path(pattern=ID_TABLET),
    sessione: sqlite3.Row = Depends(auth.sessione_scrivibile),
) -> dict[str, Any]:
    """Torna a una revisione dello storico scrivendone una nuova."""
    _tablet(id_tablet)
    nuovo = config_doc.ripristina(id_tablet, dati.revisione)
    registro.info(
        "tablet %s: ripristinata la %s come %s", id_tablet, dati.revisione, nuovo["revisione"]
    )
    return nuovo


@app.delete("/api/v1/tablets/{id_tablet}")
def elimina_tablet(
    id_tablet: str = Path(pattern=ID_TABLET),
    sessione: sqlite3.Row = Depends(auth.sessione_scrivibile),
) -> dict[str, Any]:
    """Toglie il tablet e tutto il suo storico.

    Il token del dispositivo muore con la riga: il tablet, alla chiamata
    successiva, riceve 401 e resta con l'ultima configurazione che aveva. Non
    si spegne niente a distanza, e la direzione della configurazione resta una
    sola.
    """
    _tablet(id_tablet)
    db.esegui("DELETE FROM tablet WHERE id = ?", (id_tablet,))
    registro.info("tablet %s eliminato", id_tablet)
    return {"esito": "eliminato", "id": id_tablet}
