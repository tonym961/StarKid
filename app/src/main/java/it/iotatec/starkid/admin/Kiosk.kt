package it.iotatec.starkid.admin

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import it.iotatec.starkid.MainActivity

/**
 * Modalita kiosk.
 *
 * Il kiosk vero richiede che l'app sia Device Owner. Lo si ottiene una volta
 * sola, su un tablet senza account configurati (di fabbrica o appena
 * resettato), con:
 *
 *     adb shell dpm set-device-owner it.iotatec.starkid/it.iotatec.starkid.admin.KidsDeviceAdminReceiver
 *
 * Da Device Owner si puo bloccare la barra di stato, impedire la
 * disinstallazione e tenere il bambino dentro le sole app permesse: l'uscita
 * passa solo dal PIN del genitore.
 *
 * Senza Device Owner resterebbe solo lo screen pinning di sistema, che pero
 * confina l'utente nella singola app bloccata e quindi impedirebbe di aprire
 * anche le app consentite: per questo il kiosk non viene offerto in quel caso.
 */
object Kiosk {

    fun admin(context: Context): ComponentName =
        ComponentName(context.applicationContext, KidsDeviceAdminReceiver::class.java)

    fun policyManager(context: Context): DevicePolicyManager =
        context.applicationContext
            .getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    /** In che veste StarKid comanda su questo tablet. */
    enum class Mode { NESSUNA, PROPRIETARIO_UTENTE, PROPRIETARIO_DISPOSITIVO }

    /** Vero se l'app e Device Owner e quindi il kiosk pieno e possibile. */
    fun isDeviceOwner(context: Context): Boolean = runCatching {
        policyManager(context).isDeviceOwnerApp(context.applicationContext.packageName)
    }.getOrDefault(false)

    /**
     * Vero se StarKid e almeno proprietaria del profilo di questo utente.
     *
     * Su un tablet con piu utenti il Device Owner non si puo avere: Android
     * lo concede solo a un tablet con un utente solo e nessun account. Su un
     * utente secondario pulito si puo pero essere proprietari del profilo,
     * che da meno poteri ma non zero. Quali davvero, lo dice
     * [lastFailures] dopo aver provato: non si puo sapere in anticipo
     * perche cambia da una versione di Android all'altra.
     */
    fun isProfileOwner(context: Context): Boolean = runCatching {
        policyManager(context).isProfileOwnerApp(context.applicationContext.packageName)
    }.getOrDefault(false)

    fun mode(context: Context): Mode = when {
        isDeviceOwner(context) -> Mode.PROPRIETARIO_DISPOSITIVO
        isProfileOwner(context) -> Mode.PROPRIETARIO_UTENTE
        else -> Mode.NESSUNA
    }

    /** Vero se c'e abbastanza potere per provare ad accendere il kiosk. */
    fun canManage(context: Context): Boolean = mode(context) != Mode.NESSUNA

    /** Comando adb da dare una volta sola, mostrato nell'area genitori. */
    fun provisioningCommand(context: Context): String =
        "adb shell dpm set-device-owner " +
            "${context.packageName}/${KidsDeviceAdminReceiver::class.java.name}"

    /**
     * Il pacchetto delle impostazioni di sistema, chiesto ad Android invece
     * che scritto a mano: non su tutti i dispositivi si chiama
     * `com.android.settings`.
     */
    fun settingsPackage(context: Context): String? = runCatching {
        context.packageManager
            .resolveActivity(Intent(Settings.ACTION_SETTINGS), 0)
            ?.activityInfo
            ?.packageName
    }.getOrNull()

    /**
     * Quali politiche non sono riuscite. Vuoto vuol dire tutto a posto; si
     * mostra al genitore nella schermata del kiosk, perche una politica che
     * fallisce in silenzio e peggio di una che non c'e: si crede che il
     * tablet sia bloccato e non lo e.
     */
    @Volatile
    var lastFailures: List<String> = emptyList()
        private set

