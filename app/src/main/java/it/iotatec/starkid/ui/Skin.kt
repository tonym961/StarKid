package it.iotatec.starkid.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import it.iotatec.starkid.data.SKIN_CLEAN
import it.iotatec.starkid.data.SKIN_SPACE

/** Una tinta di sfondo offerta dal tema, come sfumatura fra due o tre colori. */
data class BackgroundPreset(
    val id: String,
    val label: String,
    val colors: List<Color>,
)

/**
 * L'aspetto del launcher. Il tema cambia i colori e le decorazioni, mai la
 * disposizione: il bambino che impara dove sono le cose non deve ritrovarle
 * altrove solo perche il genitore ha cambiato tema.
 */
data class Skin(
    val id: String,
    val label: String,
    val description: String,
    val dark: Boolean,
    val colors: ColorScheme,
    /** Tinte delle tessere, a rotazione sulle app. */
    val tileColors: List<Color>,
    val tileTextColor: Color,
    val backgrounds: List<BackgroundPreset>,
    /** Stelle dietro la griglia. */
    val starfield: Boolean,
    /** Decorazioni sparse sullo sfondo; vuoto nel tema pulito. */
    val decorations: List<String>,
    /**
     * Velo dietro la barra in alto e dietro le scritte libere: su una foto
     * scelta dal genitore nessun colore di testo sarebbe leggibile da solo.
     */
    val scrim: Color,
    /** Velo sopra la foto del genitore, perche le tessere non ci si perdano. */
    val photoScrim: Color,
    /** Tinta della finestra prima che Compose disegni, per non avere un lampo. */
    val windowColor: Color,
)

private val Grape = Color(0xFF6C4AB6)
private val Lilac = Color(0xFFF6ECFF)
private val Ink = Color(0xFF2B1B46)

private val CleanColors = lightColorScheme(
    primary = Grape,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8DCFF),
    onPrimaryContainer = Ink,
    secondary = Color(0xFF00897B),
    onSecondary = Color.White,
    background = Lilac,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEFE6FA),
    onSurfaceVariant = Color(0xFF574668),
    error = Color(0xFFD32F2F),
    onError = Color.White,
)

private val SpaceInk = Color(0xFF070B1E)
private val SpaceColors = darkColorScheme(
    primary = Color(0xFF8FB8FF),
    onPrimary = Color(0xFF0A1330),
    primaryContainer = Color(0xFF263768),
    onPrimaryContainer = Color(0xFFE2EAFF),
    secondary = Color(0xFFFFC86B),
    onSecondary = Color(0xFF2E1C00),
    background = SpaceInk,
    onBackground = Color(0xFFEAEEFF),
    surface = Color(0xFF161E42),
    onSurface = Color(0xFFEAEEFF),
    surfaceVariant = Color(0xFF28325C),
    onSurfaceVariant = Color(0xFFB6C1E8),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF3B0A06),
)

/** I due temi, quelli che il genitore vede nell'area Aspetto. */
object Skins {

    val Clean = Skin(
        id = SKIN_CLEAN,
        label = "Pulito",
        description = "Tinte chiare e nessuna decorazione: si vedono solo le app.",
        dark = false,
        colors = CleanColors,
        // Pastelli che si alternano: la griglia resta allegra senza dover
        // scegliere un colore per ogni app.
        tileColors = listOf(
            Color(0xFFFFD6E8),
            Color(0xFFD6ECFF),
            Color(0xFFFFF0C9),
            Color(0xFFD8F5DC),
            Color(0xFFE8DCFF),
            Color(0xFFFFE0CC),
        ),
        tileTextColor = Ink,
        backgrounds = listOf(
            BackgroundPreset("lilla", "Lilla", listOf(Color(0xFFF8F0FF), Color(0xFFE4D4FF))),
            BackgroundPreset("cielo", "Cielo", listOf(Color(0xFFE6F4FF), Color(0xFFBFDCFF))),
            BackgroundPreset("prato", "Prato", listOf(Color(0xFFEDF9E4), Color(0xFFC4E9C9))),
            BackgroundPreset("tramonto", "Tramonto", listOf(Color(0xFFFFF2E0), Color(0xFFFFD2E0))),
        ),
        starfield = false,
        decorations = emptyList(),
        scrim = Color.White.copy(alpha = 0.82f),
        photoScrim = Color.Black.copy(alpha = 0.12f),
        windowColor = Lilac,
    )

    val Space = Skin(
        id = SKIN_SPACE,
        label = "Spaziale",
        description = "Cielo stellato, razzi e pianeti: il tablet diventa un'astronave.",
        dark = true,
        colors = SpaceColors,
        // Tinte profonde ma distinte: su un cielo nero le tessere devono
        // staccarsi senza diventare lampadine.
        tileColors = listOf(
            Color(0xFF2F3C7E),
            Color(0xFF1F4F6B),
            Color(0xFF4B2E78),
            Color(0xFF6B3A2E),
            Color(0xFF1F5648),
            Color(0xFF5C2B50),
        ),
        tileTextColor = Color(0xFFF1F4FF),
        backgrounds = listOf(
            BackgroundPreset("notte", "Notte", listOf(Color(0xFF070B1E), Color(0xFF1B2555))),
            BackgroundPreset(
                "nebulosa",
                "Nebulosa",
                listOf(Color(0xFF1B0B33), Color(0xFF2B1559), Color(0xFF071029)),
            ),
            BackgroundPreset("marte", "Marte", listOf(Color(0xFF2A1108), Color(0xFF5E2A1C))),
            BackgroundPreset("orbita", "Orbita", listOf(Color(0xFF041C2B), Color(0xFF0A2A4A))),
        ),
        starfield = true,
        decorations = listOf("🪐", "🚀"),
        scrim = Color(0xFF070B1E).copy(alpha = 0.74f),
        photoScrim = Color(0xFF070B1E).copy(alpha = 0.38f),
        windowColor = SpaceInk,
    )

    val All = listOf(Clean, Space)

    fun of(id: String): Skin = if (id == Space.id) Space else Clean
}

/**
 * Avatar del bambino. Emoji e non disegni: arrivano dal font di sistema,
 * restano colorati in ogni dimensione e non pesano nell'APK.
 */
val Avatars = listOf(
    "🚀", "👩‍🚀", "🧑‍🚀", "⭐", "🪐", "🌙",
    "🦄", "🐱", "🦊", "🐧", "🐸", "🐻",
)

val LocalSkin = staticCompositionLocalOf { Skins.Clean }
