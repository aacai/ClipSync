package zhiqiu.app.cs.core

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

@Serializable
data class LanInfo(
    val port: Int,
    val addrs: List<String>,
    val nonce: String,
)

@Serializable
data class LanBeacon(
    val id: String,
    val name: String,
    val os: String,
    val port: Int,
    val addrs: List<String>,
    val roomHash: String,
    val nonce: String,
)

@Serializable
data class SharedRoot(
    val name: String,
    val path: String,
)

@Serializable
internal data class FsRootEntry(
    val name: String,
    val index: Int,
)

@Serializable
internal data class FsDirEntry(
    val name: String,
    val path: String,
)

@Serializable
internal data class FsFileEntry(
    val name: String,
    val size: Long,
    val path: String,
)

@Serializable
internal data class FsListEntry(
    val dirs: List<FsDirEntry>,
    val files: List<FsFileEntry>,
)

internal const val LAN_FS_HTML: String = """
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>ClipSync Files</title>
<style>
:root { color-scheme: dark; --bg:#111318; --card:#1b1f27; --line:#2b303b; --text:#e8eaf0; --muted:#9299a8; --accent:#7aa2ff; }
@media (prefers-color-scheme: light) { :root { color-scheme: light; --bg:#f6f7fa; --card:#fff; --line:#e2e5ec; --text:#20242c; --muted:#6b7280; --accent:#2f6fe4; } }
* { box-sizing:border-box; }
body { margin:0; background:var(--bg); color:var(--text); font:14px/1.5 -apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif; }
main { max-width:860px; margin:0 auto; padding:28px 18px 48px; }
h1 { font-size:20px; margin:0 0 4px; }
p.hint { color:var(--muted); margin:0 0 20px; }
.bar { display:flex; gap:10px; margin-bottom:16px; }
input { flex:1; min-width:0; padding:10px 12px; border:1px solid var(--line); border-radius:10px; background:var(--card); color:var(--text); outline:none; }
input:focus { border-color:var(--accent); }
button { font:inherit; color:var(--text); background:none; border:0; cursor:pointer; padding:0; }
button:hover { color:var(--accent); }
.cards { display:grid; grid-template-columns:repeat(auto-fill,minmax(180px,1fr)); gap:12px; margin-bottom:18px; }
.card { display:block; width:100%; text-align:left; padding:16px; border:1px solid var(--line); border-radius:14px; background:var(--card); }
.card strong { display:block; font-size:15px; margin-bottom:4px; }
.card small { color:var(--muted); word-break:break-all; }
.path { color:var(--muted); margin:0 0 10px; word-break:break-all; }
.path button { color:var(--accent); }
.path span { padding:0 4px; }
.row { display:flex; align-items:center; gap:10px; padding:10px 12px; border-bottom:1px solid var(--line); color:inherit; text-decoration:none; }
.row:hover { background:var(--card); }
.row .name { flex:1; min-width:0; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.row .meta { color:var(--muted); font-variant-numeric:tabular-nums; }
.list { border:1px solid var(--line); border-radius:14px; overflow:hidden; background:var(--card); }
.empty { padding:28px 12px; text-align:center; color:var(--muted); }
.error { padding:12px; border-radius:10px; color:#ffb4b4; background:rgba(255,80,80,.12); }
</style>
</head>
<body>
<main>
<h1>ClipSync Files</h1>
<p class="hint">Share files over this LAN. The link contains an access token — do not post it publicly.</p>
<div class="bar"><input id="search" type="search" placeholder="Search in this root" autocomplete="off" spellcheck="false"></div>
<div id="message"></div>
<div id="roots" class="cards"></div>
<div id="browser" hidden>
<p id="crumb" class="path"></p>
<div id="list" class="list"></div>
</div>
</main>
<script>
(function () {
  var token = new URLSearchParams(location.search).get('token') || '';
  var roots = [];
  var state = { root: -1, path: '' };
  var timer = null;
  function byId(id) { return document.getElementById(id); }
  function escapeHtml(value) {
    return String(value).replace(/[&<>"']/g, function (c) {
      return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];
    });
  }
  function api(name, extra) {
    var params = new URLSearchParams();
    params.set('token', token);
    Object.keys(extra || {}).forEach(function (key) { params.set(key, extra[key]); });
    return '/cs/v1/fs/' + name + '?' + params.toString();
  }
  function showError(error) {
    byId('message').innerHTML = '<div class="error">' + escapeHtml(error.message || error) + '</div>';
  }
  function json(path) {
    return fetch(path, {cache:'no-store'}).then(function (response) {
      if (!response.ok) throw new Error('HTTP ' + response.status);
      return response.json();
    });
  }
  var sizeUnits = ['B', 'KB', 'MB', 'GB'];
  function humanSize(size) {
    var value = size;
    var unit = 0;
    while (value >= 1024 && unit < sizeUnits.length - 1) { value /= 1024; unit++; }
    return (unit === 0 ? value : value.toFixed(1)) + ' ' + sizeUnits[unit];
  }
  function joinPath(base, name) { return base ? base + '/' + name : name; }
  function loadRoots() {
    byId('message').innerHTML = '';
    json(api('roots')).then(function (data) {
      roots = data;
      byId('roots').innerHTML = roots.map(function (root) {
        return '<button class="card" data-index="' + root.index + '"><strong>' + escapeHtml(root.name) +
          '</strong><small>' + escapeHtml(root.name) + '</small></button>';
      }).join('') || '<div class="empty">No shared folders yet.</div>';
      Array.prototype.forEach.call(byId('roots').querySelectorAll('button'), function (button) {
        button.onclick = function () { openRoot(Number(button.getAttribute('data-index'))); };
      });
    }).catch(showError);
  }
  function openRoot(index) {
    state.root = index;
    state.path = '';
    byId('roots').innerHTML = '';
    byId('browser').hidden = false;
    load();
  }
  function renderCrumb() {
    var parts = state.path ? state.path.split('/') : [];
    var html = '<button data-path="">' + escapeHtml(roots[state.root].name) + '</button>';
    var current = '';
    parts.forEach(function (part) {
      current = current ? current + '/' + part : part;
      html += '<span>/</span><button data-path="' + escapeHtml(current) + '">' + escapeHtml(part) + '</button>';
    });
    byId('crumb').innerHTML = html;
    Array.prototype.forEach.call(byId('crumb').querySelectorAll('button'), function (button) {
      button.onclick = function () { state.path = button.getAttribute('data-path'); load(); };
    });
  }
  function render(data) {
    renderCrumb();
    var html = '';
    data.dirs.forEach(function (dir) {
      html += '<div class="row dir" data-path="' + escapeHtml(dir.path) + '"><span class="name">📁 ' +
        escapeHtml(dir.name) + '</span></div>';
    });
    data.files.forEach(function (file) {
      html += '<a class="row file" href="' + escapeHtml(api('file', {root:state.root, path:file.path})) +
        '" download><span class="name">' + escapeHtml(file.name) + '</span><span class="meta">' +
        humanSize(file.size) + '</span></a>';
    });
    byId('list').innerHTML = html || '<div class="empty">Nothing here.</div>';
    Array.prototype.forEach.call(byId('list').querySelectorAll('.dir'), function (row) {
      row.onclick = function () { state.path = row.getAttribute('data-path'); load(); };
    });
  }
  function load() {
    byId('message').innerHTML = '';
    var extra = {root:state.root, path:state.path};
    var query = byId('search').value.trim();
    if (query) extra.q = query;
    json(api('list', extra)).then(render).catch(showError);
  }
  byId('search').addEventListener('input', function () {
    if (state.root < 0) return;
    clearTimeout(timer);
    timer = setTimeout(load, 250);
  });
  loadRoots();
})();
</script>
</body>
</html>
"""

