package com.rks.calculator.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 应用数据模型 + JSON 导入导出。
 *
 * 导出文件写在 App 私有目录 `filesDir` 下（不需要任何存储权限）：
 *
 *     /data/data/com.rks.calculator/files/rks_data.json
 *     实际路径类似 /data/user/0/com.rks.calculator/files/rks_data.json
 */
object AppJson {

    const val FILE_NAME = "rks_data.json"

    /** 导出文件的完整路径。 */
    fun fileOf(context: Context): File = File(context.filesDir, FILE_NAME)

    // ---------------- 手动录入的成绩 ----------------

    /** 用户手动添加的一条成绩（或从计算器算出的一条）。 */
    data class SongEntry(
        /** 歌名（可空，用户可能只想要个标记） */
        val song: String,
        /** 定数 */
        val level: Double,
        /** ACC，百分比形式（如 99.23） */
        val acc: Double,
        /** 可选：分数 */
        val score: Int? = null,
    ) {
        /** 单曲 RKS。 */
        val rks: Double?
            get() = if (acc >= 55.0) {
                val n = (acc - 55.0) / 45.0
                n * n * level
            } else {
                null
            }
    }

    /** 导出的完整数据结构。 */
    data class Payload(
        /** 手动录入/计算得到的成绩 */
        val songs: List<SongEntry>,
        /** 导出时间戳（毫秒） */
        val exportedAt: Long,
        /** 数据格式版本 */
        val version: Int = 1,
    )

    // ---------------- 写 ----------------

    /**
     * 把数据写成 JSON 文件。
     * @return 写入的文件；失败返回 null
     */
    fun export(context: Context, payload: Payload): File? = runCatching {
        val root = JSONObject().apply {
            put("version", payload.version)
            put("exportedAt", payload.exportedAt)
            put("exportedAtText", formatTime(payload.exportedAt))

            val arr = JSONArray()
            for (s in payload.songs) {
                arr.put(JSONObject().apply {
                    put("song", s.song)
                    put("level", s.level)
                    put("acc", s.acc)
                    s.score?.let { put("score", it) }
                    s.rks?.let { put("rks", round4(it)) }
                })
            }
            put("songs", arr)
        }
        val file = fileOf(context)
        file.writeText(root.toString(2), Charsets.UTF_8)
        file
    }.getOrNull()

    // ---------------- 读 ----------------

    /** 读取导出的 JSON；失败返回空列表。 */
    fun readSongs(context: Context): List<SongEntry> = runCatching {
        val file = fileOf(context)
        if (!file.exists()) return emptyList()
        val root = JSONObject(file.readText(Charsets.UTF_8))
        val arr = root.optJSONArray("songs") ?: return emptyList()
        val out = ArrayList<SongEntry>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val level = o.optDouble("level", Double.NaN)
            val acc = o.optDouble("acc", Double.NaN)
            if (level.isNaN() || acc.isNaN()) continue
            out += SongEntry(
                song = o.optString("song", ""),
                level = level,
                acc = acc,
                score = if (o.has("score")) o.optInt("score") else null,
            )
        }
        out
    }.getOrDefault(emptyList())

    /** 解析一段 JSON 文本（用于导入）。 */
    fun parseSongs(json: String): List<SongEntry> = runCatching {
        val root = JSONObject(json)
        val arr = root.optJSONArray("songs") ?: return emptyList()
        val out = ArrayList<SongEntry>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val level = o.optDouble("level", Double.NaN)
            val acc = o.optDouble("acc", Double.NaN)
            if (level.isNaN() || acc.isNaN()) continue
            out += SongEntry(
                song = o.optString("song", ""),
                level = level,
                acc = acc,
                score = if (o.has("score")) o.optInt("score") else null,
            )
        }
        out
    }.getOrDefault(emptyList())

    // ---------------- 工具 ----------------

    private fun round4(v: Double): Double = Math.round(v * 10000.0) / 10000.0

    private fun formatTime(ms: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
        return sdf.format(java.util.Date(ms))
    }
}
