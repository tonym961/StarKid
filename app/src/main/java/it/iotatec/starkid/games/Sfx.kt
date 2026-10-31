package it.iotatec.starkid.games

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * I suoni dei giochi, sintetizzati sul momento.
 *
 * Nessun file audio nell'APK: le note si calcolano, cosi non c'e niente da
 * scaricare, niente licenze da rispettare e l'app non cresce di un byte.
 * Sono note pulite con una coda breve, non campioni: su un tablet di un
 * bambino il suono deve dire "giusto" o "sbagliato", non fare rumore.
 *
 * Il volume segue quello della musica di sistema, quindi il genitore puo
 * abbassarlo come qualsiasi altra cosa; e dall'area genitori si spegne.
 */
object Sfx {

    private const val RATE = 22_050

    /**
     * Una nota: frequenza in hertz e durata in millisecondi. Internal e non
     * private perche la usa la lista dentro [Sound], che il resto del modulo
     * deve poter nominare.
     */
    internal data class Note(val hz: Double, val ms: Int)

    enum class Sound(internal val notes: List<Note>, internal val volume: Double) {
        /** Tocco qualsiasi: una sola nota corta, deve passare inosservata. */
        TAP(listOf(Note(880.0, 60)), 0.18),

        /** Risposta giusta: tre note che salgono. */
        RIGHT(listOf(Note(659.3, 80), Note(880.0, 80), Note(1318.5, 130)), 0.30),

        /** Risposta sbagliata: due note gravi, senza stridere. */
        WRONG(listOf(Note(311.1, 130), Note(233.1, 180)), 0.26),

        /** Fine partita. */
        WIN(
            listOf(
                Note(523.3, 110),
                Note(659.3, 110),
                Note(784.0, 110),
                Note(1046.5, 260),
            ),
            0.32,
        ),

        /** Premio raccolto. */
        PICK(listOf(Note(1046.5, 55), Note(1568.0, 85)), 0.26),

        /** Botta contro un ostacolo. */
        CRASH(listOf(Note(146.8, 90), Note(110.0, 200)), 0.34),

        // Le quattro luci della sequenza: un accordo di do maggiore, cosi
        // anche una sequenza sbagliata non suona male.
        PAD_1(listOf(Note(392.0, 300)), 0.30),
        PAD_2(listOf(Note(523.3, 300)), 0.30),
        PAD_3(listOf(Note(659.3, 300)), 0.30),
        PAD_4(listOf(Note(784.0, 300)), 0.30),
    }

    fun pad(index: Int): Sound = when (index) {
        0 -> Sound.PAD_1
        1 -> Sound.PAD_2
        2 -> Sound.PAD_3
        else -> Sound.PAD_4
    }

    @Volatile
    var enabled: Boolean = true

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Piu di tre suoni insieme non si distinguono comunque, e in un gioco di
    // riflessi le richieste possono arrivare a raffica: le altre si lasciano
    // cadere invece di accumulare tracce audio.
    private val slots = Semaphore(3)

    fun play(sound: Sound) = emit(sound.notes, sound.volume)

    /** Una nota qualsiasi: serve allo xilofono, che ha le sue otto. */
    fun playNote(hz: Double, ms: Int = 420, volume: Double = 0.32) =
        emit(listOf(Note(hz, ms)), volume)

    private fun emit(notes: List<Note>, volume: Double) {
        if (!enabled) return
        if (!slots.tryAcquire()) return
        scope.launch {
            try {
                render(notes, volume)
            } finally {
                slots.release()
            }
        }
    }

    private suspend fun render(notes: List<Note>, volume: Double) {
        val samples = synth(notes, volume)
        val bytes = samples.size * 2
        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(bytes)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        }.getOrNull() ?: return

        runCatching {
            track.write(samples, 0, samples.size)
            track.play()
        }
        delay(notes.sumOf { it.ms }.toLong() + 60)
        runCatching { track.release() }
    }

    /**
     * Le note una dietro l'altra, ognuna con un attacco immediato e una coda
     * che si spegne: senza la coda si sentirebbe uno scatto alla fine di ogni
     * nota, che e il rumore piu fastidioso che un altoparlante possa fare.
     */
    private fun synth(notes: List<Note>, volume: Double): ShortArray {
        val total = notes.sumOf { it.ms * RATE / 1000 }
        val out = ShortArray(total)
        var at = 0
        for (note in notes) {
            val length = note.ms * RATE / 1000
            for (index in 0 until length) {
                val t = index.toDouble() / RATE
                val progress = index.toDouble() / length
                // Coda esponenziale, piu una dissolvenza finale che porta
                // esattamente a zero.
                val envelope = exp(-3.5 * progress) * (1.0 - progress * progress)
                val wave = sin(2 * PI * note.hz * t) +
                    0.22 * sin(4 * PI * note.hz * t)
                val value = wave * envelope * volume * Short.MAX_VALUE / 1.22
                out[at + index] = value.toInt().coerceIn(-32768, 32767).toShort()
            }
            at += length
        }
        return out
    }
}
