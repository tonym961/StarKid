package it.iotatec.starkid.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Tutta la configurazione del launcher, immutabile. */
data class KidsConfig(
    val pinSet: Boolean = false,
    val childName: String = "",
    val allowedPackages: Set<String> = emptySet(),
    /** Tetto complessivo in minuti; 0 = senza limite. */
    val dailyLimitMinutes: Int = 0,
    /** Tetto per singola app in minuti; chiave assente o 0 = senza limite. */
    val perAppLimits: Map<String, Int> = emptyMap(),
    val scheduleEnabled: Boolean = false,
    val startMinuteOfDay: Int = 8 * 60,
    val endMinuteOfDay: Int = 20 * 60,
    /** Giorni attivi, come Calendar.DAY_OF_WEEK (domenica = 1). */
    val activeDays: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7),
    /** Il genitore ha chiesto la modalita kiosk. */
    val kioskEnabled: Boolean = false,
    /** Controllo sospeso fino a questo istante (epoch ms). */
    val guardPausedUntil: Long = 0L,
    val bonusMinutes: Int = 0,
    val bonusDate: String = "",
    /** Tema scelto dal genitore: [SKIN_CLEAN] o [SKIN_SPACE]. */
    val skin: String = SKIN_CLEAN,
    /** Id della tinta di sfondo, oppure [BACKGROUND_PHOTO]. */
    val background: String = "lilla",
    /** Cambia a ogni foto nuova: e il segnale per rileggerla dal disco. */
    val backgroundStamp: Long = 0L,
    /** Emoji mostrata accanto al nome del bambino. */
    val avatar: String = "🚀",
    /** Categoria decisa dal genitore, per pacchetto; le altre si indovinano. */
    val appCategories: Map<String, String> = emptyMap(),
    /**
     * Giochi di StarKid che il genitore ha spento. Si salva chi e **spento**,
     * non chi e acceso: cosi un gioco aggiunto in futuro compare da solo
     * invece di restare invisibile a chi aveva gia salvato le sue scelte.
     */
    val disabledGames: Set<String> = emptySet(),
    /** Minuti passati sui giochi di StarKid, validi solo per [playDate]. */
    val playMinutes: Int = 0,
    val playDate: String = "",
    /** Suoni dei giochi di StarKid. */
    val soundOn: Boolean = true,
    /** [HOME_CATEGORIES] oppure [HOME_ALL]. */
    val homeLayout: String = HOME_CATEGORIES,
    /**
     * Indirizzo del server di gestione, consegnato al provisioning. Vuoto
     * vuol dire che questo tablet non parla con nessuno, ed e il valore
     * predefinito: la sincronizzazione e una cosa che si accende, non un
     * comportamento di fabbrica.
     */
    val serverUrl: String = "",
    /**
     * Prima dell'arruolamento e il token del QR, che vale una volta sola;
     * dopo e il token di questo dispositivo, che vale finche il genitore non
     * lo revoca. Sono due cose diverse con lo stesso posto, e [enrolled] dice
     * quale delle due c'e dentro.
     */
    val serverToken: String = "",
    val enrolled: Boolean = false,
    /** Quando e andata a buon fine l'ultima sincronizzazione, 0 = mai. */
    val lastSync: Long = 0L,
    /** L ultima revisione confermata al server: serve a non riconfermarla. */
    val syncedRevision: Int = 0,
    /**
     * Da quando il server risponde 401 a questo tablet, 0 = mai.
     *
     * Il token NON si butta al primo 401: si segna da quando, si continua a
     * chiedere piu piano, e lo sgancio resta una decisione di una persona.
     * Un token buttato si rifa solo portando un codice nuovo a quel tablet,
     * e un 401 vuol dire anche "il server e stato ripristinato da una copia
     * vecchia", che si aggiusta da solo.
     */
    val rifiutatoDal: Long = 0L,
    /**
     * Il genitore ha chiuso il tablet da remoto. Non e il kiosk e non e un
     * limite di tempo: e "adesso basta", e si toglie solo da dove e stato
     * messo. Il PIN sul tablet NON lo apre, se no non sarebbe un blocco a
     * distanza: sarebbe un suggerimento.
     */
    val lockedByParent: Boolean = false,
    /** [ORIENT_LANDSCAPE], [ORIENT_PORTRAIT] o [ORIENT_AUTO]. */
    val orientation: String = ORIENT_LANDSCAPE,
    /** Col kiosk acceso, lascia o no il tasto delle app recenti. */
    val kioskRecents: Boolean = false,
)

/**
 * Persistenza su SharedPreferences, esposta come StateFlow cosi la UI Compose
 * si aggiorna da sola a ogni modifica fatta dal genitore.
 */
