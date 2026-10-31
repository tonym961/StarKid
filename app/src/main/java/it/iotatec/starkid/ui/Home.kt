package it.iotatec.starkid.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Widgets
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.iotatec.starkid.data.AppCategory
import it.iotatec.starkid.data.AppEntry
import it.iotatec.starkid.data.HOME_CATEGORIES
import it.iotatec.starkid.data.KidsConfig
import it.iotatec.starkid.games.GameDef
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Una tessera della griglia. I giochi di StarKid e le app installate stanno
 * nella stessa griglia e nelle stesse categorie: per un bambino sono tutte
 * cose da toccare, la differenza di dove stanno scritte non lo riguarda.
 */
sealed interface HomeTile {
    val key: String

    data class Installed(val app: AppEntry) : HomeTile {
        override val key: String get() = app.packageName
    }

    data class Builtin(val game: GameDef) : HomeTile {
        override val key: String get() = "gioco:${game.id}"
    }
}

/**
 * La schermata che il bambino vede: barra con avatar, nome e tempo residuo,
 * categorie, e le tessere a pagine.
 *
 * Le tessere stanno su pagine affiancate e non in un elenco che scorre: su un
 * tablet per bambini le frecce grandi e i pallini in basso si capiscono prima
 * di uno scorrimento, e ogni cosa resta sempre nello stesso punto della sua
 * pagina.
 */
