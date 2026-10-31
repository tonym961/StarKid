package it.iotatec.starkid.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import it.iotatec.starkid.ui.LocalSkin

/** I colori del pennello: pochi, accesi e sempre nello stesso ordine. */
private val Crayons = listOf(
    Color(0xFF000000),
    Color(0xFFE53935),
    Color(0xFFFB8C00),
    Color(0xFFFDD835),
    Color(0xFF43A047),
    Color(0xFF1E88E5),
    Color(0xFF8E24AA),
    Color(0xFFFF80AB),
    Color(0xFF795548),
    Color(0xFFFFFFFF),
)

private val Thicknesses = listOf(10f, 24f, 46f)

/**
 * I punti stanno in una lista osservabile: con una lista normale la tela si
 * ridisegnerebbe solo quando cambia il numero di tratti, e la riga comparirebbe
 * tutta insieme a dito gia alzato invece di seguirlo.
 */
private class Stroke(
    val color: Color,
    val width: Float,
    val points: SnapshotStateList<Offset>,
)

/**
 * Foglio bianco e dito. Niente da vincere e niente da sbagliare: e l'unico
 * gioco che non tiene punteggio, e serve proprio per quello.
 */
@Composable
fun DrawGame(onBack: () -> Unit) {
    val skin = LocalSkin.current
    val strokes = remember { mutableStateListOf<Stroke>() }
    var color by remember { mutableStateOf(Crayons[1]) }
    var width by remember { mutableStateOf(Thicknesses[1]) }

    GameShell(
        title = Games.Draw.label,
        onBack = onBack,
        onRestart = { strokes.clear() },
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White)
                    // Due riconoscitori: il trascinamento disegna una riga, il
                    // tocco secco lascia un punto. Senza il secondo, a un
                    // bambino che picchietta non succede niente.
                    .pointerInput(color, width) {
                        detectDragGestures(
                            onDragStart = { start ->
                                strokes.add(Stroke(color, width, mutableStateListOf(start)))
                            },
                            onDrag = { change, _ ->
                                strokes.lastOrNull()?.points?.add(change.position)
                            },
                        )
                    }
                    .pointerInput(color, width) {
                        detectTapGestures { spot ->
                            strokes.add(Stroke(color, width, mutableStateListOf(spot)))
                        }
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    for (stroke in strokes) {
                        val points = stroke.points
                        if (points.size == 1) {
                            drawCircle(stroke.color, stroke.width / 2f, points[0])
                            continue
                        }
                        for (index in 0 until points.size - 1) {
                            drawLine(
                                color = stroke.color,
                                start = points[index],
                                end = points[index + 1],
                                strokeWidth = stroke.width,
                                cap = StrokeCap.Round,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.size(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (crayon in Crayons) {
                    Box(
                        modifier = Modifier
                            .size(if (crayon == color) 56.dp else 46.dp)
                            .clip(CircleShape)
                            .background(crayon)
                            .border(
                                width = if (crayon == color) 4.dp else 1.dp,
                                color = skin.colors.onBackground,
                                shape = CircleShape,
                            )
                            .clickable { color = crayon },
                    )
                    Spacer(Modifier.size(8.dp))
                }
                Spacer(Modifier.size(16.dp))
                for (thickness in Thicknesses) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(
                                if (thickness == width) skin.colors.primary
                                else skin.colors.surface,
                            )
                            .clickable { width = thickness },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size((thickness / 2).dp)
                                .clip(CircleShape)
                                .background(
                                    if (thickness == width) skin.colors.onPrimary
                                    else skin.colors.onSurface,
                                ),
                        )
                    }
                    Spacer(Modifier.size(8.dp))
                }
            }
        }
    }
}
