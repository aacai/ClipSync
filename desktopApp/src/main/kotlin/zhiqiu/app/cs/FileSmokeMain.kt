package zhiqiu.app.cs

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import zhiqiu.app.cs.core.ClipSyncEngine
import zhiqiu.app.cs.files.InMemoryClipStore
import zhiqiu.app.cs.files.MAX_FILE_BYTES
import zhiqiu.app.cs.files.ClipRepository
import java.security.MessageDigest
import java.util.UUID

/**
 * 文件剪贴板端到端验证：设备 A 发一个 ≤50MB 的文本文件（加密 → 上传到托管 → 广播元信息），
 * 设备 B 收到条目后自动下载、校验 sha256、解密入缓存。
 * 用法：./gradlew :desktopApp:fileSmoke [-Proom=<room>]
 */
object FileSmokeMain {
    private const val TIMEOUT_MS = 90_000L

    @JvmStatic
    fun main(args: Array<String>): Unit = runBlocking {
        val room = System.getProperty("clipsync.smoke.room", "FILESMOKE")
        val payload = "ClipSync 文件冒烟 \uD83D\uDCE1\npayload-${UUID.randomUUID()}\n".encodeToByteArray()
        val expectedSha = MessageDigest.getInstance("SHA-256").digest(payload)
            .joinToString("") { b -> ((b.toInt() and 0xFF) + 0x100).toString(16).substring(1) }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val deviceA = ClipSyncEngine(scope)
        val deviceB = ClipSyncEngine(scope)
        val storeA = InMemoryClipStore()
        val storeB = InMemoryClipStore()
        val repoA = ClipRepository(storeA)
        val repoB = ClipRepository(storeB)

        println("[fileSmoke] room='$room' payload=${payload.size}B sha256=$expectedSha")

        val received = CompletableDeferred<zhiqiu.app.cs.core.ClipSyncEngine.ClipEvent>()
        val cached = CompletableDeferred<zhiqiu.app.cs.files.FileClip>()
        scope.launch {
            deviceB.clips.first { it.file != null }.let { received.complete(it) }
        }
        scope.launch {
            // 下载完成会在仓库里落 localPath；轮询等它
            repoB.items.first { items -> items.any { it.file?.cached == true } }
                .first { it.file!!.cached }.file!!.let { cached.complete(it) }
        }

        deviceA.start(ClipSyncEngine.Config(room, "", "device-a", "File Smoke A", repository = repoA))
        deviceB.start(ClipSyncEngine.Config(room, "", "device-b", "File Smoke B", repository = repoB))
        check(awaitOnline(deviceA)) { "A 未上线：${deviceA.status.value}" }
        check(awaitOnline(deviceB)) { "B 未上线：${deviceB.status.value}" }

        // 1) 超限文件必须被本地拒绝
        val oversize = ByteArray((MAX_FILE_BYTES + 1).toInt())
        val rejected = deviceA.publishFile("huge.bin", "application/octet-stream", oversize)
        check(rejected == null) { "超限文件应该被拒绝" }
        println("[fileSmoke] oversize rejected OK")

        // 2) 正常文件：加密 → 上传 → 广播
        val item = deviceA.publishFile("smoke.txt", "text/plain", payload)
            ?: error("publishFile 被拒绝（见 fileErrors/日志）")
        check(item.file != null) { "发布条目缺少 file 元信息" }
        check(item.file!!.sha256 == expectedSha) { "sha256 不匹配" }
        check(item.file!!.size == payload.size.toLong()) { "大小不匹配" }
        println("[fileSmoke] A 已发布 ${item.text} → ${item.file!!.link}")

        // 3) B 收到条目（元信息解密成功）
        val event = withTimeoutOrNull(TIMEOUT_MS) { received.await() }
            ?: error("B 未收到文件条目")
        check(event.file != null && event.text == "smoke.txt") { "B 收到的条目不对：$event" }
        println("[fileSmoke] B 收到条目 ${event.text} (from ${event.senderName})")

        // 4) B 自动下载 → sha256 校验 → 解密 → 落缓存
        val file = withTimeoutOrNull(TIMEOUT_MS) { cached.await() }
            ?: error("B 未完成下载/解密/缓存")
        check(file.sha256 == expectedSha) { "B 侧 sha256 不匹配" }
        println("[fileSmoke] B 已缓存 ${file.name} localPath=${file.localPath}")

        // 5) A 侧历史也有这条
        val inHistory = repoA.items.value.any { it.id == item.id && it.file != null }
        check(inHistory) { "A 的历史里没有文件条目" }

        deviceA.stop()
        deviceB.stop()
        println("[fileSmoke] PASS")
    }

    private suspend fun awaitOnline(engine: ClipSyncEngine): Boolean =
        withTimeoutOrNull(25_000L) {
            engine.status.first { it is ClipSyncEngine.Status.Online }
            true
        } ?: false
}
