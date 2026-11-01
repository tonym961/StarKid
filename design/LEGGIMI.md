# Il marchio StarKid

Qui dentro non ci sono SVG disegnati a mano: ci sono tre script che li
scrivono. Per rifare tutti i file del marchio:

```bash
./design/genera.sh
```

## Perche generato e non disegnato

Il marchio di prima era questo path:

```
M16 5.5l3.2 6.9 7.3.9-5.4 5.1 1.4 7.4L16 22.2l-6.5 3.6 1.4-7.4-5.4-5.1 7.3-.9z
```

Dieci vertici messi a occhio, con le punte non tutte uguali e gli angoli vivi,
ricopiati in quattro posti con quattro misure diverse: la favicon, la testata
del sito, le stelline dei giudizi e l'icona Android erano quattro stelle
imparentate alla lontana. A 16px nessuno se ne accorgeva; a 108dp sull'icona
dell'app si vedeva benissimo.

Il nome era scritto in **Verdana grassetto**. Verdana e un carattere pensato
per leggere i testi piccoli sugli schermi del 1996, e soprattutto non c'e su
Android: li scattava il ripiego e il nome del prodotto veniva disegnato da un
altro carattere. Un marchio che cambia disegno a seconda del dispositivo non e
un marchio.

## I tre script

| file | cosa fa |
| --- | --- |
| `stella.sh` | calcola il path della stella: cinque punte a 72 gradi esatti, arrotondate con una quadratica per vertice |
| `parola.sh` | costruisce le sette lettere di "StarKid" con righe, cerchi e archi, tutti dello stesso spessore |
| `genera.sh` | mette insieme i due e scrive gli otto file veri |

Nessuno dei tre va modificato a valle: si cambia un numero qui e si rigenera
tutto insieme, altrimenti fra sei mesi le stelle tornano a essere quattro.

## Cosa esce

| file | dove si usa |
| --- | --- |
| `server/web/stella.svg` | la stella col volto: titolo della pagina di accesso |
| `server/web/parola.svg` | il nome, bianco e oro |
| `server/web/marchio.svg` | stella + nome, la testata delle cinque pagine |
| `server/web/icona.svg` | la favicon |
| `docs/logo.svg` | lo striscione del README |
| `app/.../ic_launcher_foreground.xml` | icona adattiva, davanti |
| `app/.../ic_launcher_background.xml` | icona adattiva, il cielo dietro |
| `app/.../ic_launcher_monochrome.xml` | icona a tinta unita, Android 13 e oltre |
| `design/out/maschere.css` | le due data URI delle stelline da incollare in `app.css` |

`design/out/android-*.svg` sono gli stessi due strati dell'icona Android in
SVG: servono solo a guardarli senza compilare la APK, ed escono dalle stesse
variabili, quindi non possono divergere.

## Le scelte, e perche

**La stella: `ratio 0.46`, `dOut 0.30`, `dIn 0.22`.** Scelte guardando sedici
varianti affiancate a 16, 20, 24, 32 e 160 px, non a naso. Piu magra (0.40) le
punte si sfilacciano a 16px; piu grassa (0.55) smette di essere una stella e
diventa un cuscino. Arrotondando di piu (0.46) le punte spariscono.

**Il volto c'e solo da 26px in su.** Nella favicon a 16px due occhi da mezzo
pixel non sono un volto, sono sporco: `icona.svg` ha solo la sagoma.

**La "S" ha i terminali tagliati a -5 gradi.** C'e una finestra stretta: a +35
l'arco gira cosi tanto che si chiude e la lettera si legge "9"; a -45 e oltre
il controforma di sopra si apre e si legge "б". Si rivede con
`./design/parola.sh svg -45`.

**La stella del marchio e quella delle stelline dei giudizi sono la stessa
geometria, ma non lo stesso oggetto**: il marchio ha sfumatura e volto, le
icone sono piatte e in `currentColor`. Sono un sistema, non una collisione -
ed e proprio l'essere identiche, prima, a far sembrare il logo un'icona
qualsiasi.

**Le stelline dello sfondo dell'icona Android** stanno nei cinque vuoti fra le
punte, a 30dp dal centro: li non toccano il disegno e restano dentro il
cerchio da 36dp del ritaglio tondo, che e il piu stretto dei tre che usano i
lanciatori. Il lanciatore mostra solo i 72dp centrali dei 108: tutto quello
che sta fuori puo sparire, e infatti fuori c'e solo cielo.

## Il controllo

`server/tests/test_revisione_sicurezza.py` ha tre test che tengono insieme il
sistema: che tutti i file portino la stessa stella carattere per carattere,
che dicano di essere generati, e che il nome del prodotto non sia tornato a
essere testo. Il primo fallisce appena qualcuno ritocca un SVG a mano.
