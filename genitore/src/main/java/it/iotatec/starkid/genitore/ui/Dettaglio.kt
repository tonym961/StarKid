package it.iotatec.starkid.genitore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.iotatec.starkid.genitore.Pin
import it.iotatec.starkid.genitore.Schermata
import it.iotatec.starkid.genitore.Stato
import it.iotatec.starkid.genitore.net.Spia
import org.json.JSONArray
import org.json.JSONObject

private val TEMI = listOf("pulito" to "Pulito", "spaziale" to "Spaziale")
private val SFONDI = mapOf(
    "pulito" to listOf("lilla" to "Lilla", "cielo" to "Cielo", "prato" to "Prato", "tramonto" to "Tramonto"),
    "spaziale" to listOf("notte" to "Notte", "nebulosa" to "Nebulosa", "marte" to "Marte", "orbita" to "Orbita"),
)
private val SCHERMATE = listOf("categorie" to "A scomparti", "tutte" to "Tutte in vista")
private val ORIENTAMENTI = listOf(
    "orizzontale" to "Orizzontale", "verticale" to "Verticale", "automatico" to "Automatico",
)
private val GIOCHI = listOf(
    "memory" to "Memory", "conta" to "Conta", "acchiappa" to "Acchiappa", "sequenza" to "Sequenza",
    "intruso" to "Intruso", "disegna" to "Disegna", "macchinine" to "Macchinine", "xilofono" to "Xilofono",
    "puzzle" to "Puzzle", "confronto" to "Confronto", "serie" to "Serie", "citta" to "Le citta del mondo",
)
// 1 = domenica, come java.util.Calendar.DAY_OF_WEEK, che e la numerazione che
// usa il tablet. Qui si mostra la settimana che comincia di lunedi, come la
// legge chi vive in Italia, ma i numeri salvati restano quelli.
private val GIORNI = listOf(2 to "L", 3 to "M", 4 to "M", 5 to "G", 6 to "V", 7 to "S", 1 to "D")

@Composable
fun Dettaglio(stato: Stato, idTablet: String) {
    val tablet = stato.tabletCon(idTablet)
    val documento = stato.configurazione

    Column(Modifier.fillMaxSize()) {
        Testata(
            stato = stato,
            titolo = tablet?.nome ?: "Tablet",
            indietro = { stato.vaiA(Schermata.Elenco) },
            azioni = {
                TextButton(onClick = { stato.vaiA(Schermata.Storico(idTablet)) }) { Text("Storico") }
            },
        )

        if (documento == null) {
            Column(Modifier.padding(16.dp)) {
                stato.errore?.let { Avviso(it, Tono.GUASTO) }
                if (stato.errore == null) {
                    Text("Sto leggendo la configurazione...", color = Tinte.TestoTenue)
                }
            }
            return@Column
        }

        Modulo(stato, idTablet, documento)
    }
}

