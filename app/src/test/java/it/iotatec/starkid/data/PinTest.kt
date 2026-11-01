package it.iotatec.starkid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il vettore di prova del PIN.
 *
 * Questo file e l'arbitro fra tre copie dello stesso calcolo: [Pin] qui,
 * `improntaPin` in server/web/app.js e `genitore/Pin.kt` nell'app dei
 * genitori. Le tre devono dare la stessa impronta, se no un PIN cambiato
 * dalla web UI non apre il tablet - e il guasto si vedrebbe solo davanti al
 * tablet, con l'area genitori che non si apre piu.
 *
 * I numeri qui sotto sono stati calcolati a parte, con hashlib di Python,
 * non da questo codice: un vettore che si genera da solo prova che il codice
 * e uguale a se stesso.
 */
class PinTest {

    private val sale = "00112233445566778899aabbccddeeff"

    @Test
    fun `il vettore di prova, ventimila giri`() {
        assertEquals(
            "a407c10aee432a9cf759fe4f3b30f2fd560e887dda531cd198ed2c73b1674fc5",
            Pin.impronta("1234", sale, 20_000),
        )
    }

    @Test
    fun `un giro solo, per vedere da dove parte`() {
        // Il primo giro e SHA-256 del testo "sale + pin": e qui che si
        // sbaglia, mettendo i byte del sale invece delle sue cifre.
        assertEquals(
            "f377124b2c2ffeb096001d94cb0e3df86fbe20ad9981335bc624b960d4d80924",
            Pin.impronta("1234", sale, 1),
        )
    }

    @Test
    fun `un PIN lungo`() {
        assertEquals(
            "4cc878af2c753b2d9257154db538b35acb69a5cfe2d26af3e2035e46885da369",
            Pin.impronta("87654321", sale, 20_000),
        )
    }

    @Test
    fun `giri diversi, impronte diverse`() {
        assertNotEquals(Pin.impronta("1234", sale, 20_000), Pin.impronta("1234", sale, 20_001))
    }

    @Test
    fun `sale diverso, impronta diversa`() {
        assertNotEquals(
            Pin.impronta("1234", sale, 20_000),
            Pin.impronta("1234", "ffeeddccbbaa99887766554433221100", 20_000),
        )
    }

    @Test
    fun `il sale e lungo sedici byte ed e sempre diverso`() {
        val uno = Pin.nuovoSale()
        val due = Pin.nuovoSale()
        assertTrue(Pin.saleValido(uno))
        assertTrue(Pin.saleValido(due))
        assertNotEquals(uno, due)
    }

    @Test
    fun `l'impronta ha la forma che lo schema del server pretende`() {
        assertTrue(Pin.improntaValida(Pin.impronta("1234", sale, 20_000)))
    }

    @Test
    fun `cosa si accetta come PIN`() {
        assertTrue(Pin.pinValido("1234"))
        assertTrue(Pin.pinValido("12345678"))
        assertFalse("tre cifre sono poche", Pin.pinValido("123"))
        assertFalse("nove sono troppe", Pin.pinValido("123456789"))
        assertFalse("le lettere no", Pin.pinValido("12a4"))
        assertFalse("gli spazi no", Pin.pinValido("12 4"))
        assertFalse("vuoto no", Pin.pinValido(""))
    }

    @Test
    fun `cosa si accetta da un documento`() {
        assertFalse("maiuscole no: il server manda minuscole", Pin.saleValido(sale.uppercase()))
        assertFalse("troppo corto", Pin.saleValido("00112233"))
        assertFalse(Pin.improntaValida("non e esadecimale"))
        // I giri hanno un tetto: un documento che ne chiedesse dieci milioni
        // lascerebbe ferma la tastiera del PIN per minuti.
        assertTrue(Pin.giriValidi(20_000))
        assertTrue(Pin.giriValidi(200_000))
        assertFalse(Pin.giriValidi(19_999))
        assertFalse(Pin.giriValidi(200_001))
    }
}
