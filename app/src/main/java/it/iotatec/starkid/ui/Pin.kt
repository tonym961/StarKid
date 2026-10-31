package it.iotatec.starkid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

const val PIN_MIN_LENGTH = 4
private const val PIN_MAX_LENGTH = 8

/**
 * Tastierino numerico per il PIN del genitore. Si usa una tastiera dedicata e
 * non un campo di testo: su un tablet in mano a un bambino la tastiera di
 * sistema porta con se suggerimenti e scorciatoie che e meglio non aprire.
 *
 * [error] arriva da fuori: quando cambia, il codice inserito viene azzerato.
 */
@Composable
fun PinPad(
    title: String,
    subtitle: String? = null,
    error: String? = null,
    onSubmit: (String) -> Unit,
    onCancel: (() -> Unit)? = null,
) {
    var entered by remember { mutableStateOf("") }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Intestazione ed errore occupano un'altezza fissa. Senza questo il
            // tastierino scorre su e giu quando il testo sopra cambia numero di
            // righe: fra "Scegli il PIN" e "Ripeti il PIN", o quando compare un
            // errore, le dita troverebbero un tasto diverso da quello di prima.
            Box(
                modifier = Modifier.height(170.dp).widthIn(max = NarrowMaxWidth),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(title, style = MaterialTheme.typography.headlineMedium)
                    if (subtitle != null) {
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            PinDots(entered.length)

            Box(modifier = Modifier.height(56.dp), contentAlignment = Alignment.Center) {
                if (error != null) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            Keypad(
                onDigit = { digit ->
                    if (entered.length < PIN_MAX_LENGTH) entered += digit
                },
                onBackspace = { entered = entered.dropLast(1) },
                onConfirm = {
                    // Si svuota qui, non quando cambia il messaggio d'errore:
                    // al secondo PIN sbagliato di fila l'errore e lo stesso di
                    // prima, quindi non cambia, e le cifre nuove si
                    // accodavano alle vecchie finche il tastierino si
                    // piantava a otto e il genitore restava fuori.
                    if (entered.length >= PIN_MIN_LENGTH) {
                        val code = entered
                        entered = ""
                        onSubmit(code)
                    }
                },
                confirmEnabled = entered.length >= PIN_MIN_LENGTH,
            )

            // Anche l'annullamento va riservato sempre: comparendo solo in
            // alcuni passaggi, in fondo a una colonna centrata, tirerebbe su
            // tutto il resto e il tastierino si muoverebbe di nuovo.
            Box(modifier = Modifier.height(80.dp), contentAlignment = Alignment.Center) {
                if (onCancel != null) {
                    TextButton(onClick = onCancel) { Text("Annulla") }
                }
            }
        }
    }
}

@Composable
private fun PinDots(count: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(PIN_MAX_LENGTH) { index ->
            val filled = index < count
            Box(
                modifier = Modifier.size(18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (filled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(if (filled) 18.dp else 12.dp),
                ) {}
            }
        }
    }
}

@Composable
private fun Keypad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean,
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("<", "0", "OK"),
    )
    Column(
        modifier = Modifier.width(300.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        for (row in rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (key in row) {
                    KeypadKey(
                        label = key,
                        enabled = key != "OK" || confirmEnabled,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            when (key) {
                                "<" -> onBackspace()
                                "OK" -> onConfirm()
                                else -> onDigit(key[0])
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun KeypadKey(
    label: String,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val isConfirm = label == "OK"
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.aspectRatio(1.3f),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isConfirm) colors.primary else colors.surface,
            disabledContainerColor = colors.surfaceVariant,
        ),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                color = if (isConfirm && enabled) colors.onPrimary else colors.onSurface,
            )
        }
    }
}

/**
 * Primo avvio: si imposta il PIN digitandolo due volte. Resta nello stesso
 * composable perche il secondo passaggio deve poter tornare al primo senza
 * portarsi dietro stato altrove.
 */
@Composable
fun PinSetup(onPinChosen: (String) -> Unit, onCancel: (() -> Unit)? = null) {
    var first by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val pending = first
    if (pending == null) {
        PinPad(
            title = "Scegli il PIN",
            subtitle = "Serve ai genitori per entrare nelle impostazioni.\nAlmeno $PIN_MIN_LENGTH cifre.",
            error = error,
            onSubmit = { chosen ->
                error = null
                first = chosen
            },
            onCancel = onCancel,
        )
    } else {
        PinPad(
            title = "Ripeti il PIN",
            subtitle = "Scrivilo di nuovo per conferma.",
            error = error,
            onSubmit = { repeated ->
                if (repeated == pending) {
                    onPinChosen(repeated)
                } else {
                    error = "I due codici non coincidono"
                    first = null
                }
            },
            onCancel = {
                first = null
                error = null
            },
        )
    }
}
