package com.titradeck.app.chemistry

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class VseprPrediction(
    val notation: String,
    val electronGeometry: String,
    val molecularGeometry: String,
    val angles: String,
    val model: MoleculeModel,
)

/** Small, explicit educational models; deliberately not a general structure inference engine. */
object StructureTools {
    private fun atom(symbol: String, x: Double, y: Double, z: Double = 0.0) =
        MoleculeAtom(symbol, x.toFloat(), y.toFloat(), z.toFloat())

    private fun bond(a: Int, b: Int, order: Int = 1) = MoleculeBond(a, b, order)

    private fun model(atoms: List<MoleculeAtom>, bonds: List<MoleculeBond>, caption: String) =
        MoleculeModel(atoms, bonds, caption)

    private val tetrahedron = listOf(
        Triple(1.0, 1.0, 1.0), Triple(1.0, -1.0, -1.0),
        Triple(-1.0, 1.0, -1.0), Triple(-1.0, -1.0, 1.0),
    ).map { (x, y, z) -> Triple(x / sqrt(3.0), y / sqrt(3.0), z / sqrt(3.0)) }

    private fun centered(center: String, terminals: List<String>, directions: List<Triple<Double, Double, Double>>, caption: String): MoleculeModel {
        require(terminals.size == directions.size)
        return model(
            listOf(atom(center, 0.0, 0.0)) + terminals.zip(directions).map { (s, p) -> atom(s, p.first, p.second, p.third) },
            terminals.indices.map { bond(0, it + 1) }, caption,
        )
    }

    fun predictGeometry(bondDomains: Int, lonePairs: Int): VseprPrediction {
        require(bondDomains in 2..6 && lonePairs in 0..4 && bondDomains + lonePairs in 2..6) {
            "请使用 2–6 个成键电子域，且成键域与孤对之和为 2–6；此模型不含单配位和过渡金属配合物。"
        }
        val domains = bondDomains + lonePairs
        val electronGeometry = mapOf(2 to "直线形", 3 to "平面三角形", 4 to "四面体", 5 to "三角双锥", 6 to "八面体").getValue(domains)
        val geometry = when (domains to lonePairs) {
            2 to 0 -> "直线形"
            3 to 0 -> "平面三角形"
            3 to 1 -> "折线形"
            4 to 0 -> "四面体"
            4 to 1 -> "三角锥形"
            4 to 2 -> "折线形"
            5 to 0 -> "三角双锥"
            5 to 1 -> "跷跷板形"
            5 to 2 -> "T 形"
            5 to 3 -> "直线形"
            6 to 0 -> "八面体"
            6 to 1 -> "四方锥形"
            6 to 2 -> "平面正方形"
            6 to 3 -> "T 形（理想化）"
            6 to 4 -> "直线形（理想化）"
            else -> error("不支持的电子域组合")
        }
        val angles = when {
            geometry.startsWith("直线") -> "180°"
            domains == 3 -> if (lonePairs == 0) "120°" else "通常小于 120°"
            domains == 4 -> if (lonePairs == 0) "约 109.47°" else "通常小于 109.47°"
            domains == 5 && lonePairs < 2 -> "理想角 90°、120°、180°；孤对可使其偏离"
            else -> "理想角 90°、180°；实际键角可偏离"
        }
        val axial = listOf(Triple(0.0, 0.0, 1.0), Triple(0.0, 0.0, -1.0))
        val equatorial3 = (0..2).map { Triple(cos(it * 2.0 * Math.PI / 3.0), sin(it * 2.0 * Math.PI / 3.0), 0.0) }
        val equatorial4 = listOf(Triple(1.0, 0.0, 0.0), Triple(-1.0, 0.0, 0.0), Triple(0.0, 1.0, 0.0), Triple(0.0, -1.0, 0.0))
        val directions = when (domains) {
            2 -> listOf(Triple(-1.0, 0.0, 0.0), Triple(1.0, 0.0, 0.0))
            3 -> equatorial3.take(bondDomains)
            4 -> tetrahedron.take(bondDomains)
            5 -> axial + equatorial3.take(bondDomains - 2)
            else -> when (lonePairs) {
                0 -> equatorial4 + axial
                1 -> equatorial4 + axial.take(1)
                else -> equatorial4.take(bondDomains)
            }
        }
        val notation = "AX$bondDomains" + if (lonePairs > 0) "E$lonePairs" else ""
        return VseprPrediction(notation, electronGeometry, geometry, angles,
            centered("A", List(bondDomains) { "X" }, directions, "$notation：A 为中心，X 为成键原子；孤对 E 不绘制，坐标是理想电子域方向。"))
    }

