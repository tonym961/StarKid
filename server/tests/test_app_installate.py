"""L'inventario delle app, cioe il secondo canale dal tablet al server.

Fino a qui il tablet non raccontava niente di se tranne cinque spie. Questo
elenco esiste per una ragione sola: far spuntare al genitore le app da una
lista invece di fargli scrivere "com.esempio.gioco" a mano, che e il modo piu
sicuro di sbagliare una lettera e non capire perche quell'app non si apre.

Le due cose da tenere ferme, e che questi test guardano:

1. **Il server non ne fa niente da solo.** L'inventario non tocca
   app_permesse. Un tablet che si auto-autorizzasse le app sarebbe il
   contrario di questo progetto.
2. **E un inventario, non un diario.** Dice cosa c'e installato, non cosa e
   stato aperto ne per quanto: quei numeri restano sul tablet.
"""

from __future__ import annotations

from typing import Any

import pytest
from fastapi.testclient import TestClient

from tests.conftest import documento_base

VIDEO = "com.esempio.video"
MUSICA = "com.esempio.musica"


def manda(dispositivo: dict[str, Any], cliente: TestClient, app: list[dict[str, Any]]):  # type: ignore[no-untyped-def]
    return cliente.post(
        "/api/v1/device/apps", headers=dispositivo["intestazioni"], json={"app": app}
    )


def leggi(genitore: TestClient, id_tablet: str):  # type: ignore[no-untyped-def]
    return genitore.get("/api/v1/tablets/%s/apps" % id_tablet)


# --- il caso normale -------------------------------------------------------


def test_un_tablet_che_non_ha_mai_parlato(
    genitore: TestClient, tablet: dict[str, Any]
) -> None:
    """Vuoto, non assente: la pagina deve poter dire "non ha ancora mandato
    l'elenco" senza inciampare."""
    risposta = leggi(genitore, tablet["id"])
    assert risposta.status_code == 200, risposta.text
    assert risposta.json() == {"app": [], "aggiornato_il": None}


def test_andata_e_ritorno(genitore: TestClient, dispositivo: dict[str, Any]) -> None:
    invio = manda(
        dispositivo,
        genitore,
        [{"pacchetto": VIDEO, "nome": "Video"}, {"pacchetto": MUSICA, "nome": "Musica"}],
    )
    assert invio.status_code == 200, invio.text
    assert invio.json()["quante"] == 2

    corpo = leggi(genitore, dispositivo["id"]).json()
    # In ordine di nome: e un elenco da leggere, non da elaborare.
    assert [v["pacchetto"] for v in corpo["app"]] == [MUSICA, VIDEO]
    assert corpo["aggiornato_il"] is not None


def test_l_elenco_arriva_intero_e_sostituisce(
    genitore: TestClient, dispositivo: dict[str, Any]
) -> None:
    """Un inventario a pezzi non e un inventario: una app disinstallata deve
    sparire, non restare perche nessuno l'ha nominata."""
    manda(dispositivo, genitore, [{"pacchetto": VIDEO, "nome": "Video"}])
    manda(dispositivo, genitore, [{"pacchetto": MUSICA, "nome": "Musica"}])
    corpo = leggi(genitore, dispositivo["id"]).json()
    assert [v["pacchetto"] for v in corpo["app"]] == [MUSICA]


def test_elenco_vuoto_e_una_risposta(
    genitore: TestClient, dispositivo: dict[str, Any]
) -> None:
    manda(dispositivo, genitore, [{"pacchetto": VIDEO, "nome": "Video"}])
    assert manda(dispositivo, genitore, []).status_code == 200
    assert leggi(genitore, dispositivo["id"]).json()["app"] == []


# --- il nome lo scrive l'app, non noi --------------------------------------


def test_il_nome_perde_i_caratteri_di_controllo(
    genitore: TestClient, dispositivo: dict[str, Any]
) -> None:
    manda(dispositivo, genitore, [{"pacchetto": VIDEO, "nome": "Vi\ndeo\u0007 "}])
    assert leggi(genitore, dispositivo["id"]).json()["app"][0]["nome"] == "Video"


def test_il_nome_e_corto(genitore: TestClient, dispositivo: dict[str, Any]) -> None:
    """Sessanta caratteri non bastano a scrivere una frase che convinca
    qualcuno a fare qualcosa, e il nome di un'app vero ci sta tutto."""
    manda(dispositivo, genitore, [{"pacchetto": VIDEO, "nome": "a" * 500}])
    assert len(leggi(genitore, dispositivo["id"]).json()["app"][0]["nome"]) == 60


