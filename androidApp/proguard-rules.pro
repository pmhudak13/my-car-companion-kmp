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
# Keep all Screen implementations so Voyager backstack serialization works across app updates.
# Without this, R8 renames LoginScreen/HomeScreen/etc. differently each build,
# causing ClassNotFoundException when Android restores saved state after an update.
-keep class org.mycarcompanion.app.ui.** implements cafe.adriel.voyager.core.screen.Screen { *; }

# ---------- Sentry ----------
-keep class io.sentry.** { *; }
-dontwarn io.sentry.**
-keepattributes SourceFile,LineNumberTable

# ---------- androidx.startup (reflection-based initializers) ----------
-keep class androidx.startup.** { *; }

# ---------- Firebase / ML Kit component registrars ----------
# Registrars are created by reflection from manifest meta-data. R8 full mode stripped the ML Kit
# registrars' no-arg constructors, so the VIN scanner's SharedPrefManager was never registered and
# GmsBarcodeScanning.getClient() NPE'd on every New Job / Add Vehicle screen (Sentry ANDROID-F, ANDROID-E).
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }

# ---------- Coroutines ----------
-dontwarn kotlinx.coroutines.**
# Keep the Android main-thread dispatcher factory which is loaded via ServiceLoader.
# Without this, release builds throw "No main thread dispatcher found" at runtime.
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}
# Volatile fields are updated via Atomic Field Updaters and must not be mangled.
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ---------- Serializable Screens ----------
# Screen data classes implement java.io.Serializable via CommonParcelable.
# Keep serialVersionUID so Android saved-state bundles survive R8 builds.
-keepclassmembers class org.mycarcompanion.app.ui.** implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
