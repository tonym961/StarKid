package it.iotatec.starkid.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * La schermata che il bambino trova accendendo il tablet.
 *
 * PERCHE NON E LA SCHERMATA DI BLOCCO DI ANDROID. Quella non si puo
 * sostituire: un launcher non e un keyguard, e le API per rimpiazzarlo non
 * esistono piu da Android 5. Questa viene DOPO: il tablet si sblocca come
 * sempre, e poi StarKid si mette davanti alla schermata del bambino finche
 * qualcuno non trascina la stella fino in fondo.
 *
 * PERCHE SERVE. Un tablet preso in mano, infilato in uno zaino o appoggiato
 * a faccia in su apre app a caso con la pancia. E poi: a un bambino piccolo
 * il gesto piace, ed e il primo pezzo di StarKid che vede ogni volta.
 *
 * A COSA NON SERVE, e va detto: non e sicurezza. Si trascina e si apre, non
 * c'e niente da indovinare. Quello che tiene il bambino fuori dall'area
 * genitori resta il PIN; quello che tiene chiuso il tablet resta il blocco
 * messo dal genitore, che infatti NON mostra questa schermata ma quella di
 * blocco.
 */
@Composable
fun Risveglio(
    nomeBambino: String,
    avatar: String,
    skin: Skin,
    backgroundId: String,
    photo: ImageBitmap?,
    onSbloccato: () -> Unit,
) {
    val densita = LocalDensity.current
    val ambito = rememberCoroutineScope()
    val stella = remember { PathParser().parsePathString(MARCHIO_STELLA).toPath() }
    val posizione = remember { Animatable(0f) }

    KidsBackground(
        skin = skin,
        backgroundId = backgroundId,
        photo = photo,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Orologio(skin)

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(avatar, fontSize = 76.sp)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (nomeBambino.isBlank()) "Ciao!" else "Ciao $nomeBambino!",
                    style = MaterialTheme.typography.headlineLarge,
                    color = skin.colors.onBackground,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }

            Pista(
                posizione = posizione,
                stella = stella,
                skin = skin,
                onTrascina = { delta, larghezza ->
                    ambito.launch {
                        posizione.snapTo((posizione.value + delta).coerceIn(0f, larghezza))
                    }
                },
                onLasciato = { larghezza ->
                    ambito.launch {
                        // Oltre i tre quarti si apre. Prima, si torna
                        // indietro con un rimbalzo: un gesto mancato si deve
                        // vedere, se no sembra che il tablet non risponda.
                        if (larghezza > 0f && posizione.value >= larghezza * 0.75f) {
                            posizione.animateTo(larghezza, tween(140, easing = LinearEasing))
                            onSbloccato()
                        } else {
                            posizione.animateTo(0f, tween(260))
                        }
                    }
                },
                densita = densita,
            )
        }
    }
}

@Composable
private fun Orologio(skin: Skin) {
    // Si rinfresca da solo: un orologio fermo su una schermata di risveglio
    // si nota subito. Il secondo non serve a nessuno, quindi dieci secondi.
    var adesso by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            adesso = Date()
            delay(10_000)
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = SimpleDateFormat("HH:mm", Locale.ITALIAN).format(adesso),
            fontSize = 68.sp,
            fontWeight = FontWeight.Bold,
            color = skin.colors.onBackground,
        )
        Text(
            text = SimpleDateFormat("EEEE d MMMM", Locale.ITALIAN).format(adesso)
                .replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.titleMedium,
            color = skin.colors.onBackground.copy(alpha = 0.78f),
        )
    }
}

