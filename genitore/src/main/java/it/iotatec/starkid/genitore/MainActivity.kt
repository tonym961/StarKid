package it.iotatec.starkid.genitore

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import it.iotatec.starkid.genitore.ui.Accesso
import it.iotatec.starkid.genitore.ui.Dettaglio
import it.iotatec.starkid.genitore.ui.Elenco
import it.iotatec.starkid.genitore.ui.Impostazioni
import it.iotatec.starkid.genitore.ui.Nuovo
import it.iotatec.starkid.genitore.ui.Storico
import it.iotatec.starkid.genitore.ui.TemaGenitori
import it.iotatec.starkid.genitore.ui.Tinte

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { TemaGenitori { App() } }
    }
}

@Composable
private fun App(stato: Stato = viewModel()) {
    val messaggi = remember { SnackbarHostState() }

    // Gli avvisi passano dalla barra in basso, gli errori restano scritti
    // nella schermata: un errore che sparisce da solo dopo tre secondi e un
    // errore che non hai letto.
    LaunchedEffect(stato.avviso) {
        stato.avviso?.let {
            messaggi.showSnackbar(it)
            stato.pulisciMessaggi()
        }
    }

    // Il tasto indietro riporta all elenco. Dall elenco e dall accesso non
    // fa niente: l unico modo di uscire e il tasto Home, che e quello che si
    // aspetta chi usa un telefono.
    BackHandler(enabled = stato.schermata !is Schermata.Elenco && stato.schermata !is Schermata.Accesso) {
        stato.vaiA(Schermata.Elenco)
    }

    Scaffold(
        containerColor = Tinte.Notte,
        snackbarHost = { SnackbarHost(messaggi) },
    ) { bordi ->
        Box(
            Modifier
                .fillMaxSize()
                .background(Tinte.Notte)
                .padding(bordi)
        ) {
            when (val dove = stato.schermata) {
                is Schermata.Accesso -> Accesso(stato)
                is Schermata.Elenco -> Elenco(stato)
                is Schermata.Dettaglio -> Dettaglio(stato, dove.idTablet)
                is Schermata.Nuovo -> Nuovo(stato)
                is Schermata.Impostazioni -> Impostazioni(stato)
                is Schermata.Storico -> Storico(stato, dove.idTablet)
            }
        }
    }
}
