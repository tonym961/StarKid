"""Utenti, ruoli e chi vede cosa.

I test che contano qui non sono "la rotta risponde 200": sono i NO. Un
modello a permessi si giudica da cosa riesce a impedire, e ogni test sotto
descrive una mossa che qualcuno proverebbe davvero.
"""

from __future__ import annotations

from typing import Any

import pytest
from fastapi.testclient import TestClient

from app import auth
from app.main import app

from .conftest import PAROLA, UTENTE

ALTRA_PAROLA = "fenicottero-di-cartone-9"


def _entra(nome: str, parola: str) -> TestClient:
    """Un client NUOVO con la sessione di quell utente.

    Nuovo e non quello dei fixture: i fixture "cliente", "account" e
    "genitore" sono lo STESSO oggetto, quindi entrare con un secondo nome
    sopra il primo gli rubava i cookie e i test provavano tutti la stessa
    sessione senza accorgersene. Qui ogni utente ha il suo barattolo.

    Niente context manager: il lifespan ha gia creato le tabelle quando e
    partito il client dei fixture, e rifarlo non serve.
    """
    altro = TestClient(app, base_url="https://testserver")
    risposta = altro.post("/api/v1/auth/login", json={"utente": nome, "password": parola})
    assert risposta.status_code == 200, risposta.text
    altro.headers["x-csrf-token"] = risposta.json()["csrf"]
    return altro


def _crea(genitore: TestClient, nome: str, ruolo: str, **extra: Any) -> dict[str, Any]:
    risposta = genitore.post(
        "/api/v1/utenti",
        json={"utente": nome, "password": ALTRA_PAROLA, "ruolo": ruolo, **extra},
    )
    assert risposta.status_code == 201, risposta.text
    return risposta.json()


def _tablet(genitore: TestClient, nome: str) -> str:
    risposta = genitore.post("/api/v1/tablets", json={"nome": nome})
    assert risposta.status_code == 201, risposta.text
    return risposta.json()["id"]


# ---------------------------------------------------------------------------
# Le basi
# ---------------------------------------------------------------------------


def test_chi_c_era_prima_e_admin(genitore: TestClient) -> None:
    io = genitore.get("/api/v1/auth/io")
    assert io.status_code == 200, io.text
    assert io.json() == {
        "utente": UTENTE,
        "ruolo": "admin",
        "puo_amministrare": True,
        "sola_lettura": False,
    }


def test_chi_sono_sopravvive_al_ricaricamento(genitore: TestClient) -> None:
    """Prima il ruolo arrivava solo nella risposta del login.

    Dopo un F5 la pagina non sapeva piu chi fosse l'utente ne cosa nascondere.
    Con un account solo non importava a nessuno.
    """
    assert genitore.get("/api/v1/auth/io").json()["ruolo"] == "admin"
    assert genitore.get("/api/v1/auth/io").json()["ruolo"] == "admin"


def test_il_login_dice_il_nome_del_database_non_quello_scritto(account: TestClient) -> None:
    """Il nome non distingue le maiuscole, quindi l'eco della richiesta mentirebbe."""
    risposta = account.post(
        "/api/v1/auth/login", json={"utente": UTENTE.upper(), "password": PAROLA}
    )
    assert risposta.status_code == 200, risposta.text
    assert risposta.json()["utente"] == UTENTE


# ---------------------------------------------------------------------------
# Chi vede cosa
# ---------------------------------------------------------------------------


def test_un_genitore_vede_solo_i_suoi_tablet(genitore: TestClient) -> None:
    mio = _tablet(genitore, "tablet di casa")
    _crea(genitore, "nonna", "genitore")

    altro = _entra("nonna", ALTRA_PAROLA)
    assert altro.get("/api/v1/tablets").json()["tablets"] == []

    # E non lo vede nemmeno chiedendolo per nome.
    assert altro.get("/api/v1/tablets/%s/config" % mio).status_code == 404


