package it.iotatec.starkid.genitore.net

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Il client del server StarKid.
 *
 * Niente libreria HTTP: le chiamate sono sette e sono tutte uguali, quindi
 * HttpURLConnection basta e non si porta dentro nulla. Il JSON lo fa
 * org.json, che e dentro Android.
 *
 * COSA FA DIVERSO DAL BROWSER. La web UI vive dentro un dominio e il browser
 * le tiene i cookie; qui i cookie li tiene questa classe, uno solo: quello di
 * sessione. Il token CSRF NON viene letto dal cookie ma dal corpo della
 * risposta di login, ed e piu corretto che nel browser: li il doppio invio e
 * un ripiego, qui il valore arriva una volta sola dal server e non puo
 * essere scritto da nessun altro.
 *
 * SOLO HTTPS, e senza eccezioni. Dentro queste richieste passano la password
 * del genitore, il cookie di sessione e i token di arruolamento dei tablet.
 * Un indirizzo http:// viene rifiutato qui, prima di aprire la connessione:
 * non e una preferenza, e il motivo per cui il server mette Secure sul
 * cookie e HSTS sul dominio.
 */
class Api(contesto: Context) {

    private val prefs = contesto.getSharedPreferences("starkid_genitori", Context.MODE_PRIVATE)

    var indirizzo: String
        get() = prefs.getString(CHIAVE_INDIRIZZO, "").orEmpty()
        private set(v) { prefs.edit().putString(CHIAVE_INDIRIZZO, v).apply() }

    private var cookie: String
        get() = prefs.getString(CHIAVE_COOKIE, "").orEmpty()
        set(v) { prefs.edit().putString(CHIAVE_COOKIE, v).apply() }

    private var csrf: String
        get() = prefs.getString(CHIAVE_CSRF, "").orEmpty()
        set(v) { prefs.edit().putString(CHIAVE_CSRF, v).apply() }

    var utente: String
        get() = prefs.getString(CHIAVE_UTENTE, "").orEmpty()
        private set(v) { prefs.edit().putString(CHIAVE_UTENTE, v).apply() }

    /** admin, genitore oppure osservatore. Vuoto finche non si entra. */
    var ruolo: String
        get() = prefs.getString(CHIAVE_RUOLO, "").orEmpty()
        private set(v) { prefs.edit().putString(CHIAVE_RUOLO, v).apply() }

    /** C e una sessione da provare? Non vuol dire che sia ancora valida. */
    fun forseDentro(): Boolean = indirizzo.isNotEmpty() && cookie.isNotEmpty()

    fun dimentica() {
        prefs.edit()
            .remove(CHIAVE_COOKIE).remove(CHIAVE_CSRF)
            .remove(CHIAVE_UTENTE).remove(CHIAVE_RUOLO)
            .apply()
    }

    /**
     * Chi sono, secondo il server.
     *
     * Serve a riprendere il ruolo quando la app riparte: il login e
     * lunico momento in cui il ruolo arriva da solo, e fra un avvio e
     * laltro la sessione resta ma quel dato no. Senza, a un osservatore
     * ricomparirebbero i comandi di scrittura al secondo avvio.
     */
    fun chiSono(): JSONObject {
        val risposta = chiama("GET", "/api/v1/auth/io", null)
        ruolo = risposta.optString("ruolo", ruolo.ifEmpty { "genitore" })
        utente = risposta.optString("utente", utente)
        return risposta
    }

    // -----------------------------------------------------------------------
    // Le chiamate
    // -----------------------------------------------------------------------

    fun accedi(indirizzoServer: String, nomeUtente: String, password: String): JSONObject {
        val pulito = normalizza(indirizzoServer)
        val corpo = JSONObject().put("utente", nomeUtente).put("password", password)
        // L indirizzo si salva PRIMA della chiamata perche serve a costruirla,
        // ma se la chiamata non va a buon fine non resta nessuna sessione:
        // e il cookie a dire se siamo dentro, non l indirizzo.
        indirizzo = pulito
        val risposta = chiama("POST", "/api/v1/auth/login", corpo, autenticata = false)
        csrf = risposta.optString("csrf")
        utente = risposta.optString("utente", nomeUtente)
        ruolo = risposta.optString("ruolo", "genitore")
        return risposta
    }

    fun esci() {
        try {
            chiama("POST", "/api/v1/auth/logout", JSONObject())
        } catch (e: ErroreServer) {
            // Se il server non risponde si esce lo stesso: il cookie va via
            // da qui, e di la scade da solo.
        } catch (e: IOException) {
        }
        dimentica()
    }

    fun elencoTablet(): List<Tablet> {
        val risposta = chiama("GET", "/api/v1/tablets", null)
        val array = risposta.optJSONArray("tablets") ?: JSONArray()
        return (0 until array.length()).map { Tablet.da(array.getJSONObject(it)) }
    }

    fun configurazione(idTablet: String): JSONObject =
        chiama("GET", "/api/v1/tablets/$idTablet/config", null)

    /**
     * Le app che il tablet dice di avere installate, con nome e icona.
     *
     * Serve a far spuntare le app da un elenco invece di far scrivere
     * "com.google.android.apps.youtube.kids" a mano, che e il modo piu
     * sicuro di sbagliare una lettera e non capire perche quell'app non si
     * apre. Un tablet che non ha ancora parlato risponde con un elenco
     * vuoto, e non e un errore: e il caso di tutti i tablet appena creati.
     */
    fun appInstallate(idTablet: String): List<AppInstallata> {
        val risposta = chiama("GET", "/api/v1/tablets/$idTablet/apps", null)
        val elenco = risposta.optJSONArray("app") ?: return emptyList()
        return (0 until elenco.length()).mapNotNull { i ->
            elenco.optJSONObject(i)?.let(AppInstallata::da)
        }.filter { it.pacchetto.isNotBlank() }
    }

