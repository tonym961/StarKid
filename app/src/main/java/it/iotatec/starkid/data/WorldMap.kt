package it.iotatec.starkid.data

import android.content.Context

/**
 * La mappa del mondo e l'elenco delle citta, letti dagli asset dell'app.
 *
 * I contorni vengono da Natural Earth alla scala 1:110 milioni, che e di
 * **pubblico dominio**, convertiti in decimi di grado: a questa scala un
 * decimo di grado sono una decina di chilometri, cioe meno di un pixel sullo
 * schermo, e il file scende a una quarantina di kilobyte. Nessuna mappa
 * scaricata a runtime, nessun servizio esterno, nessuna chiave: il mondo e
 * dentro l'APK e funziona in aereo.
 *
 * A questa scala i micro-stati (San Marino, Monaco, Malta, Vaticano) non
 * hanno un contorno proprio: sarebbero mezzo pixel. Le loro **citta** ci sono
 * lo stesso, perche i segnaposti non vengono da qui ma dall'elenco.
 */
object WorldMap {

    /** Un contorno chiuso: gradi di longitudine e latitudine, a coppie. */
    class Shape(val name: String, val points: FloatArray)

    data class City(
        val name: String,
        val country: String,
        val flag: String,
        val lat: Float,
        val lon: Float,
        val continent: String,
        val landmark: String,
    )

    @Volatile
    private var shapes: List<Shape>? = null

    @Volatile
    private var cities: List<City>? = null

    fun shapes(context: Context): List<Shape> =
        shapes ?: synchronized(this) {
            shapes ?: readShapes(context).also { shapes = it }
        }

    fun cities(context: Context): List<City> =
        cities ?: synchronized(this) {
            cities ?: readCities(context).also { cities = it }
        }

    private fun readShapes(context: Context): List<Shape> = runCatching {
        context.assets.open("mondo.txt").bufferedReader().useLines { lines ->
            lines.mapNotNull { line ->
                val cut = line.indexOf('|')
                if (cut <= 0) return@mapNotNull null
                val numbers = line.substring(cut + 1).split(' ')
                val points = FloatArray(numbers.size * 2)
                var at = 0
                for (pair in numbers) {
                    val comma = pair.indexOf(',')
                    if (comma <= 0) continue
                    val x = pair.substring(0, comma).toFloatOrNull() ?: continue
                    val y = pair.substring(comma + 1).toFloatOrNull() ?: continue
                    points[at++] = x / 10f
                    points[at++] = y / 10f
                }
                if (at < 8) null else Shape(line.substring(0, cut), points.copyOf(at))
            }.toList()
        }
    }.getOrDefault(emptyList())

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
