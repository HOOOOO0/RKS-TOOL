package com.rks.calculator.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * 存储权限检查与申请。
 *
 * - Android 11+（API 30+）：读取共享存储中的任意文件需要
 *   `MANAGE_EXTERNAL_STORAGE`（「所有文件访问权限」）。这是受限权限，
 *   **不能**用运行时对话框申请，只能跳到系统设置页让用户手动开启。
 * - Android 10 及以下：用 `READ_EXTERNAL_STORAGE` 运行时权限即可。
 */
object StoragePermission {

    /** 是否已经拿到读取共享存储所需的权限。 */
    fun hasPermission(context: Context): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Environment.isExternalStorageManager()
        else -> ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** 是否需要走「设置页」而不是运行时对话框。 */
    fun needsSettingsPage(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /** 运行时权限名（Android 10 及以下用）；Android 11+ 返回 null。 */
    fun runtimePermission(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) null
        else Manifest.permission.READ_EXTERNAL_STORAGE

    /**
     * 跳到「所有文件访问权限」设置页（Android 11+）。
     * 返回是否成功跳转。
     */
    fun openAllFilesSettings(context: Context): Boolean = runCatching {
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    }.getOrElse {
        // 部分机型没有上面这个 Action，退化到应用详情页
        runCatching {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
