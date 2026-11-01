/* StarKid - web UI del genitore: pezzi comuni a tutte le pagine.
 *
 * Caricato per primo da ogni pagina; dopo di lui viene il file della singola
 * pagina (pagina-*.js), che trova qui dentro tutto quello che serve
 * nell'oggetto globale SK.
 *
 * Tre regole che valgono in tutto il front-end:
 *
 *  1. Niente innerHTML con roba che arriva dal server. Il nome di un tablet
 *     e testo scritto da una persona: si mette con textContent. Il QR e la
 *     CSP delle pagine servono a poco se poi si incolla HTML altrui.
 *  2. Lo schema della configurazione e CHIUSO anche qui: si manda solo
 *     quello che l'API dichiara. Il server rifiuta con 422 i campi che non
 *     conosce, e questo codice non prova nemmeno a inventarne.
 *  3. Niente token in localStorage. La sessione e il cookie, e il cookie lo
 *     gestisce il server; qui in locale si tiene solo qualche comodita
 *     (l'ultimo tablet aperto), roba che si puo perdere senza danno.
 *
 * ---------------------------------------------------------------------------
 * Forma delle risposte che questa UI si aspetta (lato server, API v1).
 * Dove il contratto dell'API non la fissa, i lettori qui sotto accettano
 * qualche sinonimo (vedi normalizzaTablet) invece di rompersi.
 *
 * GET /api/v1/tablets
 *   {"tablets": [
 *      {"id": "ab12...", "nome": "tablet blu",
 *       "revisione": 7,                  // revisione pubblicata dal server
 *       "revisione_confermata": 6,       // ultima confermata dal tablet
 *       "ultimo_esito": "applied|rejected|partial|null",
 *       "campi_rifiutati": [{"campo": "kiosk", "motivo": "serve_device_owner"}],
 *       "visto_il": 1762000000,          // secondi, non ISO
 *       "arruolato": true,
 *       // "dichiarata" per davvero: sono le spie che il TABLET dice di se,
 *       // e il server non ha modo di verificarle. La pagina deve scriverlo.
 *       "salute_dichiarata": {"device_owner": true, "usage_access": false,
 *                             "overlay": true, "guard_running": true,
 *                             "default_home": true},
 *       "salute_il": 1762000000,
 *       "kiosk": "attivo|in attesa|spento|sconosciuto"}]}
 *
 * POST /api/v1/tablets          -> {"id": "...", "nome": "...",
 *                                   "token_arruolamento": "..."}
 * GET  /api/v1/tablets/{id}/config  -> il documento di configurazione
 * PUT  /api/v1/tablets/{id}/config  -> il documento con la revisione nuova
 * GET  /api/v1/tablets/{id}/history -> {"revisioni": [{"revisione": 7,
 *                                        "quando": "...", "configurazione": {...}}]}
 * GET  /api/v1/salute (sessione)     -> {"certificato_giorni": 61,
 *                                        "ultimo_backup": 1762000000 | null,
 *                                        "database_byte": 73728,
 *                                        "versione": "v1", "da": 1762000000}
 *                                       "certificato_giorni" manca quando il
 *                                       servizio non puo leggere il
 *                                       certificato, cosa probabile: gira con
 *                                       ProtectSystem=strict. salute.html in
 *                                       quel caso scrive che il server non lo
 *                                       dice, invece di inventare un numero.
 * -------------------------------------------------------------------------*/

'use strict';

