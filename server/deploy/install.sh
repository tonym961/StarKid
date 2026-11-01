#!/usr/bin/env bash
# =============================================================================
# StarKid - installazione del server di configurazione
#
# Si esegue SUL SERVER, come root, dalla cartella server/ del repo:
#     sudo ./deploy/install.sh
#
# Si puo rieseguire quante volte si vuole: ogni passo controlla prima com'e la
# situazione e non rifa quello che e gia a posto.
#
# Questa macchina ha dodici siti in produzione, tre dei quali fanno da proxy
# verso casa. Percio:
#   - lo script non ricarica mai nginx da solo: si ferma e chiede conferma;
#   - prima di qualunque reload esegue "nginx -t";
#   - se si risponde no, il symlink del vhost viene ritirato, cosi un reload
#     fatto da qualcun altro (o da certbot) non attiva niente a sorpresa;
#   - non installa pacchetti di sistema, non tocca gli altri vhost, non tocca
#     fail2ban e non lancia certbot.
#
# Cosa NON fa, di proposito:
#   - non attiva la jail fail2ban (vedi deploy/fail2ban-starkid.conf)
#   - non chiede il certificato: certbot lo lancia una persona, a mano
#   - non crea l'account del genitore (comando a parte, vedi README)
# =============================================================================

set -euo pipefail

# ----------------------------------------------------------------------------
# Costanti
# ----------------------------------------------------------------------------
DOMINIO=starkid.antoninomiccio.com
PORTA=8082
UTENTE=starkid
APP_DIR=/opt/starkid
STATE_DIR=/var/lib/starkid
BACKUP_DIR=/var/lib/starkid/backup
ETC_DIR=/etc/starkid
ENV_FILE=/etc/starkid/starkid.env
VENV=/opt/starkid/venv
NGX_AVAIL=/etc/nginx/sites-available/$DOMINIO
NGX_LINK=/etc/nginx/sites-enabled/$DOMINIO
# Webroot ACME: la macchina usa /var/www/<dominio>/html per ogni sito, una
# cartella per dominio e non una condivisa. Verificato sul posto guardando i
# dodici vhost gia attivi: seguire la convenzione di casa invece di imporre la
# propria e meta del lavoro quando si entra in una macchina di qualcun altro.
ACME_ROOT=/var/www/starkid.antoninomiccio.com/html
CERT=/etc/letsencrypt/live/$DOMINIO/fullchain.pem
LOCK=/var/lock/starkid-install.lock
LOG_DIR=/var/log/starkid
# L'utente dei worker di nginx: su Debian/Ubuntu e www-data, ma si legge dal
# file invece di darlo per scontato, perche e lui che deve poter scrivere i log.
NGX_UTENTE=$(awk '$1=="user" {gsub(";","",$2); print $2; exit}' /etc/nginx/nginx.conf 2>/dev/null)
NGX_UTENTE=${NGX_UTENTE:-www-data}

STAMP=$(date -u +%Y%m%dT%H%M%SZ)
SALVATAGGI=/var/backups/starkid-$STAMP

SRC=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
DEPLOY=$SRC/deploy

SOLO_CONTROLLI=no
FORZA=no
RELOAD_FATTO=no

# ----------------------------------------------------------------------------
# Utilita
# ----------------------------------------------------------------------------
rosso=''; giallo=''; verde=''; neutro=''
if [ -t 1 ]; then
    rosso=$'\033[31m'; giallo=$'\033[33m'; verde=$'\033[32m'; neutro=$'\033[0m'
fi

titolo() { printf '\n%s== %s ==%s\n' "$verde" "$*" "$neutro"; }
log()    { printf '   %s\n' "$*"; }
avviso() { printf '%s ! %s%s\n' "$giallo" "$*" "$neutro"; }
errore() { printf '%sERRORE: %s%s\n' "$rosso" "$*" "$neutro" >&2; exit 1; }

uso() {
    cat <<'FINE'
Uso: sudo ./deploy/install.sh [opzioni]

  --check     esegue solo i controlli preliminari e non modifica niente
  --forza     installa anche con poca RAM libera (vedi sotto)
  --help      questo testo

Sotto i 250 MB di RAM libera lo script si ferma: il pip install e l'unico
passo pesante e l'OOM killer, se interviene, colpisce il processo piu grosso
della macchina, che non e pip ma un servizio di un altro sito.

Lo script si ferma e chiede conferma prima di ricaricare nginx.
FINE
}

