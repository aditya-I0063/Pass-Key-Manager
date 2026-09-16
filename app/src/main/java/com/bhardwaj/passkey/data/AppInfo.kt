package com.bhardwaj.passkey.data

import com.bhardwaj.passkey.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Static facts about the running build.
 *
 * Read from BuildConfig rather than PackageManager: the previous code called getPackageInfo on
 * the main thread during ViewModel construction to obtain a value that cannot change.
 */
@Singleton
class AppInfo @Inject constructor() {
    val versionName: String = BuildConfig.VERSION_NAME
}
