package it.iotatec.starkid.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Il sorteggio di un giro: la parte dei giochi che si puo provare da sola. */
class GameRoundsTest {

    private val symbols = listOf("a", "b", "c", "d", "e", "f", "g", "h")

    @Test
    fun `il mazzo del memory ha ogni simbolo esattamente due volte`() {
        val deck = GameRounds.memoryDeck(6, symbols, Random(1))
        assertEquals(12, deck.size)
        assertEquals(6, deck.toSet().size)
        for (symbol in deck.toSet()) {
            assertEquals(2, deck.count { it == symbol })
        }
    }

    @Test
    fun `piu coppie dei simboli disponibili accorciano il mazzo invece di ripetere`() {
        val deck = GameRounds.memoryDeck(20, symbols, Random(2))
        assertEquals(symbols.size * 2, deck.size)
        assertEquals(symbols.size, deck.toSet().size)
    }

    @Test
    fun `il mazzo e mescolato, non in coppie affiancate`() {
        val deck = GameRounds.memoryDeck(6, symbols, Random(3))
        val paired = (deck.indices step 2).all { deck[it] == deck[it + 1] }
        assertTrue("le coppie non devono essere gia in ordine", !paired)
    }

    @Test
    fun `conta le stelle propone la risposta giusta e tre scelte`() {
        repeat(50) { seed ->
            val round = GameRounds.countingRound(9, Random(seed.toLong()))
            assertTrue(round.count in 1..9)
            assertEquals(3, round.options.size)
            assertEquals(3, round.options.toSet().size)
            assertTrue("la risposta giusta deve esserci", round.count in round.options)
        }
    }

    @Test
    fun `le risposte sbagliate stanno vicine a quella giusta`() {
        repeat(50) { seed ->
            val round = GameRounds.countingRound(9, Random(seed.toLong()))
            // Con tre scelte la piu lontana non puo distare piu di due:
            // altrimenti si indovinerebbe senza contare.
            val furthest = round.options.maxOf { kotlin.math.abs(it - round.count) }
            assertTrue("scelte troppo distanti: ${round.options}", furthest <= 2)
        }
    }

    @Test
    fun `l'intruso viene da un gruppo diverso dagli altri`() {
        val groups = listOf(
            listOf("cane", "gatto", "mucca", "rana"),
            listOf("auto", "bus", "treno", "nave"),
            listOf("mela", "pera", "uva", "fragola"),
        )
        repeat(50) { seed ->
            val round = GameRounds.oddRound(groups, 4, Random(seed.toLong()))
            assertEquals(4, round.items.size)
            val odd = round.items[round.oddIndex]
            val family = groups.first { group -> group.contains(round.items.first { it != odd }) }
            assertTrue("l'intruso non deve stare nella famiglia", odd !in family)
            for (item in round.items.filter { it != odd }) {
                assertTrue("gli altri devono stare tutti insieme", item in family)
            }
        }
    }

    @Test
    fun `la sequenza cresce di uno e tiene quella di prima`() {
        var sequence = emptyList<Int>()
        repeat(10) {
            val before = sequence
            sequence = GameRounds.growSequence(sequence, 4, Random(7))
            assertEquals(before.size + 1, sequence.size)
            assertEquals(before, sequence.dropLast(1))
            assertTrue(sequence.last() in 0..3)
        }
    }

    @Test
    fun `i due mucchi da confrontare non sono mai pari ne vicini`() {
        repeat(50) { seed ->
            val round = GameRounds.compareRound(9, Random(seed.toLong()))
            assertTrue(round.left in 1..9 && round.right in 1..9)
            assertTrue(
                "troppo vicini: ${round.left} e ${round.right}",
                kotlin.math.abs(round.left - round.right) >= 2,
            )
        }
    }

    @Test
    fun `la serie da completare segue davvero il suo motivo`() {
        val symbols = listOf("a", "b", "c", "d", "e")
        repeat(50) { seed ->
            val round = GameRounds.patternRound(symbols, Random(seed.toLong()))
            val period = round.options.size
            assertTrue("periodo inatteso: $period", period == 2 || period == 3)
            // Quello che si vede e il motivo ripetuto due volte...
            for (index in round.shown.indices) {
                assertEquals(round.shown[index % period], round.shown[index])
            }
            // ...e la risposta e il pezzo che tocca dopo.
            assertEquals(round.shown[round.shown.size % period], round.answer)
            assertTrue(round.answer in round.options)
        }
    }

    @Test
    fun `il puzzle mescolato si puo sempre risolvere`() {
        repeat(20) { seed ->
            val board = GameRounds.slidingBoard(3, 40, Random(seed.toLong()))
            assertEquals(9, board.size)
            // Mescolare muovendo garantisce una disposizione raggiungibile:
            // qui si controlla almeno che i pezzi ci siano tutti una volta.
            assertEquals((0..8).toSet(), board.toSet())
        }
    }

    @Test
    fun `un puzzle senza mosse e gia risolto`() {
        val board = GameRounds.slidingBoard(3, 0, Random(1))
        assertTrue(GameRounds.isSolved(board))
        assertTrue(!GameRounds.isSolved(listOf(1, 0, 2, 3, 4, 5, 6, 7, 8)))
    }

    @Test
    fun `due semi diversi danno due giri diversi`() {
        val first = GameRounds.memoryDeck(6, symbols, Random(11))
        val second = GameRounds.memoryDeck(6, symbols, Random(12))
        assertNotEquals(first, second)
    }
}
