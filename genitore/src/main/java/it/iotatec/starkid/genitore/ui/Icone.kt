package it.iotatec.starkid.genitore.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Le icone delle app, da quello che manda il server a qualcosa da disegnare.
 *
 * Il server consegna un data URL che ha scritto lui dopo aver guardato i
 * primi byte dell'immagine: "data:image/png;base64,..." oppure webp. Non e
 * quello che ha dichiarato il tablet, ed e il motivo per cui qui ci si puo
 * fidare del tipo senza ricontrollarlo.
 *
 * C'e una cache perche Compose ridisegna spesso e decodificare venticinque
 * PNG a ogni ricomposizione si sente: la chiave e la stringa stessa, che
 * cambia solo quando cambia l'icona.
 */
private val cache = mutableMapOf<String, ImageBitmap?>()

/** Null quando l'icona non c'e o non si lascia leggere: chi disegna decide. */
fun iconaDa(dataUrl: String): ImageBitmap? {
    if (dataUrl.isBlank()) return null
    cache[dataUrl]?.let { return it }
    if (cache.containsKey(dataUrl)) return null

    val decodificata = runCatching {
        val virgola = dataUrl.indexOf(',')
        if (virgola < 0 || !dataUrl.startsWith("data:image/")) return@runCatching null
        val byte = Base64.decode(dataUrl.substring(virgola + 1), Base64.DEFAULT)
        BitmapFactory.decodeByteArray(byte, 0, byte.size)?.asImageBitmap()
    }.getOrNull()

    // Si ricorda anche il fallimento: un'icona che non si legge non si
    // rilegge a ogni ricomposizione sperando che vada meglio.
    cache[dataUrl] = decodificata
    return decodificata
}
