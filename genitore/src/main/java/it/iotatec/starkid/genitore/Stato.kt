package it.iotatec.starkid.genitore

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.iotatec.starkid.genitore.net.Aggiornamenti
import it.iotatec.starkid.genitore.net.Api
import it.iotatec.starkid.genitore.net.ErroreServer
import it.iotatec.starkid.genitore.net.Tablet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/** Dove siamo. Non c e un navigatore: le schermate sono cinque. */
sealed interface Schermata {
    data object Accesso : Schermata
    data object Elenco : Schermata
    data class Dettaglio(val idTablet: String) : Schermata
    data object Nuovo : Schermata
    data class Storico(val idTablet: String) : Schermata
}

class Stato(app: Application) : AndroidViewModel(app) {

    private val api = Api(app)

    var schermata by mutableStateOf<Schermata>(if (api.forseDentro()) Schermata.Elenco else Schermata.Accesso)
        private set

    var indirizzo by mutableStateOf(api.indirizzo)
    var utente by mutableStateOf(api.utente)

    /**
     * admin, genitore o osservatore. Un osservatore vede tutto quello che
     * gli e stato dato e non puo cambiare niente: il server gli risponde
     * 403, e qui si evita di mettergli davanti i comandi che lo prenderebbero.
     */
    var ruolo by mutableStateOf(api.ruolo)

    val solaLettura: Boolean get() = ruolo == "osservatore"

    var occupato by mutableStateOf(false)
        private set
    var errore by mutableStateOf<String?>(null)
        private set
    var avviso by mutableStateOf<String?>(null)
        private set

    var tablet by mutableStateOf<List<Tablet>>(emptyList())
        private set

    /** La configurazione aperta adesso, come documento del server. */
    var configurazione by mutableStateOf<JSONObject?>(null)
        private set
    var storico by mutableStateOf<JSONArray?>(null)
        private set

    /** Il tablet appena creato: id, nome e token, che si vede una volta sola. */
    var appenaCreato by mutableStateOf<JSONObject?>(null)
        private set

    fun pulisciMessaggi() { errore = null; avviso = null }

    fun vaiA(dove: Schermata) {
        pulisciMessaggi()
        schermata = dove
        when (dove) {
            is Schermata.Elenco -> carica()
            is Schermata.Dettaglio -> caricaConfigurazione(dove.idTablet)
            is Schermata.Storico -> caricaStorico(dove.idTablet)
            is Schermata.Nuovo -> appenaCreato = null
            else -> Unit
        }
    }

    fun tabletCon(id: String): Tablet? = tablet.firstOrNull { it.id == id }

    // -----------------------------------------------------------------------

    fun accedi(server: String, nomeUtente: String, password: String) = lavora {
        api.accedi(server, nomeUtente, password)
        indirizzo = api.indirizzo
        utente = api.utente
        ruolo = api.ruolo
        tablet = api.elencoTablet()
        schermata = Schermata.Elenco
    }

    fun esci() = lavora {
        api.esci()
        tablet = emptyList()
        configurazione = null
        schermata = Schermata.Accesso
    }

    fun carica() = lavora {
        // Il ruolo si rilegge a ogni giro: il login e lunico momento in cui
        // arriva da solo, e fra un avvio e laltro la sessione resta ma quel
        // dato no. Senza, a un osservatore ricomparirebbero i comandi di
        // scrittura al secondo avvio della app.
        api.chiSono()
        ruolo = api.ruolo
        tablet = api.elencoTablet()
    }

    private fun caricaConfigurazione(id: String) = lavora {
        configurazione = api.configurazione(id)
    }

    private fun caricaStorico(id: String) = lavora { storico = api.storico(id) }

    fun salva(id: String, documento: JSONObject) = lavora {
        val salvata = api.salvaConfigurazione(id, documento)
        configurazione = salvata
        tablet = api.elencoTablet()
        avviso = "Salvato. Il tablet la prende al prossimo collegamento."
    }

    /**
     * Chiude o riapre il tablet, subito.
     *
     * Non passa dal "Salva" in fondo al modulo di proposito: e la cosa che
     * si fa di fretta, e scorrere un modulo lungo in quel momento non si fa.
     *
     * Manda il documento che il genitore ha davanti - bozza compresa, se ha
     * modifiche in corso - con il solo campo "bloccato" cambiato. Il server
     * vuole il documento intero e non accetta mezze modifiche: mandare
     * quello vecchio cancellerebbe il lavoro in corso, e la schermata lo
     * dice prima di farlo.
     */
    fun cambiaBlocco(id: String, documento: JSONObject, chiuso: Boolean) = lavora {
        val nuovo = JSONObject(documento.toString()).put("bloccato", chiuso)
        configurazione = api.salvaConfigurazione(id, nuovo)
        tablet = api.elencoTablet()
        avviso = if (chiuso) {
            "Tablet chiuso. Il tablet lo applica alla prossima chiamata, entro un minuto."
        } else {
            "Tablet riaperto. Il tablet lo applica alla prossima chiamata, entro un minuto."
        }
    }

