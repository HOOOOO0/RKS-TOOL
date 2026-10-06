package com.rks.calculator.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 应用内日志。
 *
 * 默认关闭；在「关于」页连点作者名 10 次开启开发者模式后，
 * 本次运行（以及后续运行，直到手动关闭）会把日志写入 App 私有目录：
 *
 *     /data/user/0/com.rks.calculator/files/logs/rks_log.txt
 *
 * 记录内容：
 *  - 关键步骤（读取文件、解析存档、计算 B30 等）
 *  - 被 try/catch 捕获的异常
 *  - **全局未捕获异常**（崩溃时也会先把堆栈写进文件，再交给系统）
 *
 * 之所以要写文件而不是只打 Logcat：崩溃一旦发生，进程就没了，
 * 而手机上不方便接 logcat，写文件能保证崩溃现场被保留下来。
 */
object AppLog {

    private const val TAG = "RksCalc"
    private const val DIR_NAME = "logs"
    private const val FILE_NAME = "rks_log.txt"
    private const val MAX_BYTES = 512 * 1024L

    /** 开发者模式开关（进程内有效）。 */
    @Volatile
    var enabled: Boolean = false
        private set

    private var appContext: Context? = null
    private val timeFmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    /** 在 Application/Activity 启动时调用，安装全局崩溃捕获。 */
    fun init(context: Context) {
        appContext = context.applicationContext
        installCrashHandler()
        i("AppLog", "日志系统就绪（开发者模式=${enabled}）")
    }

    /** 开发者模式的持久化键。 */
    private const val PREF = "dev_settings"
    private const val KEY_DEV = "developer_mode"

    /** 读取开发者模式状态。 */
    fun loadState(context: Context) {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        enabled = sp.getBoolean(KEY_DEV, false)
    }

    /** 设置开发者模式。 */
    fun setEnabled(context: Context, value: Boolean) {
        enabled = value
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DEV, value).apply()
        if (value) {
            i("AppLog", "开发者模式已开启，日志文件：${logFile(context)?.absolutePath}")
        }
    }

    /** 日志文件。 */
    fun logFile(context: Context): File? = runCatching {
        val dir = File(context.filesDir, DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        File(dir, FILE_NAME)
    }.getOrNull()

    /** 清空日志。 */
    fun clear(context: Context) {
        runCatching { logFile(context)?.writeText("") }
    }

    /** 读取全部日志（供界面展示或导出）。 */
    fun readAll(context: Context): String = runCatching {
        logFile(context)?.takeIf { it.exists() }?.readText(Charsets.UTF_8).orEmpty()
    }.getOrDefault("")

    // ---------------- 写日志 ----------------

    fun i(tag: String, msg: String) = write("I", tag, msg, null)

    fun w(tag: String, msg: String) = write("W", tag, msg, null)

    fun e(tag: String, msg: String, tr: Throwable? = null) = write("E", tag, msg, tr)

    private fun write(level: String, tag: String, msg: String, tr: Throwable?) {
        // 始终打到 logcat，方便有 adb 时排查
        when (level) {
            "E" -> Log.e(TAG, "[$tag] $msg", tr)
            "W" -> Log.w(TAG, "[$tag] $msg", tr)
            else -> Log.i(TAG, "[$tag] $msg")
        }
        if (!enabled) return
        val ctx = appContext ?: return
        runCatching {
            val file = logFile(ctx) ?: return
            // 简单的大小保护，超过上限就清空重来
            if (file.exists() && file.length() > MAX_BYTES) file.writeText("")

            val sb = StringBuilder()
            sb.append(timeFmt.format(Date()))
            sb.append(' ').append(level).append('/').append(tag).append(": ").append(msg)
            if (tr != null) {
                sb.append('\n').append(Log.getStackTraceString(tr))
            }
            sb.append('\n')
            file.appendText(sb.toString(), Charsets.UTF_8)
        }
    }

    // ---------------- 全局崩溃捕获 ----------------

    @Volatile
    private var crashHandlerInstalled = false

    private fun installCrashHandler() {
        if (crashHandlerInstalled) return
        crashHandlerInstalled = true

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // 先把崩溃现场写进文件（此时 enabled 可能为 false，也要写，方便排查）
            val wasEnabled = enabled
            if (!wasEnabled) enabled = true
            runCatching {
                val ctx = appContext
                if (ctx != null) {
                    val file = logFile(ctx)
                    if (file != null) {
                        val sb = StringBuilder()
                        sb.append(timeFmt.format(Date()))
                        sb.append(" E/CRASH: 未捕获异常，线程=").append(thread.name).append('\n')
                        sb.append(Log.getStackTraceString(throwable)).append('\n')
                        file.appendText(sb.toString(), Charsets.UTF_8)
                    }
                }
            }
            if (!wasEnabled) enabled = false

            previous?.uncaughtException(thread, throwable)
        }
    }
}
