package it.iotatec.starkid.games

import it.iotatec.starkid.data.AppCategory

/**
 * I giochi che StarKid si porta dentro.
 *
 * Girano senza rete, senza account e senza pubblicita: e tutto il motivo per
 * cui esistono. Su un tablet di un bambino il problema non e trovare giochi
 * gratuiti, e trovarne che non vendano qualcosa.
 *
 * Gli id finiscono nelle preferenze (quali giochi il genitore ha spento):
 * vanno tenuti stabili nel tempo.
 */
data class GameDef(
    val id: String,
    val label: String,
    val emoji: String,
    val blurb: String,
    /** In quale scomparto della barra finisce, come per le app installate. */
    val category: AppCategory,
)

object Games {

    val Memory = GameDef(
        id = "memory",
        label = "Memory spaziale",
        emoji = "🪐",
        blurb = "Trova le coppie uguali",
        category = AppCategory.GAMES,
    )

    val Counting = GameDef(
        id = "conta",
        label = "Conta le stelle",
        emoji = "⭐",
        blurb = "Quante ne vedi?",
        category = AppCategory.LEARN,
    )

    val Catch = GameDef(
        id = "acchiappa",
        label = "Acchiappa la stella",
        emoji = "💫",
        blurb = "Toccala prima che scappi",
        category = AppCategory.GAMES,
    )

    val Sequence = GameDef(
        id = "sequenza",
        label = "Sequenza luminosa",
        emoji = "🎆",
        blurb = "Ripeti l'ordine delle luci",
        category = AppCategory.LEARN,
    )

    val OddOne = GameDef(
        id = "intruso",
        label = "Trova l'intruso",
        emoji = "🔍",
        blurb = "Uno non c'entra niente",
        category = AppCategory.LEARN,
    )

    val Draw = GameDef(
        id = "disegna",
        label = "Disegna nello spazio",
        emoji = "🎨",
        blurb = "Dipingi col dito",
        category = AppCategory.DRAW,
    )

    val Car = GameDef(
        id = "macchinine",
        label = "Macchinine",
        emoji = "🏎️",
        blurb = "Inclina il tablet per sterzare",
        category = AppCategory.GAMES,
    )

    val Xylophone = GameDef(
        id = "xilofono",
        label = "Xilofono",
        emoji = "🎵",
        blurb = "Otto note da suonare",
        category = AppCategory.MUSIC,
    )

    val Puzzle = GameDef(
        id = "puzzle",
        label = "Puzzle scorrevole",
        emoji = "🧩",
        blurb = "Rimetti insieme la figura",
        category = AppCategory.GAMES,
    )

    val Compare = GameDef(
        id = "confronto",
        label = "Dove ce n'e di piu",
        emoji = "⚖️",
        blurb = "Guarda quale mucchio e piu grande",
        category = AppCategory.LEARN,
    )

    val Pattern = GameDef(
        id = "serie",
        label = "Completa la serie",
        emoji = "🔁",
        blurb = "Capisci il ritmo e continualo",
        category = AppCategory.LEARN,
    )

    val All = listOf(
        Car,
        Memory,
        Puzzle,
        Xylophone,
        Counting,
        Compare,
        Pattern,
        Catch,
        Sequence,
        OddOne,
        Draw,
    )

    fun byId(id: String): GameDef? = All.firstOrNull { it.id == id }
}

/** Figure del memory: dieci simboli, bastano per cinque coppie e avanzano. */
val MemorySymbols = listOf("🚀", "🪐", "🌙", "⭐", "☄️", "🛸", "👽", "🌍", "🔭", "🌈")

/**
 * Gruppi per "trova l'intruso". Ogni gruppo tiene cose che un bambino mette
 * insieme senza bisogno di parole; nessuna figura compare in due gruppi, se no
 * l'intruso non sarebbe uno solo.
 */
val OddOneGroups = listOf(
    listOf("🐶", "🐱", "🐮", "🐷", "🐸", "🐵", "🐔", "🦊"),
    listOf("🚗", "🚌", "🚒", "🚜", "🚲", "✈️", "🚢", "🛵"),
    listOf("🍎", "🍌", "🍓", "🍇", "🍉", "🍊", "🍐", "🍒"),
    listOf("🚀", "🪐", "⭐", "🌙", "☄️", "🛸"),
)
