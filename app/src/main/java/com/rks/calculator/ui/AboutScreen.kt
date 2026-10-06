package com.rks.calculator.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rks.calculator.util.AppLog
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val AUTHOR_NAME = "HOOO0_"
private const val GITHUB_URL = "https://github.com/HOOOOO0/RKS-TOOL"
private const val BILIBILI_URL = "https://space.bilibili.com/1098797406"

/** 连点多少次开启开发者模式。 */
private const val DEV_TAP_COUNT = 10

/**
 * 关于页。
 *
 * 包含：作者名、GitHub 地址、B 站主页、免责声明。
 * 连点作者名 10 次可开启开发者模式（开始记录日志到私有目录）。
 */
@Composable
fun AboutScreen() {
    val context = LocalContext.current
    var tapCount by remember { mutableIntStateOf(0) }
    var devEnabled by remember { mutableStateOf(AppLog.enabled) }
    var toast by remember { mutableStateOf<String?>(null) }
    var showLog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Spacer(Modifier.height(4.dp))

        // ---------- 标题 ----------
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(20.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "单曲 RKS 计算器",
                    fontSize = 24.sp,
                    color = MiuixTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Phigros 成绩工具",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                )
            }
        }

        // ---------- 关于作者 ----------
        SmallTitle(text = "关于作者")
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(16.dp),
        ) {
            // 作者名：连点 10 次开启开发者模式
            Text(
                text = AUTHOR_NAME,
                fontSize = 16.sp,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        tapCount++
                        val remain = DEV_TAP_COUNT - tapCount
                        when {
                            remain > 3 -> Unit
                            remain in 1..3 -> toast = "再点 $remain 次开启开发者模式"
                            else -> {
                                val next = !AppLog.enabled
                                AppLog.setEnabled(context, next)
                                devEnabled = next
                                toast = if (next) {
                                    "开发者模式已开启"
                                } else {
                                    "开发者模式已关闭"
                                }
                                tapCount = 0
                            }
                        }
                    },
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(14.dp))

            Text(
                text = "GitHub",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = GITHUB_URL,
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.primary,
            )

            Spacer(Modifier.height(14.dp))

            Text(
                text = "哔哩哔哩",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = BILIBILI_URL,
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.primary,
            )

            toast?.let {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = it,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }

        // ---------- 开发者模式（仅开启后显示） ----------
        if (devEnabled) {
            SmallTitle(text = "开发者模式")
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(16.dp),
            ) {
                Text(
                    text = "已开启，本次运行会记录日志到 App 私有目录。",
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = AppLog.logFile(context)?.absolutePath ?: "(路径不可用)",
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                )
                Spacer(Modifier.height(10.dp))

                Text(
                    text = if (showLog) "收起日志内容" else "查看日志内容",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { showLog = !showLog },
                    textAlign = TextAlign.Center,
                )

                if (showLog) {
                    Spacer(Modifier.height(10.dp))
                    val content = AppLog.readAll(context)
                    Text(
                        text = content.ifBlank { "(暂无日志)" },
                        fontSize = 10.sp,
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    )
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    text = "清空日志",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            AppLog.clear(context)
                            showLog = false
                        },
                    textAlign = TextAlign.Center,
                )
            }
        }

        // ---------- 免责声明 ----------
        SmallTitle(text = "免责声明")
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(16.dp),
        ) {
            Text(
                text = "本工具为第三方辅助工具，与 Pigeon Games 及 Phigros 官方无关。" +
                    "所有数据均在本地处理，不会上传到任何服务器。",
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}
