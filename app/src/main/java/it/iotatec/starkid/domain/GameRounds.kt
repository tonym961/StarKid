package it.iotatec.starkid.domain

import kotlin.random.Random

/**
 * Come si compone un giro di ciascun gioco.
 *
 * Sta qui, separato dalle schermate, per lo stesso motivo di [Guard]: sono
 * funzioni pure, e il sorteggio di un gioco e proprio la parte che conviene
 * poter provare con un test invece che a occhio sul tablet. Il generatore
 * casuale arriva da fuori, cosi nei test si semina e il giro e ripetibile.
 */
object GameRounds {

    /**
     * Mazzo del memory: ogni simbolo compare due volte e il mazzo e mescolato.
     * Se i simboli disponibili sono meno delle coppie chieste, le coppie
     * scendono: meglio un mazzo piu corto che due coppie identiche, che in un
     * memory sarebbero un imbroglio.
     */
    fun memoryDeck(pairs: Int, symbols: List<String>, random: Random): List<String> {
        val wanted = pairs.coerceIn(2, symbols.size)
        return symbols.shuffled(random)
            .take(wanted)
            .flatMap { listOf(it, it) }
            .shuffled(random)
    }

    /** Quante stelle disegnare e i numeri fra cui scegliere. */
    data class CountingRound(val count: Int, val options: List<Int>)

    /**
     * Le risposte sbagliate sono vicine a quella giusta: fra 7 e 2 si sceglie
     * contando a occhio, fra 7 e 6 bisogna contare davvero.
     */
    fun countingRound(max: Int, random: Random, options: Int = 3): CountingRound {
        val top = max.coerceAtLeast(2)
        val count = random.nextInt(1, top + 1)
        val choices = sortedSetOf(count)
        var distance = 1
        while (choices.size < options.coerceIn(2, top)) {
            val below = count - distance
            val above = count + distance
            if (below >= 1) choices.add(below)
            if (choices.size < options && above <= top) choices.add(above)
            distance++
        }
        return CountingRound(count, choices.toList().shuffled(random))
    }

    /** Le figure mostrate e la posizione di quella che non c'entra. */
    data class OddRound(val items: List<String>, val oddIndex: Int)

    /**
     * Prende [size] - 1 figure da un gruppo e una da un altro. I gruppi devono
     * essere due o piu, e abbastanza capienti: se non lo sono, il giro viene
     * piu corto invece di ripetere una figura.
     */
    fun oddRound(groups: List<List<String>>, size: Int, random: Random): OddRound {
        require(groups.size >= 2) { "servono almeno due gruppi" }
        val shuffledGroups = groups.shuffled(random)
        val family = shuffledGroups[0]
        val stranger = shuffledGroups[1].random(random)
        val wanted = (size - 1).coerceIn(1, family.size)
        val items = (family.shuffled(random).take(wanted) + stranger).shuffled(random)
        return OddRound(items, items.indexOf(stranger))
    }

    /**
     * La sequenza luminosa cresce di un passo alla volta e tiene quella di
     * prima: il bambino rifa quello che ha gia imparato e aggiunge un pezzo.
     */
    fun growSequence(current: List<Int>, colors: Int, random: Random): List<Int> =
        current + random.nextInt(colors.coerceAtLeast(2))

    /** Due mucchi da confrontare: "dove ce ne sono di piu". */
    data class CompareRound(val left: Int, val right: Int)

    /**
     * I due mucchi distano almeno due: fra cinque e sei bisognerebbe contare,
     * e contare e gia l'altro gioco. Qui si deve poter rispondere a occhio.
     */
    fun compareRound(max: Int, random: Random): CompareRound {
        val top = max.coerceAtLeast(4)
        while (true) {
            val left = random.nextInt(1, top + 1)
            val right = random.nextInt(1, top + 1)
            if (kotlin.math.abs(left - right) >= 2) return CompareRound(left, right)
        }
    }

    /** Serie logica: quello che si vede, il pezzo mancante e le scelte. */
    data class PatternRound(
        val shown: List<String>,
        val answer: String,
        val options: List<String>,
    )

    /**
     * Un motivo che si ripete (AB AB... oppure ABC ABC...) troncato prima del
     * pezzo successivo. Le scelte sono i simboli del motivo stesso: la
     * risposta si deduce dal ritmo, non si esclude per stranezza.
     */
    fun patternRound(symbols: List<String>, random: Random): PatternRound {
        require(symbols.size >= 3) { "servono almeno tre simboli" }
        val period = if (random.nextBoolean()) 2 else 3
        val motif = symbols.shuffled(random).take(period)
        val shown = List(period * 2) { motif[it % period] }
        val answer = motif[shown.size % period]
        return PatternRound(shown, answer, motif.shuffled(random))
    }

    /**
     * Puzzle scorrevole: la disposizione dei pezzi dopo una serie di mosse
     * legali a caso. Si mescola muovendo e non sorteggiando le posizioni,
     * perche meta delle disposizioni possibili di un quindici non si puo
     * risolvere, e un puzzle irrisolvibile e solo una presa in giro.
     *
     * L'indice e la casella, il valore e il pezzo; il buco e l'ultimo valore.
     */
    fun slidingBoard(size: Int, moves: Int, random: Random): List<Int> {
        val cells = size * size
        val board = MutableList(cells) { it }
        var hole = cells - 1
        repeat(moves) {
            val row = hole / size
            val column = hole % size
            val neighbours = buildList {
                if (row > 0) add(hole - size)
                if (row < size - 1) add(hole + size)
                if (column > 0) add(hole - 1)
                if (column < size - 1) add(hole + 1)
            }
            val pick = neighbours.random(random)
            board[hole] = board[pick]
            board[pick] = cells - 1
            hole = pick
        }
        return board
    }

    fun isSolved(board: List<Int>): Boolean = board.withIndex().all { it.index == it.value }
}
