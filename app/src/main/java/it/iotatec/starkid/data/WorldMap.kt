package it.iotatec.starkid.data

import android.content.Context
import androidx.compose.ui.graphics.Path
import java.io.BufferedInputStream
import java.io.DataInputStream

/**
 * La mappa del mondo che sta dentro l'APK.
 *
 * I contorni vengono da **Natural Earth alla scala 1:50 milioni**, che e di
 * pubblico dominio, quantizzati a centesimi di grado: un centesimo di grado e
 * circa un chilometro, cioe meno di un pixel anche al massimo ingrandimento,
 * quindi non si perde niente di visibile e ogni punto entra in due interi
 * corti. Nessuna mappa scaricata, nessun servizio esterno, nessuna chiave: il
 * mondo e dentro l'app e funziona in aereo.
 *
 * ## Perche un file binario e non del testo
 *
 * La versione di prima era testo, e il testo nascondeva un errore grosso: il
 * convertitore teneva solo le geometrie `MultiPolygon` e saltava in silenzio i
 * `Polygon` semplici. Risultato, 29 paesi su quasi duecento - c'erano Francia e
 * Italia (che hanno le isole) e mancavano Germania, Spagna, Brasile, India e
 * tutta l'Africa tranne l'Angola. Una mappa che a colpo d'occhio sembra una
 * mappa. Il formato binario ha conteggi espliciti in testa a ogni cosa: se la
 * lettura non finisce esattamente dove deve, [leggi] se ne accorge e lo dice,
 * invece di consegnare un mondo con dei buchi.
 *
 * ## I due livelli di dettaglio
 *
 * Il file ne contiene due. Il livello 0 e semplificato e serve a mondo intero,
 * dove il dettaglio fine sarebbe comunque sotto il pixel e si pagherebbe solo
 * in lavoro per la GPU. Il livello 1 e il dettaglio pieno e compare quando si
 * ingrandisce.
 *
 * ## Lo spazio del mondo
 *
 * Tutto quello che esce di qui e gia in "coordinate di mondo": x da 0 a 1, y da
 * 0 a 0,5. Non da 0 a 1 per tutti e due di proposito - cosi il mondo e largo
 * esattamente il doppio di quanto e alto, come nella proiezione vera, e chi
 * disegna puo usare **una sola scala** per x e y. Con due scale diverse le
 * linee orizzontali verrebbero spesse il doppio di quelle verticali.
 */
object WorldMap {

    const val TERRA = 0
    const val LAGO = 1

    /**
     * Un paese, o un lago. Il [path] e gia costruito nello spazio del mondo:
     * si costruisce una volta sola e poi si disegna applicando una
     * trasformazione, invece di rifare tutti i punti a ogni fotogramma.
     */
    class Feature(
        val name: String,
        val kind: Int,
        /** 0..5. Due paesi confinanti non hanno mai lo stesso numero. */
        val colour: Int,
        /** 0 = il nome si vede gia a mondo intero, 3 = solo da molto vicino. */
        val labelRank: Int,
        val labelX: Float,
        val labelY: Float,
        val path: Path,
        /**
         * Il riquadro dell'anello **piu grande**, non di tutti.
         *
         * Serve a decidere se il nome ci sta dentro, e va preso dal pezzo dove
         * il nome verra scritto. Prendendo il riquadro di tutti i pezzi, gli
         * Stati Uniti risultano larghi centotrenta gradi perche c'e l'Alaska
         * dall'altra parte del continente, e la scritta "Stati Uniti
         * d'America" passa il controllo e poi sborda sul Canada. Stessa storia
         * con la Francia e la Guyana, o con la Danimarca e la Groenlandia.
         */
        val minX: Float,
        val minY: Float,
        val maxX: Float,
        val maxY: Float,
        /**
         * Il riquadro di TUTTI i pezzi. Questo serve a non disegnare quello
         * che e fuori dallo schermo, e per quello il riquadro deve essere
         * largo: scartare l'Alaska mentre si sta guardando proprio l'Alaska
         * sarebbe il baco piu sciocco possibile.
         */
        val fullMinX: Float,
        val fullMinY: Float,
        val fullMaxX: Float,
        val fullMaxY: Float,
    ) {
        /** Vero se questo pezzo di mondo tocca il rettangolo in vista. */
        fun visibile(x0: Float, y0: Float, x1: Float, y1: Float): Boolean =
            fullMaxX >= x0 && fullMinX <= x1 && fullMaxY >= y0 && fullMinY <= y1
    }

