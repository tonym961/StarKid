#!/usr/bin/env bash
# =============================================================================
# StarKid - attivazione della jail fail2ban, con le tre verifiche fatte prima
#
#     sudo ./deploy/attiva-fail2ban.sh [--check] [--ignoreip "1.2.3.4 5.6.7.0/24"]
#     sudo ./deploy/attiva-fail2ban.sh --disattiva
#
# PERCHE UNO SCRIPT E NON TRE RIGHE DI README. fail2ban qui non e nostro: su
# questa macchina tiene su quattro jail, fra cui sshd, che ha gia bloccato
# qualche migliaio di indirizzi. Una jail scritta male non resta un problema
# nostro - se il server non riparte, cadono anche quelle. Le tre verifiche del
# README servivano proprio a questo, e una verifica che si fa a mano e una
# verifica che un giorno non si fa. Quindi stanno qui dentro, e lo script si
# ferma da solo invece di fidarsi.
#
# Cosa fa, in ordine:
#   1. si segna quali jail sono attive ADESSO (serve al confronto finale)
#   2. il log deve esistere e non essere vuoto, altrimenti il server fail2ban
#      non riparte e si porta dietro sshd
#   3. prova il filtro sul log vero con fail2ban-regex, e dice quante righe ha
#      riconosciuto: se sono zero avvisa, perche vuol dire che nessuno ha
#      ancora sbagliato una password e il filtro non e stato provato su niente
#   4. copia filtro e jail, con enabled = true
#   5. "fail2ban-client -t": se la configurazione non passa, si ferma e
#      rimette i file come li ha trovati
#   6. chiede conferma, poi "reload" (NON restart: un restart azzera i blocchi
#      in corso delle altre jail, sshd compresa)
#   7. ricontrolla la lista: se e sparita una jail che c'era prima, torna
#      indietro da solo e lo dice
#
# Con --check fa 1-3 e si ferma: non copia e non ricarica niente.
# =============================================================================

set -euo pipefail

QUI=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
LOG=/var/log/starkid/access.log
FILTRO_DST=/etc/fail2ban/filter.d/starkid-auth.conf
JAIL_DST=/etc/fail2ban/jail.d/starkid.conf
FILTRO_SRC="$QUI/fail2ban-starkid-filter.conf"
JAIL_SRC="$QUI/fail2ban-starkid.conf"
LOCK=/var/lock/starkid-fail2ban.lock

STAMP=$(date -u +%Y%m%dT%H%M%SZ)
SOLO_CHECK=no
DISATTIVA=no
IGNOREIP=""

rosso=''; giallo=''; verde=''; neutro=''
if [ -t 1 ]; then
    rosso=$'\033[31m'; giallo=$'\033[33m'; verde=$'\033[32m'; neutro=$'\033[0m'
fi
titolo() { printf '\n%s== %s ==%s\n' "$verde" "$*" "$neutro"; }
log()    { printf '   %s\n' "$*"; }
avviso() { printf '%s ! %s%s\n' "$giallo" "$*" "$neutro"; }
errore() { printf '%sERRORE: %s%s\n' "$rosso" "$*" "$neutro" >&2; exit 1; }

conferma() {
    local domanda=$1 parola=$2 risposta=''
    printf '\n%s\n' "$domanda"
    printf 'Scrivi esattamente "%s" per procedere (qualsiasi altra cosa si ferma): ' "$parola"
    if [ -r /dev/tty ]; then
        read -r risposta < /dev/tty || true
    elif [ -t 0 ]; then
        read -r risposta || true
    else
        printf '\n'
        errore "serve una conferma ma non c'e un terminale"
    fi
    [ "$risposta" = "$parola" ]
}

while [ $# -gt 0 ]; do
    case $1 in
        --check)      SOLO_CHECK=yes ;;
        --disattiva)  DISATTIVA=yes ;;
        --ignoreip)   shift; [ $# -gt 0 ] || errore "--ignoreip vuole un valore"
                      IGNOREIP=$1 ;;
        --help|-h)
            sed -n '2,32p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
            exit 0 ;;
        *) errore "argomento sconosciuto: $1" ;;
    esac
    shift
done

[ "$(id -u)" -eq 0 ] || errore "va eseguito come root (sudo ./deploy/attiva-fail2ban.sh)"
command -v fail2ban-client >/dev/null || errore "fail2ban-client non c'e: su questa macchina fail2ban non e installato"

exec 9>"$LOCK"
flock -n 9 || errore "c'e un altro attiva-fail2ban.sh in corso"

# Le jail che devono esserci anche dopo. Viene letta prima di toccare
# qualsiasi cosa: e il metro del confronto finale.
leggi_jail() {
    fail2ban-client status 2>/dev/null \
        | sed -n 's/^.*Jail list:[[:space:]]*//p' \
        | tr ',' '\n' | tr -d ' \t' | sed '/^$/d' | sort
}

