"""Il documento di configurazione: lettura, scrittura, storico.

Tre scelte che vale la pena spiegare.

**Una direzione sola.** Il server scrive la configurazione, il tablet la
legge. Il tablet non modifica niente: risponde soltanto "l'ho applicata" o
"questo campo no, e perche". Percio qui non esiste nessuna funzione che prenda
un valore dal dispositivo e lo scriva nel documento.

**Lo storico e potato a dieci revisioni.** Non per risparmiare disco (dieci
documenti sono qualche kilobyte) ma perche uno storico infinito e un archivio
di come cambiava la giornata di un bambino. Dieci passi indietro sono quelli
che servono per disfare un errore.

**Il ripristino aggiunge una revisione, non la toglie.** Tornare alla 4 scrive
la 12 con il contenuto della 4. Cosi il numero di revisione cresce sempre, il
tablet capisce da solo che c'e qualcosa di nuovo e lo storico resta un
registro e non un posto dove le cose scompaiono.

**La lettura e indulgente, l'ingresso e severo.** Un documento che arriva da
fuori viene rifiutato se ha un campo in piu o in meno (422). Un documento che
rileggiamo dal nostro database viene completato con i valori iniziali se lo
schema e cresciuto, e sfoltito se un campo e stato tolto: dei nostri dati
vecchi ci si fida quanto basta per restare in piedi dopo un aggiornamento.
"""

from __future__ import annotations

import json
import logging
import sqlite3
from typing import Any

from . import db
from .models import CAMPI_DOCUMENTO, VALORI_INIZIALI, DocumentoConfig

registro = logging.getLogger("starkid.config")

MASSIMO_STORICO = 10

ORIGINI = ("iniziale", "modifica", "ripristino")


class ConflittoRevisione(Exception):
    """La revisione su cui si stava lavorando non e piu quella corrente."""

    def __init__(self, attesa: int, corrente: int) -> None:
        super().__init__("revisione %s, corrente %s" % (attesa, corrente))
        self.attesa = attesa
        self.corrente = corrente


class DocumentoNonValido(Exception):
    """Il documento salvato non supera piu la validazione."""


class RevisioneInesistente(Exception):
    """La revisione chiesta non c'e (o non c'e piu, potata dallo storico)."""


def _nomi_campi(errore: Exception) -> str:
    """I nomi dei campi che non vanno, senza i valori che ci stavano dentro."""
    elenco = getattr(errore, "errors", None)
    if not callable(elenco):
        return "il documento salvato non e valido"
    try:
        nomi = sorted(
            {
                ".".join(str(p) for p in guasto.get("loc", ()) if p != "body")
                or "(radice)"
                for guasto in elenco()
            }
        )
    except Exception:  # pragma: no cover - dipende dalla versione di pydantic
        return "il documento salvato non e valido"
    if not nomi:
        return "il documento salvato non e valido"
    return "campi non validi nel documento salvato: " + ", ".join(nomi)


def valori_iniziali() -> dict[str, Any]:
    """I valori di un tablet appena creato, presi dai default dello schema."""
    documento = json.loads(json.dumps(VALORI_INIZIALI))
    # Validati anche questi: un default sbagliato nello schema deve farsi
    # sentire alla prima creazione, non il giorno in cui il tablet lo legge.
    return DocumentoConfig.model_validate(documento).valori()


def _normalizza(salvato: dict[str, Any]) -> dict[str, Any]:
    """Completa e sfoltisce un documento che arriva dal nostro database."""
    pulito: dict[str, Any] = {}
    for campo in CAMPI_DOCUMENTO:
        if campo in salvato:
            pulito[campo] = salvato[campo]
        else:
            registro.warning("documento salvato senza il campo %s: uso il valore iniziale", campo)
            pulito[campo] = json.loads(json.dumps(VALORI_INIZIALI[campo]))
    for campo in salvato:
        if campo not in CAMPI_DOCUMENTO and campo != "revisione":
            registro.warning("documento salvato con il campo %s, che non esiste piu: lo lascio", campo)
    return pulito


