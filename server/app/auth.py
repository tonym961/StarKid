"""Accesso del genitore, sessioni, CSRF e token dei dispositivi.

Niente dipendenze nuove: la libreria standard ha tutto. `hashlib.scrypt` per
la password, `hmac.compare_digest` per i confronti, `secrets` per i token. Una
libreria in meno e anche qualche megabyte di RAM in meno e una cosa in meno da
aggiornare quando esce la sua prossima falla.

Come stanno i segreti:

- la password del genitore: hash scrypt con sale, mai in chiaro, mai nei log;
- il token di sessione: nel cookie per il browser, nel database **solo come
  hash**, cosi chi legge il file del database non entra in una sessione aperta;
- i token dei dispositivi: li vede solo il tablet che li riceve, nel database
  **solo come hash**;
- il token di arruolamento: si mostra una volta al genitore (ci fa il QR nel
  browser) e nel database sta **solo come hash**. Se lo perde se ne fa un
  altro, non si recupera quello.

I token sono 256 bit di casualita, percio per ritrovarli basta SHA-256: non
serve una funzione lenta, che serve a difendere i segreti che una persona puo
inventare: le password.
"""

from __future__ import annotations

import hashlib
import hmac
import os
import secrets
import sqlite3
import threading
from typing import Any

from fastapi import Depends, HTTPException, Request, Response, status

from . import db

# ---------------------------------------------------------------------------
# Password
# ---------------------------------------------------------------------------

# 2**14 * 8 * 128 = 16 MiB per verifica. Su una macchina con 446 MB liberi e il
# punto in cui una password debole costa cara a chi la prova e niente a chi
# entra una volta al giorno. Il limite di memoria si passa esplicito perche il
# valore di default di OpenSSL (32 MB) e vicino.
SCRYPT_N = 2**14
SCRYPT_R = 8
SCRYPT_P = 1
SCRYPT_MAXMEM = 64 * 1024 * 1024

# Se l'OpenSSL della macchina fosse senza scrypt si usa pbkdf2, che c'e sempre.
PBKDF2_GIRI = 600_000

LUNGHEZZA_MINIMA_PASSWORD = 12


def _scrypt_disponibile() -> bool:
    try:
        hashlib.scrypt(b"x", salt=b"y", n=2, r=8, p=1, maxmem=SCRYPT_MAXMEM)
    except (ValueError, AttributeError, OSError):
        return False
    return True


SCRYPT = _scrypt_disponibile()


def cifra_password(password: str) -> str:
    """Restituisce la stringa da salvare: algoritmo, parametri, sale e hash."""
    sale = secrets.token_bytes(16)
    if SCRYPT:
        grezzo = hashlib.scrypt(
            password.encode("utf-8"),
            salt=sale,
            n=SCRYPT_N,
            r=SCRYPT_R,
            p=SCRYPT_P,
            maxmem=SCRYPT_MAXMEM,
            dklen=32,
        )
        return "scrypt$%d$%d$%d$%s$%s" % (
            SCRYPT_N,
            SCRYPT_R,
            SCRYPT_P,
            sale.hex(),
            grezzo.hex(),
        )
    grezzo = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), sale, PBKDF2_GIRI, 32)
    return "pbkdf2_sha256$%d$%s$%s" % (PBKDF2_GIRI, sale.hex(), grezzo.hex())


# Quante verifiche di password possono vivere insieme. Due, non quaranta.
#
# Ogni verifica costa 16 MiB, e si paga anche quando il nome utente non
# esiste (brucia_tempo). Gli endpoint sono "def", quindi FastAPI li manda nel
# threadpool di anyio, che di default ha 40 posti; nginx concede burst=5 per
# indirizzo. Da qualche indirizzo si ottenevano decine di scrypt in parallelo,
# cioe centinaia di MB sopra un cgroup da 192 MB: il kernel uccide il
# servizio, systemd lo riavvia dopo cinque secondi, e si ricomincia finche
# l'attacco dura. L'attesa crescente non copre questo caso, perche conta i
# fallimenti GIA scritti e le richieste in volo la passano tutte insieme.
#
# Con due posti il tetto diventa 32 MiB e chi insiste aspetta il suo turno
# invece di far cadere il servizio. Per un genitore che entra una volta al
# giorno, due sono abbondanti.
_VERIFICHE = threading.Semaphore(2)