def test_il_nome_puo_mancare(genitore: TestClient, dispositivo: dict[str, Any]) -> None:
    assert manda(dispositivo, genitore, [{"pacchetto": VIDEO}]).status_code == 200
    assert leggi(genitore, dispositivo["id"]).json()["app"][0]["nome"] == ""


# --- quello che si rifiuta -------------------------------------------------


@pytest.mark.parametrize(
    "pacchetto",
    ["https://esempio.it/x", "/data/local/tmp/x", "rm -rf /", "com esempio video", "video"],
)
def test_solo_nomi_di_pacchetto(
    genitore: TestClient, dispositivo: dict[str, Any], pacchetto: str
) -> None:
    assert manda(dispositivo, genitore, [{"pacchetto": pacchetto}]).status_code == 422


def test_troppe_app(genitore: TestClient, dispositivo: dict[str, Any]) -> None:
    troppe = [{"pacchetto": "com.esempio.app%d" % n} for n in range(500)]
    assert manda(dispositivo, genitore, troppe).status_code == 422


def test_campo_in_piu(genitore: TestClient, dispositivo: dict[str, Any]) -> None:
    """Vocabolario chiuso anche qui: niente versione, niente icona, niente
    minuti d'uso. Soprattutto niente minuti d'uso."""
    storta = [{"pacchetto": VIDEO, "nome": "Video", "minuti_usati": 42}]
    assert manda(dispositivo, genitore, storta).status_code == 422


def test_la_stessa_app_due_volte_si_tiene_una(
    genitore: TestClient, dispositivo: dict[str, Any]
) -> None:
    """Due activity della stessa app non sono un errore del tablet."""
    doppio = [{"pacchetto": VIDEO, "nome": "Video"}, {"pacchetto": VIDEO, "nome": "Video 2"}]
    assert manda(dispositivo, genitore, doppio).status_code == 200
    corpo = leggi(genitore, dispositivo["id"]).json()
    assert [v["pacchetto"] for v in corpo["app"]] == [VIDEO]
    assert corpo["app"][0]["nome"] == "Video"


# --- il server non ne fa niente da solo ------------------------------------


def test_l_inventario_non_tocca_le_app_permesse(
    genitore: TestClient, dispositivo: dict[str, Any]
) -> None:
    """La riga piu importante del file."""
    genitore.put(
        "/api/v1/tablets/%s/config" % dispositivo["id"],
        json=documento_base(app_permesse=[MUSICA]),
    )
    manda(dispositivo, genitore, [{"pacchetto": VIDEO, "nome": "Video"}])
    config = genitore.get("/api/v1/tablets/%s/config" % dispositivo["id"]).json()
    assert config["app_permesse"] == [MUSICA]


def test_l_inventario_non_crea_una_revisione(
    genitore: TestClient, dispositivo: dict[str, Any]
) -> None:
    prima = genitore.get("/api/v1/tablets/%s/config" % dispositivo["id"]).json()["revisione"]
    manda(dispositivo, genitore, [{"pacchetto": VIDEO, "nome": "Video"}])
    dopo = genitore.get("/api/v1/tablets/%s/config" % dispositivo["id"]).json()["revisione"]
    assert prima == dopo


def test_l_elenco_non_entra_nel_documento_del_tablet(
    genitore: TestClient, dispositivo: dict[str, Any]
) -> None:
    manda(dispositivo, genitore, [{"pacchetto": VIDEO, "nome": "Video"}])
    documento = genitore.get(
        "/api/v1/device/config", headers=dispositivo["intestazioni"]
    ).json()
    assert "app_installate" not in documento
    assert "app" not in documento


# --- chi puo parlare e chi puo leggere -------------------------------------


def test_senza_token_del_dispositivo_non_si_manda(cliente: TestClient) -> None:
    risposta = cliente.post("/api/v1/device/apps", json={"app": []})
    assert risposta.status_code == 401, risposta.text


def test_senza_sessione_non_si_legge(cliente: TestClient) -> None:
    # Senza la fixture del tablet: quella passa da genitore, che la sessione
    # la apre sullo stesso client.
    assert cliente.get("/api/v1/tablets/%s/apps" % ("a" * 16)).status_code == 401


def test_un_tablet_che_non_esiste(genitore: TestClient) -> None:
    assert leggi(genitore, "0" * 16).status_code == 404
