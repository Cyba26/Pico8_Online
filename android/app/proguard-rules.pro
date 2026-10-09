# Android runtime
-keep class androidx.** { *; }
-keep interface androidx.** { *; }

# Kotlin
-keep class kotlin.** { *; }
-keep interface kotlin.** { *; }

# Room Database
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.Database { *; }
-keep class * extends androidx.room.Entity { *; }
-keep class * extends androidx.room.Dao { *; }

# OkHttp
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**

# WebView
-keep class android.webkit.** { *; }

# Modèles de données
-keep class com.pico8.online.** { *; }
-keep interface com.pico8.online.** { *; }

# Nécessaire pour le code généré par Room
-keep class * implements androidx.room.Entity { *; }
-keep class * implements androidx.room.Dao { *; }