@Composable
private fun Modulo(stato: Stato, idTablet: String, originale: JSONObject) {
    val tablet = stato.tabletCon(idTablet)

    // Una copia da modificare: il documento del server resta intatto finche
    // il salvataggio non e andato a buon fine, cosi "annulla" e davvero il
    // ritorno a quello che c'e sul server.
    var bozza by remember(originale.toString()) { mutableStateOf(JSONObject(originale.toString())) }
    var cambiato by remember(originale.toString()) { mutableStateOf(false) }

    fun modifica(blocco: JSONObject.() -> Unit) {
        val nuova = JSONObject(bozza.toString())
        nuova.blocco()
        bozza = nuova
        cambiato = true
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        stato.errore?.let { item { Avviso(it, Tono.GUASTO) } }

        // ---- le spie -----------------------------------------------------
        item {
            Carta {
                Text("Come sta il tablet", style = MaterialTheme.typography.titleMedium, color = Tinte.Testo)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (tablet?.saluteIl != null)
                        "Secondo il tablet, ${quando(tablet.saluteIl)}."
                    else
                        "Il tablet non ha ancora mandato niente.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Tinte.TestoTenue,
                )
                Spacer(Modifier.height(10.dp))
                for (spia in Spia.entries) {
                    val acceso = tablet?.saluteDichiarata?.get(spia.chiave)
                    RigaSpia(spia.titolo, spia.conseguenza, acceso)
                }
            }
        }

        // ---- aspetto -----------------------------------------------------
        item {
            Carta {
                Titoletto("Come si vede")
                val tema = bozza.optString("tema", "pulito")
                Scelta("Tema", TEMI, tema) { nuovo ->
                    modifica {
                        put("tema", nuovo)
                        // Lo sfondo appartiene al tema: cambiando tema, uno
                        // sfondo dell'altro tema verrebbe rifiutato dal
                        // server con 422. Si riporta al primo del nuovo tema
                        // invece di lasciare un documento che non si puo
                        // salvare.
                        val ammessi = SFONDI.getValue(nuovo).map { it.first }
                        if (optString("sfondo") !in ammessi) put("sfondo", ammessi.first())
                    }
                }
                Spacer(Modifier.height(10.dp))
                Scelta("Sfondo", SFONDI.getValue(tema), bozza.optString("sfondo")) {
                    modifica { put("sfondo", it) }
                }
                Spacer(Modifier.height(10.dp))
                Scelta("Schermata", SCHERMATE, bozza.optString("schermata")) {
                    modifica { put("schermata", it) }
                }
                Spacer(Modifier.height(10.dp))
                Scelta("Orientamento", ORIENTAMENTI, bozza.optString("orientamento")) {
                    modifica { put("orientamento", it) }
                }
                Spacer(Modifier.height(10.dp))
                Interruttore("Suoni", "I versetti e le musichette dei giochi.", bozza.optBoolean("suoni", true)) {
                    modifica { put("suoni", it) }
                }
            }
        }

        // ---- tempo -------------------------------------------------------
        item {
            Carta {
                Titoletto("Quanto e quando")
                val minuti = bozza.optInt("limite_giornaliero", 60)
                Text(
                    if (minuti == 0) "Nessun limite di minuti" else "$minuti minuti al giorno",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Tinte.Testo,
                )
                Text(
                    "Il tempo dei giochi dentro StarKid conta in questo tetto: il limite non si " +
                        "aggira passando ai giochi di casa.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Tinte.TestoTenue,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        modifica { put("limite_giornaliero", (minuti - 15).coerceAtLeast(0)) }
                    }) { Text("-15") }
                    OutlinedButton(onClick = {
                        modifica { put("limite_giornaliero", (minuti + 15).coerceAtMost(1440)) }
                    }) { Text("+15") }
                    OutlinedButton(onClick = { modifica { put("limite_giornaliero", 0) } }) {
                        Text("Senza limite")
                    }
                }

                Spacer(Modifier.height(16.dp))
                val fascia = bozza.optJSONObject("fascia") ?: JSONObject()
                Interruttore(
                    "Fascia oraria",
                    "Fuori da questa fascia il tablet non si accende.",
                    fascia.optBoolean("attiva", true),
                ) { attiva ->
                    modifica { put("fascia", JSONObject(fascia.toString()).put("attiva", attiva)) }
                }
                if (fascia.optBoolean("attiva", true)) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Orario("Dalle", fascia.optInt("inizio", 480)) { m ->
                            modifica { put("fascia", JSONObject(fascia.toString()).put("inizio", m)) }
                        }
                        Orario("Alle", fascia.optInt("fine", 1200)) { m ->
                            modifica { put("fascia", JSONObject(fascia.toString()).put("fine", m)) }
                        }
                    }
                    if (fascia.optInt("fine", 1200) < fascia.optInt("inizio", 480)) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Questa fascia scavalca la mezzanotte. Va bene: il tablet la legge cosi.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Tinte.TestoTenue,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Giorni(fascia.optJSONArray("giorni")) { nuovi ->
                        modifica { put("fascia", JSONObject(fascia.toString()).put("giorni", nuovi)) }
                    }
                }
            }
        }

        // ---- blocco a distanza -------------------------------------------
        // In un riquadro suo e non dentro "Kiosk": il kiosk dice DOVE il
        // bambino puo stare, questo dice che adesso non puo stare da nessuna
        // parte. Sta in alto perche e la cosa che si cerca di fretta.
        item {
            Carta {
                Titoletto("Blocco")
                Interruttore(
                    "Tablet bloccato",
                    "Il tablet si ferma su una schermata che dice che lo hai chiuso tu.",
                    bozza.optBoolean("bloccato"),
                ) { modifica { put("bloccato", it) } }
                if (bozza.optBoolean("bloccato")) {
                    Spacer(Modifier.height(8.dp))
                    Avviso(
                        "Arriva alla prossima chiamata del tablet, entro un minuto: il " +
                            "tablet chiama il server, il server non chiama il tablet. Se e " +
                            "spento o senza rete, arriva quando torna. Il PIN sul tablet non " +
                            "lo apre.",
                        Tono.ATTESA,
                    )
                }
            }
        }

        // ---- kiosk -------------------------------------------------------
        item {
            Carta {
                Titoletto("Kiosk")
                Interruttore(
                    "Modalita kiosk",
                    "Il tablet resta dentro StarKid e alle app che hai permesso.",
                    bozza.optBoolean("kiosk"),
                ) { modifica { put("kiosk", it) } }
                if (bozza.optBoolean("kiosk")) {
                    Spacer(Modifier.height(8.dp))
                    Avviso(
                        "Accenderlo da qui e solo una richiesta: sul tablet va confermata col PIN. " +
                            "Finche non la confermi, qui resta \"in attesa\". Spegnerlo invece vale subito.",
                        Tono.ATTESA,
                    )
                    Spacer(Modifier.height(10.dp))
                    Interruttore(
                        "App recenti dentro il kiosk",
                        "Lascia raggiungibile il tasto delle app recenti.",
                        bozza.optBoolean("kiosk_recenti"),
                    ) { modifica { put("kiosk_recenti", it) } }
                }
            }
        }

        // ---- giochi ------------------------------------------------------
        item {
            val spenti = bozza.optJSONArray("giochi_spenti").aListaDiTesti()
            Carta {
                Titoletto("Giochi")
                Text(
                    "${GIOCHI.size - spenti.size} accesi su ${GIOCHI.size}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Tinte.TestoTenue,
                )
                Spacer(Modifier.height(8.dp))
                for ((id, nome) in GIOCHI) {
                    val acceso = id !in spenti
                    Interruttore(nome, null, acceso) { ora ->
                        val nuovi = if (ora) spenti - id else spenti + id
                        modifica { put("giochi_spenti", JSONArray(nuovi)) }
                    }
                }
            }
        }

        // ---- app ---------------------------------------------------------
        item {
            AppPermesse(bozza.optJSONArray("app_permesse").aListaDiTesti()) { nuove ->
                modifica { put("app_permesse", JSONArray(nuove)) }
            }
        }

        // ---- regole per app ----------------------------------------------
        // In sola lettura di proposito. Un editor di fasce fatto di
        // interruttori piccoli, su un telefono, e il modo migliore per
        // chiudere per sbaglio un'app a un bambino: le regole si scrivono
        // dalla web UI, con lo schermo grande, e da qui si controlla che
        // siano quelle giuste. Il documento le riporta indietro intatte
        // perche la bozza e una copia di tutto, non solo dei campi noti.
        item {
            val regole = bozza.optJSONArray("regole_app")
            if (regole != null && regole.length() > 0) {
                Carta {
                    Titoletto("Regole per app")
                    for (i in 0 until regole.length()) {
                        val regola = regole.optJSONObject(i) ?: continue
                        Riga(regola.optString("pacchetto"), descriviRegola(regola))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Si cambiano dalla pagina web del tablet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Tinte.TestoTenue,
                    )
                }
            }
        }

        // ---- PIN ---------------------------------------------------------
        // Questa invece si scrive da qui, al contrario delle regole per app:
        // quattro cifre su un telefono si battono meglio che su qualunque
        // altra cosa, e il momento in cui serve - "ho dimenticato il PIN del
        // tablet di mio figlio" - e esattamente quello in cui si ha il
        // telefono in mano e il tablet no.
        item {
            PinDaQui(bozza.opt("impronta_pin")) { nuova ->
                modifica { put("impronta_pin", nuova ?: JSONObject.NULL) }
            }
        }

        // ---- salva -------------------------------------------------------
        item {
            Column {
                Button(
                    onClick = { stato.salva(idTablet, bozza) },
                    enabled = cambiato && !stato.occupato && !stato.solaLettura,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        when {
                            stato.solaLettura -> "Non puoi salvare: accesso in sola lettura"
                            cambiato -> "Salva"
                            else -> "Niente da salvare"
                        }
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Il tablet la prende al prossimo collegamento, non subito.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Tinte.TestoTenue,
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ---------------------------------------------------------------------------
// I pezzi del modulo
// ---------------------------------------------------------------------------

@Composable
private fun Titoletto(testo: String) {
    Text(testo, style = MaterialTheme.typography.titleMedium, color = Tinte.Testo)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun RigaSpia(titolo: String, conseguenza: String, acceso: Boolean?) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val tinta = when (acceso) {
            true -> Tinte.Ok
            false -> Tinte.Guasto
            null -> Tinte.TestoTenue
        }
        // Piena quando va, vuota quando manca: la FORMA porta l'informazione,
        // non solo il colore.
        Stella(misura = 18.dp, piena = acceso == true, tinta = tinta, modifier = Modifier.padding(top = 3.dp))
        Column {
            Text(titolo, style = MaterialTheme.typography.bodyLarge, color = Tinte.Testo)
            if (acceso != true) {
                Text(
                    if (acceso == null) "Il tablet non l'ha ancora detto." else conseguenza,
                    style = MaterialTheme.typography.bodyMedium,
                    color = tinta,
                )
            }
        }
    }
}

@Composable
private fun Scelta(
    nome: String,
    opzioni: List<Pair<String, String>>,
    scelto: String,
    onScelta: (String) -> Unit,
) {
    Text(nome, style = MaterialTheme.typography.labelMedium, color = Tinte.TestoTenue)
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        for ((valore, etichetta) in opzioni) {
            val attivo = valore == scelto
            Box(
                Modifier
                    .weight(1f)
                    .background(
                        if (attivo) Tinte.Accento else Tinte.Campo,
                        RoundedCornerShape(10.dp),
                    )
                    .border(1.dp, if (attivo) Tinte.Accento else Tinte.Bordo, RoundedCornerShape(10.dp))
                    .clickable { onScelta(valore) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    etichetta,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (attivo) Tinte.AccentoTesto else Tinte.Testo,
                )
            }
        }
    }
}

@Composable
private fun Interruttore(titolo: String, sotto: String?, acceso: Boolean, onCambia: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(titolo, style = MaterialTheme.typography.bodyLarge, color = Tinte.Testo)
            if (sotto != null) {
                Text(sotto, style = MaterialTheme.typography.bodyMedium, color = Tinte.TestoTenue)
            }
        }
        Switch(checked = acceso, onCheckedChange = onCambia)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Orario(nome: String, minuti: Int, onCambia: (Int) -> Unit) {
    var apri by remember { mutableStateOf(false) }
    Column {
        Text(nome, style = MaterialTheme.typography.labelMedium, color = Tinte.TestoTenue)
        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = { apri = true }) { Text(oraMinuti(minuti)) }
    }
    if (apri) {
        val scelta = rememberTimePickerState(
            initialHour = minuti / 60,
            initialMinute = minuti % 60,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { apri = false },
            confirmButton = {
                TextButton(onClick = {
                    onCambia(scelta.hour * 60 + scelta.minute)
                    apri = false
                }) { Text("Va bene") }
            },
            dismissButton = { TextButton(onClick = { apri = false }) { Text("Lascia stare") } },
            text = { TimePicker(state = scelta) },
        )
    }
}

