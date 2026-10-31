package it.iotatec.starkid.games

import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.iotatec.starkid.data.SKIN_SPACE
import it.iotatec.starkid.data.WorldMap
import it.iotatec.starkid.ui.LocalSkin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.random.Random
import android.graphics.Paint as NativePaint

private const val MIN_ZOOM = 1f

/**
 * Il massimo ingrandimento. Tarato sui dati, non a sentimento: i contorni sono
 * quantizzati a centesimi di grado, cioe circa un chilometro, e a questo
 * ingrandimento un chilometro vale piu o meno un pixel. Andare oltre
 * mostrerebbe la griglia su cui sono stati arrotondati i punti.
 */
private const val MAX_ZOOM = 24f

/**
 * Lo spazio del mondo e largo 1 e alto 0,5 (vedi WorldMap): il mondo e largo il
 * doppio di quanto e alto, come nella proiezione vera.
 */
private const val WORLD_W = 1f
private const val WORLD_H = 0.5f

/**
 * Il tratto piu sottile che esista, e di gran lunga il piu economico.
 *
 * Spessore zero vuol dire "un pixel dello schermo", e il disegno lo tratta
 * come un caso a parte: non costruisce la geometria del tratto, traccia la
 * linea e basta. Un tratto con uno spessore vero, su un piano ingrandito
 * ventiquattro volte, va ricostruito vertice per vertice a ogni fotogramma -
 * ed e li che se ne andavano i millisecondi. Per i confini di una mappa un
 * pixel e anche esattamente quello che si vuole: resta sottile uguale a
 * qualunque ingrandimento, come su un atlante stampato.
 */
private val Capello = Stroke(width = 0f)

private enum class MapMode { ESPLORA, INDOVINA }

/**
 * I colori di una mappa.
 *
 * Due tavolozze, non una: in tema spaziale una mappa da atlante con il mare
 * azzurro chiaro sarebbe una finestra bianca in mezzo al cielo notturno. I sei
 * colori della terra servono a disegnare una mappa politica come quelle degli
 * atlanti, dove due paesi confinanti non hanno mai la stessa tinta - quale
 * paese prende quale numero lo decide il file, con una colorazione calcolata
 * sui confini veri.
 */
private class MapPalette(
    val seaTop: Color,
    val seaBottom: Color,
    val land: List<Color>,
    val coastShadow: Color,
    val border: Color,
    val lake: Color,
    val lakeBorder: Color,
    val graticule: Color,
    val label: Color,
    val labelHalo: Color,
    val cityLabel: Color,
    val cityHalo: Color,
    val pin: Color,
    val pinRing: Color,
)

/**
 * Tavolozza da atlante. Tinte spente e calde: su una mappa politica i colori
 * servono a distinguere i paesi, non a farsi notare, se no il mare e i
 * segnaposti rossi non si leggono piu.
 *
 * Due regole le ha imposte la revisione, dopo averle misurate:
 *
 * **Nessuna tinta puo somigliare all'acqua.** La prima versione aveva un
 * azzurrino (C2D6DA) a distanza 8 dal mare: ce l'avevano quaranta paesi, di
 * cui diciassette isole, e un'isola che sembra una chiazza di mare in un gioco
 * dove il bambino deve toccarla non e brutta, e ingiocabile. Qui i sei colori
 * sono tutti caldi o neutri, nessuno tende al blu.
 *
 * **Due tinte vicine all'occhio contano come la stessa tinta.** Prima il
 * sabbia e il grano distavano 6,6: indici diversi, stesso colore per chi
 * guarda, e ventisette confini sparivano. Chi assegna i colori nel file tratta
 * come conflitto anche le coppie percettivamente vicine, non solo gli indici
 * uguali, e lo fa su QUESTI sei valori: cambiarli qui senza rigenerare il file
 * puo rimettere due paesi confinanti sulla stessa tinta.
 */
