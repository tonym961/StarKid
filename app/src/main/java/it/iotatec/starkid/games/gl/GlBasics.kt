package it.iotatec.starkid.games.gl

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Il minimo indispensabile per disegnare in tre dimensioni: un programma di
 * shader e una manciata di solidi.
 *
 * Non c'e nessun motore 3D di mezzo e nessun modello da caricare: le forme si
 * costruiscono a mano da cubi e piramidi. Un motore porterebbe decine di
 * megabyte di libreria nativa e vorrebbe comunque i modelli delle macchine in
 * un file; qui una macchina sono sei scatole e il suo colore e un parametro,
 * che e proprio quello che serve per farne scegliere ventiquattro.
 */

private const val VERTEX_SHADER = """
uniform mat4 uMvp;
uniform mat4 uModel;
attribute vec4 aPosition;
attribute vec3 aNormal;
varying float vShade;
void main() {
    // Luce direzionale fissa, un po' dall'alto e da destra. La parte
    // ambientale non scende sotto 0.45, cosi nessuna faccia resta nera.
    vec3 world = normalize((uModel * vec4(aNormal, 0.0)).xyz);
    vec3 light = normalize(vec3(0.35, 0.85, 0.40));
    vShade = 0.45 + 0.55 * max(dot(world, light), 0.0);
    gl_Position = uMvp * aPosition;
}
"""

private const val FRAGMENT_SHADER = """
precision mediump float;
uniform vec4 uColor;
varying float vShade;
void main() {
    gl_FragColor = vec4(uColor.rgb * vShade, uColor.a);
}
"""

/** Il programma di disegno, con i suoi riferimenti gia risolti. */
class GlProgram {
    val handle: Int = link()
    val position = GLES20.glGetAttribLocation(handle, "aPosition")
    val normal = GLES20.glGetAttribLocation(handle, "aNormal")
    val mvp = GLES20.glGetUniformLocation(handle, "uMvp")
    val model = GLES20.glGetUniformLocation(handle, "uModel")
    val color = GLES20.glGetUniformLocation(handle, "uColor")

    fun use() = GLES20.glUseProgram(handle)

