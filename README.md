<p align="center">
  <img src="docs/logo.svg" alt="StarKid" width="720">
</p>

<p align="center">
  <b>Un tablet Android che diventa il tablet di un bambino.</b><br>
  Solo le app che ha scelto un genitore, a pagine e divise per categorie, con
  undici giochi dentro l'app, limiti di tempo, fasce orarie e modalita kiosk.<br>
  <b>Niente pubblicita, niente acquisti, niente rete.</b>
</p>

---

StarKid mostra solo le app approvate, applica limiti di tempo e fasce orarie, e
puo bloccare il tablet in modalita kiosk. Tutte le impostazioni stanno dietro un
PIN.

- Kotlin + Jetpack Compose, grafica 3D in OpenGL ES per il gioco dei kart
- `minSdk` 26 (Android 8), `targetSdk` 35
- Pensato per il tablet, in orizzontale e in verticale
- **Nessun servizio di rete: tutto resta sul dispositivo.** Nessun account,
  nessuna telemetria, nessuna immagine o suono scaricati: le figure sono emoji
  di sistema, i mezzi sono geometria generata dal codice e i suoni sono
  sintetizzati al volo
- Provato su hardware vero, non solo su emulatore: vedi
  [Cosa e stato provato](#cosa-e-stato-provato-e-su-cosa)

## La schermata del bambino

In alto una barra con avatar, nome, orologio e tempo che resta; sotto le
categorie (Giochi, Video, Musica, Foto, Disegno, Imparo); poi le app a pagine,
con le frecce ai lati e i pallini in fondo.

Le app stanno su pagine affiancate e non in un elenco che scorre: frecce grandi
e pallini si capiscono prima di uno scorrimento, e ogni app resta sempre nello
stesso punto della sua pagina. La griglia si adatta allo schermo tenendo le
tessere quasi quadrate: su un tablet in orizzontale vengono 5 x 2, in verticale
3 x 4.

La categoria di ogni app e indovinata da quella che l'app dichiara nel proprio
manifest, quindi i giochi si riconoscono quasi sempre da soli; il genitore puo
spostarle in **Area genitori -> Sistema le categorie**. La barra compare solo
se c'e piu di una categoria da scegliere.

## I giochi di StarKid

Undici minigiochi scritti dentro l'app. Stanno nella stessa griglia delle app
installate e nelle stesse categorie: per un bambino sono tutte cose da
toccare. Il genitore puo spegnerli uno per uno in **Area genitori -> Giochi di
StarKid**.

| Gioco | Cosa si fa | Categoria |
| --- | --- | --- |
| Macchinine | Kart **in 3D**: si guida inclinando il tablet o col dito, scegliendo fra 3 kart, 8 colori e 4 piste | Giochi |
| Memory spaziale | Sei coppie da ritrovare | Giochi |
| Puzzle scorrevole | Nove caselle da rimettere in ordine | Giochi |
| Xilofono | Otto note da suonare | Musica |
| Conta le stelle | Quante ce ne sono | Imparo |
| Dove ce n'e di piu | Due mucchi da confrontare a occhio | Imparo |
| Completa la serie | Capire il motivo che si ripete | Imparo |
| Acchiappa la stella | Trenta secondi di riflessi | Giochi |
| Sequenza luminosa | Ripetere l'ordine delle luci | Imparo |
| Trova l'intruso | Quale figura non c'entra | Imparo |
| Disegna nello spazio | Tela libera, dieci colori, tre spessori | Disegno |

Perche esistono: su un tablet di un bambino il problema dei giochi gratuiti
non e il prezzo, e la pubblicita e gli acquisti in-app. Questi non hanno
nessuna delle due cose, non chiedono account e non toccano la rete.

**Niente file nell'APK.** Le figure sono emoji del font di sistema, le
macchinine sono disegnate con Canvas (ecco perche si possono colorare), e i
suoni sono **sintetizzati sul momento**: note con attacco immediato e coda
breve, calcolate in `games/Sfx.kt`. Il volume segue quello della musica di
sistema e i suoni si spengono dall'area genitori.

Il tempo passato sui giochi **conta nel limite giornaliero** come quello delle
app installate: il tetto non si aggira passando ai giochi di casa. Se il tempo
finisce mentre il bambino gioca, il blocco prende il posto del gioco.

### Il gioco dei kart, in tre dimensioni

Il kart e le piste sono disegnati in **OpenGL ES 2.0**, senza motori di gioco e
senza modelli da caricare (`games/gl/`):

- la scocca del kart e una **carena** che passa per otto sezioni, quindi il
  pianale si allarga in mezzo e il muso va a punta; le ruote sono cilindri con
  il cerchione, e sopra c'e un pilota col casco. Il colore e un parametro: per
  questo se ne possono scegliere ventiquattro combinazioni senza avere
  ventiquattro disegni;
- ogni pezzo e appeso alla trasformazione del kart intero, non ruota per conto
  proprio: altrimenti in curva il kart si smonta in volo;
- ogni pista ha la **sua scenografia**, generata lungo la strada: palazzi con
  finestre e marciapiedi in citta, palme e mare alla spiaggia, alberi al prato,
  pianeti con gli anelli nello spazio. Casa e albero prendono altezza e colore
  da un numero ricavato dalla loro posizione sulla strada, non dal caso, se no
  a ogni fotogramma cambierebbero faccia;
- lo sterzo e l'**accelerometro** (non il sensore di prossimita, che dice solo
  vicino o lontano). Gli assi del sensore non ruotano con lo schermo, quindi
  vanno convertiti con la rotazione del display; c'e un interruttore "sterzo al
  contrario" e si puo sempre guidare col dito.

