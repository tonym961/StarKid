"""Modelli pydantic e vocabolario.

Due regole in questo file.

La prima: **extra="forbid" su ogni modello di richiesta**. Un campo non
previsto deve far rispondere 422, non essere ignorato. Un server che ignora in
silenzio quello che non capisce e un server che accetta "pin", "imei",
"bambino" e poi li scrive nel log: il rifiuto e la cosa che tiene fuori dal
database i dati che si e deciso di non avere.

La seconda: **il vocabolario non e scritto qui**. Temi, sfondi, giochi,
limiti e valori iniziali si leggono da schema/config.schema.json, che e
l'unica fonte di verita. Qui ci sta solo la struttura e il controllo. Se il
file manca o e incoerente il server non parte: meglio fermo che permissivo.
"""

from __future__ import annotations

import base64
import binascii
import copy
import json
import re
from pathlib import Path
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator

PERCORSO_SCHEMA = Path(__file__).resolve().parent.parent / "schema" / "config.schema.json"


def _carica_schema() -> dict[str, Any]:
    try:
        testo = PERCORSO_SCHEMA.read_text(encoding="utf-8")
    except OSError as errore:
        raise RuntimeError(
            "manca lo schema del documento di configurazione (%s): il server "
            "non parte senza il suo vocabolario" % PERCORSO_SCHEMA
        ) from errore
    return json.loads(testo)


SCHEMA: dict[str, Any] = _carica_schema()
_PROP: dict[str, Any] = SCHEMA["properties"]
_FASCIA: dict[str, Any] = _PROP["fascia"]["properties"]
_IMPRONTA: dict[str, Any] = _PROP["impronta_pin"]["properties"]

TEMI: tuple[str, ...] = tuple(_PROP["tema"]["enum"])
SFONDI: tuple[str, ...] = tuple(_PROP["sfondo"]["enum"])
SCHERMATE: tuple[str, ...] = tuple(_PROP["schermata"]["enum"])
ORIENTAMENTI: tuple[str, ...] = tuple(_PROP["orientamento"]["enum"])
GIOCHI: tuple[str, ...] = tuple(_PROP["giochi_spenti"]["items"]["enum"])

LIMITE_MASSIMO: int = int(_PROP["limite_giornaliero"]["maximum"])
MASSIMO_APP: int = int(_PROP["app_permesse"]["maxItems"])
LUNGHEZZA_PACCHETTO: int = int(_PROP["app_permesse"]["items"]["maxLength"])
PACCHETTO = re.compile(_PROP["app_permesse"]["items"]["pattern"])
MINUTO_MASSIMO: int = int(_FASCIA["inizio"]["maximum"])

# Il PIN: qui passano solo sale, giri e impronta, e il server non sa calcolarli
# ne verificarli. Non e pigrizia: non avendo il PIN in chiaro non puo. Quindi
# controlla la forma e basta, e il mestiere lo fanno il browser e il tablet.
SALE = re.compile(_IMPRONTA["sale"]["pattern"])
IMPRONTA = re.compile(_IMPRONTA["impronta"]["pattern"])
GIRI_MINIMI: int = int(_IMPRONTA["giri"]["minimum"])
GIRI_MASSIMI: int = int(_IMPRONTA["giri"]["maximum"])

# L'inventario delle app che il tablet dichiara. Non sta nello schema del
# documento perche non e configurazione: va nell'altro verso, dal tablet al
# server, e serve solo a far scegliere il genitore da un elenco.
MASSIMO_INVENTARIO = 400
LUNGHEZZA_ETICHETTA = 60

# Le icone delle app, perche in un elenco di venticinque voci l'icona si
# riconosce e "com.google.android.apps.youtube.kids" no.
#
# Il tipo di immagine lo decide il server guardando i primi byte, non il
# tablet dicendolo: un dispositivo non deve poter scrivere "image/svg+xml"
# davanti a qualunque cosa e farsela aprire dal browser del genitore. SVG non
# e nell'elenco apposta - dentro un SVG ci sta del codice.
ICONA_BASE64_MASSIMA = 24_000          # ~18 KB di immagine
ICONE_BASE64_TOTALI = 3_000_000        # il tetto della riga intera
FIRME_IMMAGINE: tuple[tuple[bytes, str], ...] = (
    (b"\x89PNG\r\n\x1a\n", "png"),
    (b"RIFF", "webp"),                 # piu il marchio WEBP al byte 8
)

