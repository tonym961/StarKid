"""I campi che non devono esistere.

Non e un test di forma, e un test di una decisione: il nome del bambino, il
PIN, l'identificativo dell'hardware e l'uso per singola app non stanno sul
server. Uno schema aperto li accetterebbe in silenzio e un giorno li
troveremmo in un backup.
"""

from __future__ import annotations

from typing import Any

import pytest
from fastapi.testclient import TestClient

from app.models import CAMPI_DOCUMENTO
from tests.conftest import documento_base

VIETATI: list[tuple[str, Any]] = [
    ("pin", "1234"),
    ("imei", "356938035643809"),
    ("bambino", "Luca"),
    ("uso_per_app", {"com.esempio.gioco": 42}),
]


@pytest.mark.parametrize("campo, valore", VIETATI)
def test_il_campo_vietato_da_422(
    genitore: TestClient, tablet: dict[str, Any], campo: str, valore: Any
) -> None:
    documento = documento_base(**{campo: valore})
    risposta = genitore.put("/api/v1/tablets/%s/config" % tablet["id"], json=documento)
    assert risposta.status_code == 422, risposta.text
    # Il motivo deve nominare il campo: chi legge l'errore deve capire cosa
    # togliere, e chi legge il codice fra un anno deve capire che era voluto.
    assert campo in risposta.text


@pytest.mark.parametrize("campo, valore", VIETATI)
def test_il_campo_vietato_non_crea_una_revisione(
    genitore: TestClient, tablet: dict[str, Any], campo: str, valore: Any
) -> None:
    documento = documento_base(**{campo: valore})
    genitore.put("/api/v1/tablets/%s/config" % tablet["id"], json=documento)
    corrente = genitore.get("/api/v1/tablets/%s/config" % tablet["id"])
    assert corrente.status_code == 200
    assert corrente.json()["revisione"] == 1


def test_anche_un_campo_innocuo_ma_non_previsto_da_422(
    genitore: TestClient, tablet: dict[str, Any]
) -> None:
    """Lo schema e chiuso, non e un elenco di cose cattive."""
    documento = documento_base(colore_preferito="verde")
    risposta = genitore.put("/api/v1/tablets/%s/config" % tablet["id"], json=documento)
    assert risposta.status_code == 422, risposta.text


def test_campo_in_piu_dentro_la_fascia(genitore: TestClient, tablet: dict[str, Any]) -> None:
    documento = documento_base(
        fascia={"attiva": True, "inizio": 480, "fine": 1200, "giorni": [2, 3], "pin": "1234"}
    )
    risposta = genitore.put("/api/v1/tablets/%s/config" % tablet["id"], json=documento)
    assert risposta.status_code == 422, risposta.text


def test_campo_mancante_da_422(genitore: TestClient, tablet: dict[str, Any]) -> None:
    """Chiuso vuol dire anche "niente mezzi documenti": nessun PATCH."""
    documento = documento_base()
    del documento["suoni"]
    risposta = genitore.put("/api/v1/tablets/%s/config" % tablet["id"], json=documento)
    assert risposta.status_code == 422, risposta.text


def test_creazione_tablet_senza_nome_del_bambino(genitore: TestClient) -> None:
    risposta = genitore.post(
        "/api/v1/tablets", json={"nome": "Tablet di camera", "bambino": "Luca"}
    )
    assert risposta.status_code == 422, risposta.text


def test_il_documento_consegnato_al_tablet_ha_solo_i_campi_previsti(
    genitore: TestClient, dispositivo: dict[str, Any]
) -> None:
    risposta = genitore.get(
        "/api/v1/device/config", headers=dispositivo["intestazioni"]
    )
    assert risposta.status_code == 200, risposta.text
    chiavi = set(risposta.json())
    assert chiavi == CAMPI_DOCUMENTO | {"revisione"}
    for vietato in ("pin", "imei", "bambino", "uso_per_app", "avatar", "nome_bambino"):
        assert vietato not in chiavi


def test_il_tablet_non_puo_mandare_campi_in_piu(
    genitore: TestClient, dispositivo: dict[str, Any]
) -> None:
    """Anche quello che dice il dispositivo passa da uno schema chiuso."""
    risposta = genitore.post(
        "/api/v1/device/health",
        headers=dispositivo["intestazioni"],
        json={
            "device_owner": True,
            "usage_access": True,
            "overlay": True,
            "guard_running": True,
            "default_home": True,
            "imei": "356938035643809",
        },
    )
    assert risposta.status_code == 422, risposta.text


