package it.iotatec.starkid.domain

import it.iotatec.starkid.data.KidsConfig

/** Perche il bambino e stato bloccato. */
enum class BlockReason {
    NONE,
    OUTSIDE_SCHEDULE,
    DAILY_LIMIT,
    APP_LIMIT,
    APP_NOT_ALLOWED,

    /**
     * L'app e fra quelle permesse e i suoi minuti non sono finiti, ma non e
     * la sua ora. Separato da [OUTSIDE_SCHEDULE] perche al bambino va detta
     * la cosa giusta: li e chiuso il tablet, qui le altre app si aprono.
     */
    APP_OUTSIDE_WINDOW,

    /**
     * Il genitore ha chiuso il tablet dalla web UI. Non si esaurisce col
     * tempo come DAILY_LIMIT e non finisce da solo come OUTSIDE_SCHEDULE:
     * resta finche il genitore non lo toglie da dove lo ha messo.
     */
    LOCKED_BY_PARENT;

    /**
     * Questo motivo chiude tutto il tablet, non una app sola.
     *
     * Serve in due punti, e in tutti e due cambia cosa fa il tablet:
     *
     * - **Indietro.** Da un'app bloccata, indietro porta a casa, ed e
     *   giusto: il bambino era dentro una cosa che non puo usare e la casa
     *   e dove deve tornare. Da un tablet chiuso, a casa non si va: ci si
     *   andava davvero, e per i quattro secondi prima che il sorvegliante
     *   rimettesse il blocco il bambino aveva la sua schermata con dentro i
     *   giochi. Una volta per ogni pressione di indietro.
     * - **Il blocco che cade.** Questi motivi possono finire mentre il
     *   blocco e ancora sullo schermo - il genitore riapre il tablet da
     *   lontano, arriva l'ora, un bonus rimette minuti - e allora il blocco
     *   si toglie da solo. Quelli di una app sola no: li l'app vietata non e
     *   piu davanti, e togliere il blocco ce la riporterebbe.
     */
    val chiudeIlTablet: Boolean
        get() = this == LOCKED_BY_PARENT || this == OUTSIDE_SCHEDULE || this == DAILY_LIMIT
}

/**
 * Regole di blocco. Funzioni pure, senza dipendenze da Android, cosi sono
 * verificabili con gli unit test in app/src/test.
 */
object Guard {

    /**
     * Controlli validi per tutto il dispositivo: fascia oraria e tetto
     * giornaliero complessivo.
     *
     * @param dayOfWeek come java.util.Calendar.DAY_OF_WEEK (domenica = 1).
     * @param minuteOfDay minuti dalla mezzanotte, 0..1439.
     * @param usedMinutesToday minuti gia consumati oggi nelle app permesse.
     * @param today chiave della giornata, formato yyyy-MM-dd.
     */
    fun checkGlobal(
        config: KidsConfig,
        dayOfWeek: Int,
        minuteOfDay: Int,
        usedMinutesToday: Int,
        today: String,
    ): BlockReason {
        // Prima di tutto il resto: un blocco messo dal genitore non lo
        // scavalca nessuna fascia oraria e nessun bonus.
        if (config.lockedByParent) return BlockReason.LOCKED_BY_PARENT
        if (config.scheduleEnabled) {
            if (dayOfWeek !in config.activeDays) return BlockReason.OUTSIDE_SCHEDULE
            if (!inWindow(minuteOfDay, config.startMinuteOfDay, config.endMinuteOfDay)) {
                return BlockReason.OUTSIDE_SCHEDULE
            }
        }
        val limit = config.dailyLimitMinutes
        if (limit > 0 && usedMinutesToday >= limit + bonusFor(config, today)) {
            return BlockReason.DAILY_LIMIT
        }
        return BlockReason.NONE
    }

    /** Controlli specifici dell'app in primo piano. */
    fun checkApp(
        config: KidsConfig,
        packageName: String,
        usedMinutesForApp: Int,
        dayOfWeek: Int,
        minuteOfDay: Int,
    ): BlockReason {
        if (packageName !in config.allowedPackages) return BlockReason.APP_NOT_ALLOWED
        // L'ora prima dei minuti: se non e la sua ora, quanti minuti siano
        // avanzati non interessa, e dirgli "hai finito il tempo" quando il
        // tempo c'e sarebbe una bugia.
        val fascia = config.perAppWindows[packageName]
        if (fascia != null) {
            if (dayOfWeek !in fascia.giorni) return BlockReason.APP_OUTSIDE_WINDOW
            if (!inWindow(minuteOfDay, fascia.inizio, fascia.fine)) {
                return BlockReason.APP_OUTSIDE_WINDOW
            }
        }
        val limit = config.perAppLimits[packageName] ?: 0
        if (limit > 0 && usedMinutesForApp >= limit) return BlockReason.APP_LIMIT
        return BlockReason.NONE
    }

    /** Minuti ancora disponibili oggi, o null se non c'e un tetto giornaliero. */
    fun remainingMinutes(config: KidsConfig, usedMinutesToday: Int, today: String): Int? {
        if (config.dailyLimitMinutes <= 0) return null
        val total = config.dailyLimitMinutes + bonusFor(config, today)
        return (total - usedMinutesToday).coerceAtLeast(0)
    }

    /**
     * Vero se [minuteOfDay] cade nella finestra [start, end). Gestisce anche le
     * finestre che scavalcano la mezzanotte, come 22:00 -> 07:00.
     */
    fun inWindow(minuteOfDay: Int, start: Int, end: Int): Boolean = when {
        start == end -> true
        start < end -> minuteOfDay in start until end
        else -> minuteOfDay >= start || minuteOfDay < end
    }

    private fun bonusFor(config: KidsConfig, today: String): Int =
        if (config.bonusDate == today) config.bonusMinutes else 0
}
