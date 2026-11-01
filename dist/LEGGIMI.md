# APK pronte all'uso

Qui stanno le build firmate che si installano senza compilare niente. Ci sono
perche l'app dei genitori non passa dal Play Store: la si scarica da qui, la si
mette sul telefono e si apre.

## StarKid-Genitori-1.0.1.apk

L'app di gestione per il telefono del genitore: la stessa cosa che fa la web UI,
ma a portata di tasca. Elenco dei tablet, configurazione, storico delle
revisioni, blocco e sblocco a distanza, QR di arruolamento.

| | |
|---|---|
| pacchetto | `it.iotatec.starkid.genitore` |
| versione | 1.0.1 (versionCode 1) |
| Android minimo | 8.0 (API 26) |
| firma | `CN=StarKid, O=iotatec, C=IT` |
| SHA-256 | `89c9079434625bbbd4d70327807d9184d7e122210291de298462c6917781f9d5` |

Prima di installarla, su Android serve dare il permesso "installa app sconosciute"
all'app da cui la si apre (di solito il browser o il gestore file). Chiede
l'indirizzo del server e le credenziali: le stesse della web UI.

**L'impronta della firma conta.** Se un aggiornamento futuro arriva con una SHA-256
diversa, Android rifiuta di installarlo sopra: vuol dire che non l'ha costruito
chi ha la keystore, e non va installato.

## Come si rifanno

Dalla radice del progetto, con `keystore.properties` e `starkid-release.jks` al
loro posto (nessuno dei due sta in git):

    ./gradlew :genitore:assembleRelease
    cp genitore/build/outputs/apk/release/genitore-release.apk dist/StarKid-Genitori-<versione>.apk

Il nome del file porta la versione: le vecchie non si cancellano, cosi un tablet
rimasto indietro trova ancora la sua.

Senza la keystore il build riesce lo stesso, ma l'APK esce **non firmata** e non
si installa: va bene per provare, non per questa cartella.
