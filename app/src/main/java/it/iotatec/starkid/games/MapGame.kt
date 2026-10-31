package it.iotatec.starkid.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.iotatec.starkid.data.WorldMap
import it.iotatec.starkid.ui.LocalSkin
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 9f

/** Il mondo in proiezione piana: longitudine e latitudine diventano x e y. */
private fun projectX(lon: Float) = (lon + 180f) / 360f
private fun projectY(lat: Float) = (90f - lat) / 180f

private enum class MapMode { ESPLORA, INDOVINA }

/**
 * Le citta del mondo su una mappa vera.
 *
 * Si sposta col dito e si ingrandisce con due dita, ma **anche con due
 * pulsanti grandi**: un bambino di quattro anni con le dita piccole non fa la
 * pinch, e un gioco che si puo usare solo con un gesto difficile e un gioco
 * che non si usa.
 */
@Composable
fun MapGame(onBack: () -> Unit) {
    val context = LocalContext.current
    val skin = LocalSkin.current
    val shapes = remember { WorldMap.shapes(context) }
    val cities = remember { WorldMap.cities(context) }

    var mode by remember { mutableStateOf(MapMode.ESPLORA) }
    var zoom by remember { mutableStateOf(1f) }
    // Centro della vista, in coordinate della mappa da 0 a 1.
    var centre by remember { mutableStateOf(Offset(0.5f, 0.5f)) }
    var chosen by remember { mutableStateOf<WorldMap.City?>(null) }

    var target by remember { mutableStateOf<WorldMap.City?>(null) }
    var outcome by remember { mutableStateOf<Boolean?>(null) }
    var score by remember { mutableStateOf(0) }
    var rounds by remember { mutableStateOf(0) }

    fun newRound() {
        chosen = null
        outcome = null
        target = cities.randomOrNull(Random)
    }

    LaunchedEffect(mode) {
        if (mode == MapMode.INDOVINA && target == null) newRound()
    }

    LaunchedEffect(outcome) {
        if (outcome == null) return@LaunchedEffect
        delay(if (outcome == true) 1400 else 2200)
        rounds++
        newRound()
    }

    GameShell(
        title = Games.Map.label,
        onBack = onBack,
        status = if (mode == MapMode.INDOVINA) "$score / $rounds" else null,
        onRestart = {
            zoom = 1f
            centre = Offset(0.5f, 0.5f)
            chosen = null
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                for ((value, label) in listOf(
                    MapMode.ESPLORA to "Esplora",
                    MapMode.INDOVINA to "Dov'e?",
                )) {
                    Card(
                        onClick = {
                            mode = value
                            chosen = null
                            outcome = null
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (mode == value) skin.colors.primary
                            else skin.colors.surface,
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp),
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (mode == value) skin.colors.onPrimary
                            else skin.colors.onSurface,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        )
                    }
                }
            }

            if (mode == MapMode.INDOVINA) {
                val question = target
                Text(
                    text = when {
                        outcome == true -> "Bravo! Era proprio li."
                        outcome == false -> "Era qui: ${target?.name}. ${target?.landmark?.replaceFirstChar { it.uppercase() }}."
                        question != null -> "Dov'e ${question.name}? ${question.flag}"
                        else -> ""
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = when (outcome) {
                        true -> RightGreen
                        false -> MaterialTheme.colorScheme.error
                        null -> skin.colors.onBackground
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
            }

            Box(Modifier.fillMaxSize()) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(18.dp))
                        .background(SeaColour),
                ) {
                    val viewWidth = constraints.maxWidth.toFloat()
                    val viewHeight = constraints.maxHeight.toFloat()
                    // La mappa e larga il doppio di quanto e alta: si incastra
                    // nella vista tenendo le proporzioni, cosi i continenti
                    // non si schiacciano.
                    val base = minOf(viewWidth / 2f, viewHeight)
                    val mapWidth = base * 2f * zoom
                    val mapHeight = base * zoom

                    fun toScreen(lon: Float, lat: Float) = Offset(
                        x = viewWidth / 2f + (projectX(lon) - centre.x) * mapWidth,
                        y = viewHeight / 2f + (projectY(lat) - centre.y) * mapHeight,
                    )

                    // A mondo intero centoventisette segnaposti diventano una
                    // macchia sopra l'Europa. Se ne tiene uno per casella, e
                    // la casella si stringe man mano che si ingrandisce:
                    // zoomando ne compaiono altri, che e il premio per aver
                    // guardato da vicino.
                    val visible = remember(zoom, cities) {
                        val cell = (26f / zoom).coerceAtLeast(0.6f)
                        val taken = HashSet<Long>()
                        cities.filter { city ->
                            val key = (city.lon / cell).toLong() * 1000L + (city.lat / cell).toLong()
                            taken.add(key)
                        }
                    }

                    fun clamp() {
                        // Oltre il bordo non si va: un bambino che trascina
                        // troppo si ritroverebbe davanti al nulla azzurro.
                        val halfX = (viewWidth / 2f) / mapWidth
                        val halfY = (viewHeight / 2f) / mapHeight
                        val x = if (halfX >= 0.5f) 0.5f else centre.x.coerceIn(halfX, 1f - halfX)
                        val y = if (halfY >= 0.5f) 0.5f else centre.y.coerceIn(halfY, 1f - halfY)
                        centre = Offset(x, y)
                    }

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, pinch, _ ->
                                    zoom = (zoom * pinch).coerceIn(MIN_ZOOM, MAX_ZOOM)
                                    centre = Offset(
                                        centre.x - pan.x / (base * 2f * zoom),
                                        centre.y - pan.y / (base * zoom),
                                    )
                                    clamp()
                                }
                            }
                            .pointerInput(cities, mode, target) {
                                detectTapGestures { at ->
                                    if (mode == MapMode.ESPLORA) {
                                        chosen = visible.minByOrNull { city ->
                                            val p = toScreen(city.lon, city.lat)
                                            (p - at).getDistanceSquared()
                                        }?.takeIf { city ->
                                            (toScreen(city.lon, city.lat) - at).getDistance() < 70f
                                        }

                                        if (chosen != null) Sfx.play(Sfx.Sound.TAP)
                                    } else if (outcome == null) {
                                        val goal = target ?: return@detectTapGestures
                                        val p = toScreen(goal.lon, goal.lat)
                                        // Tolleranza generosa: si chiede "dov'e
                                        // piu o meno", non la precisione al pixel.
                                        val right = (p - at).getDistance() < maxOf(90f, viewWidth * 0.07f)
                                        outcome = right
                                        if (right) score++
                                        Sfx.play(if (right) Sfx.Sound.RIGHT else Sfx.Sound.WRONG)
                                    }
                                }
                            },
                    ) {
                        // Terra.
                        for (shape in shapes) {
                            val path = Path()
                            var index = 0
                            while (index + 1 < shape.points.size) {
                                val p = toScreen(shape.points[index], shape.points[index + 1])
                                if (index == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                                index += 2
                            }
                            path.close()
                            drawPath(path, LandColour)
                            drawPath(path, CoastColour, style = Stroke(width = 1.2f))
                        }

                        // Segnaposti. I nomi non si scrivono: un bambino di
                        // quattro anni non legge, e cento scritte sovrapposte
                        // non le legge nessuno. Si tocca e si scopre.
                        val dot = (4f + zoom).coerceAtMost(11f)
                        for (city in visible) {
                            val p = toScreen(city.lon, city.lat)
                            if (p.x < -20 || p.y < -20 || p.x > size.width + 20 || p.y > size.height + 20) continue
                            drawCircle(PinShadow, dot + 2f, p)
                            drawCircle(PinColour, dot, p)
                        }

                        // Nella sfida, a risposta data si illumina il posto giusto.
                        val reveal = if (outcome != null) target else chosen
                        if (reveal != null) {
                            val p = toScreen(reveal.lon, reveal.lat)
                            drawCircle(
                                color = if (outcome == false) Color(0xFFD32F2F) else RightGreen,
                                radius = dot + 12f,
                                center = p,
                                style = Stroke(width = 5f),
                            )
                        }
                    }

                    // Due pulsanti grandi per ingrandire: la pinch non basta.
                    Column(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ZoomButton(Icons.Rounded.Add, "Ingrandisci") {
                            zoom = (zoom * 1.6f).coerceAtMost(MAX_ZOOM); clamp()
                        }
                        ZoomButton(Icons.Rounded.Remove, "Rimpicciolisci") {
                            zoom = (zoom / 1.6f).coerceAtLeast(MIN_ZOOM); clamp()
                        }
                        ZoomButton(Icons.Rounded.Public, "Torna al mondo intero") {
                            zoom = 1f
                            centre = Offset(0.5f, 0.5f)
                        }
                    }
                }

                val card = chosen
                if (mode == MapMode.ESPLORA && card != null) {
                    CityCard(card, Modifier.align(Alignment.TopCenter)) { chosen = null }
                }
            }
        }
    }
}

private val SeaColour = Color(0xFF7FB3D5)
private val LandColour = Color(0xFFBFD8A8)
private val CoastColour = Color(0xFF5E8C61)
private val PinColour = Color(0xFFE53935)
private val PinShadow = Color(0x55000000)

@Composable
private fun ZoomButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(56.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = Color.White,
            contentColor = Color(0xFF263238),
        ),
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(30.dp))
    }
}

/** Quello che compare toccando una citta: bandiera, nome e una cosa da sapere. */
@Composable
private fun CityCard(city: WorldMap.City, modifier: Modifier = Modifier, onClose: () -> Unit) {
    Card(
        modifier = modifier.padding(12.dp).widthIn(max = 520.dp).clickable(onClick = onClose),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(city.flag, fontSize = 44.sp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(city.name, style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = city.country,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = city.landmark.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}
