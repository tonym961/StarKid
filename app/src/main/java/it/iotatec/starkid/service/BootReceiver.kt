package it.iotatec.starkid.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Riaccende la sorveglianza dopo un riavvio o un aggiornamento dell'app. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        GuardService.start(context)
    }
}
