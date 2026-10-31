package it.iotatec.starkid.data

import android.content.pm.ApplicationInfo

/**
 * Vocabolario condiviso fra la configurazione salvata e la UI. Sono stringhe e
 * non enum perche finiscono dentro le SharedPreferences: un id che resta
 * uguale nel tempo sopravvive agli aggiornamenti dell'app.
 */
const val SKIN_CLEAN = "pulito"
const val SKIN_SPACE = "spaziale"

/** Lo sfondo e la foto del genitore e non una tinta del tema. */
const val BACKGROUND_PHOTO = "foto"

/**
 * Come si apre la schermata del bambino: sulle categorie, da cui si entra, o
 * direttamente su tutto quello che c'e.
 */
const val HOME_CATEGORIES = "categorie"
const val HOME_ALL = "tutte"

/**
 * Come sta lo schermo. Un tablet per bambini si usa in orizzontale, e se gira
 * da solo mentre il bambino gioca le cose si spostano sotto le dita: percio
 * l'orizzontale e il valore predefinito e la rotazione e una scelta.
 */
const val ORIENT_LANDSCAPE = "orizzontale"
const val ORIENT_PORTRAIT = "verticale"
const val ORIENT_AUTO = "automatico"

/** Categorie della barra in alto, come sul tablet di un bambino. */
enum class AppCategory(val id: String) {
    GAMES("giochi"),
    VIDEO("video"),
    MUSIC("musica"),
    // "immagini" e non "foto" perche "foto" e gia l'id dello sfondo fotografico:
    // due vocabolari diversi con la stessa parola si confondono rileggendoli.
    IMAGES("immagini"),
    DRAW("disegno"),
    LEARN("imparo"),
    OTHER("altro");

    companion object {
        fun from(id: String?): AppCategory =
            entries.firstOrNull { it.id == id } ?: OTHER

        /** Dove finisce un'app: scelta del genitore, altrimenti quella indovinata. */
        fun of(config: KidsConfig, app: AppEntry): AppCategory =
            config.appCategories[app.packageName]
                ?.let { from(it) }
                ?: app.suggestedCategory

        /**
         * Categoria indovinata da quella che l'app dichiara nel manifest
         * (`ApplicationInfo.category`, da Android 8). Serve solo come primo
         * tentativo: il genitore puo sempre spostare un'app a mano, e quasi
         * nessuna app dichiara "imparo", che resta una scelta umana.
         */
        fun suggestFor(info: ApplicationInfo): AppCategory = when (info.category) {
            // I valori sono quelli dichiarati dall'app nel manifest.
            ApplicationInfo.CATEGORY_GAME -> GAMES
            ApplicationInfo.CATEGORY_AUDIO -> MUSIC
            ApplicationInfo.CATEGORY_VIDEO -> VIDEO
            // CATEGORY_IMAGE sono galleria e fotocamera, non app per disegnare:
            // "Disegno" resta una scelta che fa il genitore.
            ApplicationInfo.CATEGORY_IMAGE -> IMAGES
            else -> OTHER
        }
    }
}
