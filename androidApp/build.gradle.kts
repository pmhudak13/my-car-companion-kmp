import java.util.Properties

// Read backend credentials from local.properties
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "org.mycarcompanion.androidapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.mycarcompanion.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 22
        versionName = "2.0.0"

        val supabaseUrl = localProperties["SUPABASE_URL"]?.toString()
            ?: error("SUPABASE_URL is missing from local.properties")
        val supabaseAnonKey = localProperties["SUPABASE_ANON_KEY"]?.toString()
            ?: error("SUPABASE_ANON_KEY is missing from local.properties")
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
        // SENTRY_DSN is optional; empty string disables Sentry (fine for local dev)
        val sentryDsn = localProperties["SENTRY_DSN"]?.toString() ?: ""
        buildConfigField("String", "SENTRY_DSN", "\"$sentryDsn\"")
    }

    signingConfigs {
        val storeFilePath = localProperties["RELEASE_STORE_FILE"]?.toString()
        if (storeFilePath != null) {
            create("release") {
                storeFile = file(storeFilePath)
                storePassword = localProperties["RELEASE_STORE_PASSWORD"]?.toString() ?: ""
                keyAlias = localProperties["RELEASE_KEY_ALIAS"]?.toString() ?: ""
                keyPassword = localProperties["RELEASE_KEY_PASSWORD"]?.toString() ?: ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            val releaseConfig = signingConfigs.findByName("release")
            if (releaseConfig != null) {
                signingConfig = releaseConfig
            }
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation(projects.shared)
    implementation(compose.components.resources)
    implementation(libs.koin.android)
    implementation(libs.androidx.activity.compose)
    implementation(libs.sentry.android)
    debugImplementation(libs.leakcanary.android)
}
