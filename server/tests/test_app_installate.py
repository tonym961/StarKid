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

import base64
from typing import Any

import pytest
from fastapi.testclient import TestClient

from app.models import RichiestaAppInstallate
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


# --- le icone --------------------------------------------------------------
#
# Il tipo di immagine lo decide il server guardando i primi byte, non il
# tablet dicendolo: quelle immagini finiscono nella pagina del genitore, e un
# dispositivo non deve poter scrivere "image/svg+xml" davanti a qualunque
# cosa. SVG non e fra i formati ammessi apposta: dentro un SVG ci sta del
# codice.

PNG = base64.b64encode(b"\x89PNG\r\n\x1a\n" + b"\x00" * 32).decode()
WEBP = base64.b64encode(b"RIFF" + b"\x00" * 4 + b"WEBP" + b"VP8 " + b"\x00" * 8).decode()


def test_un_png_arriva_come_data_url(genitore: TestClient, dispositivo: dict[str, Any]) -> None:
    manda(dispositivo, genitore, [{"pacchetto": VIDEO, "nome": "Video", "icona": PNG}])
    icona = leggi(genitore, dispositivo["id"]).json()["app"][0]["icona"]
    assert icona.startswith("data:image/png;base64,")
    # Ricodificata da noi: quello che finisce nella pagina lo scriviamo noi.
    assert icona.endswith(PNG)


def test_anche_il_webp(genitore: TestClient, dispositivo: dict[str, Any]) -> None:
    manda(dispositivo, genitore, [{"pacchetto": VIDEO, "icona": WEBP}])
    assert leggi(genitore, dispositivo["id"]).json()["app"][0]["icona"].startswith(
        "data:image/webp;base64,"
    )


@pytest.mark.parametrize(
    "storta",
    [
        base64.b64encode(b"<svg xmlns='http://www.w3.org/2000/svg'></svg>").decode(),
        base64.b64encode(b"\xff\xd8\xff\xe0JFIF").decode(),
        base64.b64encode(b"RIFF" + b"\x00" * 4 + b"WAVE").decode(),
        base64.b64encode(b"GIF89a").decode(),
        "non e nemmeno base64",
        "data:image/png;base64," + PNG,
    ],
)
def test_icona_storta_si_butta_e_l_app_resta(
    genitore: TestClient, dispositivo: dict[str, Any], storta: str
) -> None:
    """Un'icona che non si legge non deve far perdere l'elenco.

    Altrove il vocabolario chiuso rifiuta tutto il documento, ed e giusto:
    li si parla di regole. Qui si parla di un dettaglio estetico, e restare
    senza elenco vorrebbe dire tornare a scrivere i nomi dei pacchetti a
    mano.
    """
    risposta = manda(dispositivo, genitore, [{"pacchetto": VIDEO, "nome": "Video", "icona": storta}])
    assert risposta.status_code == 200, risposta.text
    voce = leggi(genitore, dispositivo["id"]).json()["app"][0]
    assert voce["pacchetto"] == VIDEO
    assert voce["icona"] == ""


def test_icona_troppo_grossa_si_butta(
    genitore: TestClient, dispositivo: dict[str, Any]
) -> None:
    grossa = base64.b64encode(b"\x89PNG\r\n\x1a\n" + b"\x00" * 30000).decode()
    assert manda(dispositivo, genitore, [{"pacchetto": VIDEO, "icona": grossa}]).status_code == 200
    assert leggi(genitore, dispositivo["id"]).json()["app"][0]["icona"] == ""


def test_senza_icona_va_bene(genitore: TestClient, dispositivo: dict[str, Any]) -> None:
    manda(dispositivo, genitore, [{"pacchetto": VIDEO, "nome": "Video"}])
    assert leggi(genitore, dispositivo["id"]).json()["app"][0]["icona"] == ""


def test_un_inventario_con_tutte_le_icone_non_arriva_nemmeno(
    genitore: TestClient, dispositivo: dict[str, Any]
) -> None:
    """Il primo muro non e il modello: e il limite sul corpo della richiesta,
    64 KB, che vale per tutta l'API. Arriva un 413 prima che qualcuno guardi
    dentro, ed e esattamente il motivo per cui il tablet manda le icone a
    gruppi invece che dentro l'inventario."""
    pesante = base64.b64encode(b"\x89PNG\r\n\x1a\n" + b"\x00" * 17000).decode()
    troppe = [{"pacchetto": "com.esempio.app%d" % n, "icona": pesante} for n in range(200)]
    assert manda(dispositivo, genitore, troppe).status_code == 413


def test_il_tetto_delle_icone_sul_modello() -> None:
    """Sotto al limite del corpo c'e lo stesso un tetto sulla somma: senza,
    quattrocento icone da diciotto kilobyte sarebbero sette megabyte in una
    riga sola del database."""
    pesante = base64.b64encode(b"\x89PNG\r\n\x1a\n" + b"\x00" * 17000).decode()
    with pytest.raises(ValueError):
        RichiestaAppInstallate.model_validate(
            {"app": [{"pacchetto": "com.esempio.app%d" % n, "icona": pesante} for n in range(200)]}
        )


