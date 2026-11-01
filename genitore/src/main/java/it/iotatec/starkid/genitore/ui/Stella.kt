package it.iotatec.starkid.genitore.ui

// Generato da design/genera.sh: non si modifica a mano.
//
// Il path della stella del marchio, lo stesso carattere per carattere di
// server/web/*.svg e dei vector drawable. Compose lo legge con PathParser,
// cosi la stella dentro la app non puo diventare un disegno diverso da
// quella della favicon.
//
// Lo spazio e quello del generatore: ingombro centrato sullo zero, largo
// 172.648 e alto 164.610.
object Marchio {
    const val PERCORSO_STELLA: String =
        "M-8.111 -72.887Q0 -91.723 8.111 -72.887L21.09 -42.751Q27.038 -28.938 42.013 -27.549L74.685 -24.519Q95.106 -22.625 79.699 -9.09L55.047 12.566Q43.749 22.492 47.055 37.163L54.27 69.173Q58.779 89.179 41.145 78.708L12.931 61.955Q0 54.277 -12.931 61.955L-41.145 78.708Q-58.779 89.179 -54.27 69.173L-47.055 37.163Q-43.749 22.492 -55.047 12.566L-79.699 -9.09Q-95.106 -22.625 -74.685 -24.519L-42.013 -27.549Q-27.038 -28.938 -21.09 -42.751Z"

    /** Mezzo lato del riquadro quadrato che contiene la stella. */
    const val MEZZO: Float = 86.324f

    /** Lato del riquadro quadrato. */
    const val LATO: Float = 172.648f

    val ORO_CHIARO = 0xFFFFE48F
    val ORO = 0xFFFFC846
    val ORO_SCURO = 0xFFF09A2B
}
