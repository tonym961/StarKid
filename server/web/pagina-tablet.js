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
      app_permesse: app,
      regole_app: raccogliRegole(),
      giochi_spenti: spenti,
      suoni: SK.$('#suoni').checked
    };
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
    var documento = SK.soloChiaviConfig(raccogli());
    var problemi = SK.validaConfig(documento);
    var fuori = segnaErrori(problemi);

    if (problemi.length) {
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
      if (!avanti) return;
    }

    SK.occupato(bottone, true, 'Pubblico...');
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

    return Promise.all([elenco, configurazione]).then(function (risultati) {
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
    SK.$('#modulo').addEventListener('input', function () { sporco = true; });
    SK.$('#modulo').addEventListener('change', function () { sporco = true; });
    SK.$('#limite').addEventListener('input', aggiornaAiutoLimite);
    SK.$('#fascia-attiva').addEventListener('change', aggiornaFascia);
    /* L'elenco delle regole segue quello delle app: si ridisegna appena si
       smette di scrivere, non a ogni tasto, o una riga a meta farebbe
       comparire e sparire righe mentre si digita. */
    SK.$('#app').addEventListener('change', disegnaRegole);
    SK.$('#app').addEventListener('blur', disegnaRegole);
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
