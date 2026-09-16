import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.room)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics.plugin)
    alias(libs.plugins.firebase.perf.plugin)
}

android {
    namespace = "com.bhardwaj.passkey"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.bhardwaj.passkey"
        minSdk = 28
        targetSdk = 36
        versionCode = 45
        versionName = "5.6.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // local.properties is gitignored, so a fresh clone or CI checkout will not have it.
        // Loading it unconditionally threw FileNotFoundException and broke configuration.
        val localProperties = Properties().apply {
            val file = project.rootProject.file("local.properties")
            if (file.exists()) file.inputStream().use { load(it) }
        }
        // Renamed from PASS_PHRASE: this value only exists to open pre-5.7 databases that were
        // encrypted with the old build-time constant, so they can be re-keyed. Never use it for
        // new vaults. Note that when the key is absent the historical builds embedded the literal
        // string "null", which the migration also has to treat as a candidate.
        buildConfigField(
            "String",
            "LEGACY_PASS_PHRASE",
            "\"${localProperties.getProperty("PASS_PHRASE")}\""
        )

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        // The onboarding strings shipped in English to all 16 locales for several releases
        // because they were marked translatable="false", which also suppressed this check.
        // Failing the build is what stops that recurring.
        error += setOf(
            "MissingTranslation",
            "ExtraTranslation",
            "ImpliedQuantity",
            "StringFormatInvalid",
            "StringFormatMatches"
        )
        abortOnError = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    bundle {
        language {
            @Suppress("UnstableApiUsage")
            enableSplit = false
        }
    }
    room {
        schemaDirectory("$projectDir/schemas")
    }
    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

dependencies {
    // Android Essentials
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Dagger - Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // DataStore Preferences
    implementation(libs.datastore.preferences)

    // Biometric Authentication
    implementation(libs.androidx.biometric)

    // Room
    implementation(libs.androidx.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.androidx.room.ktx)
    androidTestImplementation(libs.androidx.room.testing)

    // SQL Cipher
    implementation(libs.sqlcipher.android)
    implementation(libs.sqlcipher.sqlite)

    // Open CSV - used by the read-only legacy .passkey CSV importer
    implementation(libs.opencsv)

    // Backup payload serialization
    implementation(libs.kotlinx.serialization.json)

    // Argon2id for the recovery-password key slot
    implementation(libs.argon2kt)

    // Reordering Lazy Columns
    implementation(libs.reorderable)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.perf)
    implementation(libs.firebase.config)

    // In App Updates
    implementation(libs.app.update)
    implementation(libs.app.update.ktx)

    // Testing & Debugging
    testImplementation(libs.junit)
    testImplementation(libs.google.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.google.truth)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}