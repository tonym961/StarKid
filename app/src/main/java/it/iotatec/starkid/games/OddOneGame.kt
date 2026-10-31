package it.iotatec.starkid.games

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.iotatec.starkid.domain.GameRounds
import it.iotatec.starkid.ui.LocalSkin
import kotlinx.coroutines.delay
import kotlin.random.Random

private const val ROUNDS = 8
private const val ITEMS = 4

/** Quattro figure, una di un'altra famiglia: si tocca quella che non c'entra. */
@Composable
fun OddOneGame(onBack: () -> Unit) {
    var round by remember { mutableStateOf(GameRounds.oddRound(OddOneGroups, ITEMS, Random)) }
    var done by remember { mutableStateOf(0) }
    var right by remember { mutableStateOf(0) }
    var picked by remember { mutableStateOf<Int?>(null) }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(picked) {
        val answer = picked ?: return@LaunchedEffect
        Sfx.play(if (answer == round.oddIndex) Sfx.Sound.RIGHT else Sfx.Sound.WRONG)
        delay(900)
        if (answer == round.oddIndex) {
            right++
            done++
            if (done >= ROUNDS) Sfx.play(Sfx.Sound.WIN)
            round = GameRounds.oddRound(OddOneGroups, ITEMS, Random)
        }
        picked = null
    }

    fun restart() {
        round = GameRounds.oddRound(OddOneGroups, ITEMS, Random)
        done = 0
        right = 0
        picked = null
    }

    GameShell(
        title = Games.OddOne.label,
        onBack = onBack,
        status = "$done / $ROUNDS",
        onRestart = { restart() },
    ) {
        if (done >= ROUNDS) {
            WinBanner("Trovati tutti gli intrusi!", onAgain = { restart() })
            return@GameShell
        }

        Column(
            modifier = Modifier.widthIn(max = 900.dp).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Quale non c'entra?",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                round.items.forEachIndexed { index, item ->
                    FigureCard(
                        figure = item,
                        state = when {
                            picked == null -> AnswerState.IDLE
                            index == picked && index == round.oddIndex -> AnswerState.RIGHT
                            index == picked -> AnswerState.WRONG
                            else -> AnswerState.IDLE
                        },
                        onClick = {
                            if (picked == null) {
                                picked = index
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun FigureCard(
    figure: String,
    state: AnswerState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val skin = LocalSkin.current
    val background = when (state) {
        AnswerState.IDLE -> skin.tileColors[2]
        AnswerState.RIGHT -> RightGreen
        AnswerState.WRONG -> MaterialTheme.colorScheme.error
    }
    Card(
        onClick = onClick,
        modifier = modifier.aspectRatio(0.9f),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = background),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(figure, fontSize = 72.sp)
        }
    }
}
