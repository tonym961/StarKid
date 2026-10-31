package it.iotatec.starkid.games

import android.opengl.GLSurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import it.iotatec.starkid.games.gl.CarStyle
import it.iotatec.starkid.games.gl.PreviewRenderer
import it.iotatec.starkid.games.gl.RaceRenderer
import it.iotatec.starkid.games.gl.Scenery
import it.iotatec.starkid.games.gl.TrackLook
import it.iotatec.starkid.ui.LocalSkin
import kotlinx.coroutines.delay

/** Gli otto colori fra cui si sceglie, come li vede il bambino e come li vede OpenGL. */
private data class CarPaint(val label: String, val swatch: Color, val gl: FloatArray)

private fun paint(label: String, hex: Long): CarPaint {
    val color = Color(hex)
    return CarPaint(label, color, floatArrayOf(color.red, color.green, color.blue, 1f))
}

private val Paints = listOf(
    paint("rosso", 0xFFE53935),
    paint("blu", 0xFF1E88E5),
    paint("verde", 0xFF43A047),
    paint("giallo", 0xFFFDD835),
    paint("arancione", 0xFFFB8C00),
    paint("viola", 0xFF8E24AA),
    paint("azzurro", 0xFF00ACC1),
    paint("bianco", 0xFFF5F5F5),
)

private fun gl(hex: Long): FloatArray {
    val color = Color(hex)
    return floatArrayOf(color.red, color.green, color.blue, 1f)
}

/** Le quattro piste: cambiano i colori, il fondo e cosa si trova per strada. */
private data class TrackChoice(val id: String, val label: String, val look: TrackLook)

/**
 * Quattro posti nello spazio. Non sono quattro tinte diverse della stessa
 * strada: cambiano il terreno, il cielo, cosa c'e ai bordi e quale pianeta si
 * vede all'orizzonte.
 */
private val TrackChoices = listOf(
    TrackChoice(
        "luna", "Luna",
        TrackLook(
            road = gl(0xFF6E6E78), ground = gl(0xFF3A3A42), line = gl(0xFFF5F5F5),
            post = gl(0xFFBDBDC6), obstacle = gl(0xFF8A8A94), prize = gl(0xFFFFD54F),
            sky = gl(0xFF05060C), accent = gl(0xFF9AA0AE), scenery = Scenery.LUNA,
        ),
    ),
    TrackChoice(
        "marte", "Marte",
        TrackLook(
            road = gl(0xFF7A4030), ground = gl(0xFFB4552F), line = gl(0xFFFFE0B2),
            post = gl(0xFF5D2A1C), obstacle = gl(0xFF4E342E), prize = gl(0xFF4DD0E1),
            sky = gl(0xFFC9845C), accent = gl(0xFF8C3A20), scenery = Scenery.MARTE,
        ),
    ),
    TrackChoice(
        "anelli", "Anelli di Saturno",
        TrackLook(
            road = gl(0xFF2A2F6B), ground = gl(0xFF05060F), line = gl(0xFF80D8FF),
            post = gl(0xFF9575CD), obstacle = gl(0xFF7E6B8F), prize = gl(0xFFFFEE58),
            sky = gl(0xFF03040A), accent = gl(0xFFE2C68C), scenery = Scenery.ANELLI,
        ),
    ),
    TrackChoice(
        "colonia", "Colonia",
        TrackLook(
            road = gl(0xFF3A4152), ground = gl(0xFF161A28), line = gl(0xFF4DD0E1),
            post = gl(0xFF90A4AE), obstacle = gl(0xFFFF8F00), prize = gl(0xFF69F0AE),
            sky = gl(0xFF070A16), accent = gl(0xFF5C7190), scenery = Scenery.COLONIA,
        ),
    ),
)

/** Quanti giri dura la gara. */
private val LapChoices = listOf(3, 5, 7)

private enum class CarPhase { GARAGE, RACE, OVER }

/** Com'e andata la gara. */
data class RaceResult(val stars: Int, val seconds: Int, val bumps: Int)

/**
 * Il gioco delle macchinine, in tre dimensioni.
 *
 * Si sceglie la macchina (tre carrozzerie per otto colori) e la pista, poi si
 * guida inclinando il tablet o trascinando col dito. Il disegno e tutto
 * generato: nessun modello e nessuna immagine da caricare, ed e per questo
 * che il colore si cambia con un tocco.
 */
