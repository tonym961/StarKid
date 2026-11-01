"""Il PIN dell'area genitori, che si cambia da qui ma non passa da qui.

La decisione vecchia era "il PIN non lascia il tablet", e vale ancora per le
cifre: quelle non arrivano mai al server. Quello che arriva e un'impronta
calcolata nel browser, e il server non sa - e non puo sapere - a quale PIN
corrisponda. Questi test provano le due meta: che il campo nuovo funzioni, e
che la porta vecchia sia rimasta chiusa.

Il limite va detto e non nascosto: quattro cifre dietro a un'impronta si
ritrovano provandole tutte. Questo campo non rende il PIN un segreto per chi
ha il database; impedisce che il PIN ci passi in chiaro e che resti scritto
nel database, nei log e nelle dieci revisioni dello storico.
"""

from __future__ import annotations

from typing import Any

import pytest
from fastapi.testclient import TestClient

from app.models import CAMPI_DOCUMENTO, DocumentoConfig
from tests.conftest import documento_base

SALE = "00112233445566778899aabbccddeeff"
IMPRONTA = "a407c10aee432a9cf759fe4f3b30f2fd560e887dda531cd198ed2c73b1674fc5"


def impronta(**modifiche: Any) -> dict[str, Any]:
    voce: dict[str, Any] = {"sale": SALE, "giri": 20000, "impronta": IMPRONTA}
    voce.update(modifiche)
    return voce


def scrivi(genitore: TestClient, tablet: dict[str, Any], valore: Any):  # type: ignore[no-untyped-def]
    return genitore.put(
        "/api/v1/tablets/%s/config" % tablet["id"],
        json=documento_base(impronta_pin=valore),
    )


# --- il valore iniziale ----------------------------------------------------


def test_un_tablet_nuovo_non_ha_nessun_pin_dal_server() -> None:
    """null vuol dire "da qui non si dice niente": il tablet tiene il suo."""
    assert documento_base()["impronta_pin"] is None


def test_il_campo_fa_parte_del_documento() -> None:
    documento = DocumentoConfig.model_validate(documento_base())
    assert documento.valori()["impronta_pin"] is None
    # Deve stare fra i campi nominabili, se no il tablet non puo dire "questo
    # non l'ho applicato" quando l'impronta e storta.
    assert "impronta_pin" in CAMPI_DOCUMENTO


# --- quello che si accetta -------------------------------------------------


def test_un_impronta_buona_si_salva(genitore: TestClient, tablet: dict[str, Any]) -> None:
    risposta = scrivi(genitore, tablet, impronta())
    assert risposta.status_code == 200, risposta.text
    assert risposta.json()["impronta_pin"] == impronta()


def test_si_puo_tornare_a_non_mandarlo(genitore: TestClient, tablet: dict[str, Any]) -> None:
    assert scrivi(genitore, tablet, impronta()).status_code == 200
    spento = genitore.put(
        "/api/v1/tablets/%s/config" % tablet["id"],
        json=documento_base(impronta_pin=None),
    )
    assert spento.status_code == 200, spento.text
    assert spento.json()["impronta_pin"] is None


def test_il_tablet_riceve_l_impronta(
    genitore: TestClient, dispositivo: dict[str, Any], tablet: dict[str, Any]
) -> None:
    genitore.put(
        "/api/v1/tablets/%s/config" % dispositivo["id"],
        json=documento_base(impronta_pin=impronta()),
    )
    risposta = genitore.get("/api/v1/device/config", headers=dispositivo["intestazioni"])
    assert risposta.status_code == 200, risposta.text
    assert risposta.json()["impronta_pin"]["impronta"] == IMPRONTA


def test_il_tablet_puo_dire_che_non_l_ha_applicata(
    genitore: TestClient, dispositivo: dict[str, Any]
) -> None:
    risposta = genitore.post(
        "/api/v1/device/ack",
        headers=dispositivo["intestazioni"],
        json={
            "revisione": 1,
            "esito": "partial",
            "campi_rifiutati": [
                {"campo": "impronta_pin", "motivo": "valore_non_applicabile"}
            ],
        },
    )
    assert risposta.status_code == 200, risposta.text


# --- quello che si rifiuta -------------------------------------------------


@pytest.mark.parametrize(
    "sale",
    [
        SALE.upper(),            # maiuscole: il browser manda minuscole
        "00112233",              # troppo corto
        SALE + "00",             # troppo lungo
        "zz112233445566778899aabbccddeeff",
        "",
    ],
)
def test_sale_storto(genitore: TestClient, tablet: dict[str, Any], sale: str) -> None:
    assert scrivi(genitore, tablet, impronta(sale=sale)).status_code == 422


@pytest.mark.parametrize(
    "valore",
    [IMPRONTA.upper(), IMPRONTA[:-1], IMPRONTA + "0", "non e esadecimale", ""],
)
def test_impronta_storta(genitore: TestClient, tablet: dict[str, Any], valore: str) -> None:
    assert scrivi(genitore, tablet, impronta(impronta=valore)).status_code == 422


@pytest.mark.parametrize("giri", [0, 1, 19999, 200001, 10_000_000, -20000])
def test_giri_fuori_scala(genitore: TestClient, tablet: dict[str, Any], giri: int) -> None:
    """Il minimo e quello che fa il tablet da solo; il massimo e un tetto,
    perche il conto lo paga chi batte il PIN, ogni volta."""
    assert scrivi(genitore, tablet, impronta(giri=giri)).status_code == 422


def test_campo_in_piu_dentro_l_impronta(genitore: TestClient, tablet: dict[str, Any]) -> None:
    storta = impronta()
    storta["algoritmo"] = "scrypt"
    assert scrivi(genitore, tablet, storta).status_code == 422


@pytest.mark.parametrize("manca", ["sale", "giri", "impronta"])
def test_campo_mancante_dentro_l_impronta(
    genitore: TestClient, tablet: dict[str, Any], manca: str
) -> None:
    storta = impronta()
    del storta[manca]
    assert scrivi(genitore, tablet, storta).status_code == 422


@pytest.mark.parametrize("valore", ["1234", 1234, [], True])
def test_non_e_un_oggetto(genitore: TestClient, tablet: dict[str, Any], valore: Any) -> None:
    assert scrivi(genitore, tablet, valore).status_code == 422


# --- la porta vecchia resta chiusa -----------------------------------------


@pytest.mark.parametrize(
    "campo, valore",
    [("pin", "1234"), ("pin_hash", IMPRONTA), ("pin_sale", SALE)],
)
def test_le_cifre_non_entrano_sotto_nessun_nome(
    genitore: TestClient, tablet: dict[str, Any], campo: str, valore: str
) -> None:
    """Esiste impronta_pin, quindi non esistono "pin" ne i suoi pezzi sparsi:
    un vocabolario con due modi di dire la stessa cosa ne ha uno sbagliato."""
    risposta = genitore.put(
        "/api/v1/tablets/%s/config" % tablet["id"],
        json=documento_base(**{campo: valore}),
    )
    assert risposta.status_code == 422, risposta.text
    assert campo in risposta.text
    assert "impronta_pin" in risposta.text


def test_il_422_non_rimanda_indietro_quello_che_e_arrivato(
    genitore: TestClient, tablet: dict[str, Any]
) -> None:
    """Vale per tutto, ma qui conta di piu: un 422 che ripete il valore
    scriverebbe nei log quello che qualcuno ha provato a mandare."""
    risposta = genitore.put(
        "/api/v1/tablets/%s/config" % tablet["id"],
        json=documento_base(pin="9731"),
    )
    assert risposta.status_code == 422
    assert "9731" not in risposta.text
