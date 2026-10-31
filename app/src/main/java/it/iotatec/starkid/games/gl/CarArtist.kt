package it.iotatec.starkid.games.gl

import android.opengl.GLES20
import android.opengl.Matrix

/** I tre kart fra cui si sceglie. */
enum class CarStyle(val label: String) {
    KART("Kart"),
    RALLY("Da sterrato"),
    FURGONCINO("Furgoncino"),
}

/**
 * Disegna il kart, lo stesso nel garage e in pista.
 *
 * E un kart e non una macchina: telaio basso e aperto, ruote grosse che
 * sporgono agli angoli, e sopra un pilota col casco. Le proporzioni sono
 * volutamente tozze — passo corto, ruote grandi, casco tondo — perche e
 * quello che rende un mezzo simpatico invece che realistico.
 *
 * Niente modelli da caricare: scocca generata per sezioni, ruote cilindriche,
 * casco e testa sfere. Il colore e un parametro, ed e per questo che se ne
 * possono scegliere ventiquattro combinazioni.
 */
class CarArtist(private val program: GlProgram) {

    // Pianale del kart: basso, largo in mezzo, a punta davanti.
    private val chassis = loftMesh(
        listOf(
            Section(-1.80f, 0.22f, 0.26f, 0.44f),
            Section(-1.45f, 0.40f, 0.22f, 0.48f),
            Section(-0.95f, 0.54f, 0.20f, 0.50f),
            Section(-0.20f, 0.62f, 0.18f, 0.54f),
            Section(0.55f, 0.64f, 0.18f, 0.58f),
            Section(1.25f, 0.60f, 0.20f, 0.62f),
            Section(1.70f, 0.46f, 0.24f, 0.60f),
            Section(1.92f, 0.28f, 0.28f, 0.54f),
        ),
        around = 14,
    )

    // Muso a goccia, montato davanti: e quello che da la faccia al kart.
    private val nose = loftMesh(
        listOf(
            Section(-0.34f, 0.26f, -0.16f, 0.16f),
            Section(-0.10f, 0.46f, -0.20f, 0.22f),
            Section(0.20f, 0.52f, -0.22f, 0.24f),
            Section(0.34f, 0.44f, -0.20f, 0.20f),
        ),
        around = 14,
    )

    private val wheel = cylinderMesh(20)
    private val rim = cylinderMesh(12)
    private val ball = sphereMesh(10, 14)
    private val panel = boxMesh()

