package com.rks.calculator.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 存档读取。
 *
 * Phigros 的存档位于应用私有目录，**普通应用无法直接访问**，需要 root。
 * 即使有 root，也存在两个现实限制：
 *
 *  1. Android 10+ 的 SELinux 策略可能阻止 `su -c cat` 读取 /data/data 下的文件；
 *  2. 多数 root 管理器（Magisk 等）默认对「未授权的应用」拒绝 su 请求，
 *     而 su 请求会在设备上弹出授权提示 —— 如果用户没点允许，就会失败。
 *
 * 因此这里提供三种途径，按可靠性排序：
 *  - 手动选择文件（最可靠，不需要 root）
 *  - root 直读
 *  - 公共存储里的备份文件
 */
object SaveReader {

    private const val PKG = "com.PigeonGames.Phigros"
    private const val PREFS = "com.PigeonGames.Phigros.v2.playerprefs.xml"

    /** 常见存档路径。 */
    private val CANDIDATE_PATHS = listOf(
        "/data/user/0/$PKG/shared_prefs/$PREFS",
        "/data/data/$PKG/shared_prefs/$PREFS",
        "/data/user_de/0/$PKG/shared_prefs/$PREFS",
    )

    /** 结果。 */
    sealed interface RootResult {
        data class Success(val xml: String, val path: String) : RootResult
        data object NoRoot : RootResult
        data object FileNotFound : RootResult
        data class Error(val message: String) : RootResult
    }

    /** su 可执行文件的位置。 */
    private val SU_PATHS = listOf("su", "/system/bin/su", "/system/xbin/su", "/sbin/su")

    /** 找到可用的 su。 */
    private fun findSu(): String? = SU_PATHS.firstOrNull { su ->
        runCatching {
            val p = ProcessBuilder(su, "-c", "id").redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            val ok = p.waitFor(6, TimeUnit.SECONDS)
            if (!ok) { p.destroy(); false } else out.contains("uid=0")
        }.getOrDefault(false)
    }

    /** 是否有 root。 */
    fun hasRoot(): Boolean = findSu() != null

    /**
     * 用 root 读取存档。
     *
     * 读取策略：
     *  1. 先试 `cat`（最快）
     *  2. 再试 `cp` 到 app 可读的临时目录后再读（绕过部分 SELinux 限制）
     */
    fun readWithRoot(): RootResult {
        val su = findSu() ?: return RootResult.NoRoot

        for (path in CANDIDATE_PATHS) {
            // 先确认文件存在
            val exists = runCatching {
                val p = ProcessBuilder(su, "-c", "ls \"$path\"")
                    .redirectErrorStream(true).start()
                val out = p.inputStream.bufferedReader().readText()
                p.waitFor(6, TimeUnit.SECONDS) && out.contains("playerprefs")
            }.getOrDefault(false)

            if (!exists) continue

            // 方式 1：cat
            runCatching {
                val p = ProcessBuilder(su, "-c", "cat \"$path\"")
                    .redirectErrorStream(true).start()
                val out = p.inputStream.bufferedReader().readText()
                val done = p.waitFor(15, TimeUnit.SECONDS)
                if (!done) { p.destroy(); return@runCatching null }
                if (p.exitValue() == 0 && out.contains("<map")) out else null
            }.getOrNull()?.let { return RootResult.Success(it, path) }
        }

        return RootResult.FileNotFound
    }

    /** 从 Uri 读取文本（用户通过文件选择器选中的文件）。 */
    fun readFromUri(context: Context, uri: Uri): String? {
        // 先直接按文本流读取
        runCatching {
            context.contentResolver.openInputStream(uri)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
        }.getOrNull()?.let { if (it.isNotBlank()) return it }

        // 部分 provider 需要先取显示名再判断编码，这里退化为按字节读
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                String(bytes, Charsets.UTF_8)
            }
        }.getOrNull()
    }

    /** 从本地 File 读取。 */
    fun readFromFile(file: File): String? = runCatching {
        if (file.exists() && file.canRead()) file.readText(Charsets.UTF_8) else null
    }.getOrNull()

    /** 应用外部私有目录里是否有备份（用户手动拷贝过来的情况）。 */
    fun findInExternalDirs(context: Context): File? {
        val dirs = listOfNotNull(
            context.getExternalFilesDir(null),
            File("/sdcard/Download"),
            File("/sdcard/Documents"),
        )
        return dirs.asSequence()
            .flatMap { d -> d.listFiles()?.asSequence() ?: emptySequence() }
            .firstOrNull { it.isFile && it.name.contains("playerprefs") && it.name.endsWith(".xml") }
    }
}
