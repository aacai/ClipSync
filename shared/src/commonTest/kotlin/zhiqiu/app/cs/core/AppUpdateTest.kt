package zhiqiu.app.cs.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 清单就是 update.json 的样子：版本 + 各家安装包在 x0.at 上的直链。 */
private const val REAL_MANIFEST =
        """
{
  "version": "0.0.6",
  "assets": [
    {"name": "ClipSync-0.0.6-arm64.deb", "size": 68170174, "url": "https://x0.at/ClipSync-0.0.6-arm64_Fi3a.deb"},
    {"name": "ClipSync-0.0.6-arm64.dmg", "size": 73167785, "url": "https://x0.at/ClipSync-0.0.6_Llkz.dmg"},
    {"name": "ClipSync-0.0.6-release.apk", "size": 2049330, "url": "https://x0.at/ClipSync-0.0.6-release_Qm2b.apk"},
    {"name": "ClipSync-0.0.6-x64.deb", "size": 68963806, "url": "https://x0.at/ClipSync-0.0.6-x64_Rt9c.deb"},
    {"name": "ClipSync-0.0.6-x64.msi", "size": 74969399, "url": "https://x0.at/ClipSync-0.0.6-x64_Hz4d.msi"}
  ]
}
"""

class AppUpdateTest {

    @Test
    fun `the manifest decodes into version and assets`() {
        val release = decodeManifest(REAL_MANIFEST)

        assertEquals("0.0.6", release.version)
        assertEquals(5, release.assets.size)
        assertEquals("ClipSync-0.0.6-arm64.dmg", release.assets[1].name)
        assertEquals(73167785L, release.assets[1].size)
        assertEquals("https://x0.at/ClipSync-0.0.6_Llkz.dmg", release.assets[1].url)
    }

    @Test
    fun `a manifest without a version is not usable`() {
        val e = assertFailsWith<UpdateException> { decodeManifest("""{"assets":[]}""") }
        assertEquals("更新清单里没有版本号", e.message)
    }

    @Test
    fun `html is not a release`() {
        assertFailsWith<UpdateException> { decodeManifest("<html><body>502 Bad Gateway</body></html>") }
    }

    @Test
    fun `a release without any usable asset keeps its version`() {
        val release = decodeManifest("""{"version":"v9.9.9","assets":[{"name":"x","size":1}]}""")
        assertEquals("9.9.9", release.version)
        assertTrue(release.assets.isEmpty())
    }

    @Test
    fun `versions compare segment by segment`() {
        assertTrue(isBehind("0.0.3", "0.0.4"))
        assertTrue(isBehind("0.9", "0.10"), "第二段是数字，不是字典序")
        assertTrue(isBehind("1.2.3", "2.0.0"))
        assertTrue(isBehind("v0.0.3", "v0.0.4"), "带 v 前缀也要能比")
        assertFalse(isBehind("0.0.4", "0.0.3"))
        assertFalse(isBehind("0.0.3", "0.0.3"))
        assertFalse(isBehind("0.0.3", "0.0"))
    }

    @Test
    fun `each system picks its own installer`() {
        val release = decodeManifest(REAL_MANIFEST)

        assertEquals("ClipSync-0.0.6-arm64.dmg", release.assetFor("macOS", "arm64")?.name)
        assertEquals("ClipSync-0.0.6-x64.msi", release.assetFor("Windows", "x64")?.name)
        assertEquals("ClipSync-0.0.6-x64.deb", release.assetFor("Linux", "x64")?.name)
        assertEquals("ClipSync-0.0.6-arm64.deb", release.assetFor("Linux", "arm64")?.name)
        assertEquals("ClipSync-0.0.6-release.apk", release.assetFor("Android", "arm64")?.name)
    }

    @Test
    fun `no installer when the machine has no matching package`() {
        val onlyArm = decodeManifest(
            """
            {"version":"v1.0.0","assets":[
              {"name":"ClipSync-1.0.0-arm64.dmg","size":1,"url":"https://g/a.dmg"}]}
            """
                    .trimIndent(),
        )
        assertNull(onlyArm.assetFor("macOS", "x64"))
        assertNull(onlyArm.assetFor("Web", ""))
        assertNull(onlyArm.assetFor("macOS", ""), "架构认不出来时不该赌一份包")
    }

    @Test
    fun `an untagged package wins over a foreign arch`() {
        val release = decodeManifest(
            """
            {"version":"v1.0.0","assets":[
              {"name":"ClipSync-1.0.0-arm64.deb","size":1,"url":"https://g/a.deb"},
              {"name":"ClipSync-1.0.0-universal.deb","size":1,"url":"https://g/u.deb"}]}
            """
                    .trimIndent(),
        )
        assertEquals("ClipSync-1.0.0-universal.deb", release.assetFor("Linux", "x64")?.name)
    }

    @Test
    fun `vendor arch names collapse to the two package tags`() {
        assertEquals("arm64", normalizeArch("aarch64"))
        assertEquals("arm64", normalizeArch("arm64-v8a"))
        assertEquals("x64", normalizeArch("amd64"))
        assertEquals("x64", normalizeArch("x86_64"))
        assertEquals("", normalizeArch("wasm32"))
    }
}
