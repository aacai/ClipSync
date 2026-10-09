import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

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

// Broker credentials live in local.properties (gitignored) and are materialized into a
// gitignored source file at configuration time, so nothing secret ends up in version control.
// NOTE: the "App ID / App Secret" on the EMQX overview page are API credentials, NOT MQTT login
// credentials — create the MQTT user in the console:
//   部署 -> 访问控制 -> 客户端认证 -> 添加 (username / password)
val brokerDefaults = mapOf(
    "mqtt.host" to "b890fa3a.ala.cn-shenzhen.emqxsl.cn",
    "mqtt.scheme" to "ssl",
    "mqtt.portTls" to "8883",
    "mqtt.portWss" to "8084",
    "mqtt.username" to "CHANGE_ME",
    "mqtt.password" to "CHANGE_ME",
)
val brokerProps = Properties().apply {
    rootProject.layout.projectDirectory.file("local.properties").asFile
        .takeIf { it.exists() }
        ?.inputStream()
        ?.use { load(it) }
}
fun brokerProperty(key: String): String {
    val envKey = key.replace("mqtt.", "MQTT_").uppercase()
    val raw = System.getenv(envKey)
        ?: brokerProps.getProperty(key)
        ?: brokerDefaults.getValue(key)
    return raw.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")
}

val secretsFile = layout.projectDirectory
    .file("src/commonMain/kotlin/zhiqiu/app/cs/core/MqttSecrets.kt").asFile
secretsFile.parentFile.mkdirs()
secretsFile.writeText(
    """
    package zhiqiu.app.cs.core

    // GENERATED from local.properties by shared/build.gradle.kts — do not edit, do not commit.
    internal object MqttSecrets {
        const val HOST: String = "${brokerProperty("mqtt.host")}"
        const val SCHEME: String = "${brokerProperty("mqtt.scheme")}"
        const val PORT_TLS: Int = ${brokerProperty("mqtt.portTls")}
        const val PORT_WSS: Int = ${brokerProperty("mqtt.portWss")}
        const val USERNAME: String = "${brokerProperty("mqtt.username")}"
        const val PASSWORD: String = "${brokerProperty("mqtt.password")}"
    }
    """.trimIndent(),
)

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
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.mqtt.client.core)
            implementation(libs.mqtt.client.transport.ws)
            implementation(libs.cryptography.core)
            implementation(libs.cryptography.provider.optimal)
            implementation(libs.settings.core)
            implementation(libs.haze.core)
            implementation(libs.haze.blur)
            implementation(libs.haze.blur.materials)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        jvmMain.dependencies {
            implementation(libs.mqtt.client.transport.tcp)
            implementation(libs.ktor.client.java)
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
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}
