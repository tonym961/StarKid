#!/usr/bin/env bash
# =============================================================================
# StarKid - rotazione dei propri log di nginx, senza logrotate.
#
# PERCHE NON LOGROTATE. Su questa macchina logrotate NON E INSTALLATO: il
# binario non c'e e il pacchetto nemmeno, benche /etc/logrotate.d/ sia pieno di
# configurazioni (nginx, fail2ban, certbot, apt) che quindi non esegue nessuno.
# Installarlo avrebbe voluto dire mettere un pacchetto su una macchina in
# produzione di qualcun altro e, di colpo, far partire la rotazione di TUTTI i
# log della macchina, che oggi non ruotano: un cambiamento che nessuno ha
# chiesto. Questo script invece tocca due file soli, i nostri.
#
# PERCHE SERVE. Ogni richiesta rifiutata da limit_req scrive DUE righe, una
# nell'access e un warn nell'error, circa 450 byte. Con "nodelay" il rifiuto
# non costa niente a chi lo riceve, quindi un flusso si regge a lungo: a 2000
# richieste al secondo si scrive quasi 1 MB al secondo. Su un disco da 45 GB
# condiviso con dodici siti, un log senza tetto e un modo silenzioso di
# fermare tutto - nginx che non scrive piu, journald bloccato, fail2ban che
# perde i suoi file.
#
# COME. nginx riapre i suoi file quando riceve USR1: si sposta il file, si
# segnala nginx, e lui ne crea uno nuovo. Niente copytruncate, niente righe
# perse a meta.
# =============================================================================

set -euo pipefail

CARTELLA=/var/log/starkid
TETTO_MB=100
QUANTI=7

[ -d "$CARTELLA" ] || exit 0

ruotato=no

for log in "$CARTELLA"/access.log "$CARTELLA"/error.log; do
    [ -f "$log" ] || continue
    mb=$(( $(stat -c %s "$log") / 1024 / 1024 ))
    [ "$mb" -ge "$TETTO_MB" ] || continue

    # Si libera subito il posto del .1, poi si fa scalare tutto di uno.
    rm -f "$log.$QUANTI.gz"
    for n in $(seq $((QUANTI - 1)) -1 1); do
        [ -f "$log.$n.gz" ] && mv -f "$log.$n.gz" "$log.$((n + 1)).gz"
    done
    mv -f "$log" "$log.1"
    ruotato=yes
    echo "ruotato $log (${mb} MB)"
done

if [ "$ruotato" = yes ]; then
    # Il segnale va mandato UNA volta sola, dopo aver spostato tutti i file:
    # mandarlo in mezzo farebbe riaprire nginx a meta lavoro.
    if [ -f /run/nginx.pid ]; then
        kill -USR1 "$(cat /run/nginx.pid)"
        # Il tempo che nginx chiuda i vecchi descrittori prima di comprimerli:
        # gzip su un file ancora aperto in scrittura da' un archivio troncato.
        sleep 2
    fi
    for log in "$CARTELLA"/access.log "$CARTELLA"/error.log; do
        [ -f "$log.1" ] && gzip -f "$log.1"
    done
fi

exit 0
