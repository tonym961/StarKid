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

    /** Vero se l'app e Device Owner e quindi il kiosk vero e possibile. */
    fun isDeviceOwner(context: Context): Boolean = runCatching {
        policyManager(context).isDeviceOwnerApp(context.applicationContext.packageName)
    }.getOrDefault(false)

    /** Comando adb da dare una volta sola, mostrato nell'area genitori. */
    fun provisioningCommand(context: Context): String =
        "adb shell dpm set-device-owner " +
            "${context.packageName}/${KidsDeviceAdminReceiver::class.java.name}"

    /**
     * Applica le politiche del kiosk. Da richiamare a ogni modifica della
     * whitelist, perche le app permesse devono poter girare in lock task.
     */
    fun applyPolicies(context: Context, allowedPackages: Set<String>) {
        if (!isDeviceOwner(context)) return
        val dpm = policyManager(context)
        val admin = admin(context)
        val app = context.applicationContext

        runCatching {
            dpm.setLockTaskPackages(admin, (allowedPackages + app.packageName).toTypedArray())
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                // Il tasto Home resta attivo e riporta al launcher; tutto il
                // resto (recenti, notifiche, barra di stato) resta chiuso.
                dpm.setLockTaskFeatures(
                    admin,
                    DevicePolicyManager.LOCK_TASK_FEATURE_HOME or
                        DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD,
                )
            }
        }

        // Home senza possibilita di scelta: il launcher e l'unica opzione.
        runCatching {
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

        runCatching { dpm.setUninstallBlocked(admin, app.packageName, true) }
        runCatching { dpm.setStatusBarDisabled(admin, true) }
        runCatching { dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT) }
        runCatching { dpm.addUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET) }
        runCatching { dpm.addUserRestriction(admin, UserManager.DISALLOW_ADD_USER) }
        runCatching { dpm.addUserRestriction(admin, UserManager.DISALLOW_UNINSTALL_APPS) }
    }

    /** Rimuove le politiche: il tablet torna a comportarsi normalmente. */
    fun releasePolicies(context: Context) {
        if (!isDeviceOwner(context)) return
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

    /** Entra in lock task. Senza Device Owner non si fa nulla, di proposito. */
    fun enter(activity: Activity) {
        if (!isDeviceOwner(activity)) return
        if (isLocked(activity)) return
        runCatching { activity.startLockTask() }
    }

    fun exit(activity: Activity) {
        if (!isLocked(activity)) return
        runCatching { activity.stopLockTask() }
    }
}
