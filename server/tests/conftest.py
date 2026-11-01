"""Attrezzatura comune ai test.

Ogni test ha il suo file di database, in una cartella temporanea: niente stato
che passa da un test all'altro e niente bisogno di ripulire.

Il client parla in **https**: i cookie di sessione hanno il flag Secure, e un
client che parlasse in http non li rimanderebbe indietro. Provare in https
significa provare la configurazione vera invece di spegnere una difesa per
comodita.
"""

from __future__ import annotations

import sys
from pathlib import Path
from typing import Any, Iterator

import pytest
from fastapi.testclient import TestClient

RADICE = Path(__file__).resolve().parent.parent
if str(RADICE) not in sys.path:
    sys.path.insert(0, str(RADICE))

from app import auth, config_doc, db  # noqa: E402
from app.main import app  # noqa: E402

UTENTE = "genitore"
PAROLA = "sottomarino-giallo-42"


@pytest.fixture
def cliente(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> Iterator[TestClient]:
    """Client senza sessione, con un database tutto suo."""
    monkeypatch.setenv("STARKID_DB", str(tmp_path / "prova.db"))
    db.chiudi_tutte()
    # Il context manager fa partire il lifespan, che crea le tabelle.
    with TestClient(app, base_url="https://testserver") as client:
        yield client
    db.chiudi_tutte()


@pytest.fixture
def account(cliente: TestClient) -> TestClient:
    """Database con l'unico account creato, come lo crea la CLI via SSH."""
    auth.crea_utente(UTENTE, PAROLA, ruolo="admin")
    return cliente


@pytest.fixture
def genitore(account: TestClient) -> TestClient:
    """Client con la sessione aperta e il token CSRF gia in testa."""
    risposta = account.post(
        "/api/v1/auth/login", json={"utente": UTENTE, "password": PAROLA}
    )
    assert risposta.status_code == 200, risposta.text
    account.headers["x-csrf-token"] = risposta.json()["csrf"]
    return account


@pytest.fixture
def tablet(genitore: TestClient) -> dict[str, Any]:
    """Un tablet appena creato, con il suo token di arruolamento."""
    risposta = genitore.post("/api/v1/tablets", json={"nome": "Tablet di camera"})
    assert risposta.status_code == 201, risposta.text
    return risposta.json()


@pytest.fixture
def dispositivo(genitore: TestClient, tablet: dict[str, Any]) -> dict[str, Any]:
    """Il tablet arruolato: id e token del dispositivo, piu le intestazioni."""
    risposta = genitore.post(
        "/api/v1/device/enroll",
        json={"token_arruolamento": tablet["token_arruolamento"]},
    )
    assert risposta.status_code == 201, risposta.text
    dati = risposta.json()
    return {
        "id": dati["id_dispositivo"],
        "token": dati["token_dispositivo"],
        "intestazioni": {"Authorization": "Bearer %s" % dati["token_dispositivo"]},
    }


def documento_base(**modifiche: Any) -> dict[str, Any]:
    """Un documento valido e completo, da storpiare nei test."""
    documento = config_doc.valori_iniziali()
    documento.update(modifiche)
    return documento