private val AtlasGiorno = MapPalette(
    // Il mare e SCURO, e questa e la terza correzione arrivata da una misura.
    // Con l'azzurro chiaro di prima (L* 86 in alto, 77 in basso) le sei terre
    // stavano fra L* 72 e 90, cioe DENTRO la fascia di luminosita del mare: a
    // distinguerli restava solo la tinta, e le nazioni fatte di isole piccole
    // - Maldive, Bahamas, Vanuatu, Anguilla - sbiadivano nell'acqua. Di notte
    // non succedeva, perche li ogni terra sta venti punti di L* sopra il mare.
    // Abbassando solo il mare (L* 62 e 57) tutte e sei le terre guadagnano
    // insieme, e i colori della terra non si toccano: quelli sono gia stati
    // assegnati dentro mondo.bin, e cambiarli vorrebbe dire rigenerarlo.
    seaTop = Color(0xFF6D9CC5),
    seaBottom = Color(0xFF5C8DB8),
    land = listOf(
        Color(0xFFE3BE8E), // sabbia
        Color(0xFFBBD3A2), // salvia
        Color(0xFFF2E3A0), // giallo pallido
        Color(0xFFD8BBD6), // lilla
        Color(0xFFD6CCBE), // grigio caldo
        Color(0xFFE3A096), // rosa
    ),
    coastShadow = Color(0x4D1B3C56),
    border = Color(0xFF8A7F6B),
    // Il lago sta vicinissimo al mare di proposito: e acqua, e deve leggersi
    // come acqua. A staccarlo dalla terra ci pensano la luminosita (sette
    // punti di L* sotto la terra piu scura) e il suo bordo.
    lake = Color(0xFF6FA2CB),
    lakeBorder = Color(0xFF4A7493),
    graticule = Color(0x26FFFFFF),
    label = Color(0xFF4A4034),
    labelHalo = Color(0xCCFFFFFF),
    cityLabel = Color(0xFF7A1B16),
    cityHalo = Color(0xE6FFFFFF),
    pin = Color(0xFFD1352B),
    pinRing = Color(0xFFFFFFFF),
)

/** La stessa mappa di notte, per il tema spaziale: la Terra vista dall'alto. */
private val AtlasNotte = MapPalette(
    seaTop = Color(0xFF0B1B33),
    seaBottom = Color(0xFF071225),
    // Stessa regola del giorno: niente tinte che tendono al blu, o le isole
    // spariscono nel mare notturno. Al posto dell'azzurro c'e un oliva scuro.
    land = listOf(
        Color(0xFF4A4A5E), // ardesia calda
        Color(0xFF34543F), // verde bosco
        Color(0xFF5C4D34), // bruno
        Color(0xFF4E3A55), // prugna
        Color(0xFF4C4A3C), // oliva scuro
        Color(0xFF63404A), // granata
    ),
    coastShadow = Color(0x4D6FB6E0),
    border = Color(0xFF93A6BC),
    lake = Color(0xFF14304A),
    lakeBorder = Color(0xFF4E7FA0),
    graticule = Color(0x1AAFC8E0),
    label = Color(0xFFE3ECF7),
    labelHalo = Color(0xB3060D18),
    cityLabel = Color(0xFFFFD98A),
    cityHalo = Color(0xCC060D18),
    pin = Color(0xFFFF5B4C),
    pinRing = Color(0xFF0B1B33),
)

/**
 * Le citta del mondo, su una mappa vera.
 *
 * Si sposta col dito e si ingrandisce con due dita, ma **anche con due pulsanti
 * grandi**: un bambino di quattro anni con le dita piccole non fa la pinch, e
 * un gioco che si puo usare solo con un gesto difficile e un gioco che non si
 * usa.
 */