@Composable
private fun Giorni(attuali: JSONArray?, onCambia: (JSONArray) -> Unit) {
    val scelti = buildList {
        if (attuali != null) for (i in 0 until attuali.length()) add(attuali.optInt(i))
    }
    Text("Giorni", style = MaterialTheme.typography.labelMedium, color = Tinte.TestoTenue)
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((numero, lettera) in GIORNI) {
            val attivo = numero in scelti
            Box(
                Modifier
                    .size(38.dp)
                    .background(if (attivo) Tinte.Accento else Tinte.Campo, CircleShape)
                    .border(1.dp, if (attivo) Tinte.Accento else Tinte.Bordo, CircleShape)
                    .clickable {
                        // Almeno un giorno: il server rifiuta una lista
                        // vuota con 422, e una fascia senza giorni non vuol
                        // dire niente.
                        val nuovi = if (attivo) scelti - numero else scelti + numero
                        if (nuovi.isNotEmpty()) onCambia(JSONArray(nuovi.sorted()))
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    lettera,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (attivo) Tinte.AccentoTesto else Tinte.Testo,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun AppPermesse(attuali: List<String>, onCambia: (List<String>) -> Unit) {
    var nuova by remember { mutableStateOf("") }
    // Lo stesso del server (schema/config.schema.json): solo nomi di
    // pacchetto, perche un nome di pacchetto non puo essere un indirizzo,
    // un percorso o un comando.
    val valido = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+$")
    Carta {
        Titoletto("App permesse")
        Text(
            "Le app installate sul tablet che il bambino puo aprire. Di solito si scelgono " +
                "dall'area genitori sul tablet, dove si vedono con nome e icona: qui si " +
                "aggiungono scrivendo il nome del pacchetto.",
            style = MaterialTheme.typography.bodyMedium,
            color = Tinte.TestoTenue,
        )
        Spacer(Modifier.height(10.dp))
        if (attuali.isEmpty()) {
            Text("Nessuna: il bambino vede solo StarKid e i suoi giochi.", color = Tinte.TestoTenue)
        }
        for (pacchetto in attuali) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(pacchetto, style = MaterialTheme.typography.bodyMedium, color = Tinte.Testo)
                TextButton(onClick = { onCambia(attuali - pacchetto) }) { Text("Togli") }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = nuova,
            onValueChange = { nuova = it.trim() },
            label = { Text("com.esempio.app") },
            singleLine = true,
            isError = nuova.isNotEmpty() && !valido.matches(nuova),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { onCambia(attuali + nuova); nuova = "" },
            enabled = valido.matches(nuova) && nuova !in attuali,
        ) { Text("Aggiungi") }
    }
}

/**
 * Il PIN dell'area genitori, da qui.
 *
 * [corrente] e quello che c'e nel documento: o un oggetto con sale, giri e
 * impronta, o JSONObject.NULL. Le cifre non ci sono e non si possono
 * mostrare - nemmeno il server le ha - quindi qui si dice solo se un PIN da
 * qui c'e o no, e se ne scrive uno nuovo.
 */
@Composable
private fun PinDaQui(corrente: Any?, onCambia: (JSONObject?) -> Unit) {
    var scritto by remember { mutableStateOf("") }
    val cEGia = corrente is JSONObject
    val valido = Pin.valido(scritto)

    Carta {
        Titoletto("PIN dell'area genitori")
        Text(
            if (cEGia) {
                "Un PIN mandato dalla gestione c'e gia. Qual e non si puo rileggere: " +
                    "qui c'e solo la sua impronta, e nemmeno il server sa a che cifre " +
                    "corrisponda. Per cambiarlo, scrivine uno nuovo."
            } else {
                "Da qui non e mai stato mandato un PIN: sul tablet vale quello scelto " +
                    "stando davanti al tablet."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Tinte.TestoTenue,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = scritto,
            onValueChange = { nuovo -> scritto = nuovo.filter { it in '0'..'9' }.take(Pin.LUNGHEZZA_MASSIMA) },
            label = { Text("PIN nuovo, da ${Pin.LUNGHEZZA_MINIMA} a ${Pin.LUNGHEZZA_MASSIMA} cifre") },
            singleLine = true,
            isError = scritto.isNotEmpty() && !valido,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                // Il calcolo e ventimila giri di SHA-256: su un telefono
                // sono decimi di secondo, e succede al tocco, non mentre si
                // scrive. Non va spostato dentro onValueChange.
                onClick = { onCambia(Pin.impronta(scritto)); scritto = "" },
                enabled = valido,
            ) { Text("Manda questo PIN") }
            if (cEGia) {
                TextButton(onClick = { onCambia(null); scritto = "" }) {
                    Text("Smetti di mandarlo")
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Il PIN non arriva al server: parte l'impronta, calcolata qui. " +
                "\"Smetti di mandarlo\" non toglie il PIN dal tablet - quello da " +
                "lontano non si puo fare - lascia al tablet quello che ha.",
            style = MaterialTheme.typography.bodySmall,
            color = Tinte.TestoTenue,
        )
    }
}

// ---------------------------------------------------------------------------

internal fun oraMinuti(minuti: Int): String =
    "%02d:%02d".format(minuti / 60, minuti % 60)

/** Una regola in una riga sola, nelle parole che userebbe un genitore. */
internal fun descriviRegola(regola: JSONObject): String {
    val pezzi = mutableListOf<String>()
    val minuti = regola.optInt("minuti", 0)
    if (minuti > 0) pezzi += "$minuti min al giorno"
    val fascia = regola.optJSONObject("fascia")
    if (fascia != null && fascia.optBoolean("attiva")) {
        val quando = "${oraMinuti(fascia.optInt("inizio"))}-${oraMinuti(fascia.optInt("fine"))}"
        val giorni = fascia.optJSONArray("giorni")
        val quanti = giorni?.length() ?: 0
        // Sette giorni su sette non si dice: e il caso normale, e scriverlo
        // ogni volta allungherebbe tutte le righe per niente.
        pezzi += if (quanti in 1..6) "$quando, $quanti giorni" else quando
    }
    return if (pezzi.isEmpty()) "nessuna regola" else pezzi.joinToString(" · ")
}

internal fun JSONArray?.aListaDiTesti(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { optString(it) }
