package zhiqiu.app.cs.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecordingFileTest {

    private fun box(kind: String, payload: Int): ByteArray =
        byteArrayOf(
            (8 + payload ushr 24).toByte(),
            (8 + payload ushr 16).toByte(),
            (8 + payload ushr 8).toByte(),
            (8 + payload).toByte(),
        ) + kind.encodeToByteArray() + ByteArray(payload)

    private fun declared(kind: String, size: Int, payload: Int): ByteArray =
        byteArrayOf(
            (size ushr 24).toByte(),
            (size ushr 16).toByte(),
            (size ushr 8).toByte(),
            size.toByte(),
        ) + kind.encodeToByteArray() + ByteArray(payload)

    @Test
    fun `a clip without moov never finished writing`() {
        assertFalse(recordingFinished(box("ftyp", 16) + box("mdat", 512)))
        assertFalse(recordingFinished(ByteArray(0)))
        assertFalse(recordingFinished("ftyp".encodeToByteArray()))
    }

    @Test
    fun `moov anywhere in the top level means the clip is playable`() {
        assertTrue(recordingFinished(box("ftyp", 16) + box("mdat", 512) + box("moov", 240)))
        // faststart：moov 写在前面同样算完成。
        assertTrue(recordingFinished(box("ftyp", 16) + box("moov", 240) + box("mdat", 512)))
    }

    @Test
    fun `a box claiming more bytes than exist is a cut off file`() {
        assertFalse(recordingFinished(declared("mdat", 2048, 8)))
        // 长度 0 表示「一直到文件尾」，后面不可能再塞 moov。
        assertFalse(recordingFinished(box("ftyp", 16) + declared("mdat", 0, 8)))
    }
}