# Conferma esplicita: non basta "s", va scritta la parola per intero.
# Legge da /dev/tty, cosi funziona anche se lo script e stato lanciato con
# l'output in una pipe.
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
        errore "serve una conferma ma non c'e un terminale. Rilancia lo script da una sessione interattiva."
    fi
    [ "$risposta" = "$parola" ]
}

salva_copia() {
    # salva_copia <file>  -> copia il file in /var/backups/starkid-<data>/
    local f=$1
    [ -e "$f" ] || return 0
    mkdir -p "$SALVATAGGI"
    cp -a "$f" "$SALVATAGGI/$(basename "$f")"
    log "copia di sicurezza del vecchio file in $SALVATAGGI/$(basename "$f")"
}

nginx_test() {
    titolo "nginx -t"
    if nginx -t; then
        log "configurazione valida"
        return 0
    fi
    return 1
}

# ----------------------------------------------------------------------------
# Argomenti
# ----------------------------------------------------------------------------
while [ $# -gt 0 ]; do
    case $1 in
        --check) SOLO_CONTROLLI=yes ;;
        --forza) FORZA=yes ;;
        --help|-h) uso; exit 0 ;;
        *) uso; errore "argomento sconosciuto: $1" ;;
    esac
    shift
done

# ----------------------------------------------------------------------------
# Un'installazione alla volta
# (il controllo di root sta qui e non piu sotto perche il lock in /var/lock
#  si puo creare solo da root: meglio un messaggio chiaro di un permesso negato)
# ----------------------------------------------------------------------------
[ "$(id -u)" -eq 0 ] || errore "va eseguito come root (sudo ./deploy/install.sh)"

exec 9>"$LOCK"
if ! flock -n 9; then
    errore "c'e gia un install.sh in corso (lock $LOCK). Una sessione alla volta."
fi

# ============================================================================
# 0. Controlli preliminari
# ============================================================================
titolo "0. Controlli preliminari"

if [ -r /etc/os-release ]; then
    . /etc/os-release
    log "sistema: ${PRETTY_NAME:-sconosciuto}"
    case ${ID:-} in
        ubuntu|debian) ;;
        *) avviso "atteso Ubuntu, trovato ${ID:-ignoto}: il layout di nginx potrebbe essere diverso" ;;
    esac
fi

for c in python3 sqlite3 nginx systemctl openssl curl install getent flock ss; do
    command -v "$c" >/dev/null 2>&1 || errore "manca il comando: $c"
done
log "comandi necessari: tutti presenti"

if ! python3 -c 'import ensurepip' >/dev/null 2>&1; then
    errore "manca il modulo venv/ensurepip. Serve il pacchetto python3-venv: installalo tu a mano (apt install python3-venv) e rilancia. Questo script non installa pacchetti."
fi
log "python: $(python3 --version 2>&1), venv disponibile"

# --- File del repo ---
for p in "$SRC/requirements.txt" "$SRC/app" "$SRC/web" "$DEPLOY/starkid.service" \
         "$DEPLOY/starkid.nginx.conf" "$DEPLOY/starkid-backup.service" \
         "$DEPLOY/starkid-backup.timer" "$DEPLOY/starkid-backup.sh" \
         "$DEPLOY/starkid-ruota-log.sh" "$DEPLOY/starkid-ruota-log.service" \
         "$DEPLOY/starkid-ruota-log.timer" "$SRC/schema/config.schema.json"; do
    [ -e "$p" ] || errore "manca $p (sei nella cartella server/ del repo?)"
done
[ -f "$SRC/app/main.py" ] || avviso "non trovo $SRC/app/main.py: la unit avvia app.main:app, verifica che il modulo ci sia"
log "file del repo: presenti ($SRC)"

