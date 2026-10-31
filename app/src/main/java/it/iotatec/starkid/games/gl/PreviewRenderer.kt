package it.iotatec.starkid.games.gl

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * La macchina del garage: lo stesso modello della pista, che gira piano su se
 * stesso. Il bambino sceglie guardando la macchina vera, non un'icona.
 */
class PreviewRenderer(
    style: CarStyle,
    color: FloatArray,
    private val background: FloatArray,
) : GLSurfaceView.Renderer {

    @Volatile
    var style: CarStyle = style

    @Volatile
    var color: FloatArray = color

    private lateinit var program: GlProgram
    private lateinit var artist: CarArtist
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val projView = FloatArray(16)
    private var angle = 0f
    private var lastFrame = 0L

    override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(background[0], background[1], background[2], 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)
        program = GlProgram()
        artist = CarArtist(program)
    }

    override fun onSurfaceChanged(unused: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        Matrix.perspectiveM(projection, 0, 42f, width.toFloat() / height.coerceAtLeast(1), 0.5f, 60f)
    }

    override fun onDrawFrame(unused: GL10?) {
        val now = System.nanoTime()
        if (lastFrame != 0L) {
            val dt = ((now - lastFrame) / 1_000_000_000.0).toFloat().coerceAtMost(0.05f)
            // Un giro ogni venti secondi: abbastanza lento da poter guardare
            // una fiancata senza rincorrerla.
            angle = (angle + dt * 18f) % 360f
        }
        lastFrame = now

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        program.use()
        // Vicino e un po' dall'alto: il kart deve riempire il riquadro, se no
        // nel garage si sceglie un puntino.
        Matrix.setLookAtM(view, 0, 0f, 2.3f, 5.6f, 0f, 0.75f, 0f, 0f, 1f, 0f)
        Matrix.multiplyMM(projView, 0, projection, 0, view, 0)
        artist.draw(projView, style, color, x = 0f, z = 0f, heading = angle)
    }
}
