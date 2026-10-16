package zhiqiu.app.cs.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 固定数据取自 v0.0.3 那次真发布，字段名和产物名都是 GitHub 原样。 */
private const val REAL_RELEASE =
        """
{
  "tag_name": "v0.0.3",
  "name": "v0.0.3",
  "draft": false,
  "prerelease": false,
  "assets": [
    {
      "name": "ClipSync-0.0.3-arm64.deb",
      "size": 63870608,
      "browser_download_url": "https://github.com/aacai/ClipSync/releases/download/v0.0.3/ClipSync-0.0.3-arm64.deb",
      "content_type": "application/octet-stream"
    },
    {
      "name": "ClipSync-0.0.3-arm64.dmg",
      "size": 68845575,
      "browser_download_url": "https://github.com/aacai/ClipSync/releases/download/v0.0.3/ClipSync-0.0.3-arm64.dmg"
    },
    {
      "name": "ClipSync-0.0.3-release.apk",
      "size": 2054514,
      "browser_download_url": "https://github.com/aacai/ClipSync/releases/download/v0.0.3/ClipSync-0.0.3-release.apk"
    },
    {
      "name": "ClipSync-0.0.3-x64.deb",
      "size": 64696196,
      "browser_download_url": "https://github.com/aacai/ClipSync/releases/download/v0.0.3/ClipSync-0.0.3-x64.deb"
    },
    {
      "name": "ClipSync-0.0.3-x64.msi",
      "size": 70696042,
      "browser_download_url": "https://github.com/aacai/ClipSync/releases/download/v0.0.3/ClipSync-0.0.3-x64.msi"
    },
    {
      "name": "web.zip",
      "size": 9749180,
      "browser_download_url": "https://github.com/aacai/ClipSync/releases/download/v0.0.3/web.zip"
    }
  ]
}
"""

class AppUpdateTest {

    @Test
    fun `the real release payload decodes into version and assets`() {
        val release = decodeRelease(REAL_RELEASE)

        assertEquals("0.0.3", release.version)
        assertEquals(6, release.assets.size)
        assertEquals("ClipSync-0.0.3-arm64.dmg", release.assets[1].name)
        assertEquals(68845575L, release.assets[1].size)
        assertTrue(release.assets[1].url.endsWith("/ClipSync-0.0.3-arm64.dmg"))
    }

    @Test
    fun `an api error message beats a missing version`() {
        val e =
                assertFailsWith<UpdateException> {
                    decodeRelease("""{"message":"API rate limit exceeded for 1.2.3.4."}""")
                }
        assertEquals("API rate limit exceeded for 1.2.3.4.", e.message)
    }

    @Test
    fun `html is not a release`() {
        assertFailsWith<UpdateException> { decodeRelease("<html><body>502 Bad Gateway</body></html>") }
    }

    @Test
    fun `a release without any usable asset keeps its version`() {
        val release = decodeRelease("""{"tag_name":"v9.9.9","assets":[{"name":"x","size":1}]}""")
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
        val release = decodeRelease(REAL_RELEASE)

        assertEquals("ClipSync-0.0.3-arm64.dmg", release.assetFor("macOS", "arm64")?.name)
        assertEquals("ClipSync-0.0.3-x64.msi", release.assetFor("Windows", "x64")?.name)
        assertEquals("ClipSync-0.0.3-x64.deb", release.assetFor("Linux", "x64")?.name)
        assertEquals("ClipSync-0.0.3-arm64.deb", release.assetFor("Linux", "arm64")?.name)
        assertEquals("ClipSync-0.0.3-release.apk", release.assetFor("Android", "arm64")?.name)
    }

    @Test
    fun `no installer when the machine has no matching package`() {
        val onlyArm = decodeRelease(
            """
            {"tag_name":"v1.0.0","assets":[
              {"name":"ClipSync-1.0.0-arm64.dmg","size":1,"browser_download_url":"https://g/a.dmg"}]}
            """
                    .trimIndent(),
        )
        assertNull(onlyArm.assetFor("macOS", "x64"))
        assertNull(onlyArm.assetFor("Web", ""))
        assertNull(onlyArm.assetFor("macOS", ""), "架构认不出来时不该赌一份包")
    }

    @Test
    fun `an untagged package wins over a foreign arch`() {
        val release = decodeRelease(
            """
            {"tag_name":"v1.0.0","assets":[
              {"name":"ClipSync-1.0.0-arm64.deb","size":1,"browser_download_url":"https://g/a.deb"},
              {"name":"ClipSync-1.0.0-universal.deb","size":1,"browser_download_url":"https://g/u.deb"}]}
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