# I nomi dei campi, per i "campi_rifiutati" che manda il tablet: anche quelli
# sono vocabolario chiuso, un dispositivo non deve poter scrivere una stringa
# qualunque nella pagina del genitore.
CAMPI_DOCUMENTO: frozenset[str] = frozenset(k for k in _PROP if k != "revisione")
CAMPI_FASCIA: frozenset[str] = frozenset(_FASCIA)


def _sfondi_per_tema() -> dict[str, frozenset[str]]:
    """Ricava dal file la regola "lo sfondo appartiene al tema".

    Sta nello schema come allOf di if/then, che e JSON Schema valido e
    leggibile da altri strumenti; qui si rilegge invece di riscriverla.
    """
    mappa: dict[str, frozenset[str]] = {}
    for blocco in SCHEMA.get("allOf", []):
        tema = blocco.get("if", {}).get("properties", {}).get("tema", {}).get("const")
        sfondi = blocco.get("then", {}).get("properties", {}).get("sfondo", {}).get("enum")
        if tema and sfondi:
            mappa[tema] = frozenset(sfondi)
    return mappa


SFONDI_PER_TEMA: dict[str, frozenset[str]] = _sfondi_per_tema()

# Controlli di coerenza del vocabolario, all'avvio. Uno sfondo che non
# appartiene a nessun tema sarebbe accettato dal campo e rifiutato dal
# documento: meglio accorgersene qui che in produzione.
if set(SFONDI_PER_TEMA) != set(TEMI):
    raise RuntimeError("lo schema non dice a quale tema appartengono gli sfondi di ogni tema")
_unione: set[str] = set()
for _tema, _elenco in SFONDI_PER_TEMA.items():
    if not _elenco <= set(SFONDI):
        raise RuntimeError("il tema %r elenca sfondi che non esistono" % _tema)
    if _unione & _elenco:
        raise RuntimeError("lo stesso sfondo appartiene a due temi")
    _unione |= _elenco
if _unione != set(SFONDI):
    raise RuntimeError("ci sono sfondi che non appartengono a nessun tema")
if "foto" in SFONDI:
    raise RuntimeError("lo sfondo fotografico non deve stare nel vocabolario del server")

# Valori iniziali di un tablet appena creato: anche questi stanno nello schema,
# come "default" di ogni campo.
VALORI_INIZIALI: dict[str, Any] = {
    nome: copy.deepcopy(prop["default"]) for nome, prop in _PROP.items() if "default" in prop
}
if set(VALORI_INIZIALI) != CAMPI_DOCUMENTO:
    raise RuntimeError("ogni campo del documento deve avere un valore iniziale nello schema")


# ---------------------------------------------------------------------------
# Campi che non devono esistere.
#
# extra="forbid" li rifiuterebbe comunque; questo elenco serve a dare il
# motivo invece di un generico "campo non previsto". Il genitore che prova a
# mettere il nome del bambino nel server deve leggere perche non si puo, e chi
# ritocchera questo server fra un anno deve trovare scritto che era una
# decisione e non una dimenticanza.
# ---------------------------------------------------------------------------

CAMPI_VIETATI: dict[str, str] = {
    # Il PIN si cambia da qui, ma non passa di qui: quello che viaggia e
    # impronta_pin, cioe sale e impronta calcolati nel browser. Le cifre no,
    # ne sotto questo nome ne sotto un altro.
    "pin": "il PIN in chiaro non arriva al server: da qui passa solo impronta_pin",
    "pin_hash": "l'impronta del PIN si chiama impronta_pin e ha dentro sale, giri e impronta",
    "pin_sale": "sale, giri e impronta stanno insieme dentro impronta_pin",
    "bambino": "il nome del bambino non arriva al server",
    "nome_bambino": "il nome del bambino non arriva al server",
    "avatar": "l'avatar si sceglie sul tablet, non da qui",
    "imei": "il server non identifica l'hardware: per questo c'e il token",
    "seriale": "il server non identifica l'hardware: per questo c'e il token",
    "uso_per_app": "quanto ha usato ogni app e il diario di un bambino, resta sul tablet",
    "uso_app": "quanto ha usato ogni app e il diario di un bambino, resta sul tablet",
    "minuti_usati": "i minuti consumati restano sul tablet",
    "limiti_per_app": "i limiti per singola app si mettono sul tablet, non da qui",
    "foto": "la foto di sfondo sta solo sul tablet",
    "sfondo_foto": "la foto di sfondo sta solo sul tablet",
    "wifi": "la password del Wi-Fi sta nel QR, che si genera nel browser",
    "password_wifi": "la password del Wi-Fi sta nel QR, che si genera nel browser",
    "url": "il documento non contiene indirizzi: il server non deve poter dire 'esegui'",
    "server": "il documento non contiene indirizzi: il server non deve poter dire 'esegui'",
    "percorso": "il documento non contiene percorsi: il server non deve poter dire 'esegui'",
    "comando": "il documento non contiene comandi: il server non deve poter dire 'esegui'",
    "script": "il documento non contiene codice: il server non deve poter dire 'esegui'",
    "intent": "il documento non lancia niente sul tablet",
}

