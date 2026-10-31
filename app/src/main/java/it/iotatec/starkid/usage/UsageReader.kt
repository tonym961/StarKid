package it.iotatec.starkid.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import java.util.Calendar

/**
 * Legge quanto tempo e stata usata ogni app e quale app e in primo piano.
 *
 * Serve il permesso "Accesso ai dati di utilizzo", che il genitore concede a
 * mano dalle impostazioni di sistema: senza quello il launcher mostra ancora la
 * griglia, ma limiti di tempo e blocco delle app non consentite non funzionano.
 */
class UsageReader(context: Context) {

    private val app = context.applicationContext

    private val usageStats: UsageStatsManager =
        app.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    fun hasPermission(): Boolean {
        val ops = app.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                app.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                app.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Millisecondi in primo piano oggi, per pacchetto. */
    fun usageTodayMillis(): Map<String, Long> {
        val now = System.currentTimeMillis()
        val totals = HashMap<String, Long>()
        val openedAt = HashMap<String, Long>()
        val events = runCatching { usageStats.queryEvents(startOfToday(), now) }.getOrNull()
            ?: return emptyMap()

        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                EVENT_RESUMED -> openedAt[event.packageName] = event.timeStamp
                EVENT_PAUSED, EVENT_STOPPED -> {
                    val from = openedAt.remove(event.packageName)
                    if (from != null && event.timeStamp > from) {
                        totals[event.packageName] =
                            (totals[event.packageName] ?: 0L) + (event.timeStamp - from)
                    }
                }
            }
        }
        // Sessioni ancora aperte: contano fino ad adesso.
        for ((packageName, from) in openedAt) {
            if (now > from) {
                totals[packageName] = (totals[packageName] ?: 0L) + (now - from)
            }
        }
        return totals
    }

    /** Minuti usati oggi dalle sole app indicate. */
    fun minutesToday(packages: Set<String>): Int {
        val usage = usageTodayMillis()
        val millis = packages.sumOf { usage[it] ?: 0L }
        return (millis / 60_000L).toInt()
    }

    /** Pacchetto attualmente in primo piano, o null se non si riesce a capirlo. */
    fun foregroundPackage(lookBackMillis: Long = 60_000L): String? {
        val now = System.currentTimeMillis()
        val events = runCatching { usageStats.queryEvents(now - lookBackMillis, now) }.getOrNull()
            ?: return null

        var latest: String? = null
        var latestAt = 0L
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == EVENT_RESUMED && event.timeStamp >= latestAt) {
                latestAt = event.timeStamp
                latest = event.packageName
            }
        }
        return latest
    }

    companion object {
        // UsageEvents.Event usa nomi diversi a seconda della versione di Android
        // (MOVE_TO_FOREGROUND su API 21, ACTIVITY_RESUMED da API 29) ma i valori
        // numerici non sono mai cambiati: cosi il codice compila senza
        // deprecazioni ne controlli di versione.
        private const val EVENT_RESUMED = 1
        private const val EVENT_PAUSED = 2
        private const val EVENT_STOPPED = 23

        fun startOfToday(): Long = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        /** Impostazioni di sistema dove si concede l'accesso ai dati di utilizzo. */
        fun settingsIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

        /** Impostazioni di sistema per il disegno sopra le altre app. */
        fun overlaySettingsIntent(context: Context): Intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )
    }
}
