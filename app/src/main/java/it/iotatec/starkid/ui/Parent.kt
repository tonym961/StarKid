package it.iotatec.starkid.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.iotatec.starkid.data.AppCategory
import it.iotatec.starkid.data.AppEntry
import it.iotatec.starkid.data.BACKGROUND_PHOTO
import it.iotatec.starkid.data.CHECKED_ON
import it.iotatec.starkid.data.FasciaApp
import it.iotatec.starkid.data.HOME_ALL
import it.iotatec.starkid.data.HOME_CATEGORIES
import it.iotatec.starkid.data.KidsConfig
import it.iotatec.starkid.data.NotRecommended
import it.iotatec.starkid.data.ORIENT_AUTO
import it.iotatec.starkid.data.ORIENT_LANDSCAPE
import it.iotatec.starkid.data.ORIENT_PORTRAIT
import it.iotatec.starkid.data.Recommendation
import it.iotatec.starkid.data.Recommendations
import it.iotatec.starkid.games.Games

/** Stato dei requisiti di sistema, letto dall'Activity. */
data class SetupStatus(
    val isDefaultHome: Boolean,
    val hasUsageAccess: Boolean,
    val canDrawOverlay: Boolean,
    /** Vero se StarKid puo almeno provare ad accendere il kiosk. */
    val isDeviceOwner: Boolean,
    /** Device owner pieno o solo proprietaria di questo utente. */
    val fullOwner: Boolean = false,
    /** Politiche del kiosk che non sono riuscite, da mostrare al genitore. */
    val kioskFailures: List<String> = emptyList(),
)

/** Tutto quello che l'area genitori puo chiedere all'Activity di fare. */
class ParentActions(
    val back: () -> Unit,
    val setChildName: (String) -> Unit,
    val setDailyLimit: (Int) -> Unit,
    val openApps: () -> Unit,
    val openCategories: () -> Unit,
    val openAppLimits: () -> Unit,
    val openSchedule: () -> Unit,
    val openKiosk: () -> Unit,
    /** Accende il kiosk da qui, senza passare dalla sua schermata. */
    val enableKiosk: () -> Unit,
    val openLook: () -> Unit,
    val openGames: () -> Unit,
    val openRecommended: () -> Unit,
    val openServer: () -> Unit,
    val openSystemSettings: () -> Unit,
    val changePin: () -> Unit,
    val openHomeSettings: () -> Unit,
    val openUsageSettings: () -> Unit,
    val openOverlaySettings: () -> Unit,
)