_CONTROLLO = re.compile(r"[\x00-\x1f\x7f]")


def _pacchetto_valido(valore: str) -> str:
    """Lo stesso filtro di app_permesse, per la stessa ragione.

    Funzione a parte e non un validatore ereditato: in pydantic 2 copiare un
    @field_validator da un modello all'altro non lo registra, e il campo
    resta senza controllo. Era successo qui: le icone che passavano dalla
    rotta a gruppi non venivano mai guardate, e si salvava quello che
    arrivava. Un test l'ha preso.
    """
    nome = valore.strip()
    if len(nome) > LUNGHEZZA_PACCHETTO or not PACCHETTO.fullmatch(nome):
        raise ValueError("%r non e un nome di pacchetto Android" % valore)
    return nome


def _icona_valida(valore: str) -> str:
    """Base64 in entrata, data URL scritto da noi in uscita. "" se non va.

    Un'icona storta si butta e basta: altrove il vocabolario chiuso rifiuta
    tutto il documento, ed e giusto, ma li si parla di regole. Qui si parla
    di un dettaglio estetico, e rifiutare l'elenco intero perche una app su
    venticinque ha un'icona illeggibile vorrebbe dire restare senza elenco,
    cioe tornare a scrivere i nomi dei pacchetti a mano.
    """
    grezzo = valore.strip()
    if not grezzo or len(grezzo) > ICONA_BASE64_MASSIMA:
        return ""
    try:
        byte = base64.b64decode(grezzo, validate=True)
    except (ValueError, binascii.Error):
        return ""
    tipo = _tipo_immagine(byte)
    if tipo is None:
        return ""
    # Ricodificata da noi: quello che finisce nella pagina del genitore e una
    # stringa che abbiamo scritto noi, non una che e arrivata.
    return "data:image/%s;base64,%s" % (tipo, base64.b64encode(byte).decode("ascii"))


def _tipo_immagine(byte: bytes) -> str | None:
    """Che immagine e, guardando i primi byte. None = nessuna che accettiamo.

    Si guardano i byte e non quello che dichiara chi manda: e la differenza
    fra "il tablet dice che e un PNG" e "e un PNG".
    """
    for firma, tipo in FIRME_IMMAGINE:
        if not byte.startswith(firma):
            continue
        if tipo == "webp" and byte[8:12] != b"WEBP":
            # RIFF da solo e anche un WAV, un AVI e altre cose.
            continue
        return tipo
    return None


def _senza_controlli(testo: str, campo: str) -> str:
    if _CONTROLLO.search(testo):
        raise ValueError("%s contiene caratteri di controllo" % campo)
    return testo


class Richiesta(BaseModel):
    """Base di ogni corpo accettato dal server.

    extra="forbid" e qui una volta per tutte, cosi nessun modello nuovo puo
    nascere permissivo per distrazione.
    """

    model_config = ConfigDict(extra="forbid")


class Fascia(Richiesta):
    """Fascia oraria. Minuti dalla mezzanotte, come sul tablet."""

    attiva: bool
    inizio: int = Field(ge=0, le=MINUTO_MASSIMO)
    fine: int = Field(ge=0, le=MINUTO_MASSIMO)
    giorni: list[int]

    @field_validator("giorni")
    @classmethod
    def _giorni(cls, valore: list[int]) -> list[int]:
        # La numerazione e quella di java.util.Calendar.DAY_OF_WEEK, che usa il
        # tablet: 1 = domenica. Qui si controlla solo che siano 1..7, distinti e
        # almeno uno.
        if not valore:
            raise ValueError("serve almeno un giorno")
        if any(g < 1 or g > 7 for g in valore):
            raise ValueError("i giorni vanno da 1 (domenica) a 7 (sabato)")
        if len(set(valore)) != len(valore):
            raise ValueError("c'e un giorno ripetuto")
        return sorted(valore)

    # Nota: fine < inizio non e un errore, e una fascia che scavalca la
    # mezzanotte. Guard.inWindow sul tablet la gestisce, qui non si inventa una
    # regola piu severa di quella che applica il dispositivo.