def verifica_password(password: str, memorizzato: str) -> bool:
    """Confronto a tempo costante. Una stringa storta vale 'no', non un errore."""
    with _VERIFICHE:
        return _verifica_password(password, memorizzato)


def _verifica_password(password: str, memorizzato: str) -> bool:
    try:
        pezzi = memorizzato.split("$")
        if pezzi[0] == "scrypt":
            n, r, p, sale_hex, atteso_hex = pezzi[1:6]
            calcolato = hashlib.scrypt(
                password.encode("utf-8"),
                salt=bytes.fromhex(sale_hex),
                n=int(n),
                r=int(r),
                p=int(p),
                maxmem=SCRYPT_MAXMEM,
                dklen=len(atteso_hex) // 2,
            )
        elif pezzi[0] == "pbkdf2_sha256":
            giri, sale_hex, atteso_hex = pezzi[1:4]
            calcolato = hashlib.pbkdf2_hmac(
                "sha256",
                password.encode("utf-8"),
                bytes.fromhex(sale_hex),
                int(giri),
                len(atteso_hex) // 2,
            )
        else:
            return False
    except (ValueError, IndexError, TypeError):
        return False
    return hmac.compare_digest(calcolato, bytes.fromhex(atteso_hex))


# Password finta con cui confrontare quando l'utente non esiste: senza questa,
# un nome sbagliato risponderebbe molto piu in fretta di una password
# sbagliata, e il messaggio uguale per tutti non servirebbe a niente perche il
# cronometro racconterebbe la verita.
PASSWORD_FINTA = cifra_password(secrets.token_urlsafe(24))


def brucia_tempo() -> None:
    verifica_password("qualunque cosa", PASSWORD_FINTA)


# ---------------------------------------------------------------------------
# Token
# ---------------------------------------------------------------------------


def nuovo_token() -> str:
    """256 bit di casualita in forma trasportabile (anche dentro un QR)."""
    return secrets.token_urlsafe(32)


def hash_token(token: str) -> str:
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


# ---------------------------------------------------------------------------
# Tentativi: attesa crescente
# ---------------------------------------------------------------------------

FINESTRA_TENTATIVI = 3600
# I primi tre tentativi sono gratis: una password sbagliata capita. Dal quarto
# si aspetta, e si aspetta sempre di piu, fino a un quarto d'ora.
ATTESE = (0, 0, 0, 5, 15, 60, 300, 900)


def attesa_per(fallimenti: int) -> int:
    return ATTESE[min(fallimenti, len(ATTESE) - 1)]


def attesa_residua(chiavi: list[str]) -> int:
    """Quanti secondi deve ancora aspettare chi sta provando. 0 = puo provare."""
    ora = db.adesso()
    residua = 0
    for chiave in chiavi:
        riga = db.leggi_uno(
            "SELECT COUNT(*) AS quanti, MAX(istante) AS ultimo "
            "FROM tentativo WHERE chiave = ? AND istante > ?",
            (chiave, ora - FINESTRA_TENTATIVI),
        )
        if riga is None or not riga["quanti"]:
            continue
        atteso = attesa_per(int(riga["quanti"]))
        residua = max(residua, int(riga["ultimo"]) + atteso - ora)
    return max(residua, 0)


def registra_fallimento(chiavi: list[str]) -> None:
    ora = db.adesso()
    with db.scrittura() as conn:
        for chiave in chiavi:
            conn.execute(
                "INSERT INTO tentativo (chiave, istante) VALUES (?, ?)", (chiave, ora)
            )
        # Potatura qui dentro: un indirizzo che ha provato ieri non e piu un
        # sospetto, e senza cron non c'e nessun altro che passa a pulire.
        conn.execute("DELETE FROM tentativo WHERE istante < ?", (ora - FINESTRA_TENTATIVI * 24,))


