package it.iotatec.starkid.games

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.iotatec.starkid.domain.GameRounds
import it.iotatec.starkid.ui.LocalSkin
import kotlin.math.abs
import kotlin.random.Random

private const val SIZE = 3
private const val SHUFFLE_MOVES = 40

/**
 * Puzzle scorrevole a nove caselle su una figura grande.
 *
 * La figura non e un'immagine ma una emoji disegnata a tutta griglia e
 * ritagliata casella per casella: nessun file da mettere nell'APK, e si
 * cambia soggetto cambiando un carattere.
 */
@Composable
fun PuzzleGame(onBack: () -> Unit) {
    var picture by remember { mutableStateOf(MemorySymbols.random()) }
    var board by remember { mutableStateOf(GameRounds.slidingBoard(SIZE, SHUFFLE_MOVES, Random)) }
    var moves by remember { mutableStateOf(0) }
    val skin = LocalSkin.current
    val hole = SIZE * SIZE - 1
    val solved = GameRounds.isSolved(board)

    fun restart() {
        picture = MemorySymbols.random()
        board = GameRounds.slidingBoard(SIZE, SHUFFLE_MOVES, Random)
        moves = 0
    }

    fun tap(cell: Int) {
        if (solved) return
        val holeCell = board.indexOf(hole)
        val sameRow = cell / SIZE == holeCell / SIZE && abs(cell - holeCell) == 1
        val sameColumn = cell % SIZE == holeCell % SIZE && abs(cell - holeCell) == SIZE
        if (!sameRow && !sameColumn) return
        val next = board.toMutableList()
        next[holeCell] = board[cell]
        next[cell] = hole
        board = next
        moves++
        Sfx.play(if (GameRounds.isSolved(next)) Sfx.Sound.WIN else Sfx.Sound.TAP)
    }

    GameShell(
        title = Games.Puzzle.label,
        onBack = onBack,
        status = "$moves",
        onRestart = { restart() },
    ) {
        if (solved) {
            WinBanner("Rimesso a posto in $moves mosse!", onAgain = { restart() })
            return@GameShell
        }

        BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp)) {
            val side = minOf(maxWidth, maxHeight)
            val cell = side / SIZE
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(side)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(skin.tileColors[0]),
            ) {
                board.forEachIndexed { position, piece ->
                    if (piece == hole) return@forEachIndexed
                    val cellRow = position / SIZE
                    val cellColumn = position % SIZE
                    val pieceRow = piece / SIZE
                    val pieceColumn = piece % SIZE
                    // La chiave e il PEZZO, non la casella: senza, lo stato
                    // delle animazioni resta attaccato alla posizione e a
                    // ogni mossa verticale si vedono scivolare tre caselle
                    // invece di quella toccata.
                    key(piece) {
                    // La casella scivola fino al suo posto invece di saltarci:
                    // cosi si vede quale pezzo si e mosso.
                    val x by animateDpAsState(cell * cellColumn, label = "x")
                    val y by animateDpAsState(cell * cellRow, label = "y")
                    Box(
                        modifier = Modifier
                            .offset(x = x, y = y)
                            .size(cell)
                            .padding(3.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(skin.tileColors[1])
                            .clipToBounds()
                            .clickable { tap(position) },
                    ) {
                        // La figura intera, grande quanto tutta la griglia e
                        // spostata in modo che da questa finestrella si veda
                        // solo il pezzo che compete. requiredSize serve a
                        // sfuggire ai vincoli della cella: dentro una casella
                        // la figura verrebbe rimpicciolita fino a sparire.
                        Box(
                            modifier = Modifier
                                .requiredSize(cell * SIZE)
                                .offset(
                                    x = -cell * pieceColumn,
                                    y = -cell * pieceRow,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(picture, fontSize = (cell.value * SIZE * 0.70f).sp)
                        }
                    }
                    }
                }
            }
        }
    }
}
