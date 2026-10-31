"""Lo storico: dieci revisioni, poi la piu vecchia esce.

Non e un limite tecnico. Uno storico infinito sarebbe un archivio di come
cambiava la giornata di un bambino, e non serve a nessuno; dieci passi
indietro sono quelli che servono per disfare un errore.
"""

from __future__ import annotations

from typing import Any

from fastapi.testclient import TestClient

from app.config_doc import MASSIMO_STORICO
from tests.conftest import documento_base


def scrivi(genitore: TestClient, id_tablet: str, **modifiche: Any):  # type: ignore[no-untyped-def]
    risposta = genitore.put(
        "/api/v1/tablets/%s/config" % id_tablet, json=documento_base(**modifiche)
    )
    assert risposta.status_code == 200, risposta.text
    return risposta.json()


def storico(genitore: TestClient, id_tablet: str) -> list[dict[str, Any]]:
    risposta = genitore.get("/api/v1/tablets/%s/history" % id_tablet)
    assert risposta.status_code == 200, risposta.text
    corpo = risposta.json()
    assert corpo["massimo"] == MASSIMO_STORICO
    return corpo["revisioni"]


def test_alla_undicesima_revisione_la_prima_sparisce(
    genitore: TestClient, tablet: dict[str, Any]
) -> None:
    id_tablet = tablet["id"]

    # Il tablet nasce con la revisione 1.
    assert [v["revisione"] for v in storico(genitore, id_tablet)] == [1]

    # Si arriva alla decima: ci stanno tutte.
    for numero in range(2, MASSIMO_STORICO + 1):
        assert scrivi(genitore, id_tablet, limite_giornaliero=numero)["revisione"] == numero
    numeri = [v["revisione"] for v in storico(genitore, id_tablet)]
    assert numeri == list(range(MASSIMO_STORICO, 0, -1))
    assert 1 in numeri

    # L'undicesima: la prima esce.
    assert scrivi(genitore, id_tablet, limite_giornaliero=11)["revisione"] == 11
    numeri = [v["revisione"] for v in storico(genitore, id_tablet)]
    assert len(numeri) == MASSIMO_STORICO
    assert numeri == list(range(11, 1, -1))
    assert 1 not in numeri


def test_una_revisione_potata_non_si_puo_ripristinare(
    genitore: TestClient, tablet: dict[str, Any]
) -> None:
    id_tablet = tablet["id"]
    for numero in range(2, MASSIMO_STORICO + 2):
        scrivi(genitore, id_tablet, limite_giornaliero=numero)

    risposta = genitore.post(
        "/api/v1/tablets/%s/revert" % id_tablet, json={"revisione": 1}
    )
    assert risposta.status_code == 404, risposta.text
    # Il messaggio dice perche, senza raccontare niente di piu.
    assert str(MASSIMO_STORICO) in risposta.json()["errore"]


def test_il_ripristino_aggiunge_una_revisione(
    genitore: TestClient, tablet: dict[str, Any]
) -> None:
    id_tablet = tablet["id"]
    scrivi(genitore, id_tablet, tema="spaziale", sfondo="marte", suoni=False)
    scrivi(genitore, id_tablet, tema="pulito", sfondo="prato", suoni=True)

    risposta = genitore.post(
        "/api/v1/tablets/%s/revert" % id_tablet, json={"revisione": 2}
    )
    assert risposta.status_code == 200, risposta.text
    ripristinato = risposta.json()
    # Numero nuovo, contenuto vecchio: lo storico e un registro, non un posto
    # da cui le cose scompaiono.
    assert ripristinato["revisione"] == 4
    assert ripristinato["sfondo"] == "marte"
    assert ripristinato["tema"] == "spaziale"
    assert ripristinato["suoni"] is False

    voci = storico(genitore, id_tablet)
    assert [v["revisione"] for v in voci] == [4, 3, 2, 1]
    assert voci[0]["origine"] == "ripristino"
    assert voci[-1]["origine"] == "iniziale"


def test_lo_storico_porta_i_documenti_interi(
    genitore: TestClient, tablet: dict[str, Any]
) -> None:
    id_tablet = tablet["id"]
    scrivi(genitore, id_tablet, giochi_spenti=["xilofono"])
    voci = storico(genitore, id_tablet)
    assert voci[0]["leggibile"] is True
    assert voci[0]["documento"]["giochi_spenti"] == ["xilofono"]
    # La revisione sta accanto al documento, non dentro: un numero scritto in
    # due posti prima o poi racconta due storie diverse.
    assert "revisione" not in voci[0]["documento"]


def test_il_tablet_riceve_sempre_l_ultima(
    genitore: TestClient, dispositivo: dict[str, Any]
) -> None:
    id_tablet = dispositivo["id"]
    scrivi(genitore, id_tablet, sfondo="cielo")
    genitore.post("/api/v1/tablets/%s/revert" % id_tablet, json={"revisione": 1})

    consegnata = genitore.get(
        "/api/v1/device/config", headers=dispositivo["intestazioni"]
    )
    assert consegnata.status_code == 200, consegnata.text
    assert consegnata.json()["revisione"] == 3
    assert consegnata.json()["sfondo"] == "lilla"


def test_eliminare_il_tablet_porta_via_lo_storico(
    genitore: TestClient, tablet: dict[str, Any]
) -> None:
    id_tablet = tablet["id"]
    scrivi(genitore, id_tablet, suoni=False)

    assert genitore.delete("/api/v1/tablets/%s" % id_tablet).status_code == 200
    assert genitore.get("/api/v1/tablets/%s/history" % id_tablet).status_code == 404
    assert genitore.get("/api/v1/tablets").json()["tablets"] == []
