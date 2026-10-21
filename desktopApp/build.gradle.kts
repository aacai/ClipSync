import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
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

        // 只有 packageRelease* / createReleaseDistributable 这条任务链会过 ProGuard，
        // 跑 packageDmg 时下面这份规则完全不参与，这是桌面打包最常见的踩坑点。
        buildTypes.release.proguard {
            configurationFiles.from(project.file("proguard-desktop.pro"))
        }
    }
}

// jpackage 的 Apple 打包器不接受首段为 0 的 app-version，只能先拿 1.0.6 过打包校验，再把版本号写回 appVersion。
// 后处理动了包里的文件，签名 seal 会失效，所以按包内原有 entitlements 重签一次。
// macOS 的 skiko runtime jar 是双架构的：插件只把本机那份解成 Contents/app 里的 dylib，另一半还留在 jar 里进包，
// 而 -Dskiko.library.path=$APPDIR 让运行时不读它。Linux / Windows 的 jar 只有本机架构，解完就空了，那边没活可干。
tasks.matching { it.name == "createDistributable" || it.name == "createReleaseDistributable" }.configureEach {
    // 两条任务链各出一份 app 镜像，后处理对两边做同一件事，所以直接扫整个 binaries 目录
    val binariesDir = layout.buildDirectory.dir("compose/binaries").get().asFile
    val outward = appVersion
    val packed = jpackageVersion
    doLast {
        // 工具调用全写在闭包里：调脚本顶层函数或读脚本顶层属性会把 action 绑到 script 对象上，configuration cache 存不下。
        fun runTool(vararg command: String) {
            val process = ProcessBuilder(command.toList()).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            if (process.waitFor() != 0) throw GradleException("${command.first()} 失败：$output")
        }

        binariesDir.walkTopDown()
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

        binariesDir.walkTopDown()
            .filter { it.isDirectory && it.name.endsWith(".app") }
            .forEach { app ->
                val plist = File(app, "Contents/Info.plist").path
                for (key in listOf("CFBundleShortVersionString", "CFBundleVersion")) {
                    runTool("plutil", "-replace", key, "-string", outward, plist)
                }
                val cfg = File(app, "Contents/app/${app.nameWithoutExtension}.cfg")
                if (cfg.isFile) {
                    cfg.writeText(cfg.readText().replace("-Djpackage.app-version=$packed", "-Djpackage.app-version=$outward"))
                }
                val ent = File(app.parentFile, "${app.name}.entitlements.plist")
                // 转储 entitlements 时 stderr 不能并进文件：codesign 会打一行 "Executable=…"，进了就是非法 blob。
                val dumped = ProcessBuilder("codesign", "-d", "--entitlements", ":-", app.path)
                    .redirectOutput(ent)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
                    .waitFor() == 0 && ent.length() > 0
                val entitlements = if (dumped) listOf("--entitlements", ent.path) else emptyList()
                runTool("codesign", "--force", "--options", "runtime", "--sign", "-", *entitlements.toTypedArray(), app.path)
                ent.delete()
            }
    }
}

// jpackage 的 dmg 往卷根塞了背景图、卷图标、.DS_Store 和 .fseventsd，Applications 还是 Finder alias
// 而不是软链，开着「显示隐藏文件」的机器上就是一片层叠的文件夹。app 镜像已经建好了，
// 最后这层壳自己用 hdiutil 打，卷里只留 ClipSync.app 和 Applications。
fun registerDmgFromAppImage(binaryDirName: String, taskName: String, createTask: String) =
    tasks.register<Exec>(taskName) {
        dependsOn(createTask)

        val appImages = layout.buildDirectory.dir("compose/binaries/$binaryDirName/app").get().asFile
        val staging = layout.buildDirectory.dir("compose/tmp/${binaryDirName}DmgStaging").get().asFile
        val dmg = layout.buildDirectory.file("compose/binaries/$binaryDirName/dmg/ClipSync-$appVersion.dmg").get().asFile

        inputs.dir(appImages)
        outputs.file(dmg)

        commandLine(
            "sh", "-c",
            """
            set -eu
            rm -rf '$staging' '${dmg.parent}'
            mkdir -p '${dmg.parent}' '${staging.parent}'
            cp -R '${appImages}/.' '$staging/'
            ln -s /Applications '$staging/Applications'
            hdiutil create -volname ClipSync -srcfolder '$staging' -ov -format UDZO -imagekey zlib-level=9 '$dmg'
            """.trimIndent(),
        )
    }

val dmgMain = registerDmgFromAppImage("main", "packageDmgFromAppImage", "createDistributable")
val dmgRelease = registerDmgFromAppImage("main-release", "packageReleaseDmgFromAppImage", "createReleaseDistributable")

tasks.matching { it.name == "packageDmg" || it.name == "packageReleaseDmg" }.configureEach {
    // 不关掉它的话，jpackage 会把上面刚打好的那份盖回去。
    onlyIf { false }
    dependsOn(if (name == "packageDmg") dmgMain else dmgRelease)
}

// 单机小工具的 JVM 默认值全按服务器调：-Xms 取物理内存的 1/64（16G 机器上 256M）一开机就占住，
// G1 的 remember-set、240M code cache、不设限的 Metaspace 也一样留着，而实际堆只用十几 M。
// 同一份包实测：空闲 footprint 299M → 212~235M，峰值 405M → 324~341M，启动时间没变。
val jvmMemoryOptions = listOf(
    "-Xms32m",
    "-Xmx512m",
    "-XX:SoftMaxHeapSize=192m",
    "-XX:MinHeapFreeRatio=1",
    "-XX:MaxHeapFreeRatio=10",
    "-XX:MaxMetaspaceSize=256m",
    "-XX:ReservedCodeCacheSize=96m",
    "-XX:+UseSerialGC",
)

tasks.withType<AbstractJPackageTask>().configureEach {
    // 每项各自一条：@argfile 按空白切分，合成一行的话后面的选项会被 jpackage 当成自己的参数报「无效」。
    jvmMemoryOptions.forEach { freeArgs.addAll(listOf("--java-options", it)) }
}

tasks.withType<AbstractJvmToolOperationTask>().configureEach {
    // MSI 卸载/升级时若 ClipSync 仍在运行，.exe 被占用会导致 Windows Installer 只能推迟删除并弹出“需要重启”提示。
    // 通过 jpackage 的 --resource-dir 覆盖默认 main.wxs（JDK21 即支持，无需升级 JDK）：
    // 在删除文件前用 taskkill 强制结束进程，并抑制重启提示。仅挂在 Msi 任务上（其它任务无需自定义模板）。
    if (name == "packageMsi" || name == "packageReleaseMsi") {
        freeArgs.addAll(
            "--resource-dir",
            rootProject.layout.projectDirectory.file("desktopApp/package/wix").asFile.absolutePath,
        )
    }
}