def test_il_tablet_di_un_altro_risponde_come_uno_che_non_esiste(
    genitore: TestClient, cliente: TestClient
) -> None:
    """404 e non 403: a chi non ha quel tablet non si dice nemmeno che esiste.

    Sono i tablet di un'altra famiglia: perfino sapere quanti sono e
    un'informazione che non gli spetta. La risposta deve essere identica a
    quella di un identificativo inventato.
    """
    mio = _tablet(genitore, "tablet di casa")
    _crea(genitore, "nonna", "genitore")
    altro = _entra("nonna", ALTRA_PAROLA)

    vero = altro.get("/api/v1/tablets/%s/config" % mio)
    finto = altro.get("/api/v1/tablets/%s/config" % ("f" * 16))
    assert vero.status_code == finto.status_code == 404
    assert vero.json() == finto.json(), "le due risposte si distinguono"


def test_un_genitore_non_puo_scrivere_sul_tablet_di_un_altro(
    genitore: TestClient, cliente: TestClient
) -> None:
    mio = _tablet(genitore, "tablet di casa")
    documento = genitore.get("/api/v1/tablets/%s/config" % mio).json()
    _crea(genitore, "nonna", "genitore")
    altro = _entra("nonna", ALTRA_PAROLA)

    for metodo, percorso, corpo in (
        ("put", "/api/v1/tablets/%s/config" % mio, documento),
        ("post", "/api/v1/tablets/%s/revert" % mio, {"revisione": 1}),
        ("delete", "/api/v1/tablets/%s" % mio, None),
    ):
        risposta = getattr(altro, metodo)(percorso, **({"json": corpo} if corpo else {}))
        assert risposta.status_code == 404, "%s %s ha risposto %s" % (
            metodo, percorso, risposta.status_code,
        )

    # E il tablet e ancora li.
    assert genitore.get("/api/v1/tablets/%s/config" % mio).status_code == 200


def test_il_tablet_appena_creato_si_vede_subito(genitore: TestClient) -> None:
    """Chi crea un tablet deve trovarselo nell'elenco, non un 404.

    La concessione si scrive nella stessa transazione della creazione: senza,
    un genitore creerebbe un tablet e si ritroverebbe la schermata vuota un
    istante dopo.
    """
    _crea(genitore, "papa", "genitore")
    altro = _entra("papa", ALTRA_PAROLA)
    suo = _tablet(altro, "tablet suo")
    elenco = altro.get("/api/v1/tablets").json()["tablets"]
    assert [t["id"] for t in elenco] == [suo]


def test_le_concessioni_decidono_chi_vede(genitore: TestClient) -> None:
    mio = _tablet(genitore, "tablet di casa")
    nonna = _crea(genitore, "nonna", "genitore")

    dato = genitore.put("/api/v1/utenti/%s/tablet" % nonna["id"], json={"tablet": [mio]})
    assert dato.status_code == 200, dato.text

    altro = _entra("nonna", ALTRA_PAROLA)
    assert [t["id"] for t in altro.get("/api/v1/tablets").json()["tablets"]] == [mio]

    # E togliendola, sparisce.
    genitore.put("/api/v1/utenti/%s/tablet" % nonna["id"], json={"tablet": []})
    assert altro.get("/api/v1/tablets").json()["tablets"] == []


# ---------------------------------------------------------------------------
# Sola lettura
# ---------------------------------------------------------------------------


def test_un_osservatore_guarda_e_basta(genitore: TestClient) -> None:
    mio = _tablet(genitore, "tablet di casa")
    occhi = _crea(genitore, "babysitter", "osservatore")
    genitore.put("/api/v1/utenti/%s/tablet" % occhi["id"], json={"tablet": [mio]})

    guarda = _entra("babysitter", ALTRA_PAROLA)
    assert guarda.get("/api/v1/auth/io").json()["sola_lettura"] is True
    # Legge.
    assert guarda.get("/api/v1/tablets").status_code == 200
    documento = guarda.get("/api/v1/tablets/%s/config" % mio)
    assert documento.status_code == 200
    # Ma non scrive: 403, e con la frase giusta.
    scrittura = guarda.put("/api/v1/tablets/%s/config" % mio, json=documento.json())
    assert scrittura.status_code == 403
    assert "sola lettura" in scrittura.json()["errore"]


