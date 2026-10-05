// 顶层构建脚本：只声明插件，不在此应用
plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9.x 内置 Kotlin 支持，不再声明 org.jetbrains.kotlin.android
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose.multiplatform) apply false
}
