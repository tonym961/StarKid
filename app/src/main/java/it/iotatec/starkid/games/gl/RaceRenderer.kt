package it.iotatec.starkid.games.gl

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.random.Random

/** Che cosa si vede ai lati della pista. Sono tutti posti nello spazio. */
enum class Scenery { COLONIA, LUNA, MARTE, ANELLI }

/** Colori di una pista, in formato OpenGL (quattro componenti da 0 a 1). */
data class TrackLook(
    val road: FloatArray,
    val ground: FloatArray,
    val line: FloatArray,
    val post: FloatArray,
    val obstacle: FloatArray,
    val prize: FloatArray,
    val sky: FloatArray,
    /** Secondo colore del paesaggio: il mare, i tetti, i pianeti. */
    val accent: FloatArray,
    val scenery: Scenery,
) {
    // Gli array in una data class vanno confrontati a mano; qui basta
    // l'identita, le piste sono costanti.
    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
}

private class Item(val prize: Boolean, val x: Float, var z: Float, val spin: Float)

private const val ROAD_HALF = 5f
private const val CAR_HALF = 4.2f
private const val HORIZON = -260f

/**
 * Lunghezza di un giro. A velocita media fa poco piu di un minuto a giro:
 * con cinque giri la gara dura sui cinque minuti, che e il punto — una corsa
 * deve durare, se finisce in due minuti non e una corsa.
 */
private const val LAP_LENGTH = 2600f

/**
 * La gara in tre dimensioni.
 *
 * Il mondo vive qui, sul thread di OpenGL, e si muove a ogni fotogramma: la
 * schermata Compose legge solo punteggio e vite. Tenere la simulazione dove
 * si disegna evita di copiare lo stato fra due thread sessanta volte al
 * secondo, e fa muovere la macchina alla stessa cadenza dello schermo.
 */
