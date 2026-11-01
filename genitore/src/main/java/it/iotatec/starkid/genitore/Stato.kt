package it.iotatec.starkid.genitore

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
        tablet = api.elencoTablet()
        schermata = Schermata.Elenco
    }

    fun esci() = lavora {
        api.esci()
        tablet = emptyList()
        configurazione = null
        schermata = Schermata.Accesso
    }

    fun carica() = lavora { tablet = api.elencoTablet() }

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
