"""Le regole per singola app: quanto, e quando.

Il tetto giornaliero e la fascia generale dicono quanto si sta sul tablet e
quando lo si accende. Queste regole fanno le stesse due domande per una app
sola, e devono *aggiungersi* alle generali, mai scavalcarle: il controllo di
quella parte sta sul tablet (GuardTest), qui si prova che il documento sappia
dirle e sappia rifiutare quelle storte.
"""

from __future__ import annotations

from typing import Any

import pytest
from fastapi.testclient import TestClient

from app.models import DocumentoConfig
from tests.conftest import documento_base

VIDEO = "com.esempio.video"
MUSICA = "com.esempio.musica"


def regola(pacchetto: str = VIDEO, **modifiche: Any) -> dict[str, Any]:
    voce: dict[str, Any] = {"pacchetto": pacchetto}
    voce.update(modifiche)
    return voce


def fascia(inizio: int = 16 * 60, fine: int = 18 * 60, giorni: list[int] | None = None):
    return {
        "attiva": True,
        "inizio": inizio,
        "fine": fine,
        "giorni": [1, 2, 3, 4, 5, 6, 7] if giorni is None else giorni,
    }


def scrivi(genitore: TestClient, tablet: dict[str, Any], regole: list[dict[str, Any]]):  # type: ignore[no-untyped-def]
    return genitore.put(
        "/api/v1/tablets/%s/config" % tablet["id"],
        json=documento_base(app_permesse=[VIDEO, MUSICA], regole_app=regole),
    )


# --- il campo esiste e ha un valore iniziale vuoto -------------------------


def test_un_tablet_nuovo_non_ha_regole() -> None:
    """Nessuna regola e il caso normale: si stringe solo quando serve."""
    assert documento_base()["regole_app"] == []


def test_il_campo_fa_parte_del_documento() -> None:
    documento = DocumentoConfig.model_validate(documento_base())
    assert documento.valori()["regole_app"] == []


# --- quanto ----------------------------------------------------------------


def test_minuti_per_una_app(genitore: TestClient, tablet: dict[str, Any]) -> None:
    risposta = scrivi(genitore, tablet, [regola(minuti=30)])
    assert risposta.status_code == 200
    salvate = risposta.json()["regole_app"]
    assert salvate == [{"pacchetto": VIDEO, "minuti": 30, "fascia": None}]


def test_zero_minuti_e_ammesso(genitore: TestClient, tablet: dict[str, Any]) -> None:
    """Zero vuol dire "nessun tetto suo", non "zero minuti": serve per
    tenere una regola fatta solo di fascia."""
    risposta = scrivi(genitore, tablet, [regola(minuti=0, fascia=fascia())])
    assert risposta.status_code == 200


def test_minuti_negativi_rifiutati(genitore: TestClient, tablet: dict[str, Any]) -> None:
    assert scrivi(genitore, tablet, [regola(minuti=-1)]).status_code == 422


def test_minuti_oltre_la_giornata_rifiutati(
    genitore: TestClient, tablet: dict[str, Any]
) -> None:
    assert scrivi(genitore, tablet, [regola(minuti=1441)]).status_code == 422


# --- quando ----------------------------------------------------------------


def test_fascia_per_una_app(genitore: TestClient, tablet: dict[str, Any]) -> None:
    risposta = scrivi(genitore, tablet, [regola(fascia=fascia(giorni=[2, 3, 4, 5, 6]))])
    assert risposta.status_code == 200
    salvata = risposta.json()["regole_app"][0]["fascia"]
    assert salvata["inizio"] == 16 * 60
    assert salvata["fine"] == 18 * 60
    assert salvata["giorni"] == [2, 3, 4, 5, 6]


def test_la_fascia_di_una_app_puo_scavalcare_la_mezzanotte(
    genitore: TestClient, tablet: dict[str, Any]
) -> None:
    """Fine prima di inizio non e un errore: il tablet sa gestirla, e per una
    app sola - un video prima di dormire - e una richiesta normale."""
    risposta = scrivi(genitore, tablet, [regola(fascia=fascia(22 * 60, 7 * 60))])
    assert risposta.status_code == 200


