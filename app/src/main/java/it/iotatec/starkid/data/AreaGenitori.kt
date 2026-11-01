package it.iotatec.starkid.data

/**
 * "Al tablet, adesso, c'e il genitore."
 *
 * PERCHE ESISTE. Il sorvegliante (service/GuardService) guarda ogni quattro
 * secondi cosa c'e in primo piano e blocca quello che non e fra le app del
 * bambino. Non sapeva niente dell'area genitori, e da li nascevano due
 * guasti che si vedevano subito:
 *
 *  1. Il genitore apre le impostazioni di sistema dalla sua area, e quattro
 *     secondi dopo si becca "Questa app non si puo aprire" - cioe la
 *     schermata di blocco di StarKid, perche le impostazioni non sono fra le
 *     app del bambino. Ovvio che non lo siano.
 *  2. Fuori dalla fascia oraria, o a tempo finito, la schermata di blocco
 *     ricompariva SOPRA l'area genitori ogni quattro secondi: proprio quando
 *     ci si entra per cambiare l'orario, non si riusciva a cambiarlo.
 *
 * Il kiosk l'area genitori la conosceva gia (Kiosk.applyPolicies con
 * allowSettings): era il sorvegliante a essere rimasto indietro.
 *
 * QUESTO E SOLO IL PRIMO DEI DUE PEZZI, e copre il caso "il genitore e
 * davanti al tablet, dentro la sua area". Quando invece esce verso un'altra
 * app - le impostazioni di sistema, il negozio - StarKid va dietro e questo
 * flag si spegne: li serve la manutenzione, che e `Prefs.pauseGuard` e vive
 * nelle preferenze perche deve sopravvivere al cambio di app.
 *
 * NON E UN MODO DI SPEGNERE IL CONTROLLO. Vale solo mentre l'area genitori e
 * davvero davanti, e dietro l'area genitori c'e il PIN.
 */
object AreaGenitori {

    @Volatile
    private var schermata = false

    @Volatile
    private var inPrimoPiano = false

    /**
     * Il genitore e alle manette: l'area genitori e la schermata di adesso E
     * StarKid e davanti.
     *
     * Servono tutte e due. Solo la schermata non basta: quando il genitore
     * esce verso le impostazioni la schermata resta quella, ma da quel
     * momento davanti c'e un'altra app e il controllo deve tornare a
     * funzionare su tutto il resto.
     */
    val dentro: Boolean get() = schermata && inPrimoPiano

    fun schermataGenitore(si: Boolean) { schermata = si }

    fun primoPiano(si: Boolean) { inPrimoPiano = si }

    /** Serve ai test. */
    fun azzera() {
        schermata = false
        inPrimoPiano = false
    }
}
