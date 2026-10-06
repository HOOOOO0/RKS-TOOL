package com.rks.calculator.data

import android.content.Context
import java.io.File

/**
 * 存档读取。
 *
 * 优先用 root 直接读取 Phigros 的 SharedPreferences 文件；
 * 没有 root 或读取失败时，由界面引导用户去 PhiCrypto 解密，
 * 再用系统文件选择器把解密后的 xml 交给 App。
 */
object SaveReader {

    /** Phigros 存档路径（需要 root 才能访问）。 */
    const val SAVE_PATH = "/data/user/0/com.PigeonGames.Phigros/shared_prefs/" +
        "com.PigeonGames.Phigros.v2.playerprefs.xml"

    /** 备用路径：部分设备/多用户环境下 data 目录不同。 */
    private val CANDIDATE_PATHS = listOf(
        SAVE_PATH,
        "/data/data/com.PigeonGames.Phigros/shared_prefs/" +
            "com.PigeonGames.Phigros.v2.playerprefs.xml",
    )

    /** root 读取的结果。 */
    sealed interface RootResult {
        /** 读取成功。 */
        data class Success(val xml: String, val path: String) : RootResult

        /** 没有 root 权限。 */
        data object NoRoot : RootResult

        /** 有 root，但文件不存在（没玩过 / 路径不对）。 */
        data object FileNotFound : RootResult

        /** 其它错误。 */
        data class Error(val message: String) : RootResult
    }

    /**
     * 是否具备 root。判断方式是尝试执行 `su -c id` 并检查输出是否含 uid=0。
     */
    fun hasRoot(): Boolean = runCatching {
        val process = ProcessBuilder("su", "-c", "id")
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val finished = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
        if (!finished) {
            process.destroy()
            return false
        }
        output.contains("uid=0")
    }.getOrDefault(false)

    /**
     * 用 root 读取存档原文。
     */
    fun readWithRoot(): RootResult {
        if (!hasRoot()) return RootResult.NoRoot

        for (path in CANDIDATE_PATHS) {
            val result = runCatching {
                val process = ProcessBuilder("su", "-c", "cat \"$path\"")
                    .redirectErrorStream(true)
                    .start()
                val output = process.inputStream.bufferedReader().readText()
                val finished = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
                if (!finished) {
                    process.destroy()
                    return@runCatching null
                }
                if (process.exitValue() == 0 && output.isNotBlank()) output else null
            }.getOrNull()

            if (result != null) return RootResult.Success(result, path)
        }
        return RootResult.FileNotFound
    }

    /** 从任意路径（用户通过文件选择器选中的）读取文本。 */
    fun readFromUri(context: Context, uri: android.net.Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
    }.getOrNull()

    /** 从本地 File 读取（用于测试或直接路径）。 */
    fun readFromFile(file: File): String? = runCatching {
        if (file.exists()) file.readText(Charsets.UTF_8) else null
    }.getOrNull()
}