def test_fascia_senza_giorni_rifiutata(
    genitore: TestClient, tablet: dict[str, Any]
) -> None:
    assert scrivi(genitore, tablet, [regola(fascia=fascia(giorni=[]))]).status_code == 422


def test_giorno_fuori_scala_rifiutato(
    genitore: TestClient, tablet: dict[str, Any]
) -> None:
    assert scrivi(genitore, tablet, [regola(fascia=fascia(giorni=[0]))]).status_code == 422
    assert scrivi(genitore, tablet, [regola(fascia=fascia(giorni=[8]))]).status_code == 422


def test_orario_fuori_giornata_rifiutato(
    genitore: TestClient, tablet: dict[str, Any]
) -> None:
    assert scrivi(genitore, tablet, [regola(fascia=fascia(inizio=1440))]).status_code == 422


# --- il pacchetto ----------------------------------------------------------


@pytest.mark.parametrize(
    "nome",
    [
        "https://esempio.it/carico",
        "/data/local/tmp/cosa",
        "rm -rf /",
        "com esempio video",
        "video",
    ],
)
def test_solo_nomi_di_pacchetto(
    genitore: TestClient, tablet: dict[str, Any], nome: str
) -> None:
    """Lo stesso filtro di app_permesse, per la stessa ragione: il documento
    non deve poter dire "esegui questa cosa"."""
    assert scrivi(genitore, tablet, [regola(nome, minuti=10)]).status_code == 422


def test_due_regole_per_la_stessa_app_rifiutate(
    genitore: TestClient, tablet: dict[str, Any]
) -> None:
    """Fondendole, una delle due vincerebbe in silenzio, e quale dipende
    dall'ordine in cui sono arrivate."""
    risposta = scrivi(genitore, tablet, [regola(minuti=10), regola(minuti=20)])
    assert risposta.status_code == 422


def test_le_regole_escono_in_ordine(genitore: TestClient, tablet: dict[str, Any]) -> None:
    """Due documenti con le stesse regole sono lo stesso documento, e lo
    storico resta leggibile."""
    risposta = scrivi(genitore, tablet, [regola(MUSICA, minuti=10), regola(VIDEO, minuti=20)])
    assert risposta.status_code == 200
    assert [r["pacchetto"] for r in risposta.json()["regole_app"]] == [MUSICA, VIDEO]


def test_regola_per_un_app_non_permessa_si_tiene(
    genitore: TestClient, tablet: dict[str, Any]
) -> None:
    """Un'app tolta per qualche giorno non deve far perdere il lavoro fatto.
    Intanto non apre niente: e bloccata perche non e fra le permesse."""
    risposta = genitore.put(
        "/api/v1/tablets/%s/config" % tablet["id"],
        json=documento_base(app_permesse=[], regole_app=[regola(minuti=30)]),
    )
    assert risposta.status_code == 200
    assert risposta.json()["regole_app"][0]["pacchetto"] == VIDEO


# --- il vocabolario resta chiuso -------------------------------------------


def test_campo_in_piu_dentro_una_regola_rifiutato(
    genitore: TestClient, tablet: dict[str, Any]
) -> None:
    assert scrivi(genitore, tablet, [regola(minuti=10, priorita=1)]).status_code == 422


def test_campo_in_piu_dentro_la_fascia_rifiutato(
    genitore: TestClient, tablet: dict[str, Any]
) -> None:
    storta = fascia()
    storta["eccezione"] = True
    assert scrivi(genitore, tablet, [regola(fascia=storta)]).status_code == 422


def test_regola_senza_pacchetto_rifiutata(
    genitore: TestClient, tablet: dict[str, Any]
) -> None:
    risposta = genitore.put(
        "/api/v1/tablets/%s/config" % tablet["id"],
        json=documento_base(regole_app=[{"minuti": 10}]),
    )
    assert risposta.status_code == 422
