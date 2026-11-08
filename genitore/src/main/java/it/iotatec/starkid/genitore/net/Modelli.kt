package it.iotatec.starkid.genitore.net

import org.json.JSONObject

/**
 * Quello che il server dice di un tablet.
 *
 * I nomi dei campi sono quelli del server, non tradotti: se un giorno non
 * combaciano, il posto in cui guardare e uno solo. Le date sono secondi
 * epoch, come li manda il server.
 */
data class Tablet(
    val id: String,
    val nome: String,
    val creatoIl: Long,
    val arruolato: Boolean,
    val arruolatoIl: Long?,
    val attesaArruolamento: Boolean,
    val vistoIl: Long?,
    val vistoDiRecente: Boolean,
    val revisione: Int,
    val revisioneConsegnata: Int?,
    val revisioneConfermata: Int?,
    val ultimoEsito: String?,
    val campiRifiutati: List<String>,
    val allineato: Boolean,
    val kiosk: String,
    /** Il tablet e chiuso dal genitore. Lo dice il documento pubblicato, non
     *  il tablet: "bloccato" a true vuol dire che il server lo ha deciso. */
    val bloccato: Boolean,
    val configurazioneLeggibile: Boolean,
    /**
     * Le cinque spie, cosi come le DICHIARA il tablet. Il server non ha modo
     * di verificarle: il nome del campo lo dice apposta, e la schermata deve
     * dirlo a chi guarda.
     */
    val saluteDichiarata: Map<String, Boolean>,
    val saluteIl: Long?,
) {
    companion object {
        fun da(o: JSONObject): Tablet {
            val rifiutati = o.optJSONArray("campi_rifiutati")
            val salute = o.optJSONObject("salute_dichiarata")
            return Tablet(
                id = o.optString("id"),
                nome = o.optString("nome"),
                creatoIl = o.optLong("creato_il"),
                arruolato = o.optBoolean("arruolato"),
                arruolatoIl = o.optLongOrNull("arruolato_il"),
                attesaArruolamento = o.optBoolean("attesa_arruolamento"),
                vistoIl = o.optLongOrNull("visto_il"),
                vistoDiRecente = o.optBoolean("visto_di_recente"),
                revisione = o.optInt("revisione", 1),
                revisioneConsegnata = o.optIntOrNull("revisione_consegnata"),
                revisioneConfermata = o.optIntOrNull("revisione_confermata"),
                ultimoEsito = o.optString("ultimo_esito").ifEmpty { null },
                campiRifiutati = buildList {
                    if (rifiutati != null) for (i in 0 until rifiutati.length()) add(rifiutati.getString(i))
                },
                allineato = o.optBoolean("allineato"),
                kiosk = o.optString("kiosk", "sconosciuto"),
                bloccato = o.optBoolean("bloccato", false),
                configurazioneLeggibile = o.optBoolean("configurazione_leggibile", true),
                saluteDichiarata = buildMap {
                    if (salute != null) for (chiave in salute.keys()) put(chiave, salute.optBoolean(chiave))
                },
                saluteIl = o.optLongOrNull("salute_il"),
            )
        }
    }
}

private fun JSONObject.optLongOrNull(nome: String): Long? =
    if (isNull(nome)) null else optLong(nome).takeIf { it != 0L }

private fun JSONObject.optIntOrNull(nome: String): Int? =
    if (isNull(nome)) null else optInt(nome).takeIf { it != 0 }

/**
 * Le cinque spie, nell ordine in cui le mostra anche la web UI, con la frase
 * che dice la conseguenza invece del nome tecnico del permesso. "Accesso ai
 * dati di utilizzo" non vuol dire niente a nessuno: "senza, i minuti non si
 * contano" si.
 */
enum class Spia(val chiave: String, val titolo: String, val conseguenza: String) {
    // Le chiavi sono quelle di RichiestaSalute in server/app/models.py: sono
    // in inglese perche le manda il tablet, e non si rinominano per gusto.
    PREDEFINITO("default_home", "Launcher predefinito",
        "Senza, col tasto Home il bambino torna alla schermata di Android."),
    UTILIZZO("usage_access", "Accesso ai dati di utilizzo",
        "Senza, i minuti non si contano e il limite giornaliero non vale."),
    SOPRA("overlay", "Mostra sopra le altre app",
        "Senza, l'avviso di tempo scaduto non riesce a comparire."),
    SERVIZIO("guard_running", "Servizio di controllo attivo",
        "Senza, orari e limiti valgono solo mentre la app e aperta."),
    PROPRIETARIO("device_owner", "Controllo del tablet (Device Owner)",
        "Senza, il kiosk non si puo accendere."),
}
