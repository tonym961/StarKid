package it.iotatec.starkid.domain

import it.iotatec.starkid.data.KidsConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardTest {

    private val game = "com.esempio.gioco"
    private val today = "2026-10-31"

    private val base = KidsConfig(
        pinSet = true,
        allowedPackages = setOf(game),
    )

    // --- finestra oraria -------------------------------------------------

    @Test
    fun `finestra normale include solo le ore interne`() {
        assertTrue(Guard.inWindow(10 * 60, 8 * 60, 20 * 60))
        assertFalse(Guard.inWindow(7 * 60, 8 * 60, 20 * 60))
        assertFalse(Guard.inWindow(20 * 60, 8 * 60, 20 * 60))
    }

    @Test
    fun `finestra a cavallo della mezzanotte include le ore piccole`() {
        assertTrue(Guard.inWindow(23 * 60, 22 * 60, 7 * 60))
        assertTrue(Guard.inWindow(2 * 60, 22 * 60, 7 * 60))
        assertFalse(Guard.inWindow(12 * 60, 22 * 60, 7 * 60))
    }

    @Test
    fun `inizio uguale a fine vale sempre`() {
        assertTrue(Guard.inWindow(0, 9 * 60, 9 * 60))
        assertTrue(Guard.inWindow(13 * 60, 9 * 60, 9 * 60))
    }

    // --- controlli globali -----------------------------------------------

    @Test
    fun `senza limiti non si blocca`() {
        val verdict = Guard.checkGlobal(base, 2, 15 * 60, 600, today)
        assertEquals(BlockReason.NONE, verdict)
    }

    @Test
    fun `fuori fascia oraria si blocca`() {
        val config = base.copy(scheduleEnabled = true, startMinuteOfDay = 8 * 60, endMinuteOfDay = 20 * 60)
        val verdict = Guard.checkGlobal(config, 2, 21 * 60, 0, today)
        assertEquals(BlockReason.OUTSIDE_SCHEDULE, verdict)
    }

    @Test
    fun `giorno non attivo si blocca anche dentro la fascia`() {
        val config = base.copy(
            scheduleEnabled = true,
            activeDays = setOf(2, 3, 4, 5, 6),
            startMinuteOfDay = 8 * 60,
            endMinuteOfDay = 20 * 60,
        )
        // 1 = domenica, fuori dai giorni attivi
        val verdict = Guard.checkGlobal(config, 1, 10 * 60, 0, today)
        assertEquals(BlockReason.OUTSIDE_SCHEDULE, verdict)
    }

    @Test
    fun `tetto giornaliero raggiunto blocca`() {
        val config = base.copy(dailyLimitMinutes = 60)
        assertEquals(BlockReason.NONE, Guard.checkGlobal(config, 2, 10 * 60, 59, today))
        assertEquals(BlockReason.DAILY_LIMIT, Guard.checkGlobal(config, 2, 10 * 60, 60, today))
    }

    @Test
    fun `i minuti regalati oggi allargano il tetto`() {
        val config = base.copy(dailyLimitMinutes = 60, bonusMinutes = 15, bonusDate = today)
        assertEquals(BlockReason.NONE, Guard.checkGlobal(config, 2, 10 * 60, 70, today))
        assertEquals(BlockReason.DAILY_LIMIT, Guard.checkGlobal(config, 2, 10 * 60, 75, today))
    }

    @Test
    fun `i minuti regalati ieri non valgono oggi`() {
        val config = base.copy(dailyLimitMinutes = 60, bonusMinutes = 60, bonusDate = "2026-10-30")
        assertEquals(BlockReason.DAILY_LIMIT, Guard.checkGlobal(config, 2, 10 * 60, 60, today))
    }

    @Test
    fun `la fascia oraria conta prima del tetto`() {
        val config = base.copy(
            scheduleEnabled = true,
            startMinuteOfDay = 8 * 60,
            endMinuteOfDay = 20 * 60,
            dailyLimitMinutes = 30,
        )
        val verdict = Guard.checkGlobal(config, 2, 22 * 60, 999, today)
        assertEquals(BlockReason.OUTSIDE_SCHEDULE, verdict)
    }

    // --- controlli per app -----------------------------------------------

    @Test
    fun `app fuori whitelist si blocca`() {
        assertEquals(BlockReason.APP_NOT_ALLOWED, Guard.checkApp(base, "com.altro", 0))
    }

    @Test
    fun `app in whitelist senza limite passa`() {
        assertEquals(BlockReason.NONE, Guard.checkApp(base, game, 500))
    }

    @Test
    fun `limite della singola app blocca al raggiungimento`() {
        val config = base.copy(perAppLimits = mapOf(game to 20))
        assertEquals(BlockReason.NONE, Guard.checkApp(config, game, 19))
        assertEquals(BlockReason.APP_LIMIT, Guard.checkApp(config, game, 20))
    }

    // --- tempo residuo ---------------------------------------------------

    @Test
    fun `senza tetto il tempo residuo non esiste`() {
        assertNull(Guard.remainingMinutes(base, 120, today))
    }

    @Test
    fun `tempo residuo scala col consumo e non va sotto zero`() {
        val config = base.copy(dailyLimitMinutes = 60)
        assertEquals(45, Guard.remainingMinutes(config, 15, today))
        assertEquals(0, Guard.remainingMinutes(config, 90, today))
    }

    @Test
    fun `tempo residuo include i minuti regalati oggi`() {
        val config = base.copy(dailyLimitMinutes = 60, bonusMinutes = 30, bonusDate = today)
        assertEquals(60, Guard.remainingMinutes(config, 30, today))
    }
}
