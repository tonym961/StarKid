package it.iotatec.starkid.data

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Il PIN dell'area genitori: sale, giri, impronta.
 *
 * Sta fuori da [Prefs] perche lo stesso calcolo si fa in tre posti - qui, nel
 * browser (server/web/app.js, improntaPin) e nell'app dei genitori - e i tre
 * devono dare la stessa impronta carattere per carattere, altrimenti un PIN
 * mandato dalla gestione non apre niente e nessuno capisce perche. Una
 * funzione pura si prova senza un dispositivo: PinTest contiene il vettore
 * con cui si controllano gli altri due.
 *
 * COSA PROTEGGE, E COSA NO. Quattro cifre dietro a un'impronta si ritrovano
 * con diecimila tentativi: chi ha in mano il file delle preferenze, o il
 * database del server, ricava il PIN in pochi minuti per quanti giri si
 * facciano. Questo calcolo non serve a rendere il PIN un segreto; serve a non
 * scriverlo in chiaro da nessuna parte - non nelle preferenze, non nel
 * database del server, non nei log, non nelle dieci revisioni dello storico -
 * e a rendere il lavoro non gratuito. L'unica cosa che lo rende davvero
 * difficile e un PIN piu lungo, ed e per questo che [LUNGHEZZA_MASSIMA] e
 * otto e non quattro.
 */
object Pin {

    const val LUNGHEZZA_MINIMA = 4
    const val LUNGHEZZA_MASSIMA = 8

    /** I giri che fa chi imposta il PIN stando davanti al tablet. */
    const val GIRI = 20_000

    /**
     * Quelli che si accettano da un documento di configurazione. Il minimo e
     * quello che facciamo noi; il massimo e un tetto contro un documento che
     * dicesse dieci milioni e lasciasse la tastiera del PIN ferma per minuti.
     */
    const val GIRI_MINIMI = 20_000
    const val GIRI_MASSIMI = 200_000

    private val SALE = Regex("^[0-9a-f]{32}$")
    private val IMPRONTA = Regex("^[0-9a-f]{64}$")

    /** Solo cifre: la tastiera dell'area genitori non ne ha altre. */
    fun pinValido(pin: String): Boolean =
        pin.length in LUNGHEZZA_MINIMA..LUNGHEZZA_MASSIMA && pin.all { it in '0'..'9' }

    fun saleValido(sale: String): Boolean = SALE.matches(sale)

    fun improntaValida(impronta: String): Boolean = IMPRONTA.matches(impronta)

    fun giriValidi(giri: Int): Boolean = giri in GIRI_MINIMI..GIRI_MASSIMI

    fun nuovoSale(): String {
        val byte = ByteArray(16)
        SecureRandom().nextBytes(byte)
        return byte.inEsadecimale()
    }

    /**
     * L'impronta di [pin] con questo [sale] e questi [giri].
     *
     * Il sale entra nel conto come la sua stringa esadecimale, non come i
     * sedici byte che rappresenta. E cosi dalla prima versione: cambiarlo
     * adesso renderebbe irriconoscibile il PIN di ogni tablet gia in giro.
     */
    fun impronta(pin: String, sale: String, giri: Int = GIRI): String {
        val digest = MessageDigest.getInstance("SHA-256")
        var dati = (sale + pin).toByteArray()
        repeat(giri) { dati = digest.digest(dati) }
        return dati.inEsadecimale()
    }

    private fun ByteArray.inEsadecimale(): String = joinToString("") { "%02x".format(it) }
}
