package com.rks.calculator.data

import android.content.Context
import com.rks.calculator.util.AppLog
import org.json.JSONObject

/**
 * Phigros 存档解析器。
 *
 * 存档是 Android SharedPreferences 的 xml，**加密状态下**长这样：
 *
 *     <string name="xaHiFItVgoS6CBFNHTR2%2BA%3D%3D">84nt4CDG41fhF5EXHkVpow%3D%3D</string>
 *
 * 字段名和数据都是密文，本工具**不做解密**。解密后（例如通过 PhiCrypto）变成：
 *
 *     <string name="Glaciaxion.SunsetRay.0.Record.HD">{"s":996428,"a":99.60,"c":1}</string>
 *
 * 其中：
 *  - key 格式：`歌名.曲师.0.Record.难度`
 *  - `s` 分数（0~1000000）
 *  - `a` 准确率（0~100）
 *  - `c` 是否 Full Combo（0/1）
 */
object SaveParser {

    /** 一条成绩记录。 */
    data class Record(
        /** 定数表的 key（歌名.曲师）。 */
        val id: String,
        /** 原样保留的歌名（可能含特殊字符）。 */
        val songName: String,
        val composer: String,
        /** EZ / HD / IN / AT */
        val difficulty: String,
        /** 分数，0~1000000 */
        val score: Int,
        /** 准确率，0~100 */
        val acc: Double,
        /** 是否 FC */
        val fullCombo: Boolean,
        /** 定数；查不到时为 null。 */
        val level: Double?,
    ) {
        /** 单曲 RKS；定数未知或 ACC 低于有效下限时返回 null。 */
        val rks: Double?
            get() {
                val lv = level ?: return null
                if (acc < RksMathCompat.ACC_MIN_PERCENT) return null
                return RksMathCompat.rks(lv, acc / 100.0)
            }

        /** 是否满分（1000000 分）。 */
        val isPerfect: Boolean get() = score >= 1_000_000
    }

    /** 解析结果。 */
    data class Result(
        val records: List<Record>,
        /** 匹配到定数的记录数。 */
        val matched: Int,
        /** 未匹配到定数的歌曲 id（用于提示歌曲表可能过期）。 */
        val unmatchedIds: List<String>,
    )

    // key 形如：歌名.曲师.0.Record.HD
    //
    // 注意：花括号必须写成字符类 [{] [}]，**不能**写成 \{ \}。
    // Android 使用 ICU 正则引擎，它对转义花括号的处理与 Oracle JDK 不同，
    // 写成 \{ 会在 Android 上抛 PatternSyntaxException（曾导致启动即闪退）。
    //
    // 用 lazy + runCatching 包住，保证即使正则本身有问题也只是返回 null，
    // 而不会在类初始化阶段抛出 ExceptionInInitializerError 直接崩溃。
    private val RECORD_RE: Regex? by lazy {
        runCatching {
            Regex(
                """<string\s+name="([^"]+?)\.Record\.(EZ|HD|IN|AT)">([{][^}]*[}])</string>""",
                RegexOption.IGNORE_CASE,
            )
        }.onFailure {
            AppLog.e("SaveParser", "成绩正则编译失败（这会导致无法解析存档）", it)
        }.getOrNull()
    }

    /**
     * 解析解密后的存档文本。
     *
     * @param context 用于加载定数表
     * @param xml 解密后的 playerprefs xml 内容
     */
    fun parse(context: Context, xml: String): Result {
        AppLog.i("SaveParser", "开始解析，文本长度=${xml.length}")
        val table = LevelTable.load(context)
        AppLog.i("SaveParser", "定数表已加载，共 ${table.size} 首")
        val records = ArrayList<Record>(512)
        val unmatched = LinkedHashSet<String>()

        val regex = RECORD_RE
        if (regex == null) {
            AppLog.e("SaveParser", "成绩正则不可用，无法解析")
            return Result(records = emptyList(), matched = 0, unmatchedIds = emptyList())
        }

        for (m in regex.findAll(xml)) {
            val rawKey = unescapeXml(m.groupValues[1])
            val difficulty = m.groupValues[2].uppercase()
            val json = m.groupValues[3]

            val obj = runCatching { JSONObject(json) }.getOrElse {
                AppLog.w("SaveParser", "单条成绩 JSON 解析失败：$json")
                null
            } ?: continue

            // key 的前两段就是定数表的 id
            val id = rawKey.split('.').take(2).joinToString(".")
            val songName = rawKey.split('.').firstOrNull().orEmpty()
            val composer = rawKey.split('.').getOrNull(1).orEmpty()

            val info = table[id]
            val level = info?.levelOf(difficulty)

            records += Record(
                id = id,
                songName = info?.song ?: songName,
                composer = info?.composer ?: composer,
                difficulty = difficulty,
                score = obj.optInt("s", 0),
                acc = obj.optDouble("a", 0.0),
                fullCombo = obj.optInt("c", 0) == 1,
                level = level,
            )
            if (level == null) unmatched += id
        }

        val matched = records.count { it.level != null }
        AppLog.i(
            "SaveParser",
            "解析完成：${records.size} 条，匹配定数 $matched 条，未匹配 ${unmatched.size} 首",
        )

        return Result(
            records = records,
            matched = matched,
            unmatchedIds = unmatched.toList(),
        )
    }

    /** 存档是否像是「已解密」的。 */
    fun looksDecrypted(xml: String): Boolean = RECORD_RE?.containsMatchIn(xml) == true

    /** 还原 xml 实体（`&amp;` → `&` 等）。 */
    private fun unescapeXml(s: String): String = s
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")
}

/**
 * 与 [com.rks.calculator.RksMath] 相同的公式，放在这里避免数据层依赖 UI 层。
 * 数值行为完全一致。
 */
internal object RksMathCompat {
    const val ACC_MIN_PERCENT = 70.0
    private const val ACC_ZERO_PERCENT = 55.0

    fun rks(level: Double, accFraction: Double): Double {
        val normalized = (accFraction * 100.0 - ACC_ZERO_PERCENT) / 45.0
        return normalized * normalized * level
    }
}
