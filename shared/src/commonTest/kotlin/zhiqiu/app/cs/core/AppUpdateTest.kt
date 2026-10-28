package zhiqiu.app.cs.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 发布接口给的样子：tag_name + 各家安装包的直链。 */
private const val REAL_RELEASE =
        """
{
  "tag_name": "v0.0.6",
  "name": "ClipSync 0.0.6",
  "html_url": "https://github.com/aacai/ClipSync/releases/tag/v0.0.6",
  "draft": false,
  "prerelease": false,
  "assets": [
    {"name": "ClipSync-0.0.6-arm64.deb", "size": 68170174, "browser_download_url": "https://github.com/aacai/ClipSync/releases/download/v0.0.6/ClipSync-0.0.6-arm64.deb"},
    {"name": "ClipSync-0.0.6-arm64.dmg", "size": 73167785, "browser_download_url": "https://github.com/aacai/ClipSync/releases/download/v0.0.6/ClipSync-0.0.6-arm64.dmg"},
    {"name": "ClipSync-0.0.6-release.apk", "size": 2049330, "browser_download_url": "https://github.com/aacai/ClipSync/releases/download/v0.0.6/ClipSync-0.0.6-release.apk"},
    {"name": "ClipSync-0.0.6-x64.deb", "size": 68963806, "browser_download_url": "https://github.com/aacai/ClipSync/releases/download/v0.0.6/ClipSync-0.0.6-x64.deb"},
    {"name": "ClipSync-0.0.6-x64.msi", "size": 74969399, "browser_download_url": "https://github.com/aacai/ClipSync/releases/download/v0.0.6/ClipSync-0.0.6-x64.msi"}
  ]
}
"""

private const val FALLBACK = "https://github.com/aacai/ClipSync/releases"

class AppUpdateTest {

    @Test
    fun `a release decodes into version and assets`() {
        val release = decodeRelease(REAL_RELEASE)

        assertEquals("0.0.6", release.version)
        assertEquals("https://github.com/aacai/ClipSync/releases/tag/v0.0.6", release.pageUrl)
        assertEquals(5, release.assets.size)
        assertEquals("ClipSync-0.0.6-arm64.dmg", release.assets[1].name)
        assertEquals(73167785L, release.assets[1].size)
        assertEquals(
            "https://github.com/aacai/ClipSync/releases/download/v0.0.6/ClipSync-0.0.6-arm64.dmg",
            release.assets[1].url,
        )
    }

    @Test
    fun `a release without a tag is not usable`() {
        val e = assertFailsWith<UpdateException> { decodeRelease("""{"assets":[]}""") }
        assertEquals("发布信息里没有版本号", e.message)
    }

    @Test
    fun `the api explains itself when it refuses`() {
        val e = assertFailsWith<UpdateException> {
            decodeRelease("""{"message":"No public access to this repository"}""")
        }
        assertEquals("No public access to this repository", e.message)
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
    fun `the browser gets the package link, the release page, or nothing`() {
        val release = decodeRelease(REAL_RELEASE)
        val dmg = assertNotNull(release.assetFor("macOS", "arm64"))

        assertEquals(
            dmg.url,
            browserDownloadUrl(UpdateState.Available(release, dmg), FALLBACK),
            "有本机那份包就直接下那条",
        )
        assertEquals(
            release.pageUrl,
            browserDownloadUrl(UpdateState.Available(release, null), FALLBACK),
            "没有匹配的包就让人去发布页自己挑",
        )
        assertEquals(FALLBACK, browserDownloadUrl(UpdateState.Failed("超时"), FALLBACK))
        assertEquals(
            dmg.url,
            browserDownloadUrl(UpdateState.Downloading(dmg, 1_024L), FALLBACK),
            "下到一半卡住正是想跳浏览器的那一刻",
        )
        assertEquals("", browserDownloadUrl(UpdateState.Idle, FALLBACK))
        assertEquals("", browserDownloadUrl(UpdateState.UpToDate("9.9.9"), FALLBACK))
        assertEquals("", browserDownloadUrl(UpdateState.Checking, FALLBACK))
    }

    @Test
    fun `a release page missing from the payload leaves nothing to open`() {
        val release = decodeRelease("""{"tag_name":"v1.0.0","assets":[]}""")
        assertEquals("", release.pageUrl)
        assertEquals(
            FALLBACK,
            browserDownloadUrl(UpdateState.Available(release, null), FALLBACK),
            "没有 html_url 就退到发布列表",
        )
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

        assertEquals("ClipSync-0.0.6-arm64.dmg", release.assetFor("macOS", "arm64")?.name)
        assertEquals("ClipSync-0.0.6-x64.msi", release.assetFor("Windows", "x64")?.name)
        assertEquals("ClipSync-0.0.6-x64.deb", release.assetFor("Linux", "x64")?.name)
        assertEquals("ClipSync-0.0.6-arm64.deb", release.assetFor("Linux", "arm64")?.name)
        assertEquals("ClipSync-0.0.6-release.apk", release.assetFor("Android", "arm64")?.name)
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
