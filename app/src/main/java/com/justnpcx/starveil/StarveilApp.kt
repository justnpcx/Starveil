package com.justnpcx.starveil

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. Hilt graph root.
 *
 * Keep [onCreate] empty of heavy work: the skeleton deliberately does no
 * initialization here. Real init (kernel bridge, subscription scheduler)
 * belongs in explicit, testable initializers wired from here later.
 */
@HiltAndroidApp
class StarveilApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // SQLCipher is a native library and must be loaded before the first
        // database access; the Room instance is built lazily on first injection.
        System.loadLibrary("sqlcipher")
    }
}
