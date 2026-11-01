# StarKid - il server di configurazione

Un server piccolo che tiene la configurazione dei tablet e una web UI per il
genitore. Gira su `starkid.antoninomiccio.com`: Ubuntu 24.04 su Oracle Cloud,
2 vCPU e **954 MB di RAM in tutto**, con dodici siti nginx gia in produzione.

Un inquilino solo, un account solo, creato da riga di comando. Niente
iscrizione dal web. La configurazione va in una direzione sola, server verso
tablet. Il nome del bambino non arriva mai qui.

Questa cartella contiene il server e, in `deploy/`, tutto quello che serve a
installarlo. **I file di `deploy/` non fanno niente da soli**: li esegue una
persona, sul server, nell'ordine scritto qui sotto.

---

## Da leggere prima di cominciare

Quattro cose che su questa macchina fanno danno se si scoprono dopo.

### 1. Il `pip install` e il momento peggiore

Ci sono **446 MB liberi e 414 MB di swap gia in uso**. Installare le
dipendenze e l'unico passo che chiede un picco di memoria vero: se in quel
momento gira anche altro, l'OOM killer sceglie lui chi uccidere, e puo
scegliere uno dei dodici siti.

Si fa quando non gira nient'altro. Prima di partire:

```sh
free -m
systemctl list-timers --all | head -20
pgrep -a 'apt|apt-get|dpkg|unattended-upgr'
```

Le dipendenze si installano con `--only-binary :all:`, cioe **solo pacchetti
precompilati**. Se per una libreria manca il wheel, `pip` si ferma con un
errore invece di provare a compilare. Quello e il comportamento che vogliamo:
compilare qui dentro significa riempire la RAM e perdere la macchina. La
soluzione non e togliere `--only-binary`, e fissare in `requirements.txt` una
versione che abbia il wheel.

### 2. Un'altra sessione automatica con sudo senza password

Su questa macchina c'e un'altra sessione automatica che puo usare `sudo`
senza password. **Una sola alla volta.** Due cose che installano insieme, su
950 MB di RAM e con nginx in mezzo, non si limitano a rallentarsi: si pestano
i piedi su `nginx -t`, sul reload e sulla memoria.

`install.sh` prende un lock in `/var/lock/starkid-install.lock` e rifiuta di
partire se ne trova un altro in corso, ma non vede l'altra sessione: quella
va fermata guardando con i propri occhi.

### 3. C'e un riavvio in sospeso

La macchina ha un riavvio in sospeso (`/var/run/reboot-required`). Si puo
installare comunque, ma va messo in conto:

- il riavvio arrivera, e quando arriva il servizio deve ripartire da solo:
  per questo `install.sh` fa `systemctl enable`, e dopo il primo riavvio
  **va verificato** che sia davvero ripartito;
- se il riavvio avviene a metà installazione, si rilancia `install.sh`: e
  idempotente;
- se e possibile, meglio riavviare **prima**: si parte da una macchina con la
  RAM pulita e meno swap in uso, che e esattamente quello che serve al passo
  del `pip install`.

### 4. certbot e gia rosso per tre domini altrui

Su questa macchina `certbot` ha tre lineage rotte, di domini che non sono
nostri. Il risultato e che `certbot renew` **esce gia adesso con errore**, e
il timer di rinnovo risulta gia fallito.

Questo vuol dire una cosa sola, ma importante: **il giorno in cui sara il
rinnovo di StarKid a rompersi, non si vedra**, perche il rosso c'e gia. Il
certificato scadra senza che nessuno se ne accorga, e il sito smettera di
rispondere da un giorno all'altro.

Due modi di cavarsene, non alternativi:

- sistemare o rimuovere le tre lineage rotte (`certbot certificates`, poi
  `certbot delete --cert-name <nome>`), cosi il rosso torna a significare
  qualcosa. **Sono di altri: non si toccano senza decidere di toccarle.**