    class Level(val features: List<Feature>) {
        val land: List<Feature> = features.filter { it.kind == TERRA }
        val lakes: List<Feature> = features.filter { it.kind == LAGO }

        // Qui c'erano due Path con dentro TUTTI i contorni uniti, per fare una
        // chiamata di disegno invece di duecento. Sembrava un'ottimizzazione e
        // non lo era: un tracciato unico non si puo scartare a pezzi, quindi
        // ogni fotogramma tracciava i centonovemila punti del mondo anche
        // guardando il Belgio. Misurato sul tablet: 250 ms a fotogramma.
        // Disegnare paese per paese, scartando quelli fuori schermo, costa
        // molto meno. Se un giorno tornasse la tentazione: e gia stata provata.
    }

    data class City(
        val name: String,
        val country: String,
        val flag: String,
        val lat: Float,
        val lon: Float,
        val continent: String,
        val landmark: String,
    )

    /** Quello che serve a disegnare: i livelli, piu com'e andata la lettura. */
    class World(val levels: List<Level>, val problema: String?) {
        val ok: Boolean get() = problema == null && levels.isNotEmpty()

        /** Il livello giusto per questo ingrandimento. */
        fun levelFor(zoom: Float): Level =
            if (levels.size > 1 && zoom >= SOGLIA_DETTAGLIO) levels[1] else levels.first()
    }

    /**
     * Sopra questo ingrandimento si passa al dettaglio pieno. Tarato dove il
     * livello semplificato comincia a mostrare gli spigoli, non dove "sembra
     * giusto": sotto, i due livelli sono indistinguibili e il piu leggero
     * vince.
     */
    const val SOGLIA_DETTAGLIO = 2.5f

    private const val MAGIC = "SKMAP2"

    @Volatile
    private var world: World? = null

    @Volatile
    private var cities: List<City>? = null

    fun world(context: Context): World =
        world ?: synchronized(this) {
            world ?: leggi(context).also { world = it }
        }

    fun cities(context: Context): List<City> =
        cities ?: synchronized(this) {
            cities ?: readCities(context).also { cities = it }
        }

    fun projectX(lon: Float): Float = (lon + 180f) / 360f

    fun projectY(lat: Float): Float = (90f - lat) / 360f

    // -----------------------------------------------------------------------
    // Lettura
    // -----------------------------------------------------------------------

    /**
     * Legge mondo.bin. Se qualcosa non torna restituisce un [World] con il
     * problema scritto dentro invece di un'eccezione o, peggio, di un mondo
     * incompleto: il gioco mostra il guasto al posto della mappa, cosi se un
     * giorno il file si rompe lo si vede subito invece di accorgersene fra sei
     * mesi perche "manca l'Africa".
     */
    private fun leggi(context: Context): World {
        return try {
            context.assets.open("mondo.bin").use { raw ->
                DataInputStream(BufferedInputStream(raw, 1 shl 16)).use { input ->
                    val magic = ByteArray(MAGIC.length)
                    input.readFully(magic)
                    if (String(magic, Charsets.US_ASCII) != MAGIC) {
                        return World(emptyList(), "mondo.bin non comincia con $MAGIC")
                    }

                    val nameCount = input.readUnsignedShort()
                    val names = Array(nameCount) {
                        val bytes = ByteArray(input.readUnsignedShort())
                        input.readFully(bytes)
                        String(bytes, Charsets.UTF_8)
                    }

                    val levelCount = input.readUnsignedByte()
                    val levels = ArrayList<Level>(levelCount)
                    repeat(levelCount) {
                        levels.add(leggiLivello(input, names))
                    }

                    // Se avanza anche un solo byte, la lettura e andata fuori
                    // passo da qualche parte e quello che abbiamo in mano non e
                    // affidabile, anche se "sembra" una mappa.
                    if (input.read() != -1) {
                        return World(levels, "mondo.bin ha byte in piu dopo l'ultimo livello")
                    }
                    World(levels, null)
                }
            }
        } catch (errore: Exception) {
            World(emptyList(), errore.message ?: errore.javaClass.simpleName)
        }
    }

