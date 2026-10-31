package it.iotatec.starkid.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Testi piu grandi e piu grassi del normale: le etichette devono restare
// leggibili per un bambino che legge a fatica.
private val KidsTypography = Typography(
    headlineLarge = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.ExtraBold),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp),
    bodyMedium = TextStyle(fontSize = 15.sp),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
)

/**
 * I colori arrivano dal tema scelto dal genitore. Le schermate leggono sempre
 * `MaterialTheme.colorScheme`, quindi cambiano aspetto senza che nessuna di
 * loro sappia quali temi esistono; chi ha bisogno di piu di un colore (le
 * tessere, le stelle) legge [LocalSkin].
 */
@Composable
fun KidsTheme(skin: Skin = Skins.Clean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSkin provides skin) {
        MaterialTheme(
            colorScheme = skin.colors,
            typography = KidsTypography,
            content = content,
        )
    }
}
