/* tablets.html - elenco dei tablet.
 *
 * Questa pagina ha un compito preciso: se su un tablet i limiti di tempo non
 * stanno funzionando, il genitore lo deve capire entrando qui, senza aprire
 * niente. Le spie stanno sulla scheda, e sopra c'e il cartello.
 */

'use strict';

(function () {

  /* Quanto puo stare zitto un tablet prima di preoccuparsi. Il tablet si fa
     vivo da solo: se tace, o e spento o non ha rete - oppure qualcuno ha
     disinstallato StarKid. */
  var ORE_SOSPETTE = 24;
  var ORE_ALLARME = 72;

  function statoDi(tablet) {
    if (!SK.arruolato(tablet) && !tablet.ultimo_contatto) {
      return { classe: 'attesa', testo: 'Mai arrivato' };
    }
    var quando = SK.data(tablet.ultimo_contatto);
    if (!quando) return { classe: '', testo: 'Senza notizie' };
    var ore = (Date.now() - quando.getTime()) / 3600000;
    if (ore >= ORE_ALLARME) return { classe: 'guasto', testo: 'Zitto da ' + Math.round(ore / 24) + ' giorni' };
    if (ore >= ORE_SOSPETTE) return { classe: 'attesa', testo: 'Zitto da ' + Math.round(ore) + ' ore' };
    return { classe: 'ok', testo: 'Attivo' };
  }

  function rigaConfig(tablet) {
    var pubblicata = tablet.revisione;
    var applicata = tablet.revisione_applicata;
    if (applicata === null || applicata === undefined) {
      return { testo: 'Nessuna configurazione ancora applicata (pubblicata: revisione ' + pubblicata + ')', classe: 'attesa' };
    }
    if (Number(applicata) < Number(pubblicata)) {
      return {
        testo: 'Il tablet ha la revisione ' + applicata + ', sul server c\'e la ' + pubblicata +
          ': la prendera al prossimo collegamento',
        classe: 'attesa'
      };
    }
    var esito = tablet.esito;
    if (esito === 'rejected') return { testo: 'Revisione ' + applicata + ' RIFIUTATA dal tablet', classe: 'guasto' };
    if (esito === 'partial') return { testo: 'Revisione ' + applicata + ' applicata solo in parte', classe: 'attesa' };
    return { testo: 'Revisione ' + applicata + ' applicata', classe: 'ok' };
  }

  function schedaTablet(tablet, elenco) {
    var stato = statoDi(tablet);
    var config = rigaConfig(tablet);
    var rotte = SK.spieCritiche(tablet);

    var scheda = SK.el('article', { classe: 'tablet' + (rotte.length ? ' allarme' : '') });

    scheda.appendChild(SK.el('h2', {}, [
      SK.el('a', { href: 'tablet.html?id=' + encodeURIComponent(tablet.id), testo: tablet.nome })
    ]));

    var righeStato = [
      SK.el('span', { classe: 'etichetta ' + stato.classe, testo: stato.testo }),
      SK.el('span', { classe: 'piccolo tenue', testo: 'visto ' + SK.quando(tablet.ultimo_contatto) })
    ];
    /* Un tablet chiuso si deve vedere da qui, senza aprirlo: e la cosa che
       si cerca di fretta. "Chiuso da te" e non "chiuso": lo stato del server,
       che il tablet applica quando richiama - e se non l'ha ancora fatto lo
       dice gia la riga della configurazione qui sotto. */
    if (tablet.bloccato) {
      righeStato.splice(1, 0, SK.el('span', { classe: 'etichetta guasto', testo: 'Chiuso da te' }));
    }
    scheda.appendChild(SK.el('div', { classe: 'riga-bottoni' }, righeStato));

    var cartello = SK.cartelloLimiti(tablet);
    if (cartello) scheda.appendChild(cartello);

    var dati = SK.el('dl', { classe: 'dati' });
    dati.appendChild(SK.el('dt', { testo: 'Configurazione' }));
    dati.appendChild(SK.el('dd', {}, [SK.el('span', { classe: 'etichetta lunga ' + config.classe, testo: config.testo })]));
    dati.appendChild(SK.el('dt', { testo: 'Identificativo' }));
    dati.appendChild(SK.el('dd', { classe: 'mono piccolo', testo: tablet.id || '-' }));
    scheda.appendChild(dati);

    /* Quello che il tablet ha rifiutato. E qui che si vede l'asimmetria del
       kiosk: il server puo spegnerlo subito, accenderlo lo puo solo chiedere. */
    if (tablet.campi_rifiutati && tablet.campi_rifiutati.length) {
      var rifiuti = SK.avviso('attesa', 'Il tablet non ha accettato tutto');
      var elencoRifiuti = SK.el('ul');
      tablet.campi_rifiutati.forEach(function (voce) {
        elencoRifiuti.appendChild(SK.el('li', {}, [
          SK.el('strong', { testo: String(voce.campo || 'campo') + ': ' }),
          SK.testoMotivo(voce.motivo)
        ]));
      });
      rifiuti.appendChild(elencoRifiuti);
      scheda.appendChild(rifiuti);
    }

    scheda.appendChild(SK.listaSpie(tablet));

    if (tablet.salute_aggiornata) {
      scheda.appendChild(SK.el('p', {
        classe: 'piccolo tenue',
        testo: 'Spie aggiornate ' + SK.quando(tablet.salute_aggiornata) + ' (' + SK.dataEOra(tablet.salute_aggiornata) + ')'
      }));
    } else {
      scheda.appendChild(SK.el('p', {
        classe: 'piccolo tenue',
        testo: 'Il tablet non ha ancora mandato le sue spie: fino a quel momento non si sa se i limiti funzionano.'
      }));
    }

    var azioni = SK.el('div', { classe: 'riga-bottoni' });
    azioni.appendChild(SK.el('a', {
      classe: 'bottone secondario piccolo',
      href: 'tablet.html?id=' + encodeURIComponent(tablet.id),
      testo: 'Configura'
    }));
    var cancella = SK.el('button', { type: 'button', classe: 'bottone pericolo piccolo', testo: 'Cancella' });
    cancella.addEventListener('click', function () { cancellaTablet(tablet, cancella, elenco); });
    azioni.appendChild(cancella);
    scheda.appendChild(azioni);

    return scheda;
  }

  function cancellaTablet(tablet, bottone, elenco) {
    var conferma = window.confirm(
      'Cancellare "' + tablet.nome + '" dal server?\n\n' +
      'Il tablet smette di ricevere configurazioni e sparisce da questo elenco.\n' +
      'Quello che e gia sul tablet resta com\'e: per rimetterlo a posto serve il tablet in mano.\n' +
      'Per rimetterlo qui dentro servira un nuovo arruolamento con un nuovo QR.'
    );
    if (!conferma) return;
    SK.occupato(bottone, true, 'Cancello...');
    SK.apiSessione('DELETE', '/api/v1/tablets/' + encodeURIComponent(tablet.id))
      .then(function () { carica(); })
      .catch(function (errore) {
        SK.occupato(bottone, false);
        SK.mostraErrore(SK.$('#messaggio'), errore, 'Non ho potuto cancellarlo');
      });
  }

  function riepiloga(tablets) {
    var contenitore = SK.svuota(SK.$('#riepilogo'));
    var guasti = tablets.filter(function (t) { return SK.spieCritiche(t).length > 0; });
    if (guasti.length === 0) return;
    var avviso = SK.avviso('guasto', guasti.length === 1
      ? 'Su un tablet i limiti di tempo non stanno funzionando'
      : 'Su ' + guasti.length + ' tablet i limiti di tempo non stanno funzionando');
    var elenco = SK.el('ul');
    guasti.forEach(function (t) {
      elenco.appendChild(SK.el('li', {}, [
        SK.el('a', { href: 'tablet.html?id=' + encodeURIComponent(t.id), testo: t.nome }),
        ' - manca: ' + SK.spieCritiche(t).map(function (s) { return s.nome; }).join(', ')
      ]));
    });
    avviso.appendChild(elenco);
    avviso.appendChild(SK.el('p', {
      classe: 'piccolo',
      testo: 'Si sistema sul tablet, nelle impostazioni di Android: il dettaglio di cosa toccare e sotto, su ogni scheda.'
    }));
    contenitore.appendChild(avviso);
  }

  function disegna(tablets) {
    var elenco = SK.svuota(SK.$('#elenco'));
    var sottotitolo = SK.$('#sottotitolo');

    if (tablets.length === 0) {
      sottotitolo.textContent = 'Nessun tablet, per ora.';
      var vuoto = SK.el('div', { classe: 'carta centrato' }, [
        SK.el('h2', { testo: 'Qui non c\'e ancora niente' }),
        SK.el('p', { testo: 'Aggiungi il primo tablet: il server ti da un codice di arruolamento e la pagina ti disegna il QR da inquadrare col tablet appena riportato alle condizioni di fabbrica.' }),
        SK.el('a', { classe: 'bottone', href: 'nuovo.html', testo: 'Aggiungi un tablet' })
      ]);
      elenco.appendChild(vuoto);
      return;
    }

    sottotitolo.textContent = tablets.length === 1 ? 'Un tablet.' : tablets.length + ' tablet.';
    var griglia = SK.el('div', { classe: 'griglia' });
    tablets.forEach(function (t) { griglia.appendChild(schedaTablet(t, elenco)); });
    elenco.appendChild(griglia);
  }

  function carica() {
    SK.pulisci(SK.$('#messaggio'));
    SK.apiSessione('GET', '/api/v1/tablets')
      .then(function (dati) {
        var grezzi = Array.isArray(dati) ? dati : (dati.tablets || dati.dispositivi || []);
        var tablets = grezzi.map(SK.normalizzaTablet);
        tablets.sort(function (a, b) {
          /* Prima quelli che hanno un problema, poi per nome: chi apre la
             pagina deve trovarsi davanti la cosa da sistemare. */
          var pa = SK.spieCritiche(a).length > 0 ? 0 : 1;
          var pb = SK.spieCritiche(b).length > 0 ? 0 : 1;
          if (pa !== pb) return pa - pb;
          return a.nome.localeCompare(b.nome, 'it');
        });
        riepiloga(tablets);
        disegna(tablets);
      })
      .catch(function (errore) {
        SK.svuota(SK.$('#elenco'));
        SK.$('#sottotitolo').textContent = 'Elenco non disponibile.';
        SK.mostraErrore(SK.$('#messaggio'), errore, 'Non ho potuto leggere l\'elenco');
      });
  }

  document.addEventListener('DOMContentLoaded', function () {
    SK.$('#ricarica').addEventListener('click', carica);
    carica();
  });
})();
