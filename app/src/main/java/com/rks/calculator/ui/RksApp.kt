package com.rks.calculator.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
 * 页面：首页（RKS 计算器） / 关于 / 更多
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
        AnimatedContent(
            targetState = page,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "pageSwitch",
            modifier = Modifier.fillMaxSize().padding(padding),
        ) { target ->
            when (target) {
                AppPage.Home -> HomeScreen(appState)
                AppPage.About -> AboutScreen()
                AppPage.More -> MoreScreen(appState)
            }
        }
    }
}
