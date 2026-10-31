/* nuovo.html - crea un tablet e disegna il QR di arruolamento.
 *
 * Il QR si genera QUI, nel browser. Non e una scelta di stile: dentro il QR
 * ci sono il nome del bambino e la password del Wi-Fi, e il server non deve
 * vederli mai. Al server si chiede solo "dammi un codice di arruolamento per
 * un tablet nuovo"; il resto - nome del bambino, rete, tema, limite - entra
 * nel disegno senza uscire da questa pagina.
 *
 * Il contenuto del QR e il JSON di provisioning di Android: quello che il
 * tablet si aspetta quando, al primissimo avvio, si toccano sei volte lo
 * schermo e si apre il lettore di QR.
 */

'use strict';

(function () {

  /* Deve restare uguale a KidsDeviceAdminReceiver nel progetto Android. */
  var COMPONENTE = 'it.iotatec.starkid/it.iotatec.starkid.admin.KidsDeviceAdminReceiver';

  var A = 'android.app.extra.';
  var CHIAVI = {
    componente: A + 'PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME',
    scarica: A + 'PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION',
    impronta: A + 'PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM',
    sistemaAttive: A + 'PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED',
    saltaCifratura: A + 'PROVISIONING_SKIP_ENCRYPTION',
    wifiNome: A + 'PROVISIONING_WIFI_SSID',
    wifiTipo: A + 'PROVISIONING_WIFI_SECURITY_TYPE',
    wifiPassword: A + 'PROVISIONING_WIFI_PASSWORD',
    extra: A + 'PROVISIONING_ADMIN_EXTRAS_BUNDLE'
  };

  /* Le stesse chiavi che legge admin/Provisioning.kt sul tablet. */
  var EXTRA = {
    server: 'server',
    token: 'token',
    bambino: 'bambino',
    tema: 'tema',
    kiosk: 'kiosk',
    limite: 'limite_giornaliero'
  };

  var creato = null;     /* { id, nome, token } */
  var ultimoQr = null;
  var attesa = null;

  /* ------------------------------------------------------------ passo 1 */

  function crea(evento) {
    evento.preventDefault();
    var bottone = SK.$('#crea');
    var campo = SK.$('#nome');
    var errore = SK.$('#errore-nome');
    var nome = campo.value.trim();

    errore.classList.add('nascosto');
    campo.removeAttribute('aria-invalid');
    SK.pulisci(SK.$('#messaggio'));

    if (!nome) {
      errore.textContent = 'Dai un nome al tablet: ti serve per riconoscerlo nell\'elenco.';
      errore.classList.remove('nascosto');
      campo.setAttribute('aria-invalid', 'true');
      campo.focus();
      return;
    }

    SK.occupato(bottone, true, 'Creo...');
    SK.apiSessione('POST', '/api/v1/tablets', { nome: nome })
      .then(function (risposta) {
        var token = risposta && (risposta.token_arruolamento || risposta.token);
        var id = risposta && (risposta.id || risposta.id_dispositivo);
        if (!token) {
          throw new SK.ErroreApi(0,
            'Il tablet e stato creato ma il server non ha dato il codice di arruolamento, ' +
            'quindi non posso disegnare il QR. Torna all\'elenco: il tablet c\'e, e si puo ' +
            'cancellare e ricreare.');
        }
        creato = { id: id || '', nome: nome, token: String(token) };

        SK.$('#modulo-crea').classList.add('nascosto');
        SK.$('#passo-due').classList.remove('nascosto');
        SK.$('#token').value = creato.token;
        SK.$('#riga-creato').textContent = '"' + nome + '"' + (creato.id ? ' (identificativo ' + creato.id + ')' : '');
        document.title = 'QR per ' + nome + ' - StarKid';
        SK.$('#bambino').focus();
        aggiorna();
      })
      .catch(function (err) {
        SK.occupato(bottone, false);
        SK.mostraErrore(SK.$('#messaggio'), err, 'Non ho potuto creare il tablet');
      });
  }

  /* ------------------------------------------------------------ passo 2 */

  function contenuto() {
    var p = {};
    p[CHIAVI.componente] = COMPONENTE;

    var apk = SK.$('#apk').value.trim();
    if (apk) p[CHIAVI.scarica] = apk;
    var impronta = SK.$('#checksum').value.trim();
    if (impronta) p[CHIAVI.impronta] = impronta;
    if (SK.$('#sistema-attive').checked) p[CHIAVI.sistemaAttive] = true;
    if (SK.$('#salta-cifratura').checked) p[CHIAVI.saltaCifratura] = true;

    var rete = SK.$('#wifi-nome').value.trim();
    if (rete) {
      p[CHIAVI.wifiNome] = rete;
      var tipo = SK.$('#wifi-tipo').value;
      p[CHIAVI.wifiTipo] = tipo;
      var password = SK.$('#wifi-password').value;
      if (tipo !== 'NONE' && password) p[CHIAVI.wifiPassword] = password;
    }

    var extra = {};
    var server = SK.$('#server').value.trim().replace(/\/+$/, '');
    if (server) extra[EXTRA.server] = server;
    extra[EXTRA.token] = creato ? creato.token : '';
    var bambino = SK.$('#bambino').value.trim();
    if (bambino) extra[EXTRA.bambino] = bambino;
    extra[EXTRA.tema] = SK.$('#tema-iniziale').value;
    if (SK.$('#kiosk-iniziale').checked) extra[EXTRA.kiosk] = true;
    var limite = parseInt(SK.$('#limite-iniziale').value, 10);
    if (!isNaN(limite) && limite >= 0) extra[EXTRA.limite] = limite;
    p[CHIAVI.extra] = extra;

    return p;
  }

  function quantiByte(testo) {
    return (typeof TextEncoder === 'function')
      ? new TextEncoder().encode(testo).length
      : unescape(encodeURIComponent(testo)).length;
  }

  function aggiorna() {
    if (!creato) return;
    var json = JSON.stringify(contenuto());
    SK.$('#json').textContent = JSON.stringify(contenuto(), null, 2);

    var byte = quantiByte(json);
    var eci = /[^\x00-\x7F]/.test(json);
    var massimo = StarKidQR.capacitaMassima('L', eci);
    /* Prima si prova con la correzione media: regge una stampa un po'
       sporca. Se non ci sta, si scende a L, che lascia piu spazio ai dati. */
    var livello = byte <= StarKidQR.capacitaByte(StarKidQR.MAX_VERSIONE, 'M', eci) ? 'M' : 'L';

    var misura = SK.$('#misura');
    SK.$('#misura-riempimento').style.width = Math.min(100, Math.round(byte * 100 / massimo)) + '%';
    misura.classList.toggle('quasi', byte > massimo * 0.8 && byte <= massimo);
    misura.classList.toggle('troppo', byte > massimo);
    SK.$('#misura-testo').textContent = byte + ' byte su ' + massimo +
      (byte > massimo ? ' - troppo lungo' : (livello === 'L' ? ' - correzione L' : ' - correzione M'));

    var cornice = SK.svuota(SK.$('#qr-cornice'));
    var zonaErrore = SK.svuota(SK.$('#qr-errore'));
    try {
      ultimoQr = StarKidQR.codifica(json, { livello: livello });
      cornice.appendChild(StarKidQR.aSvg(ultimoQr, {
        bordo: 4,
        descrizione: 'QR di arruolamento per ' + creato.nome
      }));
      SK.$('#qr-dettagli').textContent = 'versione ' + ultimoQr.versione + ', correzione ' +
        ultimoQr.livello + ', ' + ultimoQr.dimensione + 'x' + ultimoQr.dimensione + ' moduli';
      /* Oltre una certa fittezza il QR si legge solo a fatica, soprattutto
         da uno schermo. Meglio dirlo prima che davanti al tablet
         riportato alle condizioni di fabbrica. */
      if (ultimoQr.versione >= 17) {
        zonaErrore.appendChild(SK.avviso('attesa', 'QR fitto',
          'Con questo contenuto i quadratini sono piccoli e qualche tablet fa fatica. ' +
          'Prova a inquadrarlo con un telefono prima di usarlo; se non lo legge, ' +
          'stampalo piu grande oppure togli il Wi-Fi dal QR (la rete si sceglie a mano ' +
          'sul tablet) o installa l\'APK con adb e lascia vuoti indirizzo e impronta.'));
      }
      abilitaUscite(true);
    } catch (e) {
      ultimoQr = null;
      SK.$('#qr-dettagli').textContent = '';
      zonaErrore.appendChild(SK.avviso('guasto', 'Non riesco a disegnare il QR', e.message));
      abilitaUscite(false);
    }
  }

  function abilitaUscite(acceso) {
    SK.$('#scarica-png').disabled = !acceso;
    SK.$('#stampa').disabled = !acceso;
  }

  function programmaAggiornamento() {
    if (attesa) clearTimeout(attesa);
    /* Ridisegnare a ogni tasto premuto su un QR grande fa scattare il
       telefono: si aspetta che il genitore smetta di scrivere. */
    attesa = setTimeout(aggiorna, 180);
  }

  /* -------------------------------------------------------------- uscite */

  function scaricaPng() {
    if (!ultimoQr) return;
    StarKidQR.aPngBlob(ultimoQr, { scala: 8, bordo: 4 }).then(function (blob) {
      SK.scarica(SK.nomeFileSicuro('starkid-qr-' + creato.nome, '.png'), blob);
    }).catch(function (e) {
      SK.mostraErrore(SK.$('#messaggio'), { messaggio: e.message }, 'Niente PNG');
    });
  }

  function scaricaJson() {
    if (!creato) return;
    SK.scarica(
      SK.nomeFileSicuro('starkid-provisioning-' + creato.nome, '.json'),
      JSON.stringify(contenuto(), null, 2)
    );
    SK.mostraMessaggio(SK.$('#messaggio'), 'attesa', 'File salvato',
      'Dentro ci sono il codice di arruolamento e la password del Wi-Fi: cancellalo appena hai finito.');
  }

  /* --------------------------------------------------------------- avvio */

  function avvia() {
    var tema = SK.svuota(SK.$('#tema-iniziale'));
    SK.TEMI.forEach(function (voce) {
      tema.appendChild(SK.el('option', { value: voce.id, testo: voce.nome }));
    });

    /* L'indirizzo lo sa il browser: e quello da cui e arrivata la pagina. */
    SK.$('#server').value = window.location.origin;

    SK.$('#modulo-crea').addEventListener('submit', crea);
    SK.$('#modulo-qr').addEventListener('submit', function (e) { e.preventDefault(); });
    SK.$('#modulo-qr').addEventListener('input', programmaAggiornamento);
    SK.$('#modulo-qr').addEventListener('change', programmaAggiornamento);

    SK.$('#mostra-password').addEventListener('click', function () {
      var campo = SK.$('#wifi-password');
      var nascosta = campo.type === 'password';
      campo.type = nascosta ? 'text' : 'password';
      this.textContent = nascosta ? 'Nascondi' : 'Mostra';
    });

    SK.$('#copia-token').addEventListener('click', function () {
      var bottone = this;
      SK.copia(SK.$('#token').value).then(function () {
        bottone.textContent = 'Copiato';
        setTimeout(function () { bottone.textContent = 'Copia'; }, 2000);
      }, function (e) {
        SK.$('#token').select();
        SK.mostraMessaggio(SK.$('#messaggio'), 'attesa', 'Copia a mano', e.message);
      });
    });

    SK.$('#scarica-png').addEventListener('click', scaricaPng);
    SK.$('#scarica-json').addEventListener('click', scaricaJson);
    SK.$('#stampa').addEventListener('click', function () { window.print(); });

    SK.$('#nome').focus();
  }

  document.addEventListener('DOMContentLoaded', avvia);
})();
