#!/usr/bin/env bash
# StarKid - copia di sicurezza del database
# Destinazione: /opt/starkid/bin/starkid-backup.sh  (di root, 0755)
# Lo lancia starkid-backup.service, una volta al giorno, come utente starkid.
#
# Usa "sqlite3 .backup", non "cp": con una copia a freddo di un database in
# WAL si rischia di portarsi via un file a meta. .backup usa l'API di backup
# di SQLite e funziona anche mentre il server scrive.
#
# Tiene le 7 copie piu recenti e butta le altre. Non comprime: il database e
# minuscolo (una manciata di tablet) e una copia leggibile cosi com'e vale
# piu di qualche kilobyte risparmiato.

set -euo pipefail

DB=${STARKID_DB:-/var/lib/starkid/starkid.db}
DEST=${STARKID_BACKUP_DIR:-/var/lib/starkid/backup}
KEEP=${STARKID_BACKUP_KEEP:-7}

log() { printf 'starkid-backup: %s\n' "$*"; }

if [ ! -f "$DB" ]; then
    log "nessun database in $DB, niente da copiare"
    exit 0
fi

mkdir -p "$DEST"
chmod 0700 "$DEST"

STAMP=$(date -u +%Y%m%dT%H%M%SZ)
OUT="$DEST/starkid-$STAMP.db"
TMP="$DEST/.starkid-$STAMP.db.part"

cleanup() { rm -f "$TMP"; }
trap cleanup EXIT

# .timeout aspetta se il server sta scrivendo in quel momento, invece di
# uscire subito con "database is locked".
sqlite3 "$DB" ".timeout 10000" ".backup '$TMP'"

# Una copia che non si apre non e una copia. Se l'integrita non torna, la
# copia viene buttata e il servizio esce con errore: cosi si vede nel journal.
INTEGRITA=$(sqlite3 "$TMP" "PRAGMA integrity_check;" || echo "errore di lettura")
if [ "$INTEGRITA" != "ok" ]; then
    log "copia scartata, integrity_check ha risposto: $INTEGRITA"
    exit 1
fi

chmod 0600 "$TMP"
mv "$TMP" "$OUT"
trap - EXIT

TAGLIA=$(stat -c %s "$OUT")
log "copia fatta: $OUT ($TAGLIA byte)"

# Rotazione: si tengono le $KEEP piu recenti. Ordinamento per nome, che con
# questo formato di data e anche l'ordine cronologico.
VECCHIE=$(ls -1 "$DEST"/starkid-*.db 2>/dev/null | sort | head -n -"$KEEP" || true)
if [ -n "$VECCHIE" ]; then
    printf '%s\n' "$VECCHIE" | while IFS= read -r f; do
        rm -f -- "$f"
        log "rimossa copia vecchia: $f"
    done
fi

RIMASTE=$(ls -1 "$DEST"/starkid-*.db 2>/dev/null | wc -l || true)
log "copie presenti: $RIMASTE (limite $KEEP)"