class ImprontaPin(Richiesta):
    """Il PIN dell'area genitori, come impronta.

    Qui dentro non c'e il PIN. Ci sono il sale, i giri e il risultato, che il
    browser calcola da solo e manda gia fatti: il server riceve
    sessantaquattro caratteri esadecimali e non e in grado di dire a quale PIN
    corrispondano, ne di verificarne uno. Chi legge questo file fra un anno
    non deve aggiungere un controllo "e il PIN giusto?": non si puo, ed e
    voluto.

    Resta vero - va detto, perche e il limite di questa scelta - che quattro
    cifre dietro a un'impronta si ritrovano con diecimila tentativi. Non e
    quindi un segreto per chi ha il database. Serve a non far passare il PIN
    in chiaro e a non lasciarlo scritto nel database, nei log e nelle dieci
    revisioni dello storico, che restano.
    """

    sale: str
    giri: int = Field(ge=GIRI_MINIMI, le=GIRI_MASSIMI)
    impronta: str

    @field_validator("sale")
    @classmethod
    def _sale(cls, valore: str) -> str:
        if not SALE.fullmatch(valore):
            raise ValueError("il sale sono sedici byte in esadecimale minuscolo")
        return valore

    @field_validator("impronta")
    @classmethod
    def _impronta(cls, valore: str) -> str:
        if not IMPRONTA.fullmatch(valore):
            raise ValueError("l'impronta sono 64 caratteri esadecimali minuscoli")
        return valore


class RegolaApp(Richiesta):
    """Quanto e quando, per una singola app.

    Il tetto giornaliero dice quanto si sta sul tablet e la fascia generale
    quando lo si accende; questa fa le stesse due domande per una app sola.
    Serviva perche "mezz'ora al giorno" e "solo dopo i compiti" sono due
    regole diverse, e un genitore ne vuole spesso una senza l'altra.

    Si aggiunge alle regole generali, non le scavalca: nessuna fascia di una
    app apre il tablet fuori dalla sua fascia, e nessun tetto per app
    restituisce minuti a una giornata finita.
    """

    pacchetto: str
    minuti: int = Field(default=0, ge=0, le=LIMITE_MASSIMO)
    fascia: Fascia | None = None

    @field_validator("pacchetto")
    @classmethod
    def _pacchetto(cls, valore: str) -> str:
        nome = valore.strip()
        # Lo stesso filtro di app_permesse, per la stessa ragione: un nome di
        # pacchetto non puo essere un indirizzo, un percorso o un comando.
        if len(nome) > LUNGHEZZA_PACCHETTO or not PACCHETTO.fullmatch(nome):
            raise ValueError(
                "%r non e un nome di pacchetto Android (esempio: com.esempio.gioco)" % valore
            )
        return nome


