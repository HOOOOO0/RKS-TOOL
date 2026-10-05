package com.rks.calculator.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * 应用主题：跟随系统深浅色，并启用 Monet 动态取色。
 *
 * [ThemeController] 的 `ColorSchemeMode.MonetSystem` 会优先读取系统壁纸取色
 * （Android 12+），[keyColor] 作为兜底种子色。
 */
@Composable
fun AppTheme(
    content: @Composable () -> Unit,
) {
    val controller = remember {
        ThemeController(
            colorSchemeMode = ColorSchemeMode.MonetSystem,
            keyColor = Color(0xFF3482FF),
        )
    }
    MiuixTheme(
        controller = controller,
        content = content,
    )
}
