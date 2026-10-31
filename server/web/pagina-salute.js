/* salute.html - pannello del servizio.
 *
 * Tre fonti, tenute separate di proposito:
 *
 *  - GET /healthz          senza sessione. E la porta che guarda un monitor
 *                          esterno, quindi dice solo "sono in piedi". Qui si
 *                          usa per distinguere "il servizio e giu" da "la
 *                          sessione e scaduta".
 *  - GET /api/v1/salute    con la sessione: giorni al certificato, ultimo
 *                          backup, dimensione del database. Se il server non
 *                          espone ancora questa parte, la pagina lo dice e
 *                          continua col resto invece di restare vuota.
 *  - GET /api/v1/tablets   con la sessione: da qui si ricavano i tablet che
 *                          non si fanno sentire.
 */

'use strict';

(function () {

  var ORE_SOSPETTE = 24;
  var ORE_ALLARME = 72;

  function numero(id, valore, classe, titolo) {
    var nodo = document.getElementById(id);
    nodo.textContent = valore;
    var scheda = nodo.parentNode;
    scheda.classList.remove('ok', 'attesa', 'guasto');
    if (classe) scheda.classList.add(classe);
    if (titolo) scheda.title = titolo;
  }

  function primo(oggetto, nomi) {
    for (var i = 0; i < nomi.length; i++) {
      if (oggetto && oggetto[nomi[i]] !== undefined && oggetto[nomi[i]] !== null) return oggetto[nomi[i]];
    }
    return undefined;
  }

  /* --------------------------------------------------------- certificato */

  function mostraCertificato(dati) {
    var giorni = primo(dati, ['certificato_giorni', 'giorni_certificato', 'cert_giorni', 'certificato']);
    if (giorni === undefined) { numero('n-certificato', '?', '', 'Il server non lo dice'); return null; }
    var g = Math.round(Number(giorni));
    var classe = g <= 7 ? 'guasto' : (g <= 21 ? 'attesa' : 'ok');
    numero('n-certificato', String(g), classe);
    if (g <= 21) {
      return SK.avviso(classe, 'Il certificato scade fra ' + g + ' giorni',
        'Di solito certbot lo rinnova da solo trenta giorni prima. Se siamo arrivati qui, ' +
        'il rinnovo non e passato: sul server, "certbot renew --dry-run" dice perche. ' +
        'Attenzione: su questa macchina ci sono altri certificati con lineage rotte, e ' +
        'un errore su quelle puo fermare tutto il rinnovo.');
    }
    return null;
  }

  /* -------------------------------------------------------------- backup */

  function mostraBackup(dati) {
    var valore = primo(dati, ['ultimo_backup', 'backup', 'backup_quando']);
    if (valore === undefined) { numero('n-backup', '?', '', 'Il server non lo dice'); return null; }
    var quando = SK.data(valore);
    if (!quando) {
      numero('n-backup', 'mai', 'guasto');
      return SK.avviso('guasto', 'Nessun backup',
        'Il database non e mai stato copiato. Sta tutto in un file: se la macchina si perde, ' +
        'si perdono i tablet e bisogna rifare gli arruolamenti uno per uno. ' +
        'Sul server e un timer di systemd da accendere, non un cron (qui cron non c\'e).');
    }
    var ore = (Date.now() - quando.getTime()) / 3600000;
    var classe = ore > 24 * 7 ? 'guasto' : (ore > 36 ? 'attesa' : 'ok');
    numero('n-backup', SK.quando(valore), classe, SK.dataEOra(valore));
    if (classe !== 'ok') {
      return SK.avviso(classe, 'Il backup e vecchio',
        'L\'ultima copia risale a ' + SK.dataEOra(valore) + '. Controlla il timer: ' +
        'sul server, "systemctl list-timers | grep starkid" e "journalctl -u starkid-backup".');
    }
    return null;
  }

  /* ------------------------------------------------------------ database */

  function mostraDatabase(dati) {
    var byte = primo(dati, ['database_byte', 'db_byte', 'dimensione_database', 'database']);
    if (byte === undefined) { numero('n-database', '?', '', 'Il server non lo dice'); return; }
    numero('n-database', SK.byte(byte), '', Number(byte) + ' byte');
  }

  /* -------------------------------------------------------------- tablet */

  function mostraZitti(tablets) {
    var contenitore = SK.svuota(SK.$('#zitti'));
    var zitti = tablets.filter(function (t) {
      var quando = SK.data(t.ultimo_contatto);
      if (!quando) return true;
      return (Date.now() - quando.getTime()) / 3600000 >= ORE_SOSPETTE;
    });

    numero('n-zitti', String(zitti.length), zitti.length === 0 ? 'ok' : (zitti.length > 1 ? 'guasto' : 'attesa'));

    if (tablets.length === 0) {
      contenitore.appendChild(SK.el('p', { classe: 'tenue', testo: 'Non c\'e ancora nessun tablet.' }));
      return;
    }
    if (zitti.length === 0) {
      contenitore.appendChild(SK.el('p', {}, [
        SK.el('span', { classe: 'etichetta ok', testo: 'tutti presenti' }),
        ' Tutti i tablet si sono fatti vivi nelle ultime ' + ORE_SOSPETTE + ' ore.'
      ]));
      return;
    }

    var lista = SK.el('ul');
    zitti.forEach(function (t) {
      var quando = SK.data(t.ultimo_contatto);
      var ore = quando ? (Date.now() - quando.getTime()) / 3600000 : null;
      var testo;
      if (!quando) {
        testo = ' - mai arrivato: il QR di arruolamento non e stato usato, o il tablet non e riuscito a collegarsi';
      } else if (ore >= ORE_ALLARME) {
        testo = ' - zitto da ' + Math.round(ore / 24) + ' giorni (ultima volta ' + SK.dataEOra(t.ultimo_contatto) + ')';
      } else {
        testo = ' - zitto da ' + Math.round(ore) + ' ore (ultima volta ' + SK.dataEOra(t.ultimo_contatto) + ')';
      }
      lista.appendChild(SK.el('li', {}, [
        SK.el('a', { href: 'tablet.html?id=' + encodeURIComponent(t.id), testo: t.nome }),
        testo
      ]));
    });
    contenitore.appendChild(lista);
  }

  /* ------------------------------------------------------------- insieme */

  function carica() {
    SK.pulisci(SK.$('#messaggio'));
    var dettagli = SK.svuota(SK.$('#dettagli'));

    /* Il controllo pubblico non ha bisogno della sessione: se risponde lui e
       non rispondono gli altri, il problema e la sessione, non il servizio. */
    var vivo = SK.api('GET', '/healthz').then(function (dati) { return dati || {}; }, function () { return null; });
    var servizio = SK.api('GET', '/api/v1/salute').then(function (dati) { return { dati: dati }; }, function (errore) { return { errore: errore }; });
    var elenco = SK.apiSessione('GET', '/api/v1/tablets').then(function (dati) { return dati; }, function (errore) { return { errore: errore }; });

    Promise.all([vivo, servizio, elenco]).then(function (risultati) {
      var avvisi = [];

      /* --- servizio in piedi --- */
      var salute = risultati[0];
      if (salute) {
        var stato = primo(salute, ['stato', 'status']) || 'ok';
        numero('n-servizio', stato === 'ok' ? 'in piedi' : String(stato), stato === 'ok' ? 'ok' : 'attesa');
      } else {
        numero('n-servizio', 'muto', 'guasto');
        avvisi.push(SK.avviso('guasto', 'Il controllo pubblico non risponde',
          'La pagina e arrivata (quindi nginx c\'e) ma /healthz no: il programma dietro e fermo o non riparte. ' +
          'Sul server: "systemctl status starkid" e "journalctl -u starkid -n 50".'));
      }

      /* --- numeri del servizio --- */
      var parte = risultati[1];
      if (parte.dati) {
        var a = mostraCertificato(parte.dati);
        if (a) avvisi.push(a);
        var b = mostraBackup(parte.dati);
        if (b) avvisi.push(b);
        mostraDatabase(parte.dati);
        var versione = primo(parte.dati, ['versione', 'version']);
        var da = primo(parte.dati, ['da', 'avviato', 'uptime']);
        var righe = [];
        if (versione) righe.push('Versione del server: ' + versione + '.');
        if (da) righe.push('In piedi da: ' + (SK.data(da) ? SK.dataEOra(da) : String(da)) + '.');
        if (righe.length) dettagli.appendChild(SK.el('p', { testo: righe.join(' ') }));
      } else {
        ['n-certificato', 'n-backup', 'n-database'].forEach(function (id) { numero(id, '?', ''); });
        var errore = parte.errore || {};
        if (errore.stato === 404 || errore.stato === 405 || errore.stato === 501) {
          dettagli.appendChild(SK.avviso('attesa', 'Questo server non dice come sta',
            'Certificato, backup e dimensione del database arrivano da GET /api/v1/salute, che qui ' +
            'risponde "non trovato". Quella rotta c’e dalla v1, quindi di solito vuol dire che ' +
            'il server e piu vecchio dei file di questa pagina, oppure che nginx non sta mandando ' +
            '/api/ al servizio. Il resto della pagina funziona lo stesso.'));
        } else {
          dettagli.appendChild(SK.avviso('guasto', 'Numeri del servizio non disponibili', errore.messaggio || 'motivo non detto'));
        }
      }

      /* --- tablet --- */
      var dati = risultati[2];
      if (dati && dati.errore) {
        numero('n-zitti', '?', '');
        SK.svuota(SK.$('#zitti')).appendChild(SK.avviso('guasto', 'Elenco dei tablet non disponibile', dati.errore.messaggio));
      } else {
        var grezzi = Array.isArray(dati) ? dati : ((dati && (dati.tablets || dati.dispositivi)) || []);
        var tablets = grezzi.map(SK.normalizzaTablet);
        mostraZitti(tablets);

        var rotti = tablets.filter(function (t) { return SK.spieCritiche(t).length > 0; });
        if (rotti.length) {
          avvisi.unshift(SK.avviso('guasto',
            'Su ' + rotti.length + (rotti.length === 1 ? ' tablet' : ' tablet') + ' i limiti di tempo non stanno funzionando',
            'Apri l\'elenco dei tablet: sulla scheda c\'e scritto cosa manca e dove si tocca.'));
        }
      }

      if (avvisi.length) {
        var messaggio = SK.svuota(SK.$('#messaggio'));
        messaggio.classList.remove('nascosto');
        avvisi.forEach(function (nodo) { messaggio.appendChild(nodo); });
      }

      if (!dettagli.childNodes.length) {
        dettagli.appendChild(SK.el('p', { classe: 'tenue', testo: 'Niente da segnalare.' }));
      }
      dettagli.appendChild(SK.el('p', {
        classe: 'piccolo tenue',
        testo: 'Questa pagina non tocca il server: chiede e mostra. Riavvii, rinnovi e backup si fanno in SSH.'
      }));
    });
  }

  document.addEventListener('DOMContentLoaded', function () {
    SK.$('#ricarica').addEventListener('click', carica);
    carica();
  });
})();
