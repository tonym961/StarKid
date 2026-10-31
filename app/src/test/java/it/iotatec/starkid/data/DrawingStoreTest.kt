package it.iotatec.starkid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Il formato dei disegni salvati: deve tornare indietro uguale. */
class DrawingStoreTest {

    private val strokes = listOf(
        SavedStroke(color = -65536, width = 24f, points = listOf(0.1f, 0.2f, 0.3f, 0.45f)),
        SavedStroke(color = -16776961, width = 10f, points = listOf(0.5f, 0.5f, 0.52f, 0.51f)),
    )

    @Test
    fun `un disegno salvato si rilegge uguale`() {
        val back = DrawingStore.decode(DrawingStore.encode(strokes))
        assertEquals(strokes.size, back.size)
        for (index in strokes.indices) {
            assertEquals(strokes[index].color, back[index].color)
            assertEquals(strokes[index].width, back[index].width, 0.001f)
            assertEquals(strokes[index].points.size, back[index].points.size)
            for (point in strokes[index].points.indices) {
                assertEquals(strokes[index].points[point], back[index].points[point], 0.001f)
            }
        }
    }

    @Test
    fun `un disegno vuoto non diventa un tratto fantasma`() {
        assertTrue(DrawingStore.decode("").isEmpty())
        assertTrue(DrawingStore.decode("\n\n").isEmpty())
    }

    @Test
    fun `una riga rovinata viene saltata invece di far cadere tutto`() {
        val text = DrawingStore.encode(strokes) + "\nquesta riga non c'entra niente\n;;;"
        assertEquals(2, DrawingStore.decode(text).size)
    }

    @Test
    fun `un file troncato a meta punto non disegna verso l'angolo`() {
        // L'ultimo numero e spaiato: va buttato, non completato con uno zero.
        val back = DrawingStore.decode("-65536;12.0;0.1 0.2 0.3")
        assertEquals(1, back.size)
        assertEquals(listOf(0.1f, 0.2f), back[0].points)
    }

    @Test
    fun `un tratto con un solo numero non e un tratto`() {
        assertTrue(DrawingStore.decode("-65536;12.0;0.1").isEmpty())
    }

    @Test
    fun `il colore sopravvive anche quando e negativo`() {
        // I colori ARGB di Android sono interi negativi: se si perdesse il
        // segno, ogni disegno si riaprirebbe nero.
        val one = listOf(SavedStroke(-16777216, 8f, listOf(0f, 0f, 1f, 1f)))
        assertEquals(-16777216, DrawingStore.decode(DrawingStore.encode(one))[0].color)
    }
}