class DocumentoConfig(Richiesta):
    """Il documento di configurazione, versione 1.

    In uscita dal server ha sempre la revisione; in entrata la revisione e
    facoltativa e vale "quella che avevo davanti": se non e piu la corrente la
    scrittura viene rifiutata con 409 invece di sovrascrivere il lavoro di
    un'altra scheda del browser.
    """

    revisione: int | None = Field(default=None, ge=1)
    tema: str
    sfondo: str
    schermata: str
    orientamento: str
    kiosk: bool
    kiosk_recenti: bool
    # Il blocco a distanza. E l'unico campo asimmetrico al contrario del
    # kiosk: true lo applica il tablet subito, senza chiedere niente a
    # nessuno, mentre il kiosk acceso va confermato col PIN sul tablet.
    # Qui non c'e niente da confermare: e il genitore che chiude.
    bloccato: bool
    # null vuol dire "da qui non si dice niente sul PIN", non "togli il PIN":
    # un PIN che si cancella da lontano sarebbe un modo per aprire l'area
    # genitori da fuori. Il valore iniziale e quindi null, e un tablet che
    # non ha mai sentito parlare di questo campo continua col PIN suo.
    impronta_pin: ImprontaPin | None = None
    limite_giornaliero: int = Field(ge=0, le=LIMITE_MASSIMO)
    fascia: Fascia
    app_permesse: list[str]
    regole_app: list[RegolaApp] = Field(default_factory=list)
    giochi_spenti: list[str]
    suoni: bool

    @model_validator(mode="before")
    @classmethod
    def _niente_campi_vietati(cls, dati: Any) -> Any:
        if not isinstance(dati, dict):
            return dati
        for chiave in dati:
            if not isinstance(chiave, str):
                continue
            motivo = CAMPI_VIETATI.get(chiave.strip().lower())
            if motivo is not None:
                raise ValueError("il campo %r non esiste di proposito: %s" % (chiave, motivo))
        return dati

    @field_validator("tema")
    @classmethod
    def _tema(cls, valore: str) -> str:
        if valore not in TEMI:
            raise ValueError("tema sconosciuto, sono previsti: %s" % ", ".join(TEMI))
        return valore

    @field_validator("sfondo")
    @classmethod
    def _sfondo(cls, valore: str) -> str:
        if valore == "foto":
            raise ValueError(
                "lo sfondo fotografico si sceglie sul tablet: la foto non passa dal server"
            )
        if valore not in SFONDI:
            raise ValueError("sfondo sconosciuto, sono previsti: %s" % ", ".join(SFONDI))
        return valore

    @field_validator("schermata")
    @classmethod
    def _schermata(cls, valore: str) -> str:
        if valore not in SCHERMATE:
            raise ValueError("schermata sconosciuta, sono previste: %s" % ", ".join(SCHERMATE))
        return valore

    @field_validator("orientamento")
    @classmethod
    def _orientamento(cls, valore: str) -> str:
        if valore not in ORIENTAMENTI:
            raise ValueError(
                "orientamento sconosciuto, sono previsti: %s" % ", ".join(ORIENTAMENTI)
            )
        return valore

    @field_validator("regole_app")
    @classmethod
    def _regole(cls, valore: list[RegolaApp]) -> list[RegolaApp]:
        if len(valore) > MASSIMO_APP:
            raise ValueError("troppe regole, al massimo %d" % MASSIMO_APP)
        nomi = [regola.pacchetto for regola in valore]
        if len(set(nomi)) != len(nomi):
            # Due regole per la stessa app non si fondono: una delle due
            # vincerebbe in silenzio, e quale dipende dall'ordine.
            raise ValueError("c'e un'app con due regole")
        # Non si controlla che l'app sia fra le permesse: una regola per
        # un'app tolta per qualche giorno resta ferma e torna buona quando
        # l'app viene rimessa. Intanto non apre niente - l'app e bloccata
        # comunque perche non e permessa.
        return sorted(valore, key=lambda regola: regola.pacchetto)

    @field_validator("app_permesse")
    @classmethod
    def _app(cls, valore: list[str]) -> list[str]:
        if len(valore) > MASSIMO_APP:
            raise ValueError("troppe app, al massimo %d" % MASSIMO_APP)
        pulite: list[str] = []
        for voce in valore:
            nome = voce.strip()
            # Solo nomi di pacchetto: un nome di pacchetto non puo essere un
            # indirizzo, un percorso o un comando. E il modo in cui il
            # documento resta incapace di dire "esegui questa cosa".
            if len(nome) > LUNGHEZZA_PACCHETTO or not PACCHETTO.fullmatch(nome):
                raise ValueError(
                    "%r non e un nome di pacchetto Android (esempio: com.esempio.gioco)" % voce
                )
            pulite.append(nome)
        if len(set(pulite)) != len(pulite):
            raise ValueError("c'e un'app ripetuta")
        # Ordinate: due documenti con le stesse app sono lo stesso documento, e
        # lo storico resta leggibile.
        return sorted(pulite)

    @field_validator("giochi_spenti")
    @classmethod
    def _giochi(cls, valore: list[str]) -> list[str]:
        for voce in valore:
            if voce not in GIOCHI:
                raise ValueError(
                    "gioco sconosciuto %r, sono previsti: %s" % (voce, ", ".join(GIOCHI))
                )
        if len(set(valore)) != len(valore):
            raise ValueError("c'e un gioco ripetuto")
        return sorted(valore)

    @model_validator(mode="after")
    def _sfondo_del_tema(self) -> "DocumentoConfig":
        ammessi = SFONDI_PER_TEMA.get(self.tema, frozenset())
        if self.sfondo not in ammessi:
            raise ValueError(
                "lo sfondo %r non appartiene al tema %r, che ha: %s"
                % (self.sfondo, self.tema, ", ".join(sorted(ammessi)))
            )
        return self

    def valori(self) -> dict[str, Any]:
        """Il documento senza la revisione, nell'ordine dello schema.

        La revisione non si salva dentro il documento: e la colonna della
        riga. Un numero scritto in due posti e un numero che prima o poi
        racconta due storie diverse.
        """
        completo = self.model_dump()
        completo.pop("revisione", None)
        return {chiave: completo[chiave] for chiave in _PROP if chiave in completo}


