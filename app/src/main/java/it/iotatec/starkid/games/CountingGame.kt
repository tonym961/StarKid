package it.iotatec.starkid.games

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.iotatec.starkid.domain.GameRounds
import it.iotatec.starkid.ui.LocalSkin
import kotlinx.coroutines.delay
import kotlin.random.Random

private const val ROUNDS = 8
private const val MAX_STARS = 9

/**
 * Conta le stelle. Le risposte sbagliate sono vicine a quella giusta, se no
 * si indovina a occhio senza contare: la scelta sta in [GameRounds].
 */
@Composable
fun CountingGame(onBack: () -> Unit) {
    var round by remember { mutableStateOf(GameRounds.countingRound(MAX_STARS, Random)) }
    var done by remember { mutableStateOf(0) }
    var right by remember { mutableStateOf(0) }
    var picked by remember { mutableStateOf<Int?>(null) }
    val haptic = LocalHapticFeedback.current

    // Dopo una risposta giusta si passa oltre da soli; dopo una sbagliata si
    // torna a poter rispondere, sulla stessa quantita: l'errore si corregge
    // ricontando, non ricominciando.
    LaunchedEffect(picked) {
        val answer = picked ?: return@LaunchedEffect
        Sfx.play(if (answer == round.count) Sfx.Sound.RIGHT else Sfx.Sound.WRONG)
        delay(900)
        if (answer == round.count) {
            done++
            right++
            if (done >= ROUNDS) Sfx.play(Sfx.Sound.WIN)
            round = GameRounds.countingRound(MAX_STARS, Random)
        }
        picked = null
    }

    fun restart() {
        round = GameRounds.countingRound(MAX_STARS, Random)
        done = 0
        right = 0
        picked = null
    }

    GameShell(
        title = Games.Counting.label,
        onBack = onBack,
        status = "$done / $ROUNDS",
        onRestart = { restart() },
    ) {
        if (done >= ROUNDS) {
            WinBanner("Hai contato tutto! $right su $ROUNDS.", onAgain = { restart() })
            return@GameShell
        }

        Column(
            modifier = Modifier.widthIn(max = 900.dp).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StarRow(round.count)
            Spacer(Modifier.size(28.dp))
            Text(
                text = "Quante stelle vedi?",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                for (option in round.options) {
                    NumberButton(
                        number = option,
                        state = when {
                            picked == null -> AnswerState.IDLE
                            option == round.count && picked == option -> AnswerState.RIGHT
                            option == picked -> AnswerState.WRONG
                            else -> AnswerState.IDLE
                        },
                        onClick = {
                            if (picked == null) {
                                picked = option
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        },
                    )
                }
            }
        }
    }
}

/** Le stelle su due righe al massimo: in fila da nove diventano illeggibili. */
@Composable
private fun StarRow(count: Int) {
    val perRow = if (count > 5) (count + 1) / 2 else count
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        for (chunk in (1..count).chunked(perRow)) {
            Row {
                for (ignored in chunk) {
                    Text("⭐", fontSize = 56.sp, modifier = Modifier.padding(4.dp))
                }
            }
        }
    }
}

enum class AnswerState { IDLE, RIGHT, WRONG }

@Composable
fun NumberButton(number: Int, state: AnswerState, onClick: () -> Unit) {
    val skin = LocalSkin.current
    val background = when (state) {
        AnswerState.IDLE -> skin.tileColors[1]
        AnswerState.RIGHT -> RightGreen
        AnswerState.WRONG -> MaterialTheme.colorScheme.error
    }
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = background),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Box(
            modifier = Modifier.size(110.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number.toString(),
                fontSize = 52.sp,
                color = if (state == AnswerState.IDLE) skin.tileTextColor
                else androidx.compose.ui.graphics.Color.White,
            )
        }
    }
}

/** Verde della risposta giusta: lo stesso in tutti i giochi e in tutti i temi. */
val RightGreen = androidx.compose.ui.graphics.Color(0xFF2E7D32)
