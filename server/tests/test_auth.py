"""Accesso, sessione, CSRF, token dei dispositivi.

La VPS e esposta su internet e ci sta sopra roba che non e nostra: queste
prove sono il motivo per cui si puo mettere un servizio in piu su quella
macchina.
"""

from __future__ import annotations

from typing import Any

import pytest
from fastapi.testclient import TestClient

from tests.conftest import PAROLA, UTENTE, documento_base

SOLO_CON_SESSIONE = [
    ("get", "/api/v1/tablets", None),
    ("post", "/api/v1/tablets", {"nome": "Tablet"}),
    ("get", "/api/v1/tablets/0123456789abcdef/config", None),
    ("put", "/api/v1/tablets/0123456789abcdef/config", {}),
    ("get", "/api/v1/tablets/0123456789abcdef/history", None),
    ("post", "/api/v1/tablets/0123456789abcdef/revert", {"revisione": 1}),
    ("delete", "/api/v1/tablets/0123456789abcdef", None),
]


@pytest.mark.parametrize("metodo, rotta, corpo", SOLO_CON_SESSIONE)
def test_senza_sessione_401(
    account: TestClient, metodo: str, rotta: str, corpo: dict[str, Any] | None
) -> None:
    risposta = getattr(account, metodo)(rotta, json=corpo) if corpo is not None else getattr(
        account, metodo
    )(rotta)
    assert risposta.status_code == 401, risposta.text
    assert "errore" in risposta.json()


def test_login_sbagliato_non_dice_quale_meta_e_sbagliata(account: TestClient) -> None:
    """Niente messaggio che distingua "utente sbagliato" da "password sbagliata"."""
    utente_storto = account.post(
        "/api/v1/auth/login", json={"utente": "qualcunaltro", "password": PAROLA}
    )
    password_storta = account.post(
        "/api/v1/auth/login", json={"utente": UTENTE, "password": "sbagliatissima"}
    )
    assert utente_storto.status_code == 401
    assert password_storta.status_code == 401
    assert utente_storto.json() == password_storta.json()


def test_login_senza_account(cliente: TestClient) -> None:
    """Database vuoto: la stessa risposta, non un 500 e non un indizio."""
    risposta = cliente.post("/api/v1/auth/login", json={"utente": UTENTE, "password": PAROLA})
    assert risposta.status_code == 401, risposta.text


def test_il_cookie_di_sessione_ha_le_sue_difese(account: TestClient) -> None:
    risposta = account.post(
        "/api/v1/auth/login", json={"utente": UTENTE, "password": PAROLA}
    )
    assert risposta.status_code == 200, risposta.text
    cookie = [c for c in risposta.headers.get_list("set-cookie") if c.startswith("starkid_sessione=")]
    assert len(cookie) == 1
    testo = cookie[0].lower()
    assert "httponly" in testo
    assert "secure" in testo
    assert "samesite=strict" in testo
    assert "path=/" in testo


def test_scrittura_senza_csrf_403(account: TestClient) -> None:
    risposta = account.post(
        "/api/v1/auth/login", json={"utente": UTENTE, "password": PAROLA}
    )
    assert risposta.status_code == 200
    # Sessione buona, nessun token CSRF: la lettura passa, la scrittura no.
    assert account.get("/api/v1/tablets").status_code == 200
    senza = account.post("/api/v1/tablets", json={"nome": "Tablet"})
    assert senza.status_code == 403, senza.text

    sbagliato = account.post(
        "/api/v1/tablets",
        json={"nome": "Tablet"},
        headers={"x-csrf-token": "non-e-questo"},
    )
    assert sbagliato.status_code == 403, sbagliato.text

    giusto = account.post(
        "/api/v1/tablets",
        json={"nome": "Tablet"},
        headers={"x-csrf-token": risposta.json()["csrf"]},
    )
    assert giusto.status_code == 201, giusto.text


def test_senza_sessione_il_csrf_da_solo_non_basta(account: TestClient) -> None:
    """Prima si guarda la sessione: 401, non 403. La pagina deve sapere cosa fare."""
    risposta = account.post(
        "/api/v1/tablets", json={"nome": "Tablet"}, headers={"x-csrf-token": "inventato"}
    )
    assert risposta.status_code == 401, risposta.text


def test_logout_chiude_la_sessione(genitore: TestClient) -> None:
    assert genitore.get("/api/v1/tablets").status_code == 200
    uscita = genitore.post("/api/v1/auth/logout")
    assert uscita.status_code == 200, uscita.text
    assert genitore.get("/api/v1/tablets").status_code == 401


def test_logout_vuole_il_csrf(account: TestClient) -> None:
    account.post("/api/v1/auth/login", json={"utente": UTENTE, "password": PAROLA})
    assert account.post("/api/v1/auth/logout").status_code == 403


# ---------------------------------------------------------------------------
# Attesa crescente
# ---------------------------------------------------------------------------


def test_attesa_crescente(account: TestClient) -> None:
    # I primi tre tentativi sono gratis: una password sbagliata capita.
    for tentativo in range(3):
        risposta = account.post(
            "/api/v1/auth/login", json={"utente": UTENTE, "password": "no"}
        )
        assert risposta.status_code == 401, "tentativo %d: %s" % (tentativo, risposta.text)

    bloccato = account.post("/api/v1/auth/login", json={"utente": UTENTE, "password": "no"})
    assert bloccato.status_code == 429, bloccato.text
    assert int(bloccato.headers["retry-after"]) > 0

    # E vale anche per la password giusta: l'attesa non si aggira indovinando.
    con_la_giusta = account.post(
        "/api/v1/auth/login", json={"utente": UTENTE, "password": PAROLA}
    )
    assert con_la_giusta.status_code == 429, con_la_giusta.text


