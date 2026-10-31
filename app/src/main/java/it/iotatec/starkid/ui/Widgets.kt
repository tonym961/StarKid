package it.iotatec.starkid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Larghezza massima del contenuto. Su un tablet da 1280 dp una colonna larga
 * quanto lo schermo stira i pulsanti da un bordo all'altro e allunga le righe
 * di testo oltre il comodo: meglio incolonnare al centro.
 */
val ContentMaxWidth = 760.dp

/** Per le schermate di solo testo e pulsanti, dove serve ancora piu stretto. */
val NarrowMaxWidth = 520.dp

/** Riquadro bianco con titolo, usato per le sezioni dell'area genitori. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.size(12.dp))
            content()
        }
    }
}

/** Intestazione con freccia indietro. */
@Composable
fun ScreenHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .widthIn(max = ContentMaxWidth)
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Indietro")
        }
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium)
    }
}

/**
 * Selettore numerico con meno e piu. Preferito ai cursori perche il valore
 * esatto conta: i minuti di un limite non vanno indovinati trascinando.
 */
@Composable
fun Stepper(
    label: String,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = onDecrease) {
            Icon(Icons.Rounded.Remove, contentDescription = "Diminuisci $label")
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(92.dp),
        )
        FilledTonalIconButton(onClick = onIncrease) {
            Icon(Icons.Rounded.Add, contentDescription = "Aumenta $label")
        }
    }
}

/** Pastiglia per un giorno della settimana, attiva o spenta. */
@Composable
fun DayChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) colors.primary else colors.surfaceVariant,
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.onPrimary else colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp).width(28.dp),
        )
    }
}

/**
 * Riga di stato per un permesso o un requisito: spunta verde se a posto,
 * altrimenti il pulsante che porta dove si sistema.
 */
@Composable
fun RequirementRow(
    title: String,
    description: String,
    satisfied: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        if (satisfied) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = "A posto",
                tint = Color(0xFF2E7D32),
                modifier = Modifier.size(32.dp),
            )
        } else {
            androidx.compose.material3.Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Riga etichetta + interruttore. */
@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

/** Minuti in forma leggibile: 95 -> "1h 35min". */
fun formatMinutes(minutes: Int): String {
    if (minutes <= 0) return "0 min"
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0 -> "$rest min"
        rest == 0 -> "${hours}h"
        else -> "${hours}h ${rest}min"
    }
}

/** Minuti dalla mezzanotte in orologio: 1230 -> "20:30". */
fun formatTimeOfDay(minuteOfDay: Int): String {
    val normalized = ((minuteOfDay % 1440) + 1440) % 1440
    return "%02d:%02d".format(normalized / 60, normalized % 60)
}
