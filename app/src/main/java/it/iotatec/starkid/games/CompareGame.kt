package it.iotatec.starkid.games

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.iotatec.starkid.domain.GameRounds
import it.iotatec.starkid.ui.LocalSkin
import kotlinx.coroutines.delay
import kotlin.random.Random

private const val ROUNDS = 8
private const val MAX = 9

/**
 * "Dove ce ne sono di piu". Non si conta: si guarda. I due mucchi distano
 * sempre almeno due, cosi la risposta si vede a colpo d'occhio e il gioco
 * allena il confronto, non il conteggio.
 */
@Composable
fun CompareGame(onBack: () -> Unit) {
    var round by remember { mutableStateOf(GameRounds.compareRound(MAX, Random)) }
    var fruit by remember { mutableStateOf(CompareFruits.random()) }
    var done by remember { mutableStateOf(0) }
    var picked by remember { mutableStateOf<Boolean?>(null) }

    val leftWins = round.left > round.right

    LaunchedEffect(picked) {
        val answer = picked ?: return@LaunchedEffect
        val right = answer == leftWins
        Sfx.play(if (right) Sfx.Sound.RIGHT else Sfx.Sound.WRONG)
        delay(900)
        if (right) {
            done++
            if (done >= ROUNDS) Sfx.play(Sfx.Sound.WIN)
            round = GameRounds.compareRound(MAX, Random)
            fruit = CompareFruits.random()
        }
        picked = null
    }

    fun restart() {
        round = GameRounds.compareRound(MAX, Random)
        done = 0
        picked = null
    }

    GameShell(
        title = Games.Compare.label,
        onBack = onBack,
        status = "$done / $ROUNDS",
        onRestart = { restart() },
    ) {
        if (done >= ROUNDS) {
            WinBanner("Bravo, le hai viste tutte!", onAgain = { restart() })
            return@GameShell
        }

        Column(
            modifier = Modifier.widthIn(max = 1000.dp).fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Dove ce ne sono di piu?",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Pile(
                    count = round.left,
                    fruit = fruit,
                    state = stateFor(picked, chose = true, correct = leftWins),
                    onClick = { if (picked == null) picked = true },
                    modifier = Modifier.weight(1f),
                )
                Pile(
                    count = round.right,
                    fruit = fruit,
                    state = stateFor(picked, chose = false, correct = !leftWins),
                    onClick = { if (picked == null) picked = false },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun stateFor(picked: Boolean?, chose: Boolean, correct: Boolean): AnswerState = when {
    picked == null -> AnswerState.IDLE
    picked == chose && correct -> AnswerState.RIGHT
    picked == chose -> AnswerState.WRONG
    else -> AnswerState.IDLE
}

private val CompareFruits = listOf("🍎", "🍓", "🍌", "🐟", "🌸", "⭐", "🐥", "🎈")

@Composable
private fun Pile(
    count: Int,
    fruit: String,
    state: AnswerState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val skin = LocalSkin.current
    val background = when (state) {
        AnswerState.IDLE -> skin.tileColors[3]
        AnswerState.RIGHT -> RightGreen
        AnswerState.WRONG -> MaterialTheme.colorScheme.error
    }
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = background),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Box(Modifier.fillMaxSize().padding(14.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Tre per riga: un mucchio in fila da nove si conterebbe
                // invece di guardarlo, ed e l'altro gioco.
                for (row in (1..count).chunked(3)) {
                    Row {
                        for (ignored in row) {
                            Text(
                                text = fruit,
                                fontSize = 46.sp,
                                color = Color.White,
                                modifier = Modifier.padding(5.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
