package it.iotatec.starkid.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import kotlin.math.max

/**
 * La foto che il genitore mette come sfondo.
 *
 * La foto viene **copiata** dentro l'app invece di tenere solo il suo Uri: il
 * permesso di lettura concesso dal selettore vale per questa sessione, e una
 * foto cancellata dalla galleria lascerebbe il launcher senza sfondo. Copiata
 * e ridotta, invece, resta valida per sempre e pesa poco.
 */
object BackgroundStore {

    private const val FILE_NAME = "sfondo.jpg"
    private const val TEMP_NAME = "sfondo.tmp"
    private const val QUALITY = 88

    /**
     * Lato lungo massimo del file salvato. Un tablet da 2560 px non guadagna
     * nulla da una foto da 12 megapixel, e tenerla intera significherebbe
     * decomprimerne 50 MB a ogni avvio della schermata.
     */
    const val MAX_EDGE = 2560

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun hasPhoto(context: Context): Boolean = file(context).exists()

    /** Copia la foto scelta, ruotata e ridotta. `false` se non si e potuta leggere. */
    fun import(context: Context, uri: Uri): Boolean {
        val resolver = context.contentResolver
        val rotation = runCatching {
            resolver.openInputStream(uri)?.use { readRotation(it) } ?: 0
        }.getOrDefault(0)

        val decoded = runCatching { decodeScaled(context, uri, MAX_EDGE) }.getOrNull() ?: return false
        val oriented = runCatching { rotate(decoded, rotation) }.getOrDefault(decoded)

        val temp = File(context.filesDir, TEMP_NAME)
        val written = runCatching {
            temp.outputStream().use { oriented.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
        }.getOrDefault(false)
        oriented.recycle()
        if (decoded !== oriented) decoded.recycle()

        if (!written) {
            temp.delete()
            return false
        }
        val target = file(context)
        target.delete()
        return temp.renameTo(target)
    }

    /** La foto salvata, ridotta alla dimensione dello schermo. */
    fun load(context: Context): ImageBitmap? {
        val file = file(context)
        if (!file.exists()) return null
        val metrics = context.resources.displayMetrics
        val target = max(metrics.widthPixels, metrics.heightPixels).coerceIn(720, MAX_EDGE)
        val bitmap = runCatching { decodeScaled(file, target) }.getOrNull() ?: return null
        return bitmap.asImageBitmap()
    }

    fun clear(context: Context) {
        file(context).delete()
    }

    // --- decodifica --------------------------------------------------------

    private fun decodeScaled(context: Context, uri: Uri, maxEdge: Int): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = optionsFor(bounds.outWidth, bounds.outHeight, maxEdge) ?: return null
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun decodeScaled(file: File, maxEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val options = optionsFor(bounds.outWidth, bounds.outHeight, maxEdge) ?: return null
        return BitmapFactory.decodeFile(file.path, options)
    }

    /**
     * `inSampleSize` scende solo per potenze di due, quindi da solo lascia
     * l'immagine fra una e due volte il bersaglio. Il rapporto
     * inDensity/inTargetDensity fa fare a BitmapFactory anche l'ultimo
     * ridimensionamento, in un passaggio solo: senza, bisognerebbe allocare
     * prima il bitmap grande e poi quello scalato.
     */
    private fun optionsFor(width: Int, height: Int, maxEdge: Int): BitmapFactory.Options? {
        if (width <= 0 || height <= 0) return null
        val sample = sampleSize(width, height, maxEdge)
        val sampledEdge = max(width, height) / sample
        return BitmapFactory.Options().apply {
            inSampleSize = sample
            if (sampledEdge > maxEdge) {
                inScaled = true
                inDensity = sampledEdge
                inTargetDensity = maxEdge
            }
        }
    }

    internal fun sampleSize(width: Int, height: Int, maxEdge: Int): Int {
        if (width <= 0 || height <= 0 || maxEdge <= 0) return 1
        var sample = 1
        while (max(width, height) / (sample * 2) >= maxEdge) sample *= 2
        return sample
    }

    @Suppress("DEPRECATION") // android.media.ExifInterface legge da uno stream dal livello 24
    private fun readRotation(stream: java.io.InputStream): Int =
        rotationFor(
            ExifInterface(stream)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL),
        )

    /**
     * Le foto scattate col tablet in verticale arrivano con i pixel in
     * orizzontale e l'orientamento scritto solo nell'EXIF: senza questo
     * passaggio lo sfondo comparirebbe coricato su un fianco.
     *
     * I codici sono quelli dello standard EXIF (6, 3, 8) scritti a mano e non
     * presi dalle costanti di `ExifInterface`: cosi la funzione non dipende da
     * Android e si prova con un test normale.
     */
    internal fun rotationFor(exifOrientation: Int): Int = when (exifOrientation) {
        6 -> 90
        3 -> 180
        8 -> 270
        else -> 0
    }

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