# --- RAM ---
MEM_LIB=$(awk '/^MemAvailable:/ {print int($2/1024)}' /proc/meminfo)
SWAP_TOT=$(awk '/^SwapTotal:/ {print int($2/1024)}' /proc/meminfo)
SWAP_LIB=$(awk '/^SwapFree:/ {print int($2/1024)}' /proc/meminfo)
SWAP_USO=$(( SWAP_TOT - SWAP_LIB ))
log "RAM disponibile: ${MEM_LIB} MB   swap in uso: ${SWAP_USO} MB su ${SWAP_TOT} MB"
# Qui si ferma, non si avvisa e si tira avanti. L'OOM killer e globale:
# sceglie per oom_score, cioe il processo piu grosso della macchina, che non e
# pip ma un servizio di uno degli altri undici siti. E l'unico momento
# dell'installazione che puo far danno fuori da StarKid.
if [ "$MEM_LIB" -lt 250 ]; then
    if [ "$FORZA" = yes ]; then
        avviso "solo ${MEM_LIB} MB liberi, ma e stato chiesto --forza: il pip gira comunque, dentro un tetto di memoria."
    else
        errore "solo ${MEM_LIB} MB liberi (ne servono 250). Aspetta che la macchina sia scarica, oppure --forza se sai cosa stai facendo."
    fi
fi

# --- Riavvio in sospeso ---
if [ -f /var/run/reboot-required ]; then
    avviso "c'e un riavvio in sospeso ($(cat /var/run/reboot-required 2>/dev/null | head -1))."
    avviso "Si puo installare comunque, ma quando si riavviera il servizio ripartira da solo: verificalo dopo."
fi

# --- Altre sessioni automatiche / apt in corso ---
# Si guarda il LOCK, non i nomi dei processi. Il controllo di prima era
# "pgrep -x apt|dpkg|unattended-upgr", e prendeva unattended-upgrade-shutdown:
# un demone che su ogni Ubuntu resta fermo in attesa del segnale di spegnimento
# per mesi. Avvisava sempre, su qualunque macchina, anche con apt a riposo - e
# un avviso che c'e sempre e un avviso che nessuno legge piu.
if ! flock -n /var/lib/dpkg/lock-frontend true 2>/dev/null; then
    avviso "c'e un apt/dpkg in corso: aspetta che finisca, altrimenti vi pestate i piedi sulla RAM."
fi

# --- Porta 8082 ---
if ss -ltnH 2>/dev/null | awk '{print $4}' | grep -q "127.0.0.1:$PORTA\$"; then
    if systemctl is-active --quiet starkid.service; then
        log "porta $PORTA occupata dal servizio starkid (reinstallazione: va bene)"
    else
        errore "la porta 127.0.0.1:$PORTA e occupata da qualcos'altro. Controlla con: ss -ltnp | grep $PORTA"
    fi
else
    log "porta 127.0.0.1:$PORTA libera"
fi

# --- nginx: stato di partenza ---
if nginx -t >/dev/null 2>&1; then
    log "nginx: la configurazione attuale e valida (punto di partenza pulito)"
else
    avviso "nginx -t FALLISCE GIA ADESSO, prima che tocchiamo qualcosa."
    avviso "Sistema quello prima di continuare: non si deve confondere un problema tuo con uno nostro."
    nginx -t || true
    errore "mi fermo qui"
fi

ATTIVI=$(ls -1 /etc/nginx/sites-enabled/ 2>/dev/null | wc -l || true)
log "vhost attivi adesso: $ATTIVI"

# Chi fa da default per questa porta. Se nessun vhost dichiara default_server,
# nginx elegge il primo server block che analizza, e l'ordine lo decide
# l'alfabeto di sites-enabled/. Il nostro vhost non lo dichiara (ed e giusto),
# ma se finisse primo si prenderebbe tutto il traffico per IP nudo o con Host
# sconosciuto, che oggi va a un altro sito.
DEFSRV=$(grep -rln 'default_server' /etc/nginx/sites-enabled/ /etc/nginx/conf.d/ 2>/dev/null || true)
if [ -n "$DEFSRV" ]; then
    log "default_server dichiarato in: $(echo "$DEFSRV" | tr '\n' ' ')"
else
    avviso "nessun vhost dichiara default_server: a decidere chi risponde per IP nudo e l'ordine alfabetico."
    avviso "Prima di attivare, metti default_server sul vhost che deve fare da default oggi."
fi

# http2 e un parametro del socket, non del vhost: il primo listen analizzato
# per 0.0.0.0:443 decide per tutti i siti che condividono la porta. Se qui si
# accendesse di sponda, i tre vhost che proxano verso casa perderebbero le
# WebSocket (l'upgrade non esiste in HTTP/2).
H2=$(grep -rl 'listen .*443.*http2' /etc/nginx/sites-enabled/ 2>/dev/null || true)
if [ -n "$H2" ]; then
    log "http2 e gia dichiarato da: $(echo "$H2" | tr '\n' ' ')"