def _documento(riga: sqlite3.Row) -> dict[str, Any]:
    try:
        salvato = json.loads(riga["documento"])
    except (ValueError, TypeError) as errore:
        raise DocumentoNonValido("il documento salvato non e JSON") from errore
    if not isinstance(salvato, dict):
        raise DocumentoNonValido("il documento salvato non e un oggetto")
    valori = _normalizza(salvato)
    try:
        validato = DocumentoConfig.model_validate(valori)
    except Exception as errore:  # pydantic.ValidationError e tutto cio che ne deriva
        # Solo i NOMI dei campi, non il messaggio di pydantic: quello in
        # pydantic 2 porta con se "input_value=...", e questo testo finisce in
        # un warning nel journal, che ha una ritenzione diversa dal database.
        # Sono orari e nomi di pacchetto, non segreti, ma e l'unico punto in
        # cui il contenuto della configurazione uscirebbe dal suo file.
        raise DocumentoNonValido(_nomi_campi(errore)) from errore
    # La revisione sta nella colonna, non nel documento: si rimette qui in
    # uscita, che e l'unico posto in cui le due cose si incontrano.
    return {"revisione": int(riga["revisione"]), **validato.valori()}


def salva_prima_revisione(conn: sqlite3.Connection, id_tablet: str) -> dict[str, Any]:
    """Revisione 1 di un tablet appena creato. Dentro la transazione di chi crea."""
    valori = valori_iniziali()
    conn.execute(
        "INSERT INTO revisione (id_tablet, revisione, documento, creato_il, origine) "
        "VALUES (?, 1, ?, ?, 'iniziale')",
        (id_tablet, json.dumps(valori, ensure_ascii=False), db.adesso()),
    )
    return {"revisione": 1, **valori}


def leggi(id_tablet: str) -> dict[str, Any]:
    """Il documento corrente, con la sua revisione."""
    riga = db.leggi_uno(
        "SELECT r.revisione, r.documento FROM revisione r "
        "JOIN tablet t ON t.id = r.id_tablet AND t.revisione = r.revisione "
        "WHERE r.id_tablet = ?",
        (id_tablet,),
    )
    if riga is None:
        raise RevisioneInesistente("questo tablet non ha una configurazione corrente")
    return _documento(riga)


def leggi_revisione(id_tablet: str, revisione: int) -> dict[str, Any]:
    riga = db.leggi_uno(
        "SELECT revisione, documento FROM revisione WHERE id_tablet = ? AND revisione = ?",
        (id_tablet, revisione),
    )
    if riga is None:
        raise RevisioneInesistente(
            "la revisione %d non c'e piu: lo storico tiene le ultime %d"
            % (revisione, MASSIMO_STORICO)
        )
    return _documento(riga)


def _scrivi(
    id_tablet: str, valori: dict[str, Any], origine: str, base: int | None
) -> dict[str, Any]:
    if origine not in ORIGINI:
        raise ValueError("origine sconosciuta: %r" % origine)
    ora = db.adesso()
    with db.scrittura() as conn:
        riga = conn.execute(
            "SELECT revisione FROM tablet WHERE id = ?", (id_tablet,)
        ).fetchone()
        if riga is None:
            raise RevisioneInesistente("questo tablet non esiste")
        corrente = int(riga["revisione"])
        if base is not None and base != corrente:
            raise ConflittoRevisione(base, corrente)
        nuova = corrente + 1
        conn.execute(
            "INSERT INTO revisione (id_tablet, revisione, documento, creato_il, origine) "
            "VALUES (?, ?, ?, ?, ?)",
            (id_tablet, nuova, json.dumps(valori, ensure_ascii=False), ora, origine),
        )
        conn.execute("UPDATE tablet SET revisione = ? WHERE id = ?", (nuova, id_tablet))
        # Potatura: si tengono le ultime MASSIMO_STORICO. Le revisioni crescono
        # di uno, quindi basta una disuguaglianza, senza sottoquery.
        conn.execute(
            "DELETE FROM revisione WHERE id_tablet = ? AND revisione <= ?",
            (id_tablet, nuova - MASSIMO_STORICO),
        )
    return {"revisione": nuova, **valori}