data class LanPeer(
    val id: String,
    val name: String,
    val os: String,
    val addrs: List<String>,
    val port: Int,
    val nonce: String = "",
) {
    fun base(): String? = addrs.firstOrNull()?.let { "http://$it:$port" }
}

internal interface LanServer {
    val info: LanInfo
    fun close()
}

internal expect fun createLanServer(
    port: Int,
    identity: LanIdentity,
    roomHash: String,
    blobDir: String,
    fsToken: String,
    fsRoots: List<SharedRoot>,
    onClip: (String) -> Unit,
    onPeer: (LanBeacon) -> Unit,
): LanServer?

data class LanIdentity(
    val id: String,
    val name: String,
    val os: String,
)

internal expect fun lanBlobDir(): String

internal expect fun writeLanBlob(name: String, bytes: ByteArray): Boolean

internal expect fun readLanBlob(name: String): ByteArray?

/** 删除临时目录里超过 maxAgeMs 的文件，返回删除个数。 */
internal expect fun cleanupTempFiles(maxAgeMs: Long): Int

internal fun validLanBlobName(name: String): Boolean =
    name.isNotEmpty() && name.length <= 128 && !name.contains("..") &&
        name.all { it.isLetterOrDigit() || it in ".-_" }

internal fun lanBlobNameFromLink(link: String): String? {
    val marker = "/${ClipProtocol.BLOB_SEGMENT}/"
    val index = link.lastIndexOf(marker)
    if (index < 0) return null
    val name = link.substring(index + marker.length).substringBefore('?').trim()
    return name.takeIf(::validLanBlobName)
}

internal fun lanBlobNameForSha(sha256: String): String = "cs-$sha256.bin"

internal class LanSender(
    private val http: HttpClient,
    private val roomHash: String,
) {
    fun close() = http.close()

    suspend fun publish(peers: Collection<LanPeer>, wire: String) {
        val path = "/${ClipProtocol.ROOT}/$roomHash/${ClipProtocol.CH_CLIP}"
        for (peer in peers) {
            for (host in peer.addrs) {
                val ok = runCatching {
                    http.post("http://$host:${peer.port}$path") {
                        contentType(ContentType.Text.Plain)
                        setBody(wire)
                    }.status.isSuccess()
                }.getOrDefault(false)
                if (ok) break
            }
        }
    }

    suspend fun pushBlob(peers: Collection<LanPeer>, name: String, bytes: ByteArray) {
        if (!validLanBlobName(name) || peers.isEmpty()) return
        val path = "/${ClipProtocol.ROOT}/$roomHash/${ClipProtocol.BLOB_SEGMENT}/$name"
        for (peer in peers) {
            for (host in peer.addrs) {
                val ok = runCatching {
                    http.put("http://$host:${peer.port}$path") {
                        setBody(bytes)
                    }.status.isSuccess()
                }.getOrDefault(false)
                if (ok) break
            }
        }
    }
}