def test_un_osservatore_non_puo_creare_tablet(genitore: TestClient) -> None:
    _crea(genitore, "babysitter", "osservatore")
    guarda = _entra("babysitter", ALTRA_PAROLA)
    assert guarda.post("/api/v1/tablets", json={"nome": "mio"}).status_code == 403


# ---------------------------------------------------------------------------
# Scalata di privilegi
# ---------------------------------------------------------------------------


def test_un_genitore_non_puo_amministrare_gli_utenti(
    genitore: TestClient, cliente: TestClient
) -> None:
    """La mossa piu ovvia: farsi admin da soli."""
    nonna = _crea(genitore, "nonna", "genitore")
    altro = _entra("nonna", ALTRA_PAROLA)

    assert altro.get("/api/v1/utenti").status_code == 403
    assert altro.post(
        "/api/v1/utenti", json={"utente": "complice", "password": ALTRA_PAROLA, "ruolo": "admin"}
    ).status_code == 403
    assert altro.patch(
        "/api/v1/utenti/%s" % nonna["id"], json={"ruolo": "admin"}
    ).status_code == 403
    # E il ruolo non si e mosso.
    assert altro.get("/api/v1/auth/io").json()["ruolo"] == "genitore"


def test_un_osservatore_non_puo_amministrare_gli_utenti(
    genitore: TestClient, cliente: TestClient
) -> None:
    _crea(genitore, "babysitter", "osservatore")
    guarda = _entra("babysitter", ALTRA_PAROLA)
    assert guarda.get("/api/v1/utenti").status_code == 403


def test_senza_sessione_le_rotte_degli_utenti_sono_chiuse(cliente: TestClient) -> None:
    assert cliente.get("/api/v1/utenti").status_code == 401
    assert cliente.post(
        "/api/v1/utenti", json={"utente": "x", "password": ALTRA_PAROLA}
    ).status_code == 401


def test_niente_iscrizione_dal_web(cliente: TestClient) -> None:
    """La promessa della pagina di accesso, provata.

    Un account lo crea chi e gia dentro ed e admin. Da fuori, senza
    credenziali, non esiste nessuna strada.
    """
    assert cliente.post(
        "/api/v1/utenti",
        json={"utente": "intruso", "password": ALTRA_PAROLA, "ruolo": "admin"},
    ).status_code == 401
    assert auth.utente_per_nome("intruso") is None


# ---------------------------------------------------------------------------
# Non chiudersi fuori
# ---------------------------------------------------------------------------


def test_l_ultimo_admin_non_si_puo_declassare(genitore: TestClient) -> None:
    io = genitore.get("/api/v1/auth/io").json()
    mio_id = [u for u in genitore.get("/api/v1/utenti").json()["utenti"] if u["utente"] == io["utente"]][0]["id"]
    risposta = genitore.patch("/api/v1/utenti/%s" % mio_id, json={"ruolo": "genitore"})
    assert risposta.status_code == 409
    assert genitore.get("/api/v1/auth/io").json()["ruolo"] == "admin"


def test_non_ci_si_disattiva_da_soli(genitore: TestClient) -> None:
    utenti = genitore.get("/api/v1/utenti").json()["utenti"]
    mio_id = utenti[0]["id"]
    risposta = genitore.patch("/api/v1/utenti/%s" % mio_id, json={"attivo": False})
    assert risposta.status_code == 409
    assert "tuo stesso account" in risposta.json()["errore"]


def test_non_ci_si_cancella_da_soli(genitore: TestClient) -> None:
    mio_id = genitore.get("/api/v1/utenti").json()["utenti"][0]["id"]
    assert genitore.delete("/api/v1/utenti/%s" % mio_id).status_code == 409


def test_con_due_admin_uno_si_puo_declassare(genitore: TestClient) -> None:
    """La regola e "l'ultimo", non "nessuno": con due, la mossa e legittima."""
    secondo = _crea(genitore, "seconda", "admin")
    risposta = genitore.patch("/api/v1/utenti/%s" % secondo["id"], json={"ruolo": "genitore"})
    assert risposta.status_code == 200, risposta.text
    assert risposta.json()["ruolo"] == "genitore"


