package it.iotatec.starkid.genitore.qr

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Il QR di arruolamento.
 *
 * Il contenuto e il JSON di provisioning di Android: qualche centinaio di
 * byte, quindi un QR grande, versione 10 o oltre. Correzione M e non L: il
 * codice viene letto da un tablet appena acceso, spesso inquadrando lo
 * schermo di un telefono, con i riflessi e la mano che trema. M regge fino
 * al 15% di codice rovinato e costa solo qualche modulo in piu; se il JSON
 * non ci sta nemmeno al massimo della versione, si riprova con L prima di
 * arrendersi.
 *
 * Il disegno e in bianco e nero pieno, senza antialiasing e senza grigi: un
 * modulo grigio e un modulo che un lettore puo leggere al contrario. La
 * bitmap esce dei moduli esatti e poi la si ingrandisce di un numero intero
 * di pixel per modulo, cosi i quadrati restano quadrati.
 */
fun disegnaQr(contenuto: String, lato: Int): Bitmap {
    val matrice = try {
        codifica(contenuto, ErrorCorrectionLevel.M)
    } catch (e: Exception) {
        // Troppo lungo per M: si scende a L, che tiene piu dati.
        codifica(contenuto, ErrorCorrectionLevel.L)
    }

    val moduli = matrice.width
    // Quanti pixel per modulo, interi: con un fattore frazionario alcuni
    // moduli verrebbero larghi un pixel in piu degli altri.
    val perModulo = (lato / moduli).coerceAtLeast(1)
    val misura = moduli * perModulo

    val pixel = IntArray(misura * misura)
    for (y in 0 until moduli) {
        for (x in 0 until moduli) {
            val colore = if (matrice.get(x, y)) Color.BLACK else Color.WHITE
            for (dy in 0 until perModulo) {
                val riga = (y * perModulo + dy) * misura + x * perModulo
                for (dx in 0 until perModulo) pixel[riga + dx] = colore
            }
        }
    }
    return Bitmap.createBitmap(pixel, misura, misura, Bitmap.Config.ARGB_8888)
}

private fun codifica(contenuto: String, livello: ErrorCorrectionLevel) =
    QRCodeWriter().encode(
        contenuto,
        BarcodeFormat.QR_CODE,
        0,
        0,
        mapOf(
            EncodeHintType.ERROR_CORRECTION to livello,
            EncodeHintType.CHARACTER_SET to "UTF-8",
            // Zona di silenzio: 4 moduli e il minimo dello standard. Senza,
            // molti lettori non trovano nemmeno i tre quadrati d'angolo.
            EncodeHintType.MARGIN to 4,
        ),
    )
