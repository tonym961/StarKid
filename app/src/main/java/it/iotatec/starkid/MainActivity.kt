package it.iotatec.starkid

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import it.iotatec.starkid.admin.Kiosk
import it.iotatec.starkid.data.AppEntry
import it.iotatec.starkid.data.BACKGROUND_PHOTO
import it.iotatec.starkid.data.BackgroundStore
import it.iotatec.starkid.data.InstalledApps
import it.iotatec.starkid.data.KidsConfig
import it.iotatec.starkid.data.Prefs
import it.iotatec.starkid.domain.BlockReason
import it.iotatec.starkid.domain.Guard
import it.iotatec.starkid.games.CarGame
import it.iotatec.starkid.games.CatchGame
import it.iotatec.starkid.games.CompareGame
import it.iotatec.starkid.games.CountingGame
import it.iotatec.starkid.games.DrawGame
import it.iotatec.starkid.games.GameDef
import it.iotatec.starkid.games.Games
import it.iotatec.starkid.games.MemoryGame
import it.iotatec.starkid.games.OddOneGame
import it.iotatec.starkid.games.PatternGame
import it.iotatec.starkid.games.PuzzleGame
import it.iotatec.starkid.games.SequenceGame
import it.iotatec.starkid.games.XylophoneGame
import it.iotatec.starkid.games.Sfx
import it.iotatec.starkid.service.GuardService
import it.iotatec.starkid.ui.AppLimitsScreen
import it.iotatec.starkid.ui.AppPickerScreen
import it.iotatec.starkid.ui.CategoriesScreen
import it.iotatec.starkid.ui.GamesScreen
import it.iotatec.starkid.ui.HomeScreen
import it.iotatec.starkid.ui.KidsTheme
import it.iotatec.starkid.ui.KioskScreen
import it.iotatec.starkid.ui.LockScreen
import it.iotatec.starkid.ui.LookScreen
import it.iotatec.starkid.ui.NarrowMaxWidth
import it.iotatec.starkid.ui.ParentActions
import it.iotatec.starkid.ui.ParentHome
import it.iotatec.starkid.ui.PinPad
import it.iotatec.starkid.ui.PinSetup
import it.iotatec.starkid.ui.RecommendedScreen
import it.iotatec.starkid.ui.ScheduleScreen
import it.iotatec.starkid.ui.SetupStatus
import it.iotatec.starkid.ui.Skin
import it.iotatec.starkid.ui.Skins
import it.iotatec.starkid.ui.applySkinChrome
import it.iotatec.starkid.usage.UsageReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

private enum class Screen {
    HOME,
    GAME,
    PIN_GATE,
    PIN_SETUP,
    CHANGE_PIN,
    PARENT,
    APPS,
    CATEGORIES,
    APP_LIMITS,
    SCHEDULE,
    KIOSK,
    LOOK,
    GAMES,
    RECOMMENDED,
}

/** Il launcher: schermata del bambino, blocco e area genitori. */
class MainActivity : ComponentActivity() {

    private lateinit var prefs: Prefs
    private lateinit var usage: UsageReader

    /**
     * Cresce a ogni pressione del tasto Home. L'Activity e `singleTask`,
     * quindi non viene ricreata: senza un segnale, chi preme Home si ritrova
     * la schermata che c'era prima. Se un genitore lascia aperta la sua area,
     * al bambino basterebbe il tasto Home per avere davanti "Esci dal kiosk".
     */
    private var homePresses by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.get(this)
        usage = UsageReader(this)