else
    log "nessun vhost dichiara http2 sulla 443: il nostro non lo accende (scelta da prendere per tutta la macchina)"
fi

if [ -f "$CERT" ]; then
    log "certificato per $DOMINIO: presente"
    FASE=tls
else
    log "certificato per $DOMINIO: NON presente -> prima passata, si installa solo il vhost ACME"
    FASE=acme
fi

if [ "$SOLO_CONTROLLI" = yes ]; then
    titolo "Solo controlli: non ho modificato niente"
    exit 0
fi

conferma "Procedo con l'installazione (fase: $FASE). Niente verra ricaricato in nginx senza una seconda conferma." "avanti" \
    || errore "fermato da te, niente modificato"

# ============================================================================
# 1. Utente di servizio e cartelle
# ============================================================================
titolo "1. Utente di servizio e cartelle"

if getent group "$UTENTE" >/dev/null; then
    log "gruppo $UTENTE: c'e gia"
else
    groupadd --system "$UTENTE"
    log "gruppo $UTENTE creato"
fi

if getent passwd "$UTENTE" >/dev/null; then
    log "utente $UTENTE: c'e gia"
else
    useradd --system --gid "$UTENTE" --home-dir "$APP_DIR" --no-create-home \
            --shell /usr/sbin/nologin --comment "StarKid server" "$UTENTE"
    log "utente $UTENTE creato (di sistema, senza shell, senza home)"
fi

install -d -m 0755 -o root -g root "$APP_DIR"
install -d -m 0755 -o root -g root "$APP_DIR/bin"
install -d -m 0750 -o "$UTENTE" -g "$UTENTE" "$STATE_DIR"
install -d -m 0700 -o "$UTENTE" -g "$UTENTE" "$BACKUP_DIR"
install -d -m 0750 -o root -g "$UTENTE" "$ETC_DIR"
# I log di nginx in una cartella nostra: il logrotate di nginx prende
# /var/log/nginx/*.log, e due file di logrotate sullo stesso percorso fanno
# "duplicate log entry", col secondo saltato in silenzio. Qui invece il tetto
# di dimensione vale davvero. Proprietario www-data perche sono i worker di
# nginx a scriverci.
install -d -m 0750 -o "$NGX_UTENTE" -g adm "$LOG_DIR"
# Il webroot ACME puo essere condiviso con gli altri domini: se c'e gia, non
# gli si tocca ne proprietario ne permessi.
if [ -d "$ACME_ROOT" ]; then
    log "$ACME_ROOT: c'e gia, lo lascio come sta ($(stat -c '%U:%G %a' "$ACME_ROOT"))"
    install -d "$ACME_ROOT/.well-known/acme-challenge"
else
    install -d -m 0755 -o root -g root "$ACME_ROOT"
    install -d -m 0755 -o root -g root "$ACME_ROOT/.well-known"
    install -d -m 0755 -o root -g root "$ACME_ROOT/.well-known/acme-challenge"
    log "creato il webroot ACME $ACME_ROOT"
    avviso "controlla quale webroot usano gli altri domini: grep -rl acme-challenge /etc/nginx/sites-available/"
    avviso "se usano un'altra cartella, conviene usare la stessa anche qui (si cambia in deploy/starkid.nginx.conf)."
fi
log "cartelle a posto: $APP_DIR (di root), $STATE_DIR (di $UTENTE), $ETC_DIR, $ACME_ROOT"

# ============================================================================
# 2. Ambiente Python
# ============================================================================
titolo "2. Ambiente Python"

if [ -x "$VENV/bin/python" ]; then
    log "venv: c'e gia ($("$VENV/bin/python" --version 2>&1))"
else
    log "creo il venv in $VENV"
    python3 -m venv "$VENV"
fi

MEM_ORA=$(awk '/^MemAvailable:/ {print int($2/1024)}' /proc/meminfo)
log "RAM disponibile adesso: ${MEM_ORA} MB - inizio l'installazione delle dipendenze"
log "(solo pacchetti precompilati: se per qualcosa manca il wheel, si ferma invece di compilare e finire la RAM)"

install -m 0644 -o root -g root "$SRC/requirements.txt" "$APP_DIR/requirements.txt"

