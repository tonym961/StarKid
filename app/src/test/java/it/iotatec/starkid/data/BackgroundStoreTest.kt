package it.iotatec.starkid.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Le due funzioni di BackgroundStore che si possono provare senza Android:
 * quella che decide di quanto ridurre la foto e quella che legge l'EXIF.
 */
class BackgroundStoreTest {

    @Test
    fun `foto piu piccola del bersaglio resta intera`() {
        assertEquals(1, BackgroundStore.sampleSize(1600, 1200, 2560))
    }

    @Test
    fun `foto appena sopra il bersaglio non viene dimezzata`() {
        // 3000 / 2 = 1500, sotto il bersaglio: dimezzarla butterebbe pixel che
        // servono ancora. Ci pensa poi il ridimensionamento fine.
        assertEquals(1, BackgroundStore.sampleSize(3000, 2000, 2560))
    }

    @Test
    fun `foto da venti megapixel viene dimezzata`() {
        // 5200 / 2 = 2600, ancora sopra il bersaglio: conviene decomprimere
        // meta dei pixel. Un altro passo (1300) scenderebbe troppo.
        assertEquals(2, BackgroundStore.sampleSize(5200, 3900, 2560))
    }

    @Test
    fun `conta il lato lungo anche in verticale`() {
        assertEquals(
            BackgroundStore.sampleSize(5200, 3900, 2560),
            BackgroundStore.sampleSize(3900, 5200, 2560),
        )
    }

    @Test
    fun `misure assurde non fanno dividere per zero`() {
        assertEquals(1, BackgroundStore.sampleSize(0, 0, 2560))
        assertEquals(1, BackgroundStore.sampleSize(-10, 100, 2560))
        assertEquals(1, BackgroundStore.sampleSize(4000, 3000, 0))
    }

    @Test
    fun `l'orientamento EXIF diventa gradi`() {
        assertEquals(0, BackgroundStore.rotationFor(1))
        assertEquals(90, BackgroundStore.rotationFor(6))
        assertEquals(180, BackgroundStore.rotationFor(3))
        assertEquals(270, BackgroundStore.rotationFor(8))
    }

    @Test
    fun `un orientamento che non conosco lascia la foto com'e`() {
        assertEquals(0, BackgroundStore.rotationFor(0))
        assertEquals(0, BackgroundStore.rotationFor(5))
        assertEquals(0, BackgroundStore.rotationFor(99))
    }
}

class AppCategoryTest {

    @Test
    fun `gli id si rileggono`() {
        assertEquals(AppCategory.GAMES, AppCategory.from("giochi"))
        assertEquals(AppCategory.LEARN, AppCategory.from("imparo"))
    }

    @Test
    fun `un id salvato da una versione futura non fa cadere la barra`() {
        assertEquals(AppCategory.OTHER, AppCategory.from("scacchi"))
        assertEquals(AppCategory.OTHER, AppCategory.from(null))
    }
}