Perche non un motore 3D: Filament o libGDX porterebbero decine di megabyte di
libreria nativa dentro un'app che deve restare residente come home del tablet, e
vorrebbero comunque i modelli dei mezzi in un file. Qui un kart sono numeri.

### Com'e fatta la parte giocabile

Il sorteggio di ogni giro (mazzo del memory, numeri da contare, intruso,
motivo della serie, mescolata del puzzle) sta in `domain/GameRounds.kt`:
funzioni pure che prendono il generatore casuale da fuori, quindi si provano
con un test normale invece che a occhio sul tablet. Il puzzle si mescola
facendo mosse legali e non sorteggiando le posizioni, perche meta delle
disposizioni di un quindici non si puo risolvere.

## Temi e sfondo

Due temi, in **Area genitori -> Tema, sfondo e avatar**:

- **Pulito** — tinte chiare, nessuna decorazione.
- **Spaziale** — cielo stellato, pianeta e razzo, tessere profonde.

Il tema cambia colori e decorazioni, mai la disposizione: un bambino che ha
imparato dove sono le cose non deve ritrovarle altrove.

Lo sfondo puo essere una delle quattro tinte del tema oppure **una foto scelta
dal genitore**. La foto viene copiata dentro l'app, ruotata secondo l'EXIF e
ridotta a 2560 px sul lato lungo: resta lo sfondo anche se viene cancellata
dalla galleria, e non costringe a decomprimere dodici megapixel a ogni avvio.
Sopra la foto il cielo stellato non si disegna, e la barra in alto prende un
velo in tinta col tema perche le scritte restino leggibili.

L'avatar accanto al nome si sceglie fra dodici emoji: arrivano dal font di
sistema, restano colorate a ogni dimensione e non pesano nell'APK.

## Com'e fatto