var SK = (function () {

  /* ------------------------------------------------------------- costanti */

  var TEMI = [
    { id: 'pulito', nome: 'Pulito', aiuto: 'Tinte chiare, nessuna decorazione.' },
    { id: 'spaziale', nome: 'Spaziale', aiuto: 'Cielo stellato, pianeti e razzi.' }
  ];

  /* Ogni sfondo appartiene a un tema, come sul tablet. La foto non c'e:
     resta sul tablet e non passa da qui. */
  var SFONDI = {
    pulito: [
      { id: 'lilla', nome: 'Lilla' },
      { id: 'cielo', nome: 'Cielo' },
      { id: 'prato', nome: 'Prato' },
      { id: 'tramonto', nome: 'Tramonto' }
    ],
    spaziale: [
      { id: 'notte', nome: 'Notte' },
      { id: 'nebulosa', nome: 'Nebulosa' },
      { id: 'marte', nome: 'Marte' },
      { id: 'orbita', nome: 'Orbita' }
    ]
  };

  var SCHERMATE = [
    { id: 'categorie', nome: 'A scomparti', aiuto: 'Giochi, Video, Musica...: si entra in uno per volta.' },
    { id: 'tutte', nome: 'Tutto in vista', aiuto: 'Tutte le app subito, categorie in una barra in alto.' }
  ];

  var ORIENTAMENTI = [
    { id: 'orizzontale', nome: 'Orizzontale' },
    { id: 'verticale', nome: 'Verticale' },
    { id: 'automatico', nome: 'Automatico' }
  ];

  /* Gli stessi identificativi di games/GameCatalog.kt sul tablet. */
  var GIOCHI = [
    { id: 'macchinine', nome: 'Macchinine' },
    { id: 'memory', nome: 'Memory spaziale' },
    { id: 'puzzle', nome: 'Puzzle scorrevole' },
    { id: 'xilofono', nome: 'Xilofono' },
    { id: 'conta', nome: 'Conta le stelle' },
    { id: 'confronto', nome: "Dove ce n'e di piu" },
    { id: 'serie', nome: 'Completa la serie' },
    { id: 'acchiappa', nome: 'Acchiappa la stella' },
    { id: 'sequenza', nome: 'Sequenza luminosa' },
    { id: 'intruso', nome: "Trova l'intruso" },
    { id: 'disegna', nome: 'Disegna nello spazio' }
  ];

  var GIORNI = [
    { n: 1, breve: 'Lun', nome: 'lunedi' },
    { n: 2, breve: 'Mar', nome: 'martedi' },
    { n: 3, breve: 'Mer', nome: 'mercoledi' },
    { n: 4, breve: 'Gio', nome: 'giovedi' },
    { n: 5, breve: 'Ven', nome: 'venerdi' },
    { n: 6, breve: 'Sab', nome: 'sabato' },
    { n: 7, breve: 'Dom', nome: 'domenica' }
  ];

  /* I motivi per cui un tablet puo aver rifiutato un campo. Il tablet manda
     un'etichetta di un elenco chiuso (vedi MOTIVI_RIFIUTO in app/models.py) e
     il testo per le persone sta qui, come per le spie. Il perche sta nel
     commento di quell'elenco: con il testo libero, chi avesse il token di un
     tablet scriveva quello che voleva nella pagina del genitore. */
  var MOTIVI = {
    serve_device_owner: 'Serve che StarKid sia device owner del tablet, e non lo e.',
    serve_profile_owner: 'Serve che StarKid gestisca il profilo, e non lo gestisce.',
    permesso_mancante: 'Manca un permesso da concedere sul tablet.',
    non_supportato: 'Questa versione di Android non ha questa funzione.',
    app_non_installata: 'Una delle app dell’elenco non e installata sul tablet.',
    valore_non_applicabile: 'Il valore e valido, ma su questo tablet non ha effetto.',
    errore_di_sistema: 'Il tablet ha avuto un errore di sistema applicandolo.',
    serve_conferma_pin: 'Il kiosk va confermato col PIN sul tablet: finche nessuno lo conferma resta spento.',
    sconosciuto: 'Il tablet non ha detto perche.'
  };

  function testoMotivo(codice) {
    var chiave = String(codice || 'sconosciuto');
    return MOTIVI[chiave] || ('Motivo non riconosciuto (' + chiave + ').');
  }

  /* Le spie di salute che il tablet manda con POST /api/v1/device/health.
     "limiti" dice se, con quella spia spenta, i limiti di tempo smettono
     davvero di funzionare: e la differenza fra un avviso e un allarme. */
  var SPIE = [
    {
      chiave: 'default_home',
      nome: 'Launcher predefinito',
      limiti: true,
      spenta: 'Il tasto Home non riporta a StarKid: il bambino esce e resta fuori.',
      rimedio: 'Sul tablet: Impostazioni > App > App predefinite > App Home > StarKid.'
    },
    {
      chiave: 'usage_access',
      nome: "Accesso ai dati di utilizzo",
      limiti: true,
      spenta: 'Senza questo StarKid non sa quanto tempo e stato usato il tablet.',
      rimedio: 'Sul tablet: Impostazioni > App > Accesso speciale > Accesso ai dati di utilizzo > StarKid.'
    },
    {
      chiave: 'overlay',
      nome: 'Mostra sopra le altre app',
      limiti: true,
      spenta: 'Il blocco non riesce a comparire se il bambino e dentro un gioco.',
      rimedio: 'Sul tablet: Impostazioni > App > Accesso speciale > Mostra sopra le altre app > StarKid.'
    },
    {
      chiave: 'guard_running',
      nome: 'Servizio di controllo attivo',
      limiti: true,
      spenta: 'Il servizio che conta il tempo non sta girando.',
      rimedio: 'Riavvia il tablet. Se si rispegne, togli StarKid dal risparmio energetico (Batteria > Senza restrizioni).'
    },
    {
      chiave: 'device_owner',
      nome: 'Controllo del tablet (Device Owner)',
      limiti: false,
      spenta: 'Senza Device Owner il kiosk non si puo accendere e StarKid si puo disinstallare.',
      rimedio: 'Si ottiene solo riportando il tablet alle condizioni di fabbrica e rifacendo il QR di arruolamento.'
    }
  ];

  /* Schema CHIUSO: queste e solo queste chiavi vanno nel documento. */
  var CHIAVI_CONFIG = [
    'revisione', 'tema', 'sfondo', 'schermata', 'orientamento',
    'kiosk', 'kiosk_recenti', 'bloccato', 'impronta_pin', 'limite_giornaliero',
    'fascia', 'app_permesse', 'regole_app', 'giochi_spenti', 'suoni'
  ];
  var CHIAVI_FASCIA = ['attiva', 'inizio', 'fine', 'giorni'];
  var CHIAVI_REGOLA = ['pacchetto', 'minuti', 'fascia'];
  var CHIAVI_IMPRONTA = ['sale', 'giri', 'impronta'];

  var PAGINE_AMMESSE = ['index.html', 'tablets.html', 'tablet.html', 'nuovo.html', 'salute.html'];

  /* -------------------------------------------------------------- DOM mini */

  /* Costruttore di elementi. Esiste per non dover scrivere innerHTML:
     il testo passa sempre da textContent, quindi un nome di tablet con
     "<" dentro resta un carattere e non diventa marcatura. */
  function el(tag, attributi, figli) {
    var nodo = document.createElement(tag);
    if (attributi) {
      Object.keys(attributi).forEach(function (chiave) {
        var valore = attributi[chiave];
        if (valore === null || valore === undefined || valore === false) return;
        if (chiave === 'testo') { nodo.textContent = String(valore); return; }
        if (chiave === 'classe') { nodo.className = String(valore); return; }
        if (chiave === 'dato') {
          Object.keys(valore).forEach(function (d) { nodo.dataset[d] = String(valore[d]); });
          return;
        }
        if (valore === true) { nodo.setAttribute(chiave, ''); return; }
        nodo.setAttribute(chiave, String(valore));
      });
    }
    (figli || []).forEach(function (figlio) {
      if (figlio === null || figlio === undefined || figlio === false) return;
      nodo.appendChild(typeof figlio === 'string' ? document.createTextNode(figlio) : figlio);
    });
    return nodo;
  }

  function svuota(nodo) {
    while (nodo && nodo.firstChild) nodo.removeChild(nodo.firstChild);
    return nodo;
  }

  function $(selettore, dentro) { return (dentro || document).querySelector(selettore); }
  function $$(selettore, dentro) {
    return Array.prototype.slice.call((dentro || document).querySelectorAll(selettore));
  }

  /* ---------------------------------------------------------------- errori */

  function ErroreApi(stato, messaggio, campi, dati) {
    this.name = 'ErroreApi';
    this.stato = stato;
    this.messaggio = messaggio;
    this.campi = campi || [];
    this.dati = dati || null;
    this.message = messaggio;
  }
  ErroreApi.prototype = Object.create(Error.prototype);
  ErroreApi.prototype.constructor = ErroreApi;

  /* I messaggi dicono cosa fare, non cosa e andato storto: un genitore alle
     nove di sera non deve interpretare un numero HTTP. */
  function messaggioPerStato(stato, dati) {
    var dalServer = dati && (dati.messaggio || dati.errore);
    if (typeof dalServer === 'string' && dalServer) return dalServer;
    switch (stato) {
      case 0:
        return 'Il server non risponde. Controlla la connessione, poi ricarica la pagina. Se continua, il servizio sul server e fermo.';
      case 400:
        return 'La richiesta non va bene al server. Ricarica la pagina e riprova.';
      case 401:
        return 'Sessione scaduta: devi rientrare.';
      case 403:
        return 'Questa operazione non e permessa in questa sessione. Esci e rientra.';
      case 404:
        return 'Non trovato. Forse e stato cancellato: torna all\'elenco dei tablet.';
      case 409:
        return 'Nel frattempo qualcuno ha salvato una revisione piu recente. Ricarica la pagina, guarda com\'e adesso e rifai la modifica.';
      case 413:
        return 'Hai mandato troppi dati. Accorcia l\'elenco delle app e riprova.';
      case 422:
        return 'Qualcosa nella configurazione non va bene. Correggi i campi segnati e salva di nuovo.';
      case 429:
        return 'Troppi tentativi di fila: il server ti sta rallentando per sicurezza. Aspetta un minuto e riprova.';
      case 502:
      case 503:
        return 'Il servizio StarKid non risponde (nginx c\'e, il programma dietro no). Riprova fra un minuto; se insiste va riavviato sul server.';
      default:
        if (stato >= 500) return 'Il server ha avuto un problema (' + stato + '). Riprova; se succede ancora, sul server: journalctl -u starkid -n 50.';
        return 'Richiesta non riuscita (' + stato + ').';
    }
  }

  /* Elenco dei campi rifiutati, in qualunque forma arrivi:
     - {"campi_rifiutati": [{"campo": "...", "motivo": "..."}]}  (il nostro)
     - {"detail": [{"loc": [...], "msg": "..."}]}                (FastAPI) */
  function estraiCampi(dati) {
    if (!dati || typeof dati !== 'object') return [];
    var grezzi = dati.campi_rifiutati || dati.campi || dati.detail || dati.errori;
    if (!Array.isArray(grezzi)) return [];
    return grezzi.map(function (voce) {
      if (typeof voce === 'string') return { campo: '', motivo: voce };
      var campo = voce.campo || voce.chiave || '';
      if (!campo && Array.isArray(voce.loc)) {
        campo = voce.loc.filter(function (p) { return p !== 'body'; }).join('.');
      }
      return { campo: String(campo || ''), motivo: String(voce.motivo || voce.msg || voce.message || 'valore non valido') };
    }).filter(function (v) { return v.campo || v.motivo; });
  }

  /* ------------------------------------------------------------------ rete */

  /* Il cookie col token CSRF. Il server lo imposta leggibile dalla pagina
     (httponly=false) perche questo e esattamente il suo scopo: il cookie di
     sessione invece e httponly e da qui non si vede, ed e giusto cosi. */
  var NOME_COOKIE_CSRF = 'starkid_csrf';

  function leggiCookie(nome) {
    var pezzi = String(document.cookie || '').split(';');
    for (var i = 0; i < pezzi.length; i++) {
      var taglio = pezzi[i].indexOf('=');
      if (taglio < 0) continue;
      if (pezzi[i].slice(0, taglio).trim() !== nome) continue;
      var valore = pezzi[i].slice(taglio + 1).trim();
      try { return decodeURIComponent(valore); } catch (e) { return valore; }
    }
    return '';
  }

  /* Una sola porta verso il server. Tutte le pagine passano da qui, cosi
     intestazioni, errori e sessione scaduta si trattano in un posto solo. */
  function api(metodo, percorso, corpo) {
    var opzioni = {
      method: metodo,
      credentials: 'same-origin',
      cache: 'no-store',
      /* "manual": se il server (o nginx) risponde con un rimando invece che
         con il JSON, non lo si segue. Quasi sempre vuol dire "sei finito
         sulla pagina di accesso", e si tratta come sessione scaduta invece
         di mostrare al genitore dell'HTML dentro un errore. */
      redirect: 'manual',
      /* Intestazione nostra: un modulo HTML di un altro sito non puo
         aggiungerla, quindi il server puo pretenderla e buttare via le
         richieste che arrivano da fuori (CSRF). */
      headers: { 'Accept': 'application/json', 'X-StarKid-Web': '1' }
    };
    /* La seconda serratura, e quella che il server controlla davvero:
       X-StarKid-Web e solo un'intestazione, nessuno lato server la legge.
       Senza queste righe ogni scrittura prende 403 e la UI sembra rotta
       senza dire perche. Sulle GET non serve e non si manda. */
    if (metodo !== 'GET' && metodo !== 'HEAD') {
      var gettone = leggiCookie(NOME_COOKIE_CSRF);
      if (gettone) opzioni.headers['X-CSRF-Token'] = gettone;
    }
    if (corpo !== undefined) {
      opzioni.headers['Content-Type'] = 'application/json';
      opzioni.body = JSON.stringify(corpo);
    }

    return fetch(percorso, opzioni).catch(function () {
      throw new ErroreApi(0, messaggioPerStato(0));
    }).then(function (risposta) {
      if (risposta.type === 'opaqueredirect' || (risposta.status === 0 && risposta.type !== 'basic')) {
        throw new ErroreApi(401, messaggioPerStato(401));
      }
      return risposta.text().then(function (testo) {
        var dati = null;
        if (testo) { try { dati = JSON.parse(testo); } catch (e) { dati = null; } }
        if (risposta.ok) return dati === null ? {} : dati;
        throw new ErroreApi(risposta.status, messaggioPerStato(risposta.status, dati), estraiCampi(dati), dati);
      });
    });
  }

  /* Pagina di accesso, tornando dove si era. Il "ritorno" e ripulito a mano:
     si accettano solo i nomi di pagina di questo sito, mai un indirizzo
     intero, cosi non diventa un trampolino verso un altro sito. */
  function allAccesso(motivo) {
    var qui = window.location.pathname.split('/').pop() || 'tablets.html';
    if (PAGINE_AMMESSE.indexOf(qui) < 0) qui = 'tablets.html';
    var id = parametro('id');
    var ritorno = qui + (id ? '?id=' + encodeURIComponent(id) : '');
    var q = 'index.html?motivo=' + encodeURIComponent(motivo || 'scaduta');
    if (qui !== 'index.html') q += '&ritorno=' + encodeURIComponent(ritorno);
    window.location.replace(q);
  }

  /* Come api(), ma se la sessione e caduta riporta all'accesso invece di
     lasciare la pagina piena di errori. La promessa restituita non si
     risolve mai in quel caso: la pagina sta per cambiare. */
  function apiSessione(metodo, percorso, corpo) {
    return api(metodo, percorso, corpo).catch(function (errore) {
      if (errore.stato === 401) {
        allAccesso('scaduta');
        return new Promise(function () { /* si ferma qui: via alla pagina di accesso */ });
      }
      throw errore;
    });
  }

  function destinazioneRitorno() {
    var ritorno = parametro('ritorno') || '';
    var pezzi = ritorno.split('?');
    var pagina = pezzi[0];
    if (PAGINE_AMMESSE.indexOf(pagina) < 0 || pagina === 'index.html') return 'tablets.html';
    var id = '';
    if (pezzi[1]) {
      pezzi[1].split('&').forEach(function (coppia) {
        var kv = coppia.split('=');
        if (kv[0] === 'id') id = decodeURIComponent(kv[1] || '');
      });
    }
    return pagina + (id ? '?id=' + encodeURIComponent(id) : '');
  }

  function parametro(nome) {
    return new URLSearchParams(window.location.search).get(nome);
  }

  /* ---------------------------------------------------------------- numeri */

  function minutiInOra(minuti) {
    var m = Math.max(0, Math.min(1440, Number(minuti) || 0));
    var ore = Math.floor(m / 60);
    var resto = m % 60;
    return (ore < 10 ? '0' : '') + ore + ':' + (resto < 10 ? '0' : '') + resto;
  }

  function oraInMinuti(testo) {
    var pezzi = String(testo || '').split(':');
    if (pezzi.length < 2) return null;
    var ore = parseInt(pezzi[0], 10);
    var minuti = parseInt(pezzi[1], 10);
    if (isNaN(ore) || isNaN(minuti)) return null;
    return ore * 60 + minuti;
  }

  function durata(minuti) {
    var m = Number(minuti) || 0;
    if (m <= 0) return 'nessun limite';
    if (m < 60) return m + ' minuti';
    var ore = Math.floor(m / 60);
    var resto = m % 60;
    return ore + (ore === 1 ? ' ora' : ' ore') + (resto ? ' e ' + resto + ' minuti' : '');
  }

  function byte(n) {
    var b = Number(n);
    if (!isFinite(b) || b < 0) return '?';
    if (b < 1024) return b + ' byte';
    if (b < 1024 * 1024) return (b / 1024).toFixed(1).replace('.', ',') + ' KB';
    return (b / 1024 / 1024).toFixed(1).replace('.', ',') + ' MB';
  }

  /* ------------------------------------------------------------------ date */

  /* Se il server manda una data senza fuso (il caso tipico di un
     datetime.utcnow().isoformat() in Python) la si legge come UTC: meglio
     cosi che far sembrare "visto due ore fa" un tablet appena arrivato. */
  /* Il server manda i momenti come SECONDI dal 1970, un numero intero
     (visto_il, salute_il, creato_il, arruolato_il, ultimo_backup, da). Questa
     funzione sapeva leggere solo le stringhe ISO, quindi tornava sempre null:
     ogni tablet risultava "mai visto" e la pagina Servizio li contava tutti
     come zitti. Un guasto che non fa rumore - nessun errore in console, solo
     una UI che mente. Si accettano tutti e due i formati: le stringhe perche
     sono quelle documentate in cima a questo file, i numeri perche sono
     quelli che arrivano davvero. */
  function data(valore) {
    if (valore === null || valore === undefined || valore === '') return null;
    if (valore instanceof Date) return valore;

    if (typeof valore === 'number' || /^-?\d+$/.test(String(valore).trim())) {
      var n = Number(valore);
      if (!isFinite(n) || n === 0) return null;
      /* Sotto i cento miliardi sono secondi: in millisecondi quel numero
         sarebbe il 1973, in secondi il 5138. Nessuno dei due e una data che
         questo server possa produrre, ma la prima e molto piu probabile come
         errore di unita, quindi la soglia sta li. */
      var ms = Math.abs(n) < 1e11 ? n * 1000 : n;
      var dn = new Date(ms);
      return isNaN(dn.getTime()) ? null : dn;
    }

    var testo = String(valore);
    if (/^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}/.test(testo) && !/[zZ]|[+-]\d{2}:?\d{2}$/.test(testo)) {
      testo = testo.replace(' ', 'T') + 'Z';
    }
    var d = new Date(testo);
    return isNaN(d.getTime()) ? null : d;
  }

  function quando(valore) {
    var d = data(valore);
    if (!d) return 'mai';
    var secondi = Math.round((Date.now() - d.getTime()) / 1000);
    if (secondi < 0) return 'adesso';
    if (secondi < 90) return 'poco fa';
    var minuti = Math.round(secondi / 60);
    if (minuti < 60) return minuti + ' minuti fa';
    var ore = Math.round(minuti / 60);
    if (ore < 24) return ore === 1 ? "un'ora fa" : ore + ' ore fa';
    var giorni = Math.round(ore / 24);
    if (giorni === 1) return 'ieri';
    if (giorni < 30) return giorni + ' giorni fa';
    return 'il ' + dataEOra(d);
  }

  function dataEOra(valore) {
    var d = data(valore);
    if (!d) return '-';
    var g = ('0' + d.getDate()).slice(-2);
    var m = ('0' + (d.getMonth() + 1)).slice(-2);
    var o = ('0' + d.getHours()).slice(-2);
    var i = ('0' + d.getMinutes()).slice(-2);
    return g + '/' + m + '/' + d.getFullYear() + ' alle ' + o + ':' + i;
  }

  /* ------------------------------------------------------- configurazione */

  function configVuota() {
    return {
      revisione: 0,
      tema: 'pulito',
      sfondo: 'lilla',
      schermata: 'categorie',
      orientamento: 'automatico',
      kiosk: false,
      kiosk_recenti: false,
      bloccato: false,
      impronta_pin: null,
      limite_giornaliero: 60,
      fascia: { attiva: false, inizio: 480, fine: 1200, giorni: [1, 2, 3, 4, 5, 6, 7] },
      app_permesse: [],
      regole_app: [],
      giochi_spenti: [],
      suoni: true
    };
  }

  function sfondiDi(tema) { return SFONDI[tema] || SFONDI.pulito; }

  function sfondoAppartiene(tema, sfondo) {
    return sfondiDi(tema).some(function (s) { return s.id === sfondo; });
  }

  /* ------------------------------------------------------------- il PIN */

  /* Il PIN dell'area genitori si cambia da qui, ma non passa da qui: al
     server arrivano sale, giri e impronta, calcolati in questa pagina. Il
     calcolo deve dare lo stesso risultato, carattere per carattere, di
     data/Pin.kt sul tablet e di genitore/Pin.kt: tre copie della stessa
     cosa, e il vettore di prova sta in PinTest.kt.

     Va detto chiaro, perche e il limite della scelta: quattro cifre dietro a
     un'impronta si ritrovano con diecimila tentativi, quindi questo non
     rende il PIN un segreto per chi ha il database del server. Serve a non
     farcelo passare in chiaro e a non lasciarlo scritto nel database, nei
     log e nelle dieci revisioni dello storico, che restano. Un PIN piu
     lungo e l'unica cosa che lo rende davvero difficile: per questo se ne
     accettano fino a otto cifre. */
  var PIN_CIFRE_MIN = 4;
  var PIN_CIFRE_MAX = 8;
  var PIN_GIRI = 20000;

  function pinValido(pin) {
    return typeof pin === 'string' &&
      pin.length >= PIN_CIFRE_MIN && pin.length <= PIN_CIFRE_MAX &&
      /^[0-9]+$/.test(pin);
  }

  function esadecimale(buffer) {
    var byte = new Uint8Array(buffer);
    var fuori = '';
    for (var i = 0; i < byte.length; i++) {
      fuori += (byte[i] < 16 ? '0' : '') + byte[i].toString(16);
    }
    return fuori;
  }

  function nuovoSale() {
    var byte = new Uint8Array(16);
    window.crypto.getRandomValues(byte);
    return esadecimale(byte.buffer);
  }

  /* Il sale entra nel conto come la sua stringa esadecimale, non come i
     sedici byte che rappresenta: e cosi dalla prima versione del tablet, e
     cambiarlo adesso renderebbe irriconoscibile il PIN di ogni tablet gia in
     giro. Ventimila giri in sequenza sono qualche decimo di secondo qui e
     altrettanti sul tablet: non si alzano senza misurarli su un tablet
     lento, perche quel numero lo paga chi batte il PIN, ogni volta. */
  function giroDopoGiro(dati, rimasti) {
    if (rimasti <= 0) return Promise.resolve(dati);
    return window.crypto.subtle.digest('SHA-256', dati).then(function (uscita) {
      /* Ricorsione sulle promesse, non un ciclo che costruisce ventimila
         .then() prima di cominciare: la pila non cresce (ogni passo e un
         microtask) e la memoria resta quella di un giro solo. */
      return giroDopoGiro(uscita, rimasti - 1);
    });
  }

  function improntaPin(pin, sale, giri) {
    var quanti = giri || PIN_GIRI;
    var partenza = new TextEncoder().encode(sale + pin).buffer;
    return giroDopoGiro(partenza, quanti).then(function (dati) {
      return { sale: sale, giri: quanti, impronta: esadecimale(dati) };
    });
  }

  /* Il PIN nuovo, pronto da mettere nel documento. Il sale e sempre nuovo:
     cosi rimandare lo stesso PIN fa comunque un'impronta diversa, e il
     tablet capisce che deve riapplicarlo. */
  function preparaPin(pin) {
    if (!window.crypto || !window.crypto.subtle) {
      return Promise.reject(new Error(
        'Questo browser non sa calcolare l\'impronta del PIN (serve una pagina in https). ' +
        'Il PIN si puo sempre cambiare stando davanti al tablet.'));
    }
    if (!pinValido(pin)) {
      return Promise.reject(new Error(
        'Il PIN sono da ' + PIN_CIFRE_MIN + ' a ' + PIN_CIFRE_MAX + ' cifre, niente altro.'));
    }
    return improntaPin(pin, nuovoSale(), PIN_GIRI);
  }

  function improntaValida(valore) {
    if (valore === null || valore === undefined) return true;
    if (typeof valore !== 'object' || Array.isArray(valore)) return false;
    if (!/^[0-9a-f]{32}$/.test(String(valore.sale))) return false;
    if (!/^[0-9a-f]{64}$/.test(String(valore.impronta))) return false;
    if (!Number.isInteger(valore.giri) || valore.giri < 20000 || valore.giri > 200000) return false;
    return Object.keys(valore).every(function (chiave) {
      return CHIAVI_IMPRONTA.indexOf(chiave) >= 0;
    });
  }

  /* Gli stessi controlli che fa il server, qui davanti. Non per fidarsi del
     browser - l'ultima parola e del server, che risponde 422 - ma per non
     far scoprire l'errore dopo il salvataggio. */
  function validaConfig(cfg) {
    var problemi = [];
    function no(campo, motivo) { problemi.push({ campo: campo, motivo: motivo }); }

    Object.keys(cfg).forEach(function (chiave) {
      if (CHIAVI_CONFIG.indexOf(chiave) < 0) no(chiave, 'campo non previsto: lo schema e chiuso, il server lo rifiuta');
    });

    if (!TEMI.some(function (t) { return t.id === cfg.tema; })) no('tema', 'scegli "pulito" o "spaziale"');
    if (cfg.sfondo === 'foto') {
      no('sfondo', 'la foto si sceglie sul tablet, non da qui: qui si scelgono solo le tinte del tema');
    } else if (!sfondoAppartiene(cfg.tema, cfg.sfondo)) {
      no('sfondo', 'questo sfondo non appartiene al tema ' + cfg.tema + ': scegline uno fra ' +
        sfondiDi(cfg.tema).map(function (s) { return s.nome; }).join(', '));
    }
    if (!SCHERMATE.some(function (s) { return s.id === cfg.schermata; })) no('schermata', 'scegli "categorie" o "tutte"');
    if (!ORIENTAMENTI.some(function (o) { return o.id === cfg.orientamento; })) no('orientamento', 'scegli orizzontale, verticale o automatico');
    if (typeof cfg.kiosk !== 'boolean') no('kiosk', 'deve essere acceso o spento');
    if (typeof cfg.kiosk_recenti !== 'boolean') no('kiosk_recenti', 'deve essere acceso o spento');
    if (typeof cfg.bloccato !== 'boolean') no('bloccato', 'deve essere acceso o spento');
    if (typeof cfg.suoni !== 'boolean') no('suoni', 'deve essere acceso o spento');

    /* Qui dentro non c'e il PIN: ci sono sale, giri e impronta, e il
       browser li ha appena calcolati. Si controlla la forma, perche un
       documento storto farebbe rifiutare tutto il salvataggio dal server
       con un messaggio meno chiaro di questo. */
    if (!improntaValida(cfg.impronta_pin)) {
      no('impronta_pin', 'l\'impronta del PIN non ha la forma giusta: rimetti il PIN e risalva');
    }

    var limite = cfg.limite_giornaliero;
    if (!Number.isInteger(limite) || limite < 0 || limite > 1440) {
      no('limite_giornaliero', 'minuti al giorno: un numero intero da 0 (nessun limite) a 1440');
    }

    var fascia = cfg.fascia;
    if (!fascia || typeof fascia !== 'object' || Array.isArray(fascia)) {
      no('fascia', 'manca la fascia oraria');
    } else {
      Object.keys(fascia).forEach(function (chiave) {
        if (CHIAVI_FASCIA.indexOf(chiave) < 0) no('fascia.' + chiave, 'campo non previsto dentro la fascia');
      });
      if (typeof fascia.attiva !== 'boolean') no('fascia.attiva', 'deve essere acceso o spento');
      if (!Number.isInteger(fascia.inizio) || fascia.inizio < 0 || fascia.inizio > 1440) no('fascia.inizio', 'orario non valido');
      if (!Number.isInteger(fascia.fine) || fascia.fine < 0 || fascia.fine > 1440) no('fascia.fine', 'orario non valido');
      if (Number.isInteger(fascia.inizio) && Number.isInteger(fascia.fine) && fascia.fine <= fascia.inizio) {
        no('fascia.fine', 'la fine deve venire dopo l\'inizio (per adesso la fascia non puo passare la mezzanotte)');
      }
      if (!Array.isArray(fascia.giorni) || fascia.giorni.length === 0) {
        no('fascia.giorni', 'scegli almeno un giorno, oppure spegni la fascia');
      } else if (!fascia.giorni.every(function (g) { return Number.isInteger(g) && g >= 1 && g <= 7; })) {
        no('fascia.giorni', 'i giorni vanno da 1 (lunedi) a 7 (domenica)');
      }
    }

    if (!Array.isArray(cfg.app_permesse)) {
      no('app_permesse', 'elenco non valido');
    } else {
      cfg.app_permesse.forEach(function (p) {
        if (!/^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)+$/.test(String(p))) {
          no('app_permesse', '"' + p + '" non e un nome di pacchetto Android (esempio: com.esempio.gioco)');
        }
      });
      if (cfg.app_permesse.length > 200) no('app_permesse', 'troppe app: al massimo 200');
    }

    if (!Array.isArray(cfg.regole_app)) {
      no('regole_app', 'elenco non valido');
    } else {
      if (cfg.regole_app.length > 200) no('regole_app', 'troppe regole: al massimo 200');
      var visti = [];
      cfg.regole_app.forEach(function (regola) {
        if (!regola || typeof regola !== 'object' || Array.isArray(regola)) {
          no('regole_app', 'regola non valida');
          return;
        }
        Object.keys(regola).forEach(function (chiave) {
          if (CHIAVI_REGOLA.indexOf(chiave) < 0) no('regole_app', 'campo non previsto dentro una regola: ' + chiave);
        });
        if (!/^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)+$/.test(String(regola.pacchetto))) {
          no('regole_app', '"' + regola.pacchetto + '" non e un nome di pacchetto Android');
          return;
        }
        if (visti.indexOf(regola.pacchetto) >= 0) {
          no('regole_app', regola.pacchetto + ' ha due regole: tienine una sola');
        }
        visti.push(regola.pacchetto);
        if (regola.minuti !== undefined &&
            (!Number.isInteger(regola.minuti) || regola.minuti < 0 || regola.minuti > 1440)) {
          no('regole_app', regola.pacchetto + ': i minuti vanno da 0 a 1440');
        }
        if (regola.fascia === undefined || regola.fascia === null) return;
        var f = regola.fascia;
        if (typeof f !== 'object' || Array.isArray(f)) {
          no('regole_app', regola.pacchetto + ': fascia non valida');
          return;
        }
        Object.keys(f).forEach(function (chiave) {
          if (CHIAVI_FASCIA.indexOf(chiave) < 0) no('regole_app', regola.pacchetto + ': campo non previsto dentro la fascia');
        });
        if (!Number.isInteger(f.inizio) || f.inizio < 0 || f.inizio > 1439) no('regole_app', regola.pacchetto + ': orario di inizio non valido');
        if (!Number.isInteger(f.fine) || f.fine < 0 || f.fine > 1439) no('regole_app', regola.pacchetto + ': orario di fine non valido');
        // A differenza della fascia generale, qui scavalcare la mezzanotte e
        // ammesso: il tablet lo sa fare, e per una app sola - un video prima
        // di dormire - e una richiesta normale.
        if (!Array.isArray(f.giorni) || f.giorni.length === 0) {
          no('regole_app', regola.pacchetto + ': scegli almeno un giorno, oppure togli la fascia');
        } else if (!f.giorni.every(function (g) { return Number.isInteger(g) && g >= 1 && g <= 7; })) {
          no('regole_app', regola.pacchetto + ': giorni non validi');
        }
      });
    }

    if (!Array.isArray(cfg.giochi_spenti)) {
      no('giochi_spenti', 'elenco non valido');
    } else {
      cfg.giochi_spenti.forEach(function (g) {
        if (!GIOCHI.some(function (gi) { return gi.id === g; })) no('giochi_spenti', '"' + g + '" non e un gioco di StarKid');
      });
    }
    return problemi;
  }

  /* Tiene solo le chiavi dello schema, nell'ordine dello schema. Serve a non
     rimandare al server la roba in piu che potrebbe essersi attaccata al
     documento (per esempio una chiave aggiunta da una versione futura del
     server: in quel caso e giusto che la rifiuti). */
  function soloChiaviConfig(cfg) {
    var pulito = {};
    CHIAVI_CONFIG.forEach(function (chiave) {
      if (chiave === 'fascia') {
        var f = cfg.fascia || {};
        pulito.fascia = {
          attiva: !!f.attiva,
          inizio: f.inizio,
          fine: f.fine,
          giorni: Array.isArray(f.giorni) ? f.giorni.slice().sort(function (a, b) { return a - b; }) : []
        };
        return;
      }
      if (chiave === 'impronta_pin') {
        /* null vuol dire "da qui non si dice niente sul PIN", e il tablet
           tiene il suo. Non vuol dire "togli il PIN": quello da lontano non
           si puo fare, se no sarebbe un modo per aprire l'area genitori. */
        var imp = cfg.impronta_pin;
        pulito.impronta_pin = (imp && typeof imp === 'object' && !Array.isArray(imp))
          ? { sale: imp.sale, giri: imp.giri, impronta: imp.impronta }
          : null;
        return;
      }
      if (chiave === 'regole_app') {
        // Ogni regola passa dalla stessa setacciatura del documento: una
        // chiave di troppo qui dentro fa rifiutare tutto il documento, e
        // sarebbe difficile da capire guardando il modulo.
        pulito.regole_app = (Array.isArray(cfg.regole_app) ? cfg.regole_app : []).map(function (regola) {
          var voce = { pacchetto: regola.pacchetto, minuti: regola.minuti || 0 };
          if (regola.fascia) {
            voce.fascia = {
              attiva: true,
              inizio: regola.fascia.inizio,
              fine: regola.fascia.fine,
              giorni: Array.isArray(regola.fascia.giorni)
                ? regola.fascia.giorni.slice().sort(function (a, b) { return a - b; })
                : []
            };
          }
          return voce;
        });
        return;
      }
      if (cfg[chiave] !== undefined) pulito[chiave] = cfg[chiave];
    });
    return pulito;
  }

  /* ---------------------------------------------------------------- tablet */

  function primo() {
    for (var i = 0; i < arguments.length; i++) {
      if (arguments[i] !== undefined && arguments[i] !== null) return arguments[i];
    }
    return undefined;
  }

  /* I nomi che il server manda davvero stanno PER PRIMI in ogni elenco; gli
     altri sono sinonimi tollerati. Quattro di questi (salute_dichiarata,
     ultimo_esito, visto_il, salute_il) mancavano, e il risultato era una
     pagina che si apriva senza errori mostrando tutto "ignoto": il guasto
     piu sgradevole, perche sembra un tablet silenzioso e non un campo letto
     col nome sbagliato. */
  function normalizzaTablet(grezzo) {
    var t = grezzo || {};
    var salute = t.salute_dichiarata || t.salute || t.spie || t.salute_tablet || {};
    return {
      id: String(primo(t.id, t.id_dispositivo, t.dispositivo, '')),
      nome: String(primo(t.nome, t.etichetta, t.nome_tablet, 'tablet senza nome')),
      revisione: Number(primo(t.revisione, t.revisione_corrente, 0)),
      revisione_applicata: primo(t.revisione_confermata, t.revisione_applicata, t.applicata, null),
      esito: primo(t.ultimo_esito, t.esito, t.esito_ultimo, null),
      campi_rifiutati: Array.isArray(t.campi_rifiutati) ? t.campi_rifiutati : [],
      ultimo_contatto: primo(t.visto_il, t.ultimo_contatto, t.visto, t.ultima_visita, null),
      salute_aggiornata: primo(t.salute_il, t.salute_aggiornata, t.salute_quando, null),
      arruolato: primo(t.arruolato, t.iscritto, null),
      /* null = la configurazione non si rilegge, quindi non si sa; e diverso
         da false, che vuol dire "aperto". L'elenco mostra il cartello solo
         sul true. */
      bloccato: (t.bloccato === true || t.bloccato === false) ? t.bloccato : null,
      salute: salute,
      grezzo: t
    };
  }

  function spieDi(tablet) {
    return SPIE.map(function (spia) {
      var valore = tablet.salute ? tablet.salute[spia.chiave] : undefined;
      return {
        chiave: spia.chiave,
        nome: spia.nome,
        limiti: spia.limiti,
        spenta: spia.spenta,
        rimedio: spia.rimedio,
        stato: valore === true ? 'accesa' : (valore === false ? 'spenta' : 'ignota')
      };
    });
  }

  /* Le spie che, spente, fanno saltare i limiti di tempo. */
  function spieCritiche(tablet) {
    return spieDi(tablet).filter(function (s) { return s.limiti && s.stato === 'spenta'; });
  }

  function tabletArruolato(tablet) {
    return tablet.arruolato === true || (tablet.arruolato === null && !!tablet.ultimo_contatto);
  }

  /* ------------------------------------------------------------- interfaccia */

  /* La conseguenza di una spia spenta deve stare SCRITTA, non dentro un
     title. Un title si vede solo passandoci sopra col mouse, e il caso d'uso
     dichiarato di questa UI e il genitore col telefono in mano: li non c'e
     nessun "sopra", quindi la cosa piu importante che la pagina ha da dire
     non la leggeva nessuno. (E nemmeno con la tastiera: un <li> non prende il
     fuoco, e gli screen reader il title su un <li> non lo annunciano.)

     Le spie critiche, quelle che fermano i limiti di tempo, non si ripetono
     qui: sono gia spiegate per esteso nel cartello rosso sopra la scheda
     (cartelloLimiti). Qui si scrivono le altre, che altrimenti resterebbero
     un nome rosso senza un perche. */
  function spiaNodo(spia) {
    var spenta = spia.stato === 'spenta';
    var nodo = el('li', {
      classe: 'spia ' + spia.stato,
      title: spenta ? spia.spenta : ''
    }, [spia.nome]);
    if (spia.stato === 'ignota') {
      nodo.appendChild(el('span', { classe: 'piccolo', testo: ' (mai detto)' }));
    } else if (spenta && !spia.limiti) {
      nodo.appendChild(el('span', { classe: 'conseguenza piccolo', testo: spia.spenta }));
    }
    return nodo;
  }

  /* Le spie, con sopra scritto da chi arrivano. Non e un cavillo: il server
     non puo verificarle, le scrive il tablet. Chi avesse il token di un
     tablet puo mandare cinque spie verdi piu spesso di quanto lo fa il tablet
     vero, e il genitore vedrebbe tutto a posto. Dire "secondo il tablet" e
     l'unica cosa onesta che questa pagina puo fare. */
  function listaSpie(tablet) {
    var blocco = el('div');
    var quando = tablet && tablet.salute_aggiornata;
    blocco.appendChild(el('p', {
      classe: 'piccolo tenue',
      testo: quando
        ? 'Secondo il tablet, ' + quando_minuscolo(quando) + '.'
        : 'Secondo il tablet (non ha ancora mandato niente).'
    }));
    var lista = el('ul', { classe: 'spie' });
    spieDi(tablet).forEach(function (spia) { lista.appendChild(spiaNodo(spia)); });
    blocco.appendChild(lista);
    return blocco;
  }

  function quando_minuscolo(valore) {
    var testo = quando(valore);
    return testo ? testo.charAt(0).toLowerCase() + testo.slice(1) : 'poco fa';
  }

  /* Il cartello che il progetto chiede per nome. Non e un'icona gialla in un
     angolo: se i limiti non funzionano, il genitore lo deve leggere. */
  function cartelloLimiti(tablet) {
    var rotte = spieCritiche(tablet);
    if (rotte.length === 0) return null;
    var cartello = el('div', { classe: 'avviso guasto avviso-grosso', role: 'alert' }, [
      el('h3', {}, [
        'I limiti di tempo su questo tablet ',
        el('strong', { testo: 'NON' }),
        ' stanno funzionando'
      ]),
      el('p', { classe: 'piccolo', testo: 'Il tablet ha detto che gli manca questo:' })
    ]);
    var elenco = el('ul');
    rotte.forEach(function (spia) {
      elenco.appendChild(el('li', {}, [
        el('strong', { testo: spia.nome }),
        ' - ' + spia.spenta + ' ',
        el('span', { classe: 'tenue', testo: spia.rimedio })
      ]));
    });
    cartello.appendChild(elenco);
    return cartello;
  }

  function avviso(tipo, titolo, testo) {
    var nodo = el('div', { classe: 'avviso ' + (tipo || ''), role: tipo === 'guasto' ? 'alert' : 'status' });
    if (titolo) nodo.appendChild(el('h3', { testo: titolo }));
    if (testo) nodo.appendChild(el('p', { testo: testo }));
    return nodo;
  }

  function mostraErrore(contenitore, errore, titolo) {
    if (!contenitore) return;
    svuota(contenitore);
    contenitore.classList.remove('nascosto');
    var nodo = avviso('guasto', titolo || 'Non ha funzionato', errore && errore.messaggio ? errore.messaggio : String(errore));
    var campi = (errore && errore.campi) || [];
    if (campi.length) {
      var elenco = el('ul');
      campi.forEach(function (c) {
        elenco.appendChild(el('li', {}, [c.campo ? el('strong', { testo: c.campo + ': ' }) : null, c.motivo]));
      });
      nodo.appendChild(elenco);
    }
    contenitore.appendChild(nodo);
    contenitore.scrollIntoView({ block: 'nearest' });
  }

  function mostraMessaggio(contenitore, tipo, titolo, testo) {
    if (!contenitore) return;
    svuota(contenitore);
    contenitore.classList.remove('nascosto');
    contenitore.appendChild(avviso(tipo, titolo, testo));
  }

  function pulisci(contenitore) {
    if (!contenitore) return;
    svuota(contenitore);
    contenitore.classList.add('nascosto');
  }

  function occupato(bottone, acceso, testoAttesa) {
    if (!bottone) return;
    if (acceso) {
      bottone.dataset.testoOriginale = bottone.textContent;
      bottone.disabled = true;
      bottone.setAttribute('aria-busy', 'true');
      if (testoAttesa) bottone.textContent = testoAttesa;
    } else {
      bottone.disabled = false;
      bottone.removeAttribute('aria-busy');
      if (bottone.dataset.testoOriginale) bottone.textContent = bottone.dataset.testoOriginale;
    }
  }

  /* ------------------------------------------------------------- scaricare */

  function scarica(nomeFile, contenuto, tipo) {
    var blob = contenuto instanceof Blob ? contenuto : new Blob([contenuto], { type: tipo || 'application/json;charset=utf-8' });
    var url = URL.createObjectURL(blob);
    var a = el('a', { href: url, download: nomeFile });
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    setTimeout(function () { URL.revokeObjectURL(url); }, 2000);
  }

  function copia(testo) {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      return navigator.clipboard.writeText(testo);
    }
    /* Senza https il browser non da gli appunti: si lascia il testo
       selezionato, cosi resta un ctrl+C da fare. */
    return Promise.reject(new Error('Gli appunti non sono disponibili: seleziona il testo e copialo a mano (Ctrl+C).'));
  }

  function nomeFileSicuro(testo, estensione) {
    var base = String(testo || 'starkid').toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '')
      .slice(0, 40) || 'starkid';
    return base + estensione;
  }

  /* ----------------------------------------------------------- avvio comune */

  function esci() {
    api('POST', '/api/v1/auth/logout').catch(function () { /* esco comunque */ })
      .then(function () { window.location.replace('index.html?motivo=uscito'); });
  }

  /* Chi sono, letto una volta sola per pagina.

     Prima non c'era modo di saperlo: nome e ruolo arrivavano soltanto nella
     risposta del login, quindi dopo un ricaricamento la pagina non sapeva
     piu chi fosse e mostrava tutto a tutti. Con un account solo non se ne
     accorgeva nessuno. */
  var _io = null;
  function chiSono() {
    if (_io) return _io;
    _io = api('GET', '/api/v1/auth/io').then(function (dati) {
      return {
        utente: String(dati.utente || ''),
        ruolo: String(dati.ruolo || 'genitore'),
        puoAmministrare: dati.puo_amministrare === true,
        solaLettura: dati.sola_lettura === true
      };
    });
    return _io;
  }

  /* Il ruolo finisce su <html data-ruolo="...">, cosi la CSS puo nascondere
     quello che non serve senza che ogni pagina se lo ricordi. NON e una
     difesa: il server rifiuta comunque, con 403. Questo serve a non mettere
     davanti a un osservatore dei bottoni che gli risponderebbero di no. */
  function vestiPerIlRuolo(io) {
    document.documentElement.setAttribute('data-ruolo', io.ruolo);
    var nav = $('.barra nav');
    if (nav && io.puoAmministrare && !$('.barra nav a[href="utenti.html"]')) {
      var voce = el('a', { href: 'utenti.html', testo: 'Utenti' });
      nav.appendChild(voce);
      var pagina = window.location.pathname.split('/').pop() || 'index.html';
      if (pagina === 'utenti.html') voce.classList.add('attiva');
    }
    var chi = $('#chi-sono');
    if (chi) chi.textContent = io.utente;
  }

  function avvia() {
    var bottone = $('#esci');
    if (bottone) bottone.addEventListener('click', function () { esci(); });
    var pagina = window.location.pathname.split('/').pop() || 'index.html';
    $$('.barra nav a').forEach(function (a) {
      if (a.getAttribute('href') === pagina) a.classList.add('attiva');
    });
    /* Solo dove c'e una barra, cioe dentro: la pagina di accesso non ha
       sessione e la chiamata risponderebbe 401 a vuoto. */
    if ($('.barra nav')) {
      chiSono().then(vestiPerIlRuolo).catch(function () { /* ci pensa la pagina */ });
    }
  }
  document.addEventListener('DOMContentLoaded', avvia);

  return {
    TEMI: TEMI, SFONDI: SFONDI, SCHERMATE: SCHERMATE, ORIENTAMENTI: ORIENTAMENTI,
    GIOCHI: GIOCHI, GIORNI: GIORNI, SPIE: SPIE, CHIAVI_CONFIG: CHIAVI_CONFIG,
    el: el, svuota: svuota, $: $, $$: $$,
    ErroreApi: ErroreApi, api: api, apiSessione: apiSessione,
    allAccesso: allAccesso, destinazioneRitorno: destinazioneRitorno, parametro: parametro,
    minutiInOra: minutiInOra, oraInMinuti: oraInMinuti, durata: durata, byte: byte,
    data: data, quando: quando, dataEOra: dataEOra,
    configVuota: configVuota, sfondiDi: sfondiDi, sfondoAppartiene: sfondoAppartiene,
    validaConfig: validaConfig, soloChiaviConfig: soloChiaviConfig,
    PIN_CIFRE_MIN: PIN_CIFRE_MIN, PIN_CIFRE_MAX: PIN_CIFRE_MAX, PIN_GIRI: PIN_GIRI,
    pinValido: pinValido, preparaPin: preparaPin, improntaPin: improntaPin,
    improntaValida: improntaValida,
    normalizzaTablet: normalizzaTablet, spieDi: spieDi, spieCritiche: spieCritiche,
    testoMotivo: testoMotivo,
    arruolato: tabletArruolato, listaSpie: listaSpie, cartelloLimiti: cartelloLimiti,
    avviso: avviso, mostraErrore: mostraErrore, mostraMessaggio: mostraMessaggio,
    pulisci: pulisci, occupato: occupato,
    scarica: scarica, copia: copia, nomeFileSicuro: nomeFileSicuro, esci: esci,
    chiSono: chiSono
  };
})();
