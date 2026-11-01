package it.iotatec.starkid.genitore

import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Il PIN dell'area genitori, calcolato qui dentro.
 *
 * Questa e la terza copia dello stesso conto: le altre due sono
 * `data/Pin.kt` nell'app del tablet e `improntaPin` in server/web/app.js.
 * Tre copie e una cosa che di solito si evita, ma qui i tre posti non
 * condividono niente - due APK diversi e una pagina web - e il calcolo e
 * quindici righe. Quello che non puo cambiare e il risultato: lo stesso PIN
 * con lo stesso sale deve dare la stessa impronta in tutti e tre, altrimenti
 * un PIN mandato da qui non apre il tablet. Il vettore di prova sta in
 * app/src/test/.../PinTest.kt: chi tocca una delle tre copie lo rilegge.
 *
 * Il PIN in chiaro non esce da questo telefono: al server vanno sale, giri e
 * impronta. Resta vero che quattro cifre dietro a un'impronta si ritrovano
 * provandole tutte, quindi non e un segreto per chi ha il database: serve a
 * non far viaggiare ne scrivere il PIN in chiaro.
 */
object Pin {

    const val LUNGHEZZA_MINIMA = 4
    const val LUNGHEZZA_MASSIMA = 8
    const val GIRI = 20_000

    fun valido(pin: String): Boolean =
        pin.length in LUNGHEZZA_MINIMA..LUNGHEZZA_MASSIMA && pin.all { it in '0'..'9' }

    /** L'oggetto da mettere nel documento sotto "impronta_pin". */
    fun impronta(pin: String): JSONObject {
        val byte = ByteArray(16)
        SecureRandom().nextBytes(byte)
        val sale = byte.inEsadecimale()
        return JSONObject()
            .put("sale", sale)
            .put("giri", GIRI)
            .put("impronta", calcola(pin, sale, GIRI))
    }

    /** Il sale entra come stringa esadecimale, non come i byte che contiene. */
    fun calcola(pin: String, sale: String, giri: Int): String {
        val digest = MessageDigest.getInstance("SHA-256")
        var dati = (sale + pin).toByteArray()
        repeat(giri) { dati = digest.digest(dati) }
        return dati.inEsadecimale()
    }

    private fun ByteArray.inEsadecimale(): String = joinToString("") { "%02x".format(it) }
}
