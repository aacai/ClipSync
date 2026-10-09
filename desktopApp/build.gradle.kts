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
            packageName = "zhiqiu.app.cs"
            packageVersion = "1.0.0"
        }
    }
}

// End-to-end check against the real broker: two in-process devices exchange an encrypted clip.
// Usage: ./gradlew :desktopApp:smoke -Proom=<roomCode> [-Ppassword=<pw>]
// File clipboard end-to-end: encrypt -> upload -> announce -> download/verify/cache.
// Usage: ./gradlew :desktopApp:fileSmoke [-Proom=<roomCode>]
tasks.register<JavaExec>("fileSmoke") {
    group = "verification"
    description = "Verifies the file/image clipboard path end-to-end (upload host reachable required)."
    mainClass.set("zhiqiu.app.cs.FileSmokeMain")
    classpath = sourceSets["main"].runtimeClasspath
    systemProperty("clipsync.smoke.room", providers.gradleProperty("room").getOrElse("FILESMOKE"))
}

tasks.register<JavaExec>("smoke") {
    group = "verification"
    description = "Runs two simulated devices and verifies an encrypted clipboard roundtrip."
    mainClass.set("zhiqiu.app.cs.SmokeMain")
    classpath = sourceSets["main"].runtimeClasspath
    systemProperty("clipsync.smoke.room", providers.gradleProperty("room").getOrElse("SMOKEROOM"))
    systemProperty("clipsync.smoke.password", providers.gradleProperty("password").getOrElse(""))
}