# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# SQLCipher.
# The shipped artifact is net.zetetic:sqlcipher-android, whose classes live in
# net.zetetic.database.sqlcipher.**. This module's own AAR supplies consumer ProGuard rules
# (keeping native methods, constructors and mNativeHandle), so no app-side keep rules are
# needed here - verified against app/build/outputs/mapping/release/mapping.txt, where all 61
# net.zetetic classes are kept unrenamed.
#
# The previous rules in this file referenced the legacy `net.sqlcipher.**` package, which this
# app has not used since migrating to sqlcipher-android. They matched nothing and were removed
# so they cannot be mistaken for load-bearing configuration.
