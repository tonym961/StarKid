package it.iotatec.starkid.data

/**
 * App consigliate al genitore.
 *
 * Il criterio non e il prezzo ma la pulizia: su un tablet di un bambino il
 * problema sono la pubblicita e gli acquisti in-app, non i soldi. Ogni voce
 * e stata controllata sulla scheda Google Play italiana leggendo le etichette
 * ufficiali "Contiene annunci" e "Acquisti in-app"; [CHECKED_ON] dice quando.
 *
 * Le condizioni cambiano senza preavviso: la data si mostra al genitore
 * apposta, perche un consiglio sbagliato su un tablet di un bambino costa piu
 * di un consiglio mancante.
 */
const val CHECKED_ON = "31 ottobre 2026"

data class Recommendation(
    val packageName: String,
    val name: String,
    val publisher: String,
    val blurb: String,
    val italian: Boolean,
    /** Avvertenza da mostrare in rosso; null se non ce n'e. */
    val caveat: String? = null,
)

/** I tre gruppi in cui si presentano, dal migliore al "da valutare". */
data class RecommendationGroup(
    val title: String,
    val note: String,
    val apps: List<Recommendation>,
)

val Recommendations = listOf(
    RecommendationGroup(
        title = "In italiano e pulite",
        note = "Nessuna pubblicita, nessun acquisto, funzionano senza rete.",
        apps = listOf(
            Recommendation(
                packageName = "net.gcompris.full",
                name = "GCompris",
                publisher = "Timothee Giet (progetto KDE)",
                blurb = "Oltre 150 attivita: lettere, numeri, memoria, puzzle, logica. " +
                    "Software libero, non raccoglie nessun dato.",
                italian = true,
            ),
            Recommendation(
                packageName = "com.rvappstudios.numbers123.toddler.counting.tracing",
                name = "Contare i numeri per i bambini",
                publisher = "RV AppStudios",
                blurb = "Numeri da 1 a 50: contare, tracciare le cifre, mini-giochi.",
                italian = true,
            ),
            Recommendation(
                packageName = "com.rvappstudios.math.kids.counting",
                name = "Matematica Giochi",
                publisher = "RV AppStudios",
                blurb = "Addizioni, sottrazioni e conteggio a livelli, dai 5 anni.",
                italian = true,
            ),
            Recommendation(
                packageName = "com.rvappstudios.jigsaw.puzzles.kids",
                name = "Gioco di puzzle per bambini",
                publisher = "RV AppStudios",
                blurb = "Puzzle a incastro con animali e veicoli, si gioca senza leggere.",
                italian = true,
            ),
            Recommendation(
                packageName = "com.rvappstudios.kids.drawing.games.coloring.book.paint",
                name = "Bambini gioco di disegno",
                publisher = "RV AppStudios",
                blurb = "Colorare e disegnare libero, senza una parola: va bene dai 3 anni.",
                italian = true,
            ),
            Recommendation(
                packageName = "com.lalumo.app",
                name = "Lalumo",
                publisher = "Eastcoast Laboratories",
                blurb = "Educazione musicale con personaggi animali: suoni, melodia, ritmo. " +
                    "Open source.",
                italian = true,
                caveat = "App molto piccola e poco collaudata: provala prima tu.",
            ),
            Recommendation(
                packageName = "com.duckduckmoosedesign.bus",
                name = "Wheels on the Bus",
                publisher = "Duck Duck Moose (Khan Academy)",
                blurb = "Filastrocca cantata con scene da toccare, per i piu piccoli.",
                italian = true,
            ),
        ),
    ),
    RecommendationGroup(
        title = "Ottime ma in inglese",
        note = "Valgono lo stesso, ma la lingua e un limite serio per un bambino italiano.",
        apps = listOf(
            Recommendation(
                packageName = "org.pbskids.gamesapp",
                name = "PBS KIDS Games",
                publisher = "PBS KIDS",
                blurb = "Centinaia di minigiochi dei cartoni PBS. Non raccoglie nessun dato.",
                italian = false,
                caveat = "I giochi si scaricano uno a uno: la prima volta serve la rete.",
            ),
            Recommendation(
                packageName = "org.khankids.android",
                name = "Khan Academy Kids",
                publisher = "Khan Academy",
                blurb = "Il percorso educativo piu completo per 2-8 anni, gratuito per sempre.",
                italian = false,
                caveat = "Solo in inglese, e crea un profilo del bambino.",
            ),
            Recommendation(
                packageName = "com.duckduckmoosedesign.cpkids",
                name = "ChatterPix Kids",
                publisher = "Duck Duck Moose (Khan Academy)",
                blurb = "Si fotografa un oggetto, gli si disegna una bocca e parla con la " +
                    "voce del bambino. La lingua non serve.",
                italian = false,
                caveat = "Chiede fotocamera e microfono.",
            ),
        ),
    ),
    RecommendationGroup(
        title = "Pulite, ma leggi prima",
        note = "Tecnicamente senza pubblicita, con qualcosa da sapere.",
        apps = listOf(
            Recommendation(
                packageName = "org.scratchjr.android",
                name = "ScratchJr",
                publisher = "Scratch Foundation",
                blurb = "Programmazione a blocchi per 5-7 anni: si costruiscono storie animate.",
                italian = true,
                caveat = "Su Play dichiara di poter condividere posizione e attivita con " +
                    "terze parti, cosa che non torna con quello che l'app fa.",
            ),
            Recommendation(
                packageName = "com.ferrero.applayduGP",
                name = "Applaydu",
                publisher = "Ferrero",
                blurb = "Giochi e storie in italiano, senza pubblicita ne acquisti.",
                italian = true,
                caveat = "E costruita sui personaggi delle sorprese Kinder: l'app stessa e " +
                    "pubblicita. Dichiara di raccogliere posizione e dati personali.",
            ),
        ),
    ),
)

/** Quelle da non consigliare, e perche: utile quanto i consigli. */
data class Warning(val name: String, val reason: String)

val NotRecommended = listOf(
    Warning("Bambini gioco di apprendimento", "pubblicita e acquisti in-app"),
    Warning("Giochi Educativi per Bambini (Bini Bambini)", "pubblicita e acquisti in-app"),
    Warning("Kahoot! Kids", "si paga a abbonamento"),
    Warning("LEGO DUPLO World", "gratis solo l'assaggio, poi abbonamento"),
)
