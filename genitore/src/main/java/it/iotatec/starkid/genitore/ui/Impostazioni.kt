package it.iotatec.starkid.genitore.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import it.iotatec.starkid.genitore.BuildConfig
import it.iotatec.starkid.genitore.Schermata
import it.iotatec.starkid.genitore.Stato
import it.iotatec.starkid.genitore.net.Aggiornamenti

/**
 * Impostazioni: la versione, gli aggiornamenti, e chi sei.
 *
 * Esiste perche il controllo degli aggiornamenti partiva da solo una volta
 * per avvio e basta: se l'app era gia aperta quando usciva la versione
 * nuova, non se ne accorgeva nessuno fino alla riapertura, e dal di fuori
 * sembrava che l'aggiornamento automatico non funzionasse. Da qui lo si
 * chiede, e soprattutto si vede la risposta - anche quando la risposta e
 * "sei gia all'ultima", che senza un posto dove dirla non la diceva nessuno.
 */
@Composable
fun Impostazioni(stato: Stato) {
    val contesto = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        Testata(
            stato = stato,
            titolo = "Impostazioni",
            indietro = { stato.vaiA(Schermata.Elenco) },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            stato.errore?.let { item { Avviso(it, Tono.GUASTO) } }

            item {
                Carta {
                    Text(
                        "Aggiornamenti",
                        style = MaterialTheme.typography.titleMedium,
                        color = Tinte.Testo,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Hai la versione ${BuildConfig.VERSION_NAME}. L'app guarda da sola " +
                            "una volta a ogni avvio, su GitHub: da qui puoi chiederglielo " +
                            "adesso, per esempio se l'hai lasciata aperta.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Tinte.TestoTenue,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { stato.cercaAggiornamenti(esplicito = true) },
                        enabled = !stato.occupato && !stato.scaricando,
                    ) { Text("Controlla adesso") }

                    val nuova = stato.novita
                    if (nuova != null) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "C'è la versione ${nuova.versione}",
                            style = MaterialTheme.typography.titleMedium,
                            color = Tinte.Testo,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Pesa ${nuova.byte / (1024 * 1024)} MB e arriva dalle release di " +
                                "GitHub, firmata con la stessa chiave di questa app: se la " +
                                "firma non torna, non la installo.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Tinte.TestoTenue,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { stato.aggiorna(contesto) },
                            enabled = !stato.scaricando,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (stato.scaricando) "Scarico..." else "Scarica e installa")
                        }
                        if (!Aggiornamenti.puoInstallare(contesto)) {
                            Spacer(Modifier.height(8.dp))
                            Avviso(
                                "Android chiede il permesso di installare app da StarKid: " +
                                    "premendo il bottone ti porta dove si concede.",
                                Tono.ATTESA,
                            )
                        }
                    }
                }
            }

            item {
                Carta {
                    Text(
                        "Questo telefono",
                        style = MaterialTheme.typography.titleMedium,
                        color = Tinte.Testo,
                    )
                    Spacer(Modifier.height(10.dp))
                    Riga("Server", stato.indirizzo.ifBlank { "non impostato" })
                    Riga("Utente", stato.utente.ifBlank { "nessuno" })
                    Riga(
                        "Permessi",
                        when (stato.ruolo) {
                            "admin" -> "amministratore: fai tutto"
                            "genitore" -> "genitore: i tablet che ti hanno dato"
                            "osservatore" -> "osservatore: guardi e basta"
                            else -> stato.ruolo.ifBlank { "sconosciuti" }
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { stato.esci() }) { Text("Esci") }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
