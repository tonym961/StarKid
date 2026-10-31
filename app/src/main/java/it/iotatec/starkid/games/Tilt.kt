package it.iotatec.starkid.games

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Sterzo a inclinazione, preso dall'accelerometro.
 *
 * Non dal sensore di prossimita: quello dice soltanto se c'e qualcosa davanti
 * o no, non di quanto il tablet e inclinato. L'accelerometro misura la
 * gravita, e da quella si ricava da che parte pende il tablet.
 *
 * Restituisce da -1 (tutto a sinistra) a +1 (tutto a destra).
 */
@Composable
fun rememberTilt(active: Boolean, invert: Boolean): State<Float> {
    val context = LocalContext.current
    val steering = remember { mutableFloatStateOf(0f) }

    DisposableEffect(active, invert) {
        if (!active) return@DisposableEffect onDispose { }
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (manager == null || sensor == null) return@DisposableEffect onDispose { }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val sideways = sidewaysGravity(event.values, displayRotation(context))
                // Zona morta: un tablet tenuto quasi dritto non deve sterzare
                // da solo. Oltre, si arriva a fondo corsa con una ventina di
                // gradi, che e quanto si inclina stando comodi.
                val raw = when {
                    sideways > DEAD_ZONE -> (sideways - DEAD_ZONE) / FULL_LOCK
                    sideways < -DEAD_ZONE -> (sideways + DEAD_ZONE) / FULL_LOCK
                    else -> 0f
                }.coerceIn(-1f, 1f)
                val wanted = if (invert) -raw else raw
                // Filtro: le letture grezze tremano e la macchina vibrerebbe.
                steering.floatValue += (wanted - steering.floatValue) * 0.25f
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { manager.unregisterListener(listener) }
    }

    return steering
}

/** Vero se il tablet ha un accelerometro, cioe se lo sterzo a inclinazione esiste. */
fun hasTiltSensor(context: Context): Boolean {
    val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    return manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
}

/**
 * Un tablet appoggiato su un tavolo non e mai perfettamente in piano: il
 * Lenovo di prova legge circa 0,9 m/s² di lato da fermo. Sotto questa soglia
 * non si sterza, se no la macchina scivola da sola verso un bordo.
 */
private const val DEAD_ZONE = 1.3f

/** Fondo corsa a circa venti gradi: quanto si inclina stando comodi. */
private const val FULL_LOCK = 3.6f

/**
 * Quanta gravita tira verso il bordo destro dello schermo. L'accelerometro
 * parla negli assi del dispositivo, che non ruotano con lo schermo: un tablet
 * in orizzontale ha gli assi girati di novanta gradi rispetto a quello che si
 * vede, e senza questa conversione la macchina sterzerebbe avanti e indietro.
 */
private fun sidewaysGravity(values: FloatArray, rotation: Int): Float = when (rotation) {
    Surface.ROTATION_90 -> -values[1]
    Surface.ROTATION_180 -> -values[0]
    Surface.ROTATION_270 -> values[1]
    else -> values[0]
}

@Suppress("DEPRECATION") // defaultDisplay e l'unico modo fino ad Android 10
private fun displayRotation(context: Context): Int =
    (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
