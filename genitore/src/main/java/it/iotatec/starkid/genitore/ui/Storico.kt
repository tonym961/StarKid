package it.iotatec.starkid.genitore.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.iotatec.starkid.genitore.Schermata
import it.iotatec.starkid.genitore.Stato

@Composable
fun Storico(stato: Stato, idTablet: String) {
    val tablet = stato.tabletCon(idTablet)
    val revisioni = stato.storico
    var daRimettere by remember { mutableStateOf<Int?>(null) }

    Column(Modifier.fillMaxSize()) {
        Testata(
            stato = stato,
            titolo = "Storico di ${tablet?.nome ?: "questo tablet"}",
            indietro = { stato.vaiA(Schermata.Dettaglio(idTablet)) },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            stato.errore?.let { item { Avviso(it, Tono.GUASTO) } }

            item {
                Text(
                    "Rimettere una revisione vecchia non cancella niente: ne scrive una nuova " +
                        "uguale a quella. Lo storico resta tutto.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Tinte.TestoTenue,
                )
            }

            val quante = revisioni?.length() ?: 0
            if (quante == 0 && !stato.occupato) {
                item { Text("Nessuna revisione da mostrare.", color = Tinte.TestoTenue) }
            }

            items(quante) { indice ->
                val voce = revisioni!!.optJSONObject(indice) ?: return@items
                val numero = voce.optInt("revisione")
                val attuale = numero == tablet?.revisione
                Carta {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Revisione $numero",
                            style = MaterialTheme.typography.titleMedium,
                            color = Tinte.Testo,
                        )
                        if (attuale) Etichetta("Quella di adesso", Tono.OK)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${origineInParole(voce.optString("origine"))}, ${quando(voce.optLong("creato_il"))}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Tinte.TestoTenue,
                    )
                    if (!voce.optBoolean("leggibile", true)) {
                        Spacer(Modifier.height(8.dp))
                        Avviso("Questa revisione sul server non si rilegge: non si puo rimettere.", Tono.GUASTO)
                    } else if (!attuale) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = { daRimettere = numero }) { Text("Rimetti questa") }
                    }
                }
            }
        }
    }

    daRimettere?.let { numero ->
        AlertDialog(
            onDismissRequest = { daRimettere = null },
            title = { Text("Rimettere la revisione $numero?") },
            text = {
                Text(
                    "Verra scritta come revisione nuova, e il tablet la prendera al prossimo " +
                        "collegamento. Quello che c'e adesso resta nello storico."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    stato.tornaA(idTablet, numero)
                    daRimettere = null
                }) { Text("Rimetti") }
            },
            dismissButton = {
                TextButton(onClick = { daRimettere = null }) { Text("Lascia stare") }
            },
        )
    }
}

// Le tre parole sono quelle di ORIGINI in server/app/config_doc.py. Se un
// giorno ne spunta una quarta si vede com'e scritta, invece di sparire.
private fun origineInParole(origine: String): String = when (origine) {
    "iniziale" -> "Prima configurazione"
    "modifica" -> "Modificata da te"
    "ripristino" -> "Rimessa dallo storico"
    "" -> "Origine sconosciuta"
    else -> origine.replaceFirstChar { it.uppercase() }
}

