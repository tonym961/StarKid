#!/usr/bin/env bash
# =============================================================================
# StarKid - rimozione del server di configurazione
#
#     sudo ./deploy/uninstall.sh            lascia il database e i segreti
#     sudo ./deploy/uninstall.sh --purge    cancella anche quelli
#
# Torna indietro passo per passo, nell'ordine inverso dell'installazione.
# Come install.sh: "nginx -t" prima, e il reload solo con una conferma
# esplicita, perche quel reload rilegge tutti i dodici siti.
#
# Cosa NON tocca mai:
#   - il certificato Let's Encrypt (resta, e condiviso il rinnovo con gli
#     altri domini: si rimuove a mano con "certbot delete --cert-name ...")
#   - /var/www/letsencrypt (webroot ACME, puo servire ad altri)
#   - gli altri vhost, fail2ban, i pacchetti di sistema
# =============================================================================

set -euo pipefail

DOMINIO=starkid.antoninomiccio.com
UTENTE=starkid
APP_DIR=/opt/starkid
STATE_DIR=/var/lib/starkid
ETC_DIR=/etc/starkid
NGX_AVAIL=/etc/nginx/sites-available/$DOMINIO
NGX_LINK=/etc/nginx/sites-enabled/$DOMINIO
LOCK=/var/lock/starkid-install.lock

STAMP=$(date -u +%Y%m%dT%H%M%SZ)
PURGE=no

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
        --purge) PURGE=yes ;;
        --help|-h)
            printf 'Uso: sudo ./deploy/uninstall.sh [--purge]\n'
            printf '  --purge  cancella anche database, copie di sicurezza e chiave di sessione\n'
            exit 0 ;;
        *) errore "argomento sconosciuto: $1" ;;
    esac
    shift
done

[ "$(id -u)" -eq 0 ] || errore "va eseguito come root (sudo ./deploy/uninstall.sh)"

exec 9>"$LOCK"
flock -n 9 || errore "c'e un install.sh o uninstall.sh in corso. Una sessione alla volta."

titolo "Cosa sto per fare"
log "fermare e togliere starkid.service, starkid-backup.service, starkid-backup.timer"
log "togliere il vhost $DOMINIO da nginx (con conferma prima del reload)"
log "cancellare $APP_DIR (codice e venv)"
if [ "$PURGE" = yes ]; then
    avviso "--purge: cancello ANCHE $STATE_DIR (database e tutte le copie) e $ETC_DIR, e rimuovo l'utente $UTENTE"
    avviso "Il database contiene la configurazione dei tablet e i loro token: i tablet dovranno essere riarruolati."
else
    log "lascio $STATE_DIR (database e copie) e $ETC_DIR (chiave di sessione)"
fi
log "NON tocco: certificato, /var/www/letsencrypt, altri vhost, fail2ban, pacchetti"

conferma "Confermi?" "disinstalla" || errore "fermato da te, niente modificato"

# ---------------------------------------------------------------------------
titolo "1. Servizi"
for u in starkid-backup.timer starkid-backup.service starkid.service; do
    if systemctl list-unit-files "$u" >/dev/null 2>&1 && \
       systemctl cat "$u" >/dev/null 2>&1; then
        systemctl disable --now "$u" >/dev/null 2>&1 || true
        log "fermata e disabilitata $u"
    else
        log "$u non c'era"
    fi
done

for u in starkid.service starkid-backup.service starkid-backup.timer; do
    if [ -f "/etc/systemd/system/$u" ]; then
        rm -f "/etc/systemd/system/$u"
        log "rimossa /etc/systemd/system/$u"
    fi
done
systemctl daemon-reload
systemctl reset-failed starkid.service starkid-backup.service >/dev/null 2>&1 || true