    /**
     * Chiude o riapre dall'elenco, dove il documento non ce l'abbiamo in
     * mano: si rilegge, si cambia il solo campo e si rimanda.
     *
     * Due chiamate invece di una, ma nessun rischio di rimandare indietro
     * una configurazione letta chissa quando: il server vuole il documento
     * intero e sovrascriverebbe quello che c'e.
     */
    fun cambiaBloccoDaElenco(id: String, chiuso: Boolean) = lavora {
        val documento = api.configurazione(id)
        api.salvaConfigurazione(id, JSONObject(documento.toString()).put("bloccato", chiuso))
        tablet = api.elencoTablet()
        avviso = if (chiuso) {
            "Tablet chiuso. Lo applica alla prossima chiamata, entro un minuto."
        } else {
            "Tablet riaperto. Lo applica alla prossima chiamata, entro un minuto."
        }
    }

    fun creaTablet(nome: String) = lavora {
        appenaCreato = api.nuovoTablet(nome)
        tablet = api.elencoTablet()
    }

    fun tornaA(id: String, revisione: Int) = lavora {
        api.tornaA(id, revisione)
        configurazione = api.configurazione(id)
        storico = api.storico(id)
        tablet = api.elencoTablet()
        avviso = "Rimessa la revisione $revisione."
    }

    fun cancella(id: String) = lavora {
        api.cancellaTablet(id)
        tablet = api.elencoTablet()
        schermata = Schermata.Elenco
        avviso = "Tablet cancellato."
    }

    /**
     * Un solo posto in cui si aspetta la rete e si raccolgono gli errori.
     *
     * Gli errori di rete e quelli del server finiscono tutti e due in
     * [errore] come frase gia leggibile: chi guarda la schermata non deve
     * vedere ne uno stack ne un numero. L unico caso trattato a parte e il
     * 403, che vuol dire "sessione finita" e quindi riporta all accesso
     * invece di lasciare una schermata piena di dati vecchi.
     */
    // -----------------------------------------------------------------------
    // Aggiornamenti dell'app, da GitHub
    // -----------------------------------------------------------------------

    /** La versione nuova, se ce n'e una. */
    var novita by mutableStateOf<Aggiornamenti.Novita?>(null)
        private set

    /** Lo scaricamento e in corso: ha un suo stato perche e lento. */
    var scaricando by mutableStateOf(false)
        private set

    private var giaCercato = false

    /**
     * Guarda se c'e una versione nuova.
     *
     * In silenzio, e senza passare da [lavora]: un controllo che non riesce
     * - telefono senza rete, GitHub che risponde male - non deve riempire di
     * rosso una schermata che sta facendo tutt'altro. Chi lo chiede a mano
     * invece l'esito lo vuole sapere, e allora [esplicito] e vero.
     */
    fun cercaAggiornamenti(esplicito: Boolean = false) {
        if (giaCercato && !esplicito) return
        giaCercato = true
        viewModelScope.launch {
            val trovata = withContext(Dispatchers.IO) {
                runCatching { Aggiornamenti.cerca() }.getOrNull()
            }
            novita = trovata
            if (esplicito) {
                avviso = if (trovata == null) {
                    "Questa e gia l'ultima versione."
                } else {
                    "C'e la ${trovata.versione}."
                }
            }
        }
    }

    /**
     * Scarica l'aggiornamento e apre l'installatore di Android.
     *
     * L'ultima parola e di chi guarda: questa app non installa niente da
     * sola. Se il permesso di installare non c'e, si va a chiederlo invece
     * di aprire un installatore che non farebbe niente.
     */
    fun aggiorna(context: android.content.Context) {
        val quale = novita ?: return
        if (!Aggiornamenti.puoInstallare(context)) {
            avviso = "Android chiede il permesso di installare app da StarKid: " +
                "accendilo e ripremi il bottone."
            Aggiornamenti.chiediPermessoDiInstallare(context)
            return
        }
        viewModelScope.launch {
            scaricando = true
            errore = null
            try {
                val file = withContext(Dispatchers.IO) { Aggiornamenti.scarica(context, quale) }
                Aggiornamenti.installa(context, file)
            } catch (e: ErroreServer) {
                errore = e.message
            } catch (e: IOException) {
                errore = "Non riesco a scaricare l'aggiornamento. Controlla la connessione."
            } catch (e: Exception) {
                errore = "Non ho potuto aggiornare: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                scaricando = false
            }
        }
    }

    private fun lavora(blocco: suspend () -> Unit) {
        viewModelScope.launch {
            occupato = true
            errore = null
            try {
                withContext(Dispatchers.IO) { blocco() }
            } catch (e: ErroreServer) {
                errore = e.message
                // Le parentesi non sono decorative: senza, un 401 sbagliando
                // la password in fase di accesso resetterebbe la sessione
                // mentre siamo gia nella schermata di accesso.
                if (e.stato == 403 || (e.stato == 401 && schermata !is Schermata.Accesso)) {
                    api.dimentica()
                    schermata = Schermata.Accesso
                }
            } catch (e: IOException) {
                errore = "Non riesco a parlare col server. Controlla la connessione e l'indirizzo."
            } catch (e: Exception) {
                errore = "Qualcosa non ha funzionato: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                occupato = false
            }
        }
    }
}
