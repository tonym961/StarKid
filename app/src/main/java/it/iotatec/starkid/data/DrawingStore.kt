package it.iotatec.starkid.data

import android.content.Context
import java.io.File

/**
 * Un tratto salvato: colore, spessore e i punti in coppie x,y.
 *
 * Le coordinate sono **relative alla larghezza del foglio**, non in pixel: un
 * disegno fatto in orizzontale si riapre giusto anche in verticale e su un
 * altro tablet. Si divide tutto per la larghezza, anche la y, cosi le
 * proporzioni restano quelle e un cerchio non diventa un uovo.
 */
data class SavedStroke(val color: Int, val width: Float, val points: List<Float>)

/**
 * I disegni del bambino, salvati nella memoria interna dell'app.
 *
 * Si salvano i tratti e non un'immagine: un disegno pesa qualche kilobyte di
 * testo invece di qualche megabyte di PNG, si ridisegna nitido a qualsiasi
 * dimensione, e la miniatura e lo stesso disegno in piccolo.
 */
object DrawingStore {

    private const val FOLDER = "disegni"
    private const val EXTENSION = ".tratti"

    /** Oltre questo numero il piu vecchio lascia il posto al nuovo. */
    const val MAX_DRAWINGS = 12

    private fun folder(context: Context): File =
        File(context.filesDir, FOLDER).apply { mkdirs() }

    /** I nomi dei disegni salvati, dal piu recente al piu vecchio. */
    fun list(context: Context): List<String> =
        folder(context).listFiles()
            ?.filter { it.isFile && it.name.endsWith(EXTENSION) }
            ?.sortedByDescending { it.lastModified() }
            ?.map { it.name.removeSuffix(EXTENSION) }
            ?: emptyList()

    fun load(context: Context, name: String): List<SavedStroke> =
        runCatching { decode(File(folder(context), name + EXTENSION).readText()) }
            .getOrDefault(emptyList())

    fun delete(context: Context, name: String) {
        runCatching { File(folder(context), name + EXTENSION).delete() }
    }

    /**
     * Salva e restituisce il nome dato al disegno. Il nome e un numero che
     * cresce: niente tastiera da far comparire a un bambino che non scrive.
     */
    fun save(context: Context, strokes: List<SavedStroke>): String? {
        if (strokes.isEmpty()) return null
        val existing = list(context)
        if (existing.size >= MAX_DRAWINGS) {
            existing.drop(MAX_DRAWINGS - 1).forEach { delete(context, it) }
        }
        val next = (existing.mapNotNull { it.toIntOrNull() }.maxOrNull() ?: 0) + 1
        val name = next.toString()
        val written = runCatching {
            File(folder(context), name + EXTENSION).writeText(encode(strokes))
            true
        }.getOrDefault(false)
        return if (written) name else null
    }

    // --- formato -----------------------------------------------------------

    /**
     * Un tratto per riga: `colore;spessore;x,y x,y ...`.
     *
     * Testo e non binario perche un disegno di un bambino fa qualche decina
     * di tratti: la compattezza non vale la fatica di leggere un file che
     * nessuno puo aprire per capire cosa e andato storto.
     */
    internal fun encode(strokes: List<SavedStroke>): String =
        strokes.joinToString("\n") { stroke ->
            val points = stroke.points.joinToString(" ") { round(it) }
            "${stroke.color};${round(stroke.width)};$points"
        }

    internal fun decode(text: String): List<SavedStroke> =
        text.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split(';')
                if (parts.size != 3) return@mapNotNull null
                val color = parts[0].toIntOrNull() ?: return@mapNotNull null
                val width = parts[1].toFloatOrNull() ?: return@mapNotNull null
                val numbers = parts[2].split(' ')
                    .filter { it.isNotBlank() }
                    .mapNotNull { it.toFloatOrNull() }
                // Un punto e una coppia: un numero spaiato vuol dire file
                // troncato, e si butta via l'ultimo mezzo punto invece di
                // disegnare una riga verso l'angolo.
                val even = numbers.size - numbers.size % 2
                if (even < 2) return@mapNotNull null
                SavedStroke(color, width, numbers.subList(0, even))
            }
            .toList()

    private fun round(value: Float): String = "%.4f".format(java.util.Locale.US, value)
}
