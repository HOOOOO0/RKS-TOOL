package com.rks.calculator.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rks.calculator.data.AppJson
import com.rks.calculator.data.B30Calculator
import com.rks.calculator.data.SaveHistory
import com.rks.calculator.data.SaveParser
import com.rks.calculator.util.AppLog

/**
 * 全局界面状态。
 *
 * 用 `remember` 在 Activity 级别持有，保证切换 tab 时不丢失：
 * 输入框内容、存档解析结果、B30 结果等都保留。
 *
 * 对话框的可见性也放在这里 —— 弹窗统一由 RksApp 挂载在
 * AnimatedContent 之外，避免页面切换动画销毁子树导致崩溃。
 */
class AppState {

    // ---------- 首页：计算器输入 ----------

    var levelText by mutableStateOf("")
    var accText by mutableStateOf("")
    var rksText by mutableStateOf("")
    var mode by mutableStateOf(CalcMode.Forward)

    /** 首页累计的「计算结果」列表，导出时会写进 JSON。 */
    val songEntries = mutableStateListOf<AppJson.SongEntry>()

    // ---------- 存档 ----------

    /** 存档解析结果；空表示还没读取。 */
    var saveRecords by mutableStateOf<List<SaveParser.Record>>(emptyList())

    /** 存档来源描述（root 路径 or 文件名）。 */
    var saveSource by mutableStateOf<String?>(null)

    /** 存档是否已成功读取。 */
    var saveLoaded by mutableStateOf(false)

    /** 读取过程中的提示信息。 */
    var saveMessage by mutableStateOf<String?>(null)

    /** 是否正在读取（用于禁用按钮）。 */
    var isReading by mutableStateOf(false)

    /** 权限拿到后要执行的动作。 */
    var pendingPermissionAction: (() -> Unit)? = null

    // ---------- B30 ----------

    var b30FromSave by mutableStateOf<B30Calculator.Result?>(null)
    var b30FromManual by mutableStateOf<B30Calculator.Result?>(null)

    /** 手动录入的成绩。 */
    val manualRecords = mutableStateListOf<AppJson.SongEntry>()

    // ---------- 存档历史 ----------

    /** 历史列表（最新的在前）。 */
    var history by mutableStateOf<List<SaveHistory.Entry>>(emptyList())

    /** 是否正在显示历史面板。 */
    var showHistory by mutableStateOf(false)

    /** 刷新历史列表。 */
    fun refreshHistory(context: Context) {
        history = SaveHistory.list(context)
    }

    // ---------- 加解密（实验性） ----------

    /** 是否正在加解密。 */
    var cryptoRunning by mutableStateOf(false)

    /** 加解密进度提示。 */
    var cryptoMessage by mutableStateOf<String?>(null)

    /** 加解密完成后待导出的内容。 */
    var cryptoOutput: String? = null

    // ---------- 对话框可见性 ----------

    /** 是否显示「存档已加密」引导弹窗。 */
    var showDecryptGuide by mutableStateOf(false)

    /** 是否显示 B30 明细弹窗。 */
    var showB30Detail by mutableStateOf(false)

    /** 读取成功后的提示弹窗内容；非 null 时显示。 */
    var readSuccessInfo by mutableStateOf<String?>(null)

    /** 操作结果提示（加解密等）。 */
    var actionMessage by mutableStateOf<String?>(null)

    // ---------- 导出 ----------

    /**
     * **内存中的存档内容**。
     *
     * 未解密时是加密的 xml；解密成功后被明文覆盖。
     * 所有后续操作（导出、加密、算 B30）都基于它。
     */
    var currentXml: String? = null

    /** 内存中的内容是否已是明文（能被解析出成绩）。 */
    var isPlainText by mutableStateOf(false)

    /** 内存中内容已成功解密过一次。 */
    var hasDecrypted by mutableStateOf(false)

    /** 内存中内容已成功加密过一次。 */
    var hasEncrypted by mutableStateOf(false)

    /** 当前存档对应的历史 id（若有）。 */
    var currentHistoryId: String? = null

    /** 待写出的内容（xml 或 JSON）。 */
    var pendingExportPayload: String? = null

    /** 待导出文件的建议名。 */
    var pendingExportName: String = "export.xml"

    /** 置为 true 时，界面会拉起系统「另存为」选择器。 */
    var requestExportLocation by mutableStateOf(false)

    /**
     * 把 B30 结果打包成 JSON，并请求用户选择导出位置。
     */
    fun startExport(context: Context) {
        val result = b30FromSave ?: return
        AppLog.i("AppState", "请求导出，入选 ${result.best.size} 首")
        pendingExportPayload = buildExportJson(result)
        pendingExportName = "rks_b30_${System.currentTimeMillis()}.json"
        requestExportLocation = true
    }

