package zhiqiu.app.cs

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import zhiqiu.app.cs.core.ClipSyncEngine
import java.util.UUID

/**
 * M1 end-to-end verification: spins up two in-process devices ("desktop" and "phone") in the same
 * room and checks that a text published by one is decrypted and received by the other.
 * Everything on the wire is AES-GCM encrypted with the room key.
 */
object SmokeMain {
    private const val TIMEOUT_MS = 25_000L

    @JvmStatic
    fun main(args: Array<String>): Unit = runBlocking {
        val room = System.getProperty("clipsync.smoke.room", "SMOKEROOM")
        val password = System.getProperty("clipsync.smoke.password", "")
        val sent = "smoke-${UUID.randomUUID()}"

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val deviceA = ClipSyncEngine(scope)
        val deviceB = ClipSyncEngine(scope)

        val received = CompletableDeferred<String>()
        scope.launch {
            deviceB.clips.onEach { received.complete(it.text) }.collect()
        }

        println("[smoke] room='$room' password=${if (password.isEmpty()) "(none)" else "'***'"}")
        deviceA.start(ClipSyncEngine.Config(room, password, "device-a", "Smoke Device A"))
        deviceB.start(ClipSyncEngine.Config(room, password, "device-b", "Smoke Device B"))

        scope.launch {
            deviceA.status.onEach { println("[smoke A] $it") }.collect()
        }
        scope.launch {
            deviceB.status.onEach { println("[smoke B] $it") }.collect()
        }

        check(awaitOnline(deviceA, TIMEOUT_MS)) { "A did not come online: ${deviceA.status.value}" }
        check(awaitOnline(deviceB, TIMEOUT_MS)) { "B did not come online: ${deviceB.status.value}" }

        deviceA.publishText(sent)
        println("[smoke] published: $sent")

        val got = withTimeoutOrNull(TIMEOUT_MS) { received.await() }
        println("[smoke] device B received: $got")
        println("[smoke] peers seen by A: ${deviceA.devices.value}")
        println("[smoke] peers seen by B: ${deviceB.devices.value}")

        deviceA.stop()
        deviceB.stop()

        if (got != sent) {
            System.err.println("[smoke] FAILED: expected '$sent' but got '$got'")
            kotlin.system.exitProcess(1)
        }
        println("[smoke] PASSED")
        kotlin.system.exitProcess(0)
    }

    private suspend fun awaitOnline(engine: ClipSyncEngine, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val status = engine.status.value
            if (status is ClipSyncEngine.Status.Online) return true
            if (status is ClipSyncEngine.Status.Offline) return false
            delay(100)
        }
        return false
    }
}
