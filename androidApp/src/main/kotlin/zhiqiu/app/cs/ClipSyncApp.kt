package zhiqiu.app.cs

import android.app.Application
import zhiqiu.app.cs.core.ClipSyncAppContext

class ClipSyncApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ClipSyncAppContext.context = applicationContext
    }
}
