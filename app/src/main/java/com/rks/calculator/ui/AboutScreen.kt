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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 关于页：工具说明、公式、致谢。
 */
@Composable
fun AboutScreen() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Spacer(Modifier.height(4.dp))

        // ---------- 标题区 ----------
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

        // ---------- 公式 ----------
        SmallTitle(text = "计算公式")
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(16.dp),
        ) {
            Text(text = "单曲 RKS", fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "RKS = ( (100 × ACC − 55) / 45 )² × 定数",
                fontSize = 15.sp,
                color = MiuixTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(14.dp))

            Text(text = "反向：由目标 RKS 求 ACC", fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "ACC = ( 55 + 45 × √(RKS / 定数) ) / 100",
                fontSize = 15.sp,
                color = MiuixTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(14.dp))

            Text(
                text = "该函数在 ACC = 55% 处取最小值 0，低于此值会「反弹」增大，" +
                    "因此本工具在 ACC < 70% 时直接提示「别越」，不输出数值。",
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            )
        }

        // ---------- B30 ----------
        SmallTitle(text = "B30 算法")
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(16.dp),
        ) {
            listOf(
                "① 算出存档中每首歌的单曲 RKS",
                "② 排序，取最高的 27 首",
                "③ 把所有满分（1000000 分）的歌单独排序，取最高的 3 首",
                "④ 把这 27 首与这 3 首的 RKS 相加，除以 30（分母固定 30，与数据条数无关）",
            ).forEach {
                Text(text = it, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
            }
            Text(
                text = "满分歌的 ACC 为 100%，代入公式后 RKS 在数值上等于定数。\n" +
                    "两个列表相互独立：一首满分歌若同时进入前 27 名，会被计入两次。",
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            )
        }

        // ---------- 数据来源 ----------
        SmallTitle(text = "数据说明")
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(16.dp),
        ) {
            Text(
                text = "曲目定数表内置了 327 首曲目，来自社区维护的 Phigros 曲目信息。" +
                    "游戏更新后若出现新曲，可能出现「未匹配定数」的提示。",
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "存档解析仅支持「已解密」的存档文件。" +
                    "Phigros 的原始存档是加密的，本工具不包含任何解密算法。",
                fontSize = 13.sp,
            )
        }

        // ---------- 免责 ----------
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
                textAlign = TextAlign.Start,
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}