- nel frattempo, controllare StarKid per conto suo:
  ```sh
  certbot certificates 2>/dev/null | grep -A3 starkid.antoninomiccio.com
  echo | openssl s_client -servername starkid.antoninomiccio.com \
      -connect starkid.antoninomiccio.com:443 2>/dev/null \
      | openssl x509 -noout -enddate
  ```
  La seconda riga guarda il certificato che il sito sta davvero servendo, che
  e la cosa che conta.

---

## Cosa deve esserci nel repo prima di installare

`install.sh` non scrive codice: copia quello che trova. Nella cartella
`server/` servono:

| Percorso | Cos'e |
| --- | --- |
| `app/` | il pacchetto Python. La unit systemd avvia **`app.main:app`** |
| `app/cli.py` | i comandi di amministrazione: crea l'account, riarruola un tablet (passo 6) |
| `web/` | la web UI: `index.html` e il resto. L'installazione la copia in `/opt/starkid/static` e la serve nginx, non Python |
| `requirements.txt` | le dipendenze, con versioni fissate |
| `deploy/` | i file di questo documento |

Il server legge la sua configurazione da `/etc/starkid/starkid.env`, che
`install.sh` crea la prima volta. Qui dentro ci sono **solo** le variabili che
il codice legge davvero:

| Variabile | Cos'e |
| --- | --- |
| `STARKID_DB` | `/var/lib/starkid/starkid.db` - l'unico posto dove il servizio puo scrivere |
| `STARKID_COOKIE_SECURE` | `1`: il cookie esce solo in HTTPS |

Facoltative, non scritte nel file:

| Variabile | Cos'e |
| --- | --- |
| `STARKID_LOG_LEVEL` | `INFO` se non detto altrimenti |
| `STARKID_CERT` | dove sta il certificato, per contare i giorni che restano. Il servizio quasi certamente non lo puo leggere (`ProtectSystem=strict`), e in quel caso la pagina "Come sta" scrive che il server non lo dice, invece di inventare un numero |
| `STARKID_DOCS` | `1` accende la documentazione automatica delle rotte. In produzione resta spenta |

Il file e `0640 root:starkid`: lo legge il servizio, non lo puo riscrivere.
**Non va nel repo e non va in copia da nessuna parte.**

Una variabile che **non** c'e, e che prima c'era: `STARKID_SECRET_KEY`.
`install.sh` la generava e nessuna riga di codice la leggeva, perche il cookie
di sessione non e una cosa firmata: e un token casuale opaco di cui in tabella
sta solo l'hash. Un segreto finto e peggio di nessun segreto - fra un anno
qualcuno lo ruota convinto di invalidare le sessioni, e non invalida niente.

---

## Dove finisce ogni cosa

| Sul server | Di chi e | Cos'e |
| --- | --- | --- |
| `/opt/starkid/app`, `/opt/starkid/static` | `root:root`, 0755 | il codice. Il servizio lo legge e non lo puo modificare |
| `/opt/starkid/venv` | `root:root` | l'ambiente Python |
| `/opt/starkid/bin/starkid-backup.sh` | `root:root`, 0755 | lo script della copia di sicurezza |
| `/var/lib/starkid/starkid.db` | `starkid:starkid`, 0750 | il database |
| `/var/lib/starkid/backup/` | `starkid:starkid`, 0700 | le 7 copie a rotazione |
| `/etc/starkid/starkid.env` | `root:starkid`, 0640 | la configurazione del servizio |
| `/etc/systemd/system/starkid*.{service,timer}` | `root:root` | le unita |
| `/etc/nginx/sites-available/starkid.antoninomiccio.com` | `root:root` | il vhost (piu il symlink in `sites-enabled`) |
| `/opt/starkid/bin/starkid-ruota-log.sh` | `root:root`, 0755 | ruota i log qui sotto |
| `/etc/systemd/system/starkid-ruota-log.{service,timer}` | `root:root` | il timer che lo chiama, ogni ora |
| `/var/log/starkid/{access,error}.log` | `www-data:adm`, 0750 | log propri, in una cartella propria (vedi sotto) |
| `/var/backups/starkid-<data>/` | `root:root` | copia dei file che l'installazione ha sostituito |

