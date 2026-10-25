package dev.david.braviahome.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.david.braviahome.HomeViewModel

/** Startbildschirm oder SYS.CONFIG. Die Home-Taste fuehrt immer zurueck zum Startbildschirm. */
@Composable
fun LauncherRoot(viewModel: HomeViewModel) {
    var config by rememberSaveable { mutableStateOf(false) }
    val homePresses by viewModel.homePresses.collectAsStateWithLifecycle()
    val bootRequests by viewModel.bootRequests.collectAsStateWithLifecycle()
    // Home-Taste und Einschalten (Boot-Sequenz) fuehren immer zum Startbildschirm.
    LaunchedEffect(homePresses, bootRequests.id) { config = false }
    val configRequests by viewModel.configRequests.collectAsStateWithLifecycle()
    LaunchedEffect(configRequests) { if (configRequests > 0) config = true }

    if (config) {
        ConfigScreen(viewModel, onBack = { config = false })
    } else {
        HomeScreen(viewModel, onOpenConfig = { config = true })
    }
}
