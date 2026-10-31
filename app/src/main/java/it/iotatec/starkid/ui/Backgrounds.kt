package it.iotatec.starkid.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.iotatec.starkid.data.BACKGROUND_PHOTO
import kotlin.random.Random

/**
 * La tinta corrispondente all'id salvato. Un id che appartiene all'altro tema
 * ricade sulla prima tinta di questo: cambiando tema il genitore non si ritrova
 * davanti a uno sfondo vuoto.
 */
fun resolveBackground(skin: Skin, id: String): BackgroundPreset =
    skin.backgrounds.firstOrNull { it.id == id } ?: skin.backgrounds.first()

/**
 * Sfondo della schermata del bambino: la sfumatura del tema oppure la foto
 * scelta dal genitore, piu le decorazioni del tema.
 *
 * Stelle e decorazioni si disegnano solo sulle tinte: sopra una foto
 * diventerebbero sporcizia.
 */
@Composable
fun KidsBackground(
    skin: Skin,
    backgroundId: String,
    photo: ImageBitmap?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val shown = photo?.takeIf { backgroundId == BACKGROUND_PHOTO }
    Box(modifier) {
        if (shown != null) {
            Image(
                bitmap = shown,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            Box(Modifier.matchParentSize().background(skin.photoScrim))
        } else {
            val preset = resolveBackground(skin, backgroundId)
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(preset.colors)))
            if (skin.starfield) Starfield(Modifier.matchParentSize())
            Decorations(skin)
        }
        content()
    }
}

/**
 * Sfondo senza foto ne decorazioni: la sfumatura del tema piu le stelle.
 * Lo usano i giochi, dove la foto del bambino sarebbe solo confusione sotto
 * le carte da girare.
 */
@Composable
fun PlainBackground(skin: Skin, modifier: Modifier = Modifier) {
    Box(modifier) {
        Box(
            Modifier.matchParentSize()
                .background(Brush.verticalGradient(skin.backgrounds.first().colors)),
        )
        if (skin.starfield) Starfield(Modifier.matchParentSize())
    }
}

private data class Star(val x: Float, val y: Float, val radius: Float, val alpha: Float)

/**
 * Cielo stellato. Fermo di proposito: un'animazione continua sulla schermata
 * dove il tablet passa le sue ore terrebbe sveglio il ciclo di disegno e si
 * mangerebbe la batteria.
 */
@Composable
private fun Starfield(modifier: Modifier) {
    // Un seme costante tiene le stelle al loro posto: rigenerandole a ogni
    // ricomposizione salterebbero a ogni minuto che cambia sull'orologio.
    val stars = remember { starField(count = 120, seed = 20261031L) }
    Canvas(modifier) {
        stars.forEach { star ->
            drawCircle(
                color = Color.White.copy(alpha = star.alpha),
                radius = star.radius * density,
                center = Offset(star.x * size.width, star.y * size.height),
            )
        }
    }
}

private fun starField(count: Int, seed: Long): List<Star> {
    val random = Random(seed)
    return List(count) {
        Star(
            x = random.nextFloat(),
            y = random.nextFloat(),
            radius = 0.6f + random.nextFloat() * 1.7f,
            alpha = 0.20f + random.nextFloat() * 0.65f,
        )
    }
}

/** Pianeta e razzo ai bordi, dove la griglia lascia spazio. */
@Composable
private fun BoxScope.Decorations(skin: Skin) {
    val decorations = skin.decorations
    if (decorations.isEmpty()) return
    Text(
        text = decorations[0],
        fontSize = 150.sp,
        modifier = Modifier
            .align(Alignment.BottomStart)
            .offset(x = (-40).dp, y = 40.dp)
            .alpha(0.30f),
    )
    if (decorations.size > 1) {
        Text(
            text = decorations[1],
            fontSize = 84.sp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 10.dp, y = 14.dp)
                .alpha(0.35f),
        )
    }
}

/** Pastiglia tonda con la tinta di uno sfondo, per la scelta nell'area genitori. */
@Composable
fun BackgroundSwatch(
    preset: BackgroundPreset,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val ring = if (selected) Color(0xFF2E7D32) else Color.Transparent
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(Brush.verticalGradient(preset.colors))
            .border(width = if (selected) 4.dp else 1.dp, color = ring, shape = CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = preset.label },
    )
}

/**
 * Anteprima dello sfondo scelto: un tablet in miniatura, con la barra in alto
 * e tre tessere. Un rettangolo con la sola sfumatura non direbbe quello che
 * conta, cioe se sopra quello sfondo le cose restano leggibili.
 */
@Composable
fun BackgroundPreview(
    skin: Skin,
    backgroundId: String,
    photo: ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    KidsBackground(
        skin = skin,
        backgroundId = backgroundId,
        photo = photo,
        modifier = modifier.aspectRatio(16f / 10f).clip(RoundedCornerShape(18.dp)),
    ) {
        Column(Modifier.fillMaxSize().padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(skin.scrim),
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (index in 0 until 3) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(10.dp))
                            .background(skin.tileColors[index]),
                    )
                }
            }
        }
    }
}