    private fun link(): Int {
        val vertex = compile(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragment = compile(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        return GLES20.glCreateProgram().also { program ->
            GLES20.glAttachShader(program, vertex)
            GLES20.glAttachShader(program, fragment)
            GLES20.glLinkProgram(program)
        }
    }

    private fun compile(type: Int, source: String): Int =
        GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
        }
}

/**
 * Un solido: posizioni e normali interlacciate, pronte per glDrawArrays.
 * Ogni solido si disegna tante volte con matrici e colori diversi, quindi ne
 * bastano due per tutta la scena.
 */
class Mesh(data: FloatArray) {
    private val buffer: FloatBuffer = ByteBuffer
        .allocateDirect(data.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .put(data)
        .also { it.position(0) }

    private val vertices = data.size / 6

    fun draw(program: GlProgram) {
        buffer.position(0)
        GLES20.glVertexAttribPointer(program.position, 3, GLES20.GL_FLOAT, false, 24, buffer)
        GLES20.glEnableVertexAttribArray(program.position)
        buffer.position(3)
        GLES20.glVertexAttribPointer(program.normal, 3, GLES20.GL_FLOAT, false, 24, buffer)
        GLES20.glEnableVertexAttribArray(program.normal)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertices)
    }
}

/** Cubo di lato 1 centrato nell'origine: la scatola con cui e fatto tutto. */
fun boxMesh(): Mesh {
    val h = 0.5f
    val faces = listOf(
        // (normale, quattro angoli in senso antiorario)
        floatArrayOf(0f, 0f, 1f) to listOf(
            floatArrayOf(-h, -h, h), floatArrayOf(h, -h, h),
            floatArrayOf(h, h, h), floatArrayOf(-h, h, h),
        ),
        floatArrayOf(0f, 0f, -1f) to listOf(
            floatArrayOf(h, -h, -h), floatArrayOf(-h, -h, -h),
            floatArrayOf(-h, h, -h), floatArrayOf(h, h, -h),
        ),
        floatArrayOf(1f, 0f, 0f) to listOf(
            floatArrayOf(h, -h, h), floatArrayOf(h, -h, -h),
            floatArrayOf(h, h, -h), floatArrayOf(h, h, h),
        ),
        floatArrayOf(-1f, 0f, 0f) to listOf(
            floatArrayOf(-h, -h, -h), floatArrayOf(-h, -h, h),
            floatArrayOf(-h, h, h), floatArrayOf(-h, h, -h),
        ),
        floatArrayOf(0f, 1f, 0f) to listOf(
            floatArrayOf(-h, h, h), floatArrayOf(h, h, h),
            floatArrayOf(h, h, -h), floatArrayOf(-h, h, -h),
        ),
        floatArrayOf(0f, -1f, 0f) to listOf(
            floatArrayOf(-h, -h, -h), floatArrayOf(h, -h, -h),
            floatArrayOf(h, -h, h), floatArrayOf(-h, -h, h),
        ),
    )
    val data = ArrayList<Float>(36 * 6)
    for ((normal, corners) in faces) {
        for (index in listOf(0, 1, 2, 0, 2, 3)) {
            data.addAll(corners[index].toList())
            data.addAll(normal.toList())
        }
    }
    return Mesh(data.toFloatArray())
}

/**
 * Una sezione della scocca: a che punto della lunghezza si trova, quanto e
 * larga meta macchina li, e fra quali altezze sta.
 */
data class Section(val z: Float, val halfWidth: Float, val bottom: Float, val top: Float)

/**
 * Costruisce un solido passando per le sezioni date, come una carena.
 *
 * E questo che distingue una macchina da una scatola: la larghezza e
 * l'altezza cambiano lungo la lunghezza, quindi il muso si abbassa, il
 * tetto si alza sull'abitacolo e la coda rientra. Gli spigoli sono
 * arrotondati da una supercurva, cosi la carrozzeria riflette la luce come
 * una superficie curva invece che a facce piatte.
 */
fun loftMesh(sections: List<Section>, around: Int = 14): Mesh {
    require(sections.size >= 2) { "servono almeno due sezioni" }

    fun ring(section: Section): List<FloatArray> = List(around) { index ->
        val angle = 2.0 * Math.PI * index / around
        val cos = kotlin.math.cos(angle)
        val sin = kotlin.math.sin(angle)
        // Supercurva: con esponente 3 gli angoli restano pieni ma smussati,
        // a differenza di un'ellisse che farebbe una macchina a sigaro.
        val power = 3.0
        val fx = kotlin.math.sign(cos) * Math.pow(abs(cos), 2.0 / power)
        val fy = kotlin.math.sign(sin) * Math.pow(abs(sin), 2.0 / power)
        val centreY = (section.top + section.bottom) / 2f
        val radiusY = (section.top - section.bottom) / 2f
        floatArrayOf(
            (fx * section.halfWidth).toFloat(),
            centreY + (fy * radiusY).toFloat(),
            section.z,
        )
    }

    val rings = sections.map { ring(it) }
    val data = ArrayList<Float>()

    fun triangle(a: FloatArray, b: FloatArray, c: FloatArray) {
        val ux = b[0] - a[0]; val uy = b[1] - a[1]; val uz = b[2] - a[2]
        val vx = c[0] - a[0]; val vy = c[1] - a[1]; val vz = c[2] - a[2]
        val nx = uy * vz - uz * vy
        val ny = uz * vx - ux * vz
        val nz = ux * vy - uy * vx
        for (corner in listOf(a, b, c)) {
            data.addAll(corner.toList())
            data.addAll(listOf(nx, ny, nz))
        }
    }

    for (index in 0 until rings.size - 1) {
        val front = rings[index]
        val back = rings[index + 1]
        for (step in 0 until around) {
            val next = (step + 1) % around
            triangle(front[step], back[step], back[next])
            triangle(front[step], back[next], front[next])
        }
    }

    // Tappi davanti e dietro, a ventaglio dal centro della sezione.
    val first = rings.first()
    val last = rings.last()
    val firstCentre = floatArrayOf(0f, (sections.first().top + sections.first().bottom) / 2f, sections.first().z)
    val lastCentre = floatArrayOf(0f, (sections.last().top + sections.last().bottom) / 2f, sections.last().z)
    for (step in 0 until around) {
        val next = (step + 1) % around
        triangle(firstCentre, first[next], first[step])
        triangle(lastCentre, last[step], last[next])
    }

    return Mesh(data.toFloatArray())
}

/**
 * Cilindro di raggio 0,5 e lunghezza 1 con l'asse lungo X: una ruota.
 * Quattro scatole nere agli angoli non sono ruote, e si vede.
 */
fun cylinderMesh(segments: Int = 16): Mesh {
    val data = ArrayList<Float>()
    val half = 0.5f

    fun vertex(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float) {
        data.addAll(listOf(x, y, z, nx, ny, nz))
    }

    for (step in 0 until segments) {
        val a = 2.0 * Math.PI * step / segments
        val b = 2.0 * Math.PI * (step + 1) / segments
        val ya = (kotlin.math.cos(a) * half).toFloat()
        val za = (kotlin.math.sin(a) * half).toFloat()
        val yb = (kotlin.math.cos(b) * half).toFloat()
        val zb = (kotlin.math.sin(b) * half).toFloat()

        // Fascia esterna, con le normali che puntano in fuori dall'asse.
        vertex(-half, ya, za, 0f, ya, za)
        vertex(half, ya, za, 0f, ya, za)
        vertex(half, yb, zb, 0f, yb, zb)
        vertex(-half, ya, za, 0f, ya, za)
        vertex(half, yb, zb, 0f, yb, zb)
        vertex(-half, yb, zb, 0f, yb, zb)

        // I due fianchi.
        vertex(half, 0f, 0f, 1f, 0f, 0f)
        vertex(half, ya, za, 1f, 0f, 0f)
        vertex(half, yb, zb, 1f, 0f, 0f)
        vertex(-half, 0f, 0f, -1f, 0f, 0f)
        vertex(-half, yb, zb, -1f, 0f, 0f)
        vertex(-half, ya, za, -1f, 0f, 0f)
    }
    return Mesh(data.toFloatArray())
}

/** Sfera di raggio 0,5: pianeti, chiome degli alberi, dune. */
fun sphereMesh(rings: Int = 10, segments: Int = 14): Mesh {
    val data = ArrayList<Float>()

    fun point(ring: Int, segment: Int): FloatArray {
        val phi = Math.PI * ring / rings
        val theta = 2.0 * Math.PI * segment / segments
        val x = (kotlin.math.sin(phi) * kotlin.math.cos(theta) * 0.5).toFloat()
        val y = (kotlin.math.cos(phi) * 0.5).toFloat()
        val z = (kotlin.math.sin(phi) * kotlin.math.sin(theta) * 0.5).toFloat()
        return floatArrayOf(x, y, z)
    }

    fun put(p: FloatArray) {
        data.addAll(p.toList())
        // Su una sfera centrata nell'origine la normale e la posizione
        // stessa: niente da calcolare.
        data.addAll(listOf(p[0] * 2f, p[1] * 2f, p[2] * 2f))
    }

    for (ring in 0 until rings) {
        for (segment in 0 until segments) {
            val a = point(ring, segment)
            val b = point(ring + 1, segment)
            val c = point(ring + 1, segment + 1)
            val d = point(ring, segment + 1)
            put(a); put(b); put(c)
            put(a); put(c); put(d)
        }
    }
    return Mesh(data.toFloatArray())
}

private fun abs(value: Double) = kotlin.math.abs(value)

/** Piramide a base quadrata, alta 1: i birilli e gli alberi della pista. */
fun pyramidMesh(): Mesh {
    val h = 0.5f
    val top = floatArrayOf(0f, 0.5f, 0f)
    val base = listOf(
        floatArrayOf(-h, -0.5f, h),
        floatArrayOf(h, -0.5f, h),
        floatArrayOf(h, -0.5f, -h),
        floatArrayOf(-h, -0.5f, -h),
    )
    val data = ArrayList<Float>()

    fun triangle(a: FloatArray, b: FloatArray, c: FloatArray) {
        // Normale dal prodotto vettoriale dei due lati: le facce inclinate
        // devono prendere la luce in modo diverso, se no la piramide sembra
        // un triangolo piatto.
        val ux = b[0] - a[0]; val uy = b[1] - a[1]; val uz = b[2] - a[2]
        val vx = c[0] - a[0]; val vy = c[1] - a[1]; val vz = c[2] - a[2]
        val nx = uy * vz - uz * vy
        val ny = uz * vx - ux * vz
        val nz = ux * vy - uy * vx
        for (corner in listOf(a, b, c)) {
            data.addAll(corner.toList())
            data.addAll(listOf(nx, ny, nz))
        }
    }

    for (index in 0 until 4) {
        triangle(base[index], base[(index + 1) % 4], top)
    }
    triangle(base[0], base[2], base[1])
    triangle(base[0], base[3], base[2])
    return Mesh(data.toFloatArray())
}
