package it.iotatec.starkid.domain

import it.iotatec.starkid.data.KidsConfig

/** Perche il bambino e stato bloccato. */
enum class BlockReason {
    NONE,
    OUTSIDE_SCHEDULE,
    DAILY_LIMIT,
    APP_LIMIT,
    APP_NOT_ALLOWED,
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
    ): BlockReason {
        if (packageName !in config.allowedPackages) return BlockReason.APP_NOT_ALLOWED
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
