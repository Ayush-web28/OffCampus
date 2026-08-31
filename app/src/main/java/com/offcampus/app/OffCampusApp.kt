package com.offcampus.app

import android.app.Application

/**
 * Reserved for app-wide setup (notification channels, FCM, etc.) added in later phases.
 * Firebase itself needs no explicit init call here — the Firebase Android SDK auto-initializes
 * from google-services.json via a ContentProvider declared in its own manifest.
 */
class OffCampusApp : Application()
