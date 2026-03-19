# --- MemPass Security Rules ---

# 1. Hilt/Dagger
-keep class dagger.hilt.** { *; }
-keep class com.example.mempass.di.** { *; }

# 2. Room Database
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# 3. Gson (Keep models for serialization)
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken
-keep public class * implements java.lang.reflect.Type
-keep class com.example.mempass.VaultEntities.** { *; }
-keep class com.example.mempass.VaultModels.** { *; }

# 4. Google API & Drive
-keep class com.google.api.services.drive.** { *; }
-keep class com.google.api.client.** { *; }
-dontwarn com.google.api.client.**
-dontwarn com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
-dontwarn com.google.common.collect.MinMaxPriorityQueue

# Apache HttpClient (referenced by Google Drive API)
-dontwarn org.apache.http.**
-dontwarn javax.naming.**
-dontwarn org.ietf.jgss.**

# 5. ML Kit & CameraX
-keep class com.google.mlkit.** { *; }
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# 6. Biometric
-keep class androidx.biometric.** { *; }

# 7. Kotlin Serialization/Coroutines
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature, SourceFile, LineNumberTable
-dontwarn kotlinx.coroutines.**

# 8. Argon2 & Crypto
-keep class com.lambdapioneer.argon2kt.** { *; }

# General Optimization
-repackageclasses ''
-allowaccessmodification
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*

# Security: Strip Debug and Verbose Logs in Release Builds
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
