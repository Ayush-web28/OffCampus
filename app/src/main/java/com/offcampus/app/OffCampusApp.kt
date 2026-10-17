package com.offcampus.app

import android.app.Activity
import android.app.Application
import android.os.Bundle

class OffCampusApp : Application(), Application.ActivityLifecycleCallbacks {

    companion object {
        @Volatile
        var isAppInForeground: Boolean = false
            private set
    }

    private var startedActivities = 0

    override fun onCreate() {
        super.onCreate()
        com.offcampus.app.data.ThemePreferences.init(this)
        registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityStarted(activity: Activity) {
        startedActivities++
        isAppInForeground = startedActivities > 0
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
        isAppInForeground = startedActivities > 0
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