# --- le icone a gruppi, dopo l'inventario ----------------------------------


def mandaIcone(dispositivo: dict[str, Any], cliente: TestClient, icone: list[dict[str, Any]]):  # type: ignore[no-untyped-def]
    return cliente.post(
        "/api/v1/device/apps/icons",
        headers=dispositivo["intestazioni"],
        json={"icone": icone},
    )


def test_le_icone_si_attaccano_dopo(
    genitore: TestClient, dispositivo: dict[str, Any]
) -> None:
    primo = manda(dispositivo, genitore, [{"pacchetto": VIDEO}, {"pacchetto": MUSICA}])
    # L'inventario dice subito a chi manca l'icona: e cosi che il tablet sa
    # cosa mandare, senza tenerne il conto per conto suo.
    assert sorted(primo.json()["senza_icona"]) == sorted([MUSICA, VIDEO])

    dopo = mandaIcone(dispositivo, genitore, [{"pacchetto": VIDEO, "icona": PNG}])
    assert dopo.status_code == 200, dopo.text
    assert dopo.json()["attaccate"] == 1
    assert dopo.json()["senza_icona"] == [MUSICA]

    app = {v["pacchetto"]: v for v in leggi(genitore, dispositivo["id"]).json()["app"]}
    assert app[VIDEO]["icona"].startswith("data:image/png;base64,")
    assert app[MUSICA]["icona"] == ""


def test_un_icona_per_un_app_che_non_c_e_si_lascia_cadere(
    genitore: TestClient, dispositivo: dict[str, Any]
) -> None:
    """Puo succedere davvero: l'elenco cambia mentre le icone sono per
    strada. Al giro dopo il tablet manda quelle giuste; far fallire tutto
    non servirebbe a niente."""
    manda(dispositivo, genitore, [{"pacchetto": VIDEO}])
    risposta = mandaIcone(dispositivo, genitore, [{"pacchetto": MUSICA, "icona": PNG}])
    assert risposta.status_code == 200, risposta.text
    assert risposta.json()["attaccate"] == 0
    assert risposta.json()["senza_icona"] == [VIDEO]


def test_questa_rotta_non_aggiunge_app(
    genitore: TestClient, dispositivo: dict[str, Any]
) -> None:
    manda(dispositivo, genitore, [{"pacchetto": VIDEO}])
    mandaIcone(dispositivo, genitore, [{"pacchetto": MUSICA, "icona": PNG}])
    assert [v["pacchetto"] for v in leggi(genitore, dispositivo["id"]).json()["app"]] == [VIDEO]


def test_le_icone_vogliono_il_token_del_dispositivo(cliente: TestClient) -> None:
    assert cliente.post("/api/v1/device/apps/icons", json={"icone": []}).status_code == 401


def test_campo_in_piu_dentro_un_icona(
    genitore: TestClient, dispositivo: dict[str, Any]
) -> None:
    manda(dispositivo, genitore, [{"pacchetto": VIDEO}])
    storta = [{"pacchetto": VIDEO, "icona": PNG, "larghezza": 96}]
    assert mandaIcone(dispositivo, genitore, storta).status_code == 422


def test_anche_le_icone_a_gruppi_passano_dal_controllo(
    genitore: TestClient, dispositivo: dict[str, Any]
) -> None:
    """Il controllo dei primi byte vale su tutte e due le strade.

    Non e pignoleria: la prima versione della rotta a gruppi ereditava il
    validatore dall'altro modello, pydantic non lo registrava, e il tablet
    poteva far salvare una stringa qualunque dentro il src di un'immagine
    nella pagina del genitore.
    """
    manda(dispositivo, genitore, [{"pacchetto": VIDEO}])
    finta = base64.b64encode(b"<svg xmlns='http://www.w3.org/2000/svg'></svg>").decode()
    mandaIcone(dispositivo, genitore, [{"pacchetto": VIDEO, "icona": finta}])
    assert leggi(genitore, dispositivo["id"]).json()["app"][0]["icona"] == ""

    mandaIcone(dispositivo, genitore, [{"pacchetto": VIDEO, "icona": PNG}])
    assert leggi(genitore, dispositivo["id"]).json()["app"][0]["icona"].startswith(
        "data:image/png;base64,"
    )


def test_un_pacchetto_storto_nelle_icone_e_un_errore(
    genitore: TestClient, dispositivo: dict[str, Any]
) -> None:
    """Qui invece si rifiuta: un nome di pacchetto storto non e un dettaglio
    estetico, e il filtro che tiene fuori indirizzi e comandi."""
    risposta = mandaIcone(dispositivo, genitore, [{"pacchetto": "rm -rf /", "icona": PNG}])
    assert risposta.status_code == 422, risposta.text