| Parte | File | Cosa fa |
| --- | --- | --- |
| Regole di blocco | `domain/Guard.kt` | Funzioni pure: fascia oraria, tetto giornaliero, limiti per app. Coperte da unit test. |
| Configurazione | `data/Prefs.kt` | SharedPreferences esposte come `StateFlow`; PIN salvato come SHA-256 con sale. |
| Vocabolario aspetto | `data/Appearance.kt` | Id dei temi e categorie delle app, con l'ipotesi presa dal manifest dell'app. |
| Sfondo fotografico | `data/BackgroundStore.kt` | Copia, ruota e riduce la foto del genitore. Riduzione e EXIF coperti da unit test. |
| Elenco app | `data/InstalledApps.kt` | App avviabili con etichetta e icona, caricate fuori dal thread principale. |
| Tempo d'uso | `usage/UsageReader.kt` | `UsageStatsManager`: minuti per app e app in primo piano. |
| Sorveglianza | `service/GuardService.kt` | Servizio in foreground, controlla ogni 4 secondi e richiama il blocco. |
| Kiosk | `admin/Kiosk.kt` | Politiche Device Owner e lock task. |
| Giri dei giochi | `domain/GameRounds.kt` | Sorteggio puro di ogni giro, coperto da unit test. |
| Giochi | `games/` | Gli undici minigiochi, il sintetizzatore dei suoni e lo sterzo a inclinazione. |
| Temi | `ui/Skin.kt`, `ui/Theme.kt` | I due temi: colori, tessere, tinte di sfondo, decorazioni. |
| Sfondo e stelle | `ui/Backgrounds.kt` | Disegna sfondo, cielo stellato e anteprima. |
| Schermate | `ui/` | Home del bambino, blocco, PIN, area genitori, aspetto, categorie. |

## Compilare

Servono un JDK 17 o superiore e l'SDK Android con platform 35 e build-tools 35.
Il percorso dell'SDK va in `local.properties` (`sdk.dir=...`).

```bash
./gradlew testDebugUnitTest assembleRelease
```

L'APK esce in `app/build/outputs/apk/release/`.

### Firma della release

La release si firma con una keystore dedicata, le cui credenziali stanno in
`keystore.properties` nella radice del progetto:

```properties
storeFile=starkid-release.jks
keyAlias=starkid
storePassword=...
keyPassword=...
```

Sia `keystore.properties` sia i file `*.jks` sono esclusi da git. Se
`keystore.properties` manca, la release viene prodotta **non firmata**: il
progetto compila comunque su una macchina che non ha le chiavi, ma l'APK non si
installa finche non lo firmi.

**La keystore non va perduta.** Android lega l'identita di un'app alla chiave
con cui e firmata: con una chiave diversa un aggiornamento viene rifiutato e
l'unica via e disinstallare e reinstallare, perdendo PIN e impostazioni. Tieni
una copia di `starkid-release.jks` e della sua password fuori da questa
macchina.

Per generarne una nuova:

```bash
keytool -genkeypair -keystore starkid-release.jks -alias starkid \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=StarKid, O=iotatec, C=IT"
```

## Installare sul tablet

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

Al primo avvio il launcher chiede di scegliere il PIN. Poi, nell'area genitori,
la sezione **Da sistemare** elenca cosa manca:

1. **Launcher predefinito** — cosi il tasto Home riporta qui.
2. **Accesso ai dati di utilizzo** — senza questo i limiti di tempo e il blocco
   delle app non consentite non funzionano. E un permesso che si concede a mano
   dalle impostazioni di sistema, non con una richiesta a comparsa.
3. **Mostra sopra le altre app** — serve al servizio per far comparire il blocco
   mentre il bambino e dentro un'altra app.

L'area genitori si apre tenendo premuto il lucchetto in alto a destra: una
pressione lunga, non un tocco, per non finirci dentro per sbaglio.

## Modalita kiosk

Il kiosk vero richiede che l'app sia **Device Owner**. Si imposta una volta
sola, su un tablet **senza account configurati**: appena uscito di fabbrica o
appena resettato. Non e una formalita: con anche un solo account Google sul
tablet il comando risponde

```
Not allowed to set the device owner because there are already some accounts on the device
```

e non c'e modo di aggirarlo. Va tolto l'account, o resettato il tablet, prima
di dare il comando.