        setContent {
            val config by prefs.config.collectAsState()
            val skin = Skins.of(config.skin)
            LaunchedEffect(skin.id) { applySkinChrome(skin) }
            KidsTheme(skin) {
                Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                    LauncherRoot(config, skin)
                }
            }
        }
    }


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        homePresses++
    }

    override fun onResume() {
        super.onResume()
        // L'avvio del servizio dal primo piano e quello che Android non rifiuta.
        GuardService.start(this)
        if (prefs.current.kioskEnabled) Kiosk.enter(this)
    }

    @Composable
    private fun LauncherRoot(config: KidsConfig, skin: Skin) {
        var screen by remember { mutableStateOf(Screen.HOME) }
        var apps by remember { mutableStateOf(InstalledApps.cached()) }
        var usageMillis by remember { mutableStateOf(emptyMap<String, Long>()) }
        var status by remember { mutableStateOf(readStatus()) }
        var pinError by remember { mutableStateOf<String?>(null) }
        var photo by remember { mutableStateOf<ImageBitmap?>(null) }
        var openGame by remember { mutableStateOf<GameDef?>(null) }
        val scope = rememberCoroutineScope()

        val games = remember(config.disabledGames) {
            Games.All.filter { it.id !in config.disabledGames }
        }

        // Il sintetizzatore non legge le preferenze: glielo diciamo noi.
        LaunchedEffect(config.soundOn) { Sfx.enabled = config.soundOn }

        // Un minuto di gioco dentro StarKid conta come un minuto di app: il
        // tetto giornaliero non si aggira passando ai giochi di casa.
        LaunchedEffect(screen) {
            if (screen != Screen.GAME) return@LaunchedEffect
            while (true) {
                delay(60_000)
                prefs.addPlayMinute()
            }
        }

        LaunchedEffect(Unit) { apps = InstalledApps.all(this@MainActivity) }

        // Il tasto Home riporta sempre il bambino alla sua schermata.
        LaunchedEffect(homePresses) {
            if (homePresses > 0) screen = Screen.HOME
        }

        // La foto si rilegge quando cambia o quando torna a essere lo sfondo.
        LaunchedEffect(config.background, config.backgroundStamp) {
            photo = if (config.background == BACKGROUND_PHOTO) {
                withContext(Dispatchers.IO) { BackgroundStore.load(this@MainActivity) }
            } else {
                null
            }
        }

        val pickPhoto = rememberLauncherForActivityResult(
            ActivityResultContracts.PickVisualMedia(),
        ) { uri -> if (uri != null) importPhoto(scope, uri) }

        // Conteggi e stato dei permessi si rinfrescano da soli: il genitore
        // concede i permessi in un'altra app e torna qui.
        LaunchedEffect(Unit) {
            while (true) {
                usageMillis = withContext(Dispatchers.IO) { usage.usageTodayMillis() }
                status = readStatus()
                delay(15_000)
            }
        }

        // Le politiche del kiosk si riapplicano anche quando si cambia
        // schermata: le impostazioni di sistema si aprono solo mentre il
        // genitore e nella sua area, e la porta si richiude appena esce.
        LaunchedEffect(config.allowedPackages, config.kioskEnabled, screen) {
            if (config.kioskEnabled) {
                val inParentArea = screen != Screen.HOME &&
                    screen != Screen.GAME &&
                    screen != Screen.PIN_GATE &&
                    screen != Screen.PIN_SETUP
                Kiosk.applyPolicies(
                    context = this@MainActivity,
                    allowedPackages = config.allowedPackages,
                    allowSettings = inParentArea,
                )
            }
        }

        val today = Prefs.todayKey()
        val playedToday = if (config.playDate == today) config.playMinutes else 0
        val usedToday =
            (config.allowedPackages.sumOf { usageMillis[it] ?: 0L } / 60_000L).toInt() + playedToday
        val now = Calendar.getInstance()
        val verdict = Guard.checkGlobal(
            config = config,
            dayOfWeek = now.get(Calendar.DAY_OF_WEEK),
            minuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE),
            usedMinutesToday = usedToday,
            today = today,
        )
        val remaining = Guard.remainingMinutes(config, usedToday, today)
        val allowedApps = apps.filter { it.packageName in config.allowedPackages }

        // Sulla schermata del bambino il tasto indietro non deve fare nulla;
        // nelle altre riporta indietro di un passo.
        BackHandler(enabled = true) {
            screen = when (screen) {
                Screen.HOME -> Screen.HOME
                Screen.APPS, Screen.CATEGORIES, Screen.APP_LIMITS,
                Screen.SCHEDULE, Screen.KIOSK, Screen.LOOK, Screen.GAMES,
                Screen.RECOMMENDED,
                -> Screen.PARENT
                else -> Screen.HOME
            }
        }

        // Casa e i giochi stanno sotto lo stesso controllo: se il tempo
        // finisce mentre il bambino gioca, il blocco prende il posto del
        // gioco senza che il gioco debba saperne niente.
        when (screen) {
            Screen.HOME, Screen.GAME -> when {
                !config.pinSet -> WelcomeScreen(onStart = { screen = Screen.PIN_SETUP })
                verdict != BlockReason.NONE -> LockScreen(
                    reason = verdict,
                    childName = config.childName,
                    onParentArea = {
                        pinError = null
                        screen = Screen.PIN_GATE
                    },
                )
                screen == Screen.GAME && openGame != null -> GameRouter(
                    game = openGame!!,
                    onBack = { screen = Screen.HOME },
                )
                else -> HomeScreen(
                    config = config,
                    apps = allowedApps,
                    games = games,
                    remainingMinutes = remaining,
                    photo = photo,
                    onOpenApp = { launchApp(it) },
                    onOpenGame = {
                        openGame = it
                        screen = Screen.GAME
                    },
                    onParentArea = {
                        pinError = null
                        screen = Screen.PIN_GATE
                    },
                )
            }

            Screen.PIN_GATE -> PinPad(
                title = "PIN genitore",
                error = pinError,
                onSubmit = { pin ->
                    if (prefs.checkPin(pin)) {
                        pinError = null
                        screen = Screen.PARENT
                    } else {
                        pinError = "PIN errato"
                    }
                },
                onCancel = { screen = Screen.HOME },
            )

            Screen.PIN_SETUP -> PinSetup(
                onPinChosen = { pin ->
                    prefs.setPin(pin)
                    GuardService.start(this@MainActivity)
                    screen = Screen.PARENT
                },
            )

            Screen.CHANGE_PIN -> PinSetup(
                onPinChosen = { pin ->
                    prefs.setPin(pin)
                    screen = Screen.PARENT
                },
                onCancel = { screen = Screen.PARENT },
            )

            Screen.PARENT -> ParentHome(
                config = config,
                status = status,
                allowedCount = config.allowedPackages.size,
                actions = parentActions(
                    onNavigate = { screen = it },
                    onRefreshStatus = { status = readStatus() },
                ),
            )

            Screen.APPS -> AppPickerScreen(
                apps = apps,
                allowed = config.allowedPackages,
                onToggle = { prefs.toggleAllowed(it) },
                onBack = { screen = Screen.PARENT },
            )

            Screen.CATEGORIES -> CategoriesScreen(
                apps = allowedApps,
                config = config,
                onSet = { packageName, category -> prefs.setAppCategory(packageName, category) },
                onBack = { screen = Screen.PARENT },
            )

            Screen.LOOK -> LookScreen(
                config = config,
                photo = photo,
                kioskActive = config.kioskEnabled && status.isDeviceOwner,
                onSkin = { chosen ->
                    prefs.setSkin(chosen.id, chosen.backgrounds.first().id)
                },
                onBackground = { prefs.setBackground(it) },
                onPickPhoto = {
                    pickPhoto.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onRemovePhoto = {
                    BackgroundStore.clear(this@MainActivity)
                    prefs.clearPhotoBackground(skin.backgrounds.first().id)
                },
                onAvatar = { prefs.setAvatar(it) },
                onHomeLayout = { prefs.setHomeLayout(it) },
                onBack = { screen = Screen.PARENT },
            )

            Screen.RECOMMENDED -> RecommendedScreen(
                installed = remember(apps) { apps.map { it.packageName }.toSet() },
                onOpenStore = { openStore(it) },
                onBack = { screen = Screen.PARENT },
            )

            Screen.GAMES -> GamesScreen(
                disabled = config.disabledGames,
                soundOn = config.soundOn,
                onToggle = { prefs.toggleGame(it) },
                onSound = { prefs.setSoundOn(it) },
                onBack = { screen = Screen.PARENT },
            )

            Screen.APP_LIMITS -> AppLimitsScreen(
                apps = allowedApps,
                limits = config.perAppLimits,
                onSetLimit = { packageName, minutes -> prefs.setAppLimit(packageName, minutes) },
                onBack = { screen = Screen.PARENT },
            )

            Screen.SCHEDULE -> ScheduleScreen(
                config = config,
                onToggleEnabled = { prefs.setScheduleEnabled(it) },
                onWindowChange = { start, end -> prefs.setWindow(start, end) },
                onToggleDay = { prefs.toggleDay(it) },
                onBack = { screen = Screen.PARENT },
            )

            Screen.KIOSK -> KioskScreen(
                config = config,
                status = status,
                provisioningCommand = Kiosk.provisioningCommand(this@MainActivity),
                onEnable = {
                    prefs.setKioskEnabled(true)
                    Kiosk.applyPolicies(this@MainActivity, prefs.current.allowedPackages)
                    Kiosk.enter(this@MainActivity)
                },
                onDisable = {
                    prefs.setKioskEnabled(false)
                    Kiosk.exit(this@MainActivity)
                    Kiosk.releasePolicies(this@MainActivity)
                },
                onRelinquishOwner = {
                    prefs.setKioskEnabled(false)
                    Kiosk.exit(this@MainActivity)
                    Kiosk.relinquishDeviceOwner(this@MainActivity)
                    status = readStatus()
                },
                onBack = { screen = Screen.PARENT },
            )
        }
    }

    private fun parentActions(
        onNavigate: (Screen) -> Unit,
        onRefreshStatus: () -> Unit,
    ) = ParentActions(
        back = { onNavigate(Screen.HOME) },
        setChildName = { prefs.setChildName(it) },
        setDailyLimit = { prefs.setDailyLimit(it) },
        openApps = { onNavigate(Screen.APPS) },
        openCategories = { onNavigate(Screen.CATEGORIES) },
        openAppLimits = { onNavigate(Screen.APP_LIMITS) },
        openSchedule = { onNavigate(Screen.SCHEDULE) },
        openKiosk = { onNavigate(Screen.KIOSK) },
        openLook = { onNavigate(Screen.LOOK) },
        openGames = { onNavigate(Screen.GAMES) },
        openRecommended = { onNavigate(Screen.RECOMMENDED) },
        openSystemSettings = { openSettings(Intent(Settings.ACTION_SETTINGS)) },
        changePin = { onNavigate(Screen.CHANGE_PIN) },
        pauseGuard = { prefs.pauseGuard(10) },
        openHomeSettings = {
            openSettings(Intent(Settings.ACTION_HOME_SETTINGS))
            onRefreshStatus()
        },
        openUsageSettings = {
            openSettings(UsageReader.settingsIntent())
            onRefreshStatus()
        },
        openOverlaySettings = {
            openSettings(UsageReader.overlaySettingsIntent(this))
            onRefreshStatus()
        },
    )

    private fun readStatus() = SetupStatus(
        isDefaultHome = isDefaultHome(),
        hasUsageAccess = usage.hasPermission(),
        canDrawOverlay = Settings.canDrawOverlays(this),
        isDeviceOwner = Kiosk.canManage(this),
        fullOwner = Kiosk.isDeviceOwner(this),
        kioskFailures = Kiosk.lastFailures,
    )

    private fun isDefaultHome(): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == packageName
    }

    private fun openSettings(intent: Intent) {
        runCatching { startActivity(intent) }
    }

    /**
     * Apre la scheda di una app consigliata. Prima prova il Play Store
     * installato; se non c'e, il sito. Col kiosk acceso non si apre nessuno
     * dei due, ed e giusto cosi: si installa da genitori, non da dentro il
     * launcher del bambino.
     */
    private fun openStore(packageName: String) {
        val store = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { startActivity(store); true }.getOrDefault(false)) return
        val web = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$packageName"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(web) }
    }

    /**
     * La foto scelta viene copiata subito: il permesso di lettura sull'Uri del
     * selettore vale per questa sessione e non sopravvivrebbe a un riavvio.
     */
    private fun importPhoto(scope: CoroutineScope, uri: Uri) {
        scope.launch {
            val imported = withContext(Dispatchers.IO) {
                BackgroundStore.import(this@MainActivity, uri)
            }
            if (imported) {
                prefs.setPhotoBackground()
            } else {
                Toast.makeText(
                    this@MainActivity,
                    "Non ho potuto leggere questa foto",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun launchApp(app: AppEntry) {
        val intent = packageManager.getLaunchIntentForPackage(app.packageName)
        if (intent == null) return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }
    }
}

/** Porta al gioco scelto. Ogni gioco sa solo come tornare indietro. */
@Composable
private fun GameRouter(game: GameDef, onBack: () -> Unit) {
    when (game.id) {
        Games.Car.id -> CarGame(onBack)
        Games.Memory.id -> MemoryGame(onBack)
        Games.Counting.id -> CountingGame(onBack)
        Games.Catch.id -> CatchGame(onBack)
        Games.Sequence.id -> SequenceGame(onBack)
        Games.OddOne.id -> OddOneGame(onBack)
        Games.Draw.id -> DrawGame(onBack)
        Games.Xylophone.id -> XylophoneGame(onBack)
        Games.Puzzle.id -> PuzzleGame(onBack)
        Games.Compare.id -> CompareGame(onBack)
        Games.Pattern.id -> PatternGame(onBack)
        // Un id salvato da una versione futura: si torna a casa invece di
        // restare davanti a una schermata vuota.
        else -> LaunchedEffect(game.id) { onBack() }
    }
}

/** Primo avvio: niente PIN, quindi niente da nascondere. */
@Composable
private fun WelcomeScreen(onStart: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
            )
            Spacer(Modifier.size(16.dp))
            Text(
                text = "Per cominciare serve un PIN: e la porta dell'area genitori, " +
                    "da cui si scelgono le app, i limiti di tempo e gli orari.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(max = NarrowMaxWidth),
            )
            Spacer(Modifier.size(32.dp))
            Button(
                onClick = onStart,
                modifier = Modifier.widthIn(max = NarrowMaxWidth).fillMaxWidth(),
            ) { Text("Imposta il PIN") }
        }
    }
}
