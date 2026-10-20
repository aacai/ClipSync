import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJvmToolOperationTask
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

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

val appVersion: String by rootProject.extra
val jpackageVersion = "1." + appVersion.substringAfter('.')

compose.desktop {
    application {
        mainClass = "zhiqiu.app.cs.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            // ktor 的 java 引擎是反射加载的，jdeps 看不见，不点名就没有 java.net.http
            modules("java.net.http")
            packageName = "ClipSync"
            packageVersion = appVersion
            linux { packageName = "clipsync" }
            macOS {
                packageVersion = jpackageVersion
                iconFile.set(file("packaging/ClipSync.icns"))
            }
            windows {
                packageVersion = jpackageVersion
                // 生成开始菜单项（Win 键才能搜到），并往桌面放一个快捷方式
                menuGroup = "ClipSync"
                shortcut = true
            }
        }
    }
}

// macOS 的 skiko runtime jar 是双架构的：插件只把本机那份解成 Contents/app 里的 dylib，另一半还留在 jar 里进包，
// 而 -Dskiko.library.path=$APPDIR 让运行时不读它。Linux / Windows 的 jar 只有本机架构，解完就空了，那边没活可干。
tasks.matching { it.name == "createDistributable" }.configureEach {
    val appImagesDir = layout.buildDirectory.dir("compose/binaries/main/app")
    doLast {
        appImagesDir.get().asFile.walkTopDown()
            .filter { it.isFile && it.name.startsWith("skiko-awt-runtime-") && it.name.endsWith(".jar") }
            .forEach { jar ->
                val unpacked = jar.parentFile.list()?.toSet() ?: emptySet()
                val stripped = File(jar.parentFile, "${jar.name}.stripped")
                var dropped = 0
                ZipInputStream(jar.inputStream()).use { input ->
                    ZipOutputStream(stripped.outputStream()).use { output ->
                        while (true) {
                            val entry = input.nextEntry ?: break
                            val name = entry.name
                            if (name.startsWith("libskiko-") && name !in unpacked) {
                                dropped++
                            } else {
                                output.putNextEntry(ZipEntry(name))
                                input.copyTo(output)
                                output.closeEntry()
                            }
                        }
                    }
                }
                if (dropped == 0) stripped.delete()
                else {
                    jar.delete()
                    stripped.renameTo(jar)
                }
            }
    }
}

// jpackage 的 dmg 往卷根塞了背景图、卷图标、.DS_Store 和 .fseventsd，Applications 还是 Finder alias
// 而不是软链，开着「显示隐藏文件」的机器上就是一片层叠的文件夹。app 镜像已经建好了，
// 最后这层壳自己用 hdiutil 打，卷里只留 ClipSync.app 和 Applications。
val packageDmgFromAppImage = tasks.register<Exec>("packageDmgFromAppImage") {
    dependsOn("createDistributable")

    val appImages = layout.buildDirectory.dir("compose/binaries/main/app").get().asFile
    val staging = layout.buildDirectory.dir("compose/tmp/dmgStaging").get().asFile
    val dmg = layout.buildDirectory.file("compose/binaries/main/dmg/ClipSync-$jpackageVersion.dmg").get().asFile

    inputs.dir(appImages)
    outputs.file(dmg)

    commandLine(
        "sh", "-c",
        """
        set -eu
        rm -rf '$staging'
        mkdir -p '${dmg.parent}' '${staging.parent}'
        cp -R '${appImages}/.' '$staging/'
        ln -s /Applications '$staging/Applications'
        rm -f '$dmg'
        hdiutil create -volname ClipSync -srcfolder '$staging' -ov -format UDZO -imagekey zlib-level=9 '$dmg'
        """.trimIndent(),
    )
}

tasks.matching { it.name == "packageDmg" }.configureEach {
    // 不关掉它的话，jpackage 会把上面刚打好的那份盖回去。
    onlyIf { false }
    dependsOn(packageDmgFromAppImage)
}

// MSI 卸载/升级时若 ClipSync 仍在运行，.exe 被占用会导致 Windows Installer 只能推迟删除并弹出“需要重启”提示。
// 通过 jpackage 的 --resource-dir 覆盖默认 main.wxs（JDK21 即支持，无需升级 JDK）：
// 在删除文件前用 taskkill 强制结束进程，并抑制重启提示。仅挂在 Msi 任务上（其它任务无需自定义模板）。
tasks.withType<AbstractJvmToolOperationTask>().configureEach {
    if (name == "packageMsi" || name == "packageReleaseMsi") {
        freeArgs.addAll(
            "--resource-dir",
            rootProject.layout.projectDirectory.file("desktopApp/package/wix").asFile.absolutePath,
        )
    }
}
