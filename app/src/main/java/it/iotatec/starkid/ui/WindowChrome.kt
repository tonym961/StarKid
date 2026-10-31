package it.iotatec.starkid.ui

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat

/**
 * Accorda la finestra al tema scelto. Serve in tutte le Activity a tutto
 * schermo: il launcher e il blocco.
 */
fun Activity.applySkinChrome(skin: Skin) {
    // La finestra viene dipinta prima che Compose disegni: senza questo,
    // col tema spaziale si vedrebbe un lampo lilla a ogni avvio.
    window.setBackgroundDrawable(ColorDrawable(skin.windowColor.toArgb()))

    // Il contenuto arriva sotto le barre di sistema, cosi lo sfondo ci passa
    // sotto; a tenere i comandi lontani dalle barre ci pensa
    // safeDrawingPadding. Senza, in fondo allo schermo resta una fascia
    // grigia che sul cielo stellato si vede come un errore.
    WindowCompat.setDecorFitsSystemWindows(window, false)

    // Su uno sfondo scuro le icone scure della barra di stato sparirebbero.
    WindowCompat.getInsetsController(window, window.decorView).apply {
        isAppearanceLightStatusBars = !skin.dark
        isAppearanceLightNavigationBars = !skin.dark
    }

    // Da Android 10 il sistema stende comunque un velo dietro una barra
    // trasparente, se non gli si dice di lasciar stare.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isNavigationBarContrastEnforced = false
        window.isStatusBarContrastEnforced = false
    }
}
