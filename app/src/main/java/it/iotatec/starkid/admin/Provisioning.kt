package it.iotatec.starkid.admin

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.os.PersistableBundle
import it.iotatec.starkid.data.Pin
import it.iotatec.starkid.data.Prefs

/**
 * Configurazione consegnata al momento del provisioning.
 *
 * Quando un tablet viene preparato inquadrando un QR, Android installa
 * StarKid come Device Owner e le passa un sacchetto di valori scelti da chi
 * ha generato il QR. E l'unico momento in cui si puo dire al tablet "sei di
 * questo bambino, e la tua configurazione sta qui" senza che nessuno debba
 * toccare le impostazioni a mano.
 *
 * Qui si legge quel sacchetto e si mette via. **Non si contatta nessun
 * server**: l'indirizzo viene solo memorizzato. StarKid continua a funzionare
 * identica senza rete, e la sincronizzazione resta una cosa che il genitore
 * accende, non un comportamento di fabbrica.
 */
object Provisioning {

    /** Chiavi del sacchetto. Vanno tenute uguali a quelle che genera il QR. */
    const val KEY_SERVER = "server"
    const val KEY_TOKEN = "token"
    const val KEY_CHILD = "bambino"
    const val KEY_SKIN = "tema"
    const val KEY_KIOSK = "kiosk"
    const val KEY_DAILY_LIMIT = "limite_giornaliero"

    /**
     * Il PIN dell'area genitori, in chiaro.
     *
     * In chiaro qui si puo, ed e l'unico posto: il QR si genera nel browser e
     * va dallo schermo alla fotocamera del tablet, come la password del
     * Wi-Fi, che sta nello stesso sacchetto per la stessa ragione. Dal server
     * invece il PIN non passa mai: li viaggia l'impronta, vedi
     * [it.iotatec.starkid.data.Pin].
     *
     * Serve perche il primo PIN, senza questo, si batte a mano sul tablet
     * appena finito il provisioning - e un tablet preparato per un bambino
     * dovrebbe essere pronto, non chiedere subito una configurazione.
     */
    const val KEY_PIN = "pin"

    /**
     * Applica quello che e arrivato col QR. Chiamata una volta sola, alla
     * fine del provisioning.
     */
    fun apply(context: Context, intent: Intent) {
        val extras = bundleFrom(intent) ?: return
        val prefs = Prefs.get(context)

        extras.getString(KEY_SERVER)?.takeIf { it.isNotBlank() }?.let { server ->
            prefs.setServer(server, extras.getString(KEY_TOKEN).orEmpty())
        }
        extras.getString(KEY_CHILD)?.takeIf { it.isNotBlank() }?.let { prefs.setChildName(it) }
        // Un PIN storto si lascia cadere invece di impostarlo: meglio un
        // tablet che chiede il PIN alla prima apertura dell'area genitori che
        // uno con dentro un PIN che chi ha fatto il QR non conosce.
        extras.getString(KEY_PIN)?.trim()?.takeIf { Pin.pinValido(it) }?.let { prefs.setPin(it) }
        extras.getString(KEY_SKIN)?.takeIf { it.isNotBlank() }?.let { skin ->
            prefs.setSkin(skin, defaultBackgroundFor(skin))
        }
        if (extras.containsKey(KEY_DAILY_LIMIT)) {
            prefs.setDailyLimit(extras.getInt(KEY_DAILY_LIMIT, 0))
        }
        if (extras.getBoolean(KEY_KIOSK, false)) {
            prefs.setKioskEnabled(true)
            Kiosk.applyPolicies(context, prefs.current.allowedPackages)
        }
    }

    @Suppress("DEPRECATION") // getParcelableExtra tipizzato esiste solo da Android 13
    private fun bundleFrom(intent: Intent): PersistableBundle? = runCatching {
        intent.getParcelableExtra<PersistableBundle>(
            DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE,
        )
    }.getOrNull()

    /**
     * Lo sfondo iniziale del tema chiesto. Sta qui e non nella UI perche al
     * provisioning non c'e nessuna schermata aperta da cui chiederlo.
     */
    private fun defaultBackgroundFor(skin: String): String =
        if (skin == it.iotatec.starkid.data.SKIN_SPACE) "notte" else "lilla"
}
