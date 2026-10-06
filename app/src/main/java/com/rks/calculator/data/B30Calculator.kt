package com.rks.calculator.data

import com.rks.calculator.util.AppLog

/**
 * B30 计算。
 *
 * 算法（严格按需求的伪代码实现）：
 *
 * ```text
 * ① 把存档里所有歌曲的 RKS 算出来，放进一个列表
 * ② 排序，取最高的 27 首
 * ③ 把所有满分（1000000 分）的歌曲拿出来，放进另一个列表
 * ④ 排序，取最高的 3 首
 * ⑤ 把这 27 首和这 3 首的 RKS 全部加起来，除以 30
 * ```
 *
 * 说明：
 * - 满分歌的 ACC 是 100%，代入 RKS 公式后 `((100-55)/45)² × 定数 = 定数`，
 *   所以它的 RKS 在数值上等于定数。这里统一按「算 RKS」处理，不设特例。
 * - 两个列表相互独立，**不去重**：一首满分歌如果同时进了前 27 名，
 *   它会出现在两个列表里，被计入两次（这是需求明确要求的）。
 */
object B30Calculator {

    /** 取最高的 27 首普通成绩。 */
    const val BEST_COUNT = 27

    /** 再取最高的 3 首满分成绩。 */
    const val PERFECT_COUNT = 3

    /** 最终分母。 */
    const val TOTAL_SLOTS = 30

    /** 一首入选的歌曲。 */
    data class Entry(
        val record: SaveParser.Record,
        /** 该曲的单曲 RKS。 */
        val rks: Double,
    )

    /** 计算结果。 */
    data class Result(
        /** 前 27 名。 */
        val best: List<Entry>,
        /** 前 3 首满分。 */
        val perfects: List<Entry>,
        /** 前 27 名的 RKS 之和。 */
        val bestSum: Double,
        /** 前 3 首满分的 RKS 之和。 */
        val perfectSum: Double,
        /** 最终 B30。 */
        val b30: Double,
        /** 分母，固定为 30。 */
        val divisor: Int,
        /** 参与计算的有效成绩总数。 */
        val validCount: Int,
        /** 存档里满分成绩的总数。 */
        val perfectCount: Int,
    )

    /**
     * 计算 B30。
     *
     * @param records 存档解析出的成绩；内部自行过滤无法计算 RKS 的记录
     */
    fun calculate(records: List<SaveParser.Record>): Result {
        AppLog.i("B30", "开始计算，输入 ${records.size} 条")

        // ① 所有能算出 RKS 的歌
        val all = records.filter { it.rks != null }
        AppLog.i("B30", "可算 RKS 的 ${all.size} 条")

        // ② 排序取前 27
        val best = all
            .sortedByDescending { it.rks!! }
            .take(BEST_COUNT)
            .map { Entry(it, it.rks!!) }

        // ③④ 满分歌单独拿出来，排序取前 3
        val perfectAll = all.filter { it.isPerfect }
        val perfects = perfectAll
            .sortedByDescending { it.rks!! }
            .take(PERFECT_COUNT)
            .map { Entry(it, it.rks!!) }

        // ⑤ 相加除以 30
        val bestSum = best.sumOf { it.rks }
        val perfectSum = perfects.sumOf { it.rks }

        // 分母固定为 30：不管实际有几条数据，一律除以 30。
        // 例如只有 28 条（1 首满分 + 27 首普通），或者只有 1 条，分母也还是 30。
        val divisor = TOTAL_SLOTS
        val b30 = (bestSum + perfectSum) / divisor

        AppLog.i(
            "B30",
            "结果：前27=${best.size} 首（和 ${"%.4f".format(bestSum)}），" +
                "满分=${perfects.size} 首（和 ${"%.4f".format(perfectSum)}），" +
                "分母=$divisor，B30=${"%.4f".format(b30)}",
        )

        return Result(
            best = best,
            perfects = perfects,
            bestSum = bestSum,
            perfectSum = perfectSum,
            b30 = b30,
            divisor = divisor,
            validCount = all.size,
            perfectCount = perfectAll.size,
        )
    }
}