# Il pip va dentro un tetto di memoria, come il servizio che installa. Senza
# lo scope gira come root fuori da qualunque cgroup: scompattare il binario
# Rust di pydantic-core tocca tranquillamente 200 MB di RSS, e se in quel
# momento c'e un picco altrove l'OOM killer non uccide pip, uccide il
# processo piu grosso della macchina. Con il tetto, se sfonda muore lui.
PIP=("$VENV/bin/pip" install
     --no-cache-dir
     --only-binary :all:
     --disable-pip-version-check
     --no-input
     -r "$APP_DIR/requirements.txt")

if command -v systemd-run >/dev/null 2>&1; then
    SCOPE=(systemd-run --scope --quiet --collect
           -p MemoryMax=220M -p MemorySwapMax=64M)
else
    avviso "systemd-run non c'e: il pip gira senza tetto di memoria. Controlla che la macchina sia scarica."
    SCOPE=()
fi

if ! "${SCOPE[@]}" "${PIP[@]}"; then
    avviso "pip e fallito. Se si lamenta di un wheel mancante, NON togliere --only-binary:"
    avviso "fissa invece una versione che abbia il wheel. Compilare qui dentro significa finire la RAM."
    avviso "Se invece e stato ucciso per memoria (codice 137), aspetta che la macchina sia scarica e riprova."
    errore "installazione delle dipendenze fallita"
fi
log "dipendenze installate"
"$VENV/bin/pip" --disable-pip-version-check list 2>/dev/null | sed 's/^/     /' || true

# ============================================================================
# 3. Codice e configurazione
# ============================================================================
titolo "3. Codice e configurazione"

# Il servizio viene fermato prima di sostituire i file: evita di avere meta
# del codice vecchio aperto e meta nuovo su disco.
if systemctl is-active --quiet starkid.service; then
    log "fermo starkid.service per sostituire i file"
    systemctl stop starkid.service
fi

# schema/ va copiato come app/ e web/: models.py lo legge all'avvio e senza
# di lui il server SI RIFIUTA di partire, di proposito - meglio fermo che a
# meta del suo vocabolario. Dimenticarlo qui faceva fallire l'installazione
# al passo 5, con nginx ancora intonso: il controllo ha funzionato, ma il
# file va copiato.
rm -rf "$APP_DIR/app" "$APP_DIR/static" "$APP_DIR/schema"
cp -a "$SRC/app" "$APP_DIR/app"
cp -a "$SRC/web" "$APP_DIR/static"
cp -a "$SRC/schema" "$APP_DIR/schema"
install -m 0755 -o root -g root "$DEPLOY/starkid-backup.sh" "$APP_DIR/bin/starkid-backup.sh"

# Il codice e di root e il servizio non puo riscriverselo.
chown -R root:root "$APP_DIR/app" "$APP_DIR/static" "$APP_DIR/schema" "$APP_DIR/bin"
find "$APP_DIR/app" "$APP_DIR/static" "$APP_DIR/schema" -type d -exec chmod 0755 {} +
find "$APP_DIR/app" "$APP_DIR/static" "$APP_DIR/schema" -type f -exec chmod 0644 {} +
chmod 0755 "$APP_DIR/bin/starkid-backup.sh"
log "codice copiato in $APP_DIR (di root, in sola lettura per il servizio)"

# I .pyc si scrivono adesso, una volta: a runtime /opt e in sola lettura.
"$VENV/bin/python" -m compileall -q "$APP_DIR/app" >/dev/null 2>&1 || \
    avviso "compileall non e andato a buon fine: non e grave, si perde solo un po' di tempo al primo avvio"

# Qui dentro ci vanno solo le variabili che il codice legge davvero. Prima
# c'era anche una STARKID_SECRET_KEY, generata e mai usata da nessuno: il
# cookie di sessione e un token casuale opaco di cui in tabella sta solo
# l'hash, non una cosa firmata. Un segreto finto e peggio di nessun segreto,
# perche fra un anno qualcuno lo ruota convinto di invalidare le sessioni.
if [ -f "$ENV_FILE" ]; then
    log "$ENV_FILE: c'e gia, non lo tocco"
