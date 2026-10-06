package com.rks.calculator.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.rks.calculator.util.AppLog
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 存档读取。
 *
 * Phigros 的存档位于应用私有目录，普通应用无法直接访问，需要 root；
 * 且即使有 root，Android 10+ 的 SELinux 与 root 管理器授权也可能阻止读取。
 *
 * 因此提供三条途径（按可靠性排序）：
 *  1. **手动选择文件** —— 最可靠，不需要 root
 *  2. 从公共存储自动查找 —— 免去选文件的操作
 *  3. root 直读 —— 成功率取决于设备
 *
 * 关于「读不到文件」的常见原因：
 *  - 系统文件选择器若只用通配 MIME，部分机型只显示媒体文件，`.xml` 会被隐藏
 *    → 本工具提供 `application/xml` / `text/xml` 等多种 MIME 兜底，
 *      并提供「从 Download 自动查找」按钮绕开选择器
 *  - `content://` URI 读取失败（provider 不给权限）
 *    → 读取失败时返回具体错误信息，而不是笼统的「内容为空」
 */
object SaveReader {

    private const val PKG = "com.PigeonGames.Phigros"
    private const val PREFS = "com.PigeonGames.Phigros.v2.playerprefs.xml"

    /** 存档候选路径（需要 root）。 */
    private val CANDIDATE_PATHS = listOf(
        "/data/user/0/$PKG/shared_prefs/$PREFS",
        "/data/data/$PKG/shared_prefs/$PREFS",
        "/data/user_de/0/$PKG/shared_prefs/$PREFS",
    )

    /** 文件选择器可用的 MIME 类型（覆盖 .xml 的多种声称方式）。 */
    val PICKER_MIME_TYPES = arrayOf(
        "application/xml",
        "text/xml",
        "text/plain",
        "application/octet-stream",
        "*/*",
    )

    // ---------------- 结果类型 ----------------

    /** 读取结果（带具体失败原因，便于界面提示）。 */
    sealed interface ReadResult {
        data class Success(val xml: String, val source: String) : ReadResult

        /** 文件本身读到了，但没有内容。 */
        data object EmptyFile : ReadResult

        /** 没有权限读取（Scoped Storage / provider 拒绝）。 */
        data class NoPermission(val detail: String) : ReadResult

        /** 找不到文件。 */
        data object NotFound : ReadResult

        data class Error(val message: String) : ReadResult
    }

    /** root 读取结果。 */
    sealed interface RootResult {
        data class Success(val xml: String, val path: String) : RootResult
        data object NoRoot : RootResult
        data object FileNotFound : RootResult
        data class Error(val message: String) : RootResult
    }

    // ---------------- 公共存储：自动查找 ----------------

    /**
     * 免选文件：直接在常见目录里找解密后的存档。
     * 适用于文件选择器看不到 .xml 的情况。
     */
    fun findInPublicStorage(context: Context): ReadResult {
        val dirs = buildList {
            add(File("/sdcard/Download"))
            add(File("/sdcard/Documents"))
            add(File("/sdcard"))
            context.getExternalFilesDir(null)?.let { add(it) }
        }

        val candidates = ArrayList<File>()
        for (dir in dirs) {
            val files = runCatching { dir.listFiles() }.getOrNull() ?: continue
            for (f in files) {
                if (!f.isFile) continue
                val n = f.name.lowercase()
                if (n.endsWith(".xml") && (n.contains("playerprefs") || n.contains("phigros"))) {
                    candidates += f
                }
            }
        }

        if (candidates.isEmpty()) return ReadResult.NotFound

        // 优先选「已解密」的那个（文件名常带 processed/decrypted，或内容含 Record.）
        val sorted = candidates.sortedByDescending { f ->
            val n = f.name.lowercase()
            var score = 0
            if (n.contains("processed")) score += 10
            if (n.contains("decrypt")) score += 10
            score
        }

        for (file in sorted) {
            val text = runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
            if (text.isNullOrBlank()) continue
            if (SaveParser.looksDecrypted(text)) {
                return ReadResult.Success(text, file.absolutePath)
            }
        }

        // 都没解密，就把第一个文件的内容返回，交由上层提示「这是加密的」
        val first = sorted.first()
        val text = runCatching { first.readText(Charsets.UTF_8) }.getOrNull()
        return if (text.isNullOrBlank()) {
            ReadResult.EmptyFile
        } else {
            ReadResult.Success(text, first.absolutePath)
        }
    }

