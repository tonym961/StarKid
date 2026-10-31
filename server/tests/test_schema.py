"""Il vocabolario del documento.

Qui si prova che lo schema e chiuso **nei valori** e non solo nei nomi: uno
sfondo che non appartiene al tema, un gioco che non esiste, un nome di
pacchetto che e in realta un comando. Il documento non deve poter dire niente
che il tablet non sappia gia fare.
"""

from __future__ import annotations

from typing import Any

import pytest
from fastapi.testclient import TestClient

from app.models import (
    CAMPI_DOCUMENTO,
    GIOCHI,
    SFONDI_PER_TEMA,
    VALORI_INIZIALI,
    DocumentoConfig,
)
from tests.conftest import documento_base


def scrivi(genitore: TestClient, tablet: dict[str, Any], **modifiche: Any):  # type: ignore[no-untyped-def]
    return genitore.put(
        "/api/v1/tablets/%s/config" % tablet["id"], json=documento_base(**modifiche)
    )


# ---------------------------------------------------------------------------
# Lo sfondo appartiene al tema
# ---------------------------------------------------------------------------


def test_ogni_sfondo_del_proprio_tema_va_bene(
    genitore: TestClient, tablet: dict[str, Any]
) -> None:
    for tema, sfondi in SFONDI_PER_TEMA.items():
        for sfondo in sorted(sfondi):
            risposta = scrivi(genitore, tablet, tema=tema, sfondo=sfondo)
            assert risposta.status_code == 200, risposta.text
            assert risposta.json()["sfondo"] == sfondo


def test_sfondo_di_un_altro_tema_da_422(genitore: TestClient, tablet: dict[str, Any]) -> None:
    risposta = scrivi(genitore, tablet, tema="pulito", sfondo="notte")
    assert risposta.status_code == 422, risposta.text
    assert "tema" in risposta.text

    rovescio = scrivi(genitore, tablet, tema="spaziale", sfondo="lilla")
    assert rovescio.status_code == 422, rovescio.text


def test_sfondo_foto_da_422(genitore: TestClient, tablet: dict[str, Any]) -> None:
    """La foto sta solo sul tablet: il server non deve poterla nominare."""
    risposta = scrivi(genitore, tablet, sfondo="foto")
    assert risposta.status_code == 422, risposta.text
    assert "tablet" in risposta.text.lower()


def test_sfondo_e_tema_inventati(genitore: TestClient, tablet: dict[str, Any]) -> None:
    assert scrivi(genitore, tablet, sfondo="arcobaleno").status_code == 422
    assert scrivi(genitore, tablet, tema="scurissimo", sfondo="notte").status_code == 422


# ---------------------------------------------------------------------------
# Niente "esegui"
# ---------------------------------------------------------------------------


@pytest.mark.parametrize(
    "pacchetto",
    [
        "https://esempio.it/carico.apk",
        "/data/local/tmp/cosa.sh",
        "com.esempio.gioco; rm -rf /",
        "com.esempio.gioco && reboot",
        "../../etc/passwd",
        "com esempio gioco",
        "com.",
        "unaparolasola",
        "$(id)",
    ],
)
def test_app_permesse_accetta_solo_nomi_di_pacchetto(
    genitore: TestClient, tablet: dict[str, Any], pacchetto: str
) -> None:
    risposta = scrivi(genitore, tablet, app_permesse=[pacchetto])
    assert risposta.status_code == 422, risposta.text


def test_app_permesse_buone_e_ordinate(genitore: TestClient, tablet: dict[str, Any]) -> None:
    risposta = scrivi(
        genitore,
        tablet,
        app_permesse=["com.zeta.disegno", "com.alfa.video", "it.esempio.gioco_2"],
    )
    assert risposta.status_code == 200, risposta.text
    assert risposta.json()["app_permesse"] == [
        "com.alfa.video",
        "com.zeta.disegno",
        "it.esempio.gioco_2",
    ]


def test_app_ripetuta(genitore: TestClient, tablet: dict[str, Any]) -> None:
    risposta = scrivi(genitore, tablet, app_permesse=["com.a.b", "com.a.b"])
    assert risposta.status_code == 422, risposta.text


def test_troppe_app(genitore: TestClient, tablet: dict[str, Any]) -> None:
    troppe = ["com.esempio.app%d" % n for n in range(300)]
    risposta = scrivi(genitore, tablet, app_permesse=troppe)
    assert risposta.status_code == 422, risposta.text


# ---------------------------------------------------------------------------
# Giochi, limiti, fascia
# ---------------------------------------------------------------------------


def test_giochi_spenti_solo_quelli_che_esistono(
    genitore: TestClient, tablet: dict[str, Any]
) -> None:
    buono = scrivi(genitore, tablet, giochi_spenti=["xilofono", "macchinine"])
    assert buono.status_code == 200, buono.text
    assert buono.json()["giochi_spenti"] == ["macchinine", "xilofono"]

    assert scrivi(genitore, tablet, giochi_spenti=["tetris"]).status_code == 422
    assert scrivi(genitore, tablet, giochi_spenti=["xilofono", "xilofono"]).status_code == 422


def test_i_giochi_sono_quelli_del_tablet() -> None:
    """Se qualcuno aggiunge un gioco all'app, lo schema del server va aggiornato."""
    assert "macchinine" in GIOCHI
    assert "xilofono" in GIOCHI
    assert len(GIOCHI) == len(set(GIOCHI))


@pytest.mark.parametrize("limite", [-1, 1441, 100000])
def test_limite_giornaliero_fuori_scala(
    genitore: TestClient, tablet: dict[str, Any], limite: int
) -> None:
    assert scrivi(genitore, tablet, limite_giornaliero=limite).status_code == 422


