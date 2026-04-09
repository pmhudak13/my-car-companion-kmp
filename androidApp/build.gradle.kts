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

// CMP resource bridge — the AGP 9+ KMP library plugin in :shared does not merge
// Compose Multiplatform resources into its AAR assets.  We copy them here with the
// module-qualified path that the generated resource accessors expect at runtime.
abstract class CopyComposeResources : DefaultTask() {
    @get:InputDirectory
    abstract val inputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val out = outputDir.get().asFile
            .resolve("composeResources/mycarcompanion.shared.generated.resources")
        out.mkdirs()
        inputDir.get().asFile.copyRecursively(out, overwrite = true)
    }
}

val copyComposeResources = tasks.register<CopyComposeResources>("copyComposeResources") {
    dependsOn(
        project(":shared").tasks.named("prepareComposeResourcesTaskForCommonMain"),
        project(":shared").tasks.named("copyNonXmlValueResourcesForCommonMain"),
        project(":shared").tasks.named("convertXmlValueResourcesForCommonMain"),
    )
    inputDir.set(project(":shared").layout.buildDirectory.dir(
        "generated/compose/resourceGenerator/preparedResources/commonMain/composeResources"
    ))
    outputDir.set(layout.buildDirectory.dir("composeAssets"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(
            copyComposeResources,
            CopyComposeResources::outputDir,
        )
    }
}

android {
    namespace = "org.mycarcompanion.androidapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.mycarcompanion.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 23
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
