package it.iotatec.starkid

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import it.iotatec.starkid.data.Prefs
import it.iotatec.starkid.domain.BlockReason
import it.iotatec.starkid.ui.KidsTheme
import it.iotatec.starkid.ui.LockScreen
import it.iotatec.starkid.ui.PinPad
import it.iotatec.starkid.ui.Skins
import it.iotatec.starkid.ui.UnlockOptions
import it.iotatec.starkid.ui.applySkinChrome

/**
 * Schermata di blocco mostrata dal servizio quando il bambino e dentro un'altra
 * app. Non si chiude col tasto indietro: riporta al launcher.
 */
class LockActivity : ComponentActivity() {

    private lateinit var prefs: Prefs
    private val reason = mutableStateOf(BlockReason.NONE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.get(this)
        reason.value = reasonFrom(intent)

        setContent {
            // Anche il blocco segue il tema scelto: trovarselo di un altro
            // colore suggerirebbe al bambino che non e la stessa app.
            val config by prefs.config.collectAsState()
            val skin = Skins.of(config.skin)
            LaunchedEffect(skin.id) { applySkinChrome(skin) }
            KidsTheme(skin) {
                Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                    LockRoot()
                }
            }
        }
    }

    /** L'Activity e singleInstance: il servizio la richiama con un nuovo motivo. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        reason.value = reasonFrom(intent)
    }

    @Composable
    private fun LockRoot() {
        var stage by remember { mutableStateOf(Stage.LOCKED) }
        var pinError by remember { mutableStateOf<String?>(null) }
        val current by reason

        BackHandler(enabled = true) { goHome() }

        when (stage) {
            Stage.LOCKED -> LockScreen(
                reason = current,
                childName = prefs.current.childName,
                onParentArea = {
                    pinError = null
                    stage = Stage.PIN
                },
            )

            Stage.PIN -> PinPad(
                title = "PIN genitore",
                error = pinError,
                onSubmit = { pin ->
                    if (prefs.checkPin(pin)) {
                        pinError = null
                        stage = Stage.OPTIONS
                    } else {
                        pinError = "PIN errato"
                    }
                },
                onCancel = { stage = Stage.LOCKED },
            )

            Stage.OPTIONS -> UnlockOptions(
                reason = current,
                onGrantBonus = { minutes ->
                    prefs.grantBonus(minutes)
                    goHome()
                },
                onPauseGuard = {
                    prefs.pauseGuard(10)
                    finish()
                },
                onBack = { stage = Stage.LOCKED },
            )
        }
    }

    private fun goHome() {
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(home) }
        finish()
    }

    private fun reasonFrom(intent: Intent?): BlockReason = runCatching {
        BlockReason.valueOf(intent?.getStringExtra(EXTRA_REASON) ?: BlockReason.NONE.name)
    }.getOrDefault(BlockReason.NONE)

    private enum class Stage { LOCKED, PIN, OPTIONS }

    companion object {
        const val EXTRA_REASON = "reason"
    }
}
