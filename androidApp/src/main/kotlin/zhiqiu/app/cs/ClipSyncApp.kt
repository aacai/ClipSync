package zhiqiu.app.cs

import android.app.Application
import zhiqiu.app.cs.core.ClipSyncAppContext
import zhiqiu.app.cs.ui.ClipSyncRuntime

class ClipSyncApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ClipSyncAppContext.context = applicationContext
        registerActivityLifecycleCallbacks(
            object : android.app.Application.ActivityLifecycleCallbacks {
                private var active = 0

                override fun onActivityStarted(activity: android.app.Activity) {
                    if (++active == 1) {
                        ClipSyncAppContext.appInForeground = true
                        ClipSyncRuntime.flushPendingClip()
                    }
                }

                override fun onActivityStopped(activity: android.app.Activity) {
                    if (--active <= 0) {
                        active = 0
                        ClipSyncAppContext.appInForeground = false
                    }
                }

                override fun onActivityCreated(a: android.app.Activity, b: android.os.Bundle?) {}

                override fun onActivityResumed(a: android.app.Activity) {}

                override fun onActivityPaused(a: android.app.Activity) {}

                override fun onActivitySaveInstanceState(a: android.app.Activity, b: android.os.Bundle) {}

                override fun onActivityDestroyed(a: android.app.Activity) {}
            }
        )
    }
}
