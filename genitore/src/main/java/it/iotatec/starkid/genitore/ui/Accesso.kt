package it.iotatec.starkid.genitore.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import it.iotatec.starkid.genitore.Stato

@Composable
fun Accesso(stato: Stato) {
    var server by rememberSaveable { mutableStateOf(stato.indirizzo) }
    var utente by rememberSaveable { mutableStateOf(stato.utente) }
    var password by rememberSaveable { mutableStateOf("") }
    val tastiera = LocalSoftwareKeyboardController.current

    val puoEntrare = server.isNotBlank() && utente.isNotBlank() && password.isNotEmpty() && !stato.occupato
    val entra: () -> Unit = {
        tastiera?.hide()
        stato.accedi(server, utente, password)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Stella(misura = 84.dp)
        Spacer(Modifier.height(16.dp))
        Text("StarKid", style = MaterialTheme.typography.headlineSmall, color = Tinte.Testo)
        Text(
            "I tablet dei tuoi bambini, da qui.",
            style = MaterialTheme.typography.bodyMedium,
            color = Tinte.TestoTenue,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))

        Carta {
            Text("Entra come genitore", style = MaterialTheme.typography.titleMedium, color = Tinte.Testo)
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = server,
                onValueChange = { server = it },
                label = { Text("Indirizzo del server") },
                placeholder = { Text("starkid.esempio.it") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Lo https:// si aggiunge da solo. Senza cifratura la app non parte: qui dentro " +
                    "passano la tua password e i token dei tablet.",
                style = MaterialTheme.typography.bodyMedium,
                color = Tinte.TestoTenue,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            OutlinedTextField(
                value = utente,
                onValueChange = { utente = it },
                label = { Text("Nome utente") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (puoEntrare) entra() }),
                modifier = Modifier.fillMaxWidth(),
            )

            stato.errore?.let {
                Spacer(Modifier.height(12.dp))
                Avviso(it, Tono.GUASTO)
            }

            Spacer(Modifier.height(16.dp))
            Button(onClick = entra, enabled = puoEntrare, modifier = Modifier.fillMaxWidth()) {
                if (stato.occupato) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Tinte.AccentoTesto,
                    )
                } else {
                    Text("Entra")
                }
            }
            Text(
                "Dopo qualche tentativo sbagliato il server fa aspettare sempre un po' di più: " +
                    "non è un guasto, è così che tiene fuori chi prova le password a macchina.",
                style = MaterialTheme.typography.bodyMedium,
                color = Tinte.TestoTenue,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "Questa app parla solo col tuo server, e la sessione non finisce in nessun backup.",
            style = MaterialTheme.typography.bodyMedium,
            color = Tinte.TestoTenue,
            textAlign = TextAlign.Center,
        )
    }
}