    fun degreeOfUnsaturation(formula: String): Int {
        require(formula.trim().firstOrNull()?.isDigit() != true) { "请输入单个分子的化学式，不要在前面添加反应系数。" }
        require(!formula.contains('·') && !formula.contains('.') && !formula.contains('+')) { "不饱和度需输入单个中性分子的化学式，不接受水合物、混合物或离子。" }
        val parsed = FormulaParser.parse(formula)
        require(parsed.charge == 0) { "不饱和度公式仅适用于中性闭壳层分子。" }
        val counts = parsed.composition
        require(counts.keys.all { it in setOf("C", "H", "N", "O", "S", "F", "Cl", "Br", "I") }) { "此公式仅支持 C、H、N、O、S 及 F/Cl/Br/I。" }
        val carbon = counts["C"] ?: 0
        require(carbon > 0) { "此有机不饱和度模型要求分子含 C。" }
        val halogens = listOf("F", "Cl", "Br", "I").sumOf { (counts[it] ?: 0).toLong() }
        val numerator = 2L * carbon + 2L + (counts["N"] ?: 0) - (counts["H"] ?: 0) - halogens
        require(numerator >= 0 && numerator % 2L == 0L && numerator <= Int.MAX_VALUE.toLong() * 2) { "该式得到负数或半整数 DBE，不符合此中性闭壳层模型；请检查化学式、价态或电荷。" }
        return (numerator / 2).toInt()
    }

    private fun integer(input: Map<String, String>, key: String, fallback: String): Int =
        (input[key] ?: fallback).trim().toIntOrNull() ?: throw IllegalArgumentException("请输入整数电子域数。")

    val tools: List<ToolDefinition> = listOf(
        ToolDefinition("vsepr", "VSEPR 几何预测", "用成键电子域与孤对预测单中心构型", ToolCategory.STRUCTURE,
            "电子域数 = 成键域 m + 孤对数 n；AXₘEₙ",
            listOf(FieldSpec("bondDomains", "成键电子域 m", "2", "双键或三键各算 1 个电子域"), FieldSpec("lonePairs", "中心原子孤对数 n", "2")),
        ) { input ->
            val prediction = predictGeometry(integer(input, "bondDomains", "2"), integer(input, "lonePairs", "2"))
            CalculationResult("分子构型", prediction.molecularGeometry,
                rows = listOf(ResultRow("VSEPR 型号", prediction.notation), ResultRow("电子域几何", prediction.electronGeometry), ResultRow("键角范围", prediction.angles)),
                steps = listOf("将每个单键、双键或三键视为 1 个成键电子域。", "电子域总数 = m + n，先确定电子域几何。", "安排孤对以减小排斥，再观察成键原子的空间位置。"),
                notes = listOf("只适用 2–6 电子域的单中心主族教学模型；不自动由分子式确定孤对，不用于过渡金属配合物。", "图示是理想电子域方向；孤对与不同配体会改变实际键角，不能替代实验或量子化学优化。"),
                molecule = prediction.model)
        },
        ToolDefinition("unsaturation", "不饱和度 DBE", "由 CHNXO/S 化学式计算环与 π 键当量", ToolCategory.STRUCTURE,
            "DBE = (2C + 2 + N − H − X)/2，X = F + Cl + Br + I",
            listOf(FieldSpec("formula", "分子式", "C6H6", "例如 C6H6、C2H3Cl、C8H10N4O2", inputKind = InputKind.CHEMICAL_FORMULA)),
        ) { input ->
            val formula = (input["formula"] ?: "C6H6").trim()
            val parsed = FormulaParser.parse(formula)
            val value = degreeOfUnsaturation(formula)
            val x = listOf("F", "Cl", "Br", "I").sumOf { parsed.composition[it] ?: 0 }
            CalculationResult("不饱和度", "$value", "DBE",
                rows = listOf(ResultRow("卤素原子总数 X", "$x"), ResultRow("分子式", displayChemicalFormula(parsed.normalizedFormula))),
                steps = listOf("解析化学式得到 C=${parsed.composition["C"] ?: 0}，H=${parsed.composition["H"] ?: 0}，N=${parsed.composition["N"] ?: 0}，X=$x。", "代入 (2C + 2 + N − H − X)/2 = $value。", "1 个环或 1 个双键贡献 1；1 个三键贡献 2。"),
                notes = listOf("适用于通常价态的中性闭壳层 CHNXO/S 有机分子；O 和通常二价 S 不进入公式。", "DBE 不确定连接方式、芳香性或具体异构体；不用于自由基、离子、含金属或异常价态体系。"))
        },
    )
}
