/* utenti.html - chi puo entrare, e cosa vede.
 *
 * Tutte le chiamate di questa pagina rispondono 403 a chi non e
 * amministratore: la pagina non e una difesa, e il modo comodo di usarne
 * una che sta nel server.
 *
 * Due cose che si vedono nel codice qui sotto e vale la pena dire:
 *
 *  - le concessioni si mandano INTERE ("ecco i tablet che vede"), mai a
 *    pezzi. Due richieste "aggiungi"/"togli" che si incrociano lasciano
 *    permessi che nessuno ha chiesto, ed e esattamente il genere di errore
 *    che nessuno si accorge di avere.
 *  - i bottoni che il server rifiuterebbe comunque qui non si disegnano
 *    proprio: disattivare se stessi, togliersi il ruolo quando si e
 *    l'ultimo amministratore. Il server dice di no lo stesso, con un 409 e
 *    una frase; questo serve a non far provare.
 */
(function () {

  var RUOLI = {
    admin: 'Amministratore',
    genitore: 'Genitore',
    osservatore: 'Osservatore'
  };

  var SPIEGA = {
    admin: 'Vede tutti i tablet e gestisce gli account.',
    genitore: 'Configura i tablet che gli hai dato.',
    osservatore: 'Guarda e basta: non puo cambiare niente.'
  };

  var io = null;        /* chi sono: lo chiede app.js, qui serve l'id */
  var utenti = [];
  var tablet = [];

  /* ------------------------------------------------------------- elenco */

  function scheda(u) {
    var suo = io && Number(u.id) === Number(io.id);
    var art = SK.el('article', { classe: 'carta' });

    var titolo = SK.el('h2', {}, [SK.el('span', { testo: u.utente })]);
    if (suo) titolo.appendChild(SK.el('span', { classe: 'etichetta', testo: 'sei tu' }));
    art.appendChild(titolo);

    var righe = SK.el('div', { classe: 'riga-bottoni' }, [
      SK.el('span', {
        classe: 'etichetta ' + (u.ruolo === 'admin' ? 'ok' : ''),
        testo: RUOLI[u.ruolo] || u.ruolo
      }),
      SK.el('span', {
        classe: 'etichetta ' + (u.attivo ? '' : 'guasto'),
        testo: u.attivo ? 'Attivo' : 'Disattivato'
      })
    ]);
    art.appendChild(righe);

    art.appendChild(SK.el('p', { classe: 'piccolo tenue', testo: SPIEGA[u.ruolo] || '' }));

    var dati = SK.el('dl', { classe: 'dati' });
    dati.appendChild(SK.el('dt', { testo: 'Tablet che vede' }));
    dati.appendChild(SK.el('dd', {
      testo: u.ruolo === 'admin'
        ? 'Tutti, perche e amministratore'
        : (u.quanti_tablet === 0 ? 'Nessuno' : u.quanti_tablet + ' su ' + tablet.length)
    }));
    if (u.id_padre) {
      var padre = utenti.filter(function (x) { return Number(x.id) === Number(u.id_padre); })[0];
      dati.appendChild(SK.el('dt', { testo: 'Sottoutente di' }));
      dati.appendChild(SK.el('dd', { testo: padre ? padre.utente : ('#' + u.id_padre) }));
    }
    dati.appendChild(SK.el('dt', { testo: 'Creato' }));
    dati.appendChild(SK.el('dd', { testo: SK.quando(u.creato_il) }));
    art.appendChild(dati);

    art.appendChild(bottoni(u, suo));
    return art;
  }

  /* L'ultimo amministratore attivo: il server rifiuta di toccarlo, e qui non
     si disegnano nemmeno i bottoni che proverebbero. */
  function ultimoAdmin(u) {
    if (u.ruolo !== 'admin' || !u.attivo) return false;
    return utenti.filter(function (x) { return x.ruolo === 'admin' && x.attivo; }).length <= 1;
  }

  function bottoni(u, suo) {
    var riga = SK.el('div', { classe: 'riga-bottoni' });

    if (u.ruolo !== 'admin') {
      riga.appendChild(bottone('Quali tablet', 'secondario', function () { apriTablet(u); }));
    }
    riga.appendChild(bottone('Cambia password', 'secondario', function () { cambiaPassword(u); }));

    /* Il ruolo: si cambia con un <select>, non con tre bottoni. */
    var scelta = SK.el('select', { 'aria-label': 'Ruolo di ' + u.utente });
    Object.keys(RUOLI).forEach(function (chiave) {
      var opzione = SK.el('option', { value: chiave, testo: RUOLI[chiave] });
      if (chiave === u.ruolo) opzione.selected = true;
      scelta.appendChild(opzione);
    });
    var bloccato = suo || ultimoAdmin(u);
    if (bloccato) {
      scelta.disabled = true;
      scelta.title = suo
        ? 'Non puoi cambiare il ruolo al tuo account'
        : 'E l\'ultimo amministratore attivo';
    }
    scelta.addEventListener('change', function () {
      modifica(u, { ruolo: scelta.value }, 'Ruolo di ' + u.utente + ' cambiato.');
    });
    riga.appendChild(scelta);

    if (!suo && !ultimoAdmin(u)) {
      riga.appendChild(bottone(
        u.attivo ? 'Disattiva' : 'Riattiva',
        'secondario',
        function () {
          modifica(u, { attivo: !u.attivo },
            u.attivo ? u.utente + ' disattivato: le sue sessioni sono chiuse.'
                     : u.utente + ' riattivato.');
        }
      ));
      riga.appendChild(bottone('Cancella', 'pericolo', function () { cancella(u); }));
    }
    return riga;
  }

  function bottone(testo, classe, azione) {
    var b = SK.el('button', { type: 'button', classe: 'bottone piccolo ' + classe, testo: testo });
    b.addEventListener('click', azione);
    return b;
  }

  /* ------------------------------------------------------------- azioni */

  function modifica(u, cambiamenti, detto) {
    SK.api('PATCH', '/api/v1/utenti/' + encodeURIComponent(u.id), cambiamenti)
      .then(function () {
        SK.mostraMessaggio(SK.$('#messaggio'), 'ok', 'Fatto', detto);
        carica();
      })
      .catch(function (errore) {
        SK.mostraErrore(SK.$('#messaggio'), errore, 'Non ho potuto cambiare l\'account');
        carica();
      });
  }

  function cancella(u) {
    var suoi = utenti.filter(function (x) { return Number(x.id_padre) === Number(u.id); });
    var avviso = 'Cancello l\'account "' + u.utente + '"?';
    if (suoi.length) {
      avviso += '\n\nSpariscono anche i suoi sottoutenti: ' +
        suoi.map(function (x) { return x.utente; }).join(', ') + '.';
    }
    avviso += '\n\nI tablet NON vengono cancellati: sparisce solo il permesso di vederli.';
    if (!window.confirm(avviso)) return;
    SK.api('DELETE', '/api/v1/utenti/' + encodeURIComponent(u.id))
      .then(function () {
        SK.mostraMessaggio(SK.$('#messaggio'), 'ok', 'Cancellato',
          'L\'account "' + u.utente + '" non c\'e piu. I suoi tablet restano.');
        carica();
      })
      .catch(function (errore) {
        SK.mostraErrore(SK.$('#messaggio'), errore, 'Non ho potuto cancellare l\'account');
      });
  }

  function cambiaPassword(u) {
    var nuova = window.prompt(
      'Nuova password per "' + u.utente + '".\n\n' +
      'Almeno 12 caratteri. Le sue sessioni aperte verranno chiuse; quelle ' +
      'degli altri no.'
    );
    if (nuova === null) return;
    SK.api('PUT', '/api/v1/utenti/' + encodeURIComponent(u.id) + '/password', { password: nuova })
      .then(function () {
        SK.mostraMessaggio(SK.$('#messaggio'), 'ok', 'Password cambiata',
          'Consegnala a "' + u.utente + '", e digli di cambiarsela. Le sue sessioni aperte sono chiuse.');
      })
      .catch(function (errore) {
        SK.mostraErrore(SK.$('#messaggio'), errore, 'Non ho potuto cambiare la password');
      });
  }

  /* --------------------------------------------------- quali tablet vede */

  function apriTablet(u) {
    SK.api('GET', '/api/v1/utenti/' + encodeURIComponent(u.id) + '/tablet')
      .then(function (dati) { disegnaSceltaTablet(u, dati.tablet || []); })
      .catch(function (errore) {
        SK.mostraErrore(SK.$('#messaggio'), errore, 'Non ho potuto leggere i suoi tablet');
      });
  }

  function disegnaSceltaTablet(u, scelti) {
    var vecchio = SK.$('#dialogo-tablet');
    if (vecchio) vecchio.remove();

    var caselle = [];
    var lista = SK.el('div', { id: 'lista-tablet' });
    if (!tablet.length) {
      lista.appendChild(SK.el('p', { classe: 'tenue', testo: 'Non c\'e ancora nessun tablet.' }));
    }
    tablet.forEach(function (t) {
      var casella = SK.el('input', { type: 'checkbox', id: 'vede-' + t.id, value: t.id });
      if (scelti.indexOf(t.id) !== -1) casella.checked = true;
      caselle.push(casella);
      var etichetta = SK.el('label', { classe: 'interruttore', for: 'vede-' + t.id }, [
        casella,
        SK.el('span', { testo: t.nome })
      ]);
      lista.appendChild(etichetta);
    });

    var salva = bottone('Salva', '', function () {
      var voluti = caselle.filter(function (c) { return c.checked; })
        .map(function (c) { return c.value; });
      SK.api('PUT', '/api/v1/utenti/' + encodeURIComponent(u.id) + '/tablet', { tablet: voluti })
        .then(function () {
          SK.mostraMessaggio(SK.$('#messaggio'), 'ok', 'Permessi salvati',
            u.utente + ' adesso vede ' + (voluti.length || 'nessun') + ' tablet.');
          riquadro.remove();
          carica();
        })
        .catch(function (errore) {
          SK.mostraErrore(SK.$('#messaggio'), errore, 'Non ho potuto salvare i permessi');
        });
    });

    var riquadro = SK.el('div', { classe: 'carta', id: 'dialogo-tablet' }, [
      SK.el('h2', { testo: 'Quali tablet vede ' + u.utente }),
      SK.el('p', {
        classe: 'piccolo tenue',
        testo: 'Spunta quelli che deve poter aprire. L\'elenco si manda intero: ' +
          'quello che togli qui glielo togli davvero.'
      }),
      lista,
      SK.el('div', { classe: 'riga-bottoni' }, [
        salva,
        bottone('Lascia stare', 'secondario', function () { riquadro.remove(); })
      ])
    ]);
    SK.$('#elenco').insertAdjacentElement('beforebegin', riquadro);
    riquadro.scrollIntoView({ block: 'nearest' });
  }

  /* ------------------------------------------------------- account nuovo */

  function crea(evento) {
    evento.preventDefault();
    var campo = SK.$('#nuovo-utente');
    var errore = SK.$('#errore-nuovo');
    errore.classList.add('nascosto');

    var corpo = {
      utente: campo.value.trim(),
      password: SK.$('#nuova-password').value,
      ruolo: SK.$('#nuovo-ruolo').value
    };
    var padre = SK.$('#nuovo-padre').value;
    if (padre) corpo.id_padre = Number(padre);

    if (!corpo.utente) { mostraErroreCampo('Scrivi un nome utente.'); return; }
    if (corpo.password.length < 12) {
      mostraErroreCampo('La password deve essere lunga almeno 12 caratteri.');
      return;
    }

    SK.occupato(SK.$('#crea'), true);
    SK.api('POST', '/api/v1/utenti', corpo)
      .then(function (nuovo) {
        SK.mostraMessaggio(SK.$('#messaggio'), 'ok', 'Account creato',
          'Consegna a "' + nuovo.utente + '" la password che hai scritto: da qui non si rilegge piu.');
        SK.$('#modulo-nuovo').reset();
        carica();
      })
      .catch(function (e) { mostraErroreCampo(e && e.messaggio ? e.messaggio : 'Non ha funzionato.'); })
      .then(function () { SK.occupato(SK.$('#crea'), false); });
  }

  function mostraErroreCampo(testo) {
    var errore = SK.$('#errore-nuovo');
    errore.textContent = testo;
    errore.classList.remove('nascosto');
  }

  /* -------------------------------------------------------------- avvio */

  function aggiornaPadri() {
    var scelta = SK.$('#nuovo-padre');
    var prima = scelta.value;
    SK.svuota(scelta);
    scelta.appendChild(SK.el('option', { value: '', testo: 'Nessuno: account a se' }));
    utenti.forEach(function (u) {
      /* Un sottoutente di un sottoutente complica senza servire: si offrono
         come padri solo gli account che non lo sono gia. */
      if (u.id_padre) return;
      scelta.appendChild(SK.el('option', { value: String(u.id), testo: u.utente }));
    });
    scelta.value = prima;
  }

  function carica() {
    return Promise.all([
      SK.api('GET', '/api/v1/utenti'),
      /* L'elenco dei tablet serve ai permessi. Un amministratore li vede
         tutti, ed e sempre un amministratore ad aprire questa pagina. */
      SK.api('GET', '/api/v1/tablets')
    ]).then(function (risposte) {
      utenti = risposte[0].utenti || [];
      tablet = (risposte[1].tablets || []).map(SK.normalizzaTablet);
      SK.$('#sottotitolo').textContent = utenti.length === 1
        ? 'Un account.'
        : utenti.length + ' account.';
      var elenco = SK.svuota(SK.$('#elenco'));
      var griglia = SK.el('div', { classe: 'griglia' });
      utenti.forEach(function (u) { griglia.appendChild(scheda(u)); });
      elenco.appendChild(griglia);
      aggiornaPadri();
    }).catch(function (errore) {
      SK.svuota(SK.$('#elenco'));
      SK.$('#sottotitolo').textContent = 'Elenco non disponibile.';
      SK.mostraErrore(SK.$('#messaggio'), errore, 'Non ho potuto leggere gli account');
    });
  }

  document.addEventListener('DOMContentLoaded', function () {
    SK.$('#ricarica').addEventListener('click', carica);
    SK.$('#modulo-nuovo').addEventListener('submit', crea);
    SK.chiSono()
      .then(function (chi) {
        /* Chi non e amministratore prenderebbe 403 da tutto: non si chiede
           nemmeno. Senza questa riga la pagina mostrava due messaggi per la
           stessa cosa - l'errore della chiamata e la spiegazione - e il
           primo sembrava un guasto. */
        if (!chi.puoAmministrare) {
          SK.$('#sottotitolo').textContent = 'Serve un account amministratore.';
          SK.svuota(SK.$('#elenco'));
          return;
        }
        /* L'id non arriva da /auth/io: si trova nell'elenco, che un
           amministratore puo leggere. Serve a riconoscere "sei tu". */
        return carica().then(function () {
          var mio = utenti.filter(function (u) { return u.utente === chi.utente; })[0];
          io = { id: mio ? mio.id : null, utente: chi.utente, ruolo: chi.ruolo };
          /* Ridisegna, adesso che sa chi e: senza, al primo giro mancano le
             etichette "sei tu" e i bottoni bloccati. */
          var elenco = SK.svuota(SK.$('#elenco'));
          var griglia = SK.el('div', { classe: 'griglia' });
          utenti.forEach(function (u) { griglia.appendChild(scheda(u)); });
          elenco.appendChild(griglia);
        });
      })
      .catch(function (errore) {
        SK.mostraErrore(SK.$('#messaggio'), errore, 'Non ho potuto leggere chi sei');
      });
  });
})();
