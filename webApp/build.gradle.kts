import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName = "webApp"
        browser {
            commonWebpackConfig {
                outputFileName = "webApp.js"
            }
        }
        binaries.executable()
        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions {
                    freeCompilerArgs.add("-Xklib-duplicated-unique-name-strategy=allow-all-with-warning")
                }
            }
        }
    }

    sourceSets {
        val wasmJsMain by getting {
            dependencies {
                implementation(projects.shared)
                implementation(libs.ktor.client.js)
            }
        }
    }
}

// kotlinx-coroutines-core:1.9.0 ships an old kotlin-stdlib-wasm (Kotlin 1.9 era) KLIB
// that conflicts with kotlin-stdlib-wasm-js from Kotlin 2.x. Exclude it project-wide.
configurations.all {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-wasm")
}
