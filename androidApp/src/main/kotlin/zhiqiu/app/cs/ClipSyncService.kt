package zhiqiu.app.cs

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import zhiqiu.app.cs.core.AppSettings
import zhiqiu.app.cs.ui.ClipSyncRuntime

/** 后台常驻：把同步挂在 specialUse 前台服务上，进程不再被系统冻结或回收。 */
class ClipSyncService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotice(ResidentNotice.build(this, null, 0))
        val model = ClipSyncRuntime.model(AppSettings.shared)
        scope.launch {
            combine(model.joinedRoom, model.devices) { room, devices -> room to devices.size }
                .distinctUntilChanged()
                .collect { (room, devices) ->
                    val manager = getSystemService(NotificationManager::class.java)
                    manager.notify(ResidentNotice.ID, ResidentNotice.build(this@ClipSyncService, room, devices))
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val settings = AppSettings.shared
        if (intent?.action == ResidentNotice.ACTION_STOP) {
            settings.setResidentSync(false)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!settings.residentSync.value) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundNotice(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(ResidentNotice.ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(ResidentNotice.ID, notification)
        }
    }
}

private object ResidentNotice {
    const val ID = 4711
    const val ACTION_STOP = "zhiqiu.app.cs.action.STOP_RESIDENT"

    private const val CHANNEL = "clipsync.resident"

    @Suppress("DEPRECATION")
    fun build(context: Context, room: String?, devices: Int): Notification {
        ensureChannel(context)
        val text = when {
            room == null -> context.getString(R.string.notif_resident_waiting)
            devices > 0 -> context.getString(R.string.notif_resident_devices, room, devices)
            else -> context.getString(R.string.notif_resident_room, room)
        }
        val builder =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(context, CHANNEL)
                else Notification.Builder(context)
        return builder
                .setSmallIcon(R.drawable.ic_notification_sync)
                .setContentTitle(context.getString(R.string.notif_resident_title))
                .setContentText(text)
                .setOngoing(true)
                .setShowWhen(false)
                .setVisibility(Notification.VISIBILITY_SECRET)
                .setContentIntent(
                        PendingIntent.getActivity(
                                context,
                                0,
                                Intent(context, MainActivity::class.java),
                                pendingFlags(),
                        ),
                )
                .addAction(
                        0,
                        context.getString(R.string.notif_action_stop),
                        PendingIntent.getService(
                                context,
                                1,
                                Intent(context, ClipSyncService::class.java).setAction(ACTION_STOP),
                                pendingFlags(),
                        ),
                )
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                    }
                }
                .build()
    }

    private fun pendingFlags(): Int =
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
                NotificationChannel(
                        CHANNEL,
                        context.getString(R.string.notif_channel_resident),
                        NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    lockscreenVisibility = Notification.VISIBILITY_SECRET
                    setShowBadge(false)
                },
        )
    }
}
