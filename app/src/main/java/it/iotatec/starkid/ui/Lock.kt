package it.iotatec.starkid.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import it.iotatec.starkid.domain.BlockReason

/**
 * Schermata di blocco, la stessa sia quando il bambino e nel launcher sia
 * quando il servizio lo riporta qui da un'altra app.
 *
 * L'ingresso per il genitore e una pressione lunga sul titolo: nessun pulsante
 * visibile da tentare.
 */
@Composable
fun LockScreen(
    reason: BlockReason,
    childName: String,
    onParentArea: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = emojiFor(reason),
                    style = MaterialTheme.typography.headlineLarge,
                )
                Spacer(Modifier.size(20.dp))
                Box(
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures(onLongPress = { onParentArea() })
                    },
                ) {
                    Text(
                        text = titleFor(reason),
                        style = MaterialTheme.typography.headlineLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.size(16.dp))
                Text(
                    text = messageFor(reason, childName),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.widthIn(max = NarrowMaxWidth),
                )
                Spacer(Modifier.size(36.dp))
                Text(
                    text = "Un genitore può sbloccare tenendo premuto il titolo.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Cosa puo fare il genitore dopo aver inserito il PIN su un blocco. */
@Composable
fun UnlockOptions(
    reason: BlockReason,
    onGrantBonus: (Int) -> Unit,
    onPauseGuard: () -> Unit,
    onBack: () -> Unit,
) {
    val timeRelated = reason == BlockReason.DAILY_LIMIT || reason == BlockReason.APP_LIMIT

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Sbloccato", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.size(24.dp))

            // Larghezza contenuta: su un tablet un pulsante steso da bordo a
            // bordo non si legge come un pulsante.
            val wide = Modifier.widthIn(max = NarrowMaxWidth).fillMaxWidth()

            if (timeRelated) {
                Button(onClick = { onGrantBonus(15) }, modifier = wide) {
                    Text("Regala 15 minuti per oggi")
                }
                Spacer(Modifier.size(12.dp))
                Button(onClick = { onGrantBonus(30) }, modifier = wide) {
                    Text("Regala 30 minuti per oggi")
                }
                Spacer(Modifier.size(12.dp))
            }

            OutlinedButton(onClick = onPauseGuard, modifier = wide) {
                Text("Sospendi il controllo per 10 minuti")
            }
            Spacer(Modifier.size(12.dp))
            Text(
                text = "La sospensione serve al genitore per usare il tablet senza che il " +
                    "blocco torni in mezzo. Scade da sola.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(max = NarrowMaxWidth),
            )
            Spacer(Modifier.size(24.dp))
            OutlinedButton(onClick = onBack) { Text("Indietro") }
        }
    }
}

private fun emojiFor(reason: BlockReason): String = when (reason) {
    BlockReason.OUTSIDE_SCHEDULE -> "🌙"
    BlockReason.DAILY_LIMIT -> "⏰"
    BlockReason.APP_LIMIT -> "⏳"
    BlockReason.APP_NOT_ALLOWED -> "🚫"
    BlockReason.APP_OUTSIDE_WINDOW -> "🕒"
    BlockReason.LOCKED_BY_PARENT -> "🔒"
    BlockReason.NONE -> "👋"
}

private fun titleFor(reason: BlockReason): String = when (reason) {
    BlockReason.OUTSIDE_SCHEDULE -> "Adesso si riposa"
    BlockReason.DAILY_LIMIT -> "Tempo finito!"
    BlockReason.APP_LIMIT -> "Basta con questa app"
    BlockReason.APP_NOT_ALLOWED -> "Questa app non si può aprire"
    BlockReason.APP_OUTSIDE_WINDOW -> "Non è l'ora di questa app"
    BlockReason.LOCKED_BY_PARENT -> "Il tablet è chiuso"
    BlockReason.NONE -> "Tutto a posto"
}

private fun messageFor(reason: BlockReason, childName: String): String {
    val body = when (reason) {
        BlockReason.OUTSIDE_SCHEDULE ->
            "il tablet si riaccende nella fascia oraria decisa dai tuoi genitori."
        BlockReason.DAILY_LIMIT -> "per oggi il tempo è finito. Si riprende domani!"
        BlockReason.APP_LIMIT -> "questa app ha finito i suoi minuti. Puoi usarne un'altra."
        BlockReason.APP_NOT_ALLOWED -> "questa app non è tra quelle scelte per te."
        // Si dice subito che le altre si aprono: se no suona come il tablet
        // chiuso, e il bambino lo posa invece di fare altro.
        BlockReason.APP_OUTSIDE_WINDOW ->
            "questa app si apre solo in certi orari. Le altre puoi usarle."
        // Niente "riprova piu tardi": non si riapre col tempo, si riapre
        // quando lo riapre il genitore, e dirlo chiaro evita che il
        // bambino resti li ad aspettare.
        BlockReason.LOCKED_BY_PARENT ->
            "il tablet lo hanno chiuso i tuoi genitori. Si riapre quando lo riaprono loro."
        BlockReason.NONE -> return ""
    }
    // Senza il nome la frase partirebbe in minuscolo e si leggerebbe come un
    // pezzo troncato: l'iniziale va alzata.
    return if (childName.isBlank()) body.replaceFirstChar { it.uppercase() }
    else "$childName, $body"
}