I log stanno in `/var/log/starkid/` e non in `/var/log/nginx/` per un motivo
pratico, e la rotazione e un timer nostro e non logrotate per un altro.

La cartella separata: il logrotate di nginx prende `/var/log/nginx/*.log`, e
due file che elencano lo stesso percorso fanno `duplicate log entry`, col
secondo saltato **in silenzio**.

Il timer invece di logrotate: **su questa macchina logrotate non e
installato**. Il binario non c'e e il pacchetto nemmeno, benche
`/etc/logrotate.d/` sia pieno di configurazioni — nginx, fail2ban, certbot,
apt — che quindi non esegue nessuno. Vuol dire che **nessun log di questa
macchina sta ruotando**, non solo i nostri: e una cosa da sapere, e non e
roba di StarKid. Installare il pacchetto avrebbe messo mano a una macchina in
produzione di qualcun altro e fatto partire di colpo la rotazione di tutto,
che nessuno ha chiesto. Il timer tocca due file soli.

E il tetto serve: ogni richiesta rifiutata da `limit_req` scrive due righe
(una nell'access e un warn nell'error), e a disco pieno si fermano nginx,
journald e fail2ban per tutti i siti, non solo per StarKid. Per questo il
controllo e **ogni ora** e non una volta al giorno: cento megabyte, sotto un
flusso di richieste rifiutate, si scrivono in pochi minuti.

Il webroot ACME segue la convenzione della macchina,
`/var/www/<dominio>/html`, non una cartella condivisa: e cosi che fanno gia
tutti e dodici gli altri siti.

Il servizio ascolta **solo** su `127.0.0.1:8082`. Verso l'esterno non si apre
nessuna porta nuova: c'e nginx davanti e basta.

---

## La procedura

Si fa tutto da una sessione SSH sola, con calma, leggendo l'output di ogni
passo prima di fare il successivo. I comandi sono da eseguire nella cartella
`server/` del repo, sul server.

### Passo 0 - guardare la macchina senza toccarla

```sh
cd /percorso/del/repo/server
chmod +x deploy/*.sh          # se i file sono arrivati da Windows o da uno zip
sudo ./deploy/install.sh --check
```

`--check` non modifica niente: controlla i comandi necessari, la RAM libera,
la swap in uso, il riavvio in sospeso, se la porta 8082 e libera, se i file
del repo ci sono, e soprattutto se **`nginx -t` passa gia adesso**.

**Cosa deve succedere:** finisce con "Solo controlli: non ho modificato
niente".

**Se si ferma su `nginx -t`:** la configurazione di nginx era gia rotta prima
di noi. Si sistema quella, prima. Non si deve finire a confondere un problema
che c'era con uno che abbiamo portato noi.

### Passo 1 - installare il servizio

```sh
sudo ./deploy/install.sh
```

Lo script chiede una prima conferma (si scrive `avanti`), poi:

1. crea utente e gruppo `starkid`, di sistema, senza shell e senza home;
2. crea le cartelle con i permessi della tabella qui sopra;
3. crea il venv e installa le dipendenze (**il momento del picco di RAM**,
   dentro uno scope `systemd-run` con `MemoryMax=220M`: se sfonda muore il
   `pip`, non un servizio di un altro sito);
4. copia `app/` in `/opt/starkid/app` e `web/` in `/opt/starkid/static`, di root;
5. crea `/etc/starkid/starkid.env`, se non c'e gia;
6. installa le tre unita systemd, avvia il servizio e il timer della copia;
7. verifica `http://127.0.0.1:8082/healthz` **prima di toccare nginx**;
8. installa il timer che ruota i log (ogni ora, tetto 100 MB per file);
9. scrive il vhost e si ferma a chiedere il reload (passo 3 qui sotto).