def scrivi(id_tablet: str, documento: DocumentoConfig) -> dict[str, Any]:
    """Nuova revisione a partire da un documento arrivato dalla web UI."""
    return _scrivi(id_tablet, documento.valori(), "modifica", documento.revisione)


def ripristina(id_tablet: str, revisione: int) -> dict[str, Any]:
    """Riscrive il contenuto di una vecchia revisione come revisione nuova."""
    vecchio = leggi_revisione(id_tablet, revisione)
    valori = {chiave: valore for chiave, valore in vecchio.items() if chiave != "revisione"}
    return _scrivi(id_tablet, valori, "ripristino", None)


def storico(id_tablet: str) -> list[dict[str, Any]]:
    """Le ultime revisioni, dalla piu recente. Non piu di MASSIMO_STORICO."""
    righe = db.leggi_tutti(
        "SELECT revisione, documento, creato_il, origine FROM revisione "
        "WHERE id_tablet = ? ORDER BY revisione DESC LIMIT ?",
        (id_tablet, MASSIMO_STORICO),
    )
    elenco: list[dict[str, Any]] = []
    for riga in righe:
        voce: dict[str, Any] = {
            "revisione": int(riga["revisione"]),
            "creato_il": int(riga["creato_il"]),
            "origine": riga["origine"],
        }
        try:
            documento = _documento(riga)
        except DocumentoNonValido as errore:
            # Una revisione vecchia illeggibile non deve far sparire lo
            # storico: si dice che c'e e che non si puo rileggere.
            registro.warning("revisione %s illeggibile: %s", riga["revisione"], errore)
            voce["leggibile"] = False
        else:
            documento.pop("revisione", None)
            voce["documento"] = documento
            voce["leggibile"] = True
        elenco.append(voce)
    return elenco


# ---------------------------------------------------------------------------
# Il kiosk e asimmetrico
# ---------------------------------------------------------------------------


def kiosk_e_una_richiesta(precedente: bool, nuovo: bool) -> bool:
    """Vero quando si sta chiedendo di accendere il kiosk.

    Spegnerlo e un ordine: il tablet obbedisce appena legge la
    configurazione. Accenderlo e solo una richiesta, perche prendere il
    comando di un tablet e una cosa che si conferma stando davanti al tablet,
    col PIN. Questa asimmetria e la ragione per cui un server rubato non
    diventa un modo per chiudere un bambino dentro un dispositivo.
    """
    return bool(nuovo) and not bool(precedente)


def stato_kiosk(tablet: sqlite3.Row, documento: dict[str, Any]) -> str:
    """Come sta il kiosk: spento, attivo, oppure chiesto e non confermato.

    Attenzione a cosa vuol dire "attivo": vuol dire **che il tablet dice** di
    averlo applicato. Lo spegnimento e l'unico lato che il server sa per
    certo, perche basta togliere il campo dal documento; l'accensione la puo
    solo chiedere, e la conferma arriva dal tablet, cioe esattamente da chi
    avrebbe interesse a mentire. Chi lo mostra a una persona deve scriverlo
    (vedi listaSpie in web/app.js).
    """
    if not documento.get("kiosk"):
        return "spento"
    if tablet["ack_revisione"] != documento["revisione"]:
        return "in attesa"
    rifiutati = campi_rifiutati(tablet)
    if any(c.get("campo") == "kiosk" for c in rifiutati):
        return "in attesa"
    if tablet["ack_esito"] == "applied":
        return "attivo"
    return "in attesa"


def campi_rifiutati(tablet: sqlite3.Row) -> list[dict[str, Any]]:
    grezzo = tablet["ack_campi"]
    if not grezzo:
        return []
    try:
        elenco = json.loads(grezzo)
    except (ValueError, TypeError):
        return []
    return elenco if isinstance(elenco, list) else []