@Composable
fun MapGame(onBack: () -> Unit) {
    val context = LocalContext.current
    val skin = LocalSkin.current
    val density = LocalDensity.current
    val palette = if (skin.id == SKIN_SPACE) AtlasNotte else AtlasGiorno

    // Leggere il file e costruire i contorni costa qualche decimo di secondo:
    // si fa fuori dal thread della interfaccia, una volta sola, se no il gioco
    // si apre con uno scatto.
    val world by produceState<WorldMap.World?>(initialValue = null, context) {
        value = withContext(Dispatchers.Default) { WorldMap.world(context) }
    }
    val cities = remember { WorldMap.cities(context) }

    var mode by remember { mutableStateOf(MapMode.ESPLORA) }
    var zoom by remember { mutableStateOf(1f) }
    // Centro della vista, in coordinate di mondo: (0,5 ; 0,25) e il centro.
    var centre by remember { mutableStateOf(Offset(0.5f, 0.25f)) }
    var chosen by remember { mutableStateOf<WorldMap.City?>(null) }

    var target by remember { mutableStateOf<WorldMap.City?>(null) }
    var outcome by remember { mutableStateOf<Boolean?>(null) }
    var score by remember { mutableStateOf(0) }
    var rounds by remember { mutableStateOf(0) }

    // I pennelli del testo si creano una volta: crearli dentro il disegno
    // vorrebbe dire allocare a ogni fotogramma.
    val countryPaint = remember {
        NativePaint().apply {
            isAntiAlias = true
            textAlign = NativePaint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
    }
    val countryHalo = remember {
        NativePaint().apply {
            isAntiAlias = true
            textAlign = NativePaint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            style = NativePaint.Style.STROKE
            strokeJoin = NativePaint.Join.ROUND
        }
    }
    val cityPaint = remember {
        NativePaint().apply {
            isAntiAlias = true
            textAlign = NativePaint.Align.LEFT
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
    }
    val cityHalo = remember {
        NativePaint().apply {
            isAntiAlias = true
            textAlign = NativePaint.Align.LEFT
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            style = NativePaint.Style.STROKE
            strokeJoin = NativePaint.Join.ROUND
        }
    }

    fun newRound() {
        chosen = null
        outcome = null
        target = cities.randomOrNull(Random)
    }

    LaunchedEffect(mode) {
        if (mode == MapMode.INDOVINA && target == null) newRound()
    }

    LaunchedEffect(outcome) {
        if (outcome == null) return@LaunchedEffect
        delay(if (outcome == true) 1400 else 2200)
        rounds++
        newRound()
    }

    GameShell(
        title = Games.Map.label,
        onBack = onBack,
        status = if (mode == MapMode.INDOVINA) "$score / $rounds" else null,
        onRestart = {
            zoom = 1f
            centre = Offset(0.5f, 0.25f)
            chosen = null
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                for ((value, label) in listOf(
                    MapMode.ESPLORA to "Esplora",
                    MapMode.INDOVINA to "Dov'e?",
                )) {
                    Card(
                        onClick = {
                            mode = value
                            chosen = null
                            outcome = null
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (mode == value) skin.colors.primary
                            else skin.colors.surface,
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp),
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (mode == value) skin.colors.onPrimary
                            else skin.colors.onSurface,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        )
                    }
                }
            }

            if (mode == MapMode.INDOVINA) {
                val question = target
                Text(
                    text = when {
                        outcome == true -> "Bravo! Era proprio li."
                        outcome == false -> "Era qui: ${target?.name}. ${target?.landmark?.replaceFirstChar { it.uppercase() }}."
                        question != null -> "Dov'e ${question.name}? ${question.flag}"
                        else -> ""
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = when (outcome) {
                        true -> RightGreen
                        false -> MaterialTheme.colorScheme.error
                        null -> skin.colors.onBackground
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
            }

            Box(Modifier.fillMaxSize()) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.verticalGradient(listOf(palette.seaTop, palette.seaBottom)),
                        ),
                ) {
                    val viewWidth = constraints.maxWidth.toFloat()
                    val viewHeight = constraints.maxHeight.toFloat()
                    // Pixel per unita di mondo. Una sola scala per x e y: cosi
                    // una linea spessa un pixel e spessa un pixel in tutte le
                    // direzioni, e i continenti non si schiacciano.
                    val fit = minOf(viewWidth / WORLD_W, viewHeight / WORLD_H)
                    val s = fit * zoom

                    fun toScreenX(wx: Float) = viewWidth / 2f + (wx - centre.x) * s
                    fun toScreenY(wy: Float) = viewHeight / 2f + (wy - centre.y) * s
                    fun toScreen(lon: Float, lat: Float) = Offset(
                        toScreenX(WorldMap.projectX(lon)),
                        toScreenY(WorldMap.projectY(lat)),
                    )

                    fun clamp() {
                        // Oltre il bordo non si va: un bambino che trascina
                        // troppo si ritroverebbe davanti al nulla azzurro.
                        val halfX = (viewWidth / 2f) / s
                        val halfY = (viewHeight / 2f) / s
                        val x = if (halfX >= WORLD_W / 2f) WORLD_W / 2f
                        else centre.x.coerceIn(halfX, WORLD_W - halfX)
                        val y = if (halfY >= WORLD_H / 2f) WORLD_H / 2f
                        else centre.y.coerceIn(halfY, WORLD_H - halfY)
                        centre = Offset(x, y)
                    }

                    // A mondo intero centoventisette segnaposti diventano una
                    // macchia sopra l'Europa. Se ne tiene uno per casella, e la
                    // casella si stringe man mano che si ingrandisce: zoomando
                    // ne compaiono altri, che e il premio per aver guardato da
                    // vicino.
                    val visible = remember(zoom, cities) {
                        val cell = (26f / zoom).coerceAtLeast(0.6f)
                        val taken = HashSet<Long>()
                        cities.filter { city ->
                            val key = (city.lon / cell).toLong() * 1000L + (city.lat / cell).toLong()
                            taken.add(key)
                        }
                    }

                    val mondo = world
                    val livello = mondo?.levelFor(zoom)

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { centroid, pan, pinch, _ ->
                                    val prima = s
                                    val nuovo = (zoom * pinch).coerceIn(MIN_ZOOM, MAX_ZOOM)
                                    val dopo = fit * nuovo
                                    // Si ingrandisce attorno alle dita, non
                                    // attorno al centro dello schermo: e la
                                    // differenza fra "guardo meglio questo" e
                                    // "il mondo mi scappa via".
                                    val wx = centre.x + (centroid.x - viewWidth / 2f) / prima
                                    val wy = centre.y + (centroid.y - viewHeight / 2f) / prima
                                    zoom = nuovo
                                    centre = Offset(
                                        wx - (centroid.x - viewWidth / 2f) / dopo - pan.x / dopo,
                                        wy - (centroid.y - viewHeight / 2f) / dopo - pan.y / dopo,
                                    )
                                    clamp()
                                }
                            }
                            .pointerInput(cities, mode, target, zoom) {
                                detectTapGestures { at ->
                                    if (mode == MapMode.ESPLORA) {
                                        chosen = visible.minByOrNull { city ->
                                            (toScreen(city.lon, city.lat) - at).getDistanceSquared()
                                        }?.takeIf { city ->
                                            (toScreen(city.lon, city.lat) - at).getDistance() < 70f
                                        }
                                        if (chosen != null) Sfx.play(Sfx.Sound.TAP)
                                    } else if (outcome == null) {
                                        val goal = target ?: return@detectTapGestures
                                        val p = toScreen(goal.lon, goal.lat)
                                        // Tolleranza generosa a mondo intero e
                                        // via via piu stretta ingrandendo: si
                                        // chiede "dov'e piu o meno", ma da
                                        // vicino "piu o meno" vale meno strada.
                                        val soglia = maxOf(70f, viewWidth * 0.07f / zoom.coerceAtLeast(1f))
                                        val right = (p - at).getDistance() < soglia
                                        outcome = right
                                        if (right) score++
                                        Sfx.play(if (right) Sfx.Sound.RIGHT else Sfx.Sound.WRONG)
                                    }
                                }
                            },
                    ) {
                        if (livello == null) return@Canvas

                        disegnaReticolo(palette, zoom, ::toScreenX, ::toScreenY)

                        // Il rettangolo di mondo che si vede adesso, con un
                        // margine: quello che sta fuori non si disegna.
                        val margine = 24f / s
                        val vx0 = centre.x - (viewWidth / 2f) / s - margine
                        val vx1 = centre.x + (viewWidth / 2f) / s + margine
                        val vy0 = centre.y - (viewHeight / 2f) / s - margine
                        val vy1 = centre.y + (viewHeight / 2f) / s + margine

                        // I contorni sono gia costruiti nello spazio del mondo:
                        // qui si sposta e si ingrandisce il piano, non i punti.
                        // E la differenza fra una mappa che si puo infittire e
                        // una che no.
                        withTransform({
                            translate(
                                left = viewWidth / 2f - centre.x * s,
                                top = viewHeight / 2f - centre.y * s,
                            )
                            scale(s, s, pivot = Offset.Zero)
                        }) {
                            // Si disegna SOLO quello che si vede.
                            //
                            // La prima versione univa tutti i contorni in un
                            // tracciato solo, per fare una chiamata di disegno
                            // invece di duecento. Misurato sul tablet: 250
                            // millisecondi a fotogramma, quattro al secondo.
                            // Un tracciato unico non si puo scartare a pezzi,
                            // quindi tracciare i confini costava sempre tutti
                            // e centonovemila i punti del mondo, anche
                            // guardando il Belgio. Duecento chiamate su quello
                            // che si vede costano molto meno di una chiamata
                            // su tutto.
                            val inVista = livello.land.filter {
                                it.visibile(vx0, vy0, vx1, vy1)
                            }

                            // L'ombra sulla costa: un tratto spesso sotto i
                            // riempimenti. Non e un vezzo, e quello che fa
                            // staccare la terra dal mare invece di lasciare
                            // due campiture accostate. Si disegna attorno a
                            // ogni paese, non solo lungo la costa, ma dove due
                            // paesi confinano il riempimento del vicino ci
                            // passa sopra: resta visibile solo dove il vicino
                            // non c'e, cioe sul mare. Che e il punto.
                            //
                            // Solo a mondo intero, pero. Un tratto spesso e la
                            // cosa piu cara che ci sia da disegnare, perche la
                            // sua geometria va ricalcolata a ogni fotogramma,
                            // e sul dettaglio pieno costava da solo un terzo
                            // del tempo. A mondo intero fa la differenza fra
                            // una mappa e due campiture; da vicino, con il
                            // mare che si vede appena, non la fa.
                            // E si spegne piano, non di colpo. Prima spariva
                            // sulla soglia del dettaglio, e quel salto si
                            // notava piu delle isole che comparivano insieme:
                            // la correzione di un artefatto aveva scoperto
                            // l'altro. Qui svanisce PRIMA della soglia, cosi
                            // quando si cambia livello non c'e piu niente da
                            // spegnere - e il tratto spesso, che e la cosa
                            // piu cara da disegnare, non tocca mai il
                            // dettaglio pieno.
                            val opacitaAlone = ((WorldMap.SOGLIA_DETTAGLIO - zoom) /
                                (WorldMap.SOGLIA_DETTAGLIO - 1.8f)).coerceIn(0f, 1f)
                            if (opacitaAlone > 0.01f) {
                                val alone = Stroke(
                                    width = 6f / s,
                                    join = StrokeJoin.Round,
                                    cap = StrokeCap.Round,
                                )
                                for (paese in inVista) {
                                    drawPath(
                                        path = paese.path,
                                        color = palette.coastShadow,
                                        alpha = opacitaAlone,
                                        style = alone,
                                    )
                                }
                            }

                            for (paese in inVista) {
                                drawPath(
                                    path = paese.path,
                                    color = palette.land[paese.colour % palette.land.size],
                                )
                            }

                            for (lago in livello.lakes) {
                                if (!lago.visibile(vx0, vy0, vx1, vy1)) continue
                                drawPath(lago.path, palette.lake)
                                drawPath(lago.path, palette.lakeBorder, style = Capello)
                            }

                            // Confini e coste in una passata sola: sono dello
                            // stesso colore, come su un atlante.
                            for (paese in inVista) {
                                drawPath(paese.path, palette.border, style = Capello)
                            }
                        }

                        if (zoom < ZOOM_PAESI) {
                            disegnaNomiContinenti(
                                palette = palette,
                                testo = countryPaint,
                                alone = countryHalo,
                                dimensione = with(density) { 15.sp.toPx() },
                                toScreenX = ::toScreenX,
                                toScreenY = ::toScreenY,
                            )
                        } else {
                            disegnaNomiPaesi(
                                livello = livello,
                                palette = palette,
                                zoom = zoom,
                                scala = s,
                                vista = floatArrayOf(vx0, vy0, vx1, vy1),
                                testo = countryPaint,
                                alone = countryHalo,
                                dimensione = with(density) { 13.sp.toPx() },
                                toScreenX = ::toScreenX,
                                toScreenY = ::toScreenY,
                            )
                        }

                        // Segnaposti delle citta. Il nome compare solo da
                        // vicino: a mondo intero cento scritte sovrapposte non
                        // le legge nessuno, e chi ha quattro anni non legge
                        // affatto. Si tocca e si scopre.
                        val raggio = (3.5f + zoom * 0.8f).coerceAtMost(10f)
                        val conNome = zoom >= 3.5f
                        cityPaint.color = palette.cityLabel.toArgb()
                        cityPaint.textSize = with(density) { 12.sp.toPx() }
                        cityHalo.color = palette.cityHalo.toArgb()
                        cityHalo.textSize = cityPaint.textSize
                        cityHalo.strokeWidth = with(density) { 3.dp.toPx() }

                        for (city in visible) {
                            val p = toScreen(city.lon, city.lat)
                            if (p.x < -40 || p.y < -40 || p.x > size.width + 40 || p.y > size.height + 40) continue
                            drawCircle(palette.pinRing, raggio + 2.2f, p)
                            drawCircle(palette.pin, raggio, p)
                            if (conNome) {
                                drawIntoCanvas { canvas ->
                                    val x = p.x + raggio + 6f
                                    val y = p.y + cityPaint.textSize * 0.36f
                                    canvas.nativeCanvas.drawText(city.name, x, y, cityHalo)
                                    canvas.nativeCanvas.drawText(city.name, x, y, cityPaint)
                                }
                            }
                        }

                        // Nella sfida, a risposta data si illumina il posto giusto.
                        val reveal = if (outcome != null) target else chosen
                        if (reveal != null) {
                            val p = toScreen(reveal.lon, reveal.lat)
                            drawCircle(
                                color = if (outcome == false) Color(0xFFD32F2F) else RightGreen,
                                radius = raggio + 13f,
                                center = p,
                                style = Stroke(width = 5f),
                            )
                        }
                    }

                    // Due pulsanti grandi per ingrandire: la pinch non basta.
                    Column(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ZoomButton(Icons.Rounded.Add, "Ingrandisci") {
                            zoom = (zoom * 1.8f).coerceAtMost(MAX_ZOOM); clamp()
                        }
                        ZoomButton(Icons.Rounded.Remove, "Rimpicciolisci") {
                            zoom = (zoom / 1.8f).coerceAtLeast(MIN_ZOOM); clamp()
                        }
                        ZoomButton(Icons.Rounded.Public, "Torna al mondo intero") {
                            zoom = 1f
                            centre = Offset(0.5f, 0.25f)
                        }
                    }

                    // Mentre il mondo si apre, e se non si apre.
                    val stato = world
                    if (stato == null) {
                        MapNotice("Sto aprendo il mondo...", Modifier.align(Alignment.Center))
                    } else if (!stato.ok) {
                        MapNotice(
                            "La mappa non si e aperta: ${stato.problema}",
                            Modifier.align(Alignment.Center),
                        )
                    }
                }

                val card = chosen
                if (mode == MapMode.ESPLORA && card != null) {
                    CityCard(card, Modifier.align(Alignment.TopCenter)) { chosen = null }
                }
            }
        }
    }
}