Sotto i **250 MB di RAM libera lo script si ferma** invece di avvisare e
tirare avanti: l'OOM killer e globale e sceglie il processo piu grosso della
macchina, che non e `pip`. Con `--forza` si installa comunque (il tetto di
memoria sul `pip` resta).

**Cosa controllare dopo:**

```sh
systemctl status starkid.service
curl -s http://127.0.0.1:8082/healthz
systemctl show -p MemoryCurrent --value starkid.service   # byte; atteso: ben sotto 192 MB
free -m
journalctl -u starkid.service -n 50 --no-pager
```

**Come accorgersi che e andata male:**

| Sintomo | Cosa vuol dire |
| --- | --- |
| `pip` si ferma su "no matching distribution" o "only binary" | manca il wheel per una libreria. Si fissa una versione che ce l'ha, non si toglie `--only-binary` |
| il processo del `pip` viene ucciso senza messaggio | OOM. `dmesg -T \| tail -30` lo conferma. Si riprova a macchina scarica, meglio dopo un riavvio |
| lo script esce dicendo che il servizio non risponde su 8082 | il codice non parte. Stampa lui stesso `status` e le ultime 40 righe del journal. **nginx non e stato toccato: la macchina e come prima** |
| `status` dice `Failed with result 'exit-code'` subito dopo l'avvio | quasi sempre un errore di import o la unit troppo stretta: vedi "Se il servizio non parte" in fondo |

A questo punto nginx non e ancora stato ricaricato: da fuori il sito non
risponde. Non e un problema, e il punto in cui ci si ferma.

### Passo 2 - il certificato (solo la prima volta)

Alla prima passata il certificato non esiste, e un vhost che lo cita non
passerebbe `nginx -t`. Per questo `install.sh` installa prima un vhost di
**sola porta 80**, che serve unicamente la verifica ACME. Va attivato (passo
3), e solo dopo si chiede il certificato:

```sh
sudo certbot certonly --webroot -w /var/www/starkid.antoninomiccio.com/html -d starkid.antoninomiccio.com
```

**`certonly` e `--webroot`, non `--nginx`**: il plugin nginx riscriverebbe i
file dei vhost, tutti e dodici, a modo suo.

Prima, una verifica che costa dieci secondi: gli altri domini di questa
macchina hanno gia un loro webroot ACME, e conviene usare lo stesso invece di
aggiungerne un secondo.

```sh
grep -rh -A4 'acme-challenge' /etc/nginx/sites-available/ | grep -E 'root|alias' | sort -u
```

Se viene fuori una cartella diversa da `/var/www/starkid.antoninomiccio.com/html`, si cambia il
`root` nei due blocchi ACME di `deploy/starkid.nginx.conf` e nel comando qui
sotto. Se la cartella c'era gia, `install.sh` non ne tocca ne proprietario ne
permessi.

**Cosa controllare dopo:**

```sh
sudo certbot certificates | grep -A4 starkid.antoninomiccio.com
sudo ls -l /etc/letsencrypt/live/starkid.antoninomiccio.com/
```

**Se la verifica fallisce:** quasi sempre il vhost di porta 80 non e attivo,
o il DNS non risponde ancora.

```sh
curl -I http://starkid.antoninomiccio.com/.well-known/acme-challenge/prova   # atteso: 404 da nginx, non un timeout
dig +short starkid.antoninomiccio.com
sudo tail -20 /var/log/starkid/error.log
```

Un 404 va bene: vuol dire che la richiesta arriva qui e nginx risponde. Un
timeout o il certificato di un altro sito vogliono dire che la richiesta sta
finendo da un'altra parte.

Poi si rilancia `install.sh`: la seconda volta trova il certificato e
installa il vhost completo con TLS.

```sh
sudo ./deploy/install.sh
```

### Passo 3 - il reload di nginx (il momento delicato)

`install.sh` si ferma qui e chiede di scrivere `ricarica`. Prima ha gia
eseguito `nginx -t`.