class RaceRenderer(
    private val track: TrackLook,
    private val carColor: FloatArray,
    private val style: CarStyle,
    private val totalLaps: Int,
    private val onPrize: () -> Unit,
    private val onCrash: () -> Unit,
    private val onLap: () -> Unit,
) : GLSurfaceView.Renderer {

    /** Sterzo da -1 a 1, scritto da fuori (inclinazione o dito). */
    @Volatile
    var steer = 0f

    /** Posizione assoluta imposta dal dito, oppure null se comanda lo sterzo. */
    @Volatile
    var finger: Float? = null

    @Volatile
    var score = 0
        private set

    /** Giro in corso, da 1 a [totalLaps]. */
    @Volatile
    var lap = 1
        private set

    /** Quanto manca alla fine del giro, da 0 a 1. */
    @Volatile
    var lapProgress = 0f
        private set

    @Volatile
    var finished = false
        private set

    /** Secondi dall'inizio della gara. */
    @Volatile
    var elapsed = 0f
        private set

    /** Quante volte si e sbattuto contro qualcosa. */
    @Volatile
    var bumps = 0
        private set

    @Volatile
    var running = true

    private lateinit var program: GlProgram
    private lateinit var box: Mesh
    private lateinit var pyramid: Mesh
    private lateinit var sphere: Mesh
    private lateinit var cylinder: Mesh
    private lateinit var artist: CarArtist

    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val projView = FloatArray(16)
    private val model = FloatArray(16)
    private val scratch = FloatArray(16)
    private val mvp = FloatArray(16)

    private var carX = 0f
    private var tilt = 0f
    private var speed = 26f
    private var scroll = 0f

    /** Secondi di rallentamento dopo una botta. */
    private var stunned = 0f

    /**
     * Strada percorsa da inizio partita. Serve alla scenografia: ogni casa e
     * ogni albero occupa una posizione fissa sulla strada, e da quella si
     * ricavano sempre la stessa altezza e lo stesso colore. Con un semplice
     * resto, a ogni fotogramma gli edifici cambierebbero faccia.
     */
    private var distance = 0f
    private var sinceSpawn = 0f
    private var lastFrame = 0L
    private val items = ArrayList<Item>()

    override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(track.sky[0], track.sky[1], track.sky[2], 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)
        program = GlProgram()
        box = boxMesh()
        pyramid = pyramidMesh()
        sphere = sphereMesh()
        cylinder = cylinderMesh(12)
        artist = CarArtist(program)
    }

    override fun onSurfaceChanged(unused: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val aspect = width.toFloat() / height.coerceAtLeast(1)
        Matrix.perspectiveM(projection, 0, 52f, aspect, 0.5f, 400f)
    }

    override fun onDrawFrame(unused: GL10?) {
        step()
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        program.use()

        // La telecamera segue la macchina solo per un terzo: inseguendola
        // tutta, la strada sembrerebbe ferma e la sterzata non si vedrebbe.
        Matrix.setLookAtM(
            view, 0,
            carX * 0.35f, 5.0f, 12f,
            carX * 0.6f, 1.3f, -16f,
            0f, 1f, 0f,
        )
        Matrix.multiplyMM(projView, 0, projection, 0, view, 0)

        drawGround()
        drawRoad()
        drawStripes()
        drawPosts()
        drawScenery()
        drawGates()
        drawItems()
        drawCar()
    }

    // --- simulazione -------------------------------------------------------

    private fun step() {
        val now = System.nanoTime()
        if (lastFrame == 0L) {
            lastFrame = now
            return
        }
        val dt = ((now - lastFrame) / 1_000_000_000.0).toFloat().coerceAtMost(0.05f)
        lastFrame = now
        if (!running || finished) return
        elapsed += dt

        val wanted = finger
        if (wanted != null) {
            carX = wanted.coerceIn(-CAR_HALF, CAR_HALF)
        } else {
            carX = (carX + steer * dt * 11f).coerceIn(-CAR_HALF, CAR_HALF)
        }
        // La macchina si inclina quando sterza: e il dettaglio che fa sembrare
        // il modello un oggetto e non un adesivo.
        val target = (if (wanted != null) 0f else steer) * 0.22f
        tilt += (target - tilt) * (dt * 6f).coerceAtMost(1f)

        // Dopo una botta si rallenta per un attimo e poi si riprende: una
        // corsa di cinque minuti non si puo perdere al primo asteroide, e
        // perdere tempo e una punizione che un bambino capisce senza
        // sentirsi dire che ha sbagliato.
        if (stunned > 0f) {
            stunned -= dt
            speed = (speed + dt * 6f).coerceAtMost(22f)
        } else {
            // Ogni giro si va un po' piu forte.
            val top = 46f + (lap - 1) * 5f
            speed = (speed + dt * 2.2f).coerceAtMost(top)
        }
        distance += speed * dt
        scroll = distance % 16f

        val done = (distance / LAP_LENGTH).toInt() + 1
        lapProgress = (distance % LAP_LENGTH) / LAP_LENGTH
        if (done > lap) {
            lap = done
            if (lap > totalLaps) {
                lap = totalLaps
                lapProgress = 1f
                finished = true
                return
            }
            onLap()
        }

        sinceSpawn += dt
        val every = (26f / speed).coerceIn(0.42f, 1.1f)
        if (sinceSpawn >= every) {
            sinceSpawn = 0f
            items.add(
                Item(
                    prize = Random.nextFloat() < 0.58f,
                    x = -CAR_HALF + Random.nextFloat() * CAR_HALF * 2f,
                    z = HORIZON,
                    spin = Random.nextFloat() * 6f,
                ),
            )
        }

        val gone = ArrayList<Item>()
        for (item in items) {
            item.z += speed * dt
            val hit = item.z > -2.2f && item.z < 2.2f && abs(item.x - carX) < 1.45f
            when {
                hit && item.prize -> {
                    score++
                    // Un premio da anche una spintarella.
                    speed = (speed + 4f).coerceAtMost(70f)
                    onPrize()
                    gone.add(item)
                }
                hit -> {
                    bumps++
                    stunned = 1.4f
                    speed = 14f
                    onCrash()
                    gone.add(item)
                }
                item.z > 12f -> gone.add(item)
            }
        }
        items.removeAll(gone)
    }

    // --- disegno -----------------------------------------------------------

    private fun piece(
        color: FloatArray,
        x: Float, y: Float, z: Float,
        sx: Float, sy: Float, sz: Float,
        rotY: Float = 0f,
        rotZ: Float = 0f,
        mesh: Mesh = box,
    ) {
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, x, y, z)
        if (rotY != 0f) Matrix.rotateM(model, 0, rotY, 0f, 1f, 0f)
        if (rotZ != 0f) Matrix.rotateM(model, 0, rotZ, 0f, 0f, 1f)
        Matrix.scaleM(model, 0, sx, sy, sz)
        Matrix.multiplyMM(mvp, 0, projView, 0, model, 0)
        GLES20.glUniformMatrix4fv(program.mvp, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(program.model, 1, false, model, 0)
        GLES20.glUniform4fv(program.color, 1, color, 0)
        mesh.draw(program)
    }

    private fun drawGround() {
        piece(track.ground, 0f, -0.35f, HORIZON / 2f, 600f, 0.5f, -HORIZON + 60f)
    }

    private fun drawRoad() {
        piece(track.road, 0f, -0.05f, HORIZON / 2f, ROAD_HALF * 2f, 0.3f, -HORIZON + 60f)
    }

    private fun drawStripes() {
        var z = 10f - scroll
        while (z > HORIZON) {
            piece(track.line, 0f, 0.07f, z, 0.35f, 0.08f, 5f)
            z -= 16f
        }
    }

    private fun drawPosts() {
        var z = 10f - (distance % 8f)
        var flip = ((distance / 8f).toInt()) % 2
        while (z > HORIZON) {
            val color = if (flip % 2 == 0) track.line else track.post
            piece(color, -ROAD_HALF - 0.7f, 0.45f, z, 0.3f, 1f, 0.3f)
            piece(color, ROAD_HALF + 0.7f, 0.45f, z, 0.3f, 1f, 0.3f)
            z -= 8f
            flip++
        }
    }

    /** Numero sempre uguale per la stessa casella: niente scenografia che balla. */
    private fun hash(slot: Int, salt: Int): Float {
        var value = slot * 374_761_393 + salt * 668_265_263
        value = (value xor (value shr 13)) * 1_274_126_177
        return ((value xor (value shr 16)) and 0x7FFFFF) / 8_388_607f
    }

    /**
     * Quello che si vede ai lati. Ogni pista ha il suo paesaggio: una citta
     * senza case non e una citta, e senza qualcosa di alto che passa la
     * velocita non si sente.
     */
    private fun drawScenery() {
        drawBigPlanet()
        val period = when (track.scenery) {
            Scenery.COLONIA -> 20f
            Scenery.LUNA -> 16f
            Scenery.MARTE -> 15f
            Scenery.ANELLI -> 26f
        }
        val firstSlot = ((distance - 14f) / period).toInt()
        val lastSlot = ((distance - HORIZON) / period).toInt()
        for (slot in firstSlot..lastSlot) {
            val z = distance - slot * period
            for (side in intArrayOf(-1, 1)) {
                val salt = if (side < 0) 1 else 2
                when (track.scenery) {
                    Scenery.COLONIA -> colonia(slot, salt, side, z)
                    Scenery.LUNA -> luna(slot, salt, side, z)
                    Scenery.MARTE -> marte(slot, salt, side, z)
                    Scenery.ANELLI -> asteroide(slot, salt, side, z)
                }
            }
        }
    }

    /**
     * Il pianeta grande all'orizzonte. Non scorre con la pista: sta fermo
     * lontanissimo, come succede davvero, ed e quello che dice al bambino
     * dove si trova senza bisogno di una scritta.
     */
    private fun drawBigPlanet() {
        piece(track.accent, -46f, 30f, HORIZON - 30f, 70f, 70f, 70f, mesh = sphere)
        if (track.scenery == Scenery.ANELLI) {
            piece(track.prize, -46f, 30f, HORIZON - 30f, 128f, 1.2f, 128f, rotZ = 14f, mesh = cylinder)
        }
    }

    /** Colonia: cupole abitate, torri e antenne. */
    private fun colonia(slot: Int, salt: Int, side: Int, z: Float) {
        val away = ROAD_HALF + 6f + hash(slot, salt) * 6f
        val x = away * side
        if (hash(slot, salt + 60) > 0.45f) {
            // Cupola, con la base illuminata.
            val size = 6f + hash(slot, salt + 10) * 6f
            piece(track.accent, x, 0f, z, size, size, size, mesh = sphere)
            piece(track.line, x, 0.4f, z, size * 0.92f, 0.5f, size * 0.92f, mesh = cylinder)
        } else {
            // Torre con tre fasce di oblo e l'antenna in cima.
            val height = 8f + hash(slot, salt + 10) * 14f
            val width = 2.6f + hash(slot, salt + 20) * 2f
            piece(track.accent, x, height / 2f, z, width, height, width)
            val face = (away - width / 2f - 0.06f) * side
            for (level in 1..3) {
                piece(track.line, face, height * level / 4f, z, 0.12f, height * 0.07f, width * 0.6f)
            }
            piece(track.post, x, height + 1.6f, z, 0.18f, 3.2f, 0.18f)
            piece(track.prize, x, height + 3.3f, z, 0.7f, 0.7f, 0.7f, mesh = sphere)
        }
        // Piattaforma su cui poggia tutto: nello spazio non c'e un prato.
        piece(track.road, x, 0.1f, z, 14f, 0.3f, 12f)
    }

    /** Luna: crateri e massi. */
    private fun luna(slot: Int, salt: Int, side: Int, z: Float) {
        val away = ROAD_HALF + 3f + hash(slot, salt) * 10f
        val x = away * side
        if (hash(slot, salt + 40) > 0.5f) {
            val size = 5f + hash(slot, salt + 10) * 9f
            // Cratere: il bordo chiaro e il fondo scuro.
            piece(track.line, x, 0.06f, z, size, 0.3f, size, mesh = cylinder)
            piece(track.accent, x, 0.12f, z, size * 0.72f, 0.3f, size * 0.72f, mesh = cylinder)
        } else {
            val size = 1.2f + hash(slot, salt + 10) * 2.6f
            piece(track.accent, x, size * 0.35f, z, size, size * 0.8f, size, mesh = sphere)
        }
    }

    /** Marte: guglie di roccia e archi scavati dal vento. */
    private fun marte(slot: Int, salt: Int, side: Int, z: Float) {
        val away = ROAD_HALF + 3.5f + hash(slot, salt) * 9f
        val x = away * side
        val height = 4f + hash(slot, salt + 10) * 12f
        val width = 2.5f + hash(slot, salt + 20) * 4f
        piece(track.accent, x, height / 2f, z, width, height, width, mesh = pyramid)
        if (hash(slot, salt + 30) > 0.7f) {
            // Un masso appoggiato accanto, per rompere la fila di guglie.
            piece(track.post, x + 3f * side, 1f, z + 3f, 3f, 2f, 3f, mesh = sphere)
        }
    }

    /** Anelli: asteroidi che galleggiano a varie altezze. */
    private fun asteroide(slot: Int, salt: Int, side: Int, z: Float) {
        val away = ROAD_HALF + 5f + hash(slot, salt) * 16f
        val size = 1.5f + hash(slot, salt + 10) * 5f
        val height = 1f + hash(slot, salt + 20) * 16f
        piece(track.accent, away * side, height, z, size, size * 0.8f, size, mesh = sphere)
        if (hash(slot, salt + 30) > 0.72f) {
            piece(track.post, away * side, height + size, z, size * 0.5f, size * 0.5f, size * 0.5f, mesh = sphere)
        }
    }

    /**
     * Il traguardo di ogni giro: due piloni e una traversa che si attraversa.
     * Senza qualcosa da attraversare, "giro 2 di 5" e solo un numero.
     */
    private fun drawGates() {
        for (number in 1..totalLaps) {
            val z = distance - number * LAP_LENGTH
            if (z < HORIZON || z > 12f) continue
            val colour = if (number == totalLaps) track.prize else track.line
            piece(colour, -ROAD_HALF - 0.9f, 3f, z, 0.6f, 6f, 0.6f)
            piece(colour, ROAD_HALF + 0.9f, 3f, z, 0.6f, 6f, 0.6f)
            piece(colour, 0f, 6.2f, z, ROAD_HALF * 2f + 2.4f, 0.7f, 0.5f)
            // Striscia a terra, cosi si vede anche guardando la pista.
            piece(colour, 0f, 0.09f, z, ROAD_HALF * 2f, 0.1f, 1.2f)
        }
    }

    private fun drawItems() {
        for (item in items) {
            if (item.prize) {
                val turn = (scroll * 22f + item.spin * 40f) % 360f
                piece(track.prize, item.x, 1.1f, item.z, 1.1f, 1.1f, 1.1f, rotY = turn)
            } else {
                piece(track.obstacle, item.x, 0.9f, item.z, 1.8f, 1.8f, 1.8f, mesh = pyramid)
            }
        }
    }

    private fun drawCar() {
        artist.draw(
            projView = projView,
            style = style,
            color = carColor,
            x = carX,
            z = 0f,
            // La macchina si corica un poco nella sterzata e punta il muso
            // dove sta andando: due dettagli piccoli che la fanno sembrare un
            // oggetto con un peso invece di un'immagine che scivola.
            heading = -tilt * 14f,
            roll = tilt * 26f,
            steerAngle = tilt * 70f,
        )
    }
}