@Composable
fun ParentHome(
    config: KidsConfig,
    status: SetupStatus,
    allowedCount: Int,
    actions: ParentActions,
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
            ScreenHeader("Area genitori", actions.back)

            // Prima di tutto il resto: la gestione a distanza sta chiedendo
            // il kiosk e qui e spento. Accenderlo da lontano e solo una
            // richiesta - un server rubato non deve poter chiudere un
            // bambino dentro un tablet - ma la richiesta va fatta vedere a
            // qualcuno, se no resta li per sempre e dalla pagina web sembra
            // che il kiosk non funzioni.
            if (config.kioskChiestoDaRemoto && !config.kioskEnabled) {
                SectionCard("La gestione chiede il kiosk") {
                    Text(
                        text = "Dalla pagina web hanno chiesto di chiudere questo tablet su " +
                            "StarKid. Da lontano non si puo accendere da soli: serve qualcuno " +
                            "davanti al tablet, ed e il motivo per cui stai leggendo questo " +
                            "dietro al PIN.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.size(12.dp))
                    Button(onClick = actions.enableKiosk) { Text("Accendi il kiosk adesso") }
                    Spacer(Modifier.size(8.dp))
                    TextButton(onClick = actions.openKiosk) { Text("Prima fammi vedere cos'e") }
                }
            }

            SetupSection(status, actions)

            SectionCard("Bambino") {
                var name by remember { mutableStateOf(config.childName) }
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        actions.setChildName(it)
                    },
                    label = { Text("Nome") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionCard("Aspetto") {
                Text(
                    text = "Tema ${Skins.of(config.skin).label.lowercase()}, " +
                        "sfondo ${backgroundLabel(config)}.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.size(12.dp))
                Button(onClick = actions.openLook) { Text("Tema, sfondo e avatar") }
            }

            SectionCard("App permesse") {
                Text(
                    text = if (allowedCount == 0) "Nessuna app scelta"
                    else "$allowedCount app sulla schermata",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.size(12.dp))
                Button(onClick = actions.openApps) { Text("Scegli le app") }
                Spacer(Modifier.size(10.dp))
                OutlinedButton(onClick = actions.openCategories) { Text("Sistema le categorie") }
            }

            SectionCard("Giochi di StarKid") {
                val on = Games.All.count { it.id !in config.disabledGames }
                Text(
                    text = "$on giochi su ${Games.All.size} accesi. Sono dentro l'app: " +
                        "niente pubblicita, niente acquisti, niente rete.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.size(12.dp))
                Button(onClick = actions.openGames) { Text("Scegli i giochi") }
                Spacer(Modifier.size(10.dp))
                OutlinedButton(onClick = actions.openRecommended) {
                    Text("App consigliate da installare")
                }
            }

            SectionCard("Server di gestione") {
                Text(
                    text = if (config.enrolled) {
                        "Collegato a " + config.serverUrl + ". Da collegato, le regole le " +
                            "decide il server."
                    } else {
                        "Non collegato: tutto quello che c'e qui lo decidi da questo tablet."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.size(12.dp))
                Button(onClick = actions.openServer) {
                    Text(if (config.enrolled) "Vedi il collegamento" else "Collega al server")
                }
            }

            SectionCard("Tempo di utilizzo") {
                Stepper(
                    label = "Limite al giorno",
                    value = if (config.dailyLimitMinutes == 0) "Libero"
                    else formatMinutes(config.dailyLimitMinutes),
                    onDecrease = { actions.setDailyLimit(config.dailyLimitMinutes - 15) },
                    onIncrease = { actions.setDailyLimit(config.dailyLimitMinutes + 15) },
                )
                Text(
                    text = "A zero il tempo non viene limitato.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(12.dp))
                OutlinedButton(onClick = actions.openAppLimits) { Text("Limiti per singola app") }
            }

            SectionCard("Fasce orarie") {
                Text(
                    text = if (!config.scheduleEnabled) "Nessuna fascia: si puo usare sempre"
                    else "Dalle ${formatTimeOfDay(config.startMinuteOfDay)} alle " +
                        formatTimeOfDay(config.endMinuteOfDay),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.size(12.dp))
                Button(onClick = actions.openSchedule) { Text("Imposta gli orari") }
            }

            SectionCard("Modalita kiosk") {
                Text(
                    text = when {
                        config.kioskEnabled && status.isDeviceOwner -> "Attiva: il tablet e bloccato sul launcher."
                        status.isDeviceOwner -> "Disponibile, ma non attiva."
                        else -> "Non disponibile: l'app non e Device Owner."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.size(12.dp))
                Button(onClick = actions.openKiosk) { Text("Gestisci kiosk") }
            }

            SectionCard("Sicurezza") {
                OutlinedButton(onClick = actions.changePin, modifier = Modifier.fillMaxWidth()) {
                    Text("Cambia PIN")
                }
                Spacer(Modifier.size(10.dp))
                OutlinedButton(
                    onClick = actions.openSystemSettings,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Impostazioni di sistema")
                }
                Spacer(Modifier.size(12.dp))

                // Non c'e piu nessun bottone "sospendi il controllo", e non
                // serve: da qui dentro non ti blocca niente, sempre. Prima
                // quel bottone c'era, non cambiava niente sullo schermo e
                // bisognava premerlo per poter aprire le impostazioni -
                // cioe: un interruttore che non diceva in che posizione
                // stava, per un problema che non doveva esistere.
                Text(
                    text = "Qui dentro comandi tu. Il PIN e il cancello: oltre quello il " +
                        "controllo e fermo, e niente ti chiude in faccia quello che apri.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    text = "Aprendo le impostazioni di Android la porta resta aperta ancora " +
                        "dieci minuti dopo che esci da qui, e col kiosk acceso StarKid si " +
                        "toglie di mezzo per davvero - se no Android stesso non ti lascia " +
                        "arrivare alle opzioni sviluppatore.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    text = "Si richiude quando torni alla schermata del bambino: e quello il " +
                        "gesto che dice \"ho finito\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.size(8.dp))
            Button(
                onClick = actions.back,
                modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(),
            ) { Text("Torna alla schermata del bambino") }
            Spacer(Modifier.size(24.dp))
        }
    }
}

@Composable
private fun SetupSection(status: SetupStatus, actions: ParentActions) {
    val missing = !status.isDefaultHome || !status.hasUsageAccess || !status.canDrawOverlay
    if (!missing) return

    SectionCard("Da sistemare") {
        RequirementRow(
            title = "Launcher predefinito",
            description = "Con il tasto Home si torna qui.",
            satisfied = status.isDefaultHome,
            actionLabel = "Imposta",
            onAction = actions.openHomeSettings,
        )
        HorizontalDivider()
        RequirementRow(
            title = "Accesso ai dati di utilizzo",
            description = "Senza questo i limiti di tempo non funzionano.",
            satisfied = status.hasUsageAccess,
            actionLabel = "Concedi",
            onAction = actions.openUsageSettings,
        )
        HorizontalDivider()
        RequirementRow(
            title = "Mostra sopra le altre app",
            description = "Serve a far comparire il blocco dentro le altre app.",
            satisfied = status.canDrawOverlay,
            actionLabel = "Concedi",
            onAction = actions.openOverlaySettings,
        )
    }
}

@Composable
fun AppPickerScreen(
    apps: List<AppEntry>,
    allowed: Set<String>,
    onToggle: (String) -> Unit,
    onBack: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ScreenHeader("Scegli le app", onBack)
            LazyColumn(Modifier.widthIn(max = ContentMaxWidth).fillMaxSize()) {
                items(apps, key = { it.packageName }) { app ->
                    Row(
                        // Tutta la riga accende e spegne l'app: su un tablet
                        // centrare la sola casella in fondo a destra e un
                        // bersaglio inutilmente piccolo.
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(app.packageName) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.foundation.Image(
                            bitmap = app.icon,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp),
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Checkbox(
                            checked = app.packageName in allowed,
                            onCheckedChange = { onToggle(app.packageName) },
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun AppLimitsScreen(
    apps: List<AppEntry>,
    limits: Map<String, Int>,
    windows: Map<String, FasciaApp>,
    onSetLimit: (String, Int) -> Unit,
    onSetWindow: (String, FasciaApp?) -> Unit,
    onBack: () -> Unit,
) {
    // Come in ScheduleScreen: Calendar mette la domenica a 1, qui la
    // settimana si mostra all'italiana.
    val week = listOf(2 to "L", 3 to "M", 4 to "M", 5 to "G", 6 to "V", 7 to "S", 1 to "D")
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ScreenHeader("Limiti per app", onBack)
            if (apps.isEmpty()) {
                Text(
                    text = "Prima scegli quali app puo usare il bambino.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            LazyColumn(Modifier.widthIn(max = ContentMaxWidth).fillMaxSize()) {
                items(apps, key = { it.packageName }) { app ->
                    val minutes = limits[app.packageName] ?: 0
                    Column(Modifier.padding(vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.foundation.Image(
                                bitmap = app.icon,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(app.label, style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.size(6.dp))
                        Stepper(
                            label = "Massimo al giorno",
                            value = if (minutes == 0) "Libero" else formatMinutes(minutes),
                            onDecrease = { onSetLimit(app.packageName, minutes - 10) },
                            onIncrease = { onSetLimit(app.packageName, minutes + 10) },
                        )

                        // Quanto e quando sono due domande diverse: "mezz'ora
                        // al giorno" e "solo dopo i compiti" si vogliono
                        // spesso una senza l'altra.
                        Spacer(Modifier.size(6.dp))
                        val fascia = windows[app.packageName]
                        SwitchRow(
                            label = "Solo in certi orari",
                            checked = fascia != null,
                            onCheckedChange = { acceso ->
                                onSetWindow(
                                    app.packageName,
                                    // Il valore iniziale e un pomeriggio: e
                                    // l'ora che un genitore sceglie quasi
                                    // sempre, e cosi l'interruttore da subito
                                    // una fascia sensata invece di 00:00-00:00,
                                    // che vorrebbe dire "sempre" e sembrerebbe
                                    // rotto.
                                    if (acceso) FasciaApp(16 * 60, 18 * 60) else null,
                                )
                            },
                        )
                        if (fascia != null) {
                            Spacer(Modifier.size(8.dp))
                            Stepper(
                                label = "Da",
                                value = formatTimeOfDay(fascia.inizio),
                                onDecrease = {
                                    onSetWindow(app.packageName, fascia.copy(inizio = (fascia.inizio - 15).mod(24 * 60)))
                                },
                                onIncrease = {
                                    onSetWindow(app.packageName, fascia.copy(inizio = (fascia.inizio + 15).mod(24 * 60)))
                                },
                            )
                            Spacer(Modifier.size(8.dp))
                            Stepper(
                                label = "A",
                                value = formatTimeOfDay(fascia.fine),
                                onDecrease = {
                                    onSetWindow(app.packageName, fascia.copy(fine = (fascia.fine - 15).mod(24 * 60)))
                                },
                                onIncrease = {
                                    onSetWindow(app.packageName, fascia.copy(fine = (fascia.fine + 15).mod(24 * 60)))
                                },
                            )
                            Spacer(Modifier.size(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                for ((giorno, sigla) in week) {
                                    DayChip(
                                        label = sigla,
                                        selected = giorno in fascia.giorni,
                                        onClick = {
                                            val prossimi = fascia.giorni.toMutableSet()
                                            if (!prossimi.remove(giorno)) prossimi.add(giorno)
                                            // Zero giorni vorrebbe dire "mai",
                                            // e non e quello che si chiede
                                            // togliendo l'ultimo: si spegne
                                            // la fascia, che e la stessa cosa
                                            // detta bene.
                                            onSetWindow(
                                                app.packageName,
                                                if (prossimi.isEmpty()) null
                                                else fascia.copy(giorni = prossimi),
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun ScheduleScreen(
    config: KidsConfig,
    onToggleEnabled: (Boolean) -> Unit,
    onWindowChange: (Int, Int) -> Unit,
    onToggleDay: (Int) -> Unit,
    onBack: () -> Unit,
) {
    // Calendar.DAY_OF_WEEK mette la domenica a 1; qui si mostra la settimana
    // all'italiana, da lunedi.
    val week = listOf(2 to "L", 3 to "M", 4 to "M", 5 to "G", 6 to "V", 7 to "S", 1 to "D")

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ScreenHeader("Fasce orarie", onBack)

            SectionCard("Attivazione") {
                SwitchRow(
                    label = "Limita le ore di utilizzo",
                    checked = config.scheduleEnabled,
                    onCheckedChange = onToggleEnabled,
                )
            }

            SectionCard("Orario consentito") {
                Stepper(
                    label = "Inizio",
                    value = formatTimeOfDay(config.startMinuteOfDay),
                    onDecrease = { onWindowChange(config.startMinuteOfDay - 15, config.endMinuteOfDay) },
                    onIncrease = { onWindowChange(config.startMinuteOfDay + 15, config.endMinuteOfDay) },
                )
                Spacer(Modifier.size(10.dp))
                Stepper(
                    label = "Fine",
                    value = formatTimeOfDay(config.endMinuteOfDay),
                    onDecrease = { onWindowChange(config.startMinuteOfDay, config.endMinuteOfDay - 15) },
                    onIncrease = { onWindowChange(config.startMinuteOfDay, config.endMinuteOfDay + 15) },
                )
                Text(
                    text = "Una fascia che scavalca la mezzanotte, per esempio 22:00 - 07:00, e ammessa.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard("Giorni") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((day, label) in week) {
                        DayChip(
                            label = label,
                            selected = day in config.activeDays,
                            onClick = { onToggleDay(day) },
                        )
                    }
                }
            }
            Spacer(Modifier.size(24.dp))
        }
    }
}

@Composable
fun KioskScreen(
    config: KidsConfig,
    status: SetupStatus,
    provisioningCommand: String,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onLeaveToLauncher: () -> Unit,
    onRecents: (Boolean) -> Unit,
    onRelinquishOwner: () -> Unit,
    onBack: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ScreenHeader("Modalita kiosk", onBack)

            if (status.isDeviceOwner) {
                SectionCard("Stato") {
                    Text(
                        text = when {
                            config.kioskEnabled && status.fullOwner ->
                                "Kiosk attivo. Il bambino resta dentro il launcher e le app " +
                                    "permesse: barra di stato e impostazioni sono chiuse, " +
                                    "l'app non si disinstalla."
                            config.kioskEnabled ->
                                "Kiosk attivo su questo utente. StarKid non e proprietaria " +
                                    "dell'intero tablet, quindi qualche blocco potrebbe non " +
                                    "aver fatto presa: controlla l'elenco qui sotto."
                            status.fullOwner -> "L'app e Device Owner: il kiosk si puo accendere."
                            else -> "StarKid e proprietaria di questo utente, non di tutto il " +
                                "tablet. Il kiosk si puo provare, ma con meno poteri."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    // Acceso non vuol dire attivo adesso, e qui dentro non lo
                    // e mai: l'area genitori promette che niente ti blocca, e
                    // la mantiene uscendo dal lock task finche ci stai. Senza
                    // questa riga chi accendeva l'interruttore vedeva la barra
                    // di sistema restare dov'era e pensava che il kiosk fosse
                    // rotto - e non lo era, stava solo aspettando che uscisse.
                    if (config.kioskEnabled) {
                        Spacer(Modifier.size(12.dp))
                        Text(
                            text = "Adesso non e in funzione, e qui dentro non lo sara mai: " +
                                "nell'area genitori StarKid si toglie di mezzo apposta. " +
                                "Si chiude sul serio quando torni alla schermata del bambino.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (status.kioskFailures.isNotEmpty()) {
                        Spacer(Modifier.size(12.dp))
                        Text(
                            text = "Non ha funzionato:",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        for (failure in status.kioskFailures) {
                            Text(
                                text = "· $failure",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    Spacer(Modifier.size(14.dp))
                    SwitchRow(
                        label = "Lascia le app recenti",
                        checked = config.kioskRecents,
                        onCheckedChange = onRecents,
                    )
                    Text(
                        text = "Spente, il bambino non puo passare da una app all'altra se " +
                            "non dalla schermata di StarKid.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(14.dp))
                    if (config.kioskEnabled) {
                        Button(onClick = onDisable, modifier = Modifier.fillMaxWidth()) {
                            Text("Esci dal kiosk")
                        }
                        Spacer(Modifier.size(10.dp))
                        OutlinedButton(
                            onClick = onLeaveToLauncher,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Esci e usa il launcher di sistema")
                        }
                        Text(
                            text = "Spegne il kiosk, libera la home e apre le impostazioni " +
                                "per rimettere il launcher di prima. StarKid resta " +
                                "installata e il PIN non cambia.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Button(onClick = onEnable, modifier = Modifier.fillMaxWidth()) {
                            Text("Attiva il kiosk")
                        }
                    }
                }

                SectionCard("Smontare tutto") {
                    Text(
                        text = "Restituisce il ruolo di Device Owner al sistema e rimuove ogni " +
                            "restrizione. Da fare prima di dare il tablet a qualcun altro.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(12.dp))
                    TextButton(onClick = onRelinquishOwner) { Text("Rinuncia a Device Owner") }
                }
            } else {
                SectionCard("Serve il Device Owner") {
                    Text(
                        text = "Il kiosk vero richiede che questa app sia Device Owner del tablet. " +
                            "Si imposta una volta sola, via adb, su un tablet senza account " +
                            "configurati: appena uscito di fabbrica o appena resettato.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.size(14.dp))
                    Text("Passaggi:", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = "1. Reset del tablet, poi salta l'accesso a Google: nessun account.\n" +
                            "2. Attiva Opzioni sviluppatore e Debug USB.\n" +
                            "3. Installa questo launcher.\n" +
                            "4. Collega il tablet al computer e lancia il comando qui sotto.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.size(14.dp))
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Text(
                            text = provisioningCommand,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }

                SectionCard("Perche non c'e un'alternativa") {
                    Text(
                        text = "Senza Device Owner Android offre solo il blocco su schermo, che " +
                            "confina l'utente nella singola app bloccata: il bambino non " +
                            "potrebbe aprire nemmeno le app permesse. Per questo il kiosk non " +
                            "viene proposto in quel caso. Il launcher come home predefinita, " +
                            "con limiti di tempo e blocco delle app non consentite, funziona " +
                            "comunque.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.size(24.dp))
        }
    }
}

/**
 * App consigliate al genitore, con quello che serve per decidere: pubblicita,
 * acquisti, lingua, e la data in cui e stato controllato.
 */
@Composable
fun RecommendedScreen(
    installed: Set<String>,
    onOpenStore: (String) -> Unit,
    onBack: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ScreenHeader("App consigliate", onBack)

            SectionCard("Come sono scelte") {
                Text(
                    text = "Il criterio non e il prezzo: quasi tutto il gratis sul Play Store " +
                        "e pagato dalla pubblicita. Qui ci sono solo app senza pubblicita e " +
                        "senza acquisti, che funzionano anche senza rete.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    text = "Controllate sulle schede del Play Store il $CHECKED_ON. " +
                        "Le condizioni cambiano senza preavviso: prima di installarne una, " +
                        "dai un'occhiata alle etichette sulla sua scheda.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            for (group in Recommendations) {
                SectionCard(group.title) {
                    Text(
                        text = group.note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(12.dp))
                    for (app in group.apps) {
                        RecommendationRow(
                            app = app,
                            installed = app.packageName in installed,
                            onOpenStore = { onOpenStore(app.packageName) },
                        )
                        HorizontalDivider()
                    }
                }
            }

            SectionCard("Da evitare") {
                Text(
                    text = "Compaiono spesso negli elenchi di giochi gratuiti per bambini, " +
                        "ma gratuite non sono:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(10.dp))
                for (warning in NotRecommended) {
                    Text(
                        text = "${warning.name} — ${warning.reason}",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
            Spacer(Modifier.size(24.dp))
        }
    }
}

@Composable
private fun RecommendationRow(
    app: Recommendation,
    installed: Boolean,
    onOpenStore: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(app.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = app.publisher,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            if (installed) {
                Text(
                    text = "gia installata",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                )
            } else {
                OutlinedButton(onClick = onOpenStore) { Text("Vedi") }
            }
        }
        Spacer(Modifier.size(6.dp))
        Text(app.blurb, style = MaterialTheme.typography.bodyMedium)
        if (!app.italian) {
            Text(
                text = "Solo in inglese.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (app.caveat != null) {
            Spacer(Modifier.size(4.dp))
            Text(
                text = app.caveat,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Quali giochi di StarKid compaiono sulla schermata del bambino. */
@Composable
fun GamesScreen(
    disabled: Set<String>,
    soundOn: Boolean,
    onToggle: (String) -> Unit,
    onSound: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ScreenHeader("Giochi di StarKid", onBack)

            SectionCard("Suoni") {
                SwitchRow(
                    label = "Suoni dei giochi",
                    checked = soundOn,
                    onCheckedChange = onSound,
                )
                Text(
                    text = "Note brevi per dire giusto o sbagliato. Seguono il volume " +
                        "della musica del tablet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard("Quali far vedere") {
                Text(
                    text = "I giochi spenti spariscono dalla griglia. Il tempo passato a " +
                        "giocare conta nel limite giornaliero come quello delle altre app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(12.dp))
                for (game in Games.All) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(game.emoji, fontSize = 34.sp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(game.label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = game.blurb,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        androidx.compose.material3.Switch(
                            checked = game.id !in disabled,
                            onCheckedChange = { onToggle(game.id) },
                        )
                    }
                    HorizontalDivider()
                }
            }
            Spacer(Modifier.size(24.dp))
        }
    }
}

private fun backgroundLabel(config: KidsConfig): String =
    if (config.background == BACKGROUND_PHOTO) "una foto"
    else resolveBackground(Skins.of(config.skin), config.background).label.lowercase()

/** Tema, sfondo e avatar: tutto quello che si vede e non cambia le regole. */
@Composable
fun LookScreen(
    config: KidsConfig,
    photo: ImageBitmap?,
    kioskActive: Boolean,
    onSkin: (Skin) -> Unit,
    onBackground: (String) -> Unit,
    onPickPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onAvatar: (String) -> Unit,
    onHomeLayout: (String) -> Unit,
    onOrientation: (String) -> Unit,
    onBack: () -> Unit,
) {
    val skin = Skins.of(config.skin)

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ScreenHeader("Aspetto", onBack)

            SectionCard("Anteprima") {
                // Contenuta: a tutta larghezza l'anteprima da sola riempirebbe
                // lo schermo e spingerebbe tema e sfondo sotto il bordo.
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    BackgroundPreview(
                        skin = skin,
                        backgroundId = config.background,
                        photo = photo,
                        modifier = Modifier.widthIn(max = 380.dp).fillMaxWidth(),
                    )
                }
            }

            SectionCard("Schermata principale") {
                Text(
                    text = "Come si apre la schermata del bambino.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LayoutCard(
                        title = "A scomparti",
                        blurb = "Prima le categorie, poi si entra. Meno cose insieme " +
                            "davanti agli occhi.",
                        selected = config.homeLayout == HOME_CATEGORIES,
                        onClick = { onHomeLayout(HOME_CATEGORIES) },
                        modifier = Modifier.weight(1f),
                    )
                    LayoutCard(
                        title = "Tutto in vista",
                        blurb = "Tutte le app e i giochi sulle pagine, con la barra " +
                            "delle categorie in alto.",
                        selected = config.homeLayout == HOME_ALL,
                        onClick = { onHomeLayout(HOME_ALL) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            SectionCard("Come sta lo schermo") {
                Text(
                    text = "Se gira da solo mentre il bambino gioca, le cose si spostano " +
                        "sotto le dita. Di base resta in orizzontale.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for ((value, label) in listOf(
                        ORIENT_LANDSCAPE to "Orizzontale",
                        ORIENT_PORTRAIT to "Verticale",
                        ORIENT_AUTO to "Come lo gira",
                    )) {
                        Card(
                            onClick = { onOrientation(value) },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (config.orientation == value) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                            ),
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (config.orientation == value) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
            }

            SectionCard("Tema") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (candidate in Skins.All) {
                        ThemeCard(
                            skin = candidate,
                            selected = candidate.id == skin.id,
                            onClick = { onSkin(candidate) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            SectionCard("Sfondo") {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    for (preset in skin.backgrounds) {
                        BackgroundSwatch(
                            preset = preset,
                            selected = preset.id == config.background,
                            onClick = { onBackground(preset.id) },
                        )
                    }
                }
                Spacer(Modifier.size(16.dp))
                Button(onClick = onPickPhoto) { Text("Metti una foto") }
                if (config.backgroundStamp != 0L) {
                    Spacer(Modifier.size(10.dp))
                    if (config.background != BACKGROUND_PHOTO) {
                        OutlinedButton(onClick = { onBackground(BACKGROUND_PHOTO) }) {
                            Text("Usa la foto salvata")
                        }
                    } else {
                        OutlinedButton(onClick = onRemovePhoto) { Text("Togli la foto") }
                    }
                }
                Spacer(Modifier.size(10.dp))
                Text(
                    text = "La foto viene copiata e ridotta dentro l'app: resta lo sfondo anche " +
                        "se la cancelli dalla galleria.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (kioskActive) {
                    Spacer(Modifier.size(10.dp))
                    Text(
                        text = "Col kiosk attivo il selettore di sistema non si apre: spegni il " +
                            "kiosk per scegliere la foto, poi riaccendilo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            SectionCard("Avatar") {
                Text(
                    text = "Compare accanto al nome, in alto a sinistra.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(12.dp))
                for (row in Avatars.chunked(6)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (emoji in row) {
                            AvatarChoice(
                                emoji = emoji,
                                selected = emoji == config.avatar,
                                onClick = { onAvatar(emoji) },
                            )
                        }
                    }
                    Spacer(Modifier.size(10.dp))
                }
            }
            Spacer(Modifier.size(24.dp))
        }
    }
}

@Composable
private fun LayoutCard(
    title: String,
    blurb: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(4.dp))
            Text(
                text = blurb,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ThemeCard(
    skin: Skin,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(Modifier.padding(12.dp)) {
            // Un pezzo del tema vero, non un quadratino di colore: si vede lo
            // sfondo con una tessera sopra, come sulla schermata del bambino.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.verticalGradient(skin.backgrounds.first().colors)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 64.dp, height = 44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(skin.tileColors.first()),
                )
            }
            Spacer(Modifier.size(10.dp))
            Text(skin.label, style = MaterialTheme.typography.titleMedium)
            Text(
                text = skin.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AvatarChoice(emoji: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(60.dp)
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = emoji, fontSize = 30.sp)
    }
}

/**
 * Dove finisce ogni app sulla barra del bambino. La prima volta la categoria e
 * quella dichiarata dall'app stessa: quasi tutti i giochi si riconoscono da
 * soli, il resto lo sistema il genitore.
 */
@Composable
fun CategoriesScreen(
    apps: List<AppEntry>,
    config: KidsConfig,
    onSet: (String, AppCategory) -> Unit,
    onBack: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ScreenHeader("Categorie", onBack)
            if (apps.isEmpty()) {
                Text(
                    text = "Prima scegli quali app puo usare il bambino.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            LazyColumn(Modifier.widthIn(max = ContentMaxWidth).fillMaxSize()) {
                items(apps, key = { it.packageName }) { app ->
                    val current = AppCategory.of(config, app)
                    Column(Modifier.padding(vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.foundation.Image(
                                bitmap = app.icon,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(app.label, style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.size(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (category in AppCategory.entries) {
                                CategoryPick(
                                    category = category,
                                    selected = category == current,
                                    onClick = { onSet(app.packageName, category) },
                                )
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun CategoryPick(
    category: AppCategory,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) colors.primary else colors.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).width(62.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            androidx.compose.material3.Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = if (selected) colors.onPrimary else colors.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.size(4.dp))
            Text(
                text = category.label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) colors.onPrimary else colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
