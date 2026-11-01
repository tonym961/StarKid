package it.iotatec.starkid.genitore.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * La stella del marchio.
 *
 * Non e un disegno rifatto a mano in Kotlin: e la stessa stringa che sta
 * nella favicon, nello striscione del README e nei vector drawable, letta
 * con PathParser. Se un giorno la stella cambia, cambia anche qui senza che
 * nessuno se ne debba ricordare.
 */
@Composable
fun Stella(
    misura: Dp,
    modifier: Modifier = Modifier,
    piena: Boolean = true,
    tinta: Color? = null,
) {
    val percorso = remember { PathParser().parsePathString(Marchio.PERCORSO_STELLA).toPath() }
    Canvas(modifier = modifier.size(misura)) {
        val fattore = size.minDimension / Marchio.LATO
        val pennello: Brush = tinta?.let { SolidColor(it) } ?: Brush.linearGradient(
            0f to Color(Marchio.ORO_CHIARO),
            0.55f to Color(Marchio.ORO),
            1f to Color(Marchio.ORO_SCURO),
            start = Offset(size.width * 0.1f, 0f),
            end = Offset(size.width * 0.5f, size.height),
        )
        // Il path e centrato sullo zero, quindi va portato al centro della
        // tela e poi scalato: prima la traslazione, se no si scala anche lo
        // spostamento.
        translate(size.width / 2f, size.height / 2f) {
            scale(fattore, fattore, Offset.Zero) {
                if (piena) drawPath(percorso, pennello)
                else drawPath(percorso, pennello, style = Stroke(width = 15f))
            }
        }
    }
}

/** La carta: lo stesso riquadro della web UI, bordo compreso. */
@Composable
fun Carta(
    modifier: Modifier = Modifier,
    contenuto: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Tinte.Carta),
        border = BorderStroke(1.dp, Tinte.Bordo),
    ) {
        Column(Modifier.padding(16.dp), content = contenuto)
    }
}

enum class Tono { OK, ATTESA, GUASTO, SPENTO }

/**
 * L etichetta di stato. Il colore non basta mai da solo: accanto c e sempre
 * la parola, ed e la stessa regola del sito - chi non distingue il verde dal
 * rosso deve poter leggere lo stato, non indovinarlo.
 */
@Composable
fun Etichetta(testo: String, tono: Tono, modifier: Modifier = Modifier) {
    val davanti: Color
    val dietro: Color
    when (tono) {
        Tono.OK -> { davanti = Tinte.Ok; dietro = Tinte.OkVelo }
        Tono.ATTESA -> { davanti = Tinte.Attesa; dietro = Tinte.AttesaVelo }
        Tono.GUASTO -> { davanti = Tinte.Guasto; dietro = Tinte.GuastoVelo }
        Tono.SPENTO -> { davanti = Tinte.TestoTenue; dietro = Tinte.CartaAlt }
    }
    Row(
        modifier = modifier
            .background(dietro, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(8.dp).background(davanti, CircleShape))
        Text(testo, style = MaterialTheme.typography.labelMedium, color = davanti)
    }
}

/** Una riga "etichetta: valore", quella che nel sito e una <dl>. */
@Composable
fun Riga(nome: String, valore: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(nome, style = MaterialTheme.typography.bodyMedium, color = Tinte.TestoTenue)
        Text(
            valore,
            style = MaterialTheme.typography.bodyMedium,
            color = Tinte.Testo,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/**
 * Un avviso dentro una schermata.
 *
 * Non e una barra che compare e sparisce: un errore che se ne va da solo
 * dopo tre secondi e un errore che non hai letto. Gli avvisi che possono
 * sparire passano dalla snackbar, questi no.
 */
@Composable
fun Avviso(testo: String, tono: Tono, modifier: Modifier = Modifier) {
    val davanti: Color
    val dietro: Color
    when (tono) {
        Tono.OK -> { davanti = Tinte.Ok; dietro = Tinte.OkVelo }
        Tono.ATTESA -> { davanti = Tinte.Attesa; dietro = Tinte.AttesaVelo }
        Tono.GUASTO -> { davanti = Tinte.Guasto; dietro = Tinte.GuastoVelo }
        Tono.SPENTO -> { davanti = Tinte.TestoTenue; dietro = Tinte.CartaAlt }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(dietro, RoundedCornerShape(10.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Il filetto a sinistra: la forma, non solo il colore.
        Box(
            Modifier
                .width(3.dp)
                .height(20.dp)
                .background(davanti, RoundedCornerShape(2.dp))
        )
        Text(testo, style = MaterialTheme.typography.bodyMedium, color = davanti)
    }
}