    /**
     * Applica le politiche del kiosk. Da richiamare a ogni modifica della
     * whitelist, perche le app permesse devono poter girare in lock task.
     *
     * [allowSettings] apre un varco per le impostazioni di sistema. Si usa
     * solo mentre il genitore e dentro la sua area, protetta dal PIN, e si
     * richiude appena esce: un varco permanente sarebbe la via d'uscita dal
     * kiosk per chiunque sappia dove toccare.
     */
    fun applyPolicies(
        context: Context,
        allowedPackages: Set<String>,
        allowSettings: Boolean = false,
        allowRecents: Boolean = false,
    ) {
        if (!canManage(context)) return
        val dpm = policyManager(context)
        val admin = admin(context)
        val app = context.applicationContext
        val failures = mutableListOf<String>()

        fun attempt(what: String, block: () -> Unit) {
            runCatching(block).onFailure { failures.add(what) }
        }

        val settings = if (allowSettings) settingsPackage(context) else null
        attempt("elenco delle app ammesse") {
            val packages = allowedPackages + app.packageName + listOfNotNull(settings)
            dpm.setLockTaskPackages(admin, packages.toTypedArray())
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            attempt("blocco di notifiche e recenti") {
                // Si accende SOLO quello che e elencato qui: tutto il resto
                // resta spento, comprese notifiche, tendina e menu di
                // spegnimento. Le recenti sono una scelta del genitore.
                var features = DevicePolicyManager.LOCK_TASK_FEATURE_HOME
                if (allowRecents) {
                    features = features or DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW
                }
                dpm.setLockTaskFeatures(admin, features)
            }
        } else {
            failures.add("blocco di notifiche e recenti (serve Android 9 o piu recente)")
        }

        // Home senza possibilita di scelta: il launcher e l'unica opzione.
        attempt("home fissa sul launcher") {
            val home = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            dpm.addPersistentPreferredActivity(
                admin,
                home,
                ComponentName(app, MainActivity::class.java),
            )
        }

        attempt("disinstallazione bloccata") { dpm.setUninstallBlocked(admin, app.packageName, true) }
        attempt("barra di stato nascosta") { dpm.setStatusBarDisabled(admin, true) }
        attempt("avvio in modalita sicura bloccato") {
            dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
        }
        attempt("ripristino di fabbrica bloccato") {
            dpm.addUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET)
        }
        attempt("nuovi utenti bloccati") {
            dpm.addUserRestriction(admin, UserManager.DISALLOW_ADD_USER)
        }
        attempt("disinstallazioni bloccate") {
            dpm.addUserRestriction(admin, UserManager.DISALLOW_UNINSTALL_APPS)
        }
        // Senza questa, dalla tendina o da una notifica si arriva alle
        // impostazioni e da li si esce da tutto.
        attempt("modifica degli account bloccata") {
            dpm.addUserRestriction(admin, UserManager.DISALLOW_MODIFY_ACCOUNTS)
        }

        lastFailures = failures
    }

    /** Rimuove le politiche: il tablet torna a comportarsi normalmente. */
    fun releasePolicies(context: Context) {
        if (!canManage(context)) return
        val dpm = policyManager(context)
        val admin = admin(context)
        val app = context.applicationContext

        runCatching { dpm.setStatusBarDisabled(admin, false) }
        runCatching { dpm.clearPackagePersistentPreferredActivities(admin, app.packageName) }
        runCatching { dpm.setUninstallBlocked(admin, app.packageName, false) }
        runCatching { dpm.clearUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT) }
        runCatching { dpm.clearUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET) }
        runCatching { dpm.clearUserRestriction(admin, UserManager.DISALLOW_ADD_USER) }
        runCatching { dpm.clearUserRestriction(admin, UserManager.DISALLOW_UNINSTALL_APPS) }
        runCatching { dpm.setLockTaskPackages(admin, emptyArray()) }
    }

    /**
     * Restituisce il Device Owner al sistema. Serve per smontare il kiosk senza
     * resettare il tablet; su qualche build il metodo non e disponibile e in
     * quel caso resta solo il ripristino di fabbrica.
     */
    @Suppress("DEPRECATION") // clearDeviceOwnerApp e l'unico modo per rinunciare al ruolo
    fun relinquishDeviceOwner(context: Context): Boolean = runCatching {
        releasePolicies(context)
        policyManager(context).clearDeviceOwnerApp(context.applicationContext.packageName)
        true
    }.getOrDefault(false)

    // --- lock task, lato Activity ----------------------------------------

    fun isLocked(activity: Activity): Boolean {
        val am = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }

    /** Entra in lock task. Senza alcun potere non si fa nulla, di proposito. */
    fun enter(activity: Activity) {
        if (!canManage(activity)) return
        if (isLocked(activity)) return
        runCatching { activity.startLockTask() }
            .onFailure { lastFailures = lastFailures + "avvio del kiosk (${it.message})" }
    }

    fun exit(activity: Activity) {
        if (!isLocked(activity)) return
        runCatching { activity.stopLockTask() }
    }
}