Quel reload **rilegge tutti i tredici vhost**, compresi i tre che fanno da
proxy verso le macchine di casa. `nginx -t` dice che la sintassi va bene, non
che il resto del mondo e a posto: un `listen` su un indirizzo che non si puo
occupare passa il test e fallisce al reload.

Se si risponde qualcosa di diverso da `ricarica`, lo script **ritira il
symlink** e lascia la macchina esattamente come l'ha trovata. Il vhost resta
pronto in `sites-available` e si attiva quando si vuole:

```sh
sudo ln -s /etc/nginx/sites-available/starkid.antoninomiccio.com \
           /etc/nginx/sites-enabled/starkid.antoninomiccio.com
sudo nginx -t && sudo systemctl reload nginx
```

**Cosa controllare subito dopo il reload, in quest'ordine:**

```sh
systemctl is-active nginx
curl -I https://starkid.antoninomiccio.com/            # atteso: 200
curl -s  https://starkid.antoninomiccio.com/healthz    # atteso: la risposta del servizio
curl -I  http://starkid.antoninomiccio.com/            # atteso: 301 verso https
```

E poi, **senza salti**, i siti degli altri: due o tre a mano, e in particolare
i tre che fanno da proxy verso casa.

```sh
ls /etc/nginx/sites-enabled/
sudo tail -30 /var/log/nginx/error.log
```

**Se qualcosa si e rotto, si torna indietro in dieci secondi:**

```sh
sudo rm /etc/nginx/sites-enabled/starkid.antoninomiccio.com
sudo nginx -t && sudo systemctl reload nginx
```

Non serve altro: il nostro vhost e un file e un symlink, non modifica niente
di comune. I file che l'installazione ha eventualmente sostituito sono in
`/var/backups/starkid-<data>/`.

**Reload, non restart.** `systemctl restart nginx` chiude tutte le
connessioni dei dodici siti e, se la configurazione ha un problema, nginx non
riparte piu. Il reload tiene in piedi la vecchia configurazione se la nuova
non va.

### Passo 4 - il 502

Se `https://starkid.antoninomiccio.com/` risponde **502 Bad Gateway**, nginx
sta bene ed e il servizio dietro che non risponde:

```sh
systemctl status starkid.service
curl -s http://127.0.0.1:8082/healthz
sudo tail -20 /var/log/starkid/error.log
```

Se invece la pagina arriva ma **la web UI resta bianca**, il server Python sta
rispondendo e il problema e nei file statici: `ls -l /opt/starkid/static` e
poi la console del browser.

### Passo 5 - la copia di sicurezza

Il timer e gia attivo da `install.sh`. Si verifica senza aspettare la notte:

```sh
systemctl list-timers starkid-backup.timer
sudo systemctl start starkid-backup.service
journalctl -u starkid-backup.service -n 20 --no-pager
sudo ls -l /var/lib/starkid/backup/
```

**Cosa deve succedere:** nel journal una riga "copia fatta: ... byte", e un
file `starkid-<data>.db` nella cartella. Il numero di copie non supera mai 7.

La copia usa `sqlite3 .backup`, non `cp`: funziona anche mentre il server
scrive. Dopo la copia viene fatto un `PRAGMA integrity_check`, e se non
risponde `ok` la copia viene buttata e il servizio esce con errore. Un errore
di `starkid-backup.service` nel journal e quindi da prendere sul serio: vuol
dire che una copia non era buona.

**Come si ripristina** (il servizio deve essere fermo):

```sh
sudo systemctl stop starkid.service
sudo -u starkid cp /var/lib/starkid/backup/starkid-<data>.db /var/lib/starkid/starkid.db
sudo -u starkid rm -f /var/lib/starkid/starkid.db-wal /var/lib/starkid/starkid.db-shm
sudo systemctl start starkid.service
```

Su questa macchina **cron non esiste**: i lavori periodici sono timer di
systemd. Non ha senso cercare `crontab -e`.