@Composable
private fun Pista(
    posizione: Animatable<Float, AnimationVector1D>,
    stella: Path,
    skin: Skin,
    densita: androidx.compose.ui.unit.Density,
    onTrascina: (Float, Float) -> Unit,
    onLasciato: (Float) -> Unit,
) {
    val altezza = 96.dp
    val lato = 80.dp
    // La pista ha i capi tondi di raggio altezza/2: una stella appoggiata al
    // bordo con le punte fuori dalla curva si vede, e sembra un errore. Gli
    // 8dp sono l'aria che la tiene dentro, agli estremi come al centro.
    val aria = 8.dp

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().height(altezza),
        contentAlignment = Alignment.CenterStart,
    ) {
        val corsa = with(densita) { (maxWidth - lato - aria * 2).toPx() }
        val avanzamento = if (corsa > 0f) (posizione.value / corsa).coerceIn(0f, 1f) else 0f

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    skin.colors.onBackground.copy(alpha = 0.12f),
                    RoundedCornerShape(altezza / 2),
                )
        )

        // La scia dietro la stella: dice quanto manca senza una parola.
        if (avanzamento > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(avanzamento)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(listOf(Color(0x00FFC846), Color(0x55FFC846))),
                        RoundedCornerShape(altezza / 2),
                    )
            )
        }

        // La scritta respira, e sbiadisce man mano che si trascina: a meta
        // strada ha gia fatto il suo lavoro e dopo darebbe fastidio.
        val battito = rememberInfiniteTransition(label = "battito")
        val respiro by battito.animateFloat(
            initialValue = 0.45f,
            targetValue = 0.95f,
            animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
            label = "respiro",
        )
        Text(
            text = "Trascina la stella",
            style = MaterialTheme.typography.titleMedium,
            color = skin.colors.onBackground,
            modifier = Modifier
                .align(Alignment.Center)
                .alpha((1f - avanzamento * 2f).coerceIn(0f, 1f) * respiro),
        )

        Canvas(
            modifier = Modifier
                .padding(start = aria)
                .size(lato)
                .offset { IntOffset(posizione.value.roundToInt(), 0) }
                .semantics { contentDescription = "Trascina la stella per aprire" }
                .pointerInput(corsa) {
                    detectHorizontalDragGestures(
                        onDragEnd = { onLasciato(corsa) },
                        onDragCancel = { onLasciato(corsa) },
                    ) { cambiamento, delta ->
                        cambiamento.consume()
                        onTrascina(delta, corsa)
                    }
                },
        ) {
            val fattore = size.minDimension / MARCHIO_LATO
            translate(size.width / 2f, size.height / 2f) {
                scale(fattore, fattore, Offset.Zero) {
                    drawPath(
                        stella,
                        Brush.linearGradient(
                            0f to Color(0xFFFFE48F),
                            0.55f to Color(0xFFFFC846),
                            1f to Color(0xFFF09A2B),
                            start = Offset(-MARCHIO_LATO / 2f, -MARCHIO_LATO / 2f),
                            end = Offset(0f, MARCHIO_LATO / 2f),
                        ),
                    )
                }
            }
        }
    }
}

/**
 * La stella del marchio: lo stesso path che design/genera.sh scrive nella
 * favicon, nello striscione e nei vector drawable. E copiato invece di
 * importato perche :app non dipende da :genitore; che resti identico lo
 * controlla il test test_il_marchio_e_una_stella_sola_dappertutto.
 */
private const val MARCHIO_STELLA =
    "M-8.111 -72.887Q0 -91.723 8.111 -72.887L21.09 -42.751Q27.038 -28.938 42.013 -27.549L74.685 -24.519Q95.106 -22.625 79.699 -9.09L55.047 12.566Q43.749 22.492 47.055 37.163L54.27 69.173Q58.779 89.179 41.145 78.708L12.931 61.955Q0 54.277 -12.931 61.955L-41.145 78.708Q-58.779 89.179 -54.27 69.173L-47.055 37.163Q-43.749 22.492 -55.047 12.566L-79.699 -9.09Q-95.106 -22.625 -74.685 -24.519L-42.013 -27.549Q-27.038 -28.938 -21.09 -42.751Z"

private const val MARCHIO_LATO = 172.648f