@Composable
fun HomeScreen(
    config: KidsConfig,
    apps: List<AppEntry>,
    games: List<GameDef>,
    remainingMinutes: Int?,
    photo: ImageBitmap?,
    onOpenApp: (AppEntry) -> Unit,
    onOpenGame: (GameDef) -> Unit,
    onParentArea: () -> Unit,
) {
    val skin = LocalSkin.current
    val byCategory = config.homeLayout == HOME_CATEGORIES
    var chosen by remember { mutableStateOf<AppCategory?>(null) }

    // I giochi di StarKid vengono per primi: sono gli unici che funzionano
    // sempre, senza rete e senza pubblicita.
    val tiles = remember(apps, games) {
        games.map { HomeTile.Builtin(it) } + apps.map { HomeTile.Installed(it) }
    }
    val categoryOf: (HomeTile) -> AppCategory = { tile ->
        when (tile) {
            is HomeTile.Builtin -> tile.game.category
            is HomeTile.Installed -> AppCategory.of(config, tile.app)
        }
    }

    val categories = remember(tiles, config.appCategories) {
        AppCategory.entries.filter { category -> tiles.any { categoryOf(it) == category } }
    }
    // Una categoria svuotata dal genitore mentre il bambino la stava guardando
    // non deve lasciare una pagina vuota.
    val active = chosen?.takeIf { it in categories }
    val visible = remember(tiles, active, config.appCategories) {
        if (active == null) tiles else tiles.filter { categoryOf(it) == active }
    }

    KidsBackground(
        skin = skin,
        backgroundId = config.background,
        photo = photo,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
            HomeTopBar(
                skin = skin,
                avatar = config.avatar,
                childName = config.childName,
                remainingMinutes = remainingMinutes,
                onParentArea = onParentArea,
            )
            Spacer(Modifier.size(12.dp))

            when {
                tiles.isEmpty() -> EmptyState(skin, Modifier.weight(1f))

                // A categorie: la schermata si apre sugli scomparti, non su
                // tutto. Si entra in uno, si gioca, si torna indietro.
                byCategory && active == null && categories.size > 1 -> key("categorie") {
                    CategoryPages(
                        categories = categories,
                        countOf = { category -> tiles.count { categoryOf(it) == category } },
                        skin = skin,
                        onOpen = { chosen = it },
                        modifier = Modifier.weight(1f),
                    )
                }

                else -> {
                    if (byCategory) {
                        BackToCategories(skin, active) { chosen = null }
                        Spacer(Modifier.size(10.dp))
                    } else if (categories.size > 1) {
                        CategoryBar(
                            categories = categories,
                            active = active,
                            onPick = { chosen = if (it == active) null else it },
                        )
                        Spacer(Modifier.size(10.dp))
                    }
                    // Cambiando categoria il pager deve ripartire dalla prima
                    // pagina: con key() lo stato si ricrea da zero.
                    key(active) {
                        TilePages(
                            tiles = visible,
                            skin = skin,
                            onOpenApp = onOpenApp,
                            onOpenGame = onOpenGame,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTopBar(
    skin: Skin,
    avatar: String,
    childName: String,
    remainingMinutes: Int?,
    onParentArea: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(skin.scrim)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(avatar)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = if (childName.isBlank()) "Ciao!" else "Ciao, $childName!",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Clock()
        }
        if (remainingMinutes != null) {
            TimeChip(remainingMinutes)
            Spacer(Modifier.width(12.dp))
        }
        ParentDoor(onParentArea)
    }
}

@Composable
private fun Avatar(emoji: String) {
    Box(
        modifier = Modifier
            .size(66.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = emoji, fontSize = 36.sp)
    }
}

@Composable
private fun Clock() {
    var label by remember { mutableStateOf(clockLabel()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            label = clockLabel()
        }
    }
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun clockLabel(): String {
    val now = Calendar.getInstance()
    return "%02d:%02d".format(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
}

@Composable
private fun TimeChip(remainingMinutes: Int) {
    val warn = remainingMinutes <= 10
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (warn) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = formatMinutes(remainingMinutes),
                style = MaterialTheme.typography.titleLarge,
                color = if (warn) MaterialTheme.colorScheme.onError
                else MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = "ancora",
                style = MaterialTheme.typography.bodyMedium,
                color = if (warn) MaterialTheme.colorScheme.onError
                else MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/**
 * Ingresso all'area genitori: si apre solo con una pressione lunga, cosi un
 * bambino che tocca a caso non ci finisce dentro.
 */
@Composable
private fun ParentDoor(onParentArea: () -> Unit) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onParentArea() })
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Lock,
            contentDescription = "Area genitori: tieni premuto",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp),
        )
    }
}

/**
 * La schermata a scomparti: pochi riquadri grandi, uno per categoria, con
 * quante cose ci sono dentro. Per un bambino piccolo sette bersagli grandi
 * sono piu facili di venti piccoli, e il nome della categoria si impara.
 */
@Composable
private fun CategoryPages(
    categories: List<AppCategory>,
    countOf: (AppCategory) -> Int,
    skin: Skin,
    onOpen: (AppCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = (maxWidth / 260.dp).toInt().coerceIn(2, 4)
        val rows = ((categories.size + columns - 1) / columns).coerceAtLeast(1)
        val iconSize = ((maxHeight / rows) * 0.34f).coerceIn(44.dp, 110.dp)
        Column(Modifier.fillMaxSize()) {
            for (row in 0 until rows) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    for (column in 0 until columns) {
                        Box(Modifier.weight(1f).fillMaxHeight().padding(10.dp)) {
                            val category = categories.getOrNull(row * columns + column)
                            if (category != null) {
                                val background = skin.tileColors[
                                    category.ordinal.mod(skin.tileColors.size),
                                ]
                                Tile(
                                    label = category.label,
                                    background = background,
                                    textColor = skin.tileTextColor,
                                    onClick = { onOpen(category) },
                                    subtitle = "${countOf(category)}",
                                ) {
                                    Icon(
                                        imageVector = category.icon,
                                        contentDescription = null,
                                        tint = skin.tileTextColor,
                                        modifier = Modifier.size(iconSize),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Dentro una categoria: un pulsante grande per tornare agli scomparti. */
@Composable
private fun BackToCategories(skin: Skin, category: AppCategory?, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(26.dp))
            .background(skin.scrim)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledIconButton(
            onClick = onBack,
            modifier = Modifier.size(52.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Torna agli scomparti",
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        if (category != null) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = category.label,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(8.dp))
    }
}

/** Barra delle categorie: compare solo se c'e piu di una categoria da scegliere. */
@Composable
private fun CategoryBar(
    categories: List<AppCategory>,
    active: AppCategory?,
    onPick: (AppCategory) -> Unit,
) {
    Row(
        // Scorrevole: con tutte le categorie piene, su uno schermo stretto le
        // ultime pastiglie finirebbero tagliate fuori.
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CategoryChip(
            label = "Tutte",
            icon = Icons.Rounded.Widgets,
            selected = active == null,
            onClick = { active?.let(onPick) },
        )
        for (category in categories) {
            CategoryChip(
                label = category.label,
                icon = category.icon,
                selected = category == active,
                onClick = { onPick(category) },
            )
        }
    }
}

@Composable
private fun CategoryChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) colors.primary else colors.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 4.dp else 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) colors.onPrimary else colors.onSurface,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) colors.onPrimary else colors.onSurface,
            )
        }
    }
}

/** La griglia a pagine, con le frecce ai lati e i pallini in basso. */
@Composable
private fun TilePages(
    tiles: List<HomeTile>,
    skin: Skin,
    onOpenApp: (AppEntry) -> Unit,
    onOpenGame: (GameDef) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Le frecce hanno una corsia riservata su entrambi i lati: comparendo
        // e sparendo dentro la griglia sposterebbero le tessere sotto il dito.
        val lane = 60.dp
        val gridWidth = maxWidth - lane * 2
        val gridHeight = maxHeight - 24.dp
        // Tessere grandi: poche per pagina, bersagli comodi per un dito
        // piccolo. Le righe si contano sulla larghezza di una cella, non su
        // una misura fissa: cosi in verticale le tessere non diventano
        // colonne altissime mezze vuote.
        val columns = (gridWidth / 200.dp).toInt().coerceIn(2, 5)
        val cellWidth = gridWidth / columns
        val rows = (gridHeight / cellWidth).toInt().coerceIn(1, 4)
        val perPage = columns * rows
        val pages = ((tiles.size + perPage - 1) / perPage).coerceAtLeast(1)
        val iconSize = ((gridHeight / rows) * 0.40f).coerceIn(40.dp, 112.dp)

        val state = rememberPagerState(pageCount = { pages })
        val scope = rememberCoroutineScope()

        Column(Modifier.fillMaxSize()) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                ArrowLane(
                    width = lane,
                    visible = state.currentPage > 0,
                    icon = Icons.Rounded.ChevronLeft,
                    description = "Pagina precedente",
                    onClick = { scope.launch { state.animateScrollToPage(state.currentPage - 1) } },
                )
                HorizontalPager(
                    state = state,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                ) { page ->
                    GridPage(
                        tiles = tiles,
                        page = page,
                        columns = columns,
                        rows = rows,
                        iconSize = iconSize,
                        skin = skin,
                        onOpenApp = onOpenApp,
                        onOpenGame = onOpenGame,
                    )
                }
                ArrowLane(
                    width = lane,
                    visible = state.currentPage < pages - 1,
                    icon = Icons.Rounded.ChevronRight,
                    description = "Pagina successiva",
                    onClick = { scope.launch { state.animateScrollToPage(state.currentPage + 1) } },
                )
            }
            PageDots(pages = pages, current = state.currentPage)
        }
    }
}

