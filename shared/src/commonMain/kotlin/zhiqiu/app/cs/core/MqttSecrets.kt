package zhiqiu.app.cs.core

// 公共 MQTT broker 的登录凭据，随包发布。通信内容是端到端加密的，broker 只能见到密文，
// 所以这对凭据泄露最多让人能连上 broker，读不到任何房间内容。换成自己的 broker 就改这一个文件。
internal object MqttSecrets {
    const val HOST: String = "b890fa3a.ala.cn-shenzhen.emqxsl.cn"
    const val SCHEME: String = "ssl"
    const val PORT_TLS: Int = 8883
    const val PORT_WSS: Int = 8084
    const val USERNAME: String = "clipsync"
    const val PASSWORD: String = "0ae0cf6e4016414fc46ee11c2ec4f2e3"
}