### Passo 6 - l'account del genitore

Non c'e iscrizione dal web: l'unico account si crea da qui.

```sh
sudo -u starkid env -C /opt/starkid /opt/starkid/venv/bin/python -m app.cli crea-utente <nomeutente>
```

`env -C /opt/starkid` non e un vezzo: `python -m app.cli` cerca il pacchetto
`app` nella cartella da cui lo lanci, e lanciandolo da casa tua risponde
`No module named app`. Il servizio non se ne accorge perche systemd gli mette
gia `WorkingDirectory=/opt/starkid`.

E un **nome utente**, non un'email. La password si scrive quando la chiede:
**non va passata come argomento**, finirebbe nella cronologia della shell e,
per qualche secondo, nella lista dei processi visibile a tutti.

**Cosa controllare dopo:** si prova l'accesso dal browser, e si guarda che un
tentativo sbagliato finisca nel log con il 401:

```sh
sudo tail -5 /var/log/starkid/access.log
```

#### Gli altri comandi di `app.cli`

Si lanciano tutti allo stesso modo:

```sh
sudo -u starkid env -C /opt/starkid /opt/starkid/venv/bin/python -m app.cli <comando>
```

| Comando | Cosa fa |
| --- | --- |
| `stato` | cos'e nel database: account, tablet, revisioni, sessioni aperte |
| `cambia-password` | cambia la password e chiude tutte le sessioni web |
| `sblocca` | azzera i tentativi falliti, per quando ci si chiude fuori da soli |
| `riarruola <id>` | **revoca il token di un tablet** ed emette quello nuovo |

`riarruola` e l'unico modo di revocare il token di un dispositivo, e serve in
tre casi che prima erano vicoli ciechi: il tablet venduto o perso, il token
finito in mano a qualcuno (il figlio piu grande che si legge il bundle di
provisioning), e il token di arruolamento scaduto prima del primo avvio. Chi
aveva il token vecchio continuava a leggere ogni nuova configurazione - gli
orari, i giorni, il limite, le app - per sempre. L'alternativa era cancellare
il tablet e ricrearlo, che butta via le dieci revisioni e cambia l'id: tutta
la configurazione da rifare a mano. Con `riarruola` id e storico restano, e il
token nuovo si vede **una volta sola**.

### Passo 7 - fail2ban (da decidere, non e automatico)

`install.sh` **non tocca fail2ban**. I due file sono qui da leggere:

| File del repo | Dove andrebbe |
| --- | --- |
| `deploy/fail2ban-starkid-filter.conf` | `/etc/fail2ban/filter.d/starkid-auth.conf` |
| `deploy/fail2ban-starkid.conf` | `/etc/fail2ban/jail.d/starkid.conf` |

La jail nasce con `enabled = false`: anche dopo averla copiata non fa niente
finche non la si accende a mano. Prima:

1. il log deve esistere, cioe il vhost deve avere ricevuto almeno una
   richiesta: `ls -l /var/log/starkid/access.log`.
   **Se il file non c'e, fail2ban non riparte e si porta dietro anche le
   quattro jail che funzionano adesso, sshd compresa.**
2. provare il filtro su righe vere, senza accendere niente:
   ```sh
   sudo fail2ban-regex /var/log/starkid/access.log \
        /etc/fail2ban/filter.d/starkid-auth.conf
   ```
   Deve contare i `POST /api/v1/auth/login` finiti in 401 e nient'altro.
3. decidere se mettere il proprio indirizzo in `ignoreip`: cinque password
   sbagliate sono un blocco di un'ora sulla stessa macchina da cui si
   amministra.

Poi `enabled = true` e:

```sh
sudo fail2ban-client reload          # reload, non restart: un restart azzera i blocchi in corso
sudo fail2ban-client status starkid-auth
```

