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

# ---------- Compose / Voyager ----------
-keep class cafe.adriel.voyager.** { *; }

# ---------- Coroutines ----------
-dontwarn kotlinx.coroutines.**