# ---------------------------------------------------------------------------
# --disattiva: la strada del ritorno, prima di tutto il resto
# ---------------------------------------------------------------------------
if [ "$DISATTIVA" = yes ]; then
    titolo "Disattivazione"
    PRIMA=$(leggi_jail)
    log "jail attive adesso: $(echo "$PRIMA" | tr '\n' ' ')"
    if [ ! -f "$JAIL_DST" ]; then
        log "$JAIL_DST non c'e: niente da disattivare"
        exit 0
    fi
    mv "$JAIL_DST" "$JAIL_DST.disattivata-$STAMP"
    log "jail messa da parte come $JAIL_DST.disattivata-$STAMP"
    if ! fail2ban-client -t >/dev/null 2>&1; then
        mv "$JAIL_DST.disattivata-$STAMP" "$JAIL_DST"
        errore "senza la nostra jail la configurazione non passa: rimesso tutto come prima"
    fi
    fail2ban-client reload >/dev/null
    DOPO=$(leggi_jail)
    log "jail attive ora:    $(echo "$DOPO" | tr '\n' ' ')"
    MANCANTI=$(comm -23 <(echo "$PRIMA" | grep -v '^starkid-auth$') <(echo "$DOPO") || true)
    [ -z "$MANCANTI" ] || errore "sono sparite jail che c'erano prima: $MANCANTI"
    log "il filtro resta in $FILTRO_DST (inerte: nessuna jail lo usa)"
    printf '\n'
    exit 0
fi

# ---------------------------------------------------------------------------
titolo "1. Com'e adesso"
PRIMA=$(leggi_jail)
[ -n "$PRIMA" ] || errore "fail2ban non risponde: 'systemctl status fail2ban' prima di andare avanti"
log "jail attive: $(echo "$PRIMA" | tr '\n' ' ')"
log "versione:    $(fail2ban-client --version 2>/dev/null | head -1)"
if echo "$PRIMA" | grep -qx 'starkid-auth'; then
    avviso "la jail starkid-auth e GIA attiva: questo giro la riscrive e ricarica"
fi

# ---------------------------------------------------------------------------
titolo "2. Il log deve esistere"
# Questo e il controllo che conta davvero: con un logpath che non esiste il
# server fail2ban non riparte, e le quattro jail di prima restano a terra.
[ -f "$LOG" ] || errore "$LOG non c'e. Il vhost deve essere attivo e aver ricevuto almeno una richiesta."
RIGHE=$(wc -l < "$LOG")
[ "$RIGHE" -gt 0 ] || errore "$LOG e vuoto: nginx non ci ha ancora scritto niente"
log "$LOG: $RIGHE righe"
log "leggibile da fail2ban (gira da root): $(stat -c '%U:%G %a' "$LOG")"

# ---------------------------------------------------------------------------
titolo "3. Il filtro, provato sul log vero"
[ -f "$FILTRO_SRC" ] || errore "manca $FILTRO_SRC"
[ -f "$JAIL_SRC" ]   || errore "manca $JAIL_SRC"

# Si prova dalla copia in /tmp, non dalla destinazione: finche le verifiche non
# passano, /etc/fail2ban resta come era.
PROVA=$(mktemp -d)
trap 'rm -rf "$PROVA"' EXIT
install -m 0644 "$FILTRO_SRC" "$PROVA/starkid-auth.conf"

USCITA="$PROVA/regex.txt"
# Attenzione all'esito: fail2ban-regex torna 1 anche solo perche non ha trovato
# nessuna riga da bloccare, che e il caso normale su un server appena messo su.
# Quindi l'esito non si guarda: si guarda se ha prodotto il suo riepilogo.
fail2ban-regex "$LOG" "$PROVA/starkid-auth.conf" >"$USCITA" 2>&1 || true
if ! grep -q '^Lines:' "$USCITA"; then
    sed 's/^/   | /' "$USCITA"
    errore "fail2ban-regex non e arrivato in fondo: il filtro non e valido"
fi
TROVATE=$(sed -n 's/^Lines:.*[^0-9]\([0-9]\+\) matched.*/\1/p' "$USCITA" | head -1)
TROVATE=${TROVATE:-0}
log "righe riconosciute come accesso fallito: $TROVATE su $RIGHE"
if [ "$TROVATE" -eq 0 ]; then
    avviso "ZERO righe riconosciute. Non vuol dire che il filtro e sbagliato: vuol dire"
    avviso "che in quel log non c'e ancora nessun accesso fallito, quindi il filtro NON"
    avviso "e stato provato su niente. Per provarlo davvero, da un'altra macchina:"
    avviso "  curl -s -o /dev/null -w '%{http_code}\\n' -X POST \\"
    avviso "    https://starkid.antoninomiccio.com/api/v1/auth/login \\"
    avviso "    -H 'Content-Type: application/json' -d '{\"utente\":\"prova\",\"password\":\"x\"}'"
    avviso "Devono venire 401. Poi rilancia questo script."
else
    log "le righe riconosciute, viste da vicino:"
    grep -nE '"POST /api/v1/auth/login HTTP/[0-9.]+" (401|403|422|429)' "$LOG" \
        | tail -5 | sed 's/^/     /'
fi

