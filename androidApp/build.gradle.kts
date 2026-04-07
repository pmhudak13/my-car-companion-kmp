import java.util.Properties

// Read backend credentials from local.properties
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "org.mycarcompanion.androidapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.mycarcompanion.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 21
        versionName = "2.0.0"

        buildConfigField("String", "SUPABASE_URL", "\"${localProperties["SUPABASE_URL"] ?: ""}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProperties["SUPABASE_ANON_KEY"] ?: ""}\"")
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation(projects.shared)
    implementation(libs.koin.android)
    implementation(libs.androidx.activity.compose)
}