else
    umask 077
    {
        echo "# StarKid - generato da install.sh il $STAMP"
        echo "# Le uniche due variabili che il codice legge. Il percorso del"
        echo "# database e l'unico posto dove il servizio puo scrivere."
        echo "STARKID_DB=$STATE_DIR/starkid.db"
        echo "STARKID_COOKIE_SECURE=1"
    } > "$ENV_FILE"
    umask 022
    chown root:"$UTENTE" "$ENV_FILE"
    chmod 0640 "$ENV_FILE"
    log "$ENV_FILE creato (0640 root:$UTENTE)"
fi

# ============================================================================
# 4. Unita systemd
# ============================================================================
titolo "4. Unita systemd"

for u in starkid.service starkid-backup.service starkid-backup.timer; do
    if [ -f "/etc/systemd/system/$u" ] && ! cmp -s "$DEPLOY/$u" "/etc/systemd/system/$u"; then
        salva_copia "/etc/systemd/system/$u"
    fi
    install -m 0644 -o root -g root "$DEPLOY/$u" "/etc/systemd/system/$u"
    log "installata $u"
done

systemctl daemon-reload
systemctl enable starkid.service >/dev/null

# --- Una copia del database PRIMA di riavviare -----------------------------
# salva_copia() sopra non serve qui, e sarebbe anzi sbagliata: fa "cp -a", e
# un database in WAL copiato a freddo puo venire via a meta. Si usa lo stesso
# script del timer, che fa "sqlite3 .backup" e poi controlla l'integrita.
#
# Perche proprio qui: da questo punto in poi parte la versione nuova del
# codice sul database vecchio. Finche si aggiungono solo campi al documento
# di configurazione non succede niente, ma il giorno in cui una versione
# cambia le tabelle, l'unica rete sarebbe la copia del timer - cioe fino a
# ventiquattro ore prima. Una copia costa un secondo.
if [ -f "$STATE_DIR/starkid.db" ]; then
    # "sudo -u utente env VAR=..." e non "sudo -u utente VAR=...": la seconda
    # forma passa solo se sudoers concede setenv, e qui non lo concede nessuno.
    if sudo -u "$UTENTE" env \
        STARKID_DB="$STATE_DIR/starkid.db" \
        STARKID_BACKUP_DIR="$BACKUP_DIR" \
        "$APP_DIR/bin/starkid-backup.sh" >/dev/null 2>&1; then
        log "copia del database fatta prima di riavviare (sqlite3 .backup, integrita verificata)"
    else
        avviso "la copia del database PRIMA del riavvio non e riuscita."
        avviso "Non e un motivo per fermarsi se stai solo aggiornando il codice, ma se questa"
        avviso "versione cambia le tabelle, fermati qui e guarda: $BACKUP_DIR"
        conferma "Vado avanti lo stesso?" "avanti senza copia" \
            || errore "fermato da te: il servizio vecchio sta ancora girando, niente e cambiato"
    fi
else
    log "nessun database ancora: niente da copiare"
fi

systemctl restart starkid.service
systemctl enable --now starkid-backup.timer >/dev/null
log "servizio avviato, timer della copia di sicurezza attivo"

# --- Verifica locale, prima di toccare nginx ---
titolo "5. Verifica in locale (ancora senza nginx)"
OK=no
for i in $(seq 1 20); do
    if curl -fsS --max-time 3 "http://127.0.0.1:$PORTA/healthz" >/dev/null 2>&1; then
        OK=yes; break
    fi
    sleep 1
done
if [ "$OK" = yes ]; then
    log "127.0.0.1:$PORTA/healthz risponde: $(curl -fsS --max-time 3 "http://127.0.0.1:$PORTA/healthz")"
else
    printf '\n'
    systemctl --no-pager --full status starkid.service || true
    journalctl -u starkid.service -n 40 --no-pager || true
    errore "il servizio non risponde su 127.0.0.1:$PORTA. nginx NON e stato toccato: la macchina e come prima."
fi
log "RSS del servizio: $(systemctl show -p MemoryCurrent --value starkid.service | awk '{if ($1+0>0) printf "%.0f MB", $1/1048576; else print "n/d"}') (limite 192 MB)"

# ============================================================================
# 6. nginx
# ============================================================================
titolo "6. nginx"

