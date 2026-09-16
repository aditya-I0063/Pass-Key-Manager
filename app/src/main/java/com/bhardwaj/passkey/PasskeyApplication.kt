package com.bhardwaj.passkey

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.bhardwaj.passkey.data.security.AppLockObserver
import javax.inject.Inject
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PasskeyApplication : Application() {

    @Inject
    lateinit var appLockObserver: AppLockObserver

    override fun onCreate() {
        super.onCreate()
        System.loadLibrary("sqlcipher")
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLockObserver)
    }
}