# ---------------------------------------------------------------------------
# Lato tablet
# ---------------------------------------------------------------------------


class RichiestaArruolamento(Richiesta):
    token_arruolamento: str = Field(min_length=16, max_length=200)

    @field_validator("token_arruolamento")
    @classmethod
    def _token(cls, valore: str) -> str:
        if not re.fullmatch(r"[A-Za-z0-9_\-]{16,200}", valore):
            raise ValueError("il token di arruolamento non ha la forma giusta")
        return valore


# I motivi che un tablet puo dare. Vocabolario chiuso, come "campo".
#
# Prima era una stringa libera di 200 caratteri, con il commento che chi
# rubasse il token di un tablet non si sarebbe ritrovato a scrivere nella
# pagina del genitore. Non era vero: 200 caratteri per campo e fino a 40
# campi fanno ottomila caratteri di testo suo in quella pagina. Non e XSS -
# la pagina usa textContent e le parentesi angolari erano rifiutate - e
# phishing: "Aggiornamento necessario, apri questo indirizzo e inserisci la
# password", firmato dal tablet del figlio.
#
# I due lati sono codice nostro, quindi non serve testo libero: il tablet
# manda un'etichetta, il testo per le persone sta nella web UI (come si fa
# gia per le spie in web/app.js).
MOTIVI_RIFIUTO: tuple[str, ...] = (
    "serve_device_owner",       # la politica richiede device owner, qui non c'e
    "serve_profile_owner",      # idem per il profilo
    "serve_conferma_pin",       # il kiosk acceso da qui va confermato sul tablet
    "permesso_mancante",        # manca un permesso che l'utente deve concedere
    "non_supportato",           # la versione di Android non ha questa funzione
    "app_non_installata",       # un pacchetto dell'elenco non c'e sul tablet
    "valore_non_applicabile",   # il valore e valido ma qui non ha effetto
    "errore_di_sistema",        # l'API ha risposto male
    "sconosciuto",              # ultima spiaggia: meglio questo di una bugia
)


class CampoRifiutato(Richiesta):
    """Un campo che il tablet non ha potuto applicare, e perche."""

    campo: str = Field(min_length=1, max_length=40)
    motivo: str = Field(default="sconosciuto", max_length=40)

    @field_validator("campo")
    @classmethod
    def _campo(cls, valore: str) -> str:
        pezzi = valore.split(".")
        if len(pezzi) > 2:
            raise ValueError("nome di campo non previsto")
        if pezzi[0] not in CAMPI_DOCUMENTO:
            raise ValueError("il campo %r non esiste nel documento" % valore)
        if len(pezzi) == 2 and (pezzi[0] != "fascia" or pezzi[1] not in CAMPI_FASCIA):
            raise ValueError("il campo %r non esiste nel documento" % valore)
        return valore

    @field_validator("motivo")
    @classmethod
    def _motivo(cls, valore: str) -> str:
        motivo = valore.strip()
        if motivo not in MOTIVI_RIFIUTO:
            raise ValueError(
                "motivo non previsto: usa uno fra %s" % ", ".join(MOTIVI_RIFIUTO)
            )
        return motivo


class RichiestaAck(Richiesta):
    revisione: int = Field(ge=1)
    esito: Literal["applied", "rejected", "partial"]
    campi_rifiutati: list[CampoRifiutato] = Field(default_factory=list, max_length=40)

    @model_validator(mode="after")
    def _coerenza(self) -> "RichiestaAck":
        if self.esito == "applied" and self.campi_rifiutati:
            raise ValueError("un esito 'applied' non puo elencare campi rifiutati")
        if self.esito in ("rejected", "partial") and not self.campi_rifiutati:
            raise ValueError("un esito %r deve dire quali campi non ha applicato" % self.esito)
        nomi = [c.campo for c in self.campi_rifiutati]
        if len(set(nomi)) != len(nomi):
            raise ValueError("c'e un campo rifiutato due volte")
        return self


