package it.iotatec.starkid.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import it.iotatec.starkid.LockActivity
import it.iotatec.starkid.MainActivity
import it.iotatec.starkid.R
import it.iotatec.starkid.data.InstalledApps
import it.iotatec.starkid.data.Prefs
import it.iotatec.starkid.domain.BlockReason
import it.iotatec.starkid.domain.Guard
import it.iotatec.starkid.usage.UsageReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Sorveglia cosa c'e in primo piano e riporta il bambino alla schermata di
 * blocco quando apre un'app non consentita, finisce il tempo o esce dalla
 * fascia oraria.
 *
 * Perche funzioni servono due permessi concessi a mano dal genitore:
 * l'accesso ai dati di utilizzo (per sapere quale app e in primo piano) e il
 * disegno sopra le altre app (per poter mostrare il blocco da dietro le
 * quinte). In modalita kiosk il Device Owner copre entrambe le esigenze.
 */
class GuardService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var watcher: Job? = null

    private lateinit var prefs: Prefs
    private lateinit var usage: UsageReader

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs.get(this)
        usage = UsageReader(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        if (watcher?.isActive != true) {
            watcher = scope.launch {
                while (isActive) {
                    runCatching { tick() }
                    delay(POLL_MILLIS)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun tick() {
        val config = prefs.current
        if (System.currentTimeMillis() < config.guardPausedUntil) return
        if (!config.pinSet) return
        if (!usage.hasPermission()) return

        val usageMillis = usage.usageTodayMillis()
        val usedToday =
            (config.allowedPackages.sumOf { usageMillis[it] ?: 0L } / 60_000L).toInt()
        val now = Calendar.getInstance()
        val minuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        val global = Guard.checkGlobal(
            config = config,
            dayOfWeek = now.get(Calendar.DAY_OF_WEEK),
            minuteOfDay = minuteOfDay,
            usedMinutesToday = usedToday,
            today = Prefs.todayKey(),
        )
        if (global != BlockReason.NONE) {
            showLock(global)
            return
        }

        val foreground = usage.foregroundPackage(POLL_MILLIS * 4) ?: return
        if (foreground == packageName) return
        // Interessano solo le vere app: barra di sistema, tastiere e servizi non
        // vanno toccati, o il blocco scatterebbe a ogni notifica.
        if (foreground !in InstalledApps.launchablePackages(this)) return

        val appUsed = ((usageMillis[foreground] ?: 0L) / 60_000L).toInt()
        val verdict = Guard.checkApp(config, foreground, appUsed)
        if (verdict != BlockReason.NONE) showLock(verdict)
    }

    private fun showLock(reason: BlockReason) {
        val intent = Intent(this, LockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(LockActivity.EXTRA_REASON, reason.name)
        }
        runCatching { startActivity(intent) }
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.guard_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { setShowBadge(false) }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.guard_notification_title))
            .setSmallIcon(R.drawable.ic_shield)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "guard"
        private const val NOTIFICATION_ID = 42
        private const val POLL_MILLIS = 4_000L

        fun start(context: Context) {
            val intent = Intent(context.applicationContext, GuardService::class.java)
            // Da Android 12 l'avvio di un servizio in foreground dal background
            // puo essere rifiutato: la chiamata affidabile e quella fatta dal
            // launcher mentre e in primo piano.
            runCatching { context.applicationContext.startForegroundService(intent) }
        }
    }
}