C'e anche una seconda cosa, piu piccola, da decidere: il vhost scrive i
rifiuti di `limit_req` in `/var/log/starkid/error.log`, mentre la jail
`nginx-limit-req` gia attiva guarda `/var/log/nginx/error.log`. Per farle
vedere anche i nostri va aggiunto il percorso a **quella** jail, che e una
jail esistente e funzionante: e una modifica da fare di proposito, sapendo che
se si sbaglia si perde anche quello che funziona oggi.

---

## Tornare indietro

```sh
sudo ./deploy/uninstall.sh            # lascia database, copie e segreti
sudo ./deploy/uninstall.sh --purge    # cancella anche quelli
```

Chiede conferma all'inizio (`disinstalla`), e di nuovo prima del reload di
nginx, e una terza volta prima di cancellare i dati. Non tocca il
certificato, il webroot ACME, gli altri vhost, fail2ban e i pacchetti.

Il vhost non viene cancellato ma rinominato in
`...rimosso-<data>`: se si e sbagliato, si rimette con un symlink.

Dopo `--purge` i tablet vanno riarruolati: i loro token stanno nel database.

---

## Se il servizio non parte

La unit e volutamente stretta. Se nel journal compare un errore di permessi,
di `seccomp` o di memoria, il problema e quasi sempre una di queste righe, in
quest'ordine di probabilita:

| Riga | Cosa salta | Come si vede |
| --- | --- | --- |
| `MemoryMax=192M` | il processo viene ucciso sotto carico | `journalctl -u starkid.service \| grep -i memory`, oppure `oom` in `dmesg -T` |
| `ReadWritePaths=/var/lib/starkid` | il codice cerca di scrivere altrove (una cache, un file temporaneo fuori da `/tmp`) | `Read-only file system` nel journal |
| `SystemCallFilter` | una libreria usa una chiamata di sistema fuori da `@system-service` | il processo muore con `SIGSYS` |
| `ProcSubset=pid` | il codice legge `/proc/meminfo` o simili (per esempio per riportare la memoria in `/healthz`): dentro la unit non li vede | `FileNotFoundError: /proc/meminfo` nel journal |
| `MemoryDenyWriteExecute=yes` | una libreria con JIT o codice generato a runtime | errore di `mmap` all'import |
| `IPAddressDeny=any` | il servizio prova a uscire in rete (non deve: la configurazione va in una direzione sola) | connessioni rifiutate verso l'esterno |

Il modo giusto di provare e **non** modificare il file installato, ma usare un
override, che si vede e si toglie in un secondo:

```sh
sudo systemctl edit starkid.service     # scrivi [Service] e la riga da cambiare
sudo systemctl daemon-reload && sudo systemctl restart starkid.service
sudo systemctl revert starkid.service   # per rimetterlo come il file del repo
```

Se una riga va rimossa per davvero, va rimossa **nel repo** e spiegata, non
solo sul server: alla prossima installazione tornerebbe.

---

## Le scelte, in breve

- **Niente porte nuove.** Il servizio ascolta su `127.0.0.1:8082`, c'e nginx
  davanti. Fuori dalla macchina si vedono solo la 80 e la 443, che erano gia
  aperte.
- **Il servizio non puo riscriversi.** Il codice e di root; l'unico posto
  scrivibile e `/var/lib/starkid`.
- **Il servizio non puo uscire in rete** (`IPAddressDeny=any` con la sola
  eccezione del loopback). La configurazione va da server a tablet: a questo
  server non serve chiamare nessuno.
- **192 MB di limite di memoria**, con `MemoryHigh` a 160 per fargli
  recuperare memoria prima di arrivare al limite duro. Un worker solo: su
  questa macchina il vincolo e la RAM, non la concorrenza.
- **Due limiti di frequenza separati**: largo sull'API (10/s: un tablet ne fa
  molte meno), stretto sull'accesso (10 al minuto). Le zone si chiamano
  `starkid_api`, `starkid_login` e `starkid_conn` per non sovrapporsi a quelle
  di `conf.d/ratelimit.conf`, che sono globali.
