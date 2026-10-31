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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import it.iotatec.starkid.data.DrawingStore
import it.iotatec.starkid.data.SavedStroke
import it.iotatec.starkid.ui.LocalSkin

/** Un pulsante tondo della barra degli strumenti. */
@Composable
private fun ToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: Color,
    background: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(30.dp))
    }
}

/**
 * I disegni salvati, in miniatura. La miniatura non e un'immagine: e lo
 * stesso disegno ridisegnato in piccolo, percio e sempre nitida e non occupa
 * niente.
 */
@Composable
private fun Gallery(
    names: List<String>,
    load: (String) -> List<SavedStroke>,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClose: () -> Unit,
) {
    val skin = LocalSkin.current
    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ToolButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                description = "Torna al foglio",
                tint = skin.colors.onPrimary,
                background = skin.colors.primary,
                onClick = onClose,
            )
            Spacer(Modifier.size(14.dp))
            Text(
                text = if (names.isEmpty()) "Non hai ancora salvato niente" else "I miei disegni",
                style = MaterialTheme.typography.headlineMedium,
                color = skin.colors.primary,
            )
        }
        Spacer(Modifier.size(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 170.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(names, key = { it }) { name ->
                val strokes = remember(name) { load(name) }
                Box {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.4f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .clickable { onOpen(name) },
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            for (stroke in strokes) {
                                val points = stroke.points
                                var index = 0
                                while (index + 3 < points.size) {
                                    drawLine(
                                        color = Color(stroke.color),
                                        start = Offset(points[index] * size.width, points[index + 1] * size.width),
                                        end = Offset(points[index + 2] * size.width, points[index + 3] * size.width),
                                        strokeWidth = (stroke.width * size.width / 1000f).coerceAtLeast(1.5f),
                                        cap = StrokeCap.Round,
                                    )
                                    index += 2
                                }
                            }
                        }
                    }
                    ToolButton(
                        icon = Icons.Rounded.Delete,
                        description = "Butta via questo disegno",
                        tint = Color.White,
                        background = MaterialTheme.colorScheme.error,
                        onClick = { onDelete(name) },
                    )
                }
            }
        }
    }
}

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
    val context = LocalContext.current
    val strokes = remember { mutableStateListOf<Stroke>() }
    var color by remember { mutableStateOf(Crayons[1]) }
    var width by remember { mutableStateOf(Thicknesses[1]) }
    var gallery by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(DrawingStore.list(context)) }
    // Larghezza del foglio in pixel: serve a salvare in misure relative, cosi
    // un disegno fatto in orizzontale si riapre giusto anche in verticale.
    var sheetWidth by remember { mutableStateOf(1f) }

    GameShell(
        title = Games.Draw.label,
        onBack = onBack,
        onRestart = { strokes.clear() },
    ) {
        if (gallery) {
            Gallery(
                names = saved,
                load = { DrawingStore.load(context, it) },
                onOpen = { name ->
                    strokes.clear()
                    DrawingStore.load(context, name).forEach { stroke ->
                        val points = mutableStateListOf<Offset>()
                        for (index in stroke.points.indices step 2) {
                            points.add(
                                Offset(
                                    stroke.points[index] * sheetWidth,
                                    stroke.points[index + 1] * sheetWidth,
                                ),
                            )
                        }
                        strokes.add(Stroke(Color(stroke.color), stroke.width, points))
                    }
                    gallery = false
                },
                onDelete = { name ->
                    DrawingStore.delete(context, name)
                    saved = DrawingStore.list(context)
                },
                onClose = { gallery = false },
            )
            return@GameShell
        }

        Column(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White)
                    .onSizeChanged { sheetWidth = it.width.toFloat().coerceAtLeast(1f) }
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
                // Salvare e riaprire: due bersagli grandi accanto ai colori,
                // non nascosti in un menu.
                ToolButton(
                    icon = Icons.Rounded.Save,
                    description = "Salva il disegno",
                    tint = skin.colors.primary,
                    background = skin.colors.surface,
                ) {
                    val relative = strokes.map { stroke ->
                        SavedStroke(
                            color = stroke.color.toArgb(),
                            width = stroke.width,
                            points = stroke.points.flatMap {
                                listOf(it.x / sheetWidth, it.y / sheetWidth)
                            },
                        )
                    }
                    if (DrawingStore.save(context, relative) != null) {
                        saved = DrawingStore.list(context)
                        Sfx.play(Sfx.Sound.RIGHT)
                    }
                }
                Spacer(Modifier.size(10.dp))
                ToolButton(
                    icon = Icons.Rounded.PhotoLibrary,
                    description = "I miei disegni",
                    tint = skin.colors.primary,
                    background = skin.colors.surface,
                ) {
                    saved = DrawingStore.list(context)
                    gallery = true
                }
                Spacer(Modifier.size(18.dp))
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
