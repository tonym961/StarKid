package it.iotatec.starkid.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * "Il tablet si e riacceso": una sola verita, letta dalla schermata e scritta
 * dal servizio.
 *
 * PERCHE NON STA NELL'ACTIVITY. ACTION_SCREEN_OFF arriva solo a un ricevitore
 * registrato a runtime, e un ricevitore registrato dall'Activity vive quanto
 * l'Activity: se lo schermo si spegne mentre il bambino e dentro un'altra app
 * permessa, StarKid e ferma e non lo riceve - e alla riaccensione il
 * risveglio non comparirebbe proprio nel caso in cui serve di piu, cioe
 * quando il tablet e rimasto aperto su qualcos'altro.
 *
 * Il servizio di controllo invece gira sempre. E lui a registrare il
 * ricevitore, e questo e il posto dove lascia il segnale.
 *
 * E uno stato di Compose, non un booleano qualsiasi: la schermata deve
 * ridisegnarsi da sola quando cambia.
 */
object Risvegli {

    /**
     * Parte a true: anche il primo avvio dopo l'accensione del tablet e un
     * risveglio, e trovarsi davanti la schermata del bambino senza aver
     * fatto niente sarebbe la stessa sorpresa.
     */
    var daRisvegliare by mutableStateOf(true)

    fun schermoSpento() { daRisvegliare = true }

    fun sbloccato() { daRisvegliare = false }
}
