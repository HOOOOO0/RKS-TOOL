package com.rks.calculator.data

import android.content.Context
import com.rks.calculator.util.AppLog
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 存档历史。
 *
 * 每次成功读取一份存档（无论来自 root 还是手动选择），都会存一条记录，
 * 包含两部分：
 *  - **xml 原文**：可以随时导出还原，或用于以后重新解密
 *  - **解析后的成绩数据**：可以直接算 B30，不受 xml 是否加密影响
 *
 * 存储位置（App 私有目录，不需要任何权限）：
 *
 *     files/history/index.json          历史索引（元信息）
 *     files/history/save_<id>.xml       该条记录的 xml 原文
 *
 * 条数不做限制（按需求）。每条 xml 约 60~110 KB。
 */
object SaveHistory {

    private const val DIR = "history"
    private const val INDEX = "index.json"

    /** 一条历史记录。 */
    data class Entry(
        /** 唯一 id，同时是文件名的一部分。 */
        val id: String,
        /** 读取时间（毫秒）。 */
        val timestamp: Long,
        /** 来源描述（root 路径或所选文件名）。 */
        val source: String,
        /** 成绩条数。 */
        val recordCount: Int,
        /** 满分份数。 */
        val perfectCount: Int,
        /** 该记录的 B30。 */
        val b30: Double,
        /** 是否有 xml 原文可导出。 */
        val hasXml: Boolean,
    ) {
        /** 界面上显示的时间文本。 */
        val timeText: String
            get() = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
                .format(java.util.Date(timestamp))
    }

    // ---------------- 路径 ----------------

    private fun dirOf(context: Context): File {
        val d = File(context.filesDir, DIR)
        if (!d.exists()) d.mkdirs()
        return d
    }

    private fun indexFile(context: Context) = File(dirOf(context), INDEX)

    private fun xmlFile(context: Context, id: String) = File(dirOf(context), "save_$id.xml")

    // ---------------- 读写索引 ----------------

    /** 读取全部历史，按时间倒序（最新的在前）。 */
    fun list(context: Context): List<Entry> = runCatching {
        val f = indexFile(context)
        if (!f.exists()) return emptyList()
        val root = JSONObject(f.readText(Charsets.UTF_8))
        val arr = root.optJSONArray("entries") ?: return emptyList()
        val out = ArrayList<Entry>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id")
            if (id.isBlank()) continue
            out += Entry(
                id = id,
                timestamp = o.optLong("timestamp", 0L),
                source = o.optString("source", "未知"),
                recordCount = o.optInt("recordCount", 0),
                perfectCount = o.optInt("perfectCount", 0),
                b30 = o.optDouble("b30", 0.0),
                hasXml = xmlFile(context, id).exists(),
            )
        }
        out.sortedByDescending { it.timestamp }
    }.getOrElse {
        AppLog.e("SaveHistory", "读取历史索引失败", it)
        emptyList()
    }

    private fun writeIndex(context: Context, entries: List<Entry>) {
        runCatching {
            val arr = JSONArray()
            for (e in entries) {
                arr.put(JSONObject().apply {
                    put("id", e.id)
                    put("timestamp", e.timestamp)
                    put("source", e.source)
                    put("recordCount", e.recordCount)
                    put("perfectCount", e.perfectCount)
                    put("b30", e.b30)
                })
            }
            val root = JSONObject().put("entries", arr)
            indexFile(context).writeText(root.toString(2), Charsets.UTF_8)
        }.onFailure { AppLog.e("SaveHistory", "写入历史索引失败", it) }
    }

    // ---------------- 增删 ----------------

    /**
     * 新增一条历史。
     *
     * @param xml 存档原文；若没有（例如只解析了数据）可传 null
     */
    fun add(
        context: Context,
        source: String,
        xml: String?,
        records: List<SaveParser.Record>,
        b30: Double,
    ): Entry? {
        return runCatching {
            val id = System.currentTimeMillis().toString()
            // 写 xml 原文
            if (!xml.isNullOrBlank()) {
                xmlFile(context, id).writeText(xml, Charsets.UTF_8)
            }

            val entry = Entry(
                id = id,
                timestamp = System.currentTimeMillis(),
                source = source,
                recordCount = records.size,
                perfectCount = records.count { it.isPerfect },
                b30 = b30,
                hasXml = !xml.isNullOrBlank(),
            )

            val all = list(context) + entry
            writeIndex(context, all)
            AppLog.i("SaveHistory", "已保存历史：$source（${records.size} 条，B30=${"%.4f".format(b30)}）")
            entry
        }.getOrElse {
            AppLog.e("SaveHistory", "保存历史失败", it)
            null
        }
    }

    /** 删除一条。 */
    fun remove(context: Context, id: String) {
        runCatching {
            xmlFile(context, id).delete()
            writeIndex(context, list(context).filterNot { it.id == id })
            AppLog.i("SaveHistory", "已删除历史：$id")
        }.onFailure { AppLog.e("SaveHistory", "删除历史失败", it) }
    }

    /** 清空全部。 */
    fun clear(context: Context) {
        runCatching {
            dirOf(context).listFiles()?.forEach { it.delete() }
            AppLog.i("SaveHistory", "已清空全部历史")
        }.onFailure { AppLog.e("SaveHistory", "清空历史失败", it) }
    }

    // ---------------- 取内容 ----------------

    /** 取某条历史的 xml 原文。 */
    fun readXml(context: Context, id: String): String? = runCatching {
        val f = xmlFile(context, id)
        if (f.exists()) f.readText(Charsets.UTF_8) else null
    }.getOrNull()

    /** 重新解析某条历史的 xml，拿到成绩数据。 */
    fun parseRecords(context: Context, id: String): List<SaveParser.Record> {
        val xml = readXml(context, id) ?: return emptyList()
        return runCatching { SaveParser.parse(context, xml).records }.getOrElse {
            AppLog.e("SaveHistory", "重新解析历史失败", it)
            emptyList()
        }
    }

    /** 历史占用的空间（字节）。 */
    fun sizeOnDisk(context: Context): Long = runCatching {
        dirOf(context).listFiles()?.sumOf { if (it.isFile) it.length() else 0L } ?: 0L
    }.getOrDefault(0L)
}