    fun salvaConfigurazione(idTablet: String, documento: JSONObject): JSONObject =
        chiama("PUT", "/api/v1/tablets/$idTablet/config", documento)

    fun storico(idTablet: String): JSONArray =
        chiama("GET", "/api/v1/tablets/$idTablet/history", null)
            .optJSONArray("revisioni") ?: JSONArray()

    fun tornaA(idTablet: String, revisione: Int): JSONObject =
        chiama("POST", "/api/v1/tablets/$idTablet/revert", JSONObject().put("revisione", revisione))

    fun nuovoTablet(nome: String): JSONObject =
        chiama("POST", "/api/v1/tablets", JSONObject().put("nome", nome))

    fun cancellaTablet(idTablet: String): JSONObject =
        chiama("DELETE", "/api/v1/tablets/$idTablet", null)

    // -----------------------------------------------------------------------
    // Il motore
    // -----------------------------------------------------------------------

    private fun chiama(
        metodo: String,
        percorso: String,
        corpo: JSONObject?,
        autenticata: Boolean = true,
    ): JSONObject {
        if (indirizzo.isEmpty()) throw ErroreServer(0, "Non hai ancora detto qual e il server.")
        val url = URL(indirizzo + percorso)
        if (url.protocol != "https") {
            throw ErroreServer(0, "L'indirizzo deve cominciare per https://: qui dentro passa la tua password.")
        }
        val conn = (url.openConnection() as HttpsURLConnection).apply {
            requestMethod = metodo
            connectTimeout = 12_000
            readTimeout = 20_000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "StarKidGenitori/1.0")
            if (autenticata && cookie.isNotEmpty()) setRequestProperty("Cookie", cookie)
            if (metodo != "GET" && csrf.isNotEmpty()) setRequestProperty("x-csrf-token", csrf)
            if (corpo != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            corpo?.let { conn.outputStream.use { flusso -> flusso.write(it.toString().toByteArray()) } }

            val stato = conn.responseCode
            raccogliCookie(conn)

            val testo = (if (stato in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use(BufferedReader::readText).orEmpty()

            if (stato !in 200..299) throw ErroreServer(stato, spiega(stato, testo))
            if (testo.isBlank()) return JSONObject()
            return when (val primo = testo.trimStart().firstOrNull()) {
                '{' -> JSONObject(testo)
                // /history risponde con una lista: la si incarta per avere
                // sempre lo stesso tipo di ritorno.
                '[' -> JSONObject().put("revisioni", JSONArray(testo))
                else -> throw ErroreServer(stato, "Il server ha risposto qualcosa che non e JSON (iniziava per '$primo').")
            }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Il cookie di sessione cambia a ogni accesso e il server lo rinnova: va
     * riletto a ogni risposta, non solo al login.
     */
    private fun raccogliCookie(conn: HttpURLConnection) {
        var i = 0
        while (true) {
            val nome = conn.getHeaderFieldKey(i) ?: if (i == 0) { i++; continue } else break
            if (nome.equals("Set-Cookie", ignoreCase = true)) {
                val valore = conn.getHeaderField(i).orEmpty()
                val primo = valore.substringBefore(';').trim()
                if (primo.startsWith("$COOKIE_SESSIONE=")) {
                    // Un cookie svuotato e un logout deciso dal server.
                    cookie = if (primo.endsWith("=") || primo.endsWith("=\"\"")) "" else primo
                }
            }
            i++
        }
    }

    /**
     * I numeri non dicono niente a un genitore. Ogni codice che il server usa
     * davvero ha la sua frase; per gli altri si mostra il numero, che almeno
     * si puo riferire.
     */
    private fun spiega(stato: Int, testo: String): String {
        val dettaglio = runCatching { JSONObject(testo).optString("detail") }.getOrNull().orEmpty()
        return when (stato) {
            401 -> "Nome utente o password non vanno bene."
            403 -> "La sessione non e piu valida: rientra."
            404 -> "Questo tablet non c'e piu sul server."
            409 -> "Qualcun altro ha cambiato questa configurazione mentre la modificavi. Riapri il tablet e rifai la modifica."
            422 -> if (dettaglio.isNotEmpty()) "Il server ha rifiutato questi valori: $dettaglio"
                   else "Il server ha rifiutato questi valori."
            429 -> "Troppi tentativi. Aspetta qualche minuto."
            in 500..599 -> "Il server ha un problema (errore $stato). Non e colpa di quello che hai scritto."
            else -> if (dettaglio.isNotEmpty()) dettaglio else "Il server ha risposto $stato."
        }
    }

    private fun normalizza(grezzo: String): String {
        var s = grezzo.trim().trimEnd('/')
        if (s.isEmpty()) return s
        if (!s.contains("://")) s = "https://$s"
        return s
    }

    private companion object {
        const val COOKIE_SESSIONE = "starkid_sessione"
        const val CHIAVE_INDIRIZZO = "indirizzo"
        const val CHIAVE_COOKIE = "cookie"
        const val CHIAVE_CSRF = "csrf"
        const val CHIAVE_UTENTE = "utente"
        const val CHIAVE_RUOLO = "ruolo"
    }
}

/** Un errore che si puo mostrare a chi usa la app, senza tradurlo ancora. */
class ErroreServer(val stato: Int, messaggio: String) : Exception(messaggio)
