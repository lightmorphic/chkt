package org.chkt.app

import android.app.Application
import org.chkt.app.alarm.Notifications

class ChktApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
    }
}
