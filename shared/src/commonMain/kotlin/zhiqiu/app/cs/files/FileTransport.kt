package zhiqiu.app.cs.files

/** 引擎上传下载密文只认这一层：真机走托管方，测试里换成记账的假件。 */
interface FileTransport {
    suspend fun upload(
        fileName: String,
        bytes: ByteArray,
        onProgress: (sent: Long, total: Long) -> Unit = { _, _ -> },
    ): UploadedLink

    suspend fun download(
        link: String,
        onProgress: (received: Long, total: Long) -> Unit = { _, _ -> },
    ): ByteArray
}

/** 每开一次会话要一份新的传输层，测试从这里塞假的进来。 */
fun interface FileTransportFactory {
    fun create(): FileTransport
}

internal val DefaultFileTransports = FileTransportFactory { FileTransferClient(createSharedHttpClient()) }