@Composable
fun CarGame(onBack: () -> Unit) {
    var phase by remember { mutableStateOf(CarPhase.GARAGE) }
    var style by remember { mutableStateOf(CarStyle.KART) }
    var paintIndex by remember { mutableStateOf(0) }
    var track by remember { mutableStateOf(TrackChoices[0]) }
    var laps by remember { mutableStateOf(5) }
    var invert by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<RaceResult?>(null) }
    val context = LocalContext.current

    when (phase) {
        CarPhase.GARAGE -> Garage(
            style = style,
            paintIndex = paintIndex,
            track = track,
            laps = laps,
            invert = invert,
            tiltAvailable = remember { hasTiltSensor(context) },
            onStyle = { style = it },
            onPaint = { paintIndex = it },
            onTrack = { track = it },
            onLaps = { laps = it },
            onInvert = { invert = it },
            onStart = { phase = CarPhase.RACE },
            onBack = onBack,
        )

        CarPhase.RACE -> Race(
            style = style,
            color = Paints[paintIndex].gl,
            track = track,
            laps = laps,
            invert = invert,
            onFinished = {
                result = it
                phase = CarPhase.OVER
            },
            onBack = { phase = CarPhase.GARAGE },
        )

        CarPhase.OVER -> GameShell(
            title = Games.Car.label,
            onBack = onBack,
            onRestart = { phase = CarPhase.RACE },
        ) {
            val done = result
            val minutes = (done?.seconds ?: 0) / 60
            val rest = (done?.seconds ?: 0) % 60
            WinBanner(
                text = "Arrivato!\n$laps giri in $minutes:${"%02d".format(rest)}\n" +
                    "${done?.stars ?: 0} stelle raccolte, ${done?.bumps ?: 0} botte",
                onAgain = { phase = CarPhase.RACE },
            )
        }
    }
}

// --- garage ---------------------------------------------------------------

