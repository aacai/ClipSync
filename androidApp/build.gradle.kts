import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

// 签名来源优先级：环境变量（CI Secrets）> androidApp/signing.properties（本地，gitignore）
val signingPropsFile = rootProject.file("androidApp/signing.properties")
val signingProps = Properties()
if (signingPropsFile.exists()) {
    signingPropsFile.inputStream().use { signingProps.load(it) }
}
fun signingProp(name: String, envName: String): String? =
    System.getenv(envName) ?: signingProps.getProperty(name)
val hasSigningConfig = signingProp("storeFile", "KEYSTORE_FILE") != null
    && signingProp("storePassword", "KEYSTORE_PASSWORD") != null
    && signingProp("keyAlias", "KEY_ALIAS") != null
    && signingProp("keyPassword", "KEY_PASSWORD") != null
dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

android {
    namespace = "zhiqiu.app.cs"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "zhiqiu.app.cs"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.0.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        // CI 注入 KEYSTORE_* 环境变量时使用正式签名；缺失时降级 debug 签名，保证构建不中断
        create("release") {
            if (hasSigningConfig) {
                storeFile = file(signingProp("storeFile", "KEYSTORE_FILE")!!)
                storePassword = signingProp("storePassword", "KEYSTORE_PASSWORD")
                keyAlias = signingProp("keyAlias", "KEY_ALIAS")
                keyPassword = signingProp("keyPassword", "KEY_PASSWORD")
            } else {
                initWith(getByName("debug"))
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}