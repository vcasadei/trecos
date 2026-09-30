package app.trecos.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.trecos.AppContainer
import app.trecos.TrecosApplication

/**
 * @return the app's [AppContainer], from the application context.
 */
@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as TrecosApplication).container

/**
 * Gets or creates a ViewModel built from the [AppContainer].
 *
 * @param key distinguishes ViewModels of the same type on one screen, such as per record id.
 * @param create builds the ViewModel from the container.
 * @return the ViewModel scoped to the current navigation entry.
 */
@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, noinline create: (AppContainer) -> VM): VM {
    val container = appContainer()
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}
