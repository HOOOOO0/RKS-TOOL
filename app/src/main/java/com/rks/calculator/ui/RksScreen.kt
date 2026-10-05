package com.rks.calculator.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale

/** 换算方向。 */
private enum class Mode {
    /** 正向：定数 + ACC -> 单曲 RKS。箭头朝下。 */
    Forward,

    /** 反向：定数 + 目标 RKS -> 所需 ACC。箭头朝上。 */
    Reverse,
}

/**
 * 单曲 RKS 计算器主界面。
 *
 * 布局自上而下：
 *  1. 两个输入框：定数 / ACC（反向模式下第二个框变成「目标 RKS」）
 *  2. 中间一个箭头按钮：点击在正向与反向之间切换，箭头随之旋转 180°
 *  3. 下方结果卡片：实时显示换算结果
 */
@Composable
fun RksScreen() {
    var mode by remember { mutableStateOf(Mode.Forward) }

    var levelText by remember { mutableStateOf("") }
    var accText by remember { mutableStateOf("") }
    var rksText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(title = "单曲 RKS 计算器")
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(padding)
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
                    value = levelText,
                    onValueChange = { levelText = it },
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

                // 第二个输入框的内容随方向切换
                if (mode == Mode.Forward) {
                    TextField(
                        value = accText,
                        onValueChange = { accText = it },
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
                        value = rksText,
                        onValueChange = { rksText = it },
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
                    text = if (mode == Mode.Forward) {
                        "ACC 支持百分比（99.23）或小数（0.9923）"
                    } else {
                        "由目标 RKS 与定数反推所需 ACC"
                    },
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                )
            }

            // ---------- 中间箭头：点击切换方向 ----------
            ArrowSwitcher(
                mode = mode,
                onClick = {
                    // 切到反向时把当前正算结果回填，省得重新输入
                    if (mode == Mode.Forward) {
                        val outcome = RksMath.evaluate(levelText, accText)
                        if (outcome is RksMath.Outcome.Success) {
                            rksText = format(outcome.rks)
                        }
                    }
                    mode = if (mode == Mode.Forward) Mode.Reverse else Mode.Forward
                },
            )

            // ---------- 结果区 ----------
            SmallTitle(text = "结果")
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(16.dp),
            ) {
                if (mode == Mode.Forward) {
                    ForwardResult(levelText, accText)
                } else {
                    ReverseResult(levelText, rksText)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * 中间那个可点击的箭头：正向朝下，反向朝上（旋转 180° 带动画）。
 */
@Composable
private fun ArrowSwitcher(
    mode: Mode,
    onClick: () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (mode == Mode.Forward) 0f else 180f,
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
                contentDescription = if (mode == Mode.Forward) "切换为反向换算" else "切换为正向换算",
                modifier = Modifier
                    .size(32.dp)
                    .rotate(rotation),
                tint = MiuixTheme.colorScheme.primary,
            )
        }
        Text(
            text = if (mode == Mode.Forward) {
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
        is RksMath.Outcome.Success -> format(outcome.rks)
        RksMath.Outcome.TooLow -> "别越"
        RksMath.Outcome.TooHigh -> "别越"
        RksMath.Outcome.Empty -> "--"
    }

    val isError = outcome is RksMath.Outcome.TooLow || outcome is RksMath.Outcome.TooHigh

    val footnote = when (outcome) {
        is RksMath.Outcome.Success ->
            level?.let { "定数 ${format(it)} 时，100% ACC 的 RKS 上限为 ${format(RksMath.maxRks(it))}" } ?: ""

        RksMath.Outcome.TooLow ->
            "ACC 低于 ${format(RksMath.ACC_MIN_PERCENT)}%，公式已失去意义"

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
        is RksMath.ReverseOutcome.Success -> format(RksMath.percentOf(outcome.accFraction)) + "%"
        RksMath.ReverseOutcome.Unreachable -> "别越"
        RksMath.ReverseOutcome.TooLow -> "别越"
        RksMath.ReverseOutcome.Empty -> "--"
    }

    val isError = outcome is RksMath.ReverseOutcome.Unreachable ||
        outcome is RksMath.ReverseOutcome.TooLow

    val footnote = when (outcome) {
        is RksMath.ReverseOutcome.Success ->
            level?.let { "定数 ${format(it)} 的 RKS 上限为 ${format(RksMath.maxRks(it))}" } ?: ""

        RksMath.ReverseOutcome.Unreachable -> "目标 RKS 超过该定数上限，100% ACC 也达不到"
        RksMath.ReverseOutcome.TooLow ->
            "目标 RKS 过低，所需 ACC 已低于 ${format(RksMath.ACC_MIN_PERCENT)}%"

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

/** 结果卡片内部排版的公共部分。 */
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

        AnimatedContent(
            targetState = value,
            label = "resultValue",
        ) { target ->
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

/** 统一保留 4 位小数，并去掉多余的尾随 0。 */
private fun format(value: Double): String {
    val rounded = String.format(Locale.US, "%.4f", value)
    return rounded.trimEnd('0').trimEnd('.').ifEmpty { "0" }
}