def azzera_tentativi(chiavi: list[str]) -> None:
    with db.scrittura() as conn:
        for chiave in chiavi:
            conn.execute("DELETE FROM tentativo WHERE chiave = ?", (chiave,))


def vieta_se_in_attesa(chiavi: list[str]) -> None:
    secondi = attesa_residua(chiavi)
    if secondi > 0:
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail="troppi tentativi, riprova fra %d secondi" % secondi,
            headers={"Retry-After": str(secondi)},
        )


def indirizzo(request: Request) -> str:
    """L'indirizzo di chi chiama, usato solo per contare i tentativi.

    uvicorn ascolta su 127.0.0.1 e davanti c'e nginx, quindi il peer e sempre
    il proxy: l'indirizzo vero sta negli header. Si guardano **solo** se il
    peer e il loopback, altrimenti chiunque potrebbe scegliersi l'identita con
    cui farsi contare i tentativi.
    """
    peer = request.client.host if request.client else ""
    # "testclient" e il nome che si da il client dei test: sta in questo elenco
    # perche anche la parte con gli header va provata, e non puo essere
    # l'indirizzo di nessuna connessione vera.
    if peer not in ("127.0.0.1", "::1", "localhost", "testclient"):
        return peer or "sconosciuto"
    inoltrato = request.headers.get("x-forwarded-for")
    if inoltrato:
        # nginx usa $proxy_add_x_forwarded_for: l'ultimo pezzo e quello che ha
        # aggiunto lui, cioe chi ha davvero aperto la connessione.
        ultimo = inoltrato.split(",")[-1].strip()
        if ultimo:
            return ultimo[:64]
    reale = request.headers.get("x-real-ip")
    if reale:
        return reale.strip()[:64]
    return peer or "sconosciuto"


# ---------------------------------------------------------------------------
# Sessione del genitore
# ---------------------------------------------------------------------------

NOME_COOKIE = "starkid_sessione"
# Il token CSRF viaggia in un secondo cookie, questo si leggibile dal
# JavaScript della pagina: la pagina lo rimette nell'header a ogni scrittura.
# Il confronto pero non e fra cookie e header (sarebbe il "double submit", che
# cade se qualcuno riesce a scrivere un cookie sul dominio) ma fra l'header e
# il valore salvato in tabella per quella sessione.
NOME_COOKIE_CSRF = "starkid_csrf"
NOME_HEADER_CSRF = "x-csrf-token"
# Dodici ore di inattivita: una serata al tablet del figlio sta dentro, una
# sessione dimenticata aperta no.
DURATA_INATTIVITA = 12 * 3600
# Sette giorni dal login, qualunque cosa accada: una sessione non diventa
# eterna a forza di essere usata.
DURATA_MASSIMA = 7 * 86400
# Per non scrivere sul database a ogni richiesta: l'ultimo accesso si aggiorna
# al massimo una volta al minuto.
PASSO_RINFRESCO = 60


def cookie_sicuro() -> bool:
    """Secure acceso, sempre, tranne se qualcuno lo spegne a mano per provare in locale."""
    return os.environ.get("STARKID_COOKIE_SECURE", "1") not in ("0", "no", "false")


def crea_sessione() -> tuple[str, str]:
    """Nuova sessione. Restituisce (token da mettere nel cookie, token CSRF)."""
    token = nuovo_token()
    csrf = nuovo_token()
    ora = db.adesso()
    with db.scrittura() as conn:
        # Ogni login butta via le sessioni scadute: anche questa e una pulizia
        # che senza cron deve farsi da sola.
        conn.execute("DELETE FROM sessione WHERE scade_il < ?", (ora,))
        conn.execute(
            "INSERT INTO sessione (hash_token, csrf, creato_il, visto_il, scade_il) "
            "VALUES (?, ?, ?, ?, ?)",
            (hash_token(token), csrf, ora, ora, ora + DURATA_INATTIVITA),
        )
    return token, csrf