/**
 * I meridiani e i paralleli. Faint, ma ci sono: sono la cosa che distingue una
 * mappa da una sagoma di continenti su uno sfondo azzurro, e danno la scala.
 * Il passo si stringe ingrandendo, se no a mondo intero sarebbero una rete e da
 * vicino non se ne vedrebbe nemmeno uno.
 */
private fun DrawScope.disegnaReticolo(
    palette: MapPalette,
    zoom: Float,
    toScreenX: (Float) -> Float,
    toScreenY: (Float) -> Float,
) {
    val passo = when {
        zoom < 2f -> 30f
        zoom < 5f -> 15f
        zoom < 12f -> 5f
        else -> 1f
    }
    val tratto = Stroke(width = 1f)

    var lon = -180f
    while (lon <= 180f) {
        val x = toScreenX(WorldMap.projectX(lon))
        if (x >= -1f && x <= size.width + 1f) {
            drawLine(palette.graticule, Offset(x, 0f), Offset(x, size.height), tratto.width)
        }
        lon += passo
    }

    var lat = floor(-90f / passo) * passo
    val fine = ceil(90f / passo) * passo
    while (lat <= fine) {
        val y = toScreenY(WorldMap.projectY(lat))
        if (y >= -1f && y <= size.height + 1f) {
            drawLine(palette.graticule, Offset(0f, y), Offset(size.width, y), tratto.width)
        }
        lat += passo
    }
}

