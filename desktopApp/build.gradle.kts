import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "zhiqiu.app.cs.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            // --name：ClipSync.app / ClipSync-<版本>.dmg / .msi 的文件名都跟着它
            packageName = "ClipSync"
            packageVersion = "0.0.1"
            // deb 的 Package: 字段必须全小写，单独给一个合法名
            linux { packageName = "clipsync" }
            // jpackage 不让 dmg/msi 的主版本为 0，这两个内部版本号只能从 1 起；发布文件名仍按 0.0.1 打
            macOS { packageVersion = "1.0.0" }
            windows { packageVersion = "1.0.0" }
        }
    }
}
