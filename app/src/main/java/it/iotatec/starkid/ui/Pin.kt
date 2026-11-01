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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Larghezza piena del tastierino, quando lo schermo la concede. */
private val KEYPAD_WIDTH = 300.dp

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

    val premiCifra: (Char) -> Unit = { digit ->
        if (entered.length < PIN_MAX_LENGTH) entered += digit
    }
    val conferma: () -> Unit = {
        // Si svuota qui, non quando cambia il messaggio d'errore: al secondo
        // PIN sbagliato di fila l'errore e lo stesso di prima, quindi non
        // cambia, e le cifre nuove si accodavano alle vecchie finche il
        // tastierino si piantava a otto e il genitore restava fuori.
        if (entered.length >= PIN_MIN_LENGTH) {
            val code = entered
            entered = ""
            onSubmit(code)
        }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(24.dp)) {
            // QUANTO SPAZIO C'E DAVVERO.
            //
            // La colonna in piedi chiede circa 690dp di altezza: 170
            // d'intestazione, i pallini, 56 d'errore, 319 di tastierino, 80
            // per Annulla. Su un tablet da 400dp di altezza - cioe un 800x1340
            // a densita 2x tenuto in orizzontale, che e il bersaglio di
            // StarKid - non ci stanno, e Compose non ritaglia: i pezzi si
            // sovrappongono e i numeri diventano illeggibili. Succedeva
            // davvero, sul tablet vero.
            //
            // Sotto i 640dp si mette tutto affiancato: a sinistra il testo, a
            // destra il tastierino. In orizzontale e anche piu comodo, perche
            // i tasti restano dalla parte della mano.
            val stretto = maxHeight < 640.dp
            val latoTastierino = if (stretto) {
                // Il tastierino e alto (larghezza/3)/1.3*4 piu 36 di spazi:
                // da qui si ricava la larghezza massima che ci sta in altezza.
                minOf(KEYPAD_WIDTH, (maxHeight - 84.dp) * 0.975f)
            } else {
                KEYPAD_WIDTH
            }

            val testata = @Composable {
                // Intestazione ed errore occupano un'altezza fissa. Senza, il
                // tastierino scorre su e giu quando il testo sopra cambia
                // numero di righe - fra "Scegli il PIN" e "Ripeti il PIN", o
                // quando compare un errore - e le dita troverebbero un tasto
                // diverso da quello di prima.
                Box(
                    modifier = Modifier
                        .height(if (stretto) 120.dp else 170.dp)
                        .widthIn(max = NarrowMaxWidth),
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

                Box(
                    modifier = Modifier.height(if (stretto) 48.dp else 56.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (error != null) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            // Anche l'annullamento ha il suo posto riservato: comparendo solo
            // in alcuni passaggi tirerebbe su tutto il resto, e il tastierino
            // si muoverebbe di nuovo.
            val annulla = @Composable {
                Box(
                    modifier = Modifier.height(if (stretto) 56.dp else 80.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (onCancel != null) {
                        TextButton(onClick = onCancel) { Text("Annulla") }
                    }
                }
            }

            val tastierino = @Composable {
                Keypad(
                    larghezza = latoTastierino,
                    onDigit = premiCifra,
                    onBackspace = { entered = entered.dropLast(1) },
                    onConfirm = conferma,
                    confirmEnabled = entered.length >= PIN_MIN_LENGTH,
                )
            }

            if (stretto) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        testata()
                        annulla()
                    }
                    Spacer(Modifier.size(24.dp))
                    tastierino()
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        // Rete di sicurezza: su uno schermo che non ci sta
                        // comunque, si scorre invece di sovrapporsi.
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    testata()
                    tastierino()
                    annulla()
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
    larghezza: Dp,
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
        modifier = Modifier.width(larghezza),
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
