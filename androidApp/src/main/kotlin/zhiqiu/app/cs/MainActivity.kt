package zhiqiu.app.cs

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import zhiqiu.app.cs.core.AppSettings

class MainActivity : ComponentActivity() {
    private val residentScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App()
        }
        followResidentSwitch()
    }

    override fun onDestroy() {
        residentScope.cancel()
        super.onDestroy()
    }

    /** 设置页的开关只写状态，起停服务在这里做——只有 Activity 在前台才允许启动前台服务。 */
    private fun followResidentSwitch() {
        val settings = AppSettings.shared
        val service = Intent(this, ClipSyncService::class.java)
        residentScope.launch {
            settings.residentSync.collect { enabled ->
                if (!enabled) {
                    stopService(service)
                    return@collect
                }
                askNotificationPermission()
                val started = runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service) else startService(service)
                }.isSuccess
                if (!started) settings.setResidentSync(false)
            }
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (getSystemService(NotificationManager::class.java).areNotificationsEnabled()) return
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFY_REQUEST)
    }

    private companion object {
        const val NOTIFY_REQUEST = 1
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
