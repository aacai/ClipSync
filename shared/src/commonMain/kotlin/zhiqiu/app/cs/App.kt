package zhiqiu.app.cs

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import zhiqiu.app.cs.ui.AppModel
import zhiqiu.app.cs.ui.AppTheme
import zhiqiu.app.cs.ui.JoinView
import zhiqiu.app.cs.ui.MainView
import zhiqiu.app.cs.ui.rememberPlatformUi

@Composable
fun App() {
    val platform = rememberPlatformUi()
    val scope = rememberCoroutineScope()
    val model = remember(platform) { AppModel(scope, platform) }

    AppTheme {
        // 根节点必须铺满背景色：wasmJs 下根元素透明会让浅色文字落在白色 body 上
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val joinedRoom by model.joinedRoom.collectAsState()
            var offlineBrowsing by rememberSaveable { mutableStateOf(false) }

            if (joinedRoom != null || offlineBrowsing) {
                MainView(
                        model = model,
                        roomCode = joinedRoom,
                        onLeave = {
                            model.disconnect()
                            offlineBrowsing = false
                        },
                )
            } else {
                JoinView(model, onBrowseOffline = { offlineBrowsing = true })
            }
        }
    }
}