    private fun buildExportJson(result: B30Calculator.Result): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"b30\": ").append(result.b30).append(",\n")
        sb.append("  \"divisor\": ").append(result.divisor).append(",\n")
        sb.append("  \"bestSum\": ").append(result.bestSum).append(",\n")
        sb.append("  \"perfectSum\": ").append(result.perfectSum).append(",\n")
        sb.append("  \"validCount\": ").append(result.validCount).append(",\n")

        sb.append("  \"best\": [\n")
        result.best.forEachIndexed { i, e ->
            sb.append("    {")
                .append("\"song\": \"").append(escape(e.record.songName)).append("\", ")
                .append("\"difficulty\": \"").append(e.record.difficulty).append("\", ")
                .append("\"level\": ").append(e.record.level ?: 0.0).append(", ")
                .append("\"acc\": ").append(e.record.acc).append(", ")
                .append("\"score\": ").append(e.record.score).append(", ")
                .append("\"rks\": ").append(e.rks)
                .append("}")
            if (i != result.best.lastIndex) sb.append(',')
            sb.append('\n')
        }
        sb.append("  ],\n")

        sb.append("  \"perfects\": [\n")
        result.perfects.forEachIndexed { i, e ->
            sb.append("    {")
                .append("\"song\": \"").append(escape(e.record.songName)).append("\", ")
                .append("\"difficulty\": \"").append(e.record.difficulty).append("\", ")
                .append("\"level\": ").append(e.record.level ?: 0.0).append(", ")
                .append("\"rks\": ").append(e.rks)
                .append("}")
            if (i != result.perfects.lastIndex) sb.append(',')
            sb.append('\n')
        }
        sb.append("  ]\n}")
        return sb.toString()
    }

    private fun escape(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")

    /**
     * 请求导出存档 xml 原文。
     *
     * @param xml 要导出的内容
     * @param name 建议的文件名
     */
    fun requestExportXml(xml: String, name: String) {
        AppLog.i("AppState", "请求导出存档 xml，长度=${xml.length}，文件名=$name")
        pendingExportPayload = xml
        pendingExportName = name
        requestExportLocation = true
    }

    /** 请求导出当前内存中的存档（若有）。 */
    fun exportCurrentSave() {
        val xml = currentXml
        if (xml.isNullOrBlank()) {
            actionMessage = "内存中没有存档内容，请先读取存档。"
            return
        }
        requestExportXml(xml, "playerprefs_current.xml")
    }

    /**
     * 把一份存档内容放进内存。
     *
     * @param xml 内容（可能是加密的，也可能是明文）
     * @param source 来源描述
     * @param tryParse 是否尝试解析成绩（明文字符串时才需要）
     */
    fun setSaveContent(
        context: Context,
        xml: String,
        source: String,
        tryParse: Boolean = true,
    ) {
        currentXml = xml
        saveSource = source

        if (tryParse && SaveParser.looksDecrypted(xml)) {
            // 已经是明文，直接解析
            isPlainText = true
            val parsed = runCatching { SaveParser.parse(context, xml) }.getOrNull()
            if (parsed != null) {
                saveRecords = parsed.records
                saveLoaded = true
                b30FromSave = B30Calculator.calculate(parsed.records)
            }
        } else {
            // 加密内容：清掉旧的成绩（否则会误以为已就绪）
            isPlainText = false
            saveLoaded = false
        }
    }

    /** 内存里是否有内容。 */
    val hasContent: Boolean get() = !currentXml.isNullOrBlank()

    /** 把一条计算结果加入导出列表（去重：同定数同 ACC 只留一条）。 */
    fun addEntry(entry: AppJson.SongEntry) {
        val exists = songEntries.any { it.level == entry.level && it.acc == entry.acc }
        if (!exists) songEntries.add(entry)
    }

    fun removeEntry(index: Int) {
        if (index in songEntries.indices) songEntries.removeAt(index)
    }

    /** 清空存档相关状态。 */
    fun clearSave() {
        saveRecords = emptyList()
        saveSource = null
        saveLoaded = false
        b30FromSave = null
        currentXml = null
        isPlainText = false
        hasDecrypted = false
        hasEncrypted = false
    }
}

/** 换算方向。 */
enum class CalcMode {
    /** 正向：定数 + ACC -> RKS */
    Forward,

    /** 反向：定数 + 目标 RKS -> 所需 ACC */
    Reverse,
}

/** 底栏页面。 */
enum class AppPage(val label: String) {
    Home("首页"),
    About("关于"),
    More("更多"),
}