# La rotazione va via con il resto: i log restano (vedi in fondo), ma senza
# nginx che ci scrive non crescono piu.
for u in starkid-ruota-log.timer starkid-ruota-log.service; do
    if [ -f "/etc/systemd/system/$u" ]; then
        systemctl disable --now "$u" >/dev/null 2>&1 || true
        rm -f "/etc/systemd/system/$u"
        log "rimossa /etc/systemd/system/$u"
    fi
done
rm -f /etc/logrotate.d/starkid

# ---------------------------------------------------------------------------
titolo "2. nginx"
TOLTO=no
if [ -L "$NGX_LINK" ]; then
    rm -f "$NGX_LINK"
    TOLTO=yes
    log "rimosso il symlink $NGX_LINK"
else
    log "il symlink non c'era: nginx non stava servendo il vhost"
fi

if [ -f "$NGX_AVAIL" ]; then
    mv "$NGX_AVAIL" "$NGX_AVAIL.rimosso-$STAMP"
    log "vhost messo da parte come $NGX_AVAIL.rimosso-$STAMP (non cancellato: se serve si rimette)"
fi

if ! nginx -t; then
    avviso "nginx -t fallisce. NON ricaricare: guarda l'output qui sopra."
    avviso "Se serve rimettere tutto come prima: ln -s $NGX_AVAIL.rimosso-$STAMP $NGX_LINK"
    errore "mi fermo senza ricaricare nginx"
fi
log "nginx -t: configurazione valida senza il nostro vhost"

if [ "$TOLTO" = yes ]; then
    if conferma "Ricarico nginx per rendere effettiva la rimozione? (rilegge tutti i vhost, compresi i proxy verso casa)" "ricarica"; then
        nginx -t && systemctl reload nginx
        log "nginx ricaricato: $DOMINIO non risponde piu"
    else
        avviso "non ricaricato: nginx continua a servire il vhost in memoria finche non lo ricarichi tu"
        log "quando vuoi:  nginx -t && systemctl reload nginx"
    fi
fi

# ---------------------------------------------------------------------------
titolo "3. Codice e ambiente Python"
if [ -d "$APP_DIR" ]; then
    rm -rf "$APP_DIR"
    log "rimosso $APP_DIR (codice e venv)"
else
    log "$APP_DIR non c'era"
fi

# ---------------------------------------------------------------------------
titolo "4. Dati"
if [ "$PURGE" = yes ]; then
    if conferma "Ultima conferma: cancello il database e tutte le copie in $STATE_DIR. Non si torna indietro." "cancella i dati"; then
        rm -rf "$STATE_DIR" "$ETC_DIR"
        log "rimossi $STATE_DIR e $ETC_DIR"
        if getent passwd "$UTENTE" >/dev/null; then
            userdel "$UTENTE" || avviso "userdel $UTENTE non e andato: controlla con 'ps -u $UTENTE'"
            log "utente $UTENTE rimosso"
        fi
        if getent group "$UTENTE" >/dev/null; then
            groupdel "$UTENTE" 2>/dev/null || true
        fi
    else
        log "dati lasciati dove sono: $STATE_DIR"
    fi
else
    log "database e copie restano in $STATE_DIR"
    log "chiave di sessione resta in $ETC_DIR/starkid.env"
    log "utente di servizio $UTENTE: lasciato (serve a leggere quei file)"
    log "per cancellare anche quelli: sudo ./deploy/uninstall.sh --purge"
fi

# ---------------------------------------------------------------------------
titolo "5. Cose da sistemare a mano, se vuoi"
log "certificato:  certbot certificates | grep -A3 $DOMINIO"
log "              per togliere il rinnovo: certbot delete --cert-name $DOMINIO"
log "fail2ban:     se avevi copiato la jail, togli /etc/fail2ban/jail.d/starkid.conf"
log "              e /etc/fail2ban/filter.d/starkid-auth.conf, poi: fail2ban-client reload"
log "log di nginx: /var/log/starkid/ resta (access.log, error.log e i loro ruotati)"
log "DNS:          starkid.antoninomiccio.com continua a puntare qui"
printf '\n'
