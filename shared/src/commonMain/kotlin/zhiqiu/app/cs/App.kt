package zhiqiu.app.cs

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable
import zhiqiu.app.cs.core.AppSettings
import zhiqiu.app.cs.ui.AppModel
import zhiqiu.app.cs.ui.AppTheme
import zhiqiu.app.cs.ui.MainView
import zhiqiu.app.cs.ui.M3
import zhiqiu.app.cs.ui.SettingsView
import zhiqiu.app.cs.ui.rememberAppModel
import zhiqiu.app.cs.ui.rememberPlatformUi

@Serializable
internal data object HistoryRoute

@Serializable
internal data object SettingsRoute

@Composable
fun App() {
    val platform = rememberPlatformUi()
    val settings = AppSettings.shared
    val model = rememberAppModel(platform, settings)
    val themeMode by settings.themeMode.collectAsState()
    val language by settings.language.collectAsState()

    AppTheme(themeMode) {
        val background by
                animateColorAsState(MaterialTheme.colorScheme.background, tween(M3.MEDIUM_3, easing = M3.STANDARD), label = "appBg")
        // 根节点必须铺满背景色：wasmJs 下根元素透明会让浅色文字落在白色 body 上
        Surface(modifier = Modifier.fillMaxSize(), color = background) {
            val joinedRoom by model.joinedRoom.collectAsState()
            val nav = rememberNavController()
            key(language) {
                ClipSyncNavHost(nav, model, settings, joinedRoom)
            }
        }
    }
}

@Composable
private fun ClipSyncNavHost(
        nav: NavHostController,
        model: AppModel,
        settings: AppSettings,
        roomCode: String?,
) {
    NavHost(
            navController = nav,
            startDestination = HistoryRoute,
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                fadeIn(tween(M3.MEDIUM_2, easing = M3.EMPHASIZED_IN)) +
                        slideInHorizontally(tween(M3.MEDIUM_3, easing = M3.EMPHASIZED)) { it / 16 }
            },
            exitTransition = {
                fadeOut(tween(M3.SHORT_4, easing = M3.EMPHASIZED_OUT)) +
                        slideOutHorizontally(tween(M3.MEDIUM_3, easing = M3.EMPHASIZED)) { -it / 16 }
            },
            popEnterTransition = {
                fadeIn(tween(M3.MEDIUM_2, easing = M3.EMPHASIZED_IN)) +
                        slideInHorizontally(tween(M3.MEDIUM_3, easing = M3.EMPHASIZED)) { -it / 16 }
            },
            popExitTransition = {
                fadeOut(tween(M3.SHORT_4, easing = M3.EMPHASIZED_OUT)) +
                        slideOutHorizontally(tween(M3.MEDIUM_3, easing = M3.EMPHASIZED)) { it / 16 }
            },
    ) {
        composable<HistoryRoute> {
            MainView(
                    model = model,
                    roomCode = roomCode,
                    onLeave = model::disconnect,
                    onOpenSettings = { nav.navigate(SettingsRoute) },
            )
        }
        composable<SettingsRoute> {
            SettingsView(
                    model = model,
                    settings = settings,
                    roomCode = roomCode,
                    onBack = { nav.popBackStack() },
                    onLeave = model::disconnect,
            )
        }
    }
}
