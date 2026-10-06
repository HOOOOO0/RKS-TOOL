package com.rks.calculator.ui

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
 *  2. B30 计算（用存档数据 / 用手动录入数据）
 */
@Composable
fun MoreScreen(appState: AppState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 弹窗状态
    var showDecryptGuide by remember { mutableStateOf(false) }
    var showSaveDetail by remember { mutableStateOf(false) }
    var pendingPickFile by remember { mutableStateOf(false) }

    // 系统文件选择器
    val filePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    SaveReader.readFromUri(context, uri)
                }
                if (text.isNullOrBlank()) {
                    appState.saveMessage = "读取文件失败"
                } else if (!SaveParser.looksDecrypted(text)) {
                    appState.saveMessage = "这个文件看起来还是加密的，请先在 PhiCrypto 解密后再选择"
                    showDecryptGuide = true
                } else {
                    val result = withContext(Dispatchers.Default) {
                        SaveParser.parse(context, text)
                    }
                    appState.saveRecords = result.records
                    appState.saveSource = uri.lastPathSegment ?: "已选择的文件"
                    appState.saveLoaded = true
                    appState.b30FromSave = B30Calculator.calculate(result.records)
                    appState.saveMessage = "读取成功：${result.records.size} 条成绩" +
                        if (result.unmatchedIds.isNotEmpty()) {
                            "，其中 ${result.unmatchedIds.size} 首未匹配到定数"
                        } else {
                            ""
                        }
                }
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
                        val result = withContext(Dispatchers.IO) { SaveReader.readWithRoot() }
                        when (result) {
                            is SaveReader.RootResult.Success -> {
                                if (!SaveParser.looksDecrypted(result.xml)) {
                                    // 读到了，但是加密的 → 引导去解密
                                    appState.saveMessage = "存档已读取，但内容是加密的"
                                    showDecryptGuide = true
                                } else {
                                    val parsed = withContext(Dispatchers.Default) {
                                        SaveParser.parse(context, result.xml)
                                    }
                                    appState.saveRecords = parsed.records
                                    appState.saveSource = result.path
                                    appState.saveLoaded = true
                                    appState.b30FromSave = B30Calculator.calculate(parsed.records)
                                    appState.saveMessage = "读取成功：${parsed.records.size} 条成绩"
                                }
                            }

                            SaveReader.RootResult.NoRoot -> {
                                appState.saveMessage = "未检测到 root 权限"
                                showDecryptGuide = true
                            }

                            SaveReader.RootResult.FileNotFound -> {
                                appState.saveMessage = "未找到存档文件（可能没玩过，或路径不同）"
                            }

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
                text = "27 首最高 RKS + 3 首满分，除以 30",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            )
            Spacer(Modifier.height(14.dp))

            // --- 用存档算 ---
            val saveResult = appState.b30FromSave
            Text(text = "用存档数据计算", fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            if (saveResult != null) {
                B30ResultView(saveResult, onDetail = { showSaveDetail = true })
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
                        appState.b30FromSave = B30Calculator.calculate(appState.saveRecords)
                    },
                    enabled = appState.saveLoaded && appState.saveRecords.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(18.dp))

            // --- 用手动录入算 ---
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
                    // 把手动录入转换为存档记录格式（无分数则视为非满分）
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

    // ---------------- 弹窗 ----------------

    if (showDecryptGuide) {
        DecryptGuideDialog(onDismiss = { showDecryptGuide = false })
    }

    if (showSaveDetail) {
        appState.b30FromSave?.let {
            B30DetailDialog(result = it, onDismiss = { showSaveDetail = false })
        }
    }
}

/** B30 结果的小卡片展示。 */
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
