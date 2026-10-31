package it.iotatec.starkid.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * Riferimento richiesto da Android per fare di questa app il Device Owner.
 * Le politiche le applica [Kiosk]; qui si raccoglie soltanto la
 * configurazione consegnata al momento del provisioning.
 */
class KidsDeviceAdminReceiver : DeviceAdminReceiver() {

    /**
     * Android chiama questo metodo quando il provisioning e andato a buon
     * fine, e nell'intent c'e il sacchetto di valori messo nel QR. E l'unica
     * occasione per configurare il tablet senza che nessuno lo tocchi.
     */
    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        Provisioning.apply(context, intent)
    }
}
