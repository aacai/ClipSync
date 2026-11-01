package zhiqiu.app.cs.ui

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecycleCacheTest {

    private fun cache() = RecycleCache<String>(8)

    @Test
    fun `entries still on screen outlive the scrap window`() {
        val cache = cache()
        val keys = (0..19).map { "on-screen-$it" }
        keys.forEach {
            cache.acquire(it)
            cache.put(it, it)
        }
        keys.forEach { assertNotNull(cache.peek(it), "还在用的 $it 不该被回收") }
    }

    @Test
    fun `released entries shrink back to the scrap window`() {
        val cache = cache()
        val keys = (0..11).map { "released-$it" }
        keys.forEach {
            cache.acquire(it)
            cache.put(it, it)
        }
        keys.forEach { cache.release(it) }
        val left = keys.count { cache.peek(it) != null }
        assertTrue(left <= 8, "离屏的只该留回收池那么大，实际留了 $left")
        assertNull(cache.peek(keys.first()), "最早离屏的那张该先腾地方")
    }

    @Test
    fun `a second reader keeps the entry after the first lets go`() {
        val cache = cache()
        val shared = "shared-row"
        cache.acquire(shared)
        cache.acquire(shared)
        cache.put(shared, shared)
        cache.release(shared)
        (0..29).map { "crowd-$it" }.forEach {
            cache.acquire(it)
            cache.put(it, it)
            cache.release(it)
        }
        assertNotNull(cache.peek(shared), "还有行在看它，不该被挤掉")
    }

    @Test
    fun `a row that scrolls away decodes again on the way back`() {
        val cache = cache()
        val key = "far-row"
        cache.acquire(key)
        cache.put(key, key)
        cache.release(key)
        (0..19).map { "crowd-$it" }.forEach {
            cache.acquire(it)
            cache.put(it, it)
            cache.release(it)
        }
        assertNull(cache.peek(key), "离屏够久的该丢掉")
        cache.put(key, key)
        assertNotNull(cache.peek(key), "滚回来重新解出来还能用")
    }
}
