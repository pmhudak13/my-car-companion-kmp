# ---------- App classes ----------
-keep class org.mycarcompanion.app.MyApp { *; }
-keep class org.mycarcompanion.app.MainActivity { *; }

# ---------- kotlinx.serialization ----------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class org.mycarcompanion.app.data.models.**$$serializer { *; }
-keepclassmembers class org.mycarcompanion.app.data.models.** {
    *** Companion;
}
-keepclasseswithmembers class org.mycarcompanion.app.data.models.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---------- Ktor / OkHttp ----------
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# ---------- Supabase ----------
-keep class io.github.jan.supabase.** { *; }
-dontwarn io.github.jan.supabase.**

# ---------- Koin ----------
-keep class org.koin.** { *; }
-keep class org.mycarcompanion.app.di.** { *; }
-keep class org.mycarcompanion.app.ui.**ScreenModel { *; }
-keep class org.mycarcompanion.app.data.repository.** { *; }

# ---------- Compose / Voyager ----------
-keep class cafe.adriel.voyager.** { *; }

# ---------- Sentry ----------
-keep class io.sentry.** { *; }
-dontwarn io.sentry.**
-keepattributes SourceFile,LineNumberTable

# ---------- androidx.startup (reflection-based initializers) ----------
-keep class androidx.startup.** { *; }

# ---------- Coroutines ----------
-dontwarn kotlinx.coroutines.**