def elimina_sessione(token: str) -> None:
    db.esegui("DELETE FROM sessione WHERE hash_token = ?", (hash_token(token),))


def sessione_da_token(token: str) -> sqlite3.Row | None:
    ora = db.adesso()
    riga = db.leggi_uno(
        "SELECT * FROM sessione WHERE hash_token = ?", (hash_token(token),)
    )
    if riga is None:
        return None
    if riga["scade_il"] <= ora or riga["creato_il"] + DURATA_MASSIMA <= ora:
        db.esegui("DELETE FROM sessione WHERE hash_token = ?", (riga["hash_token"],))
        return None
    if ora - int(riga["visto_il"]) >= PASSO_RINFRESCO:
        db.esegui(
            "UPDATE sessione SET visto_il = ?, scade_il = ? WHERE hash_token = ?",
            (ora, ora + DURATA_INATTIVITA, riga["hash_token"]),
        )
    return riga


def imposta_cookie(response: Response, token: str) -> None:
    response.set_cookie(
        NOME_COOKIE,
        token,
        max_age=DURATA_MASSIMA,
        httponly=True,  # il JavaScript della pagina non deve poterlo leggere
        secure=cookie_sicuro(),  # solo su https
        samesite="strict",  # nessun altro sito lo porta con se
        path="/",
    )


def imposta_cookie_csrf(response: Response, csrf: str) -> None:
    response.set_cookie(
        NOME_COOKIE_CSRF,
        csrf,
        max_age=DURATA_MASSIMA,
        httponly=False,  # qui serve che la pagina lo legga
        secure=cookie_sicuro(),
        samesite="strict",
        path="/",
    )


def togli_cookie(response: Response) -> None:
    response.delete_cookie(
        NOME_COOKIE,
        path="/",
        httponly=True,
        secure=cookie_sicuro(),
        samesite="strict",
    )


def togli_cookie_csrf(response: Response) -> None:
    response.delete_cookie(
        NOME_COOKIE_CSRF,
        path="/",
        httponly=False,
        secure=cookie_sicuro(),
        samesite="strict",
    )


def sessione_attiva(request: Request) -> sqlite3.Row:
    """Dipendenza: serve una sessione valida, altrimenti 401."""
    token = request.cookies.get(NOME_COOKIE)
    sessione = sessione_da_token(token) if token else None
    if sessione is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED, detail="serve l'accesso"
        )
    return sessione


def sessione_scrivibile(
    request: Request, sessione: sqlite3.Row = Depends(sessione_attiva)
) -> sqlite3.Row:
    """Dipendenza delle scritture: sessione valida **piu** token CSRF.

    Il cookie e SameSite=Strict, che da solo fermerebbe quasi tutto; il token
    e la seconda serratura, perche "quasi" non e una parola da sicurezza. Chi
    non lo manda prende 403, non 401: la differenza serve alla pagina, che nel
    primo caso deve rifare l'accesso e nel secondo ricaricare il token.
    """
    # Il confronto si fa su byte, non su str. compare_digest su due stringhe
    # pretende che siano ASCII e altrimenti alza TypeError: un'intestazione
    # con un carattere accentato diventerebbe un 500 con tanto di traceback
    # nel journal, invece del 403 che le spetta.
    inviato = request.headers.get(NOME_HEADER_CSRF, "").encode("utf-8", "ignore")
    atteso = str(sessione["csrf"]).encode("utf-8")
    if not inviato or not hmac.compare_digest(inviato, atteso):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN, detail="token CSRF mancante o sbagliato"
        )
    return sessione


# ---------------------------------------------------------------------------
# Account: uno solo
# ---------------------------------------------------------------------------


def utente_unico() -> sqlite3.Row | None:
    return db.leggi_uno("SELECT * FROM utente WHERE id = 1")


