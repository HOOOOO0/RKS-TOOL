package com.rks.calculator.data

import android.content.Context
import org.json.JSONObject

/**
 * Phigros 曲目定数表。
 *
 * 存档里只记录了分数（s）、ACC（a）、是否 FC（c），**没有定数**，
 * 所以定数必须从这张表里查。数据来源为社区维护的 Phigros 曲目信息
 * （打包在 assets/level_table.json 中）。
 *
 * 表的 key 与存档里的记录 key 前两段完全一致：
 *
 *     存档 key:  歌名.曲师.0.Record.难度
 *     表   key:  歌名.曲师
 */
object LevelTable {

    /** 单首歌的信息。 */
    data class SongInfo(
        val id: String,
        val song: String,
        val composer: String,
        /** 难度 -> 定数，例如 {"IN": 15.7, "AT": 16.3} */
        val levels: Map<String, Double>,
    ) {
        fun levelOf(difficulty: String): Double? = levels[difficulty.uppercase()]
    }

    @Volatile
    private var table: Map<String, SongInfo>? = null

    /** 加载（并缓存）定数表。 */
    fun load(context: Context): Map<String, SongInfo> {
        table?.let { return it }
        synchronized(this) {
            table?.let { return it }
            val loaded = parse(context)
            table = loaded
            return loaded
        }
    }

    private fun parse(context: Context): Map<String, SongInfo> {
        val result = HashMap<String, SongInfo>(512)
        val text = context.assets.open("level_table.json")
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

        val root = JSONObject(text)
        val ids = root.keys()
        while (ids.hasNext()) {
            val id = ids.next()
            val obj = root.optJSONObject(id) ?: continue
            val levelsObj = obj.optJSONObject("levels") ?: continue

            val levels = HashMap<String, Double>(4)
            val diffKeys = levelsObj.keys()
            while (diffKeys.hasNext()) {
                val d = diffKeys.next()
                val v = levelsObj.optDouble(d, Double.NaN)
                if (!v.isNaN()) levels[d.uppercase()] = v
            }

            result[id] = SongInfo(
                id = id,
                song = obj.optString("song", id.substringBefore('.')),
                composer = obj.optString("composer", id.substringAfter('.', "")),
                levels = levels,
            )
        }
        return result
    }

    /** 查一首歌；查不到返回 null。 */
    fun find(context: Context, id: String): SongInfo? = load(context)[id]

    /** 已缓存的曲目数量（未加载时为 0）。 */
    fun size(): Int = table?.size ?: 0
}
