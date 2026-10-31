package it.iotatec.starkid.admin

import android.app.admin.DeviceAdminReceiver

/**
 * Riferimento richiesto da Android per fare di questa app il Device Owner.
 * Non serve altro: tutte le politiche le applica [Kiosk].
 */
class KidsDeviceAdminReceiver : DeviceAdminReceiver()
