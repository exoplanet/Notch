package com.exoplanet.notch

import android.app.Application

class NotchApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotchEngine.attach(this)
    }
}
