package it.iotatec.starkid.games

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.iotatec.starkid.domain.GameRounds
import it.iotatec.starkid.ui.LocalSkin
import kotlinx.coroutines.delay
import kotlin.random.Random

private const val ROUNDS = 8

/** I simboli dei motivi: forme semplici, nessuna di queste vuol dire niente. */
private val PatternSymbols = listOf("🔴", "🔵", "🟡", "🟢", "🟣", "🟠", "⬛", "⭐")

/**
 * Completa la serie. Diverso dalla sequenza luminosa: li si ricorda, qui si
 * capisce il ritmo. Il motivo e sempre visibile, non c'e niente da tenere a
 * mente.
 */
@Composable
fun PatternGame(onBack: () -> Unit) {
    var round by remember { mutableStateOf(GameRounds.patternRound(PatternSymbols, Random)) }
    var done by remember { mutableStateOf(0) }
    var picked by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(picked) {
        val answer = picked ?: return@LaunchedEffect
        val right = answer == round.answer
        Sfx.play(if (right) Sfx.Sound.RIGHT else Sfx.Sound.WRONG)
        delay(900)
        if (right) {
            done++
            if (done >= ROUNDS) Sfx.play(Sfx.Sound.WIN)
            round = GameRounds.patternRound(PatternSymbols, Random)
        }
        picked = null
    }

    fun restart() {
        round = GameRounds.patternRound(PatternSymbols, Random)
        done = 0
        picked = null
    }

    GameShell(
        title = Games.Pattern.label,
        onBack = onBack,
        status = "$done / $ROUNDS",
        onRestart = { restart() },
    ) {
        if (done >= ROUNDS) {
            WinBanner("Hai capito tutti i ritmi!", onAgain = { restart() })
            return@GameShell
        }

        Column(
            modifier = Modifier.widthIn(max = 1000.dp).padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Che cosa viene dopo?",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(20.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (symbol in round.shown) {
                    Text(symbol, fontSize = 54.sp)
                }
                Spacer(Modifier.size(8.dp))
                // Il posto vuoto, con la sua cornice: si vede che manca
                // qualcosa e dove va messo.
                Box(
                    modifier = Modifier.size(78.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Box(Modifier.size(78.dp), contentAlignment = Alignment.Center) {
                            Text("?", fontSize = 46.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(Modifier.size(34.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                for (option in round.options) {
                    OptionCard(
                        symbol = option,
                        state = when {
                            picked == null -> AnswerState.IDLE
                            option == picked && option == round.answer -> AnswerState.RIGHT
                            option == picked -> AnswerState.WRONG
                            else -> AnswerState.IDLE
                        },
                        onClick = { if (picked == null) picked = option },
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionCard(symbol: String, state: AnswerState, onClick: () -> Unit) {
    val skin = LocalSkin.current
    val background = when (state) {
        AnswerState.IDLE -> skin.tileColors[4]
        AnswerState.RIGHT -> RightGreen
        AnswerState.WRONG -> MaterialTheme.colorScheme.error
    }
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = background),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
            Text(symbol, fontSize = 56.sp)
        }
    }
}
