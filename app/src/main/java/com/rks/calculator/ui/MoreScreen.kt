package com.rks.calculator.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rks.calculator.data.B30Calculator
import com.rks.calculator.data.SaveParser
import com.rks.calculator.data.SaveReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 更多页：
 *  1. 读取存档（root 直读 / 手动选择解密后的文件）
 *  2. B30 计算（用存档数据 / 用首页记录数据）
 *
 * 注意：对话框不在这里渲染，统一由 [RksApp] 挂载，
 * 避免页面切换动画销毁子树导致崩溃。
 */
@Composable
fun MoreScreen(appState: AppState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 处理读到的存档文本（统一入口，任何异常都在这里兜住）
    fun handleSaveText(text: String?, source: String) {
        if (text.isNullOrBlank()) {
            appState.saveMessage = "读取文件失败（内容为空）"
            return
        }
        if (!SaveParser.looksDecrypted(text)) {
            appState.saveMessage = "这个文件还是加密的"
            appState.showDecryptGuide = true
            return
        }
        // 解析过程整体包一层，任何异常都转成提示而不是崩溃
        val outcome = runCatching { SaveParser.parse(context, text) }
        outcome.fold(
            onSuccess = { result ->
                appState.saveRecords = result.records
                appState.saveSource = source
                appState.saveLoaded = true
                appState.b30FromSave = B30Calculator.calculate(result.records)
                appState.saveMessage = buildString {
                    append("读取成功：").append(result.records.size).append(" 条成绩")
                    if (result.unmatchedIds.isNotEmpty()) {
                        append("，").append(result.unmatchedIds.size).append(" 首未匹配定数")
                    }
                }
            },
            onFailure = { e ->
                appState.saveMessage = "解析失败：${e.message ?: e.javaClass.simpleName}"
            },
        )
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) {
            appState.saveMessage = "没有选择文件"
        } else {
            scope.launch {
                val text = runCatching {
                    withContext(Dispatchers.IO) { SaveReader.readFromUri(context, uri) }
                }.getOrNull()
                handleSaveText(text, uri.lastPathSegment ?: "已选择的文件")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Spacer(Modifier.height(4.dp))

        // ================= 读取存档 =================
        SmallTitle(text = "存档")
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(16.dp),
        ) {
            Text(
                text = "读取 Phigros 存档以获得成绩数据。",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            )
            Spacer(Modifier.height(12.dp))

            TextButton(
                text = "用 root 读取存档",
                onClick = {
                    appState.saveMessage = "正在读取…"
                    scope.launch {
                        val result = runCatching {
                            withContext(Dispatchers.IO) { SaveReader.readWithRoot() }
                        }.getOrElse { SaveReader.RootResult.Error(it.message ?: "未知错误") }

                        when (result) {
                            is SaveReader.RootResult.Success ->
                                handleSaveText(result.xml, result.path)

                            SaveReader.RootResult.NoRoot -> {
                                appState.saveMessage = "未检测到 root 权限"
                                appState.showDecryptGuide = true
                            }

                            SaveReader.RootResult.FileNotFound ->
                                appState.saveMessage =
                                    "未找到存档文件。可能路径不同，或游戏从未运行过。"

                            is SaveReader.RootResult.Error ->
                                appState.saveMessage = "读取失败：${result.message}"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            TextButton(
                text = "手动选择已解密的存档",
                onClick = { filePicker.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            TextButton(
                text = "存档是加密的？点这里查看解密方法",
                onClick = { appState.showDecryptGuide = true },
                modifier = Modifier.fillMaxWidth(),
            )

            appState.saveMessage?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = it,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.primary,
                )
            }

            if (appState.saveLoaded) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "来源：${appState.saveSource ?: "未知"}\n" +
                        "共 ${appState.saveRecords.size} 条成绩，" +
                        "满分 ${appState.saveRecords.count { it.isPerfect }} 首",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                )
                Spacer(Modifier.height(10.dp))
                TextButton(
                    text = "清空存档数据",
                    onClick = {
                        appState.clearSave()
                        appState.saveMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // ================= B30 =================
        SmallTitle(text = "B30")
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(16.dp),
        ) {
            Text(
                text = "27 首最高 RKS ＋ 3 首满分，除以 30（分母固定为 30）",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            )
            Spacer(Modifier.height(14.dp))

            // --- 用存档算 ---
            Text(text = "用存档数据计算", fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            val saveResult = appState.b30FromSave
            if (saveResult != null) {
                B30ResultView(saveResult, onDetail = { appState.showB30Detail = true })
            } else {
                Text(
                    text = if (appState.saveLoaded) "点下面的按钮计算" else "请先在上方读取存档",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                )
                Spacer(Modifier.height(8.dp))
                TextButton(
                    text = "计算 B30",
                    onClick = {
                        appState.b30FromSave =
                            B30Calculator.calculate(appState.saveRecords)
                    },
                    enabled = appState.saveLoaded && appState.saveRecords.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(18.dp))

            // --- 用首页记录算 ---
            Text(text = "用首页记录的数据计算", fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "首页「加入导出列表」的记录：${appState.songEntries.size} 条",
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(
                text = "用首页数据算 B30",
                onClick = {
                    val asRecords = appState.songEntries.map { e ->
                        SaveParser.Record(
                            id = e.song.ifBlank { "手动录入" },
                            songName = e.song.ifBlank { "手动录入" },
                            composer = "",
                            difficulty = "IN",
                            score = e.score ?: 0,
                            acc = e.acc,
                            fullCombo = false,
                            level = e.level,
                        )
                    }
                    appState.b30FromManual = B30Calculator.calculate(asRecords)
                },
                enabled = appState.songEntries.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            )

            appState.b30FromManual?.let {
                Spacer(Modifier.height(12.dp))
                B30ResultView(it, onDetail = null)
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** B30 结果小卡片。 */
@Composable
private fun B30ResultView(
    result: B30Calculator.Result,
    onDetail: (() -> Unit)?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(14.dp),
    ) {
        Text(
            text = "B30",
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = formatNumber(result.b30),
            fontSize = 34.sp,
            color = MiuixTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "前 27 首 ${formatNumber(result.bestSum)} ＋ " +
                "满分 ${result.perfects.size} 首 ${formatNumber(result.perfectSum)}" +
                "，除以 ${result.divisor}",
            fontSize = 11.sp,
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
        )
        if (onDetail != null) {
            Spacer(Modifier.height(10.dp))
            TextButton(
                text = "查看入选歌曲",
                onClick = onDetail,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
