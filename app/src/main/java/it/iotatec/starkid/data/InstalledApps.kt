package it.iotatec.starkid.data

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Una app avviabile, con icona gia pronta per Compose. */
data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap,
    /** Categoria dichiarata dall'app; il genitore puo sovrascriverla. */
    val suggestedCategory: AppCategory = AppCategory.OTHER,
)

/**
 * Elenco delle app avviabili. Caricare etichette e icone di un centinaio di
 * pacchetti costa troppo per il thread principale, quindi si fa una volta su
 * Dispatchers.IO e si tiene in cache.
 */
object InstalledApps {

    // Le tessere mostrano l'icona a 96 dp: su un tablet a densita 2x servono
    // quasi 200 px, sotto i quali l'icona si vede sgranata.
    private const val ICON_PX = 224
    private const val PACKAGE_CACHE_MILLIS = 60_000L

    @Volatile
    private var cache: List<AppEntry>? = null

    @Volatile
    private var packageCache: Set<String>? = null

    @Volatile
    private var packageCacheAt = 0L

    @Volatile
    private var homeCache: Set<String>? = null

    @Volatile
    private var homeCacheAt = 0L

    suspend fun all(context: Context, refresh: Boolean = false): List<AppEntry> {
        if (!refresh) cache?.let { return it }
        return withContext(Dispatchers.IO) { load(context).also { cache = it } }
    }

    fun cached(): List<AppEntry> = cache ?: emptyList()

    /**
     * Soli nomi dei pacchetti avviabili, senza etichette ne icone: abbastanza
     * leggero da ricalcolarlo dentro il servizio di controllo, che deve sapere
     * se l'app in primo piano e una vera app o un pezzo di sistema.
     */
    @Suppress("DEPRECATION") // queryIntentActivities(Intent, Int) va bene fino a SDK 35
    fun launchablePackages(context: Context): Set<String> {
        val now = System.currentTimeMillis()
        packageCache?.let { if (now - packageCacheAt < PACKAGE_CACHE_MILLIS) return it }
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val packages = runCatching {
            context.packageManager.queryIntentActivities(query, 0)
                .map { it.activityInfo.applicationInfo.packageName }
                .toSet()
        }.getOrDefault(packageCache ?: emptySet())
        packageCache = packages
        packageCacheAt = now
        return packages
    }

    /**
     * Pacchetto ed etichetta delle app avviabili, senza icone.
     *
     * E quello che si manda al server, perche il genitore spunti le app da
     * un elenco invece di scriverne il nome a mano. Le icone non ci sono di
     * proposito: costano a caricarle e di la non servirebbero a niente, e
     * farebbero diventare un inventario di cento app qualche megabyte.
     *
     * StarKid non si elenca da sola: non e una app che il genitore debba
     * permettere, e gia tutto il resto.
     */
    @Suppress("DEPRECATION") // queryIntentActivities(Intent, Int) va bene fino a SDK 35
    fun inventario(context: Context): List<Pair<String, String>> {
        val pm = context.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return runCatching {
            pm.queryIntentActivities(query, 0)
                .asSequence()
                .map { it.activityInfo.applicationInfo }
                .filter { it.packageName != context.packageName }
                .distinctBy { it.packageName }
                .map { info ->
                    val etichetta = runCatching { pm.getApplicationLabel(info).toString() }
                        .getOrDefault(info.packageName)
                    info.packageName to etichetta
                }
                .sortedBy { it.second.lowercase() }
                .toList()
        }.getOrDefault(emptyList())
    }

    /**
     * I pacchetti che sanno fare da schermata iniziale, StarKid compresa.
     *
     * Serve al sorvegliante per accorgersi che davanti c'e **un altro
     * launcher**. Non e un'app che il bambino ha aperto: e un pezzo di
     * sistema che si e ripreso il posto di StarKid, e succede davvero - dopo
     * un aggiornamento dell'app, che chiude l'activity e con lei il lock
     * task. In [launchablePackages] non comparirebbe nemmeno, perche i
     * launcher di solito non si elencano da soli nel cassetto delle app.
     */
    @Suppress("DEPRECATION") // queryIntentActivities(Intent, Int) va bene fino a SDK 35
    fun homePackages(context: Context): Set<String> {
        val now = System.currentTimeMillis()
        homeCache?.let { if (now - homeCacheAt < PACKAGE_CACHE_MILLIS) return it }
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val packages = runCatching {
            context.packageManager.queryIntentActivities(query, 0)
                .map { it.activityInfo.applicationInfo.packageName }
                .toSet()
        }.getOrDefault(homeCache ?: emptySet())
        homeCache = packages
        homeCacheAt = now
        return packages
    }

    @Suppress("DEPRECATION") // queryIntentActivities(Intent, Int) va bene fino a SDK 35
    private fun load(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(query, 0)
            .asSequence()
            .map { it.activityInfo.applicationInfo }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .mapNotNull { info ->
                runCatching {
                    AppEntry(
                        packageName = info.packageName,
                        label = pm.getApplicationLabel(info).toString(),
                        icon = pm.getApplicationIcon(info).toImageBitmap(),
                        suggestedCategory = AppCategory.suggestFor(info),
                    )
                }.getOrNull()
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    private fun Drawable.toImageBitmap(): ImageBitmap =
        toBitmap(ICON_PX, ICON_PX).asImageBitmap()
}
