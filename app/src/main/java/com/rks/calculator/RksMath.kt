package com.rks.calculator

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 单曲 RKS 换算核心逻辑（与 UI 完全解耦，方便单元测试）。
 *
 * 正向公式（由定数 level 与准确率 ACC 求单曲 RKS）：
 *
 *     RKS = (((100 * ACC - 55) / 45) ^ 2) * level
 *
 * 其中 ACC 取 0~1 之间的小数（例如 99.23% 写作 0.9923）。
 *
 * 反向公式（由目标 RKS 与定数 level 反推所需 ACC）：
 *
 *     ACC = (55 + 45 * sqrt(RKS / level)) / 100
 *
 * 边界说明：
 * 该二次函数在 100*ACC = 55（即 ACC = 55%）处取到最小值 0，
 * 当 ACC < 55% 时函数值会「反弹」增大，这在 Phigros 中没有意义。
 * 因此低于阈值时直接返回 [Outcome.TooLow]（界面显示「别越」），不输出数值。
 */
object RksMath {

    /** ACC 有效下限（百分比）。低于该值公式失去物理意义。 */
    const val ACC_MIN_PERCENT = 70.0

    /** ACC 有效上限（百分比）。 */
    const val ACC_MAX_PERCENT = 100.0

    /** ACC 硬下限：公式的数学零点，低于此值函数开始反弹。 */
    private const val ACC_ZERO_PERCENT = 55.0

    /**
     * 把一个「定数」文本解析成 Double。
     *
     * 允许 15、15.7、15.75、16.3 这类写法，并容忍末尾误带的「%」。
     */
    fun parseLevel(text: String): Double? {
        val cleaned = text.trim().removeSuffix("%").trim()
        if (cleaned.isEmpty()) return null
        val value = cleaned.toDoubleOrNull() ?: return null
        return if (value > 0.0) value else null
    }

    /**
     * 把 ACC 文本解析成小数形式，自动识别两种写法：
     *
     * - 大于 1 视为百分比：`99.23` -> `0.9923`，`100` -> `1.0`
     * - 小于等于 1 视为小数：`0.9923` -> `0.9923`，`1` -> `1.0`
     * - 带「%」一律按百分比：`99.23%` -> `0.9923`
     *
     * @return 解析失败返回 null。
     */
    fun parseAcc(text: String): Double? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        val hasPercentSign = trimmed.endsWith("%")
        val cleaned = if (hasPercentSign) trimmed.removeSuffix("%").trim() else trimmed
        if (cleaned.isEmpty()) return null

        val value = cleaned.toDoubleOrNull() ?: return null
        if (value < 0.0) return null

        return when {
            hasPercentSign -> value / 100.0
            value > 1.0 -> value / 100.0
            else -> value
        }
    }

    /** 把小数形式 ACC 转回百分比（0.9923 -> 99.23）。 */
    fun percentOf(accFraction: Double): Double = accFraction * 100.0

    /**
     * 正向换算：由定数 [level] 与 [accFraction] 求单曲 RKS。
     *
     * @param accFraction ACC 的小数形式，例如 0.9923。
     */
    fun rks(level: Double, accFraction: Double): Double {
        val accPercent = percentOf(accFraction)
        val normalized = (accPercent - ACC_ZERO_PERCENT) / 45.0
        return normalized.pow(2) * level
    }

    /**
     * 反向换算：由目标 RKS [targetRks] 与定数 [level] 反推所需 ACC。
     *
     * @return ACC 的小数形式；目标 RKS 超过该定数上限时返回 null。
     */
    fun requiredAcc(level: Double, targetRks: Double): Double? {
        if (level <= 0.0 || targetRks < 0.0) return null
        val ratio = targetRks / level
        // ratio > 1 表示目标 RKS 超过该定数 100% ACC 的上限，不可能达成
        if (ratio > 1.0 + 1e-9) return null
        val accPercent = ACC_ZERO_PERCENT + 45.0 * sqrt(ratio.coerceAtLeast(0.0))
        return accPercent / 100.0
    }

    /** 给定定数，返回 ACC 100% 时的单曲 RKS 上限（数值上等于定数）。 */
    fun maxRks(level: Double): Double = level

    /** 正向换算结果。 */
    sealed interface Outcome {
        /** 换算成功。 */
        data class Success(
            /** 单曲 RKS。 */
            val rks: Double,
            /** 解析后实际使用的 ACC 小数形式。 */
            val accFraction: Double,
        ) : Outcome

        /** ACC 低于 [ACC_MIN_PERCENT]，公式失去意义（界面显示「别越」）。 */
        data object TooLow : Outcome

        /** ACC 超过 100%。 */
        data object TooHigh : Outcome

        /** 定数或 ACC 未填写 / 无法解析。 */
        data object Empty : Outcome
    }

    /** 正向换算入口：直接把两个输入框的原始文本丢进来即可。 */
    fun evaluate(levelText: String, accText: String): Outcome {
        if (levelText.isBlank() || accText.isBlank()) return Outcome.Empty
        val level = parseLevel(levelText) ?: return Outcome.Empty
        val accFraction = parseAcc(accText) ?: return Outcome.Empty

        val accPercent = percentOf(accFraction)
        return when {
            accPercent > ACC_MAX_PERCENT + 1e-9 -> Outcome.TooHigh
            accPercent < ACC_MIN_PERCENT -> Outcome.TooLow
            else -> Outcome.Success(rks(level, accFraction), accFraction)
        }
    }

    /** 反向换算结果。 */
    sealed interface ReverseOutcome {
        /** 反推成功。 */
        data class Success(
            /** 需要的 ACC 小数形式。 */
            val accFraction: Double,
            /** 目标 RKS。 */
            val targetRks: Double,
        ) : ReverseOutcome

        /** 目标 RKS 超过该定数上限（100% ACC 也达不到）。 */
        data object Unreachable : ReverseOutcome

        /** 目标 RKS 过低，反推出的 ACC 低于有效下限。 */
        data object TooLow : ReverseOutcome

        /** 输入未填写 / 无法解析。 */
        data object Empty : ReverseOutcome
    }

    /** 反向换算入口：由目标 RKS 文本与定数文本反推所需 ACC。 */
    fun evaluateReverse(levelText: String, rksText: String): ReverseOutcome {
        if (levelText.isBlank() || rksText.isBlank()) return ReverseOutcome.Empty
        val level = parseLevel(levelText) ?: return ReverseOutcome.Empty
        val target = rksText.trim().toDoubleOrNull() ?: return ReverseOutcome.Empty
        if (target < 0.0) return ReverseOutcome.Empty

        val acc = requiredAcc(level, target) ?: return ReverseOutcome.Unreachable
        // 目标 RKS 过低时会反推出低于有效下限的 ACC，同样视为无意义
        if (percentOf(acc) < ACC_MIN_PERCENT) return ReverseOutcome.TooLow
        return ReverseOutcome.Success(acc, target)
    }
}
