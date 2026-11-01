package it.iotatec.starkid.sync

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import it.iotatec.starkid.admin.Kiosk
import it.iotatec.starkid.data.BACKGROUND_PHOTO
import it.iotatec.starkid.data.FasciaApp
import it.iotatec.starkid.data.Prefs
import it.iotatec.starkid.service.GuardService
import it.iotatec.starkid.usage.UsageReader
import org.json.JSONArray
import org.json.JSONObject

/**
 * La sincronizzazione col server di gestione.
 *
 * COSA NON E. Non e un canale di comandi: il server non puo dire al tablet
 * "apri questa app" o "scarica da qui". Puo solo consegnare un documento di
 * configurazione, con un vocabolario chiuso, e il tablet decide cosa riesce
 * ad applicare. Quello che non applica lo DICE, con un codice, invece di
 * fingere di averlo fatto.
 *
 * QUANTO E LENTA. Il tablet chiama, il server non chiama il tablet: non c'e
 * nessun canale aperto, nessuna notifica push, nessun servizio di Google in
 * mezzo. Vuol dire che una modifica fatta dal genitore arriva al prossimo
 * giro, non subito, e che il blocco a distanza ha lo stesso ritardo. E un
 * prezzo pagato apposta: un tablet che tiene una connessione aperta verso
 * casa e un tablet che si puo raggiungere da fuori.
 *
 * COSA PARTE DA QUI. La revisione applicata, i campi non applicati e le
 * cinque spie dei permessi. Niente del bambino: non il nome, non il PIN, non
 * quanto ha giocato e a cosa. Quello resta sul tablet.
 */
object Sync {

    private const val TAG = "StarKidSync"

    /** Ogni quanto, al massimo, si richiama il server. */
    const val INTERVALLO_MILLIS = 5L * 60L * 1000L

    sealed interface Esito {
        /** Il tablet non e legato a nessun server: e il caso normale. */
        data object NonConfigurato : Esito
        data class Fatto(val revisione: Int, val rifiutati: Int) : Esito
        data class Fallita(val perche: String) : Esito
        /**
         * Il server risponde 401. Il token NON viene buttato: si continua a
         * chiedere, piu piano, e lo sgancio resta una decisione di una
         * persona. [da] e quando il rifiuto e cominciato.
         */
        data class NonRiconosciuto(val da: Long) : Esito
    }

