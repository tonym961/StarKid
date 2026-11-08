/* tablet.html - configurazione di un tablet.
 *
 * La configurazione va in una direzione sola: da qui al tablet. Quello che si
 * salva qui diventa una revisione nuova; il tablet se la prende al prossimo
 * collegamento e poi dice com'e andata (applicata, rifiutata, a metà).
 * Questa pagina non riceve mai impostazioni dal tablet.
 */

'use strict';

(function () {

  var ID = SK.parametro('id');
  var base = null;      /* la configurazione come l'ha data il server */
  var tablet = null;    /* la scheda del tablet, per nome e spie */
  var sporco = false;

  /* ------------------------------------------------------- costruzione */

  function nodoErrore(campo) {
    /* getElementById e non querySelector: i nomi dei campi hanno il punto
       dentro (fascia.inizio) e in un selettore il punto vorrebbe dire
       "classe". */
    return document.getElementById('errore-' + campo);
  }

  function segnaErrori(problemi) {
    SK.$$('.errore-campo').forEach(function (nodo) {
      nodo.textContent = '';
      nodo.classList.add('nascosto');
    });
    SK.$$('[aria-invalid]').forEach(function (nodo) { nodo.removeAttribute('aria-invalid'); });

    var fuori = [];
    problemi.forEach(function (problema) {
      var nodo = nodoErrore(problema.campo);
      if (!nodo) { fuori.push(problema); return; }
      nodo.textContent = problema.motivo;
      nodo.classList.remove('nascosto');
      var vicino = nodo.parentNode.querySelector('input, select, textarea');
      if (vicino) vicino.setAttribute('aria-invalid', 'true');
    });
    return fuori;
  }

  function costruisciTemi() {
    var contenitore = SK.svuota(SK.$('#temi'));
    SK.TEMI.forEach(function (tema) {
      var input = SK.el('input', { type: 'radio', name: 'tema', value: tema.id, id: 'tema-' + tema.id });
      input.addEventListener('change', function () {
        costruisciSfondi(tema.id, null);
        sporco = true;
      });
      contenitore.appendChild(SK.el('label', { classe: 'interruttore', for: 'tema-' + tema.id }, [
        input,
        SK.el('span', {}, [tema.nome, SK.el('span', { classe: 'aiuto', testo: tema.aiuto })])
      ]));
    });
  }

  /* Gli sfondi di un tema, e solo quelli: e la regola che il server fa
     rispettare con un 422, qui la si fa rispettare non mostrando il resto. */
  function costruisciSfondi(tema, scelto) {
    var contenitore = SK.svuota(SK.$('#sfondi'));
    var disponibili = SK.sfondiDi(tema);
    var valido = disponibili.some(function (s) { return s.id === scelto; });
    var attivo = valido ? scelto : disponibili[0].id;

    disponibili.forEach(function (sfondo) {
      var input = SK.el('input', {
        type: 'radio', name: 'sfondo', value: sfondo.id, id: 'sfondo-' + sfondo.id
      });
      if (sfondo.id === attivo) input.checked = true;
      input.addEventListener('change', function () { sporco = true; });
      contenitore.appendChild(SK.el('label', { for: 'sfondo-' + sfondo.id }, [
        SK.el('span', { classe: 'provino ' + sfondo.id, 'aria-hidden': 'true' }),
        input,
        sfondo.nome
      ]));
    });

    var nodo = nodoErrore('sfondo');
    if (scelto && !valido) {
      nodo.textContent = 'Lo sfondo "' + scelto + '" non appartiene al tema ' + tema +
        ': ho scelto "' + attivo + '". Controlla prima di salvare.';
      nodo.classList.remove('nascosto');
    } else {
      nodo.textContent = '';
      nodo.classList.add('nascosto');
    }
  }

  function costruisciScelte() {
    var schermata = SK.svuota(SK.$('#schermata'));
    SK.SCHERMATE.forEach(function (voce) {
      schermata.appendChild(SK.el('option', { value: voce.id, testo: voce.nome }));
    });
    schermata.addEventListener('change', function () {
      var scelta = SK.SCHERMATE.filter(function (v) { return v.id === schermata.value; })[0];
      SK.$('#aiuto-schermata').textContent = scelta ? scelta.aiuto : '';
    });

    var orientamento = SK.svuota(SK.$('#orientamento'));
    SK.ORIENTAMENTI.forEach(function (voce) {
      orientamento.appendChild(SK.el('option', { value: voce.id, testo: voce.nome }));
    });

    var giorni = SK.svuota(SK.$('#giorni'));
    SK.GIORNI.forEach(function (giorno) {
      giorni.appendChild(SK.el('label', { for: 'giorno-' + giorno.n, title: giorno.nome }, [
        SK.el('input', { type: 'checkbox', id: 'giorno-' + giorno.n, value: String(giorno.n), 'data-giorno': String(giorno.n) }),
        giorno.breve
      ]));
    });

    var giochi = SK.svuota(SK.$('#giochi'));
    SK.GIOCHI.forEach(function (gioco) {
      giochi.appendChild(SK.el('label', { classe: 'casella', for: 'gioco-' + gioco.id }, [
        SK.el('input', { type: 'checkbox', id: 'gioco-' + gioco.id, 'data-gioco': gioco.id }),
        SK.el('span', { testo: gioco.nome })
      ]));
    });
  }

  /* ------------------------------------------------------ regole per app */

  /* Le regole scritte finora, per pacchetto. Vive fuori dal DOM perche
     l'elenco si ridisegna a ogni modifica della casella delle app, e quello
     che il genitore ha gia impostato non deve sparire mentre scrive il nome
     di un'altra app. Tiene anche le regole di app non piu nell'elenco: il
     server le conserva, e rimettendo l'app si ritrova il lavoro fatto. */
  var regole = {};

  function regolaVuota() {
    return { minuti: 0, fascia: null };
  }

  function fasciaIniziale() {
    /* Il pomeriggio: e l'ora che si sceglie quasi sempre, e dare 00:00-00:00
       vorrebbe dire "sempre" - l'interruttore sembrerebbe non funzionare. */
    return { attiva: true, inizio: 16 * 60, fine: 18 * 60, giorni: [1, 2, 3, 4, 5, 6, 7] };
  }

  function appScritte() {
    return SK.$('#app').value.split('\n').map(function (riga) {
      return riga.trim();
    }).filter(function (riga) { return riga.length > 0; });
  }

  /* --------------------------------------------- cosa vale adesso */

  /* La pagina aveva nove riquadri e cinque modi diversi di fermare un
     bambino - chiuso adesso, fascia, minuti, app permesse, kiosk - ognuno
     col suo riquadro e le sue parole. Per sapere cosa poteva fare il figlio
     bisognava leggerli tutti e tenerli a mente insieme.

     Questo li legge e ne fa quattro righe in italiano. Si rifa a ogni
     modifica del modulo, quindi mentre si cambia qualcosa e anche
     l'anteprima di quello che si sta per mandare. */

  function nomiGiorni(giorni) {
    if (!Array.isArray(giorni) || giorni.length === 0) return 'mai';
    if (giorni.length === 7) return 'tutti i giorni';
    var ordinati = giorni.slice().sort(function (a, b) { return a - b; });
    return ordinati.map(function (n) {
      var g = SK.GIORNI.filter(function (v) { return v.n === n; })[0];
      return g ? g.breve || g.nome : n;
    }).join(', ');
  }

  function rigaRiassunto(testo, tono) {
    var riga = SK.el('li', { classe: 'riassunto-riga' + (tono ? ' ' + tono : '') });
    riga.appendChild(document.createTextNode(testo));
    return riga;
  }

  function disegnaRiassunto() {
    var sezione = SK.$('#riassunto');
    if (!base) return;
    var cfg = SK.soloChiaviConfig(raccogli());
    var titolo = SK.$('#riassunto-titolo');
    var righe = SK.svuota(SK.$('#riassunto-righe'));
    sezione.classList.remove('nascosto');

    /* 1. La domanda piu urgente: si puo usare, adesso? */
    sezione.classList.toggle('chiuso', !!cfg.bloccato);
    titolo.textContent = cfg.bloccato
      ? 'Adesso il tablet e chiuso'
      : 'Adesso il tablet si puo usare';

    if (cfg.bloccato) {
      righe.appendChild(rigaRiassunto(
        'Il bambino vede una schermata che dice che lo hai chiuso tu, e non ne ' +
        'esce: il PIN sul tablet non la apre. Tutto il resto qui sotto vale ' +
        'quando lo riapri.', 'forte'));
    }

    /* 2. Quando. */
    var fascia = cfg.fascia || {};
    if (fascia.attiva) {
      righe.appendChild(rigaRiassunto(
        'Si accende dalle ' + SK.minutiInOra(fascia.inizio) + ' alle ' +
        SK.minutiInOra(fascia.fine) + ', ' + nomiGiorni(fascia.giorni) +
        '. Fuori da quelle ore si ferma da solo.'));
    } else {
      righe.appendChild(rigaRiassunto('A qualunque ora del giorno e della notte.'));
    }

    /* 3. Quanto. */
    var minuti = cfg.limite_giornaliero;
    righe.appendChild(rigaRiassunto(
      (Number.isInteger(minuti) && minuti > 0)
        ? 'Al massimo ' + SK.durata(minuti) + ' al giorno, giochi di StarKid compresi.'
        : 'Senza limite di minuti: dentro gli orari puo stare quanto vuole.'));

    /* 4. Cosa puo aprire. */
    var app = Array.isArray(cfg.app_permesse) ? cfg.app_permesse.length : 0;
    var regole = Array.isArray(cfg.regole_app) ? cfg.regole_app.length : 0;
    var cosa = app === 0
      ? 'Non puo aprire nessuna app del tablet: gli restano i giochi di StarKid.'
      : 'Puo aprire ' + app + (app === 1 ? ' app' : ' app') + ' del tablet, piu i giochi di StarKid.';
    if (regole > 0) {
      cosa += ' Di quelle, ' + regole + (regole === 1 ? ' ha' : ' hanno') + ' orari o minuti suoi.';
    }
    righe.appendChild(rigaRiassunto(cosa));

    /* 5. Dove puo stare. */
    if (cfg.kiosk) {
      righe.appendChild(rigaRiassunto(
        cfg.kiosk_recenti
          ? 'Non puo uscire da StarKid, ma la barra in basso resta: da li vede ' +
            'il cassetto con tutte le app del tablet, anche quelle che non si aprono.'
          : 'Non puo uscire da StarKid: niente barra di sistema, niente altre app.'));
    } else {
      righe.appendChild(rigaRiassunto(
        'Puo uscire da StarKid e girare nel tablet: i limiti di tempo valgono ' +
        'lo stesso, ma le altre app le vede.'));
    }

    SK.$('#riassunto-nota').textContent = notaRiassunto();
  }

  /* Da dove viene quello che si sta leggendo: il server, il tablet, o le
     modifiche non ancora mandate. Senza, un riassunto che cambia mentre si
     tocca il modulo sembrerebbe gia in vigore. */
  function notaRiassunto() {
    if (sporco) {
      return 'Stai guardando le modifiche che non hai ancora salvato. ' +
        'Sul tablet vale ancora quello di prima.';
    }
    if (tablet && tablet.allineato) {
      return 'Questo e quello che c\'e sul tablet: l\'ha confermato lui.';
    }
    if (tablet && tablet.revisione_applicata !== null &&
        tablet.revisione_applicata !== undefined && base &&
        tablet.revisione_applicata !== base.revisione) {
      return 'Salvato, ma il tablet non l\'ha ancora preso: lo fa al prossimo ' +
        'collegamento, entro un minuto da quando e acceso e in rete.';
    }
    return 'Salvato sul server.';
  }

  /* ------------------------------------------------- le app del tablet */

  /* Quello che il tablet ha dichiarato di avere installato: null finche non
     si sa, [] se non ha mai mandato niente. La differenza conta, perche le
     due cose vanno dette in due modi diversi. */
  var inventario = null;
  var inventarioIl = null;

  function caricaInventario() {
    return SK.apiSessione('GET', '/api/v1/tablets/' + encodeURIComponent(ID) + '/apps')
      .then(function (risposta) {
        inventario = (risposta && Array.isArray(risposta.app)) ? risposta.app : [];
        inventarioIl = risposta ? risposta.aggiornato_il : null;
      })
      .catch(function () {
        /* Un elenco che non arriva non deve impedire di configurare: resta
           il riquadro per scriverle a mano. */
        inventario = [];
        inventarioIl = null;
      })
      .then(disegnaElencoApp);
  }

  /* La textarea resta la verita: le spunte la scrivono, e da li leggono
     raccogli() e le regole per app. Cosi non ci sono due elenchi da tenere
     d'accordo, che e il modo classico di ritrovarsene uno sbagliato.

     Quello che NON si fa e ridisegnare l'elenco: le caselle ci sono gia e
     sono giuste, e rifarle vorrebbe dire buttare via quella appena toccata
     con dentro il fuoco della tastiera. Chi spunta tre app di fila con Tab
     e barra spaziatrice, dopo la prima si ritroverebbe in cima alla
     pagina. */
  function spunta(pacchetto, acceso) {
    var elenco = appScritte();
    var dentro = elenco.indexOf(pacchetto);
    if (acceso && dentro < 0) elenco.push(pacchetto);
    if (!acceso && dentro >= 0) elenco.splice(dentro, 1);
    SK.$('#app').value = elenco.join('\n');
    sporco = true;
    disegnaRegole();
  }

  function rigaApp(pacchetto, nome, permessa, mancante, icona) {
    var id = 'app-' + pacchetto.replace(/[^a-zA-Z0-9]/g, '_');
    var etichetta = SK.el('label', { classe: 'interruttore' });
    etichetta.setAttribute('for', id);
    var casella = SK.el('input', { id: id });
    casella.type = 'checkbox';
    casella.checked = permessa;
    casella.addEventListener('change', function () { spunta(pacchetto, this.checked); });
    etichetta.appendChild(casella);

    /* L'icona, quando il tablet l'ha mandata. In un elenco di venticinque
       voci e la cosa che si riconosce: "com.google.android.apps.youtube.kids"
       no. L'indirizzo e un data URL scritto dal server, che ha guardato i
       primi byte dell'immagine: qui non si indovina niente. */
    if (icona) {
      var figura = SK.el('img', { classe: 'icona-app', src: icona, alt: '' });
      figura.width = 40;
      figura.height = 40;
      figura.loading = 'lazy';
      etichetta.appendChild(figura);
    }

    var testo = SK.el('span', {});
    /* Il nome lo scrive l'app, non noi: textContent, e sempre col nome del
       pacchetto accanto, cosi si vede di che app si tratta davvero. */
    testo.appendChild(document.createTextNode(nome || pacchetto));
    var sotto = SK.el('span', { classe: 'aiuto' });
    /* Senza nome il titolo e gia il pacchetto: ripeterlo sotto non dice
       niente a nessuno. */
    if (nome) sotto.appendChild(SK.el('span', { classe: 'pacchetto', testo: pacchetto }));
    if (mancante) {
      sotto.appendChild(document.createTextNode(
        (nome ? ' - ' : '') + 'permessa, ma il tablet non la elenca fra le sue app'));
    }
    if (sotto.childNodes.length) testo.appendChild(sotto);
    etichetta.appendChild(testo);
    return etichetta;
  }

  function disegnaElencoApp() {
    var contenitore = SK.svuota(SK.$('#app-elenco'));
    var quando = SK.$('#app-quando');
    var permesse = appScritte();

    if (inventario === null) {
      quando.textContent = 'Sto chiedendo al server quali app ha il tablet...';
      return;
    }

    if (inventario.length === 0) {
      quando.textContent = 'Il tablet non ha ancora mandato l\'elenco delle sue app. ' +
        'Lo manda al prossimo collegamento, e da allora qui ci sono le spunte. ' +
        'Intanto si scrivono a mano qui sotto.';
      SK.$('#app-mano').open = true;
    } else {
      quando.textContent = 'Elenco dichiarato dal tablet' +
        (inventarioIl ? ' ' + SK.quando(inventarioIl) : '') +
        ': ' + inventario.length + (inventario.length === 1 ? ' app.' : ' app.') +
        ' Spunta quelle che il bambino puo aprire.';
    }

    var elencate = [];
    inventario.forEach(function (voce) {
      if (!voce || !voce.pacchetto) return;
      elencate.push(voce.pacchetto);
      contenitore.appendChild(rigaApp(
        voce.pacchetto, voce.nome, permesse.indexOf(voce.pacchetto) >= 0, false, voce.icona));
    });

    /* Permesse ma non installate: un'app tolta dal tablet, o scritta a mano
       prima di installarla. Si vedono lo stesso, se no sparirebbero dalla
       vista pur restando nel documento. */
    permesse.forEach(function (pacchetto) {
      if (elencate.indexOf(pacchetto) >= 0) return;
      contenitore.appendChild(rigaApp(pacchetto, "", true, true, ""));
    });

    if (contenitore.childNodes.length === 0) {
      contenitore.appendChild(SK.el('p', {
        classe: 'piccolo tenue',
        testo: 'Nessuna app spuntata: al bambino restano i giochi di StarKid.'
      }));
    }
  }

  function disegnaRegole() {
    var contenitore = SK.svuota(SK.$('#regole-app'));
    var elenco = appScritte();
    if (elenco.length === 0) {
      contenitore.appendChild(SK.el('p', {
        classe: 'piccolo tenue',
        testo: 'Nessuna app nell’elenco qui sopra: non c’e niente da stringere.'
      }));
      return;
    }
    elenco.forEach(function (pacchetto) {
      contenitore.appendChild(rigaRegola(pacchetto));
    });
  }

  function rigaRegola(pacchetto) {
    var regola = regole[pacchetto] || regolaVuota();
    regole[pacchetto] = regola;

    var minuti = SK.el('input', {
      type: 'number', min: '0', max: '1440', step: '5',
      value: String(regola.minuti || 0)
    });
    minuti.addEventListener('input', function () {
      var valore = Number(minuti.value);
      regola.minuti = isNaN(valore) ? 0 : valore;
      sporco = true;
    });

    var orari = SK.el('div', { classe: regola.fascia ? '' : 'nascosto' });

    var interruttore = SK.el('input', { type: 'checkbox' });
    interruttore.checked = !!regola.fascia;
    interruttore.addEventListener('change', function () {
      regola.fascia = interruttore.checked ? fasciaIniziale() : null;
      orari.classList.toggle('nascosto', !interruttore.checked);
      if (regola.fascia) riempiOrari(orari, regola);
      sporco = true;
    });

    if (regola.fascia) riempiOrari(orari, regola);

    return SK.el('div', { classe: 'carta-interna' }, [
      SK.el('p', { classe: 'pacchetto', testo: pacchetto }),
      SK.el('div', { classe: 'campo' }, [
        SK.el('label', {}, ['Minuti al giorno per questa app', minuti]),
        SK.el('span', { classe: 'aiuto', testo: '0 vuol dire nessun tetto suo: resta solo quello complessivo.' })
      ]),
      SK.el('label', { classe: 'interruttore' }, [
        interruttore,
        SK.el('span', {}, ['Solo in certi orari'])
      ]),
      orari
    ]);
  }

  function riempiOrari(contenitore, regola) {
    SK.svuota(contenitore);
    var fascia = regola.fascia;

    var dalle = SK.el('input', { type: 'time', step: '300', value: SK.minutiInOra(fascia.inizio) });
    dalle.addEventListener('input', function () {
      fascia.inizio = SK.oraInMinuti(dalle.value);
      sporco = true;
    });
    var alle = SK.el('input', { type: 'time', step: '300', value: SK.minutiInOra(fascia.fine) });
    alle.addEventListener('input', function () {
      fascia.fine = SK.oraInMinuti(alle.value);
      sporco = true;
    });

    var giorni = SK.el('div', { classe: 'giorni' });
    SK.GIORNI.forEach(function (giorno) {
      var casella = SK.el('input', { type: 'checkbox', value: String(giorno.n) });
      casella.checked = fascia.giorni.indexOf(giorno.n) >= 0;
      casella.addEventListener('change', function () {
        var dentro = fascia.giorni.indexOf(giorno.n);
        if (casella.checked && dentro < 0) fascia.giorni.push(giorno.n);
        if (!casella.checked && dentro >= 0) fascia.giorni.splice(dentro, 1);
        fascia.giorni.sort(function (a, b) { return a - b; });
        sporco = true;
      });
      giorni.appendChild(SK.el('label', { title: giorno.nome }, [casella, giorno.breve]));
    });

    contenitore.appendChild(SK.el('div', { classe: 'due-colonne' }, [
      SK.el('div', { classe: 'campo' }, [SK.el('label', {}, ['Dalle', dalle])]),
      SK.el('div', { classe: 'campo' }, [SK.el('label', {}, ['Alle', alle])])
    ]));
    contenitore.appendChild(SK.el('div', { classe: 'campo' }, [
      SK.el('label', {}, ['Giorni']),
      giorni,
      SK.el('span', {
        classe: 'aiuto',
        testo: 'Alle prima di dalle vuol dire che la fascia scavalca la mezzanotte.'
      })
    ]));
  }

  function raccogliRegole() {
    /* Si mandano solo le regole che dicono qualcosa. Una riga con zero
       minuti e nessuna fascia e come non averla, e riempire il documento di
       righe vuote renderebbe lo storico illeggibile. */
    var fuori = [];
    Object.keys(regole).forEach(function (pacchetto) {
      var regola = regole[pacchetto];
      var minuti = regola.minuti || 0;
      if (minuti <= 0 && !regola.fascia) return;
      var voce = { pacchetto: pacchetto, minuti: minuti };
      if (regola.fascia) {
        voce.fascia = {
          attiva: true,
          inizio: regola.fascia.inizio,
          fine: regola.fascia.fine,
          giorni: regola.fascia.giorni.slice()
        };
      }
      fuori.push(voce);
    });
    fuori.sort(function (a, b) { return a.pacchetto < b.pacchetto ? -1 : 1; });
    return fuori;
  }

  /* --------------------------------------------- dal documento al modulo */

  function applica(cfg) {
    var tema = SK.TEMI.some(function (t) { return t.id === cfg.tema; }) ? cfg.tema : 'pulito';
    var radioTema = document.getElementById('tema-' + tema);
    if (radioTema) radioTema.checked = true;
    costruisciSfondi(tema, cfg.sfondo);

    SK.$('#schermata').value = cfg.schermata || 'categorie';
    var scelta = SK.SCHERMATE.filter(function (v) { return v.id === SK.$('#schermata').value; })[0];
    SK.$('#aiuto-schermata').textContent = scelta ? scelta.aiuto : '';
    SK.$('#orientamento').value = cfg.orientamento || 'automatico';

    SK.$('#limite').value = Number(cfg.limite_giornaliero);
    aggiornaAiutoLimite();

    var fascia = cfg.fascia || {};
    SK.$('#fascia-attiva').checked = !!fascia.attiva;
    SK.$('#fascia-inizio').value = SK.minutiInOra(fascia.inizio === undefined ? 480 : fascia.inizio);
    SK.$('#fascia-fine').value = SK.minutiInOra(fascia.fine === undefined ? 1200 : fascia.fine);
    var giorni = Array.isArray(fascia.giorni) ? fascia.giorni : [];
    SK.GIORNI.forEach(function (giorno) {
      document.getElementById('giorno-' + giorno.n).checked = giorni.indexOf(giorno.n) >= 0;
    });
    aggiornaFascia();

    SK.$('#kiosk').checked = !!cfg.kiosk;
    SK.$('#kiosk_recenti').checked = !!cfg.kiosk_recenti;
    SK.$('#bloccato').checked = !!cfg.bloccato;
    SK.$('#suoni').checked = cfg.suoni !== false;

    var spenti = Array.isArray(cfg.giochi_spenti) ? cfg.giochi_spenti : [];
    SK.GIOCHI.forEach(function (gioco) {
      document.getElementById('gioco-' + gioco.id).checked = spenti.indexOf(gioco.id) < 0;
    });

    SK.$('#app').value = (Array.isArray(cfg.app_permesse) ? cfg.app_permesse : []).join('\n');

    raccontaPin(cfg);

    disegnaElencoApp();

    regole = {};
    (Array.isArray(cfg.regole_app) ? cfg.regole_app : []).forEach(function (voce) {
      if (!voce || !voce.pacchetto) return;
      var fascia = voce.fascia && voce.fascia.attiva ? {
        attiva: true,
        inizio: voce.fascia.inizio,
        fine: voce.fascia.fine,
        giorni: Array.isArray(voce.fascia.giorni) ? voce.fascia.giorni.slice() : [1, 2, 3, 4, 5, 6, 7]
      } : null;
      regole[voce.pacchetto] = { minuti: voce.minuti || 0, fascia: fascia };
    });
    disegnaRegole();

    segnaErrori([]);
    sporco = false;
    disegnaRiassunto();
  }

  function raccogli() {
    var temaScelto = (document.querySelector('input[name=tema]:checked') || {}).value || 'pulito';
    var sfondoScelto = (document.querySelector('input[name=sfondo]:checked') || {}).value || '';

    var giorni = [];
    SK.GIORNI.forEach(function (giorno) {
      if (document.getElementById('giorno-' + giorno.n).checked) giorni.push(giorno.n);
    });

    var spenti = [];
    SK.GIOCHI.forEach(function (gioco) {
      if (!document.getElementById('gioco-' + gioco.id).checked) spenti.push(gioco.id);
    });

    var app = SK.$('#app').value.split('\n').map(function (riga) {
      return riga.trim();
    }).filter(function (riga) { return riga.length > 0; });

    var limiteTesto = SK.$('#limite').value.trim();
    var limite = limiteTesto === '' ? NaN : Number(limiteTesto);

    return {
      /* La revisione che si manda e quella da cui siamo partiti: il server
         pubblica la successiva e, se nel frattempo e cambiata, risponde 409
         invece di sovrascrivere il lavoro di qualcun altro. */
      revisione: base ? base.revisione : 0,
      tema: temaScelto,
      sfondo: sfondoScelto,
      schermata: SK.$('#schermata').value,
      orientamento: SK.$('#orientamento').value,
      kiosk: SK.$('#kiosk').checked,
      kiosk_recenti: SK.$('#kiosk_recenti').checked,
      bloccato: SK.$('#bloccato').checked,
      /* Si passa il numero com'e, anche se e NaN o con la virgola: ci pensa
         validaConfig a dirlo con parole umane invece di correggerlo di
         nascosto. */
      limite_giornaliero: limite,
      fascia: {
        attiva: SK.$('#fascia-attiva').checked,
        inizio: SK.oraInMinuti(SK.$('#fascia-inizio').value),
        fine: SK.oraInMinuti(SK.$('#fascia-fine').value),
        giorni: giorni
      },
      /* Quello che c'e adesso. Il PIN nuovo, che va calcolato e quindi
         arriva dopo, lo mette salva(): cosi raccogli() resta immediata e
         "scarica" continua a funzionare senza aspettare niente. */
      impronta_pin: base ? (base.impronta_pin || null) : null,
      app_permesse: app,
      regole_app: raccogliRegole(),
      giochi_spenti: spenti,
      suoni: SK.$('#suoni').checked
    };
  }

  /* ---------------------------------------------------------------- PIN */

  /* Cosa dire del PIN, guardando quello che c'e nel documento. Le cifre non
     si possono mostrare: qui c'e solo l'impronta, e nemmeno il server sa a
     quale PIN corrisponda. */
  function raccontaPin(cfg) {
    var stato = SK.$('#pin-stato');
    if (cfg && cfg.impronta_pin) {
      stato.textContent = 'Un PIN mandato da qui c\'e gia. Qual e non si puo ' +
        'rileggere, nemmeno da questa pagina: scrivine uno nuovo per cambiarlo.';
    } else {
      stato.textContent = 'Da qui non e mai stato mandato un PIN: sul tablet ' +
        'vale quello scelto davanti al tablet.';
    }
    SK.$('#pin').value = '';
    SK.$('#pin-ripeti').value = '';
    SK.$('#pin-togli').checked = false;
  }

  /* Il PIN del modulo, risolto in quello che va messo nel documento.
     Rifiuta con { campo, motivo }, come i problemi di validaConfig, cosi
     l'errore si accende sul campo giusto invece che in cima alla pagina. */
  function pinPerIlDocumento() {
    var scritto = SK.$('#pin').value.trim();
    var ripetuto = SK.$('#pin-ripeti').value.trim();
    var togli = SK.$('#pin-togli').checked;
    var corrente = base ? (base.impronta_pin || null) : null;

    if (togli && scritto) {
      return Promise.reject({
        campo: 'impronta_pin',
        motivo: 'o mandi un PIN nuovo o smetti di mandarlo: tutte e due insieme no'
      });
    }
    if (togli) return Promise.resolve(null);
    if (!scritto && !ripetuto) return Promise.resolve(corrente);
    if (!SK.pinValido(scritto)) {
      return Promise.reject({
        campo: 'impronta_pin',
        motivo: 'il PIN sono da ' + SK.PIN_CIFRE_MIN + ' a ' + SK.PIN_CIFRE_MAX +
          ' cifre, niente altro'
      });
    }
    if (scritto !== ripetuto) {
      return Promise.reject({ campo: 'pin-ripeti', motivo: 'le due volte non coincidono' });
    }
    return SK.preparaPin(scritto).catch(function (errore) {
      return Promise.reject({ campo: 'impronta_pin', motivo: errore.message });
    });
  }

  function aggiornaAiutoLimite() {
    var minuti = Number(SK.$('#limite').value);
    SK.$('#aiuto-limite').textContent = (isNaN(minuti) ? '' : SK.durata(minuti) + ' al giorno. ') +
      '0 significa nessun limite. Il tempo dei giochi di StarKid conta come quello delle altre app.';
  }

  function aggiornaFascia() {
    var attiva = SK.$('#fascia-attiva').checked;
    SK.$('#fascia-dettagli').classList.toggle('nascosto', !attiva);
  }

  /* ------------------------------------------------------------ salvare */

  function salva(evento) {
    evento.preventDefault();
    var bottone = SK.$('#salva');
    var messaggio = SK.$('#messaggio');

    /* Il PIN va calcolato prima, e il calcolo e lento apposta: ventimila
       giri di SHA-256 sono qualche decimo di secondo. Da qui in poi tutto
       il salvataggio sta dentro la promessa. */
    SK.occupato(bottone, true, 'Salvo...');
    pinPerIlDocumento().then(
      function (impronta) { pubblica(bottone, messaggio, impronta); },
      function (problema) {
        SK.occupato(bottone, false);
        segnaErrori([problema]);
        SK.mostraErrore(messaggio, { messaggio: 'Non ho salvato: ' + problema.motivo + '.' },
          'Controlla prima di salvare');
        var primo = SK.$('[aria-invalid="true"]');
        if (primo) primo.focus();
      }
    );
  }

  function pubblica(bottone, messaggio, impronta) {
    /* Prima che applica() rifaccia base: serve solo per dire, alla fine, se
       il PIN e cambiato davvero o se e lo stesso di prima. */
    var precedente = base ? (base.impronta_pin || null) : null;
    var grezzo = raccogli();
    grezzo.impronta_pin = impronta;
    var documento = SK.soloChiaviConfig(grezzo);
    var problemi = SK.validaConfig(documento);
    var fuori = segnaErrori(problemi);

    if (problemi.length) {
      SK.occupato(bottone, false);
      SK.mostraErrore(messaggio, {
        messaggio: 'Non ho salvato: ci sono ' + problemi.length + (problemi.length === 1 ? ' campo' : ' campi') +
          ' da correggere. I campi sbagliati sono segnati in rosso qui sotto.',
        campi: fuori
      }, 'Controlla prima di salvare');
      var primo = SK.$('[aria-invalid="true"]');
      if (primo) primo.focus();
      return;
    }

    /* Se il tablet ha detto di non essere Device Owner, accendere il kiosk e
       una richiesta che verra rifiutata: meglio dirlo prima. */
    if (documento.kiosk && base && !base.kiosk && tablet && tablet.salute && tablet.salute.device_owner === false) {
      var avanti = window.confirm(
        'Questo tablet ha detto di non essere Device Owner.\n\n' +
        'Il kiosk gli verra chiesto, ma lo rifiutera: te lo ritroverai scritto fra i campi rifiutati.\n' +
        'Il Device Owner si ottiene solo riportando il tablet alle condizioni di fabbrica e rifacendo l\'arruolamento col QR.\n\n' +
        'Salvo comunque il resto?'
      );
      if (!avanti) { SK.occupato(bottone, false); return; }
    }

    /* Non SK.occupato un'altra volta: il bottone e gia occupato da salva(),
       e la seconda chiamata si porterebbe via il testo originale. */
    bottone.textContent = 'Pubblico...';
    SK.apiSessione('PUT', '/api/v1/tablets/' + encodeURIComponent(ID) + '/config', documento)
      .then(function (risposta) {
        SK.occupato(bottone, false);
        /* Si parte da quello che si e mandato e si lascia vincere quello che
           risponde il server: se risponde solo con il numero di revisione, il
           modulo non deve svuotarsi. */
        var nuova = (risposta && typeof risposta === 'object') ? risposta : {};
        base = Object.assign({}, documento, { revisione: documento.revisione + 1 }, nuova);
        applica(base);
        aggiornaSottotitolo();
        var testo = 'Pubblicata la revisione ' + base.revisione +
          '. Il tablet la prende al prossimo collegamento e poi dice se l\'ha applicata.';
        if (documento.kiosk) {
          testo += ' Il kiosk e stato CHIESTO: lo accende solo se e Device Owner.';
        }
        if (impronta && (!precedente || precedente.impronta !== impronta.impronta)) {
          testo += ' Il PIN nuovo vale da quando il tablet riceve questa revisione: ' +
            'da li in poi quello vecchio non apre piu.';
        }
        SK.mostraMessaggio(SK.$('#messaggio'), 'ok', 'Salvato', testo);
        caricaStorico();
      })
      .catch(function (errore) {
        SK.occupato(bottone, false);
        var fuoriServer = segnaErrori(errore.campi || []);
        SK.mostraErrore(SK.$('#messaggio'), {
          messaggio: errore.messaggio,
          campi: fuoriServer
        }, errore.stato === 422 ? 'Il server ha rifiutato la configurazione' : 'Non ho potuto salvare');
      });
  }

  function scarica() {
    var documento = SK.soloChiaviConfig(raccogli());
    var nome = SK.nomeFileSicuro('starkid-' + (tablet ? tablet.nome : ID) + '-rev' + documento.revisione, '.json');
    SK.scarica(nome, JSON.stringify(documento, null, 2));
  }

  /* ------------------------------------------------------------ storico */

  function caricaStorico() {
    var contenitore = SK.$('#storico');
    SK.apiSessione('GET', '/api/v1/tablets/' + encodeURIComponent(ID) + '/history')
      .then(function (dati) {
        var voci = Array.isArray(dati) ? dati : (dati.revisioni || dati.storico || []);
        SK.svuota(contenitore);
        if (!voci.length) {
          contenitore.appendChild(SK.el('p', { classe: 'tenue', testo: 'Nessuna revisione precedente: questa e la prima.' }));
          return;
        }
        var lista = SK.el('ul', { classe: 'storico' });
        voci.slice(0, 10).forEach(function (voce) {
          var numero = Number(voce.revisione !== undefined ? voce.revisione : voce.rev);
          var corrente = base && numero === base.revisione;
          var riga = SK.el('li', {}, [
            SK.el('span', { classe: 'rev', testo: 'rev ' + numero }),
            SK.el('span', {
              classe: 'quando',
              testo: voce.quando || voce.data ? SK.dataEOra(voce.quando || voce.data) : 'data non detta'
            })
          ]);
          if (corrente) {
            riga.appendChild(SK.el('span', { classe: 'etichetta ok', testo: 'in uso' }));
          } else {
            var torna = SK.el('button', { type: 'button', classe: 'bottone secondario piccolo', testo: 'Torna a questa' });
            torna.addEventListener('click', function () { rimetti(numero, torna); });
            riga.appendChild(torna);
          }
          var contenuto = voce.configurazione || voce.config || voce.documento;
          if (contenuto) {
            riga.appendChild(SK.el('details', {}, [
              SK.el('summary', { classe: 'piccolo', testo: 'Com\'era' }),
              SK.el('pre', { testo: JSON.stringify(contenuto, null, 2) })
            ]));
          }
          lista.appendChild(riga);
        });
        contenitore.appendChild(lista);
      })
      .catch(function (errore) {
        SK.svuota(contenitore);
        contenitore.appendChild(SK.avviso('attesa', 'Storico non disponibile', errore.messaggio));
      });
  }

  function rimetti(revisione, bottone) {
    if (!window.confirm(
      'Tornare alla revisione ' + revisione + '?\n\n' +
      'Non cancella niente: pubblica una revisione nuova con lo stesso contenuto di quella.\n' +
      'Il tablet la prende al prossimo collegamento.'
    )) return;
    SK.occupato(bottone, true, 'Torno...');
    SK.apiSessione('POST', '/api/v1/tablets/' + encodeURIComponent(ID) + '/revert', { revisione: revisione })
      .then(function () {
        /* Prima si ricarica (carica() pulisce i messaggi), poi si scrive
           l'esito: al contrario sparirebbe subito. */
        return carica().then(function () {
          SK.mostraMessaggio(SK.$('#messaggio'), 'ok', 'Fatto',
            'Ho pubblicato una revisione nuova con il contenuto della ' + revisione +
            '. Il tablet la prende al prossimo collegamento.');
        });
      })
      .catch(function (errore) {
        SK.occupato(bottone, false);
        SK.mostraErrore(SK.$('#messaggio'), errore, 'Non ho potuto tornare indietro');
      });
  }

  /* ------------------------------------------------------------- avvio */

  function aggiornaSottotitolo() {
    var pezzi = [];
    if (base) pezzi.push('revisione ' + base.revisione);
    if (tablet) {
      if (tablet.revisione_applicata !== null && tablet.revisione_applicata !== undefined) {
        pezzi.push('sul tablet c\'e la ' + tablet.revisione_applicata);
      }
      pezzi.push('visto ' + SK.quando(tablet.ultimo_contatto));
    }
    SK.$('#sottotitolo').textContent = pezzi.join(' - ');
  }

  function carica() {
    SK.pulisci(SK.$('#messaggio'));

    /* L'elenco serve per il nome e per le spie: il documento di
       configurazione non le contiene, e giusto cosi (sono due cose diverse).
       Se l'elenco non arriva, la configurazione si modifica comunque. */
    var elenco = SK.apiSessione('GET', '/api/v1/tablets').catch(function () { return null; });
    var configurazione = SK.apiSessione('GET', '/api/v1/tablets/' + encodeURIComponent(ID) + '/config');
    /* Parte insieme alle altre due e non aspetta nessuno: se le app del
       tablet non arrivano, la configurazione si modifica lo stesso. */
    var app = caricaInventario();

    return Promise.all([elenco, configurazione, app]).then(function (risultati) {
      var dati = risultati[0];
      if (dati) {
        var grezzi = Array.isArray(dati) ? dati : (dati.tablets || dati.dispositivi || []);
        var trovato = grezzi.map(SK.normalizzaTablet).filter(function (t) { return t.id === ID; })[0];
        if (trovato) {
          tablet = trovato;
          SK.$('#titolo').textContent = trovato.nome;
          document.title = trovato.nome + ' - StarKid';
          var cartello = SK.cartelloLimiti(trovato);
          var contenitore = SK.svuota(SK.$('#cartello'));
          if (cartello) contenitore.appendChild(cartello);
          contenitore.appendChild(SK.listaSpie(trovato));
        }
      }

      var cfg = risultati[1];
      base = Object.assign(SK.configVuota(), cfg || {});
      applica(base);
      aggiornaSottotitolo();
      SK.$('#modulo').classList.remove('nascosto');
      caricaStorico();
    }).catch(function (errore) {
      SK.$('#sottotitolo').textContent = 'Configurazione non disponibile.';
      SK.mostraErrore(SK.$('#messaggio'), errore,
        errore.stato === 404 ? 'Questo tablet non c\'e piu' : 'Non ho potuto leggere la configurazione');
    });
  }

  document.addEventListener('DOMContentLoaded', function () {
    if (!ID) { window.location.replace('tablets.html'); return; }

    costruisciTemi();
    costruisciScelte();
    costruisciSfondi('pulito', 'lilla');

    SK.$('#modulo').addEventListener('submit', salva);
    /* Il riassunto in cima segue il modulo: mentre si cambia qualcosa dice
       cosa si sta per mandare, e la nota sotto dice che non e ancora
       salvato. */
    SK.$("#modulo").addEventListener("input", function () { sporco = true; disegnaRiassunto(); });
    SK.$("#modulo").addEventListener("change", function () { sporco = true; disegnaRiassunto(); });
    SK.$('#limite').addEventListener('input', aggiornaAiutoLimite);
    SK.$('#fascia-attiva').addEventListener('change', aggiornaFascia);
    /* L'elenco delle regole segue quello delle app: si ridisegna appena si
       smette di scrivere, non a ogni tasto, o una riga a meta farebbe
       comparire e sparire righe mentre si digita. */
    /* Scrivendo a mano si rifanno tutte e due: le spunte qui sopra e le
       regole per app guardano lo stesso elenco. */
    SK.$('#app').addEventListener('change', function () { disegnaElencoApp(); disegnaRegole(); });
    SK.$('#app').addEventListener('blur', function () { disegnaElencoApp(); disegnaRegole(); });
    SK.$('#scarica').addEventListener('click', scarica);
    SK.$('#annulla').addEventListener('click', function () {
      if (!base) return;
      applica(base);
      SK.mostraMessaggio(SK.$('#messaggio'), '', 'Modifiche annullate',
        'Il modulo e tornato alla revisione ' + base.revisione + ', quella che c\'e sul server.');
    });

    window.addEventListener('beforeunload', function (evento) {
      if (!sporco) return;
      evento.preventDefault();
      evento.returnValue = '';
    });

    carica();
  });
})();
