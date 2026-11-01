package it.iotatec.starkid.genitore.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * I colori sono gli stessi token di server/web/app.css, non valori nuovi:
 * questa app e la stessa cosa della web UI vista dal telefono, e due
 * tavolozze imparentate alla lontana sarebbero peggio di una sola.
 *
 * I numeri di contrasto in fondo ai commenti arrivano da li e valgono qui:
 * sono rapporti, non dipendono dalla piattaforma.
 */
object Tinte {
    // Notte (tema scuro, lo stesso di Spaziale)
    val Notte = Color(0xFF070B1E)
    val Carta = Color(0xFF212C5A)          // 1,46:1 sulla notte
    val CartaAlt = Color(0xFF2A3566)
    val Bordo = Color(0xFF45507F)
    val BordoForte = Color(0xFF717FC0)
    val Testo = Color(0xFFEAEEFF)          // 11,56:1 su carta
    val TestoTenue = Color(0xFFB6C1E8)     // 7,51:1 su carta
    val Accento = Color(0xFF8FB8FF)        // 6,66:1 su carta
    val AccentoTesto = Color(0xFF0A1330)   // 9,11:1 sull accento
    val Campo = Color(0xFF141C42)
    val Oro = Color(0xFFFFC846)

    // Gli stati: sempre tre, e mai solo colore - accanto c e sempre una
    // parola, come nelle etichette del sito.
    val Ok = Color(0xFF6FDC9B)             // 8,59:1
    val OkVelo = Color(0xFF10301F)
    val Attesa = Color(0xFFF2C75C)         // 9,08:1
    val AttesaVelo = Color(0xFF352A0F)
    val Guasto = Color(0xFFFF8A80)         // 6,38:1
    val GuastoVelo = Color(0xFF3A1718)
}

private val SchemaScuro = darkColorScheme(
    primary = Tinte.Accento,
    onPrimary = Tinte.AccentoTesto,
    secondary = Tinte.Oro,
    onSecondary = Tinte.Notte,
    background = Tinte.Notte,
    onBackground = Tinte.Testo,
    surface = Tinte.Carta,
    onSurface = Tinte.Testo,
    surfaceVariant = Tinte.CartaAlt,
    onSurfaceVariant = Tinte.TestoTenue,
    outline = Tinte.Bordo,
    outlineVariant = Tinte.Bordo,
    error = Tinte.Guasto,
    onError = Tinte.Notte,
    errorContainer = Tinte.GuastoVelo,
    onErrorContainer = Tinte.Guasto,
)

// La app e sempre scura: il fondo E il cielo, come la pagina di accesso del
// sito. Non c e un tema chiaro da scegliere, quindi non si finge che ci sia.
private val SchemaChiaro = SchemaScuro

private val Caratteri = Typography(
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 23.sp),
    // 13sp e il minimo: sotto, su un telefono, un genitore con quarant anni
    // deve avvicinare il braccio.
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun TemaGenitori(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) SchemaScuro else SchemaChiaro,
        typography = Caratteri,
        content = content,
    )
}
