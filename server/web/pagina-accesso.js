/* index.html - accesso.
 *
 * Un solo account, un solo inquilino: qui non c'e iscrizione, non c'e
 * recupero password, non c'e scelta di famiglia. Solo utente e password.
 */

'use strict';

(function () {

  var MOTIVI = {
    scaduta: ['attesa', 'Sessione scaduta', 'Per sicurezza la sessione dura poco. Rientra e ti riporto dov\'eri.'],
    uscito: ['ok', 'Sei uscito', 'La sessione e chiusa. Su un computer non tuo chiudi anche la finestra del browser.'],
    richiesta: ['attesa', 'Serve l\'accesso', 'Questa pagina e solo per chi e entrato.']
  };

  function avvia() {
    var modulo = SK.$('#modulo-accesso');
    var utente = SK.$('#utente');
    var password = SK.$('#password');
    var bottone = SK.$('#entra');
    var messaggio = SK.$('#messaggio');

    var motivo = MOTIVI[SK.parametro('motivo')];
    if (motivo) SK.mostraMessaggio(messaggio, motivo[0], motivo[1], motivo[2]);

    utente.focus();

    function erroreCampo(campo, testo) {
      var nodo = SK.$('#errore-' + campo);
      var input = SK.$('#' + campo);
      if (!nodo) return;
      if (testo) {
        nodo.textContent = testo;
        nodo.classList.remove('nascosto');
        input.setAttribute('aria-invalid', 'true');
      } else {
        nodo.textContent = '';
        nodo.classList.add('nascosto');
        input.removeAttribute('aria-invalid');
      }
    }

    modulo.addEventListener('submit', function (evento) {
      evento.preventDefault();
      SK.pulisci(messaggio);
      erroreCampo('utente', '');
      erroreCampo('password', '');

      var nome = utente.value.trim();
      var segreto = password.value;
      var manca = false;
      if (!nome) { erroreCampo('utente', 'Scrivi il nome utente.'); manca = true; }
      if (!segreto) { erroreCampo('password', 'Scrivi la password.'); manca = true; }
      if (manca) { (nome ? password : utente).focus(); return; }

      SK.occupato(bottone, true, 'Un momento...');
      SK.api('POST', '/api/v1/auth/login', { utente: nome, password: segreto })
        .then(function () {
          password.value = '';
          window.location.replace(SK.destinazioneRitorno());
        })
        .catch(function (errore) {
          SK.occupato(bottone, false);
          password.value = '';
          password.focus();
          if (errore.stato === 401 || errore.stato === 403) {
            SK.mostraMessaggio(messaggio, 'guasto', 'Utente o password sbagliati',
              'Controlla le maiuscole e che non sia rimasto uno spazio alla fine. ' +
              'Se la password non c\'e piu, si rifa sul server da riga di comando: ' +
              'questa pagina non la puo cambiare.');
          } else if (errore.stato === 429) {
            SK.mostraMessaggio(messaggio, 'guasto', 'Troppi tentativi',
              'Il server ha rallentato gli accessi per sicurezza. Aspetta un minuto e riprova.');
          } else {
            SK.mostraErrore(messaggio, errore, 'Non sono riuscito a entrare');
          }
        });
    });
  }

  document.addEventListener('DOMContentLoaded', avvia);
})();
