package it.iotatec.starkid.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.Intent
import android.os.IBinder
import it.iotatec.starkid.LockActivity
import it.iotatec.starkid.MainActivity
import it.iotatec.starkid.R
import it.iotatec.starkid.data.AreaGenitori
import it.iotatec.starkid.data.InstalledApps
import it.iotatec.starkid.data.Prefs
import it.iotatec.starkid.data.Risvegli
import it.iotatec.starkid.domain.BlockReason
import it.iotatec.starkid.domain.Guard
import it.iotatec.starkid.sync.Sync
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
    private var sincronizzatore: Job? = null

    /** Quando si e provato l'ultima volta a riportare davanti StarKid. */
    private var ultimoRientro = 0L

    private lateinit var prefs: Prefs
    private lateinit var usage: UsageReader

    /**
     * "Il tablet si e riacceso". Sta qui e non nell'Activity perche
     * ACTION_SCREEN_OFF arriva solo a un ricevitore registrato a runtime, e
     * un ricevitore dell'Activity vive quanto l'Activity: se lo schermo si
     * spegne mentre il bambino e dentro un'altra app permessa, StarKid e
     * ferma e non lo riceve. Il servizio invece gira sempre.
     */
    private val schermoSpento = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) Risvegli.schermoSpento()
        }
    }

    override fun onCreate() {
        super.onCreate()
        inMarcia = true
        prefs = Prefs.get(this)
        usage = UsageReader(this)
        createChannel()
        registerReceiver(schermoSpento, IntentFilter(Intent.ACTION_SCREEN_OFF))
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
        // La sincronizzazione col server ha un giro suo, lento e su
        // Dispatchers.IO: non deve mai far aspettare il controllo delle app,
        // che gira ogni quattro secondi. La sveglia e di un minuto e la pausa
        // dentro Sync.esegui() e di cinquantacinque secondi, quindi il server
        // si chiama una volta al minuto; il margine c'e perche con i due
        // numeri uguali un orologio che si riallinea farebbe saltare un giro.
        if (sincronizzatore?.isActive != true) {
            sincronizzatore = scope.launch(Dispatchers.IO) {
                while (isActive) {
                    runCatching { Sync.esegui(applicationContext) }
                    delay(60_000L)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        inMarcia = false
        runCatching { unregisterReceiver(schermoSpento) }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun tick() {
        val config = prefs.current
        // Il genitore e davanti, dentro la sua area: niente blocchi. Senza
        // questa riga, fuori orario la schermata di blocco ricompariva SOPRA
        // l'area genitori ogni quattro secondi, e non si riusciva a cambiare
        // l'orario che la stava facendo comparire.
        if (AreaGenitori.dentro) return
        // Manutenzione: il genitore e uscito apposta verso un'altra app.
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
        // Nessun blocco generale, ma il blocco potrebbe essere ancora davanti
        // per un motivo appena caduto: il genitore ha riaperto il tablet dalla
        // web UI, e arrivata l'ora di accendersi, un bonus ha rimesso minuti.
        // Senza questa riga il bambino restava davanti a "Il tablet e chiuso"
        // finche non premeva indietro - e il blocco e fatto apposta perche non
        // gli venga in mente di premere niente.
        nascondiBloccoGenerale()

        val foreground = usage.foregroundPackage(POLL_MILLIS * 4) ?: return
        if (foreground == packageName) return

        // Davanti c'e un altro launcher. Non e una app che il bambino ha
        // aperto: e StarKid che e stata messa da parte, e con lei se n'e
        // andato il lock task. Succede a ogni aggiornamento dell'app, che
        // chiude l'activity, e succede dopo un arresto. Visto sul TB310FU:
        // tablet lasciato sul launcher Lenovo, con dentro tutte le app, e del
        // kiosk non restava niente mentre il server lo dava per acceso.
        //
        // Non si mostra nessun blocco - al bambino non c'e niente da
        // spiegare - si torna a casa, e rientrando in MainActivity rientra
        // anche il lock task.
        if (config.kioskEnabled && foreground in InstalledApps.homePackages(this)) {
            tornaACasa()
            return
        }

        // Interessano solo le vere app: barra di sistema, tastiere e servizi non
        // vanno toccati, o il blocco scatterebbe a ogni notifica.
        if (foreground !in InstalledApps.launchablePackages(this)) return

        val appUsed = ((usageMillis[foreground] ?: 0L) / 60_000L).toInt()
        val verdict = Guard.checkApp(config, foreground, appUsed, now.get(Calendar.DAY_OF_WEEK), minuteOfDay)
        if (verdict != BlockReason.NONE) showLock(verdict)
    }

    /**
     * Chiude il blocco se e davanti per un motivo che vale per tutto il
     * tablet e che adesso non vale piu.
     *
     * I motivi legati alla singola app non si toccano: quando il blocco e
     * davanti, l'app vietata non e piu in primo piano - il blocco lo e - e
     * toglierlo riporterebbe il bambino dentro l'app, per farcelo ribloccare
     * quattro secondi dopo. Da li si esce con indietro, che porta a casa.
     */
    /**
     * Riporta davanti StarKid, senza dire niente a nessuno.
     *
     * Con la pausa in mezzo perche, se l'avvio da sottofondo venisse
     * rifiutato, questa verrebbe richiamata ogni quattro secondi per sempre.
     * Quando invece funziona la pausa non si vede: al giro dopo davanti c'e
     * StarKid e non si passa nemmeno di qui.
     */
    private fun tornaACasa() {
        val adesso = System.currentTimeMillis()
        if (adesso - ultimoRientro < RIENTRO_MILLIS) return
        ultimoRientro = adesso
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        runCatching { startActivity(intent) }
    }

    private fun nascondiBloccoGenerale() {
        val motivo = LockActivity.motivoInCorso ?: return
        if (motivo !in MOTIVI_GENERALI) return
        val intent = Intent(this, LockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(LockActivity.EXTRA_REASON, BlockReason.NONE.name)
        }
        runCatching { startActivity(intent) }
    }

    private fun showLock(reason: BlockReason) {
        // Il blocco e gia davanti con questo motivo: rilanciarlo non cambia
        // niente sullo schermo e costa un avvio di Activity ogni quattro
        // secondi, per tutto il tempo che dura il blocco.
        if (LockActivity.giaMostrato(reason)) return
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
        /**
         * Il servizio e vivo. Serve alla spia "servizio attivo" che il tablet
         * manda al server: un flag messo e tolto da onCreate e onDestroy dice
         * la verita, mentre chiedere ad ActivityManager chi sta girando, da
         * Android 8, risponde solo delle proprie e in modo impreciso.
         */
        @Volatile
        var inMarcia: Boolean = false
            private set

        private const val CHANNEL_ID = "guard"
        private const val NOTIFICATION_ID = 42
        private const val POLL_MILLIS = 4_000L

        /** Quanto si aspetta prima di riprovare a tornare a casa. */
        private const val RIENTRO_MILLIS = 10_000L

        /**
         * I motivi che non dipendono da quale app c'era davanti, e che
         * possono cadere da soli mentre il blocco e ancora sullo schermo.
         */
        private val MOTIVI_GENERALI = setOf(
            BlockReason.LOCKED_BY_PARENT,
            BlockReason.OUTSIDE_SCHEDULE,
            BlockReason.DAILY_LIMIT,
        )

        fun start(context: Context) {
            val intent = Intent(context.applicationContext, GuardService::class.java)
            // Da Android 12 l'avvio di un servizio in foreground dal background
            // puo essere rifiutato: la chiamata affidabile e quella fatta dal
            // launcher mentre e in primo piano.
            runCatching { context.applicationContext.startForegroundService(intent) }
        }
    }
}
