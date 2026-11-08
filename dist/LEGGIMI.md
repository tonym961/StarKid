# APK pronte all'uso

Qui stanno le build firmate che si installano senza compilare niente. Ci sono
perche l'app dei genitori non passa dal Play Store: la si scarica da qui, la si
mette sul telefono e si apre.

## StarKid-Genitori-1.0.4.apk

Il **blocco del tablet si fa dall'elenco**, senza aprire la scheda: un bottone
per tablet, che dice "Chiudi il tablet adesso" o "Riapri" e parte subito.
Nella scheda il blocco e salito in cima, sempre con un bottone che non
aspetta il salvataggio in fondo.

| | |
|---|---|
| pacchetto | `it.iotatec.starkid.genitore` |
| versione | 1.0.4 (versionCode 4) |
| Android minimo | 8.0 (API 26) |
| firma | `CN=StarKid, O=iotatec, C=IT` |
| SHA-256 | `173834d3ddf6c7c98032bcd0b47b06a13f1cdce37e227339022f81dbd89252f4` |

Dalla 1.0.3 l'app si aggiorna da sola, quindi questa arriva senza che tu
debba scaricare niente - **a patto di avere gia la 1.0.3 o piu**. Chi ha la
1.0.2 o meno deve installarla a mano un'ultima volta.

## StarKid-Genitori-1.0.3.apk

Dalla 1.0.3 l'app **si aggiorna da sola**: guarda in questa cartella e nelle
release del repository, e quando trova una versione piu alta la propone. Non
installa niente di nascosto - apre l'installatore di Android, che chiede - e
prima controlla che l'APK sia firmato con la stessa chiave di quella in
esecuzione. Quindi questa e l'ultima volta che serve scaricarla a mano.

Nuovo anche il **blocco a distanza in cima alla scheda del tablet**, con un
bottone che agisce subito senza passare dal salvataggio in fondo.

| | |
|---|---|
| pacchetto | `it.iotatec.starkid.genitore` |
| versione | 1.0.3 (versionCode 3) |
| Android minimo | 8.0 (API 26) |
| firma | `CN=StarKid, O=iotatec, C=IT` |
| SHA-256 | `40adbd514ff619472294e11c9bde6ad368359f9c4e87edb3bdcbccf799155e3b` |

**Come si pubblica una versione nuova.** Basta mettere l'APK qui dentro col
nome `StarKid-Genitori-<versione>.apk` e fare push: l'ultimo numero della
versione e il versionCode, ed e quello che le app installate confrontano. In
alternativa si allega lo stesso file a una release, che l'app guarda
ugualmente. Il nome del file conta: fuori da quello schema non viene visto.

## StarKid-Genitori-1.0.2.apk

L'app di gestione per il telefono del genitore: la stessa cosa che fa la web UI,
ma a portata di tasca. Elenco dei tablet, configurazione, storico delle
revisioni, blocco e sblocco a distanza, QR di arruolamento.

Dalla 1.0.2 cambia il **PIN dell'area genitori** del tablet: le cifre si
battono qui e non escono dal telefono, al server va la loro impronta. Serve
quando il PIN si e dimenticato, cioe quando si ha in mano il telefono e non il
tablet.

| | |
|---|---|
| versione | 1.0.2 (versionCode 2) |
| SHA-256 | `33560a3ce09fadfaa2c64dba60ee4c279dd4dfeb6dac5bb920aa904f9590e149` |

## StarKid-Genitori-1.0.1.apk

La precedente, lasciata qui apposta: le vecchie non si cancellano. Non sa
cambiare il PIN.

| | |
|---|---|
| versione | 1.0.1 (versionCode 1) |
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
