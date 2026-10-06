package com.rks.calculator.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rks.calculator.data.AppJson
import com.rks.calculator.data.B30Calculator
import com.rks.calculator.data.SaveParser

/**
 * 全局界面状态。
 *
 * 用 `remember` 在 Activity 级别持有，保证切换 tab 时不丢失：
 * 输入框内容、存档解析结果、B30 结果等都保留。
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

    /** 存档解析结果；null 表示还没读取。 */
    var saveRecords by mutableStateOf<List<SaveParser.Record>>(emptyList())

    /** 存档来源描述（root 路径 or 文件名），用于界面显示。 */
    var saveSource by mutableStateOf<String?>(null)

    /** 存档是否已成功读取。 */
    var saveLoaded by mutableStateOf(false)

    /** 读取过程中的提示信息。 */
    var saveMessage by mutableStateOf<String?>(null)

    // ---------- B30 ----------

    /** 用「存档数据」算出的 B30。 */
    var b30FromSave by mutableStateOf<B30Calculator.Result?>(null)

    /** 用「手动录入数据」算出的 B30。 */
    var b30FromManual by mutableStateOf<B30Calculator.Result?>(null)

    /** 手动录入的成绩。 */
    val manualRecords = mutableStateListOf<AppJson.SongEntry>()

    // ---------- 对话框可见性（放在 AppState 里，避免页面切换时被销毁） ----------

    /** 是否显示「存档已加密」引导弹窗。 */
    var showDecryptGuide by mutableStateOf(false)

    /** 是否显示 B30 明细弹窗。 */
    var showB30Detail by mutableStateOf(false)

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
