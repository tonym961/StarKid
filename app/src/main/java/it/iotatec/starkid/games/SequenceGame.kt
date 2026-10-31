package it.iotatec.starkid.games

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.iotatec.starkid.domain.GameRounds
import kotlinx.coroutines.delay
import kotlin.random.Random

/** Quattro luci, sempre le stesse e sempre nello stesso posto. */
private val PadColors = listOf(
    Color(0xFFE53935),
    Color(0xFF1E88E5),
    Color(0xFF43A047),
    Color(0xFFFDD835),
)

private enum class Phase { SHOWING, WAITING, LOST }

/**
 * La sequenza si allunga di una luce alla volta e tiene quella di prima: il
 * bambino rifa quello che ha gia imparato e aggiunge un pezzo.
 */
@Composable
fun SequenceGame(onBack: () -> Unit) {
    var sequence by remember { mutableStateOf(GameRounds.growSequence(emptyList(), 4, Random)) }
    var phase by remember { mutableStateOf(Phase.SHOWING) }
    var lit by remember { mutableStateOf(-1) }
    var position by remember { mutableStateOf(0) }
    var best by remember { mutableStateOf(0) }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(sequence, phase) {
        if (phase != Phase.SHOWING) return@LaunchedEffect
        delay(600)
        for (pad in sequence) {
            lit = pad
            Sfx.play(Sfx.pad(pad))
            delay(520)
            lit = -1
            delay(240)
        }
        position = 0
        phase = Phase.WAITING
    }

    fun restart() {
        sequence = GameRounds.growSequence(emptyList(), 4, Random)
        position = 0
        lit = -1
        phase = Phase.SHOWING
    }

    fun press(pad: Int) {
        if (phase != Phase.WAITING) return
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (sequence[position] != pad) {
            Sfx.play(Sfx.Sound.WRONG)
            best = maxOf(best, sequence.size - 1)
            phase = Phase.LOST
            return
        }
        Sfx.play(Sfx.pad(pad))
        position++
        if (position == sequence.size) {
            best = maxOf(best, sequence.size)
            sequence = GameRounds.growSequence(sequence, 4, Random)
            phase = Phase.SHOWING
        }
    }

    GameShell(
        title = Games.Sequence.label,
        onBack = onBack,
        status = "${sequence.size}",
        onRestart = { restart() },
    ) {
        if (phase == Phase.LOST) {
            WinBanner("Sei arrivato a $best luci!", onAgain = { restart() })
            return@GameShell
        }

        Column(
            modifier = Modifier.widthIn(max = 560.dp).padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (phase == Phase.SHOWING) "Guarda bene..." else "Tocca tu!",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(20.dp))
            for (row in 0 until 2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    for (column in 0 until 2) {
                        val pad = row * 2 + column
                        Pad(
                            color = PadColors[pad],
                            on = lit == pad,
                            enabled = phase == Phase.WAITING,
                            onClick = { press(pad) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun Pad(
    color: Color,
    on: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // La luce accesa non cambia colore ma si schiarisce: il colore e
    // l'identita della pastiglia e deve restare riconoscibile anche spenta.
    val glow by animateFloatAsState(if (on) 1f else 0.45f, label = "luce")
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.aspectRatio(1.2f).alpha(glow),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = color,
            disabledContainerColor = color,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (on) 10.dp else 2.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (on) Text("✨", fontSize = 48.sp)
        }
    }
}
