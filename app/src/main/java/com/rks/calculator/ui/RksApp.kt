package com.rks.calculator.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.More

/**
 * App 主框架：底部导航栏 + 三个页面。
 *
 * 注意：对话框统一挂在 [Scaffold] 的**内容之外**（见文件末尾），
 * 不要放在 AnimatedContent 里面 —— 页面切换动画会重建子树，
 * 导致 MiuixPopupHost 在动画中途被销毁而崩溃。
 */
@Composable
fun RksApp() {
    val appState = remember { AppState() }
    var page by remember { mutableStateOf(AppPage.Home) }

    val navigationItems = remember {
        listOf(
            NavigationItem(label = AppPage.Home.label, icon = MiuixIcons.Home),
            NavigationItem(label = AppPage.About.label, icon = MiuixIcons.Info),
            NavigationItem(label = AppPage.More.label, icon = MiuixIcons.More),
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                navigationItems.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = page.ordinal == index,
                        onClick = { page = AppPage.entries[index] },
                        icon = item.icon,
                        label = item.label,
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AnimatedContent(
                targetState = page,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "pageSwitch",
                modifier = Modifier.fillMaxSize(),
            ) { target ->
                when (target) {
                    AppPage.Home -> HomeScreen(appState)
                    AppPage.About -> AboutScreen()
                    AppPage.More -> MoreScreen(appState)
                }
            }

            // 对话框放在动画之外，避免页面切换时被连带销毁
            if (appState.showDecryptGuide) {
                DecryptGuideDialog(onDismiss = { appState.showDecryptGuide = false })
            }
            if (appState.showB30Detail) {
                appState.b30FromSave?.let {
                    B30DetailDialog(result = it, onDismiss = { appState.showB30Detail = false })
                }
            }
        }
    }
}