/**
 * I continenti, con il punto dove scrivere il nome.
 *
 * Stanno qui e non nel file perche non sono un dato geografico: sono sette
 * nomi che non cambiano, e il punto dove scriverli e una scelta tipografica,
 * non il risultato di un calcolo. Servono perche a mondo intero i nomi utili a
 * un bambino di cinque anni non sono "Burkina Faso" e "Paraguay": sono
 * "Africa" e "America del Sud". I paesi arrivano quando si ingrandisce.
 */
private val Continenti = listOf(
    Triple("EUROPA", 16f, 51f),
    Triple("AFRICA", 20f, 3f),
    Triple("ASIA", 90f, 45f),
    Triple("AMERICA DEL NORD", -100f, 46f),
    Triple("AMERICA DEL SUD", -59f, -15f),
    Triple("OCEANIA", 134f, -25f),
    Triple("ANTARTIDE", 25f, -80f),
)

/** Sopra questo ingrandimento si passa dai continenti ai paesi. */
private const val ZOOM_PAESI = 2f

/**
 * I nomi dei continenti, scritti come su un atlante: maiuscoletto spaziato e
 * tenue, perche devono dare il senso dell'insieme senza coprire la mappa.
 */
private fun DrawScope.disegnaNomiContinenti(
    palette: MapPalette,
    testo: NativePaint,
    alone: NativePaint,
    dimensione: Float,
    toScreenX: (Float) -> Float,
    toScreenY: (Float) -> Float,
) {
    testo.color = palette.label.copy(alpha = 0.75f).toArgb()
    testo.textSize = dimensione
    testo.letterSpacing = 0.22f
    alone.color = palette.labelHalo.toArgb()
    alone.textSize = dimensione
    alone.letterSpacing = 0.22f
    alone.strokeWidth = dimensione * 0.24f

    drawIntoCanvas { canvas ->
        val nativo = canvas.nativeCanvas
        for ((nome, lon, lat) in Continenti) {
            val x = toScreenX(WorldMap.projectX(lon))
            val y = toScreenY(WorldMap.projectY(lat)) + dimensione * 0.34f
            // Il nome sta dentro per intero, o non si scrive. Controllare solo
            // il punto lascia passare scritte tagliate a meta dal bordo, che
            // sono peggio di una scritta assente.
            val mezzo = testo.measureText(nome) / 2f
            if (x - mezzo < 2f || x + mezzo > size.width - 2f) continue
            if (y - dimensione < 2f || y > size.height - 2f) continue
            nativo.drawText(nome, x, y, alone)
            nativo.drawText(nome, x, y, testo)
        }
    }
    // Rimesso a zero: lo stesso pennello serve poi ai nomi dei paesi, che
    // spaziati non li vuole.
    testo.letterSpacing = 0f
    alone.letterSpacing = 0f
}

