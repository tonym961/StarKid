package it.iotatec.starkid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import it.iotatec.starkid.data.KidsConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Collega questo tablet al server di gestione.
 *
 * PERCHE ESISTE. Il modo previsto per legare un tablet al server e il QR
 * della prima configurazione di Android, che pero si inquadra solo a tablet
 * appena resettato. Un tablet gia in mano a un bambino, con le sue app e il
 * suo PIN, non si resetta per aggiungere la gestione a distanza: serviva un
 * modo di farlo da qui. Il codice lo da il genitore dalla web UI, con
 * "riarruola" se quello della creazione e gia stato speso.
 *
 * COSA CAMBIA ACCENDENDOLA. Da quel momento la configurazione la decide il
 * server: quello che si cambia qui sul tablet viene risistemato al giro
 * dopo. E detto nella schermata, perche scoprirlo da soli sarebbe una brutta
 * sorpresa.
 */
@Composable
fun ServerScreen(
    config: KidsConfig,
    onCollega: (indirizzo: String, codice: String) -> Unit,
    onScollega: () -> Unit,
    onSincronizzaAdesso: () -> Unit,
    esito: String?,
    occupato: Boolean,
    onBack: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ScreenHeader("Server di gestione", onBack)

            if (config.enrolled && config.rifiutatoDal > 0L) {
                // Il tablet NON si e sganciato da solo, e questo riquadro e
                // il motivo per cui puo permetterselo: dice cosa succede e
                // lascia la decisione a chi legge. Un token buttato si rifa
                // solo portando un codice nuovo fin qui.
                SectionCard("Il server non ci riconosce") {
                    Text(
                        text = "Da ${quando(config.rifiutatoDal)} il server risponde che questo " +
                            "tablet non lo conosce.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = "Il codice non e stato buttato, e il tablet continua a chiedere. " +
                            "Spesso e passeggero: il server ripristinato da una copia vecchia, un " +
                            "aggiornamento a meta. In quei casi torna a funzionare da solo.\n\n" +
                            "Se invece il tablet e stato cancellato dal server, non tornera: " +
                            "scollegalo qui sotto e ricollegalo con un codice nuovo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (config.enrolled) {
                SectionCard("Collegato") {
                    Text(config.serverUrl, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = if (config.lastSync == 0L) {
                            "Non si e ancora collegato nemmeno una volta."
                        } else {
                            "Ultimo contatto: ${quando(config.lastSync)}. " +
                                "Revisione applicata: ${config.syncedRevision}."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = "Il tablet chiama il server ogni cinque minuti. Il server non " +
                            "chiama il tablet: non c'e nessuna porta aperta verso qui, e per " +
                            "questo una modifica fatta da lontano arriva al giro dopo e non " +
                            "subito.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(12.dp))
                    Button(onClick = onSincronizzaAdesso, enabled = !occupato) {
                        Text(if (occupato) "Sto chiamando..." else "Sincronizza adesso")
                    }
                    Spacer(Modifier.size(10.dp))
                    OutlinedButton(onClick = onScollega, enabled = !occupato) {
                        Text("Scollega dal server")
                    }
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = "Scollegando, il tablet continua a funzionare esattamente come " +
                            "adesso: tiene l'ultima configurazione ricevuta e smette di " +
                            "chiedere. Dal server risultera zitto.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Collegamento(config, onCollega, occupato)
            }

            esito?.let {
                SectionCard("Esito") {
                    Text(it, style = MaterialTheme.typography.bodyLarge)
                }
            }

            SectionCard("Cosa esce da questo tablet") {
                Text(
                    text = "Solo due cose: quale configurazione e stata applicata, e le cinque " +
                        "spie dei permessi. Non il nome del bambino, non il PIN, non quanto ha " +
                        "giocato e a cosa. Quelle restano qui, ed e una scelta.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Collegamento(
    config: KidsConfig,
    onCollega: (String, String) -> Unit,
    occupato: Boolean,
) {
    var indirizzo by remember { mutableStateOf(config.serverUrl) }
    var codice by remember { mutableStateOf("") }
    val pulito = codice.trim()
    val indirizzoPulito = indirizzo.trim().trimEnd('/')
    // Lo stesso che accetta il server (RichiestaArruolamento in models.py).
    val codiceValido = Regex("^[A-Za-z0-9_\\-]{16,200}$").matches(pulito)

    SectionCard("Non collegato") {
        Text(
            text = "Questo tablet non parla con nessun server: tutto quello che c'e qui lo ha " +
                "deciso chi ha in mano il tablet. Collegandolo, un genitore puo cambiare le " +
                "regole da lontano.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    SectionCard("Collega") {
        OutlinedTextField(
            value = indirizzo,
            onValueChange = { indirizzo = it },
            label = { Text("Indirizzo del server") },
            placeholder = { Text("starkid.esempio.it") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = "Lo https:// si aggiunge da solo. Senza cifratura non si collega: qui " +
                "dentro passa il codice di questo tablet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(12.dp))
        OutlinedTextField(
            value = codice,
            onValueChange = { codice = it },
            label = { Text("Codice di arruolamento") },
            singleLine = true,
            isError = pulito.isNotEmpty() && !codiceValido,
            // Niente maiuscola automatica: il codice distingue maiuscole e
            // minuscole, e la tastiera che alza la prima lettera lo rompe.
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = "Lo trovi sul server quando crei il tablet. Si vede una volta sola: se e " +
                "andato perso, dal server si chiede di riarruolare il tablet e ne esce uno " +
                "nuovo, senza perdere la configurazione.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(14.dp))
        Button(
            onClick = { onCollega(indirizzoPulito, pulito) },
            enabled = !occupato && indirizzoPulito.isNotBlank() && codiceValido,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (occupato) "Sto chiamando..." else "Collega")
        }
        Spacer(Modifier.size(10.dp))
        Text(
            text = "Da collegato, la configurazione la decide il server: quello che cambi qui " +
                "sul tablet viene risistemato al giro dopo.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun quando(istante: Long): String {
    val passati = System.currentTimeMillis() - istante
    return when {
        passati < 60_000 -> "proprio ora"
        passati < 3_600_000 -> "${passati / 60_000} minuti fa"
        passati < 86_400_000 -> "${passati / 3_600_000} ore fa"
        else -> SimpleDateFormat("d MMMM, HH:mm", Locale.ITALIAN).format(Date(istante))
    }
}