@Composable
private fun Garage(
    style: CarStyle,
    paintIndex: Int,
    track: TrackChoice,
    laps: Int,
    invert: Boolean,
    tiltAvailable: Boolean,
    onStyle: (CarStyle) -> Unit,
    onPaint: (Int) -> Unit,
    onTrack: (TrackChoice) -> Unit,
    onLaps: (Int) -> Unit,
    onInvert: (Boolean) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    val skin = LocalSkin.current
    val surface = skin.colors.surface
    val renderer = remember {
        PreviewRenderer(
            style = style,
            color = Paints[paintIndex].gl,
            background = floatArrayOf(surface.red, surface.green, surface.blue, 1f),
        )
    }
    renderer.style = style
    renderer.color = Paints[paintIndex].gl

    GameShell(title = Games.Car.label, onBack = onBack) {
        Column(
            modifier = Modifier
                .widthIn(max = 900.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .clip(RoundedCornerShape(24.dp)),
            ) {
                GlView(renderer, Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (candidate in CarStyle.entries) {
                    Card(
                        onClick = { onStyle(candidate) },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (candidate == style) skin.colors.primary
                            else skin.colors.surface,
                        ),
                    ) {
                        Text(
                            text = candidate.label,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (candidate == style) skin.colors.onPrimary
                            else skin.colors.onSurface,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Paints.forEachIndexed { index, candidate ->
                    Box(
                        modifier = Modifier
                            .size(if (index == paintIndex) 54.dp else 44.dp)
                            .clip(CircleShape)
                            .background(candidate.swatch)
                            .border(
                                width = if (index == paintIndex) 4.dp else 1.dp,
                                color = skin.colors.onBackground,
                                shape = CircleShape,
                            )
                            .clickable { onPaint(index) },
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text = "Scegli la pista",
                style = MaterialTheme.typography.titleLarge,
                color = skin.colors.onBackground,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (candidate in TrackChoices) {
                    TrackCard(
                        track = candidate,
                        selected = candidate.id == track.id,
                        onClick = { onTrack(candidate) },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "Quanti giri",
                style = MaterialTheme.typography.titleLarge,
                color = skin.colors.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (count in LapChoices) {
                    Card(
                        onClick = { onLaps(count) },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (count == laps) skin.colors.primary
                            else skin.colors.surface,
                        ),
                    ) {
                        Text(
                            text = "$count",
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (count == laps) skin.colors.onPrimary
                            else skin.colors.onSurface,
                            modifier = Modifier.padding(horizontal = 26.dp, vertical = 10.dp),
                        )
                    }
                }
            }
            Text(
                text = "Cinque giri sono circa cinque minuti di gara.",
                style = MaterialTheme.typography.bodyMedium,
                color = skin.colors.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))
            if (tiltAvailable) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Sterzo al contrario",
                        style = MaterialTheme.typography.bodyLarge,
                        color = skin.colors.onBackground,
                    )
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = invert, onCheckedChange = onInvert)
                }
            }
            Text(
                text = if (tiltAvailable) {
                    "Inclina il tablet per sterzare, oppure trascina col dito."
                } else {
                    "Questo tablet non ha il sensore di inclinazione: si sterza col dito."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = skin.colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(16.dp))
            Card(
                onClick = onStart,
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = RightGreen),
            ) {
                Text(
                    text = "Via!",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 56.dp, vertical = 16.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TrackCard(track: TrackChoice, selected: Boolean, onClick: () -> Unit) {
    val ground = track.look.ground
    val road = track.look.road
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(ground[0], ground[1], ground[2]),
        ),
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(4.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
    ) {
        Column(
            modifier = Modifier.padding(10.dp).width(92.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(46.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(road[0], road[1], road[2])),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = track.label,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                maxLines = 1,
            )
        }
    }
}

// --- gara -----------------------------------------------------------------

@Composable
private fun Race(
    style: CarStyle,
    color: FloatArray,
    track: TrackChoice,
    laps: Int,
    invert: Boolean,
    onFinished: (RaceResult) -> Unit,
    onBack: () -> Unit,
) {
    val renderer = remember {
        RaceRenderer(
            track = track.look,
            carColor = color,
            style = style,
            totalLaps = laps,
            onPrize = { Sfx.play(Sfx.Sound.PICK) },
            onCrash = { Sfx.play(Sfx.Sound.CRASH) },
            onLap = { Sfx.play(Sfx.Sound.RIGHT) },
        )
    }

    var lap by remember { mutableStateOf(1) }
    var score by remember { mutableStateOf(0) }
    var seconds by remember { mutableStateOf(0) }
    val tilt by rememberTilt(active = true, invert = invert)
    renderer.steer = tilt

    // Il mondo vive nel renderer, su un altro thread: qui si legge solo
    // quello che va scritto in cima allo schermo, cinque volte al secondo.
    LaunchedEffect(Unit) {
        while (true) {
            lap = renderer.lap
            score = renderer.score
            seconds = renderer.elapsed.toInt()
            if (renderer.finished) break
            delay(200)
        }
        Sfx.play(Sfx.Sound.WIN)
        onFinished(RaceResult(renderer.score, renderer.elapsed.toInt(), renderer.bumps))
    }

    GameShell(
        title = track.label,
        onBack = onBack,
        status = "Giro $lap/$laps  ⭐$score  ${seconds / 60}:${"%02d".format(seconds % 60)}",
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(20.dp))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = { renderer.finger = null },
                        onDragCancel = { renderer.finger = null },
                        onDrag = { change, _ ->
                            // Dal punto toccato alla corsia: tutta la
                            // larghezza dello schermo copre tutta la strada.
                            val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                            renderer.finger = (fraction - 0.5f) * 8.4f
                        },
                    )
                },
        ) {
            GlView(renderer, Modifier.fillMaxSize())
        }
    }
}

/**
 * La finestra OpenGL dentro Compose. Va messa in pausa quando la schermata
 * sparisce, se no il thread di disegno continua a girare a vuoto e consuma
 * batteria dietro un gioco che nessuno sta guardando.
 */
@Composable
private fun GlView(renderer: GLSurfaceView.Renderer, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = remember {
        GLSurfaceView(context).apply {
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 0, 16, 0)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
    }
    DisposableEffect(view) {
        view.onResume()
        onDispose { view.onPause() }
    }
    AndroidView(factory = { view }, modifier = modifier)
}
