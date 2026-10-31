package it.iotatec.starkid.games

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlinx.coroutines.delay
import kotlin.random.Random

private const val GAME_SECONDS = 30
private const val STAR_DP = 120

/**
 * Una stella che salta da una parte all'altra: va toccata prima che si
 * sposti. Il bersaglio e grande 120 dp di proposito: il gioco sta nel
 * trovarla, non nel centrarla.
 */
@Composable
fun CatchGame(onBack: () -> Unit) {
    var secondsLeft by remember { mutableStateOf(GAME_SECONDS) }
    var score by remember { mutableStateOf(0) }
    var spotX by remember { mutableStateOf(0.5f) }
    var spotY by remember { mutableStateOf(0.5f) }
    var moves by remember { mutableStateOf(0) }
    var runId by remember { mutableStateOf(0) }
    val haptic = LocalHapticFeedback.current
    val over = secondsLeft <= 0

    LaunchedEffect(runId) {
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft--
        }
        Sfx.play(Sfx.Sound.WIN)
    }

    // Il contatore riparte a ogni spostamento, compresi quelli causati da un
    // tocco: chi la prende subito si guadagna una stella nuova senza aspettare.
    LaunchedEffect(moves, over, runId) {
        if (over) return@LaunchedEffect
        delay(1500)
        spotX = Random.nextFloat()
        spotY = Random.nextFloat()
        moves++
    }

    fun restart() {
        secondsLeft = GAME_SECONDS
        score = 0
        moves = 0
        runId++
    }

    GameShell(
        title = Games.Catch.label,
        onBack = onBack,
        status = if (over) "$score" else "$secondsLeft s · $score",
        onRestart = { restart() },
    ) {
        if (over) {
            WinBanner("Hai preso $score stelle!", onAgain = { restart() })
            return@GameShell
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val star = STAR_DP.dp
            val freeX = (maxWidth - star).coerceAtLeast(0.dp)
            val freeY = (maxHeight - star).coerceAtLeast(0.dp)
            Box(
                modifier = Modifier
                    .offset(x = freeX * spotX, y = freeY * spotY)
                    .size(star)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        score++
                        Sfx.play(Sfx.Sound.PICK)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        spotX = Random.nextFloat()
                        spotY = Random.nextFloat()
                        moves++
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("⭐", fontSize = 92.sp)
            }
        }
    }
}