class AppInstallata(Richiesta):
    """Una app che c'e sul tablet: nome del pacchetto e come si chiama.

    Il nome lo decide l'app stessa, quindi e testo che non abbiamo scritto
    noi. Per i motivi dei campi rifiutati il testo libero dal dispositivo e
    stato rifiutato del tutto; qui non si puo - "com.google.android.youtube"
    non e un'etichetta da mettere davanti a una persona - e allora si
    stringe: niente caratteri di controllo e sessanta caratteri, che non
    bastano a scriverci una frase che convinca qualcuno a fare qualcosa. La
    pagina del genitore mostra sempre anche il nome del pacchetto accanto,
    cosi e chiaro di che app si parla.
    """

    pacchetto: str
    nome: str = ""
    # In entrata: base64 e basta. In uscita da questo validatore: un data URL
    # gia fatto, col tipo che ha deciso il server. Cosi la pagina del
    # genitore non deve ne fidarsi ne indovinare.
    icona: str = ""

    @field_validator("pacchetto")
    @classmethod
    def _pacchetto(cls, valore: str) -> str:
        return _pacchetto_valido(valore)

    @field_validator("icona")
    @classmethod
    def _icona(cls, valore: str) -> str:
        return _icona_valida(valore)

    @field_validator("nome")
    @classmethod
    def _nome(cls, valore: str) -> str:
        return _CONTROLLO.sub("", valore).strip()[:LUNGHEZZA_ETICHETTA]


class RichiestaAppInstallate(Richiesta):
    """L'inventario che manda il tablet.

    Arriva tutto insieme e sostituisce il precedente: un inventario a pezzi
    non e un inventario. Il server non ne fa niente da solo - non tocca
    app_permesse, non decide niente - lo tiene da parte perche la pagina del
    genitore possa mostrare un elenco da spuntare. La configurazione continua
    ad andare in una direzione sola.
    """

    app: list[AppInstallata] = Field(default_factory=list)

    @field_validator("app")
    @classmethod
    def _app(cls, valore: list[AppInstallata]) -> list[AppInstallata]:
        if len(valore) > MASSIMO_INVENTARIO:
            raise ValueError("troppe app, al massimo %d" % MASSIMO_INVENTARIO)
        # Il tetto delle icone e sulla somma, non sulla singola: quattrocento
        # icone da diciotto kilobyte sarebbero sette megabyte in una riga.
        if sum(len(voce.icona) for voce in valore) > ICONE_BASE64_TOTALI:
            raise ValueError("le icone pesano troppo tutte insieme")
        visti: set[str] = set()
        pulite: list[AppInstallata] = []
        for voce in valore:
            # Un pacchetto ripetuto non e un errore del tablet che valga un
            # 422: sono due activity della stessa app. Si tiene la prima.
            if voce.pacchetto in visti:
                continue
            visti.add(voce.pacchetto)
            pulite.append(voce)
        return sorted(pulite, key=lambda voce: (voce.nome.lower(), voce.pacchetto))


class IconaApp(Richiesta):
    """L'icona di una app gia nell'inventario."""

    pacchetto: str
    icona: str = ""

    # Scritti per esteso, non ereditati da AppInstallata: vedi la nota su
    # _pacchetto_valido. Un validatore copiato da un altro modello non viene
    # registrato, e il campo resta senza controllo - era successo proprio
    # qui, e si salvava quello che arrivava.
    @field_validator("pacchetto")
    @classmethod
    def _pacchetto(cls, valore: str) -> str:
        return _pacchetto_valido(valore)

    @field_validator("icona")
    @classmethod
    def _icona(cls, valore: str) -> str:
        return _icona_valida(valore)


class RichiestaIcone(Richiesta):
    """Un pacchetto di icone, non tutte insieme.

    Le icone non viaggiano dentro l'inventario perche non ci starebbero: il
    corpo di una richiesta e limitato a 64 KB - l'API scambia JSON piccoli, e
    quel limite protegge tutto il resto - e venticinque icone ne fanno un
    centinaio. Quindi l'elenco arriva senza, e le icone dopo, a gruppi che
    stanno nel limite. Il tablet decide quanti alla volta guardando i byte,
    non il numero: un'icona grande e otto icone piccole pesano uguale.

    Arrivano solo per app gia nell'inventario: questa rotta non ne aggiunge.
    """

    icone: list[IconaApp] = Field(default_factory=list)

    @field_validator("icone")
    @classmethod
    def _icone(cls, valore: list[IconaApp]) -> list[IconaApp]:
        if len(valore) > MASSIMO_INVENTARIO:
            raise ValueError("troppe icone in una volta")
        return valore


