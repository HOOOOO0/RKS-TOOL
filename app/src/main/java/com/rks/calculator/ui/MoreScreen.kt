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
import com.rks.calculator.util.AppLog
import com.rks.calculator.util.StoragePermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 更多页：读取存档 + B30 计算。
 *
 * 读取存档的流程（按需求）：
 *  1. 先申请文件访问权限（Android 11+ 跳设置页开「所有文件访问」）
 *  2. 权限到手后检查 root
 *  3. 有 root 就读
 *     /data/user/0/com.PigeonGames.Phigros/shared_prefs/com.PigeonGames.Phigros.v2.playerprefs.xml
 *  4. 读出来若是加密的，引导解密；解密后的文件可手动选择
 *  5. 解析成功后，让用户选择导出位置（另存为）
 */
@Composable
fun MoreScreen(appState: AppState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ---------- 权限申请 ----------

    val runtimePermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        AppLog.i("MoreScreen", "运行时权限结果 granted=$granted")
        if (granted) {
            appState.pendingPermissionAction?.invoke()
        } else {
            appState.saveMessage = "没有存储权限，无法读取存档文件"
        }
        appState.pendingPermissionAction = null
        appState.isReading = false
    }

    // ---------- 选择导出位置（另存为） ----------

    val exportLocationPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) {
            appState.saveMessage = "已取消导出"
            return@rememberLauncherForActivityResult
        }
        val payload = appState.pendingExportPayload
        if (payload == null) {
            appState.saveMessage = "没有可导出的数据"
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(payload.toByteArray(Charsets.UTF_8))
                        true
                    } ?: false
                }
            }.getOrElse {
                AppLog.e("MoreScreen", "导出失败", it)
                false
            }
            appState.saveMessage = if (ok) "已导出到所选位置" else "导出失败"
            appState.pendingExportPayload = null
        }
    }

    // ---------- 统一处理读取结果 ----------

    fun handleReadResult(
        ctx: android.content.Context,
        state: AppState,
        result: SaveReader.ReadResult,
    ) {
        when (result) {
            is SaveReader.ReadResult.Success -> {
                AppLog.i("MoreScreen", "读取成功 source=${result.source}")
                state.saveSource = result.source
                if (!SaveParser.looksDecrypted(result.xml)) {
                    AppLog.w("MoreScreen", "档案仍是加密的")
                    state.saveMessage = "存档已读到，但内容是加密的"
                    state.showDecryptGuide = true
                    return
                }
                runCatching { SaveParser.parse(ctx, result.xml) }.fold(
                    onSuccess = { parsed ->
                        state.saveRecords = parsed.records
                        state.saveLoaded = true
                        state.b30FromSave = B30Calculator.calculate(parsed.records)
                        state.saveMessage = buildString {
                            append("读取成功：").append(parsed.records.size).append(" 条成绩")
                            if (parsed.unmatchedIds.isNotEmpty()) {
                                append("，").append(parsed.unmatchedIds.size).append(" 首未匹配定数")
                            }
                        }
                    },
                    onFailure = { e ->
                        AppLog.e("MoreScreen", "解析失败", e)
                        state.saveMessage = "解析失败：${e.message ?: e.javaClass.simpleName}"
                    },
                )
            }

            SaveReader.ReadResult.EmptyFile ->
                state.saveMessage = "读取失败：文件是空的"

            is SaveReader.ReadResult.NoPermission -> {
                state.saveMessage = "没有读取权限：${result.detail}"
                state.showDecryptGuide = false
            }

            SaveReader.ReadResult.NotFound ->
                state.saveMessage = "没找到存档文件"

            is SaveReader.ReadResult.Error -> {
                AppLog.e("MoreScreen", "读取失败：${result.message}")
                state.saveMessage = "读取失败：${result.message}"
            }
        }
    }

    // ---------- 手动选择存档文件 ----------

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        AppLog.i("MoreScreen", "文件选择器回调 uri=$uri")
        if (uri == null) {
            appState.saveMessage = "没有选择文件"
        } else {
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) { SaveReader.readFromUri(context, uri) }
                }.getOrElse {
                    AppLog.e("MoreScreen", "读取 URI 抛异常", it)
                    SaveReader.ReadResult.Error(it.message ?: "未知错误")
                }
                handleReadResult(context, appState, result)
            }
        }
    }

    /** 权限到手之后要做的事：检查 root → 读取存档。 */
    fun doReadSave() {
        AppLog.i("MoreScreen", "开始读取存档流程")
        appState.saveMessage = "正在检查 root 权限…"
        scope.launch {
            val hasRoot = withContext(Dispatchers.IO) { SaveReader.hasRoot() }
            AppLog.i("MoreScreen", "hasRoot=$hasRoot")
            if (!hasRoot) {
                appState.saveMessage = "未检测到 root 权限，无法直接读取游戏存档"
                appState.showDecryptGuide = true
                return@launch
            }
            appState.saveMessage = "正在读取存档…"
            val result = runCatching {
                withContext(Dispatchers.IO) { SaveReader.readWithRoot() }
            }.getOrElse {
                AppLog.e("MoreScreen", "root 读取抛异常", it)
                SaveReader.RootResult.Error(it.message ?: "未知错误")
            }
            when (result) {
                is SaveReader.RootResult.Success -> {
                    appState.saveSource = result.path
                    if (!SaveParser.looksDecrypted(result.xml)) {
                        appState.saveMessage = "存档已读到，但内容是加密的"
                        appState.showDecryptGuide = true
                    } else {
                        runCatching { SaveParser.parse(context, result.xml) }.fold(
                            onSuccess = { parsed ->
                                appState.saveRecords = parsed.records
                                appState.saveLoaded = true
                                appState.b30FromSave = B30Calculator.calculate(parsed.records)
                                appState.saveMessage = "读取成功：${parsed.records.size} 条成绩"
                            },
                            onFailure = { e ->
                                AppLog.e("MoreScreen", "解析失败", e)
                                appState.saveMessage = "解析失败：${e.message}"
                            },
                        )
                    }
                }

                SaveReader.RootResult.NoRoot -> {
                    appState.saveMessage = "未检测到 root 权限"
                    appState.showDecryptGuide = true
                }

                SaveReader.RootResult.FileNotFound ->
                    appState.saveMessage =
                        "没找到存档文件。请确认：① 游戏已安装并运行过；" +
                            "② 存档路径确实是 /data/user/0/com.PigeonGames.Phigros/shared_prefs/"

                is SaveReader.RootResult.PermissionDenied ->
                    appState.saveMessage =
                        "找到存档了，但读取被拒绝（SELinux 或 su 权限不足）。\n" +
                            "请改用「手动选择已解密的存档」。"

                is SaveReader.RootResult.Error ->
                    appState.saveMessage = "读取失败：${result.message}"
            }
        }
    }

    /** 入口：先要权限，再读存档。 */
    fun startReadFlow() {
        if (StoragePermission.hasPermission(context)) {
            doReadSave()
        } else if (StoragePermission.needsSettingsPage()) {
            appState.saveMessage = "请在本页开启「所有文件访问权限」后返回"
            appState.pendingPermissionAction = { doReadSave() }
            StoragePermission.openAllFilesSettings(context)
        } else {
            val perm = StoragePermission.runtimePermission()
            if (perm != null) {
                appState.pendingPermissionAction = { doReadSave() }
                runtimePermLauncher.launch(perm)
            } else {
                doReadSave()
            }
        }
    }

    val hasPerm = StoragePermission.hasPermission(context)

    // ================= 界面 =================

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Spacer(Modifier.height(4.dp))

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
            Spacer(Modifier.height(10.dp))

            // 权限状态
            Text(
                text = if (hasPerm) "文件访问权限：已授予" else "文件访问权限：未授予",
                fontSize = 12.sp,
                color = if (hasPerm) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.error
                },
            )

            if (!hasPerm) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    text = "授予文件访问权限",
                    onClick = {
                        if (StoragePermission.needsSettingsPage()) {
                            StoragePermission.openAllFilesSettings(context)
                        } else {
                            StoragePermission.runtimePermission()?.let {
                                runtimePermLauncher.launch(it)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(10.dp))

            TextButton(
                text = "读取存档",
                onClick = { startReadFlow() },
                enabled = !appState.isReading,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            TextButton(
                text = "手动选择已解密的存档",
                onClick = {
                    AppLog.i("MoreScreen", "点击：手动选择存档")
                    filePicker.launch(SaveReader.PICKER_MIME_TYPES)
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            TextButton(
                text = "存档是加密的？查看解密方法",
                onClick = { appState.showDecryptGuide = true },
                modifier = Modifier.fillMaxWidth(),
            )

            appState.saveMessage?.let {
                Spacer(Modifier.height(10.dp))
                Text(text = it, fontSize = 12.sp, color = MiuixTheme.colorScheme.primary)
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
                text = "27 首最高 RKS ＋ 3 首满分，除以 30（分母固定 30）",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            )
            Spacer(Modifier.height(14.dp))

            Text(text = "用存档数据计算", fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            val saveResult = appState.b30FromSave
            if (saveResult != null) {
                B30ResultView(
                    result = saveResult,
                    onDetail = { appState.showB30Detail = true },
                    onExport = { appState.startExport(context) },
                )
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
                        AppLog.i("MoreScreen", "点击：用存档算 B30")
                        appState.b30FromSave = B30Calculator.calculate(appState.saveRecords)
                    },
                    enabled = appState.saveLoaded && appState.saveRecords.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(18.dp))

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
                B30ResultView(result = it, onDetail = null, onExport = null)
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    // 导出位置选择器：状态变化时触发
    if (appState.requestExportLocation) {
        appState.requestExportLocation = false
        exportLocationPicker.launch("rks_b30.json")
    }
}

/** B30 结果小卡片。 */
@Composable
private fun B30ResultView(
    result: B30Calculator.Result,
    onDetail: (() -> Unit)?,
    onExport: (() -> Unit)?,
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
        if (onExport != null) {
            Spacer(Modifier.height(8.dp))
            TextButton(
                text = "导出到指定位置",
                onClick = onExport,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