# ---------------------------------------------------------------------------
# Dispositivi
# ---------------------------------------------------------------------------


@pytest.mark.parametrize(
    "intestazioni",
    [
        {},
        {"Authorization": "Bearer"},
        {"Authorization": "Bearer inventato-di-sana-pianta"},
        {"Authorization": "Basic cGlwcG86cGx1dG8="},
    ],
)
def test_il_tablet_senza_token_buono_401(
    account: TestClient, intestazioni: dict[str, str]
) -> None:
    for rotta, corpo in (
        ("/api/v1/device/config", None),
        ("/api/v1/device/ack", {"revisione": 1, "esito": "applied"}),
        (
            "/api/v1/device/health",
            {
                "device_owner": True,
                "usage_access": True,
                "overlay": True,
                "guard_running": True,
                "default_home": True,
            },
        ),
    ):
        if corpo is None:
            risposta = account.get(rotta, headers=intestazioni)
        else:
            risposta = account.post(rotta, headers=intestazioni, json=corpo)
        assert risposta.status_code == 401, "%s: %s" % (rotta, risposta.text)


def test_il_token_di_arruolamento_vale_una_volta_sola(
    genitore: TestClient, tablet: dict[str, Any]
) -> None:
    primo = genitore.post(
        "/api/v1/device/enroll", json={"token_arruolamento": tablet["token_arruolamento"]}
    )
    assert primo.status_code == 201, primo.text

    secondo = genitore.post(
        "/api/v1/device/enroll", json={"token_arruolamento": tablet["token_arruolamento"]}
    )
    assert secondo.status_code == 401, secondo.text


def test_arruolamento_con_un_token_inventato(genitore: TestClient) -> None:
    risposta = genitore.post(
        "/api/v1/device/enroll", json={"token_arruolamento": "a" * 43}
    )
    assert risposta.status_code == 401, risposta.text


def test_il_token_del_dispositivo_non_apre_la_web_ui(
    genitore: TestClient, dispositivo: dict[str, Any]
) -> None:
    """Due autenticazioni separate: il tablet non e il genitore."""
    genitore.cookies.clear()
    genitore.headers.pop("x-csrf-token", None)
    risposta = genitore.get("/api/v1/tablets", headers=dispositivo["intestazioni"])
    assert risposta.status_code == 401, risposta.text


def test_il_tablet_legge_e_conferma(genitore: TestClient, dispositivo: dict[str, Any]) -> None:
    configurazione = genitore.get(
        "/api/v1/device/config", headers=dispositivo["intestazioni"]
    )
    assert configurazione.status_code == 200, configurazione.text
    revisione = configurazione.json()["revisione"]

    ack = genitore.post(
        "/api/v1/device/ack",
        headers=dispositivo["intestazioni"],
        json={"revisione": revisione, "esito": "applied"},
    )
    assert ack.status_code == 200, ack.text

    elenco = genitore.get("/api/v1/tablets").json()["tablets"]
    assert elenco[0]["allineato"] is True
    assert elenco[0]["arruolato"] is True


def test_il_tablet_non_conferma_una_revisione_che_non_esiste(
    genitore: TestClient, dispositivo: dict[str, Any]
) -> None:
    risposta = genitore.post(
        "/api/v1/device/ack",
        headers=dispositivo["intestazioni"],
        json={"revisione": 99, "esito": "applied"},
    )
    assert risposta.status_code == 409, risposta.text


# ---------------------------------------------------------------------------
# Il kiosk e asimmetrico
# ---------------------------------------------------------------------------


def test_accendere_il_kiosk_e_solo_una_richiesta(
    genitore: TestClient, dispositivo: dict[str, Any]
) -> None:
    scritta = genitore.put(
        "/api/v1/tablets/%s/config" % dispositivo["id"], json=documento_base(kiosk=True)
    )
    assert scritta.status_code == 200, scritta.text
    assert scritta.json()["kiosk_stato"] == "in attesa"

    # Il tablet dice che non l'ha acceso: nessuno ha confermato col PIN.
    genitore.post(
        "/api/v1/device/ack",
        headers=dispositivo["intestazioni"],
        json={
            "revisione": scritta.json()["revisione"],
            "esito": "partial",
            "campi_rifiutati": [{"campo": "kiosk", "motivo": "serve il PIN sul tablet"}],
        },
    )
    elenco = genitore.get("/api/v1/tablets").json()["tablets"]
    assert elenco[0]["kiosk"] == "in attesa"

    # Spegnerlo invece e un ordine, e basta applicarlo.
    spenta = genitore.put(
        "/api/v1/tablets/%s/config" % dispositivo["id"], json=documento_base(kiosk=False)
    )
    assert spenta.status_code == 200, spenta.text
    assert "kiosk_stato" not in spenta.json()
    genitore.post(
        "/api/v1/device/ack",
        headers=dispositivo["intestazioni"],
        json={"revisione": spenta.json()["revisione"], "esito": "applied"},
    )
    elenco = genitore.get("/api/v1/tablets").json()["tablets"]
    assert elenco[0]["kiosk"] == "spento"


# ---------------------------------------------------------------------------
# /healthz non racconta niente
# ---------------------------------------------------------------------------


def test_healthz_non_rivela_niente(cliente: TestClient) -> None:
    risposta = cliente.get("/healthz")
    assert risposta.status_code == 200
    assert risposta.json() == {"stato": "ok"}
    testo = risposta.text.lower()
    for parola in ("python", "uvicorn", "fastapi", "sqlite", "/opt", "/var", "versione"):
        assert parola not in testo


def test_la_documentazione_automatica_e_spenta(cliente: TestClient) -> None:
    assert cliente.get("/api/docs").status_code == 404
    assert cliente.get("/api/openapi.json").status_code == 404
