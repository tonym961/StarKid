package it.iotatec.starkid

import android.app.Application
import it.iotatec.starkid.data.Prefs

class KidsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Prima lettura delle preferenze fuori dal percorso di disegno.
        Prefs.get(this)
    }
}
