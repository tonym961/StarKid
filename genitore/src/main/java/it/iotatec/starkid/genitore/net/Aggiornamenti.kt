package it.iotatec.starkid.genitore.net

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import it.iotatec.starkid.genitore.BuildConfig
import org.json.JSONArray
import java.io.BufferedReader
import java.io.File
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

/**
 * L'aggiornamento dell'app dei genitori, preso da GitHub.
 *
 * Questa app non passa dal Play Store: senza, l'unico modo di aggiornarla
 * sarebbe andare sul sito, scaricare il file e aprirlo a mano, e chi lo fa
 * una volta non lo fa la seconda. Quindi se lo guarda da sola.
 *
 * DA DOVE. Dalle release del repository, che e **pubblico**: nessun token
 * dentro l'APK, ed e la ragione per cui questa strada si puo percorrere. Se
 * un giorno il repository diventasse privato, questo smetterebbe di
 * funzionare e andrebbe rifatto in un altro modo - non mettendo un token qui
 * dentro, che sarebbe un segreto consegnato a chiunque abbia l'APK.
 *
 * COSA CONTROLLA PRIMA DI INSTALLARE, in ordine:
 *
 *  1. L'indirizzo e di GitHub ed e in https. Anche dopo i rimbalzi, che
 *     GitHub fa sempre: il file vero sta su un altro host.
 *  2. Il file non e assurdamente grande.
 *  3. **La firma e la nostra.** E il controllo che conta. Un APK firmato da
 *     un altro non si installa sopra a questo - lo rifiuta Android - ma
 *     accorgersene qui vuol dire poterlo dire a parole, invece di lasciare
 *     un errore di sistema che non spiega niente.
 *
 * E non installa niente da sola: apre l'installatore di Android, che chiede
 * il permesso a chi guarda. Una app che si aggiorna in silenzio e una app
 * che puo cambiare da sotto le mani.
 */
object Aggiornamenti {

    const val REPO = "tonym961/StarKid"

    /**
     * Le ultime release, non solo "latest": una release puo portare solo il
     * launcher, e allora l'APK dei genitori li dentro non c'e. Si cerca la
     * piu recente che ce l'ha.
     */
    private const val ELENCO = "https://api.github.com/repos/" + REPO + "/releases?per_page=10"

    /**
     * La seconda strada: la cartella dist del repository.
     *
     * Le APK firmate stanno li da prima che esistessero le release, e ci
     * restano - la regola scritta in dist/LEGGIMI.md e che le vecchie non si
     * cancellano. Guardare tutte e due vuol dire che pubblicare una versione
     * nuova e una cosa sola (un push), e che chi preferisce le release puo
     * usare quelle senza cambiare niente qui.
     */
    private const val CARTELLA = "https://api.github.com/repos/" + REPO + "/contents/dist"

    /**
     * Il nome dice la versione, e l'ultimo numero e il versionCode: e la
     * regola scritta in build.gradle.kts, la stessa da cui nasce l'APK.
     */
    private val NOME_APK = Regex("^StarKid-Genitori-([0-9]+)\\.([0-9]+)\\.([0-9]+)\\.apk$")

    private val HOST_AMMESSI = listOf("github.com", "githubusercontent.com")

    private const val BYTE_MASSIMI = 80L * 1024 * 1024

    data class Novita(
        val versione: String,
        val codice: Int,
        val indirizzo: String,
        val byte: Long,
        val note: String,
    )

    /**
     * La versione nuova, o null se questa e gia l'ultima. Fuori dal thread
     * principale.
     *
     * Guarda nelle release e nella cartella dist, e tiene la piu alta delle
     * due. Se una delle due strade non risponde - GitHub che limita le
     * chiamate, una rete storta - vale quello che ha trovato l'altra invece
     * di non dire niente.
     */
    fun cerca(): Novita? {
        val daRelease = runCatching { nelleRelease() }.getOrNull()
        val daCartella = runCatching { nellaCartella() }.getOrNull()
        if (daRelease == null && daCartella == null) {
            // Tutte e due andate male: si rilancia, cosi chi ha chiesto il
            // controllo a mano riceve un errore invece di un "sei
            // aggiornato" che non e stato verificato.
            nelleRelease()
        }
        return listOfNotNull(daRelease, daCartella).maxByOrNull { it.codice }
    }

    private fun nellaCartella(): Novita? {
        val dentro = JSONArray(scaricaTesto(CARTELLA))
        var migliore: Novita? = null
        for (i in 0 until dentro.length()) {
            val voce = dentro.optJSONObject(i) ?: continue
            if (voce.optString("type") != "file") continue
            val pezzi = NOME_APK.find(voce.optString("name")) ?: continue
            val codice = pezzi.groupValues[3].toIntOrNull() ?: continue
            if (codice <= BuildConfig.VERSION_CODE) continue
            if (migliore != null && codice <= migliore.codice) continue
            val indirizzo = voce.optString("download_url")
            if (indirizzo.isBlank()) continue
            migliore = Novita(
                versione = pezzi.groupValues[1] + "." + pezzi.groupValues[2] +
                    "." + pezzi.groupValues[3],
                codice = codice,
                indirizzo = indirizzo,
                byte = voce.optLong("size"),
                note = "",
            )
        }
        return migliore
    }

