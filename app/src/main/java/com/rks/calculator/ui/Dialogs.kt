package com.rks.calculator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rks.calculator.data.B30Calculator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 解密引导页的网址与口令。 */
private const val DECRYPT_SITE = "https://phi.yanx.us/"
private const val DECRYPT_PASSWORD = "luobo233"

/**
 * 存档解密引导弹窗。
 *
 * Phigros 的原始存档是加密的，本工具不包含解密算法，
 * 因此引导用户去第三方工具解密后，再把解密后的文件交给本 App 读取。
 */
@Composable
fun DecryptGuideDialog(onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current

    OverlayDialog(
        show = true,
        renderInRootScaffold = false,
        title = "存档是加密的",
        summary = "Phigros 的原始存档（playerprefs.xml）字段名和数据都是密文，" +
            "本工具无法直接读取。请先用在线工具解密，再回来选择解密后的文件。",
        onDismissRequest = onDismiss,
        onDismissFinished = onDismiss,
        content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = "解密步骤", fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))

                listOf(
                    "1. 用浏览器打开下面的网址",
                    "2. 输入口令进入",
                    "3. 上传 playerprefs.xml，解密并下载",
                    "4. 回到本页，点「手动选择已解密的存档」",
                ).forEach {
                    Text(
                        text = it,
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                }

                Spacer(Modifier.height(12.dp))

                // 网址
                Text(text = "解密工具", fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = DECRYPT_SITE,
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))

                // 口令
                Text(text = "口令", fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = DECRYPT_PASSWORD,
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.primary,
                )

                Spacer(Modifier.height(14.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(
                        text = "复制信息",
                        onClick = {
                            clipboard.setText(
                                AnnotatedString(
                                    "解密工具：$DECRYPT_SITE\n口令：$DECRYPT_PASSWORD",
                                ),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = "知道了",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
    )
}

/**
 * B30 明细弹窗：展示入选的前 27 首与 3 首满分。
 */
@Composable
fun B30DetailDialog(
    result: B30Calculator.Result,
    onDismiss: () -> Unit,
) {
    OverlayDialog(
        show = true,
        renderInRootScaffold = false,
        title = "B30 入选歌曲",
        summary = "B30 = ${formatNumber(result.b30)}（共 ${result.divisor} 首参与计算）",
        onDismissRequest = onDismiss,
        onDismissFinished = onDismiss,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "满分歌曲（取最高的 3 首）",
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(6.dp))
                if (result.perfects.isEmpty()) {
                    Text(
                        text = "（没有满分成绩）",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    )
                } else {
                    result.perfects.forEachIndexed { i, e ->
                        EntryRow(
                            index = i + 1,
                            name = e.record.songName,
                            difficulty = e.record.difficulty,
                            level = e.record.level,
                            rks = e.rks,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "最高的 27 首",
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(6.dp))
                result.best.forEachIndexed { i, e ->
                    EntryRow(
                        index = i + 1,
                        name = e.record.songName,
                        difficulty = e.record.difficulty,
                        level = e.record.level,
                        rks = e.rks,
                    )
                }

                Spacer(Modifier.height(16.dp))
                TextButton(
                    text = "关闭",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

@Composable
private fun EntryRow(
    index: Int,
    name: String,
    difficulty: String,
    level: Double?,
    rks: Double,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$index.",
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            modifier = Modifier.weight(0.08f),
        )
        Column(modifier = Modifier.weight(0.67f)) {
            Text(
                text = name,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                text = "$difficulty${level?.let { " · " + formatNumber(it) } ?: ""}",
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            )
        }
        Text(
            text = formatNumber(rks),
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.primary,
            modifier = Modifier.weight(0.25f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
    Spacer(Modifier.height(8.dp))
}

/**
 * 通用信息弹窗：标题 + 正文 + 确定按钮。
 *
 * 用于「已读到存档」「解密完成」这类一次性提示。
 */
@Composable
fun InfoDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
) {
    OverlayDialog(
        show = true,
        renderInRootScaffold = false,
        title = title,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismiss,
        content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = message,
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                )
                Spacer(Modifier.height(16.dp))
                TextButton(
                    text = "知道了",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}
