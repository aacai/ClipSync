import org.gradle.api.publish.maven.MavenPublication
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    `maven-publish`
}

group = "io.github.zhiqiu"
version = "0.1.0-SNAPSHOT"

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
kotlin {
    android {
        namespace = "zhiqiu.crypto"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    jvm {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    iosArm64()
    iosSimulatorArm64()

    // 纯 Kotlin 实现不依赖平台 API，web 端同样可用
    wasmJs {
        // 便于在 js/wasm 端编译与运行测试
        nodejs()
    }

    sourceSets {
        commonMain.dependencies {
            // 仅依赖 Kotlin 标准库，无第三方依赖
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        val jvmTest by getting {
            dependencies {
                implementation(libs.kotlin.testJunit)
            }
        }
    }
}

publishing {
    publications.withType<MavenPublication> {
        pom {
            name.set("crypto-core")
            description.set("纯 Kotlin 多平台轻量加密原语：SHA-256 / SHA-512 / HMAC-SHA256 / HMAC-SHA512 / PBKDF2-HMAC-SHA256 / AES-256-CTR / AES-256-CBC / AES-256-GCM / ChaCha20 / ChaCha20-Poly1305 / BLAKE2b / Argon2id（零第三方依赖，全平台可用）")
            url.set("https://github.com/zhiqiu/destiny-crypto")
            inceptionYear.set("2026")
            licenses {
                license {
                    name.set("MIT")
                    url.set("https://opensource.org/licenses/MIT")
                }
            }
            developers {
                developer {
                    id.set("zhiqiu")
                    name.set("zhiqiu")
                }
            }
            scm {
                url.set("https://github.com/zhiqiu/destiny-crypto")
                connection.set("scm:git:git://github.com/zhiqiu/destiny-crypto.git")
                developerConnection.set("scm:git:ssh://git@github.com/zhiqiu/destiny-crypto.git")
            }
        }
    }
}
