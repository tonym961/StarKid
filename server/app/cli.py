"""Amministrazione da riga di comando.

L'account si crea da qui e solo da qui: non esiste nessun endpoint che lo
faccia, perche "niente iscrizione pubblica" si rispetta non scrivendo la
rotta, non mettendoci un controllo davanti.

Sulla macchina di destinazione, con il venv e l'utente di servizio:

    cd /opt/starkid
    sudo -u starkid venv/bin/python -m app.cli crea-utente genitore
    sudo -u starkid venv/bin/python -m app.cli cambia-password
    sudo -u starkid venv/bin/python -m app.cli stato
    sudo -u starkid venv/bin/python -m app.cli sblocca

La password non si passa come argomento: gli argomenti si vedono in `ps` e
restano nella cronologia della shell. Si digita, senza eco. Se lo standard
input non e un terminale la si legge da li, cosi uno script di installazione
puo fare la sua parte senza stamparla da nessuna parte.
"""

from __future__ import annotations

import argparse
import getpass
import sys

from . import auth, db


def _password_dalla_mano(conferma: bool = True) -> str:
    if not sys.stdin.isatty():
        return sys.stdin.readline().rstrip("\n")
    prima = getpass.getpass("Password (almeno %d caratteri): " % auth.LUNGHEZZA_MINIMA_PASSWORD)
    if not conferma:
        return prima
    seconda = getpass.getpass("Di nuovo: ")
    if prima != seconda:
        raise SystemExit("le due password non sono uguali")
    return prima


def crea_utente(argomenti: argparse.Namespace) -> int:
    db.inizializza()
    try:
        auth.crea_utente(argomenti.nome, _password_dalla_mano())
    except ValueError as errore:
        print("no: %s" % errore, file=sys.stderr)
        return 1
    print("account %r creato" % argomenti.nome)
    return 0


def cambia_password(argomenti: argparse.Namespace) -> int:
    db.inizializza()
    try:
        auth.cambia_password(_password_dalla_mano())
    except ValueError as errore:
        print("no: %s" % errore, file=sys.stderr)
        return 1
    print("password cambiata, le sessioni aperte sono state chiuse")
    return 0


def stato(argomenti: argparse.Namespace) -> int:
    """Quello che /healthz non dice, detto a chi e entrato nella macchina."""
    db.inizializza()
    utente = auth.utente_unico()
    print("database:        %s" % db.percorso())
    print("schema:          versione %d" % db.versione())
    print("account:         %s" % (utente["utente"] if utente else "nessuno, va creato"))
    print("hash password:   %s" % ("scrypt" if auth.SCRYPT else "pbkdf2_sha256"))
    righe = db.leggi_tutti("SELECT id, nome, revisione, hash_dispositivo FROM tablet ORDER BY creato_il")
    print("tablet:          %d" % len(righe))
    for riga in righe:
        print(
            "  %s  rev %-4s %-10s %s"
            % (
                riga["id"],
                riga["revisione"],
                "arruolato" if riga["hash_dispositivo"] else "da arruolare",
                riga["nome"],
            )
        )
    sessioni = db.leggi_uno("SELECT COUNT(*) AS quante FROM sessione")
    print("sessioni aperte: %s" % (sessioni["quante"] if sessioni else 0))
    return 0


def sblocca(argomenti: argparse.Namespace) -> int:
    """Azzera l'attesa crescente, per quando il genitore si e chiuso fuori da solo."""
    db.inizializza()
    db.esegui("DELETE FROM tentativo")
    print("tentativi azzerati")
    return 0


# Sette giorni, come all'arruolamento: lo stesso numero sta in main.py, e qui
# si ripete di proposito invece di importarlo, perche importare main da un
# comando di riga vorrebbe dire tirare dentro FastAPI per cambiare una riga.
DURATA_ARRUOLAMENTO = 7 * 86400


def riarruola(argomenti: argparse.Namespace) -> int:
    """Rimette un tablet in attesa di arruolamento, conservando storico e id.

    E l'unico modo di revocare il token di un dispositivo. Serve in tre casi
    che prima erano vicoli ciechi: il tablet venduto o perso, il token
    finito in mano a qualcuno (il figlio piu grande che si legge il bundle di
    provisioning), e il token di arruolamento scaduto prima del primo avvio -
    in quel caso la riga restava con un hash non riutilizzabile e non c'era
    nessun modo di riemetterlo.

    L'alternativa era cancellare il tablet e ricrearlo, che butta via le dieci
    revisioni e cambia l'id: tutta la configurazione da rifare a mano.
    """
    db.inizializza()
    riga = db.leggi_uno(
        "SELECT id, nome, hash_dispositivo FROM tablet WHERE id = ?", (argomenti.id,)
    )
    if riga is None:
        print("non c'e nessun tablet con id %s (vedi 'stato')" % argomenti.id, file=sys.stderr)
        return 1

    token = auth.nuovo_token()
    scade = db.adesso() + DURATA_ARRUOLAMENTO
    db.esegui(
        "UPDATE tablet SET hash_dispositivo = NULL, arruolato_il = NULL, "
        "hash_arruolamento = ?, arruolamento_scade = ? WHERE id = ?",
        (auth.hash_token(token), scade, argomenti.id),
    )
    if riga["hash_dispositivo"]:
        print("il token di prima non vale piu: quel tablet non legge piu la configurazione")
    print("tablet:              %s (%s)" % (riga["id"], riga["nome"]))
    print("storico e id:        conservati")
    print("token di arruolamento (una volta sola, vale 7 giorni):")
    print()
    print("    %s" % token)
    print()
    print("Mettilo nel QR dalla pagina del tablet, oppure passalo al tablet a mano.")
    return 0


def principale(argv: list[str] | None = None) -> int:
    analizzatore = argparse.ArgumentParser(
        prog="python -m app.cli", description="Amministrazione del server StarKid"
    )
    comandi = analizzatore.add_subparsers(dest="comando", required=True)

    uno = comandi.add_parser("crea-utente", help="crea l'unico account (una volta sola)")
    uno.add_argument("nome", help="nome utente, non email")
    uno.set_defaults(funzione=crea_utente)

    due = comandi.add_parser("cambia-password", help="cambia la password e chiude le sessioni")
    due.set_defaults(funzione=cambia_password)

    tre = comandi.add_parser("stato", help="cosa c'e nel database")
    tre.set_defaults(funzione=stato)

    quattro = comandi.add_parser("sblocca", help="azzera i tentativi di accesso falliti")
    quattro.set_defaults(funzione=sblocca)

    cinque = comandi.add_parser(
        "riarruola",
        help="revoca il token di un tablet e ne emette uno nuovo, tenendo storico e id",
    )
    cinque.add_argument("id", help="l'id del tablet, 16 cifre esadecimali (vedi 'stato')")
    cinque.set_defaults(funzione=riarruola)

    argomenti = analizzatore.parse_args(argv)
    try:
        return int(argomenti.funzione(argomenti))
    finally:
        db.chiudi_tutte()


if __name__ == "__main__":
    raise SystemExit(principale())