1. Reset del tablet; durante la configurazione salta l'accesso a Google.
2. Attiva Opzioni sviluppatore e Debug USB.
3. Installa il launcher.
4. Collega il tablet e lancia:

```bash
adb shell dpm set-device-owner it.iotatec.starkid/it.iotatec.starkid.admin.KidsDeviceAdminReceiver
```

Se il comando risponde che non si puo impostare un Device Owner, sul dispositivo
e rimasto un account: serve un altro reset.

Da Device Owner, l'interruttore nell'area genitori accende il kiosk e:

- il bambino resta dentro il launcher e le app permesse (lock task);
- barra di stato, notifiche e app recenti sono chiuse;
- il launcher non si disinstalla e non si cambia home;
- avvio in modalita sicura, ripristino di fabbrica e nuovi utenti sono bloccati.

Il tasto Home resta attivo di proposito: riporta alla griglia delle app.

**Uscita dal kiosk:** area genitori, PIN, *Modalita kiosk* -> *Esci dal kiosk*.
Da li si puo anche restituire il ruolo di Device Owner al sistema, operazione da
fare prima di passare il tablet a qualcun altro.

### Perche senza Device Owner il kiosk non c'e

L'unica alternativa che Android offre e il blocco su schermo, che confina
l'utente nella **singola** app bloccata: il bambino non potrebbe aprire nemmeno
le app consentite, e l'uscita e comunque gestita dal sistema, non dal nostro
PIN. Per questo l'app non lo propone. Senza Device Owner restano il launcher
come home predefinita, i limiti di tempo e il blocco delle app non consentite,
che funzionano comunque.

## Cosa e stato provato, e su cosa

Provato su un **Lenovo Tab M10 FHD Plus (TB-X606FA), Android 10, API 29**,
1200x1920 a densita 1.5x, con l'APK di release firmato:

- avvio come home predefinita, senza crash;
- `GuardService` in foreground e blocco di un'app non consentita;
- le due schermate del bambino nei due temi, in orizzontale e in verticale;
- scelta della foto col selettore di sistema, riduzione e rotazione;
- categorie indovinate dal manifest delle app installate.

Il **kiosk Device Owner** e stato provato per intero sullo stesso tablet, dopo
averne tolto l'account Google:

| Prova | Esito |
| --- | --- |
| `dpm set-device-owner` | `Success`, confermato da `dumpsys device_policy` |
| Attivazione del kiosk | `mLockTaskModeState=LOCKED` |
| Barra di stato | sparita |
| App permessa (un gioco Unity) | si apre e resta dentro il lock task |
| App non permessa (Impostazioni) | rifiutata, `error code 101` (violazione lock task) |
| Tasto Home | riporta alla schermata del bambino |
| Aggiornamento dell'APK | il kiosk resta attivo |
| Uscita dal kiosk col PIN | `mLockTaskModeState=NONE`, Impostazioni di nuovo apribili |
| Rinuncia a Device Owner | il sistema non ha piu un Device Owner, la UI torna a proporre il comando adb |

Da quella prova e uscito un buco che la sola compilazione non poteva mostrare:
l'Activity e `singleTask` e non viene ricreata, quindi premendo Home si
ritrovava la schermata precedente. Se un genitore lasciava aperta la propria
area, al bambino bastava il tasto Home per avere davanti **Esci dal kiosk**.
Ora `onNewIntent` riporta sempre alla schermata del bambino.

## Limiti noti

- I minuti si leggono da `UsageStatsManager`, che aggrega gli eventi di sistema:
  il conteggio e accurato nell'ordine di qualche secondo, non al millisecondo.
- Il controllo scatta a intervalli di 4 secondi, quindi un'app non consentita
  resta visibile per un istante prima del blocco.
- Senza Device Owner un bambino piu grande puo disinstallare il launcher dalle
  impostazioni o cambiare home. Il kiosk e la risposta a questo.
- Il PIN e di 4-8 cifre: protegge da un bambino, non da un adulto motivato.