    private fun nelleRelease(): Novita? {
        val release = JSONArray(scaricaTesto(ELENCO))
        var migliore: Novita? = null
        for (i in 0 until release.length()) {
            val voce = release.optJSONObject(i) ?: continue
            if (voce.optBoolean("draft") || voce.optBoolean("prerelease")) continue
            val allegati = voce.optJSONArray("assets") ?: continue
            for (j in 0 until allegati.length()) {
                val allegato = allegati.optJSONObject(j) ?: continue
                val pezzi = NOME_APK.find(allegato.optString("name")) ?: continue
                val codice = pezzi.groupValues[3].toIntOrNull() ?: continue
                if (codice <= BuildConfig.VERSION_CODE) continue
                if (migliore != null && codice <= migliore.codice) continue
                migliore = Novita(
                    versione = pezzi.groupValues[1] + "." + pezzi.groupValues[2] +
                        "." + pezzi.groupValues[3],
                    codice = codice,
                    indirizzo = allegato.optString("browser_download_url"),
                    byte = allegato.optLong("size"),
                    note = voce.optString("body").trim().take(500),
                )
            }
        }
        return migliore
    }

    /**
     * Scarica l'APK e torna il file. Fuori dal thread principale.
     *
     * Va nella cartella cache: se poi non si installa, il sistema se lo
     * riprende quando gli serve spazio. Un APK dimenticato in Download resta
     * li per anni.
     */
    fun scarica(context: Context, novita: Novita): File {
        if (novita.byte > BYTE_MASSIMI) {
            throw ErroreServer(0, "Il file e troppo grande per essere l'app dei genitori.")
        }
        val cartella = File(context.cacheDir, "aggiornamenti").apply { mkdirs() }
        cartella.listFiles()?.forEach { it.delete() }
        val file = File(cartella, "StarKid-Genitori-" + novita.versione + ".apk")

        val conn = apri(novita.indirizzo, seguiRimbalzi = true)
        try {
            if (conn.responseCode !in 200..299) {
                throw ErroreServer(conn.responseCode, "GitHub non ha dato il file.")
            }
            // Dopo i rimbalzi: GitHub manda su un altro host, e anche quello
            // deve essere suo.
            controllaHost(conn.url)
            var scritti = 0L
            conn.inputStream.use { dentro ->
                file.outputStream().use { fuori ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val quanti = dentro.read(buffer)
                        if (quanti <= 0) break
                        scritti += quanti
                        if (scritti > BYTE_MASSIMI) {
                            throw ErroreServer(0, "Il file continua ad arrivare: mi fermo.")
                        }
                        fuori.write(buffer, 0, quanti)
                    }
                }
            }
        } catch (e: Exception) {
            file.delete()
            throw e
        } finally {
            conn.disconnect()
        }

        if (!firmaNostra(context, file)) {
            file.delete()
            throw ErroreServer(
                0,
                "Il file scaricato non e firmato con la chiave di StarKid: non lo installo. " +
                    "Prendilo a mano dal repository e confronta l'impronta scritta in dist/LEGGIMI.md.",
            )
        }
        return file
    }

    /** Apre l'installatore di Android. Decide una persona, non questa app. */
    fun installa(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".file", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Android vuole un permesso a parte per installare da qui, e si concede
     * solo dalle impostazioni. Senza, l'installatore si apre e non fa niente.
     */
    fun puoInstallare(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    fun chiediPermessoDiInstallare(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(Uri.parse("package:" + context.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    // -----------------------------------------------------------------------

    private fun scaricaTesto(indirizzo: String): String {
        val conn = apri(indirizzo, seguiRimbalzi = false)
        try {
            val stato = conn.responseCode
            val testo = (if (stato in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            if (stato !in 200..299) {
                throw ErroreServer(
                    stato,
                    "GitHub ha risposto " + stato + " mentre cercavo gli aggiornamenti.",
                )
            }
            return testo
        } finally {
            conn.disconnect()
        }
    }

    private fun apri(indirizzo: String, seguiRimbalzi: Boolean): HttpsURLConnection {
        val url = URL(indirizzo)
        controllaHost(url)
        return (url.openConnection() as HttpsURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = seguiRimbalzi
            setRequestProperty("Accept", "application/vnd.github+json")
            // GitHub rifiuta chi non si presenta.
            setRequestProperty("User-Agent", "StarKidGenitori/" + BuildConfig.VERSION_NAME)
        }
    }

    private fun controllaHost(url: URL) {
        if (url.protocol != "https") {
            throw ErroreServer(0, "L'aggiornamento si scarica solo in https.")
        }
        val host = url.host.lowercase()
        if (HOST_AMMESSI.none { host == it || host.endsWith("." + it) }) {
            throw ErroreServer(
                0,
                "L'aggiornamento arriverebbe da " + host + ", che non e GitHub: mi fermo.",
            )
        }
    }

    /**
     * La firma dell'APK scaricato e la stessa di quella in esecuzione.
     *
     * Android lo controlla comunque al momento di installare e rifiuta; qui
     * si controlla prima per poterlo dire con parole.
     */
    private fun firmaNostra(context: Context, file: File): Boolean = runCatching {
        val pm = context.packageManager
        val nostre = impronte(firmeDa(pm.getPackageInfo(context.packageName, bandiera())))
        val suo = pm.getPackageArchiveInfo(file.absolutePath, bandiera()) ?: return false
        if (suo.packageName != context.packageName) return false
        nostre.isNotEmpty() && nostre == impronte(firmeDa(suo))
    }.getOrDefault(false)

    @Suppress("DEPRECATION") // GET_SIGNATURES serve ancora sotto Android 9
    private fun bandiera(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }

    @Suppress("DEPRECATION")
    private fun firmeDa(info: PackageInfo): Array<out Signature> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.let { firme ->
                if (firme.hasMultipleSigners()) firme.apkContentsSigners
                else firme.signingCertificateHistory
            }.orEmpty()
        } else {
            info.signatures.orEmpty()
        }

    private fun impronte(firme: Array<out Signature>): Set<String> {
        val digest = MessageDigest.getInstance("SHA-256")
        return firme.map { firma ->
            digest.digest(firma.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    }
}
