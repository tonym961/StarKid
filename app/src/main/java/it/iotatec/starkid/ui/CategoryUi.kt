package it.iotatec.starkid.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.ui.graphics.vector.ImageVector
import it.iotatec.starkid.data.AppCategory

/** Come si chiamano le categorie sulla barra del bambino. */
val AppCategory.label: String
    get() = when (this) {
        AppCategory.GAMES -> "Giochi"
        AppCategory.VIDEO -> "Video"
        AppCategory.MUSIC -> "Musica"
        AppCategory.IMAGES -> "Foto"
        AppCategory.DRAW -> "Disegno"
        AppCategory.LEARN -> "Imparo"
        AppCategory.OTHER -> "Altro"
    }

/** Un disegno per ogni categoria: alla barra guarda chi non sa ancora leggere. */
val AppCategory.icon: ImageVector
    get() = when (this) {
        AppCategory.GAMES -> Icons.Rounded.SportsEsports
        AppCategory.VIDEO -> Icons.Rounded.Movie
        AppCategory.MUSIC -> Icons.Rounded.MusicNote
        AppCategory.IMAGES -> Icons.Rounded.PhotoCamera
        AppCategory.DRAW -> Icons.Rounded.Brush
        AppCategory.LEARN -> Icons.Rounded.School
        AppCategory.OTHER -> Icons.Rounded.Widgets
    }