# La rotazione va messa PRIMA del vhost: dal momento in cui nginx scrive in
# $LOG_DIR quei file devono avere un tetto.
#
# E un timer nostro e non logrotate. Su questa macchina logrotate non e
# installato - il binario non c'e e il pacchetto nemmeno - benche
# /etc/logrotate.d/ sia pieno di configurazioni che percio non esegue nessuno.
# Metterlo avrebbe voluto dire installare un pacchetto su una macchina in
# produzione di qualcun altro e far partire di colpo la rotazione di tutti i
# suoi log, che oggi non ruotano: un cambiamento che nessuno ha chiesto. Il
# timer qui sotto tocca due file soli, i nostri.
install -m 0755 -o root -g root "$DEPLOY/starkid-ruota-log.sh" "$APP_DIR/bin/starkid-ruota-log.sh"
for u in starkid-ruota-log.service starkid-ruota-log.timer; do
    if [ -f "/etc/systemd/system/$u" ] && ! cmp -s "$DEPLOY/$u" "/etc/systemd/system/$u"; then
        salva_copia "/etc/systemd/system/$u"
    fi
    install -m 0644 -o root -g root "$DEPLOY/$u" "/etc/systemd/system/$u"
done
systemctl daemon-reload
systemctl enable --now starkid-ruota-log.timer >/dev/null 2>&1 || \
    avviso "non sono riuscito ad attivare starkid-ruota-log.timer: i log crescerebbero senza tetto, controllalo."
log "rotazione dei log attiva (tetto 100 MB per file, 7 ruotati, controllo ogni ora)"

if [ "$FASE" = acme ]; then
    log "prima passata: installo un vhost di sola porta 80 con il webroot ACME."
    log "Nessun TLS, nessun rimando: serve solo a far passare la verifica di certbot."
    if [ -f "$NGX_AVAIL" ]; then
        salva_copia "$NGX_AVAIL"
    fi
    cat > "$NGX_AVAIL" <<'NGX'
# StarKid - PRIMA PASSATA (solo ACME).
# Generato da install.sh perche il certificato per starkid.antoninomiccio.com
# non esiste ancora: con i riferimenti al certificato "nginx -t" fallirebbe.
# Dopo "certbot certonly --webroot", rilanciando install.sh questo file viene
# sostituito dal vhost completo (deploy/starkid.nginx.conf).
server {
    listen 80;
    server_name starkid.antoninomiccio.com;

    server_tokens off;
    access_log /var/log/starkid/access.log;
    error_log  /var/log/starkid/error.log warn;

    location ^~ /.well-known/acme-challenge/ {
        auth_basic off;
        allow all;
        default_type "text/plain";
        root /var/www/starkid.antoninomiccio.com/html;
        try_files $uri =404;
    }

    location / {
        return 404;
    }
}
NGX
    chmod 0644 "$NGX_AVAIL"
    log "scritto $NGX_AVAIL (solo ACME)"
else
    if [ -f "$NGX_AVAIL" ] && ! cmp -s "$DEPLOY/starkid.nginx.conf" "$NGX_AVAIL"; then
        salva_copia "$NGX_AVAIL"
    fi
    install -m 0644 -o root -g root "$DEPLOY/starkid.nginx.conf" "$NGX_AVAIL"
    log "scritto $NGX_AVAIL (vhost completo con TLS)"
fi

# Il symlink serve perche "nginx -t" veda il nuovo vhost. Se poi il reload non
# viene confermato, lo togliamo: cosi nessun reload altrui lo attiva a sorpresa.
SYMLINK_NUOVO=no
if [ -L "$NGX_LINK" ]; then
    log "symlink in sites-enabled: c'era gia"
elif [ -e "$NGX_LINK" ]; then
    errore "$NGX_LINK esiste e non e un symlink: guarda cos'e prima di andare avanti"
else
    ln -s "$NGX_AVAIL" "$NGX_LINK"
    SYMLINK_NUOVO=yes
    log "creato il symlink $NGX_LINK"
fi

if ! nginx_test; then
    avviso "nginx -t ha dato errore con il nostro vhost: lo ritiro subito."
    if [ "$SYMLINK_NUOVO" = yes ]; then
        rm -f "$NGX_LINK"
        log "symlink rimosso"
    fi
    if nginx -t >/dev/null 2>&1; then
        log "la configurazione e tornata valida: la macchina e come prima, nessun reload fatto"
    else
        avviso "ATTENZIONE: nginx -t fallisce ancora dopo il ritiro. Guarda subito l'output qui sopra."
    fi
    errore "vhost rifiutato da nginx, niente ricaricato"
fi

