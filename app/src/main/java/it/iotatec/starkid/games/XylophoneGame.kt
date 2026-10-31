package it.iotatec.starkid.games

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Le otto barre: una scala di do, dal grave all'acuto. */
private val Bars = listOf(
    "do" to 261.6,
    "re" to 293.7,
    "mi" to 329.6,
    "fa" to 349.2,
    "sol" to 392.0,
    "la" to 440.0,
    "si" to 493.9,
    "do" to 523.3,
)

private val BarColors = listOf(
    Color(0xFFE53935),
    Color(0xFFFB8C00),
    Color(0xFFFDD835),
    Color(0xFF43A047),
    Color(0xFF00ACC1),
    Color(0xFF1E88E5),
    Color(0xFF8E24AA),
    Color(0xFFEC407A),
)

/**
 * Lo xilofono. Le barre si accorciano salendo di tono, come nello strumento
 * vero: e la prima cosa che fa capire a un bambino che le note alte stanno
 * da una parte e quelle basse dall'altra.
 */
@Composable
fun XylophoneGame(onBack: () -> Unit) {
    GameShell(title = Games.Xylophone.label, onBack = onBack) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 900.dp)
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Bars.forEachIndexed { index, (name, hz) ->
                Bar(
                    name = name,
                    color = BarColors[index],
                    // La piu grave e piena, la piu acuta arriva al 62%.
                    heightFraction = 1f - index * 0.055f,
                    onHit = { Sfx.playNote(hz) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun Bar(
    name: String,
    color: Color,
    heightFraction: Float,
    onHit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var struck by remember { mutableStateOf(0) }
    // La barra si schiaccia appena e torna su: senza un segno visibile, su un
    // tablet senza volume il bambino non capisce di averla colpita.
    val squash by animateFloatAsState(if (struck > 0) 0.94f else 1f, label = "colpo")

    LaunchedEffect(struck) {
        if (struck == 0) return@LaunchedEffect
        delay(120)
        struck = 0
    }

    Box(modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(heightFraction)
                .scale(squash)
                .clip(RoundedCornerShape(18.dp))
                .background(color)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    struck++
                    onHit()
                },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = name,
                    fontSize = 24.sp,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 18.dp),
                )
            }
        }
    }
}