def test_un_admin_disattivato_non_entra_piu(genitore: TestClient) -> None:
    secondo = _crea(genitore, "seconda", "admin")
    spento = genitore.patch("/api/v1/utenti/%s" % secondo["id"], json={"attivo": False})
    assert spento.status_code == 200, spento.text
    risposta = TestClient(app, base_url="https://testserver").post(
        "/api/v1/auth/login", json={"utente": "seconda", "password": ALTRA_PAROLA}
    )
    assert risposta.status_code == 401
    # Stessa frase di una password sbagliata: arrivare a dire "sei
    # disattivato" direbbe a chi prova che la password era giusta.
    assert risposta.json()["errore"] == "credenziali non valide"


def test_disattivare_chiude_le_sessioni_aperte(genitore: TestClient) -> None:
    nonna = _crea(genitore, "nonna", "genitore")
    altro = _entra("nonna", ALTRA_PAROLA)
    assert altro.get("/api/v1/tablets").status_code == 200
    genitore.patch("/api/v1/utenti/%s" % nonna["id"], json={"attivo": False})
    assert altro.get("/api/v1/tablets").status_code == 401


# ---------------------------------------------------------------------------
# Password e sessioni
# ---------------------------------------------------------------------------


def test_cambiare_password_non_butta_fuori_gli_altri(
    genitore: TestClient, cliente: TestClient
) -> None:
    """Prima qui c'era un DELETE FROM sessione senza WHERE.

    Con un account solo era giusto. Con due, un genitore che cambia la sua
    password buttava fuori l'altro.
    """
    nonna = _crea(genitore, "nonna", "genitore")
    altro = _entra("nonna", ALTRA_PAROLA)
    assert altro.get("/api/v1/tablets").status_code == 200

    # L'admin cambia la password di se stesso... no: di nessun altro. Qui si
    # cambia quella dell'admin, e la sessione della nonna deve reggere.
    utenti = genitore.get("/api/v1/utenti").json()["utenti"]
    mio_id = [u for u in utenti if u["utente"] == UTENTE][0]["id"]
    cambiata = genitore.put(
        "/api/v1/utenti/%s/password" % mio_id, json={"password": "un-altra-lunga-12"}
    )
    assert cambiata.status_code == 200, cambiata.text
    assert altro.get("/api/v1/tablets").status_code == 200, "la sessione di un altro e caduta"


def test_cambiare_la_password_chiude_le_sessioni_di_quell_utente(
    genitore: TestClient, cliente: TestClient
) -> None:
    nonna = _crea(genitore, "nonna", "genitore")
    altro = _entra("nonna", ALTRA_PAROLA)
    genitore.put(
        "/api/v1/utenti/%s/password" % nonna["id"], json={"password": "un-altra-lunga-12"}
    )
    assert altro.get("/api/v1/tablets").status_code == 401


# ---------------------------------------------------------------------------
# Sottoutenti
# ---------------------------------------------------------------------------


def test_un_sottoutente_sa_di_chi_e_figlio(genitore: TestClient) -> None:
    papa = _crea(genitore, "papa", "genitore")
    figlio = _crea(genitore, "tata", "osservatore", id_padre=papa["id"])
    assert figlio["id_padre"] == papa["id"]


def test_cancellare_il_padre_porta_via_i_sottoutenti(
    genitore: TestClient, cliente: TestClient
) -> None:
    papa = _crea(genitore, "papa", "genitore")
    _crea(genitore, "tata", "osservatore", id_padre=papa["id"])
    assert genitore.delete("/api/v1/utenti/%s" % papa["id"]).status_code == 200
    nomi = {u["utente"] for u in genitore.get("/api/v1/utenti").json()["utenti"]}
    assert "tata" not in nomi, "il sottoutente e rimasto appeso senza padre"


def test_cancellare_un_utente_non_porta_via_i_suoi_tablet(genitore: TestClient) -> None:
    """Un genitore che se ne va non deve far sparire il tablet di casa.

    E soprattutto non deve far morire un token di dispositivo: quello si rifa
    solo portando un codice nuovo fin sul tablet.
    """
    papa = _crea(genitore, "papa", "genitore")
    mio = _tablet(genitore, "tablet di casa")
    genitore.put("/api/v1/utenti/%s/tablet" % papa["id"], json={"tablet": [mio]})
    genitore.delete("/api/v1/utenti/%s" % papa["id"])
    assert genitore.get("/api/v1/tablets/%s/config" % mio).status_code == 200


