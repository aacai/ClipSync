import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

compose.resources {
    packageOfResClass = "zhiqiu.app.cs.resources"
    publicResClass = false
}

// 版本号的唯一来源是根 build.gradle.kts 的 appVersion，这里把它烤成一个常量给 shared 用。
val appVersion: String = rootProject.extra["appVersion"] as String

val appVersionSource = layout.buildDirectory.dir("generated/appVersion/kotlin")

val writeAppVersion by tasks.registering {
    description = "生成 APP_VERSION 常量"
    val outDir = appVersionSource
    val version = appVersion
    inputs.property("appVersion", version)
    outputs.dir(outDir)
    doLast {
        val target = outDir.get().file("AppVersion.kt").asFile
        target.parentFile.mkdirs()
        target.writeText(
            """
            package zhiqiu.app.cs.core

            // 由根 build.gradle.kts 的 appVersion 生成，改版本号请改那边。
            const val APP_VERSION: String = "$version"
            """.trimIndent() + "\n"
        )
    }
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    android {
       namespace = "zhiqiu.app.cs.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()

       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
       withDeviceTestBuilder {
           sourceSetTreeName = "test"
       }.configure {
           instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
       }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.navigation.compose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(project(":crypto:crypto-core"))
            implementation(libs.mqtt.client.core)
            implementation(libs.mqtt.client.transport.ws)
            implementation(libs.cryptography.core)
            implementation(libs.cryptography.provider.optimal)
            implementation(libs.settings.core)
            implementation(libs.backdrop)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        jvmMain.dependencies {
            implementation(libs.mqtt.client.transport.tcp)
            implementation(libs.ktor.client.java)
            implementation(libs.jna)
            // 预览窗口里的播放器：Compose Desktop 没有视频控件，JavaFX 的 MediaView 是
            // 唯一不用额外装 VLC 就能放 H.264 MP4 的方案。
            val javafx = "21.0.5"
            val javafxClassifier =
                when {
                    System.getProperty("os.name").startsWith("Mac", ignoreCase = true) &&
                        System.getProperty("os.arch") == "aarch64" -> "mac-aarch64"
                    System.getProperty("os.name").startsWith("Mac", ignoreCase = true) -> "mac"
                    System.getProperty("os.name").startsWith("Windows", ignoreCase = true) -> "win"
                    else -> "linux"
                }
            implementation("org.openjfx:javafx-base:$javafx:$javafxClassifier")
            implementation("org.openjfx:javafx-graphics:$javafx:$javafxClassifier")
            implementation("org.openjfx:javafx-media:$javafx:$javafxClassifier")
            implementation("org.openjfx:javafx-swing:$javafx:$javafxClassifier")
        }
        androidMain.dependencies {
            implementation(libs.mqtt.client.transport.tcp)
            implementation(libs.ktor.client.cio)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
            implementation(libs.androidx.activity.compose)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
            implementation(libs.ktor.client.websockets)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.settings.test)
        }
    }
}

kotlin.sourceSets.getByName("commonMain").kotlin.srcDir(writeAppVersion)

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}
