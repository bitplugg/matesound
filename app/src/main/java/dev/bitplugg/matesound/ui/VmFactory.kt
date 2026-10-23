package dev.bitplugg.matesound.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.bitplugg.matesound.AppContainer
import dev.bitplugg.matesound.MateSoundApp

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer is not provided")
}

@Composable
inline fun <reified VM : ViewModel> appViewModel(
    crossinline creator: CreationExtras.(AppContainer) -> VM,
): VM {
    val container = (LocalContext.current.applicationContext as MateSoundApp).container
    return viewModel(
        factory = viewModelFactory {
            initializer { creator(container) }
        },
    )
}
