/* StarKidQR - generatore di codici QR, in JavaScript semplice.
 *
 * Scritto per StarKid, parte dello stesso progetto e sotto la stessa licenza.
 * Non e una libreria copiata da fuori: e un'implementazione della norma
 * ISO/IEC 18004 fatta qui, per due motivi pratici.
 *
 *   1. Il QR di arruolamento si genera NEL BROWSER, perche dentro ci sono il
 *      nome del bambino e la password del Wi-Fi: cose che il server non deve
 *      vedere mai. Quindi il generatore deve stare in questa pagina.
 *   2. Sul server non c'e node e non si installa niente: nessun bundler,
 *      nessuna CDN. Un file, servito da nginx come tutti gli altri.
 *
 * Cosa fa e cosa non fa, detto chiaro:
 *   - modalita byte (UTF-8), con ECI 26 messo davanti solo se c'e qualche
 *     carattere fuori dall'ASCII (per esempio una "o" accentata in un nome);
 *   - livelli di correzione L e M. Q e H non ci sono: un QR di arruolamento
 *     si inquadra una volta da uno schermo pulito, e ogni livello in piu
 *     ruba spazio ai dati;
 *   - versioni da 1 a 20, cioe fino a 97x97 moduli. Il limite non e pigrizia:
 *     e il punto oltre il quale un tablet, con la fotocamera a mezzo metro da
 *     un monitor, comincia a non leggere piu. Il massimo utile e 858 byte a
 *     livello L. Se il testo non ci sta, codifica() lo dice con un messaggio
 *     che spiega cosa togliere, invece di produrre un QR che non si legge.
 *
 * Le due tabelle qui sotto sono gli unici dati presi dalla norma; tutto il
 * resto (numero di moduli, posizioni degli allineamenti, informazioni di
 * formato e di versione) e calcolato. Sono state ricontrollate confrontando
 * la capacita che se ne ricava, versione per versione e livello per livello,
 * con la tabella delle capacita in byte pubblicata nella norma.
 */

'use strict';