    private val car = FloatArray(16)
    private val local = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)

    private val tyre = floatArrayOf(0.11f, 0.11f, 0.13f, 1f)
    private val metal = floatArrayOf(0.80f, 0.82f, 0.86f, 1f)
    private val dark = floatArrayOf(0.20f, 0.21f, 0.25f, 1f)
    private val visor = floatArrayOf(0.16f, 0.26f, 0.38f, 1f)
    private val skin = floatArrayOf(0.98f, 0.80f, 0.64f, 1f)
    private val suit = floatArrayOf(0.96f, 0.96f, 0.98f, 1f)
    private val lamp = floatArrayOf(1f, 0.96f, 0.76f, 1f)

    /**
     * Disegna un pezzo in **coordinate del kart**, non del mondo: la sua
     * posizione viene moltiplicata per la trasformazione del kart intero,
     * cosi quando il kart gira, ruote e pilota girano con lui. Ruotando ogni
     * pezzo per conto suo, il kart si smonterebbe in volo.
     */
    private fun part(
        projView: FloatArray,
        mesh: Mesh,
        color: FloatArray,
        tx: Float, ty: Float, tz: Float,
        sx: Float, sy: Float, sz: Float,
        rx: Float = 0f, ry: Float = 0f, rz: Float = 0f,
    ) {
        Matrix.setIdentityM(local, 0)
        Matrix.translateM(local, 0, tx, ty, tz)
        if (rz != 0f) Matrix.rotateM(local, 0, rz, 0f, 0f, 1f)
        if (ry != 0f) Matrix.rotateM(local, 0, ry, 0f, 1f, 0f)
        if (rx != 0f) Matrix.rotateM(local, 0, rx, 1f, 0f, 0f)
        Matrix.scaleM(local, 0, sx, sy, sz)
        Matrix.multiplyMM(model, 0, car, 0, local, 0)
        Matrix.multiplyMM(mvp, 0, projView, 0, model, 0)
        GLES20.glUniformMatrix4fv(program.mvp, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(program.model, 1, false, model, 0)
        GLES20.glUniform4fv(program.color, 1, color, 0)
        mesh.draw(program)
    }

    /**
     * @param roll inclinazione in gradi nella sterzata
     * @param steerAngle rotazione delle ruote anteriori e del volante
     */
    fun draw(
        projView: FloatArray,
        style: CarStyle,
        color: FloatArray,
        x: Float,
        z: Float,
        heading: Float = 0f,
        roll: Float = 0f,
        steerAngle: Float = 0f,
    ) {
        Matrix.setIdentityM(car, 0)
        Matrix.translateM(car, 0, x, 0f, z)
        if (heading != 0f) Matrix.rotateM(car, 0, heading, 0f, 1f, 0f)
        if (roll != 0f) Matrix.rotateM(car, 0, roll, 0f, 0f, 1f)

        val lift = if (style == CarStyle.RALLY) 0.26f else 0f
        val frontRadius = if (style == CarStyle.RALLY) 0.42f else 0.33f
        val rearRadius = if (style == CarStyle.RALLY) 0.52f else 0.44f

        // --- telaio --------------------------------------------------------
        part(projView, chassis, color, 0f, lift, 0f, 1f, 1f, 1f)
        part(projView, nose, color, 0f, 0.42f + lift, -1.92f, 1f, 1f, 1f)

        // Fiancate: due casse laterali, come sui kart veri.
        for (side in listOf(-1f, 1f)) {
            part(projView, panel, color, 0.80f * side, 0.42f + lift, 0.25f, 0.34f, 0.34f, 1.55f)
        }

        // Paraurti davanti e dietro, in tinta piu scura.
        part(projView, panel, dark, 0f, 0.36f + lift, -2.10f, 1.30f, 0.16f, 0.18f)
        part(projView, panel, dark, 0f, 0.42f + lift, 2.02f, 1.20f, 0.18f, 0.18f)

        // --- sedile e pilota ------------------------------------------------
        part(projView, panel, dark, 0f, 0.62f + lift, 0.72f, 0.70f, 0.14f, 0.72f)
        part(projView, panel, dark, 0f, 0.95f + lift, 1.12f, 0.74f, 0.62f, 0.16f, rx = -12f)

        // Busto, testa, casco: la parte che lo rende simpatico.
        part(projView, ball, suit, 0f, 1.02f + lift, 0.74f, 0.66f, 0.72f, 0.56f)
        part(projView, ball, skin, 0f, 1.40f + lift, 0.66f, 0.38f, 0.40f, 0.38f)
        part(projView, ball, color, 0f, 1.46f + lift, 0.68f, 0.54f, 0.52f, 0.54f)
        part(projView, panel, visor, 0f, 1.42f + lift, 0.44f, 0.40f, 0.17f, 0.10f)

        // Braccia verso il volante.
        for (side in listOf(-1f, 1f)) {
            part(projView, panel, suit, 0.26f * side, 1.00f + lift, 0.30f, 0.16f, 0.16f, 0.80f, rx = 16f)
        }
        // Volante: un disco inclinato verso il pilota.
        part(projView, rim, dark, 0f, 1.00f + lift, -0.10f, 0.07f, 0.46f, 0.46f, ry = 90f, rx = -28f)

        // --- motore e alettone ----------------------------------------------
        part(projView, panel, dark, 0.62f, 0.66f + lift, 1.40f, 0.36f, 0.40f, 0.60f)
        part(projView, rim, metal, 0.62f, 0.96f + lift, 1.40f, 0.12f, 0.18f, 0.18f, ry = 90f)
        for (side in listOf(-1f, 1f)) {
            part(projView, panel, dark, 0.38f * side, 1.00f + lift, 1.86f, 0.10f, 0.52f, 0.10f)
        }
        part(projView, panel, color, 0f, 1.28f + lift, 1.86f, 1.26f, 0.09f, 0.36f)

        if (style == CarStyle.FURGONCINO) {
            // Un cassoncino al posto dell'alettone.
            part(projView, panel, color, 0f, 1.00f + lift, 1.50f, 1.15f, 0.75f, 0.95f)
            part(projView, panel, dark, 0f, 1.38f + lift, 1.50f, 1.19f, 0.10f, 0.99f)
        }

        // --- ruote -----------------------------------------------------------
        val frontX = 0.92f
        val rearX = if (style == CarStyle.RALLY) 1.04f else 0.98f
        for (side in listOf(-1f, 1f)) {
            val front = frontRadius * 2f
            val rear = rearRadius * 2f
            part(projView, wheel, tyre, frontX * side, frontRadius, -1.42f, 0.34f, front, front, ry = steerAngle)
            part(projView, rim, metal, (frontX + 0.13f) * side, frontRadius, -1.42f, 0.10f, front * 0.5f, front * 0.5f, ry = steerAngle)
            part(projView, wheel, tyre, rearX * side, rearRadius, 1.38f, 0.46f, rear, rear)
            part(projView, rim, metal, (rearX + 0.18f) * side, rearRadius, 1.38f, 0.12f, rear * 0.5f, rear * 0.5f)
        }

        // Due faretti tondi sul muso.
        for (side in listOf(-1f, 1f)) {
            part(projView, ball, lamp, 0.30f * side, 0.46f + lift, -2.16f, 0.26f, 0.26f, 0.14f)
        }
    }
}