def test_il_tablet_non_puo_inventarsi_un_campo_rifiutato(
    genitore: TestClient, dispositivo: dict[str, Any]
) -> None:
    """I campi rifiutati finiscono sotto gli occhi del genitore: vocabolario chiuso."""
    risposta = genitore.post(
        "/api/v1/device/ack",
        headers=dispositivo["intestazioni"],
        json={
            "revisione": 1,
            "esito": "partial",
            "campi_rifiutati": [{"campo": "<script>", "motivo": "ciao"}],
        },
    )
    assert risposta.status_code == 422, risposta.text

    marcatura = genitore.post(
        "/api/v1/device/ack",
        headers=dispositivo["intestazioni"],
        json={
            "revisione": 1,
            "esito": "partial",
            "campi_rifiutati": [
                {"campo": "kiosk", "motivo": "<img src=x onerror=alert(1)>"}
            ],
        },
    )
    assert marcatura.status_code == 422, marcatura.text

    # Il motivo e un vocabolario chiuso, non testo libero. Una frase scritta
    # dal tablet, anche innocua, non entra: con 200 caratteri per campo e
    # quaranta campi, chi avesse il token di un tablet aveva ottomila
    # caratteri suoi nella pagina del genitore - non XSS, ma phishing
    # ("apri questo indirizzo e inserisci la password", firmato dal tablet
    # del figlio). Il testo per le persone sta nella web UI.
    frase = genitore.post(
        "/api/v1/device/ack",
        headers=dispositivo["intestazioni"],
        json={
            "revisione": 1,
            "esito": "partial",
            "campi_rifiutati": [{"campo": "kiosk", "motivo": "serve il PIN sul tablet"}],
        },
    )
    assert frase.status_code == 422, frase.text

    buono = genitore.post(
        "/api/v1/device/ack",
        headers=dispositivo["intestazioni"],
        json={
            "revisione": 1,
            "esito": "partial",
            "campi_rifiutati": [{"campo": "kiosk", "motivo": "serve_device_owner"}],
        },
    )
    assert buono.status_code == 200, buono.text

    # Senza motivo va bene: vale "sconosciuto", che e meglio di una bugia.
    muto = genitore.post(
        "/api/v1/device/ack",
        headers=dispositivo["intestazioni"],
        json={
            "revisione": 1,
            "esito": "partial",
            "campi_rifiutati": [{"campo": "kiosk"}],
        },
    )
    assert muto.status_code == 200, muto.text


def test_il_tablet_puo_tacere_sul_kiosk_in_funzione(
    genitore: TestClient, dispositivo: dict[str, Any]
) -> None:
    """La spia "kiosk in funzione" e arrivata dopo, e i tablet non ancora
    aggiornati non la mandano: senza un valore iniziale le loro spie
    verrebbero rifiutate tutte, e dal server quei tablet diventerebbero muti
    proprio mentre si cerca di capire cosa non va."""
    risposta = genitore.post(
        "/api/v1/device/health",
        headers=dispositivo["intestazioni"],
        json={
            "device_owner": True,
            "usage_access": True,
            "overlay": True,
            "guard_running": True,
            "default_home": True,
        },
    )
    assert risposta.status_code == 200, risposta.text
    voce = genitore.get("/api/v1/tablets").json()["tablets"][0]
    assert voce["salute_dichiarata"]["kiosk_attivo"] is False


def test_il_kiosk_in_funzione_arriva_fino_alla_pagina(
    genitore: TestClient, dispositivo: dict[str, Any]
) -> None:
    """Distingue "il kiosk e acceso nelle preferenze" da "il lock task e in
    funzione adesso". Finche non c'era, un tablet poteva risultare chiuso
    da qui mentre era aperto in mano al bambino."""
    risposta = genitore.post(
        "/api/v1/device/health",
        headers=dispositivo["intestazioni"],
        json={
            "device_owner": True,
            "usage_access": True,
            "overlay": True,
            "guard_running": True,
            "default_home": True,
            "kiosk_attivo": True,
        },
    )
    assert risposta.status_code == 200, risposta.text
    voce = genitore.get("/api/v1/tablets").json()["tablets"][0]
    assert voce["salute_dichiarata"]["kiosk_attivo"] is True
