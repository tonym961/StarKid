package it.iotatec.starkid.sync

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Le cinque chiamate che il tablet fa al server di gestione.
 *
 * Nessuna libreria: sono cinque rotte, tutte JSON, e HttpURLConnection
 * basta. Il tablet e un launcher che deve restare leggero e che funziona
 * benissimo senza rete; non e il caso di portarsi dentro un client HTTP per
 * questo.
 *
 * SOLO HTTPS. Dentro passa il token di questo dispositivo: chi lo intercetta
 * puo leggere e riscrivere la configurazione del tablet di un bambino. Un
 * indirizzo http:// viene rifiutato qui, prima di aprire la connessione.
 *
 * COSA NON FA: non manda niente del bambino. Non il nome, non il PIN, non
 * quanto ha giocato e a cosa. Le uniche cose che partono da qui sono le
 * cinque spie dei permessi e quale revisione e stata applicata - il resto
 * resta sul tablet, ed e una scelta, non una dimenticanza.
 */
class Server(private val indirizzo: String, private val token: String) {

    fun arruola(tokenArruolamento: String): JSONObject =
        chiama(
            "POST",
            "/api/v1/device/enroll",
            JSONObject().put("token_arruolamento", tokenArruolamento),
            conToken = false,
        )

    fun configurazione(): JSONObject = chiama("GET", "/api/v1/device/config", null)

    fun conferma(revisione: Int, esito: String, rifiutati: List<Pair<String, String>>): JSONObject {
        val campi = JSONArray()
        for ((campo, motivo) in rifiutati) {
            campi.put(JSONObject().put("campo", campo).put("motivo", motivo))
        }
        return chiama(
            "POST",
            "/api/v1/device/ack",
            JSONObject().put("revisione", revisione).put("esito", esito).put("campi_rifiutati", campi),
        )
    }

    fun spie(
        deviceOwner: Boolean,
        usageAccess: Boolean,
        overlay: Boolean,
        guardRunning: Boolean,
        defaultHome: Boolean,
    ): JSONObject = chiama(
        "POST",
        "/api/v1/device/health",
        JSONObject()
            .put("device_owner", deviceOwner)
            .put("usage_access", usageAccess)
            .put("overlay", overlay)
            .put("guard_running", guardRunning)
            .put("default_home", defaultHome),
    )

    /**
     * L'elenco delle app installate, perche il genitore le spunti invece di
     * scriverne il nome a mano. Dice cosa c'e, non cosa e stato usato.
     */
    fun appInstallate(app: List<Pair<String, String>>): JSONObject {
        val elenco = JSONArray()
        for ((pacchetto, nome) in app) {
            elenco.put(JSONObject().put("pacchetto", pacchetto).put("nome", nome))
        }
        return chiama("POST", "/api/v1/device/apps", JSONObject().put("app", elenco))
    }

    // -----------------------------------------------------------------------

    private fun chiama(
        metodo: String,
        percorso: String,
        corpo: JSONObject?,
        conToken: Boolean = true,
    ): JSONObject {
        val url = URL(indirizzo.trimEnd('/') + percorso)
        if (url.protocol != "https") {
            throw ErroreServer(0, "il server deve essere https: qui passa il token del tablet")
        }
        val conn = (url.openConnection() as HttpsURLConnection).apply {
            requestMethod = metodo
            connectTimeout = 12_000
            readTimeout = 20_000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "StarKid/1.0 (tablet)")
            if (conToken) setRequestProperty("Authorization", "Bearer $token")
            if (corpo != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            corpo?.let { conn.outputStream.use { f -> f.write(it.toString().toByteArray()) } }
            val stato = conn.responseCode
            val testo = (if (stato in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            if (stato !in 200..299) {
                val dettaglio = runCatching { JSONObject(testo).optString("detail") }.getOrNull()
                throw ErroreServer(stato, dettaglio.orEmpty().ifEmpty { "il server ha risposto $stato" })
            }
            return if (testo.isBlank()) JSONObject() else JSONObject(testo)
        } finally {
            conn.disconnect()
        }
    }
}

class ErroreServer(val stato: Int, messaggio: String) : Exception(messaggio) {
    /**
     * Il server non ci riconosce: il token non vale piu e non ha senso
     * ritentarlo ogni quarto d'ora per sempre. Succede se il genitore ha
     * cancellato il tablet o ha chiesto un nuovo arruolamento.
     */
    val tokenMorto: Boolean get() = stato == 401
}