    private fun leggiLivello(input: DataInputStream, names: Array<String>): Level {
        val featureCount = input.readInt()
        val features = ArrayList<Feature>(featureCount)
        repeat(featureCount) {
            val nameIndex = input.readUnsignedShort()
            val kind = input.readUnsignedByte()
            val colour = input.readUnsignedByte()
            val labelRank = input.readUnsignedByte()
            val labelLon = input.readShort() / 100f
            val labelLat = input.readShort() / 100f
            val ringCount = input.readUnsignedShort()

            val path = Path()
            // Riquadro dell'anello piu grande visto finora, non di tutti.
            var minX = 0f
            var minY = 0f
            var maxX = 0f
            var maxY = 0f
            var areaMassima = -1f
            var fullMinX = Float.MAX_VALUE
            var fullMinY = Float.MAX_VALUE
            var fullMaxX = -Float.MAX_VALUE
            var fullMaxY = -Float.MAX_VALUE

            repeat(ringCount) {
                val pointCount = input.readInt()
                var rMinX = Float.MAX_VALUE
                var rMinY = Float.MAX_VALUE
                var rMaxX = -Float.MAX_VALUE
                var rMaxY = -Float.MAX_VALUE
                for (i in 0 until pointCount) {
                    val x = projectX(input.readShort() / 100f)
                    val y = projectY(input.readShort() / 100f)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    if (x < rMinX) rMinX = x
                    if (x > rMaxX) rMaxX = x
                    if (y < rMinY) rMinY = y
                    if (y > rMaxY) rMaxY = y
                }
                // Gli anelli sono chiusi implicitamente: il primo punto non e
                // ripetuto in fondo, lo richiude close().
                path.close()

                if (pointCount > 0) {
                    val area = (rMaxX - rMinX) * (rMaxY - rMinY)
                    if (area > areaMassima) {
                        areaMassima = area
                        minX = rMinX
                        minY = rMinY
                        maxX = rMaxX
                        maxY = rMaxY
                    }
                    if (rMinX < fullMinX) fullMinX = rMinX
                    if (rMaxX > fullMaxX) fullMaxX = rMaxX
                    if (rMinY < fullMinY) fullMinY = rMinY
                    if (rMaxY > fullMaxY) fullMaxY = rMaxY
                }
            }

            features.add(
                Feature(
                    name = names.getOrElse(nameIndex) { "" },
                    kind = kind,
                    colour = colour,
                    labelRank = labelRank,
                    labelX = projectX(labelLon),
                    labelY = projectY(labelLat),
                    path = path,
                    minX = minX,
                    minY = minY,
                    maxX = maxX,
                    maxY = maxY,
                    fullMinX = if (fullMinX == Float.MAX_VALUE) 0f else fullMinX,
                    fullMinY = if (fullMinY == Float.MAX_VALUE) 0f else fullMinY,
                    fullMaxX = if (fullMaxX == -Float.MAX_VALUE) 0f else fullMaxX,
                    fullMaxY = if (fullMaxY == -Float.MAX_VALUE) 0f else fullMaxY,
                ),
            )
        }
        return Level(features)
    }

    private fun readCities(context: Context): List<City> = runCatching {
        context.assets.open("citta.txt").bufferedReader().useLines { lines ->
            lines.mapNotNull { line ->
                val parts = line.split('|')
                if (parts.size < 7) return@mapNotNull null
                val lat = parts[3].toFloatOrNull() ?: return@mapNotNull null
                val lon = parts[4].toFloatOrNull() ?: return@mapNotNull null
                City(parts[0], parts[1], parts[2], lat, lon, parts[5], parts[6])
            }.toList()
        }
    }.getOrDefault(emptyList())
}
