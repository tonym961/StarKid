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
    "pin": "il PIN dell'area genitori non lascia il tablet",
    "pin_hash": "il PIN dell'area genitori non lascia il tablet",
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
    limite_giornaliero: int = Field(ge=0, le=LIMITE_MASSIMO)
    fascia: Fascia
    app_permesse: list[str]
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
