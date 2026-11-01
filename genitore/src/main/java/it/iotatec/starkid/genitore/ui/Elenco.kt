package it.iotatec.starkid.genitore.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.iotatec.starkid.genitore.Schermata
import it.iotatec.starkid.genitore.Stato
import it.iotatec.starkid.genitore.net.Tablet

private const val ORE_SOSPETTE = 24.0
private const val ORE_ALLARME = 72.0

/**
 * Lo stato di un tablet in una parola, con le stesse soglie e le stesse
 * parole della web UI (server/web/pagina-tablets.js). Se le due dicessero
 * cose diverse dello stesso tablet, il genitore non saprebbe a chi credere.
 */
fun statoDi(t: Tablet, adesso: Long = System.currentTimeMillis() / 1000): Pair<String, Tono> {
    if (!t.arruolato && t.vistoIl == null) return "Mai arrivato" to Tono.ATTESA
    val visto = t.vistoIl ?: return "Senza notizie" to Tono.SPENTO
    val ore = (adesso - visto) / 3600.0
    return when {
        ore >= ORE_ALLARME -> "Zitto da ${Math.round(ore / 24)} giorni" to Tono.GUASTO
        ore >= ORE_SOSPETTE -> "Zitto da ${Math.round(ore)} ore" to Tono.ATTESA
        else -> "Attivo" to Tono.OK
    }
}

/** "4 minuti fa", "ieri", "mai". Le ore esatte non servono a nessuno. */
fun quando(secondi: Long?, adesso: Long = System.currentTimeMillis() / 1000): String {
    if (secondi == null || secondi <= 0) return "mai"
    val d = adesso - secondi
    return when {
        d < 60 -> "proprio ora"
        d < 3600 -> "${d / 60} minuti fa"
        d < 86_400 -> "${d / 3600} ore fa"
        d < 172_800 -> "ieri"
        else -> "${d / 86_400} giorni fa"
    }
}

@Composable
fun Elenco(stato: Stato) {
    Column(Modifier.fillMaxSize()) {
        Testata(
            stato = stato,
            azioni = {
                TextButton(onClick = { stato.carica() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.height(0.dp))
                    Text(" Aggiorna")
                }
            },
        )

        if (stato.occupato) LinearProgressIndicator(Modifier.fillMaxWidth())

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            stato.errore?.let { messaggio ->
                item { Avviso(messaggio, Tono.GUASTO) }
            }

            if (stato.tablet.isEmpty() && !stato.occupato) {
                item {
                    Carta {
                        Text(
                            "Ancora nessun tablet",
                            style = MaterialTheme.typography.titleMedium,
                            color = Tinte.Testo,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Si aggiunge qui: la app fa un codice QR, e il tablet lo inquadra " +
                                "una volta sola.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Tinte.TestoTenue,
                        )
                    }
                }
            }

            items(stato.tablet, key = { it.id }) { t ->
                SchedaTablet(t) { stato.vaiA(Schermata.Dettaglio(t.id)) }
            }

            item {
                // Un osservatore prenderebbe 403: meglio non mettergli
                // davanti il bottone. La serratura resta il server.
                if (stato.solaLettura) {
                    Avviso(
                        "Il tuo accesso e in sola lettura: puoi guardare i tablet che ti " +
                            "hanno dato, ma non cambiare niente e non aggiungerne.",
                        Tono.SPENTO,
                    )
                } else {
                    Button(
                        onClick = { stato.vaiA(Schermata.Nuovo) },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text("  Aggiungi un tablet")
                    }
                }
            }
        }
    }
}

@Composable
private fun SchedaTablet(t: Tablet, onClick: () -> Unit) {
    val (testo, tono) = statoDi(t)
    Carta(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(t.nome, style = MaterialTheme.typography.titleMedium, color = Tinte.Testo)
            Stella(misura = 22.dp, piena = t.allineato, tinta = if (t.allineato) null else Tinte.BordoForte)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Etichetta(testo, tono)
            Text("visto ${quando(t.vistoIl)}", style = MaterialTheme.typography.bodyMedium, color = Tinte.TestoTenue)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            rigaConfigurazione(t),
            style = MaterialTheme.typography.bodyMedium,
            color = Tinte.TestoTenue,
        )
    }
}

/**
 * Cosa sta facendo la configurazione. Le tre cose che contano sono: quale
 * revisione ha il server, quale ne ha confermata il tablet, e se qualche
 * campo e stato rifiutato - il kiosk si rifiuta da solo finche non lo
 * confermi col PIN sul tablet, e non e un guasto.
 */
private fun rigaConfigurazione(t: Tablet): String = when {
    !t.configurazioneLeggibile -> "La configurazione sul server non si legge: va risistemata."
    t.revisioneConfermata == null -> "Revisione ${t.revisione} pubblicata, il tablet non l'ha ancora presa."
    t.allineato -> "Revisione ${t.revisione} applicata."
    t.campiRifiutati.isNotEmpty() ->
        "Revisione ${t.revisioneConfermata} presa, ma il tablet non ha applicato: " +
            t.campiRifiutati.joinToString(", ") + "."
    else -> "Il server e alla ${t.revisione}, il tablet ha confermato la ${t.revisioneConfermata}."
}

/** La barra in alto: il marchio a sinistra, le azioni a destra. */
@Composable
fun Testata(
    stato: Stato,
    titolo: String? = null,
    indietro: (() -> Unit)? = null,
    azioni: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (indietro != null) {
            TextButton(onClick = indietro) { Text("< Indietro") }
        } else {
            Stella(misura = 24.dp)
        }
        Text(
            titolo ?: "StarKid",
            style = MaterialTheme.typography.titleMedium,
            color = Tinte.Testo,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
        azioni()
        if (indietro == null) {
            TextButton(onClick = { stato.esci() }) { Text("Esci") }
        }
    }
}