/**
 * I nomi dei paesi.
 *
 * Due regole, e sono quelle che separano una mappa leggibile da un groviglio di
 * scritte. La prima: ogni paese ha un rango, deciso dalla sua area quando il
 * file e stato costruito, e ingrandendo si scende di rango - a mondo intero si
 * leggono una ventina di nomi, non duecento. La seconda: il nome si scrive solo
 * se **ci sta dentro** il paese; cosi il Lussemburgo non si prende una scritta
 * larga tre volte lui, e comparira quando sara abbastanza grande da meritarsela.
 */
private fun DrawScope.disegnaNomiPaesi(
    livello: WorldMap.Level,
    palette: MapPalette,
    zoom: Float,
    scala: Float,
    /** Il rettangolo di mondo in vista: x0, y0, x1, y1. */
    vista: FloatArray,
    testo: NativePaint,
    alone: NativePaint,
    dimensione: Float,
    toScreenX: (Float) -> Float,
    toScreenY: (Float) -> Float,
) {
    val rangoMassimo = when {
        zoom < 3f -> 0
        zoom < 5f -> 1
        zoom < 10f -> 2
        else -> 3
    }

    testo.color = palette.label.toArgb()
    testo.textSize = dimensione
    alone.color = palette.labelHalo.toArgb()
    alone.textSize = dimensione
    alone.strokeWidth = dimensione * 0.28f

    drawIntoCanvas { canvas ->
        val nativo = canvas.nativeCanvas
        for (paese in livello.land) {
            if (paese.labelRank > rangoMassimo) continue
            if (paese.name.isEmpty()) continue
            if (!paese.visibile(vista[0], vista[1], vista[2], vista[3])) continue

            val x = toScreenX(paese.labelX)
            val y = toScreenY(paese.labelY)

            val larghezzaTesto = testo.measureText(paese.name)
            // Tagliato dal bordo e peggio che assente: o ci sta tutto, o niente.
            val mezzo = larghezzaTesto / 2f
            if (x - mezzo < 2f || x + mezzo > size.width - 2f) continue
            if (y - dimensione < 2f || y > size.height - 2f) continue

            val larghezzaPaese = (paese.maxX - paese.minX) * scala
            // Se il nome e piu largo del paese, non si scrive: tornera quando
            // il paese sara abbastanza grande.
            if (larghezzaTesto > larghezzaPaese * 0.92f) continue

            nativo.drawText(paese.name, x, y + dimensione * 0.34f, alone)
            nativo.drawText(paese.name, x, y + dimensione * 0.34f, testo)
        }
    }
}

@Composable
private fun MapNotice(testo: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.padding(20.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Text(
            text = testo,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp),
        )
    }
}

@Composable
private fun ZoomButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(56.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = Color.White,
            contentColor = Color(0xFF263238),
        ),
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(30.dp))
    }
}

/** Quello che compare toccando una citta: bandiera, nome e una cosa da sapere. */
@Composable
private fun CityCard(city: WorldMap.City, modifier: Modifier = Modifier, onClose: () -> Unit) {
    Card(
        modifier = modifier.padding(12.dp).widthIn(max = 520.dp).clickable(onClick = onClose),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(city.flag, fontSize = 44.sp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(city.name, style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = city.country,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = city.landmark.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}
