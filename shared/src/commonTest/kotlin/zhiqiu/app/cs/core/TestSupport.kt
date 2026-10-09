package zhiqiu.app.cs.core

import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/** Minimal suspend-test helper — no extra test framework dependency. */
internal fun runBlockingTest(block: suspend () -> Unit) = runBlocking { block() }
