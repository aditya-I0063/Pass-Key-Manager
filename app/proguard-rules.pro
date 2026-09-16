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

# Argon2 (recovery-password key slot).
# The argon2kt AAR ships no consumer rules of its own. Argon2Jni survives today only because of
# the default "-keepclasseswithmembernames class * { native <methods>; }" rule, and the JNI layer
# also reaches ByteBufferTarget's members directly. Relying on that indirectly in the vault
# unlock path is not worth the risk, so the binding is kept explicitly.
-keep class com.lambdapioneer.argon2kt.Argon2Jni { *; }
-keep class com.lambdapioneer.argon2kt.Argon2JniVerification { *; }
-keepclassmembers class com.lambdapioneer.argon2kt.ByteBufferTarget { *; }
-keepclasseswithmembernames class com.lambdapioneer.argon2kt.** {
    native <methods>;
}