@Composable
private fun ArrowLane(
    width: Dp,
    visible: Boolean,
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.width(width).fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) {
        if (visible) {
            FilledIconButton(
                onClick = onClick,
                modifier = Modifier.size(52.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(icon, contentDescription = description, modifier = Modifier.size(34.dp))
            }
        }
    }
}

/**
 * Una pagina. Le celle hanno tutte lo stesso peso: una tessera non cambia
 * dimensione a seconda di quante ce ne sono, cosi il bersaglio da centrare
 * resta sempre quello.
 */
@Composable
private fun GridPage(
    tiles: List<HomeTile>,
    page: Int,
    columns: Int,
    rows: Int,
    iconSize: Dp,
    skin: Skin,
    onOpenApp: (AppEntry) -> Unit,
    onOpenGame: (GameDef) -> Unit,
) {
    val first = page * columns * rows
    Column(Modifier.fillMaxSize()) {
        for (row in 0 until rows) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                for (column in 0 until columns) {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(8.dp)) {
                        val tile = tiles.getOrNull(first + row * columns + column)
                        if (tile != null) {
                            val background = skin.tileColors[
                                tile.key.hashCode().mod(skin.tileColors.size),
                            ]
                            when (tile) {
                                is HomeTile.Installed -> Tile(
                                    label = tile.app.label,
                                    background = background,
                                    textColor = skin.tileTextColor,
                                    onClick = { onOpenApp(tile.app) },
                                ) {
                                    Image(
                                        bitmap = tile.app.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(iconSize),
                                    )
                                }

                                is HomeTile.Builtin -> Tile(
                                    label = tile.game.label,
                                    background = background,
                                    textColor = skin.tileTextColor,
                                    onClick = { onOpenGame(tile.game) },
                                ) {
                                    Text(
                                        text = tile.game.emoji,
                                        fontSize = (iconSize.value * 0.78f).sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Tile(
    label: String,
    background: Color,
    textColor: Color,
    onClick: () -> Unit,
    subtitle: String? = null,
    icon: @Composable () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = background),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            icon()
            Spacer(Modifier.size(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = textColor,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.headlineMedium,
                    color = textColor.copy(alpha = 0.75f),
                )
            }
        }
    }
}

/** Pallini delle pagine: il bambino vede quante pagine ci sono e dov'e. */
@Composable
private fun PageDots(pages: Int, current: Int) {
    if (pages <= 1) {
        Spacer(Modifier.height(24.dp))
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth().height(24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (page in 0 until pages) {
            val here = page == current
            Box(
                modifier = Modifier
                    .padding(horizontal = 5.dp)
                    .size(width = if (here) 28.dp else 12.dp, height = 12.dp)
                    .clip(CircleShape)
                    .background(
                        if (here) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    ),
            )
        }
    }
}

@Composable
private fun EmptyState(skin: Skin, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(skin.scrim)
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Nessuna app ancora", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.size(12.dp))
            Text(
                text = "Un genitore deve scegliere le app:\ntieni premuto il lucchetto in alto a destra.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