    /**
     * Collega questo tablet a un server, da dentro l'area genitori.
     *
     * Serve ai tablet che sono gia in uso: il QR della prima configurazione
     * di Android si inquadra solo a tablet appena resettato, e un tablet con
     * dentro le app e il PIN di un bambino non si resetta per aggiungere la
     * gestione a distanza.
     *
     * L'indirizzo e il codice si scrivono solo se l'arruolamento riesce: un
     * tentativo andato male non deve lasciare il tablet mezzo collegato a un
     * server sbagliato.
     */
    fun collega(context: Context, indirizzo: String, codice: String): Esito {
        val prefs = Prefs.get(context)
        val completo = if (indirizzo.contains("://")) indirizzo else "https://$indirizzo"
        return try {
            val risposta = Server(completo, "").arruola(codice)
            val token = risposta.optString("token_dispositivo")
            if (token.isBlank()) return Esito.Fallita("il server non ha dato il token")
            prefs.setServer(completo, codice)
            prefs.setArruolato(token)
            esegui(context, forzata = true)
        } catch (e: ErroreServer) {
            Esito.Fallita(
                if (e.stato == 401) "Il codice non vale: o e gia stato usato, o e scaduto. " +
                    "Dal server si chiede di riarruolare il tablet."
                else e.message ?: "errore del server",
            )
        } catch (e: Exception) {
            Esito.Fallita("Non riesco a raggiungere il server: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /** Scollega: il tablet tiene quello che ha e smette di chiedere. */
    fun scollega(context: Context) {
        val prefs = Prefs.get(context)
        prefs.dimenticaArruolamento()
        prefs.setServer("", "")
    }

    /**
     * Un giro completo. Va chiamata fuori dal thread principale.
     *
     * @param forzata ignora l'intervallo: la usa il genitore quando preme
     *   "sincronizza adesso" e non ha voglia di aspettare cinque minuti.
     */
    fun esegui(context: Context, forzata: Boolean = false): Esito {
        val prefs = Prefs.get(context)
        var config = prefs.current

        if (config.serverUrl.isBlank() || config.serverToken.isBlank()) return Esito.NonConfigurato
        if (!forzata && System.currentTimeMillis() - config.lastSync < INTERVALLO_MILLIS) {
            return Esito.NonConfigurato
        }

        return try {
            if (!config.enrolled) {
                val risposta = Server(config.serverUrl, "").arruola(config.serverToken)
                val nuovo = risposta.optString("token_dispositivo")
                if (nuovo.isBlank()) return Esito.Fallita("il server non ha dato il token")
                prefs.setArruolato(nuovo)
                config = prefs.current
                Log.i(TAG, "arruolato")
            }

            val server = Server(config.serverUrl, config.serverToken)
            val documento = server.configurazione()
            val revisione = documento.optInt("revisione", 0)

            val rifiutati = applica(context, prefs, documento)
            val esito = when {
                rifiutati.isEmpty() -> "applied"
                rifiutati.size >= CAMPI_APPLICABILI -> "rejected"
                else -> "partial"
            }
            if (revisione > 0) server.conferma(revisione, esito, rifiutati)

            server.spie(
                deviceOwner = Kiosk.canManage(context),
                usageAccess = UsageReader(context).hasPermission(),
                overlay = Settings.canDrawOverlays(context),
                guardRunning = GuardService.inMarcia,
                defaultHome = laCasaEStarKid(context),
            )

            prefs.segnaSincronizzato(revisione)
            Esito.Fatto(revisione, rifiutati.size)
        } catch (e: ErroreServer) {
            if (e.tokenMorto) {
                // NON si butta il token.
                //
                // La versione di prima faceva dimenticareArruolamento() al
                // primo 401, con la scusa di non sbattere contro un errore
                // ogni cinque minuti. Era il guasto piu caro di tutto il
                // progetto: il token si rifa solo con "app.cli riarruola" PIU
                // il codice portato fisicamente a quel tablet. Un giro nelle
                // camere dei bambini, per un codice di stato.
                //
                // E un 401 non vuol dire quasi mai "sei stato cancellato".
                // Vuol dire anche: il server e stato ripristinato da una
                // copia presa prima che questo tablet si arruolasse (la riga
                // c'e ma hash_dispositivo e NULL), oppure e in mezzo a una
                // migrazione, oppure qualcuno ha sbagliato un deploy. In
                // tutti quei casi il token torna valido da solo, e un tablet
                // che si e gia sganciato invece non torna indietro.
                //
                // Quindi: si segna da quando il server non ci riconosce, si
                // continua a chiedere piano, e la decisione di sganciarsi
                // resta di una persona - dall'area genitori, "Scollega dal
                // server". Chi amministra lo vede: dal server quel tablet
                // risulta zitto, e la differenza fra "zitto" e "sganciato"
                // conta.
                if (prefs.current.rifiutatoDal == 0L) {
                    prefs.segnaRifiutato(System.currentTimeMillis())
                }
                Log.w(TAG, "il server non ci riconosce (401): tengo il token e riprovo piano")
                Esito.NonRiconosciuto(prefs.current.rifiutatoDal)
            } else {
                Log.w(TAG, "sincronizzazione fallita: ${e.message}")
                Esito.Fallita(e.message ?: "errore del server")
            }
        } catch (e: Exception) {
            // Rete assente, DNS che non risolve, certificato scaduto: non e
            // un guasto del tablet e non deve diventare un crash. Si riprova
            // al giro dopo.
            Log.w(TAG, "sincronizzazione fallita: ${e.javaClass.simpleName} ${e.message}")
            Esito.Fallita(e.message ?: e.javaClass.simpleName)
        }
    }

    /** Quanti campi del documento questo tablet sa applicare. */
    private const val CAMPI_APPLICABILI = 13

    /**
     * Applica il documento e torna i campi che NON e riuscito ad applicare,
     * con il codice del motivo. I codici sono quelli che la web UI sa
     * tradurre (MOTIVI in server/web/app.js): uno nuovo qui vuole una riga
     * anche li, altrimenti al genitore compare "motivo non riconosciuto".
     */
    private fun applica(
        context: Context,
        prefs: Prefs,
        d: JSONObject,
    ): List<Pair<String, String>> {
        val rifiutati = mutableListOf<Pair<String, String>>()
        val prima = prefs.current

        // --- aspetto ------------------------------------------------------
        val tema = d.optString("tema", prima.skin)
        val sfondo = d.optString("sfondo", prima.background)
        prefs.setSkin(tema, sfondo)
        if (prima.background != BACKGROUND_PHOTO) {
            prefs.setBackground(sfondo)
        } else {
            // Lo sfondo fotografico l'ha scelto il genitore sul tablet, e la
            // foto non passa dal server: una tinta non lo sostituisce.
            rifiutati += "sfondo" to "valore_non_applicabile"
        }
        prefs.setHomeLayout(d.optString("schermata", prima.homeLayout))
        prefs.setOrientation(d.optString("orientamento", prima.orientation))
        prefs.setSoundOn(d.optBoolean("suoni", prima.soundOn))

        // --- tempo --------------------------------------------------------
        prefs.setDailyLimit(d.optInt("limite_giornaliero", prima.dailyLimitMinutes))
        d.optJSONObject("fascia")?.let { f ->
            prefs.setScheduleEnabled(f.optBoolean("attiva", prima.scheduleEnabled))
            prefs.setWindow(
                f.optInt("inizio", prima.startMinuteOfDay),
                f.optInt("fine", prima.endMinuteOfDay),
            )
            f.optJSONArray("giorni")?.let { g ->
                prefs.setActiveDays((0 until g.length()).map { g.optInt(it) }.toSet())
            }
        }

        // --- app e giochi -------------------------------------------------
        d.optJSONArray("app_permesse")?.let { a ->
            val chieste = (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }
            val installate = chieste.filter { installata(context, it) }
            prefs.setAllowed(installate.toSet())
            if (installate.size != chieste.size) {
                rifiutati += "app_permesse" to "app_non_installata"
            }
        }
        // Le regole per singola app arrivano tutte insieme: quello che non e
        // nell'elenco non c'e piu, come per app_permesse. Applicarne una
        // meta - i minuti nuovi con la fascia vecchia - sarebbe peggio che
        // non applicarne nessuna.
        d.optJSONArray("regole_app")?.let { r ->
            val minuti = mutableMapOf<String, Int>()
            val fasce = mutableMapOf<String, FasciaApp>()
            for (i in 0 until r.length()) {
                val regola = r.optJSONObject(i) ?: continue
                val pacchetto = regola.optString("pacchetto")
                if (pacchetto.isBlank()) continue
                regola.optInt("minuti", 0).let { if (it > 0) minuti[pacchetto] = it }
                val f = regola.optJSONObject("fascia")
                if (f != null && f.optBoolean("attiva", false)) {
                    val giorni = f.optJSONArray("giorni")
                        ?.let { g -> (0 until g.length()).map { g.optInt(it) }.toSet() }
                        ?.filter { it in 1..7 }
                        ?.toSet()
                        .orEmpty()
                    if (giorni.isNotEmpty()) {
                        fasce[pacchetto] = FasciaApp(f.optInt("inizio"), f.optInt("fine"), giorni)
                    }
                }
            }
            prefs.setAppLimits(minuti)
            prefs.setAppWindows(fasce)
        }
        d.optJSONArray("giochi_spenti")?.let { g ->
            prefs.setDisabledGames((0 until g.length()).map { g.optString(it) }.toSet())
        }

        // --- kiosk --------------------------------------------------------
        // Asimmetrico di proposito: spegnerlo vale subito, accenderlo e solo
        // una richiesta. Un genitore che accende il kiosk da una pagina web
        // sta cambiando il tablet in mano a un bambino: la conferma col PIN
        // e li perche qualcuno davanti al tablet lo sappia.
        val kioskChiesto = d.optBoolean("kiosk", prima.kioskEnabled)
        when {
            !kioskChiesto && prima.kioskEnabled -> {
                prefs.setKioskEnabled(false)
                Kiosk.releasePolicies(context)
            }
            kioskChiesto && !prima.kioskEnabled -> {
                rifiutati += "kiosk" to if (Kiosk.canManage(context)) {
                    "serve_conferma_pin"
                } else {
                    "serve_device_owner"
                }
            }
        }
        prefs.setKioskRecents(d.optBoolean("kiosk_recenti", prima.kioskRecents))

        // --- blocco a distanza --------------------------------------------
        // Questo invece si applica subito in tutti e due i versi: e il
        // genitore che chiude e che riapre, e non c'e niente da confermare
        // sul tablet. Un blocco che si annulla dal tablet non sarebbe un
        // blocco.
        prefs.setBloccatoDalGenitore(d.optBoolean("bloccato", false))

        return rifiutati
    }

    private fun installata(context: Context, pacchetto: String): Boolean = runCatching {
        context.packageManager.getApplicationInfo(pacchetto, 0)
        true
    }.getOrDefault(false)

    private fun laCasaEStarKid(context: Context): Boolean = runCatching {
        val casa = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        context.packageManager
            .resolveActivity(casa, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName == context.packageName
    }.getOrDefault(false)
}
