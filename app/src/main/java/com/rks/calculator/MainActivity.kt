package com.rks.calculator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rks.calculator.ui.RksApp
import com.rks.calculator.ui.theme.AppTheme
import com.rks.calculator.util.AppLog

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 恢复开发者模式状态，并安装全局崩溃捕获
        AppLog.loadState(this)
        AppLog.init(this)
        AppLog.i("MainActivity", "onCreate")

        setContent {
            AppTheme {
                RksApp()
            }
        }
    }
}