    // ---------------- 手动选择：content:// URI ----------------

    /**
     * 从用户选择的 URI 读取。
     *
     * 依次尝试：
     *  1. `ContentResolver.openInputStream`（最常见）
     *  2. 如果 URI 是 `file://`，直接读路径
     */
    fun readFromUri(context: Context, uri: Uri): ReadResult {
        AppLog.i("SaveReader", "读取 URI：$uri  scheme=${uri.scheme}")
        // file:// 协议：直接按路径读
        if (uri.scheme == "file") {
            val path = uri.path
            if (path != null) {
                val f = File(path)
                if (!f.exists()) return ReadResult.NotFound
                val text = runCatching { f.readText(Charsets.UTF_8) }.getOrNull()
                return if (text.isNullOrBlank()) ReadResult.EmptyFile
                else ReadResult.Success(text, path)
            }
        }

        // content:// 协议
        val openError = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                if (bytes.isEmpty()) return ReadResult.EmptyFile
                ReadResult.Success(String(bytes, Charsets.UTF_8), displayName(context, uri))
            } ?: ReadResult.NotFound
        }.getOrElse { e ->
            val msg = e.message ?: e.javaClass.simpleName
            if (msg.contains("Permission", ignoreCase = true) ||
                msg.contains("denied", ignoreCase = true) ||
                e is SecurityException
            ) {
                ReadResult.NoPermission(msg)
            } else {
                ReadResult.Error(msg)
            }
        }
        return openError
    }

    /** 取出 URI 对应的显示名，便于界面展示来源。 */
    private fun displayName(context: Context, uri: Uri): String {
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) {
                    val name = c.getString(idx)
                    if (!name.isNullOrBlank()) return name
                }
            }
        }
        return uri.lastPathSegment ?: uri.toString()
    }

    // ---------------- root ----------------

    private val SU_PATHS = listOf("su", "/system/bin/su", "/system/xbin/su", "/sbin/su")

    private fun findSu(): String? = SU_PATHS.firstOrNull { su ->
        runCatching {
            val p = ProcessBuilder(su, "-c", "id").redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            val ok = p.waitFor(6, TimeUnit.SECONDS)
            if (!ok) { p.destroy(); false } else out.contains("uid=0")
        }.getOrDefault(false)
    }

    fun hasRoot(): Boolean = findSu() != null

    /** 用 root 读取存档。 */
    fun readWithRoot(): RootResult {
        AppLog.i("SaveReader", "尝试 root 读取")
        val su = findSu() ?: run {
            AppLog.w("SaveReader", "未找到可用的 su")
            return RootResult.NoRoot
        }
        AppLog.i("SaveReader", "找到 su：$su")

        for (path in CANDIDATE_PATHS) {
            AppLog.i("SaveReader", "尝试路径：$path")
            val text = runCatching {
                val p = ProcessBuilder(su, "-c", "cat \"$path\"")
                    .redirectErrorStream(true).start()
                val out = p.inputStream.bufferedReader().readText()
                val done = p.waitFor(15, TimeUnit.SECONDS)
                if (!done) { p.destroy(); return@runCatching null }
                if (p.exitValue() == 0 && out.contains("<map")) out else null
            }.getOrNull()

            if (text != null) {
                AppLog.i("SaveReader", "读取成功：$path，长度=${text.length}")
                return RootResult.Success(text, path)
            }
            AppLog.w("SaveReader", "该路径读取失败：$path")
        }
        AppLog.w("SaveReader", "所有候选路径均失败")
        return RootResult.FileNotFound
    }
}