class RichiestaSalute(Richiesta):
    """Le spie del tablet: cinque permessi, nessun dato del bambino."""

    device_owner: bool
    usage_access: bool
    overlay: bool
    guard_running: bool
    default_home: bool


# ---------------------------------------------------------------------------
# Lato web
# ---------------------------------------------------------------------------


class RichiestaLogin(Richiesta):
    # Nome utente, non email: non c'e nessuna posta da verificare, nessun
    # recupero password da mandare e nessun indirizzo da tenere nel database.
    utente: str = Field(min_length=1, max_length=64)
    # Il massimo non e una stima: una password lunghissima e lavoro di scrypt
    # regalato a chi la manda, e si paga in RAM su una macchina che ne ha poca.
    password: str = Field(min_length=1, max_length=256)

    @field_validator("utente")
    @classmethod
    def _utente(cls, valore: str) -> str:
        return _senza_controlli(valore.strip(), "utente")


class RichiestaNuovoTablet(Richiesta):
    # Il nome del tablet, non quello del bambino: "quello di camera", "il
    # vecchio Samsung". Il server non ha un campo per il nome del bambino.
    nome: str = Field(min_length=1, max_length=60)

    @field_validator("nome")
    @classmethod
    def _nome(cls, valore: str) -> str:
        nome = _senza_controlli(valore.strip(), "nome")
        if not nome:
            raise ValueError("il nome non puo essere vuoto")
        return nome


class RichiestaRipristino(Richiesta):
    revisione: int = Field(ge=1)


# ---------------------------------------------------------------------------
# Utenti e ruoli
# ---------------------------------------------------------------------------

RUOLI: tuple[str, ...] = ("admin", "genitore", "osservatore")


class RichiestaNuovoUtente(Richiesta):
    """Un account creato da un amministratore che e gia dentro.

    Non e iscrizione: non esiste nessuna rotta che si possa chiamare senza
    credenziali per aggiungere un account. La proprieta che la pagina di
    accesso promette - "da fuori nessuno puo farsi un account" - resta vera
    alla lettera.
    """

    utente: str = Field(min_length=1, max_length=64)
    # Lo stesso minimo che pretende auth.crea_utente: qui si rifiuta prima,
    # con il nome del campo sbagliato, invece di farlo scoppiare dentro.
    password: str = Field(min_length=12, max_length=1024)
    ruolo: Literal["admin", "genitore", "osservatore"] = "genitore"
    # Il sottoutente: di chi e figlio. Lo controlla la rotta, perche la
    # regola ("solo i tuoi, e solo sui tuoi tablet") dipende da chi chiede.
    id_padre: int | None = Field(default=None, ge=1)

    @field_validator("utente")
    @classmethod
    def _utente(cls, valore: str) -> str:
        nome = _senza_controlli(valore.strip(), "utente")
        if not nome:
            raise ValueError("il nome utente non puo essere vuoto")
        if "/" in nome or "\\" in nome or " " in nome:
            raise ValueError("il nome utente non puo contenere spazi ne barre")
        return nome


class RichiestaModificaUtente(Richiesta):
    """Cosa si puo cambiare di un account da HTTP: il ruolo e se e acceso.

    Il nome no, di proposito: un nome che cambia sotto i piedi rende
    illeggibili i log e il registro delle revisioni. Si disattiva e se ne fa
    un altro.
    """

    ruolo: Literal["admin", "genitore", "osservatore"] | None = None
    attivo: bool | None = None


class RichiestaPassword(Richiesta):
    password: str = Field(min_length=12, max_length=1024)


class RichiestaConcessioni(Richiesta):
    """Quali tablet vede un utente. L'elenco arriva intero, non a pezzi.

    Come per la configurazione: niente aggiunte e rimozioni separate, perche
    due richieste che si incrociano lascerebbero permessi che nessuno ha
    chiesto. Si manda l'elenco che deve valere.
    """

    tablet: list[str] = Field(default_factory=list, max_length=200)

    @field_validator("tablet")
    @classmethod
    def _tablet(cls, valore: list[str]) -> list[str]:
        for id_tablet in valore:
            if not re.fullmatch(r"[0-9a-f]{16}", id_tablet):
                raise ValueError("identificativo di tablet non valido: %r" % id_tablet)
        if len(set(valore)) != len(valore):
            raise ValueError("c'e un tablet ripetuto")
        return valore