def test_limite_giornaliero_zero_vuol_dire_senza_limite(
    genitore: TestClient, tablet: dict[str, Any]
) -> None:
    risposta = scrivi(genitore, tablet, limite_giornaliero=0)
    assert risposta.status_code == 200, risposta.text
    assert risposta.json()["limite_giornaliero"] == 0


@pytest.mark.parametrize(
    "fascia",
    [
        {"attiva": True, "inizio": -1, "fine": 1200, "giorni": [1]},
        {"attiva": True, "inizio": 480, "fine": 1440, "giorni": [1]},
        {"attiva": True, "inizio": 480, "fine": 1200, "giorni": []},
        {"attiva": True, "inizio": 480, "fine": 1200, "giorni": [0]},
        {"attiva": True, "inizio": 480, "fine": 1200, "giorni": [8]},
        {"attiva": True, "inizio": 480, "fine": 1200, "giorni": [2, 2]},
        {"attiva": True, "inizio": 480, "giorni": [2]},
    ],
)
def test_fascia_sbagliata(
    genitore: TestClient, tablet: dict[str, Any], fascia: dict[str, Any]
) -> None:
    assert scrivi(genitore, tablet, fascia=fascia).status_code == 422


def test_fascia_che_scavalca_la_mezzanotte_va_bene(
    genitore: TestClient, tablet: dict[str, Any]
) -> None:
    """Il tablet la gestisce (Guard.inWindow): il server non sia piu severo di lui."""
    risposta = scrivi(
        genitore, tablet, fascia={"attiva": True, "inizio": 1320, "fine": 420, "giorni": [6, 7]}
    )
    assert risposta.status_code == 200, risposta.text
    assert risposta.json()["fascia"]["giorni"] == [6, 7]


# ---------------------------------------------------------------------------
# Lo schema sul disco e i modelli dicono la stessa cosa
# ---------------------------------------------------------------------------


def test_i_campi_del_modello_sono_quelli_dello_schema() -> None:
    campi_modello = set(DocumentoConfig.model_fields) - {"revisione"}
    assert campi_modello == CAMPI_DOCUMENTO


def test_i_valori_iniziali_sono_un_documento_valido() -> None:
    documento = DocumentoConfig.model_validate(VALORI_INIZIALI)
    assert documento.sfondo in SFONDI_PER_TEMA[documento.tema]


def test_ogni_tema_ha_i_suoi_sfondi() -> None:
    assert set(SFONDI_PER_TEMA) == {"pulito", "spaziale"}
    assert "foto" not in {s for sfondi in SFONDI_PER_TEMA.values() for s in sfondi}


# ---------------------------------------------------------------------------
# Revisione: chi scrive dice da dove e partito
# ---------------------------------------------------------------------------


def test_revisione_vecchia_da_409(genitore: TestClient, tablet: dict[str, Any]) -> None:
    primo = scrivi(genitore, tablet, revisione=1, suoni=False)
    assert primo.status_code == 200, primo.text
    assert primo.json()["revisione"] == 2

    tardivo = scrivi(genitore, tablet, revisione=1, suoni=True)
    assert tardivo.status_code == 409, tardivo.text
    assert tardivo.json()["revisione_corrente"] == 2


def test_senza_revisione_si_scrive_e_basta(
    genitore: TestClient, tablet: dict[str, Any]
) -> None:
    risposta = scrivi(genitore, tablet, suoni=False)
    assert risposta.status_code == 200, risposta.text
    assert risposta.json()["revisione"] == 2


# ---------------------------------------------------------------------------
# La forma delle risposte, che la web UI legge
# ---------------------------------------------------------------------------


def test_il_422_dice_quale_campo_e_sbagliato(
    genitore: TestClient, tablet: dict[str, Any]
) -> None:
    """La pagina accende il campo sbagliato nel modulo: le serve il nome."""
    risposta = scrivi(genitore, tablet, limite_giornaliero=9999)
    assert risposta.status_code == 422
    corpo = risposta.json()
    assert corpo["errore"]
    assert corpo["dettagli"] == corpo["campi"]
    assert corpo["campi"][0]["campo"] == "limite_giornaliero"
    assert corpo["campi"][0]["motivo"]
    # Il 422 non rimanda indietro quello che e stato mandato.
    assert "9999" not in risposta.text


def test_l_elenco_sta_sotto_la_chiave_tablets(genitore: TestClient, tablet: dict[str, Any]) -> None:
    corpo = genitore.get("/api/v1/tablets").json()
    assert list(corpo) == ["tablets"]
    assert corpo["tablets"][0]["id"] == tablet["id"]
    for spia in (
        "salute_dichiarata",
        "kiosk",
        "revisione_confermata",
        "campi_rifiutati",
        "allineato",
    ):
        assert spia in corpo["tablets"][0]


def test_le_spie_si_chiamano_dichiarate(genitore: TestClient, tablet: dict[str, Any]) -> None:
    """Il nome del campo deve dire che e il tablet a dirlo, non il server.

    Le spie e l'esito dell'ack sono autodichiarazioni: il server non ha modo
    di verificarle, e chi avesse il token di un tablet puo mandarle tutte
    verdi piu spesso del tablet vero, sovrascrivendolo. Un campo che si chiama
    "salute" invita chi legge a presentarlo come un fatto.
    """
    voce = genitore.get("/api/v1/tablets").json()["tablets"][0]
    assert "salute" not in voce
    assert "salute_dichiarata" in voce
