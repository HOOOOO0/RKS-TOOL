package com.rks.calculator.data

import com.rks.calculator.util.AppLog
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * 存档加解密（**实验性**）。
 *
 * ## 原理
 *
 * Phigros 存档把每个 `<string>` 的 **name 和 value 分别加密**，
 * 因此可以做批量处理：
 *
 * ```text
 * ① 从 xml 里取出所有 name 和 value，各组成一个数组
 * ② 两个数组分别 POST /decrypt（或 /encrypt）
 * ③ 把返回结果按原顺序拼回 xml
 * ```
 *
 * ## ⚠️ 重要说明
 *
 * 加解密**算法在第三方服务器上**（`phi.yanx.us`），本地没有实现：
 *
 * - **存档内容会被上传到该服务器**（包含玩家 ID、全部成绩等）
 * - 该服务是第三方个人站点，**随时可能变更或停止服务**
 * - 接口一旦变化，本功能即失效，且不会有任何通知
 *
 * 因此本功能标记为实验性，且不应用于批量或自动化场景。
 * 若接口不可用，请改用本 App 的「手动选择已解密的存档」。
 */
object PhiCrypto {

    /** 服务地址。 */
    private const val BASE = "https://phi.yanx.us"

    /** 单次请求最多提交多少条，避免请求体过大。 */
    private const val BATCH_SIZE = 400

    /** 连接/读取超时。 */
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 90_000

    /** 加解密方向。 */
    enum class Direction(val path: String, val label: String) {
        Decrypt("/decrypt", "解密"),
        Encrypt("/encrypt", "加密"),
    }

    /** 结果。 */
    sealed interface Result {
        data class Success(val xml: String, val succeeded: Int, val total: Int) : Result
        data class Failure(val message: String) : Result
    }

    /**
     * 检查服务是否可用。
     */
    fun checkService(): Boolean = runCatching {
        val body = httpGet("$BASE/check-cookie")
        body.contains("valid")
    }.getOrElse {
        AppLog.w("PhiCrypto", "服务检查失败：${it.message}")
        false
    }.also { AppLog.i("PhiCrypto", "服务可用=$it") }

    /**
     * 处理整份 xml。
     *
     * @param xml 存档 xml 原文
     * @param direction 解密或加密
     */
    fun process(xml: String, direction: Direction): Result {
        AppLog.i("PhiCrypto", "开始${direction.label}，xml 长度=${xml.length}")

        val pairs = extractPairs(xml)
        if (pairs.isEmpty()) {
            return Result.Failure("没有在 xml 里找到任何 <string> 记录，文件格式可能不对")
        }
        AppLog.i("PhiCrypto", "提取到 ${pairs.size} 条记录")

        val names = pairs.map { it.first }
        val values = pairs.map { it.second }
        val sessionId = System.currentTimeMillis() % 100000
        AppLog.i("PhiCrypto", "会话 $sessionId 开始${direction.label}：${pairs.size} 条")

        // 分别处理 name 和 value
        val decodedNames = callApi(names, direction)
            ?: return Result.Failure("${direction.label}失败：无法连接服务器（${BASE}）")

        val decodedValues = callApi(values, direction)
            ?: return Result.Failure("${direction.label}失败：无法连接服务器（${BASE}）")

        // 统计成功数，失败的保留原值
        var succeeded = 0
        var total = 0
        val out = StringBuilder(xml.length + 1024)
        out.append("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n")

        for (i in pairs.indices) {
            total++
            val okName = decodedNames.getOrNull(i)
            val okValue = decodedValues.getOrNull(i)
            val name = okName ?: pairs[i].first
            val value = okValue ?: pairs[i].second
            if (okName != null && okValue != null) succeeded++

            // 加密方向的输出要 URL 编码（与存档格式一致）
            val finalName = if (direction == Direction.Encrypt && okName != null) {
                encodeURIComponent(name)
            } else {
                escapeAttr(name)
            }
            val finalValue = if (direction == Direction.Encrypt && okValue != null) {
                encodeURIComponent(value)
            } else {
                escapeText(value)
            }

            out.append("    <string name=\"").append(finalName).append("\">")
                .append(finalValue).append("</string>\n")
        }
        out.append("</map>")

        AppLog.i("PhiCrypto", "${direction.label}完成：$succeeded/$total 条成功")
        return Result.Success(out.toString(), succeeded, total)
    }

    // ---------------- 网络 ----------------

    /**
     * 分批调用接口。
     *
     * @return 按原顺序展开的结果数组；任一环节失败返回 null
     */
    private fun callApi(items: List<String>, direction: Direction): List<String?>? {
        val results = ArrayList<String?>(items.size)

        var index = 0
        while (index < items.size) {
            val end = minOf(index + BATCH_SIZE, items.size)
            val batch = items.subList(index, end)

            val arr = JSONArray()
            batch.forEach { arr.put(it) }
            val body = JSONObject().put("text", arr).toString()

            val response = runCatching { httpPost("$BASE${direction.path}", body) }
                .getOrElse {
                    AppLog.e("PhiCrypto", "请求失败：$it")
                    return null
                }

            val parsed = runCatching { JSONObject(response).getJSONArray("result") }
                .getOrElse {
                    AppLog.e("PhiCrypto", "响应解析失败：${response.take(200)}")
                    return null
                }

            for (i in 0 until parsed.length()) {
                val item = parsed.optJSONObject(i)
                if (item != null && item.optBoolean("success", false)) {
                    results.add(item.optString("data", ""))
                } else {
                    results.add(null)
                }
            }

            index = end
        }
        return results
    }

    private fun httpPost(urlStr: String, body: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("User-Agent", "RksCalculator/1.0")
        }
        try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.let { s ->
                BufferedReader(InputStreamReader(s, Charsets.UTF_8)).use { it.readText() }
            } ?: ""
            if (code !in 200..299) throw IllegalStateException("HTTP $code: ${text.take(150)}")
            return text
        } finally {
            conn.disconnect()
        }
    }

    private fun httpGet(urlStr: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = 20_000
            setRequestProperty("User-Agent", "RksCalculator/1.0")
        }
        try {
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    // ---------------- xml 处理 ----------------

    /** 从 xml 里取出 (name, value) 对，保持原顺序。 */
    private fun extractPairs(xml: String): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>(1024)
        val re = Regex("""<string\s+name="([^"]*?)">([\s\S]*?)</string>""")
        for (m in re.findAll(xml)) {
            out += m.groupValues[1] to m.groupValues[2]
        }
        return out
    }

    /** 还原 xml 实体。 */
    private fun unescape(s: String): String = s
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")

    /** 属性值转义（同时处理 name 部分）。 */
    private fun escapeAttr(s: String): String = unescape(s)
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    /** 文本节点转义。 */
    private fun escapeText(s: String): String = unescape(s)
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    /** 与 JavaScript encodeURIComponent 等价的 URL 编码。 */
    private fun encodeURIComponent(s: String): String {
        val sb = StringBuilder(s.length * 2)
        for (b in s.toByteArray(Charsets.UTF_8)) {
            val c = b.toInt().toChar()
            if (c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' ||
                c == '-' || c == '_' || c == '.' || c == '!' ||
                c == '~' || c == '*' || c == '\'' || c == '(' || c == ')'
            ) {
                sb.append(c)
            } else {
                sb.append('%')
                sb.append(String.format("%02X", b.toInt() and 0xFF))
            }
        }
        return sb.toString()
    }
}