- **`auth_basic off` nell'API dei dispositivi e nel blocco ACME.** I tablet si
  autenticano col loro Bearer token. Se un domani comparisse un `auth_basic`
  nella configurazione comune, i tablet si fermerebbero tutti insieme e il
  rinnovo del certificato con loro.
- **HSTS senza `includeSubDomains` e senza `preload`.** Sul dominio ci sono
  altri host, e un preload sbagliato non si disfa piu.
- **Log di nginx separati, in una cartella propria con un tetto.** Servono a
  fail2ban, tengono StarKid fuori dai log degli altri dodici siti, e non
  possono riempire il disco (100 MB, poi ruotano).
- **Niente `http2` sul nostro `listen`.** In nginx 1.24 HTTP/2 si accende col
  parametro di `listen`, e i parametri di protocollo appartengono al
  **socket**, non al vhost: il primo `listen` analizzato per `0.0.0.0:443`
  decide per tutti i server block che condividono la porta. Siccome
  `sites-enabled/` si legge in ordine alfabetico, scriverlo qui vorrebbe dire
  accenderlo a sorte su tutti e dodici i siti - e nei tre che proxano verso
  casa l'upgrade a WebSocket in HTTP/2 non esiste, quindi si romperebbero.
  Accendere HTTP/2 e una decisione per l'intera macchina, da prendere
  guardando `grep -rn 'listen .*443' /etc/nginx/sites-available/`.
- **IPv6 commentato** nel vhost: se la macchina non ha IPv6 attivo, un
  `listen [::]:80` passa `nginx -t` e fa fallire il bind al reload, e quel
  reload tocca tutti i siti. Si accende dopo aver guardato `ss -ltn`.
- **Il `pip install` sotto un tetto di memoria** (`systemd-run --scope`), e
  sotto i 250 MB liberi lo script si ferma. L'OOM killer e globale: sceglie
  il processo piu grosso della macchina, che non e `pip` ma un servizio di un
  altro sito. Era l'unico passo dell'installazione con raggio d'azione fuori
  da StarKid.
- **Il threadpool a 8 thread, non 40.** Gli endpoint sono sincroni e finiscono
  nel threadpool di anyio; ogni thread tiene aperta la sua connessione a
  SQLite e ogni verifica di password costa 16 MiB. Col default si sfondava
  `MemoryMax` e il servizio moriva e ripartiva in ciclo.
- **Due verifiche di password alla volta** (un semaforo). L'attesa crescente
  conta i fallimenti gia scritti, quindi non ferma le richieste *in volo*: una
  manciata di accessi simultanei bastava a far uccidere il servizio per
  memoria. Ora chi insiste aspetta il turno.
- **L'attesa crescente si applica all'indirizzo, non al nome utente.** C'e un
  account solo: contarla sul nome significava che chiunque lo indovinasse
  poteva chiudere fuori il genitore da ogni indirizzo, con il rimedio via SSH.
  Il nome si continua a contare, per vederlo nei log.
- **I campi che il tablet dichiara si chiamano `_dichiarata`.** Le spie di
  salute e l'esito dell'ack li scrive il tablet e il server non puo
  verificarli: chi ne avesse il token puo mandarli tutti verdi piu spesso del
  tablet vero. Il nome del campo lo dice, cosi la pagina non li presenta come
  fatti.
- **Il `motivo` di un campo rifiutato e un vocabolario chiuso.** Era testo
  libero: 200 caratteri per campo e 40 campi fanno ottomila caratteri scritti
  da un dispositivo dentro la pagina del genitore. Non era XSS, era phishing.
  Il tablet manda un'etichetta, il testo per le persone sta nella web UI.
- **Il token di un dispositivo si puo revocare** (`app.cli riarruola`), senza
  buttare via storico e id.
- **Il reload di nginx non e automatico**, e il no e reversibile: se si dice
  no, lo script ritira anche il symlink, perche un reload fatto da qualcun
  altro (o da certbot) non attivi il vhost a sorpresa.