var StarKidQR = (function () {

  var MAX_VERSIONE = 20;

  /* Codeword di correzione per ogni blocco, versioni 1..20. */
  var EC_PER_BLOCCO = {
    L: [7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28],
    M: [10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26]
  };

  /* Numero di blocchi di correzione, versioni 1..20. */
  var BLOCCHI = {
    L: [1, 1, 1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4, 4, 6, 6, 6, 6, 7, 8],
    M: [1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16]
  };

  /* I due bit che identificano il livello nelle informazioni di formato. */
  var BIT_LIVELLO = { L: 1, M: 0 };

  var PAD = [0xEC, 0x11];

  /* ------------------------------------------------------------ geometria */

  function dimensione(versione) { return versione * 4 + 17; }

  /* Moduli disponibili per i dati, cioe tutti meno quelli di servizio
     (mirini, separatori, temporizzazione, allineamenti, formato, versione e
     il modulo scuro). Calcolato invece che tabellato: una tabella in meno da
     sbagliare. */
  function moduliGrezzi(versione) {
    var lato = dimensione(versione);
    var totale = lato * lato;
    totale -= 8 * 8 * 3;              /* tre mirini con i loro separatori */
    totale -= 15 * 2 + 1;             /* informazioni di formato + modulo scuro */
    totale -= (lato - 16) * 2;        /* le due righe di temporizzazione */
    if (versione >= 2) {
      var quanti = Math.floor(versione / 7) + 2;
      totale -= (quanti - 1) * (quanti - 1) * 25;   /* allineamenti liberi */
      totale -= (quanti - 2) * 2 * 20;              /* quelli sulla temporizzazione */
      if (versione >= 7) totale -= 6 * 3 * 2;       /* informazioni di versione */
    }
    return totale;
  }

  function codewordGrezzi(versione) { return Math.floor(moduliGrezzi(versione) / 8); }

  function codewordDati(versione, livello) {
    return codewordGrezzi(versione) - EC_PER_BLOCCO[livello][versione - 1] * BLOCCHI[livello][versione - 1];
  }

  /* Byte di testo che ci stanno, tolto l'inquadramento (modalita, lunghezza
     ed eventuale ECI). */
  function capacitaByte(versione, livello, eci) {
    var bit = codewordDati(versione, livello) * 8 - inquadramento(versione, eci);
    return Math.max(0, Math.floor(bit / 8));
  }

  function inquadramento(versione, eci) {
    return (eci ? 12 : 0) + 4 + (versione < 10 ? 8 : 16);
  }

  function capacitaMassima(livello, eci) {
    return capacitaByte(MAX_VERSIONE, livello || 'L', !!eci);
  }

  /* Centri degli allineamenti: 6, poi l'ultimo a lato-7, poi indietro a passo
     fisso. La formula sta nella norma (e il caso della versione 32 e
     l'unico fuori regola). */
  function posizioniAllineamento(versione) {
    if (versione === 1) return [];
    var quanti = Math.floor(versione / 7) + 2;
    var passo = (versione === 32) ? 26 : Math.ceil((versione * 4 + 4) / (quanti * 2 - 2)) * 2;
    var posizioni = [6];
    for (var p = dimensione(versione) - 7; posizioni.length < quanti; p -= passo) posizioni.splice(1, 0, p);
    return posizioni;
  }

  /* ---------------------------------------------- aritmetica di Galois */

  /* Prodotto nel campo GF(256) della norma (polinomio 0x11D). Senza tabelle:
     otto passi di raddoppio con riduzione, come una moltiplicazione in
     colonna. */
  function moltiplica(x, y) {
    var z = 0;
    for (var i = 7; i >= 0; i--) {
      z = (z << 1) ^ ((z >>> 7) * 0x11D);
      z ^= ((y >>> i) & 1) * x;
    }
    return z & 0xFF;
  }

  function polinomioGeneratore(grado) {
    var risultato = new Uint8Array(grado);
    risultato[grado - 1] = 1;
    var radice = 1;
    for (var i = 0; i < grado; i++) {
      for (var j = 0; j < grado; j++) {
        risultato[j] = moltiplica(risultato[j], radice);
        if (j + 1 < grado) risultato[j] ^= risultato[j + 1];
      }
      radice = moltiplica(radice, 0x02);
    }
    return risultato;
  }

  /* Resto della divisione: sono i codeword di correzione del blocco. */
  function restoRS(dati, generatore) {
    var resto = new Uint8Array(generatore.length);
    for (var i = 0; i < dati.length; i++) {
      var fattore = dati[i] ^ resto[0];
      resto.copyWithin(0, 1);
      resto[resto.length - 1] = 0;
      for (var j = 0; j < generatore.length; j++) resto[j] ^= moltiplica(generatore[j], fattore);
    }
    return resto;
  }

  /* --------------------------------------------------------------- bit */

  function Flusso() { this.bit = []; }
  Flusso.prototype.aggiungi = function (valore, quantita) {
    for (var i = quantita - 1; i >= 0; i--) this.bit.push((valore >>> i) & 1);
  };

  function bitDi(valore, posizione) { return (valore >>> posizione) & 1; }

  /* ---------------------------------------------------------- codifica */

  function byteUtf8(testo) {
    if (typeof TextEncoder === 'function') return new TextEncoder().encode(String(testo));
    /* Ripiego per browser molto vecchi. */
    var s = unescape(encodeURIComponent(String(testo)));
    var out = new Uint8Array(s.length);
    for (var i = 0; i < s.length; i++) out[i] = s.charCodeAt(i) & 0xFF;
    return out;
  }

  function scegliVersione(quantiByte, livello, eci) {
    for (var v = 1; v <= MAX_VERSIONE; v++) {
      if (quantiByte <= capacitaByte(v, livello, eci)) return v;
    }
    return null;
  }

  /**
   * Costruisce il codice. Restituisce
   *   { versione, livello, dimensione, maschera, moduli, byte, scuro(x, y) }
   * dove moduli e un Uint8Array di dimensione*dimensione valori 0/1,
   * riga per riga.
   */
  function codifica(testo, opzioni) {
    opzioni = opzioni || {};
    var livello = (opzioni.livello === 'L' || opzioni.livello === 'M') ? opzioni.livello : 'M';
    var dati = byteUtf8(testo);
    if (dati.length === 0) throw new Error('Non c\'e niente da mettere nel QR.');

    /* ECI 26 = UTF-8. Si mette solo se serve davvero: senza caratteri
       accentati un lettore vecchio non deve nemmeno incontrarlo. */
    var eci = false;
    for (var i = 0; i < dati.length; i++) { if (dati[i] > 0x7F) { eci = true; break; } }

    var versione = scegliVersione(dati.length, livello, eci);
    if (versione === null) {
      var massimo = capacitaByte(MAX_VERSIONE, livello, eci);
      throw new Error(
        'Il testo e troppo lungo per un QR leggibile: ' + dati.length + ' byte, il massimo e ' + massimo +
        '. Accorcia: togli la rete Wi-Fi dal QR (la si collega a mano sul tablet), usa un indirizzo del server piu corto, ' +
        'oppure lascia fuori il collegamento di scaricamento dell\'APK e installalo con adb.'
      );
    }

    var flusso = new Flusso();
    if (eci) { flusso.aggiungi(7, 4); flusso.aggiungi(26, 8); }
    flusso.aggiungi(4, 4);                                    /* modalita byte */
    flusso.aggiungi(dati.length, versione < 10 ? 8 : 16);     /* quanti byte */
    for (i = 0; i < dati.length; i++) flusso.aggiungi(dati[i], 8);

    var bitDisponibili = codewordDati(versione, livello) * 8;
    flusso.aggiungi(0, Math.min(4, bitDisponibili - flusso.bit.length));   /* chiusura */
    flusso.aggiungi(0, (8 - flusso.bit.length % 8) % 8);                   /* al byte */

    var codeword = new Uint8Array(codewordDati(versione, livello));
    for (i = 0; i < flusso.bit.length; i++) {
      codeword[i >>> 3] |= flusso.bit[i] << (7 - (i & 7));
    }
    /* Riempimento: 11101100 00010001 alternati fino alla fine, come dice la
       norma (sono due byte scelti perche non somigliano a niente d'altro). */
    var riempimenti = 0;
    for (i = Math.ceil(flusso.bit.length / 8); i < codeword.length; i++) {
      codeword[i] = PAD[riempimenti++ % 2];
    }

    var finale = intreccia(codeword, versione, livello);
    var qr = disegna(versione, livello, finale, opzioni.maschera);
    qr.byte = dati.length;
    return qr;
  }

  /* I blocchi si alternano codeword per codeword: cosi una macchia sul
     codice danneggia un pezzo di ogni blocco invece di distruggerne uno. */
  function intreccia(codeword, versione, livello) {
    var quantiBlocchi = BLOCCHI[livello][versione - 1];
    var ecLunghezza = EC_PER_BLOCCO[livello][versione - 1];
    var totaleGrezzi = codewordGrezzi(versione);
    var corti = Math.floor(codeword.length / quantiBlocchi);
    var quantiCorti = quantiBlocchi - (codeword.length % quantiBlocchi);
    var generatore = polinomioGeneratore(ecLunghezza);

    var blocchi = [];
    var inizio = 0;
    for (var b = 0; b < quantiBlocchi; b++) {
      var lunghezza = corti + (b < quantiCorti ? 0 : 1);
      var dati = codeword.subarray(inizio, inizio + lunghezza);
      inizio += lunghezza;
      blocchi.push({ dati: dati, ec: restoRS(dati, generatore) });
    }

    var risultato = new Uint8Array(totaleGrezzi);
    var scritti = 0;
    for (var i = 0; i < corti + 1; i++) {
      for (b = 0; b < quantiBlocchi; b++) {
        if (i < blocchi[b].dati.length) risultato[scritti++] = blocchi[b].dati[i];
      }
    }
    for (i = 0; i < ecLunghezza; i++) {
      for (b = 0; b < quantiBlocchi; b++) risultato[scritti++] = blocchi[b].ec[i];
    }
    return risultato;
  }

  /* --------------------------------------------------------- disegno */

  function disegna(versione, livello, codeword, mascheraForzata) {
    var lato = dimensione(versione);
    var moduli = new Uint8Array(lato * lato);
    var servizio = new Uint8Array(lato * lato);

    function metti(x, y, scuro) {
      if (x < 0 || y < 0 || x >= lato || y >= lato) return;
      moduli[y * lato + x] = scuro ? 1 : 0;
    }
    function mettiServizio(x, y, scuro) {
      if (x < 0 || y < 0 || x >= lato || y >= lato) return;
      moduli[y * lato + x] = scuro ? 1 : 0;
      servizio[y * lato + x] = 1;
    }
    function diServizio(x, y) { return servizio[y * lato + x] === 1; }

    /* Mirini (7x7) con i separatori chiari intorno. */
    function mirino(cx, cy) {
      for (var dy = -4; dy <= 4; dy++) {
        for (var dx = -4; dx <= 4; dx++) {
          var distanza = Math.max(Math.abs(dx), Math.abs(dy));
          mettiServizio(cx + dx, cy + dy, distanza !== 2 && distanza !== 4);
        }
      }
    }

    /* Temporizzazione: la riga e la colonna 6, scuro-chiaro alternati. */
    for (var i = 0; i < lato; i++) {
      mettiServizio(6, i, i % 2 === 0);
      mettiServizio(i, 6, i % 2 === 0);
    }
    mirino(3, 3);
    mirino(lato - 4, 3);
    mirino(3, lato - 4);

    /* Allineamenti (5x5), tranne dove ci sono i mirini. */
    var posizioni = posizioniAllineamento(versione);
    for (i = 0; i < posizioni.length; i++) {
      for (var j = 0; j < posizioni.length; j++) {
        var agli = (i === 0 && j === 0) ||
          (i === 0 && j === posizioni.length - 1) ||
          (i === posizioni.length - 1 && j === 0);
        if (agli) continue;
        for (var dy2 = -2; dy2 <= 2; dy2++) {
          for (var dx2 = -2; dx2 <= 2; dx2++) {
            mettiServizio(posizioni[i] + dx2, posizioni[j] + dy2,
              Math.max(Math.abs(dx2), Math.abs(dy2)) !== 1);
          }
        }
      }
    }

    /* Posti riservati al formato: il contenuto arriva dopo, quando si sa
       quale maschera ha vinto. */
    riservaFormato(mettiServizio, lato);

    /* Informazioni di versione, dalla 7 in su: BCH(18,6) con 0x1F25. */
    if (versione >= 7) {
      var resto = versione;
      for (i = 0; i < 12; i++) resto = (resto << 1) ^ ((resto >>> 11) * 0x1F25);
      var bitVersione = (versione << 12) | resto;
      for (i = 0; i < 18; i++) {
        var scuro = bitDi(bitVersione, i) === 1;
        var a = lato - 11 + i % 3;
        var b = Math.floor(i / 3);
        mettiServizio(a, b, scuro);
        mettiServizio(b, a, scuro);
      }
    }

    /* I dati: due colonne per volta, dal basso a destra, serpeggiando. */
    var indice = 0;
    var totaleBit = codeword.length * 8;
    for (var destra = lato - 1; destra >= 1; destra -= 2) {
      if (destra === 6) destra = 5;                 /* la colonna 6 e temporizzazione */
      for (var verticale = 0; verticale < lato; verticale++) {
        for (var k = 0; k < 2; k++) {
          var x = destra - k;
          var versoSu = ((destra + 1) & 2) === 0;
          var y = versoSu ? lato - 1 - verticale : verticale;
          if (!diServizio(x, y) && indice < totaleBit) {
            metti(x, y, bitDi(codeword[indice >>> 3], 7 - (indice & 7)) === 1);
            indice++;
          }
          /* I moduli rimasti (i "bit di riempimento" della norma) restano
             chiari: e esattamente quello che vuole la norma. */
        }
      }
    }

    /* Maschera: si provano tutte e otto e vince quella con meno penalita,
       cosi il codice non viene fuori a strisce o con finti mirini dentro. */
    var migliore = -1;
    var migliorePena = Infinity;
    var copia = moduli.slice();
    for (var m = 0; m < 8; m++) {
      if (mascheraForzata !== undefined && mascheraForzata !== null && m !== mascheraForzata) continue;
      moduli.set(copia);
      applicaMaschera(moduli, servizio, lato, m);
      scriviFormato(moduli, servizio, lato, livello, m);
      var pena = penalita(moduli, lato);
      if (pena < migliorePena) { migliorePena = pena; migliore = m; }
    }
    moduli.set(copia);
    applicaMaschera(moduli, servizio, lato, migliore);
    scriviFormato(moduli, servizio, lato, livello, migliore);

    return {
      versione: versione,
      livello: livello,
      dimensione: lato,
      maschera: migliore,
      moduli: moduli,
      scuro: function (x, y) {
        if (x < 0 || y < 0 || x >= lato || y >= lato) return false;
        return moduli[y * lato + x] === 1;
      }
    };
  }

  function riservaFormato(mettiServizio, lato) {
    for (var i = 0; i <= 8; i++) {
      if (i !== 6) { mettiServizio(8, i, false); mettiServizio(i, 8, false); }
    }
    mettiServizio(8, 8, false);
    for (i = 0; i < 8; i++) {
      mettiServizio(lato - 1 - i, 8, false);
      mettiServizio(8, lato - 1 - i, false);
    }
    mettiServizio(8, lato - 8, true);     /* il modulo scuro, sempre scuro */
  }

  /* Informazioni di formato: 5 bit di dati (livello + maschera) con 10 bit
     BCH(15,5) calcolati con 0x537, il tutto in XOR con 0x5412. */
  function scriviFormato(moduli, servizio, lato, livello, maschera) {
    var dati = (BIT_LIVELLO[livello] << 3) | maschera;
    var resto = dati;
    for (var i = 0; i < 10; i++) resto = (resto << 1) ^ ((resto >>> 9) * 0x537);
    var bit = ((dati << 10) | resto) ^ 0x5412;

    function metti(x, y, valore) {
      moduli[y * lato + x] = valore;
      servizio[y * lato + x] = 1;
    }

    for (i = 0; i <= 5; i++) metti(8, i, bitDi(bit, i));
    metti(8, 7, bitDi(bit, 6));
    metti(8, 8, bitDi(bit, 7));
    metti(7, 8, bitDi(bit, 8));
    for (i = 9; i < 15; i++) metti(14 - i, 8, bitDi(bit, i));

    for (i = 0; i < 8; i++) metti(lato - 1 - i, 8, bitDi(bit, i));
    for (i = 8; i < 15; i++) metti(8, lato - 15 + i, bitDi(bit, i));
    metti(8, lato - 8, 1);
  }

  var MASCHERE = [
    function (x, y) { return (x + y) % 2 === 0; },
    function (x, y) { return y % 2 === 0; },
    function (x, y) { return x % 3 === 0; },
    function (x, y) { return (x + y) % 3 === 0; },
    function (x, y) { return (Math.floor(y / 2) + Math.floor(x / 3)) % 2 === 0; },
    function (x, y) { return (x * y) % 2 + (x * y) % 3 === 0; },
    function (x, y) { return ((x * y) % 2 + (x * y) % 3) % 2 === 0; },
    function (x, y) { return ((x + y) % 2 + (x * y) % 3) % 2 === 0; }
  ];

  function applicaMaschera(moduli, servizio, lato, maschera) {
    var regola = MASCHERE[maschera];
    for (var y = 0; y < lato; y++) {
      for (var x = 0; x < lato; x++) {
        if (servizio[y * lato + x]) continue;
        if (regola(x, y)) moduli[y * lato + x] ^= 1;
      }
    }
  }

  /* Le quattro penalita della norma. Servono solo a scegliere la maschera. */
  function penalita(moduli, lato) {
    var totale = 0;
    function m(x, y) { return moduli[y * lato + x]; }

    /* 1 - file di cinque o piu moduli dello stesso colore.
       3 - uno schema 1:1:3:1:1 con quattro moduli chiari a lato: somiglia a
           un mirino e inganna il lettore. Si cercano le due sequenze di
           undici moduli che la norma indica.
       Si cercano solo le finestre di undici moduli interamente dentro il
       codice: la norma conta anche quelle che sconfinano oltre il bordo, ma
       questo influisce soltanto su quale maschera vince, non sulla
       leggibilita del codice che ne esce. */
    var schemaA = [1, 0, 1, 1, 1, 0, 1, 0, 0, 0, 0];
    var schemaB = [0, 0, 0, 0, 1, 0, 1, 1, 1, 0, 1];

    function fila(leggi) {
      var pena = 0;
      var precedente = -1;
      var quanti = 0;
      var ultimi = [];
      for (var i = 0; i < lato; i++) {
        var valore = leggi(i);
        if (valore === precedente) {
          quanti++;
          if (quanti === 5) pena += 3;
          else if (quanti > 5) pena += 1;
        } else {
          precedente = valore;
          quanti = 1;
        }
        ultimi.push(valore);
        if (ultimi.length > 11) ultimi.shift();
        if (ultimi.length === 11) {
          var ugualeA = true, ugualeB = true;
          for (var j = 0; j < 11; j++) {
            if (ultimi[j] !== schemaA[j]) ugualeA = false;
            if (ultimi[j] !== schemaB[j]) ugualeB = false;
          }
          if (ugualeA) pena += 40;
          if (ugualeB) pena += 40;
        }
      }
      return pena;
    }

    for (var y = 0; y < lato; y++) {
      totale += fila(function (x) { return m(x, y); });
    }
    for (var x = 0; x < lato; x++) {
      totale += fila(function (y2) { return m(x, y2); });
    }

    /* 2 - quadrati 2x2 di un solo colore. */
    for (y = 0; y < lato - 1; y++) {
      for (x = 0; x < lato - 1; x++) {
        var v = m(x, y);
        if (v === m(x + 1, y) && v === m(x, y + 1) && v === m(x + 1, y + 1)) totale += 3;
      }
    }

    /* 4 - troppo scuro o troppo chiaro nel complesso. */
    var scuri = 0;
    for (var i = 0; i < moduli.length; i++) scuri += moduli[i];
    var percento = scuri * 100 / (lato * lato);
    totale += Math.floor(Math.abs(percento - 50) / 5) * 10;
    return totale;
  }

  /* ----------------------------------------------------------- uscita */

  /* SVG costruito con il DOM, non con una stringa di marcatura: cosi non
     serve innerHTML da nessuna parte. Un solo <path>: migliaia di <rect>
     fanno faticare i telefoni. */
  function aSvg(qr, opzioni) {
    opzioni = opzioni || {};
    var bordo = opzioni.bordo === undefined ? 4 : Math.max(0, opzioni.bordo);
    var lato = qr.dimensione + bordo * 2;
    var ns = 'http://www.w3.org/2000/svg';

    var svg = document.createElementNS(ns, 'svg');
    svg.setAttribute('xmlns', ns);
    svg.setAttribute('viewBox', '0 0 ' + lato + ' ' + lato);
    svg.setAttribute('shape-rendering', 'crispEdges');
    svg.setAttribute('role', 'img');
    svg.setAttribute('aria-label', opzioni.descrizione || 'Codice QR di arruolamento');

    var fondo = document.createElementNS(ns, 'rect');
    fondo.setAttribute('width', String(lato));
    fondo.setAttribute('height', String(lato));
    fondo.setAttribute('fill', opzioni.chiaro || '#ffffff');
    svg.appendChild(fondo);

    var pezzi = [];
    for (var y = 0; y < qr.dimensione; y++) {
      for (var x = 0; x < qr.dimensione; x++) {
        if (qr.moduli[y * qr.dimensione + x]) {
          pezzi.push('M' + (x + bordo) + ',' + (y + bordo) + 'h1v1h-1z');
        }
      }
    }
    var tracciato = document.createElementNS(ns, 'path');
    tracciato.setAttribute('d', pezzi.join(''));
    tracciato.setAttribute('fill', opzioni.scuro || '#000000');
    svg.appendChild(tracciato);
    return svg;
  }

  /* PNG, per chi vuole il file da stampare o da mandare. Passa da un canvas,
     quindi non esce dal browser. */
  function aPngBlob(qr, opzioni) {
    opzioni = opzioni || {};
    var scala = Math.max(1, Math.round(opzioni.scala || 8));
    var bordo = opzioni.bordo === undefined ? 4 : Math.max(0, opzioni.bordo);
    var lato = (qr.dimensione + bordo * 2) * scala;
    var tela = document.createElement('canvas');
    tela.width = lato;
    tela.height = lato;
    var pennello = tela.getContext('2d');
    pennello.fillStyle = '#ffffff';
    pennello.fillRect(0, 0, lato, lato);
    pennello.fillStyle = '#000000';
    for (var y = 0; y < qr.dimensione; y++) {
      for (var x = 0; x < qr.dimensione; x++) {
        if (qr.moduli[y * qr.dimensione + x]) {
          pennello.fillRect((x + bordo) * scala, (y + bordo) * scala, scala, scala);
        }
      }
    }
    return new Promise(function (risolvi, rifiuta) {
      if (!tela.toBlob) { rifiuta(new Error('Questo browser non sa salvare il PNG: usa il tasto di stampa.')); return; }
      tela.toBlob(function (blob) {
        if (blob) risolvi(blob); else rifiuta(new Error('Non e riuscito a creare il PNG.'));
      }, 'image/png');
    });
  }

  return {
    codifica: codifica,
    aSvg: aSvg,
    aPngBlob: aPngBlob,
    capacitaByte: capacitaByte,
    capacitaMassima: capacitaMassima,
    MAX_VERSIONE: MAX_VERSIONE,
    /* Esposti per il collaudo (vedi il file di prova nel repository):
       permettono di rileggere un codice e confrontarlo con l'originale. */
    _interni: {
      EC_PER_BLOCCO: EC_PER_BLOCCO,
      BLOCCHI: BLOCCHI,
      BIT_LIVELLO: BIT_LIVELLO,
      dimensione: dimensione,
      moduliGrezzi: moduliGrezzi,
      codewordGrezzi: codewordGrezzi,
      codewordDati: codewordDati,
      posizioniAllineamento: posizioniAllineamento,
      moltiplica: moltiplica,
      polinomioGeneratore: polinomioGeneratore,
      restoRS: restoRS,
      MASCHERE: MASCHERE,
      penalita: penalita
    }
  };
})();
