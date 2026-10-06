package com.rks.calculator.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rks.calculator.RksMath
import com.rks.calculator.data.AppJson
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale

/**
 * 首页：单曲 RKS 计算器。
 *
 * 两个输入框 → 中间箭头（点击切换正反向） → 结果卡片 → 底部导出按钮。
 */
@Composable
fun HomeScreen(appState: AppState) {
    var exportMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Spacer(Modifier.height(4.dp))

        // ---------- 输入区 ----------
        SmallTitle(text = "输入")
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(12.dp),
        ) {
            TextField(
                value = appState.levelText,
                onValueChange = { appState.levelText = it },
                label = "定数",
                useLabelAsPlaceholder = true,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next,
                ),
            )

            Spacer(Modifier.height(12.dp))

            if (appState.mode == CalcMode.Forward) {
                TextField(
                    value = appState.accText,
                    onValueChange = { appState.accText = it },
                    label = "ACC（如 99.23 或 0.9923）",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                )
            } else {
                TextField(
                    value = appState.rksText,
                    onValueChange = { appState.rksText = it },
                    label = "目标 RKS",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = if (appState.mode == CalcMode.Forward) {
                    "ACC 支持百分比（99.23）或小数（0.9923）"
                } else {
                    "由目标 RKS 与定数反推所需 ACC"
                },
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            )
        }

        // ---------- 中间箭头 ----------
        ArrowSwitcher(
            mode = appState.mode,
            onClick = {
                if (appState.mode == CalcMode.Forward) {
                    val outcome = RksMath.evaluate(appState.levelText, appState.accText)
                    if (outcome is RksMath.Outcome.Success) {
                        appState.rksText = formatNumber(outcome.rks)
                    }
                }
                appState.mode = if (appState.mode == CalcMode.Forward) {
                    CalcMode.Reverse
                } else {
                    CalcMode.Forward
                }
            },
        )

        // ---------- 结果区 ----------
        SmallTitle(text = "结果")
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(16.dp),
        ) {
            if (appState.mode == CalcMode.Forward) {
                ForwardResult(appState.levelText, appState.accText)
            } else {
                ReverseResult(appState.levelText, appState.rksText)
            }
        }

        // ---------- 加入导出列表 ----------
        val currentRks: Double? = run {
            val outcome = RksMath.evaluate(appState.levelText, appState.accText)
            if (outcome is RksMath.Outcome.Success) outcome.rks else null
        }
        val levelValue = RksMath.parseLevel(appState.levelText)
        val accValue = RksMath.parseAcc(appState.accText)

        TextButton(
            text = "加入导出列表",
            onClick = {
                if (currentRks != null && levelValue != null && accValue != null) {
                    appState.addEntry(
                        AppJson.SongEntry(
                            song = "",
                            level = levelValue,
                            acc = RksMath.percentOf(accValue),
                        ),
                    )
                    exportMessage = "已加入（共 ${appState.songEntries.size} 条）"
                } else {
                    exportMessage = "请先填好定数与 ACC"
                }
            },
            enabled = currentRks != null,
            modifier = Modifier.fillMaxWidth(),
        )

        // ---------- 导出 ----------
        SmallTitle(text = "数据")
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(12.dp),
        ) {
            Text(
                text = "已记录 ${appState.songEntries.size} 条成绩，可导出为 JSON 文件",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            )
            Spacer(Modifier.height(10.dp))
            ExportButton(
                appState = appState,
                onMessage = { exportMessage = it },
            )
        }

        exportMessage?.let {
            Text(
                text = it,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

/**
 * 导出按钮：把当前数据写成 JSON 到 App 的 data 目录。
 */
@Composable
private fun ExportButton(
    appState: AppState,
    onMessage: (String) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    TextButton(
        text = "导出 JSON",
        onClick = {
            val payload = AppJson.Payload(
                songs = appState.songEntries.toList(),
                exportedAt = System.currentTimeMillis(),
            )
            val file = AppJson.export(context, payload)
            onMessage(
                if (file != null) {
                    "已导出到：\n${file.absolutePath}"
                } else {
                    "导出失败"
                },
            )
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** 中间那个可点击的箭头：正向朝下，反向朝上。 */
@Composable
private fun ArrowSwitcher(
    mode: CalcMode,
    onClick: () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (mode == CalcMode.Forward) 0f else 180f,
        animationSpec = tween(durationMillis = 300),
        label = "arrowRotation",
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = MiuixIcons.ExpandMore,
                contentDescription = if (mode == CalcMode.Forward) "切换为反向换算" else "切换为正向换算",
                modifier = Modifier
                    .size(32.dp)
                    .rotate(rotation),
                tint = MiuixTheme.colorScheme.primary,
            )
        }
        Text(
            text = if (mode == CalcMode.Forward) {
                "正向：算 RKS（点击切换方向）"
            } else {
                "反向：算 ACC（点击切换方向）"
            },
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
    }
}

/** 正向结果：单曲 RKS。 */
@Composable
private fun ForwardResult(levelText: String, accText: String) {
    val outcome = RksMath.evaluate(levelText, accText)
    val level = RksMath.parseLevel(levelText)

    val mainText = when (outcome) {
        is RksMath.Outcome.Success -> formatNumber(outcome.rks)
        RksMath.Outcome.TooLow -> "别越"
        RksMath.Outcome.TooHigh -> "别越"
        RksMath.Outcome.Empty -> "--"
    }
    val isError = outcome is RksMath.Outcome.TooLow || outcome is RksMath.Outcome.TooHigh
    val footnote = when (outcome) {
        is RksMath.Outcome.Success ->
            level?.let { "定数 ${formatNumber(it)} 时，100% ACC 的 RKS 上限为 ${formatNumber(RksMath.maxRks(it))}" } ?: ""
        RksMath.Outcome.TooLow ->
            "ACC 低于 ${formatNumber(RksMath.ACC_MIN_PERCENT)}%，公式已失去意义"
        RksMath.Outcome.TooHigh -> "ACC 不能超过 100%"
        RksMath.Outcome.Empty -> "请填写定数与 ACC"
    }

    ResultContent(
        label = "单曲 RKS",
        value = mainText,
        valueColor = if (isError) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary,
        footnote = footnote,
        isError = isError,
    )
}

/** 反向结果：达成目标 RKS 所需的 ACC。 */
@Composable
private fun ReverseResult(levelText: String, rksText: String) {
    val outcome = RksMath.evaluateReverse(levelText, rksText)
    val level = RksMath.parseLevel(levelText)

    val mainText = when (outcome) {
        is RksMath.ReverseOutcome.Success -> formatNumber(RksMath.percentOf(outcome.accFraction)) + "%"
        RksMath.ReverseOutcome.Unreachable -> "别越"
        RksMath.ReverseOutcome.TooLow -> "别越"
        RksMath.ReverseOutcome.Empty -> "--"
    }
    val isError = outcome is RksMath.ReverseOutcome.Unreachable ||
        outcome is RksMath.ReverseOutcome.TooLow

    val footnote = when (outcome) {
        is RksMath.ReverseOutcome.Success ->
            level?.let { "定数 ${formatNumber(it)} 的 RKS 上限为 ${formatNumber(RksMath.maxRks(it))}" } ?: ""
        RksMath.ReverseOutcome.Unreachable -> "目标 RKS 超过该定数上限，100% ACC 也达不到"
        RksMath.ReverseOutcome.TooLow ->
            "目标 RKS 过低，所需 ACC 已低于 ${formatNumber(RksMath.ACC_MIN_PERCENT)}%"
        RksMath.ReverseOutcome.Empty -> "请填写定数与目标 RKS"
    }

    ResultContent(
        label = "所需 ACC",
        value = mainText,
        valueColor = if (isError) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary,
        footnote = footnote,
        isError = isError,
    )
}

/** 结果卡片内部排版。 */
@Composable
private fun ResultContent(
    label: String,
    value: String,
    valueColor: Color,
    footnote: String,
    isError: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
        )
        Spacer(Modifier.height(8.dp))
        AnimatedContent(targetState = value, label = "resultValue") { target ->
            Text(
                text = target,
                fontSize = if (isError) 40.sp else 48.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (footnote.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = footnote,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 统一保留 4 位小数，去掉多余的尾随 0。 */
internal fun formatNumber(value: Double): String {
    val rounded = String.format(Locale.US, "%.4f", value)
    return rounded.trimEnd('0').trimEnd('.').ifEmpty { "0" }
}