class Prefs private constructor(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("starkid", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(read())
    val config: StateFlow<KidsConfig> = _config.asStateFlow()

    val current: KidsConfig get() = _config.value

    // --- lettura ---------------------------------------------------------

    private fun read() = KidsConfig(
        pinSet = sp.getString(KEY_PIN_HASH, null) != null,
        childName = sp.getString(KEY_CHILD_NAME, "") ?: "",
        allowedPackages = sp.getStringSet(KEY_ALLOWED, emptySet())?.toSet() ?: emptySet(),
        dailyLimitMinutes = sp.getInt(KEY_DAILY_LIMIT, 0),
        perAppLimits = decodeLimits(sp.getString(KEY_APP_LIMITS, "") ?: ""),
        scheduleEnabled = sp.getBoolean(KEY_SCHEDULE_ON, false),
        startMinuteOfDay = sp.getInt(KEY_START, 8 * 60),
        endMinuteOfDay = sp.getInt(KEY_END, 20 * 60),
        activeDays = sp.getStringSet(KEY_DAYS, null)
            ?.mapNotNull { it.toIntOrNull() }
            ?.toSet()
            ?: setOf(1, 2, 3, 4, 5, 6, 7),
        kioskEnabled = sp.getBoolean(KEY_KIOSK, false),
        guardPausedUntil = sp.getLong(KEY_PAUSED_UNTIL, 0L),
        bonusMinutes = sp.getInt(KEY_BONUS_MIN, 0),
        bonusDate = sp.getString(KEY_BONUS_DATE, "") ?: "",
        skin = sp.getString(KEY_SKIN, SKIN_CLEAN) ?: SKIN_CLEAN,
        background = sp.getString(KEY_BACKGROUND, "lilla") ?: "lilla",
        backgroundStamp = sp.getLong(KEY_BACKGROUND_STAMP, 0L),
        avatar = sp.getString(KEY_AVATAR, "🚀") ?: "🚀",
        appCategories = decodeText(sp.getString(KEY_CATEGORIES, "") ?: ""),
        disabledGames = sp.getStringSet(KEY_GAMES_OFF, emptySet())?.toSet() ?: emptySet(),
        playMinutes = sp.getInt(KEY_PLAY_MINUTES, 0),
        playDate = sp.getString(KEY_PLAY_DATE, "") ?: "",
        soundOn = sp.getBoolean(KEY_SOUND, true),
        homeLayout = sp.getString(KEY_HOME_LAYOUT, HOME_CATEGORIES) ?: HOME_CATEGORIES,
        serverUrl = sp.getString(KEY_SERVER_URL, "") ?: "",
        serverToken = sp.getString(KEY_SERVER_TOKEN, "") ?: "",
        enrolled = sp.getBoolean(KEY_ENROLLED, false),
        lastSync = sp.getLong(KEY_LAST_SYNC, 0L),
        syncedRevision = sp.getInt(KEY_SYNCED_REV, 0),
        lockedByParent = sp.getBoolean(KEY_LOCKED, false),
        rifiutatoDal = sp.getLong(KEY_RIFIUTATO, 0L),
        orientation = sp.getString(KEY_ORIENTATION, ORIENT_LANDSCAPE) ?: ORIENT_LANDSCAPE,
        kioskRecents = sp.getBoolean(KEY_KIOSK_RECENTS, false),
    )

    private fun edit(block: SharedPreferences.Editor.() -> Unit) {
        sp.edit().apply(block).apply()
        _config.value = read()
    }

    // --- scrittura -------------------------------------------------------

    fun setChildName(name: String) = edit { putString(KEY_CHILD_NAME, name.trim()) }

    fun setAllowed(packages: Set<String>) = edit { putStringSet(KEY_ALLOWED, packages) }

    fun toggleAllowed(packageName: String) {
        val next = current.allowedPackages.toMutableSet()
        if (!next.remove(packageName)) next.add(packageName)
        setAllowed(next)
    }

    fun setDailyLimit(minutes: Int) = edit { putInt(KEY_DAILY_LIMIT, minutes.coerceIn(0, 24 * 60)) }

    fun setAppLimit(packageName: String, minutes: Int) {
        val next = current.perAppLimits.toMutableMap()
        if (minutes <= 0) next.remove(packageName) else next[packageName] = minutes
        edit { putString(KEY_APP_LIMITS, encodeLimits(next)) }
    }

    fun setScheduleEnabled(enabled: Boolean) = edit { putBoolean(KEY_SCHEDULE_ON, enabled) }

    fun setWindow(startMinute: Int, endMinute: Int) = edit {
        putInt(KEY_START, startMinute.mod(24 * 60))
        putInt(KEY_END, endMinute.mod(24 * 60))
    }

    fun toggleDay(day: Int) {
        val next = current.activeDays.toMutableSet()
        if (!next.remove(day)) next.add(day)
        edit { putStringSet(KEY_DAYS, next.map { it.toString() }.toSet()) }
    }

    fun setKioskEnabled(enabled: Boolean) = edit { putBoolean(KEY_KIOSK, enabled) }

    // --- aspetto ---------------------------------------------------------

    /**
     * Il tema porta con se il proprio sfondo: le tinte di un tema non esistono
     * nell'altro, quindi cambiare tema senza cambiare sfondo lascerebbe una
     * configurazione che la UI deve interpretare. Una foto, che vale per tutti
     * i temi, resta al suo posto.
     */
    fun setSkin(id: String, defaultBackground: String) = edit {
        putString(KEY_SKIN, id)
        if (current.background != BACKGROUND_PHOTO) putString(KEY_BACKGROUND, defaultBackground)
    }

    fun setBackground(id: String) = edit { putString(KEY_BACKGROUND, id) }

    fun setPhotoBackground() = edit {
        putString(KEY_BACKGROUND, BACKGROUND_PHOTO)
        putLong(KEY_BACKGROUND_STAMP, System.currentTimeMillis())
    }

    fun clearPhotoBackground(fallback: String) = edit {
        putString(KEY_BACKGROUND, fallback)
        putLong(KEY_BACKGROUND_STAMP, 0L)
    }

    fun setAvatar(emoji: String) = edit { putString(KEY_AVATAR, emoji) }

    fun setSoundOn(on: Boolean) = edit { putBoolean(KEY_SOUND, on) }

    fun setHomeLayout(layout: String) = edit { putString(KEY_HOME_LAYOUT, layout) }

    fun setOrientation(value: String) = edit { putString(KEY_ORIENTATION, value) }

    fun setKioskRecents(allow: Boolean) = edit { putBoolean(KEY_KIOSK_RECENTS, allow) }

    fun setServer(url: String, token: String) = edit {
        putString(KEY_SERVER_URL, url.trim())
        putString(KEY_SERVER_TOKEN, token.trim())
        // Un token nuovo e sempre un token di arruolamento, mai di
        // dispositivo: e il server a trasformarlo, e lo segna setArruolato().
        putBoolean(KEY_ENROLLED, false)
    }

    // --- sincronizzazione col server --------------------------------------

    /** Arruolamento riuscito: da qui in poi il token e quello del dispositivo. */
    fun setArruolato(tokenDispositivo: String) = edit {
        putString(KEY_SERVER_TOKEN, tokenDispositivo.trim())
        putBoolean(KEY_ENROLLED, true)
    }

    /**
     * Il server non ci riconosce piu: il token va buttato, non ritentato.
     * Succede se il genitore ha cancellato il tablet o ha chiesto un nuovo
     * arruolamento. Il tablet continua a funzionare, da solo, come prima di
     * essere arruolato.
     */
    fun dimenticaArruolamento() = edit {
        putString(KEY_SERVER_TOKEN, "")
        putBoolean(KEY_ENROLLED, false)
        putInt(KEY_SYNCED_REV, 0)
        putLong(KEY_RIFIUTATO, 0L)
    }

    fun segnaSincronizzato(revisione: Int) = edit {
        putLong(KEY_LAST_SYNC, System.currentTimeMillis())
        putInt(KEY_SYNCED_REV, revisione)
        // Un giro andato a buon fine cancella il rifiuto: se era passeggero,
        // non deve restare scritto da nessuna parte.
        putLong(KEY_RIFIUTATO, 0L)
    }

    fun segnaRifiutato(quando: Long) = edit { putLong(KEY_RIFIUTATO, quando) }

    fun setBloccatoDalGenitore(bloccato: Boolean) = edit { putBoolean(KEY_LOCKED, bloccato) }

    fun setActiveDays(giorni: Set<Int>) = edit {
        val validi = giorni.filter { it in 1..7 }.toSet().ifEmpty { setOf(1, 2, 3, 4, 5, 6, 7) }
        putStringSet(KEY_DAYS, validi.map { it.toString() }.toSet())
    }

    fun setDisabledGames(giochi: Set<String>) = edit { putStringSet(KEY_GAMES_OFF, giochi) }

    fun toggleGame(gameId: String) {
        val next = current.disabledGames.toMutableSet()
        if (!next.remove(gameId)) next.add(gameId)
        edit { putStringSet(KEY_GAMES_OFF, next) }
    }

    /**
     * Un minuto passato su un gioco di StarKid. Conta nel tetto giornaliero
     * come conterebbe una app qualsiasi: i giochi sono dentro il launcher, ma
     * restano tempo davanti allo schermo.
     */
    fun addPlayMinute() {
        val today = todayKey()
        val already = if (current.playDate == today) current.playMinutes else 0
        edit {
            putString(KEY_PLAY_DATE, today)
            putInt(KEY_PLAY_MINUTES, already + 1)
        }
    }

    fun setAppCategory(packageName: String, category: AppCategory) {
        val next = current.appCategories.toMutableMap()
        next[packageName] = category.id
        edit { putString(KEY_CATEGORIES, encodeText(next)) }
    }

    fun pauseGuard(minutes: Int) = edit {
        putLong(KEY_PAUSED_UNTIL, System.currentTimeMillis() + minutes * 60_000L)
    }

    fun resumeGuard() = edit { putLong(KEY_PAUSED_UNTIL, 0L) }

    /** Regala minuti extra validi solo per la giornata in corso. */
    fun grantBonus(minutes: Int) {
        val today = todayKey()
        val already = if (current.bonusDate == today) current.bonusMinutes else 0
        edit {
            putString(KEY_BONUS_DATE, today)
            putInt(KEY_BONUS_MIN, already + minutes)
        }
    }

    // --- PIN -------------------------------------------------------------

    fun setPin(pin: String) {
        val salt = newSalt()
        edit {
            putString(KEY_PIN_SALT, salt)
            putString(KEY_PIN_HASH, hash(pin, salt))
        }
    }

    fun checkPin(pin: String): Boolean {
        val salt = sp.getString(KEY_PIN_SALT, null) ?: return false
        val stored = sp.getString(KEY_PIN_HASH, null) ?: return false
        return stored == hash(pin, salt)
    }

    private fun newSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.toHex()
    }

    private fun hash(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        var data = (salt + pin).toByteArray()
        // Ventimila giri: un PIN di 4 cifre resta debole di per se, ma leggere
        // l'hash dal file delle preferenze non basta piu a ricavarlo subito.
        repeat(20_000) { data = digest.digest(data) }
        return data.toHex()
    }

    companion object {
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_CHILD_NAME = "child_name"
        private const val KEY_ALLOWED = "allowed"
        private const val KEY_DAILY_LIMIT = "daily_limit"
        private const val KEY_APP_LIMITS = "app_limits"
        private const val KEY_SCHEDULE_ON = "schedule_on"
        private const val KEY_START = "window_start"
        private const val KEY_END = "window_end"
        private const val KEY_DAYS = "active_days"
        private const val KEY_KIOSK = "kiosk_enabled"
        private const val KEY_PAUSED_UNTIL = "paused_until"
        private const val KEY_BONUS_MIN = "bonus_minutes"
        private const val KEY_BONUS_DATE = "bonus_date"
        private const val KEY_SKIN = "skin"
        private const val KEY_BACKGROUND = "background"
        private const val KEY_BACKGROUND_STAMP = "background_stamp"
        private const val KEY_AVATAR = "avatar"
        private const val KEY_CATEGORIES = "app_categories"
        private const val KEY_GAMES_OFF = "games_off"
        private const val KEY_PLAY_MINUTES = "play_minutes"
        private const val KEY_PLAY_DATE = "play_date"
        private const val KEY_SOUND = "sound_on"
        private const val KEY_HOME_LAYOUT = "home_layout"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_SERVER_TOKEN = "server_token"
        private const val KEY_ENROLLED = "server_enrolled"
        private const val KEY_LAST_SYNC = "server_last_sync"
        private const val KEY_SYNCED_REV = "server_synced_revision"
        private const val KEY_LOCKED = "locked_by_parent"
        private const val KEY_RIFIUTATO = "server_rifiutato_dal"
        private const val KEY_ORIENTATION = "orientation"
        private const val KEY_KIOSK_RECENTS = "kiosk_recents"

        @Volatile
        private var instance: Prefs? = null

        fun get(context: Context): Prefs =
            instance ?: synchronized(this) {
                instance ?: Prefs(context).also { instance = it }
            }

        fun todayKey(): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)

        private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte) }

        private fun encodeLimits(map: Map<String, Int>): String =
            map.entries.joinToString("|") { "${it.key}=${it.value}" }

        private fun encodeText(map: Map<String, String>): String =
            map.entries.joinToString("|") { "${it.key}=${it.value}" }

        private fun decodeText(raw: String): Map<String, String> =
            raw.split('|')
                .filter { it.isNotBlank() }
                .mapNotNull { entry ->
                    val parts = entry.split('=')
                    if (parts.size == 2) parts[0] to parts[1] else null
                }
                .toMap()

        private fun decodeLimits(raw: String): Map<String, Int> =
            raw.split('|')
                .filter { it.isNotBlank() }
                .mapNotNull { entry ->
                    val parts = entry.split('=')
                    val minutes = parts.getOrNull(1)?.toIntOrNull()
                    if (parts.size == 2 && minutes != null) parts[0] to minutes else null
                }
                .toMap()
    }
}