# ---------------------------------------------------------------------------
# Dettagli che fanno danno
# ---------------------------------------------------------------------------


def test_l_elenco_utenti_non_contiene_password(genitore: TestClient) -> None:
    """Un elenco di hash e un elenco di cose da provare con comodo."""
    _crea(genitore, "nonna", "genitore")
    testo = genitore.get("/api/v1/utenti").text
    assert "password" not in testo
    assert "scrypt" not in testo and "pbkdf2" not in testo


def test_due_nomi_che_differiscono_per_una_maiuscola_sono_lo_stesso(
    genitore: TestClient,
) -> None:
    """Altrimenti "Nonna" e "nonna" sono due account, ed e un inganno."""
    _crea(genitore, "nonna", "genitore")
    doppio = genitore.post(
        "/api/v1/utenti", json={"utente": "NONNA", "password": ALTRA_PAROLA, "ruolo": "genitore"}
    )
    assert doppio.status_code == 409


def test_un_utente_che_non_esiste_da_404_non_500(genitore: TestClient) -> None:
    assert genitore.get("/api/v1/utenti/9999/tablet").status_code == 404
    assert genitore.patch("/api/v1/utenti/9999", json={"attivo": False}).status_code == 404
    assert genitore.delete("/api/v1/utenti/9999").status_code == 404


def test_concedere_un_tablet_che_non_esiste_non_passa(genitore: TestClient) -> None:
    nonna = _crea(genitore, "nonna", "genitore")
    risposta = genitore.put(
        "/api/v1/utenti/%s/tablet" % nonna["id"], json={"tablet": ["f" * 16]}
    )
    assert risposta.status_code == 404


def test_le_rotte_dei_dispositivi_non_sono_cambiate(cliente: TestClient) -> None:
    """I tablet non hanno niente a che fare con gli utenti.

    Un token di dispositivo vale per il suo tablet e basta: nessun ruolo,
    nessuna concessione. Se un giorno qualcuno ci infila un controllo sul
    proprietario, deve rispondere 403 e MAI 401 - il tablet tratta il 401
    come "il server non mi conosce", e un 401 di troppo vale un giro nelle
    camere dei bambini.
    """
    assert cliente.get("/api/v1/device/config").status_code == 401
    assert cliente.post(
        "/api/v1/device/enroll", json={"token_arruolamento": "a" * 20}
    ).status_code == 401


def test_le_letture_non_pretendono_il_token_csrf(genitore: TestClient) -> None:
    """Una GET che pretende il CSRF risponde 403 a chi ha fatto tutto giusto.

    E successo davvero, e i test non l'avevano visto: mettono l'intestazione
    CSRF come predefinita sul client, quindi la mandano anche sulle letture.
    Il browser no, e la pagina Utenti rispondeva "token CSRF mancante" a un
    amministratore appena entrato.

    Qui si tolgono le intestazioni per il tempo della prova, come farebbe un
    browser vero.
    """
    senza = dict(genitore.headers)
    senza.pop("x-csrf-token", None)
    for percorso in ("/api/v1/utenti", "/api/v1/auth/io", "/api/v1/tablets"):
        risposta = genitore.get(percorso, headers={"x-csrf-token": ""})
        assert risposta.status_code == 200, "%s ha risposto %s senza CSRF" % (
            percorso, risposta.status_code,
        )


def test_le_scritture_lo_pretendono_eccome(genitore: TestClient) -> None:
    """L'altra meta: senza token, una scrittura deve prendere 403."""
    risposta = genitore.post(
        "/api/v1/utenti",
        json={"utente": "senzacsrf", "password": ALTRA_PAROLA, "ruolo": "genitore"},
        headers={"x-csrf-token": ""},
    )
    assert risposta.status_code == 403
    assert auth.utente_per_nome("senzacsrf") is None