def crea_utente(nome: str, password: str) -> None:
    """Crea l'unico account. Da riga di comando, mai da HTTP.

    Non esiste nessun endpoint che faccia questo: niente iscrizione pubblica
    era una decisione, e il modo di rispettarla e non scrivere la rotta.
    """
    nome = nome.strip()
    if not nome:
        raise ValueError("il nome utente non puo essere vuoto")
    if len(password) < LUNGHEZZA_MINIMA_PASSWORD:
        raise ValueError("la password deve essere lunga almeno %d caratteri" % LUNGHEZZA_MINIMA_PASSWORD)
    ora = db.adesso()
    try:
        db.esegui(
            "INSERT INTO utente (id, utente, password, creato_il, aggiornato_il) "
            "VALUES (1, ?, ?, ?, ?)",
            (nome, cifra_password(password), ora, ora),
        )
    except sqlite3.IntegrityError as errore:
        raise ValueError(
            "l'account esiste gia: questo server ne tiene uno solo, usa cambia-password"
        ) from errore


def cambia_password(password: str) -> None:
    if len(password) < LUNGHEZZA_MINIMA_PASSWORD:
        raise ValueError("la password deve essere lunga almeno %d caratteri" % LUNGHEZZA_MINIMA_PASSWORD)
    if utente_unico() is None:
        raise ValueError("non c'e nessun account da cambiare")
    with db.scrittura() as conn:
        conn.execute(
            "UPDATE utente SET password = ?, aggiornato_il = ? WHERE id = 1",
            (cifra_password(password), db.adesso()),
        )
        # Cambiare password chiude tutte le sessioni aperte: e il senso di
        # cambiarla.
        conn.execute("DELETE FROM sessione")


def accedi(nome: str, password: str) -> bool:
    """Vero se le credenziali sono giuste.

    Un nome che non esiste costa lo stesso tempo di una password sbagliata, e
    chi chiama risponde la stessa frase nei due casi.
    """
    utente = utente_unico()
    # compare_digest su byte e non su str: un nome con un accento farebbe
    # TypeError sulla versione con le stringhe.
    if utente is None or not hmac.compare_digest(
        str(utente["utente"]).encode("utf-8"), nome.encode("utf-8")
    ):
        brucia_tempo()
        return False
    return verifica_password(password, str(utente["password"]))


# ---------------------------------------------------------------------------
# Dispositivi
# ---------------------------------------------------------------------------


def dispositivo(request: Request) -> sqlite3.Row:
    """Dipendenza dei tablet: `Authorization: Bearer <token del dispositivo>`."""
    intestazione = request.headers.get("authorization", "")
    pezzi = intestazione.split(" ", 1)
    if len(pezzi) != 2 or pezzi[0].lower() != "bearer":
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="serve il token del dispositivo",
            headers={"WWW-Authenticate": "Bearer"},
        )
    token = pezzi[1].strip()
    riga = (
        db.leggi_uno(
            "SELECT * FROM tablet WHERE hash_dispositivo = ?", (hash_token(token),)
        )
        if token
        else None
    )
    if riga is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="token del dispositivo non valido",
            headers={"WWW-Authenticate": "Bearer"},
        )
    db.esegui("UPDATE tablet SET visto_il = ? WHERE id = ?", (db.adesso(), riga["id"]))
    return riga


def intestazioni_sicurezza() -> dict[str, Any]:
    """Intestazioni che il server mette su ogni risposta.

    Sono poche e tutte sul contenuto: niente HSTS ne CSP, che riguardano le
    pagine e le mette nginx, che e l'unico a sapere con quali domini e
    certificati sta lavorando.
    """
    return {
        "X-Content-Type-Options": "nosniff",
        "Referrer-Policy": "no-referrer",
        # Una configurazione in una cache intermedia e una configurazione che
        # torna indietro: la direzione della configurazione e una sola.
        "Cache-Control": "no-store",
    }