printf '\n'
avviso "Il prossimo passo e il reload di nginx."
# Si contano i symlink ADESSO, non si somma uno al conteggio del passo 0.
# Sommando, alla seconda passata il nostro vhost veniva contato due volte (il
# passo 0 lo trova gia collegato) e il messaggio annunciava un vhost in piu di
# quelli che esistono. Un numero sbagliato proprio nella riga che chiede una
# conferma delicata e il modo migliore per far perdere fiducia nel resto.
ATTIVI_ORA=$(ls -1 /etc/nginx/sites-enabled/ 2>/dev/null | wc -l || echo "?")
avviso "Quel reload rilegge TUTTI i $ATTIVI_ORA vhost di questa macchina, compresi i tre"
avviso "che fanno da proxy verso casa. nginx -t e passato, ma il reload resta una tua decisione."
log "Se rispondi no: il symlink viene ritirato e la macchina resta esattamente come l'hai trovata."

if conferma "Ricarico nginx adesso?" "ricarica"; then
    nginx -t && systemctl reload nginx
    RELOAD_FATTO=yes
    log "nginx ricaricato"
    sleep 1
    if systemctl is-active --quiet nginx; then
        log "nginx attivo"
    else
        avviso "nginx NON risulta attivo: guarda subito 'systemctl status nginx' e 'journalctl -u nginx -n 50'"
    fi
    log "vhost attivi ora: $(ls -1 /etc/nginx/sites-enabled/ | wc -l)"
else
    if [ "$SYMLINK_NUOVO" = yes ]; then
        rm -f "$NGX_LINK"
        log "symlink ritirato: il vhost e pronto in sites-available ma non attivo"
    else
        avviso "il symlink c'era gia da prima e l'ho lasciato: al prossimo reload (anche quello di certbot) il vhost nuovo diventera attivo. Se non lo vuoi, togli $NGX_LINK a mano."
    fi
    log "Per attivarlo dopo:  ln -s $NGX_AVAIL $NGX_LINK && nginx -t && systemctl reload nginx"
fi

# ============================================================================
# 7. fail2ban: proposto, non attivato
# ============================================================================
titolo "7. fail2ban"
log "NON ho toccato fail2ban. I file sono qui, da leggere e copiare a mano:"
log "  $DEPLOY/fail2ban-starkid-filter.conf  ->  /etc/fail2ban/filter.d/starkid-auth.conf"
log "  $DEPLOY/fail2ban-starkid.conf         ->  /etc/fail2ban/jail.d/starkid.conf"
log "La jail nasce con enabled = false. Prima provala con fail2ban-regex (vedi README)."
log "Attenzione: se il logpath non esiste, fail2ban non parte piu e si porta dietro anche la jail sshd."

# ============================================================================
# Riepilogo
# ============================================================================
titolo "Fatto"
log "servizio:    systemctl status starkid.service   (127.0.0.1:$PORTA)"
log "log:         journalctl -u starkid.service -f"
log "database:    $STATE_DIR/starkid.db"
log "copie:       $BACKUP_DIR  (timer: systemctl list-timers starkid-backup.timer)"
log "segreti:     $ENV_FILE"
[ -d "$SALVATAGGI" ] && log "file vecchi: $SALVATAGGI"

printf '\n'
if [ "$FASE" = acme ]; then
    titolo "Prossimo passo: il certificato"
    cat <<FINE
   Il vhost di sola porta 80 e pronto. Se NON hai ricaricato nginx, il
   certificato non si puo ancora chiedere: prima attiva il vhost.

   1) certbot certonly --webroot -w $ACME_ROOT -d $DOMINIO
   2) rilancia questo script:  sudo $DEPLOY/install.sh
      (la seconda volta installa il vhost completo con TLS)

   Non usare "certbot --nginx": riscriverebbe i file dei vhost.
FINE
else
    titolo "Prossimo passo: l'account del genitore"
    cat <<FINE
   L'account si crea solo da qui, non c'e iscrizione dal web:

   sudo -u $UTENTE env -C $APP_DIR $VENV/bin/python -m app.cli crea-utente <nomeutente>

   La password si scrive quando la chiede: non passarla come argomento,
   finirebbe nella cronologia della shell e nella lista dei processi.
FINE
fi

if [ "$RELOAD_FATTO" = no ]; then
    printf '\n'
    avviso "nginx NON e stato ricaricato: da fuori il sito non risponde ancora."
fi
printf '\n'