if [ "$SOLO_CHECK" = yes ]; then
    titolo "--check: mi fermo qui"
    log "niente copiato, niente ricaricato. /etc/fail2ban e come l'ho trovato."
    printf '\n'
    exit 0
fi

# ---------------------------------------------------------------------------
titolo "4. Copio filtro e jail"
install -o root -g root -m 0644 "$PROVA/starkid-auth.conf" "$FILTRO_DST"
log "filtro -> $FILTRO_DST"

RIPRISTINA=no
if [ -f "$JAIL_DST" ]; then
    cp -a "$JAIL_DST" "$JAIL_DST.prima-$STAMP"
    RIPRISTINA=yes
    log "la jail che c'era e salvata come $JAIL_DST.prima-$STAMP"
fi

# enabled = true si mette QUI, non nel file in repo: quel file resta spento
# cosi un install su una macchina nuova non accende niente a sorpresa.
sed 's/^enabled  *=.*/enabled  = true/' "$JAIL_SRC" >"$PROVA/jail.conf"
grep -qx 'enabled  = true' "$PROVA/jail.conf" || errore "non ho trovato la riga 'enabled' in $JAIL_SRC"

if [ -n "$IGNOREIP" ]; then
    printf '\n# Messo da attiva-fail2ban.sh il %s.\nignoreip = 127.0.0.1/8 ::1 %s\n' \
        "$STAMP" "$IGNOREIP" >>"$PROVA/jail.conf"
    log "ignoreip: 127.0.0.1/8 ::1 $IGNOREIP"
else
    printf '\n# Messo da attiva-fail2ban.sh il %s.\nignoreip = 127.0.0.1/8 ::1\n' \
        "$STAMP" >>"$PROVA/jail.conf"
    log "ignoreip: solo localhost"
    avviso "il tuo indirizzo non e fra le eccezioni: cinque password sbagliate e stai"
    avviso "fuori un'ora anche tu. Si rientra da SSH:"
    avviso "  sudo fail2ban-client set starkid-auth unbanip IL.TUO.INDI.RIZZO"
    avviso "Per metterlo fra le eccezioni:  --ignoreip \"IL.TUO.INDI.RIZZO\""
fi
install -o root -g root -m 0644 "$PROVA/jail.conf" "$JAIL_DST"
log "jail   -> $JAIL_DST  (enabled = true)"

# ---------------------------------------------------------------------------
titolo "5. La configurazione passa?"
indietro() {
    if [ "$RIPRISTINA" = yes ]; then
        mv -f "$JAIL_DST.prima-$STAMP" "$JAIL_DST"
        log "rimessa la jail di prima"
    else
        rm -f "$JAIL_DST"
        log "tolto $JAIL_DST"
    fi
}
if ! fail2ban-client -t >"$PROVA/test.txt" 2>&1; then
    sed 's/^/   | /' "$PROVA/test.txt"
    indietro
    errore "fail2ban-client -t non passa: NON ho ricaricato, la macchina e come prima"
fi
log "fail2ban-client -t: $(grep -c . "$PROVA/test.txt") righe, esito OK"
grep -i 'warning' "$PROVA/test.txt" | sed 's/^/   | /' || true

# ---------------------------------------------------------------------------
titolo "6. Ricarico fail2ban"
log "reload, non restart: i blocchi in corso delle altre jail restano"
log "jail toccate dal reload: tutte e $(echo "$PRIMA" | wc -l), sshd compresa"
if ! conferma "Ricarico?" "ricarica"; then
    indietro
    errore "fermato da te: rimesso tutto come prima, niente ricaricato"
fi
fail2ban-client reload >/dev/null

# ---------------------------------------------------------------------------
titolo "7. Ricontrollo"
DOPO=$(leggi_jail)
log "jail attive ora: $(echo "$DOPO" | tr '\n' ' ')"

MANCANTI=$(comm -23 <(echo "$PRIMA") <(echo "$DOPO") || true)
if [ -n "$MANCANTI" ]; then
    avviso "SONO SPARITE jail che c'erano prima: $MANCANTI"
    avviso "torno indietro subito"
    indietro
    fail2ban-client reload >/dev/null
    log "jail attive dopo il ritorno: $(leggi_jail | tr '\n' ' ')"
    errore "la nostra jail ne ha buttata giu un'altra: non l'ho lasciata attiva"
fi
log "le $(echo "$PRIMA" | wc -l) jail di prima ci sono tutte"

if ! echo "$DOPO" | grep -qx 'starkid-auth'; then
    errore "starkid-auth non risulta attiva: guarda 'journalctl -u fail2ban -n 50'"
fi
printf '\n'
fail2ban-client status starkid-auth | sed 's/^/   /'

titolo "Fatto"
log "stato:      sudo fail2ban-client status starkid-auth"
log "chi e fuori: sudo fail2ban-client get starkid-auth banip --with-time"
log "far rientrare qualcuno: sudo fail2ban-client set starkid-auth unbanip 1.2.3.4"
log "spegnere:   sudo ./deploy/attiva-fail2ban.sh --disattiva"
printf '\n'
