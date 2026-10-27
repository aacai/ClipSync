package zhiqiu.app.cs.ui

import com.sun.jna.Library
import com.sun.jna.Native
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 加载 `libclipsyncrecord.dylib`。打包后在 Frameworks 里，开发时靠系统属性指过去。 */
internal object ScreenRecord {
    val isAvailable: Boolean
        get() = Mac.isMacOs && native != null

    private val native: RecordNative? by lazy { load() }

    fun active(): Boolean = native?.cs_record_active() == 1

    /** 失败时返回给界面看的原因；成功返回 null。 */
    fun start(
            region: RecordRegion?,
            mic: Boolean,
            systemAudio: Boolean,
            fps: Int,
            longEdge: Int,
            micDevice: String,
            path: String,
    ): String? {
        val lib = native ?: return "录屏组件没有装上"
        // 存着的设备拔掉了就退回系统默认：把不存在的 ID 交给 ScreenCaptureKit 只会让开录失败。
        val device = if (mic && micDevice.isNotEmpty() && inputs().none { it.id == micDevice }) "" else micDevice
        val code =
                lib.cs_record_start(
                        region?.x?.toDouble() ?: 0.0,
                        region?.y?.toDouble() ?: 0.0,
                        region?.width?.toDouble() ?: 0.0,
                        region?.height?.toDouble() ?: 0.0,
                        if (mic) 1 else 0,
                        if (systemAudio) 1 else 0,
                        fps,
                        longEdge,
                        device,
                        path,
                )
        return if (code == 0) null else lib.cs_record_last_error().ifBlank { "录屏失败" }
    }

    /** 本机可用的麦克风：设置页挑输入设备用。旧 dylib 没有这个符号就当没有可选设备。 */
    fun inputs(): List<RecordInput> {
        val lib = native ?: return emptyList()
        val raw = runCatching { lib.cs_record_inputs() }.getOrNull() ?: return emptyList()
        return runCatching {
            Json.parseToJsonElement(raw).jsonArray.mapNotNull { element ->
                val obj = element.jsonObject
                val id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val name = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                if (id.isEmpty()) null else RecordInput(id, name.ifBlank { id })
            }
        }.getOrDefault(emptyList())
    }

    fun stop(): String? {
        val lib = native ?: return null
        return if (lib.cs_record_stop() == 0) null else lib.cs_record_last_error().ifBlank { "停止录屏失败" }
    }

    /**
     * 把自己现在的窗口全部移出录制范围。停止条是开录之后才建出来的，开录那一刻的排除快照里没有它，
     * 所以要等它出现后重取一次可共享内容。
     */
    fun excludeSelf(): String? {
        val lib = native ?: return null
        // 装好的包里可能还是旧 dylib，没有这个符号；排除不掉只是把停止条录进去，不值得为此崩掉。
        val code = runCatching { lib.cs_record_exclude_self() }.getOrDefault(0)
        return if (code == 0) null else lib.cs_record_last_error().ifBlank { "无法排除本应用窗口" }
    }

    private fun load(): RecordNative? {
        val file = locate() ?: return null
        return runCatching { Native.load(file.absolutePath, RecordNative::class.java) }
                .onFailure { System.err.println("载入录屏组件失败：${file.absolutePath}：$it") }
                .getOrNull()
    }

    private fun locate(): File? {
        val fromProperty = System.getProperty("clipsync.record.lib")?.let { File(it) }
        if (fromProperty != null && fromProperty.isFile) return fromProperty
        // jpackage 的 java.home 是 Contents/runtime/Contents/Home，Frameworks 在 Contents 下；
        // 从启动器路径找更直接：Contents/MacOS/ClipSync -> Contents/Frameworks。
        val contents = System.getProperty("jpackage.app-path")?.let { File(it).parentFile?.parentFile }
        if (contents != null) {
            val bundled = File(contents, "Frameworks/libclipsyncrecord.dylib")
            if (bundled.isFile) return bundled
        }
        val home = System.getProperty("java.home")?.let { File(it) } ?: return null
        val bundled = File(home, "../../../Frameworks/libclipsyncrecord.dylib")
        return bundled.takeIf { it.isFile }
    }
}

private interface RecordNative : Library {
    fun cs_record_start(
            x: Double,
            y: Double,
            w: Double,
            h: Double,
            mic: Int,
            systemAudio: Int,
            fps: Int,
            longEdge: Int,
            micDevice: String,
            path: String,
    ): Int

    fun cs_record_stop(): Int

    fun cs_record_exclude_self(): Int

    fun cs_record_active(): Int

    fun cs_record_inputs(): String

    fun cs_record_last_error(): String
}
