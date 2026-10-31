package it.iotatec.starkid.games

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.iotatec.starkid.domain.GameRounds
import it.iotatec.starkid.ui.LocalSkin
import kotlinx.coroutines.delay
import kotlin.random.Random

private const val PAIRS = 6

/** Memory a sei coppie: dodici carte, quattro per riga. */
@Composable
fun MemoryGame(onBack: () -> Unit) {
    var deck by remember { mutableStateOf(GameRounds.memoryDeck(PAIRS, MemorySymbols, Random)) }
    var faceUp by remember { mutableStateOf(emptyList<Int>()) }
    var matched by remember { mutableStateOf(emptySet<Int>()) }
    var moves by remember { mutableStateOf(0) }

    val won = matched.size == deck.size

    // Due carte scoperte: si guardano per un attimo e poi si decide. Il
    // ritardo non e pigrizia, e il tempo che serve a un bambino per vederle.
    LaunchedEffect(faceUp) {
        if (faceUp.size < 2) return@LaunchedEffect
        delay(800)
        val (first, second) = faceUp
        if (deck[first] == deck[second]) {
            matched = matched + first + second
            Sfx.play(if (matched.size == deck.size) Sfx.Sound.WIN else Sfx.Sound.RIGHT)
        } else {
            Sfx.play(Sfx.Sound.WRONG)
        }
        faceUp = emptyList()
    }

    fun restart() {
        deck = GameRounds.memoryDeck(PAIRS, MemorySymbols, Random)
        faceUp = emptyList()
        matched = emptySet()
        moves = 0
    }

    GameShell(
        title = Games.Memory.label,
        onBack = onBack,
        status = "${matched.size / 2} / $PAIRS",
        onRestart = { restart() },
    ) {
        if (won) {
            WinBanner(
                text = "Bravo! Trovate tutte in $moves mosse.",
                onAgain = { restart() },
            )
            return@GameShell
        }

        BoxWithConstraints(Modifier.fillMaxSize().widthIn(max = 1000.dp)) {
            val columns = 4
            val rows = (deck.size + columns - 1) / columns
            val cardHeight = (maxHeight - 16.dp) / rows
            val symbolSize = (cardHeight.value * 0.42f).coerceIn(28f, 72f)

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
            ) {
                for (row in 0 until rows) {
                    Row(Modifier.fillMaxWidth().weight(1f)) {
                        for (column in 0 until columns) {
                            val index = row * columns + column
                            Box(Modifier.weight(1f).fillMaxHeight().padding(6.dp)) {
                                if (index < deck.size) {
                                    MemoryCard(
                                        symbol = deck[index],
                                        open = index in faceUp || index in matched,
                                        done = index in matched,
                                        symbolSize = symbolSize,
                                        onClick = {
                                            if (faceUp.size < 2 &&
                                                index !in faceUp &&
                                                index !in matched
                                            ) {
                                                Sfx.play(Sfx.Sound.TAP)
                                                faceUp = faceUp + index
                                                if (faceUp.size == 2) moves++
                                            }
                                        },
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
private fun MemoryCard(
    symbol: String,
    open: Boolean,
    done: Boolean,
    symbolSize: Float,
    onClick: () -> Unit,
) {
    val skin = LocalSkin.current
    // Le carte azzeccate restano scoperte ma si spengono: si vede a colpo
    // d'occhio quanto manca, senza toglierle e far ballare la griglia.
    val fade by animateFloatAsState(if (done) 0.45f else 1f, label = "carta")

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxSize().alpha(fade),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (open) skin.tileColors[1] else skin.tileColors[0],
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (open) 1.dp else 4.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = if (open) symbol else "?",
                fontSize = symbolSize.sp,
                color = skin.tileTextColor,
            )
        }
    }
}

/** Il premio di fine partita, uguale in tutti i giochi che si possono vincere. */
@Composable
fun WinBanner(text: String, onAgain: () -> Unit) {
    val skin = LocalSkin.current
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 36.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("🎉", fontSize = 72.sp)
            Spacer(Modifier.size(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.size(20.dp))
            Card(
                onClick = onAgain,
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = skin.colors.primary),
            ) {
                Text(
                    text = "Ancora!",
                    style = MaterialTheme.typography.titleLarge,
                    color = skin.colors.onPrimary,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 14.dp),
                )
            }
        }
    }
}
