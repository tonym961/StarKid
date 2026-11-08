package it.iotatec.starkid.genitore.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import it.iotatec.starkid.genitore.Schermata
import it.iotatec.starkid.genitore.Stato
import it.iotatec.starkid.genitore.qr.disegnaQr

@Composable
fun Nuovo(stato: Stato) {
    var nome by rememberSaveable { mutableStateOf("") }
    var wifiRete by rememberSaveable { mutableStateOf("") }
    var wifiPassword by rememberSaveable { mutableStateOf("") }
    val creato = stato.appenaCreato

    Column(Modifier.fillMaxSize()) {
        Testata(stato = stato, titolo = "Un tablet nuovo", indietro = { stato.vaiA(Schermata.Elenco) })

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            stato.errore?.let { Avviso(it, Tono.GUASTO) }

            if (creato == null) {
                Carta {
                    Text(
                        "Come si chiama questo tablet? È un nome per te, per riconoscerlo " +
                            "nell'elenco: non lo vede il bambino.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Tinte.TestoTenue,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = nome,
                        onValueChange = { nome = it },
                        label = { Text("Nome") },
                        placeholder = { Text("Tablet del salotto") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { stato.creaTablet(nome.trim()) },
                        enabled = nome.isNotBlank() && !stato.occupato,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Crea e fammi il QR") }
                }
            } else {
                QrDiArruolamento(
                    nome = creato.optString("nome"),
                    token = creato.optString("token_arruolamento"),
                    indirizzo = stato.indirizzo,
                    wifiRete = wifiRete,
                    wifiPassword = wifiPassword,
                    onWifiRete = { wifiRete = it },
                    onWifiPassword = { wifiPassword = it },
                )
                OutlinedButton(
                    onClick = { stato.vaiA(Schermata.Elenco) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Ho finito") }
            }
        }
    }
}

@Composable
private fun QrDiArruolamento(
    nome: String,
    token: String,
    indirizzo: String,
    wifiRete: String,
    wifiPassword: String,
    onWifiRete: (String) -> Unit,
    onWifiPassword: (String) -> Unit,
) {
    Carta {
        Text("Il QR per \"$nome\"", style = MaterialTheme.typography.titleMedium, color = Tinte.Testo)
        Spacer(Modifier.height(6.dp))
        Text(
            "Sul tablet nuovo, dalla prima schermata di Android, tocca cinque volte in un punto " +
                "vuoto: si apre il lettore di QR. Inquadra questo.",
            style = MaterialTheme.typography.bodyMedium,
            color = Tinte.TestoTenue,
        )
    }

    Carta {
        Text(
            "Se il tablet non ha ancora il Wi-Fi puoi metterlo nel QR. La password NON passa " +
                "dal server: resta in questo telefono e finisce solo dentro il codice.",
            style = MaterialTheme.typography.bodyMedium,
            color = Tinte.TestoTenue,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = wifiRete,
            onValueChange = onWifiRete,
            label = { Text("Nome della rete (facoltativo)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = wifiPassword,
            onValueChange = onWifiPassword,
            label = { Text("Password del Wi-Fi") },
            singleLine = true,
            enabled = wifiRete.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
    }

    val contenuto = remember(token, indirizzo, wifiRete, wifiPassword) {
        jsonDiProvisioning(indirizzo, token, wifiRete, wifiPassword)
    }
    val codice = remember(contenuto) { runCatching { disegnaQr(contenuto, 1024) }.getOrNull() }

    if (codice == null) {
        Avviso(
            "Non riesco a fare il QR con questi dati: probabilmente il nome della rete o la " +
                "password sono troppo lunghi. Togli il Wi-Fi dal codice e mettilo a mano sul tablet.",
            Tono.GUASTO,
        )
    } else {
        // Sfondo bianco e un bordo di silenzio tutto intorno: un QR su fondo
        // scuro molti lettori non lo prendono, e senza margine nemmeno.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(12.dp))
                .padding(16.dp),
        ) {
            Image(
                bitmap = codice.asImageBitmap(),
                contentDescription = "Codice QR di arruolamento per $nome",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            )
        }
    }

    Carta {
        Text(
            "Il token si vede adesso e basta: sul server ne resta solo l'impronta. Se chiudi " +
                "questa schermata e il tablet non ha ancora letto il codice, il tablet va " +
                "cancellato e rifatto.",
            style = MaterialTheme.typography.bodyMedium,
            color = Tinte.Attesa,
        )
    }
}

/**
 * Il JSON di provisioning di Android: quello che il tablet si aspetta dal
 * lettore di QR della prima configurazione. I nomi delle chiavi sono di
 * Android, non nostri, e gli extra sono quelli che legge
 * admin/Provisioning.kt nel launcher.
 *
 * La password del Wi-Fi sta QUI e non sul server, di proposito: e il motivo
 * per cui anche nella web UI il QR si disegna nel browser.
 */
private fun jsonDiProvisioning(
    indirizzo: String,
    token: String,
    wifiRete: String,
    wifiPassword: String,
): String {
    val extra = org.json.JSONObject()
        .put("server", indirizzo)
        .put("token", token)
    val o = org.json.JSONObject()
        // Nome per esteso, identico a COMPONENTE in server/web/pagina-nuovo.js:
        // il QR fatto dal telefono e quello fatto dal browser devono arruolare
        // lo stesso tablet allo stesso modo.
        .put(
            "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME",
            "it.iotatec.starkid/it.iotatec.starkid.admin.KidsDeviceAdminReceiver",
        )
        .put("android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE", extra)
        .put("android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED", true)
    if (wifiRete.isNotBlank()) {
        o.put("android.app.extra.PROVISIONING_WIFI_SSID", wifiRete)
        if (wifiPassword.isNotEmpty()) {
            o.put("android.app.extra.PROVISIONING_WIFI_SECURITY_TYPE", "WPA")
            o.put("android.app.extra.PROVISIONING_WIFI_PASSWORD", wifiPassword)
        }
    }
    return o.toString()
}
