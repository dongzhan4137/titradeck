package com.titradeck.app.chemistry

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class StoichiometrySolution(
    val extent: Double,
    val limitingIndices: List<Int>,
    val allReactantsKnown: Boolean,
    val fromProduct: Boolean,
)

object StoichiometrySolver {
    fun solve(equation: BalancedEquation, knownMoles: Map<Int, Double>): StoichiometrySolution {
        require(knownMoles.isNotEmpty()) { "至少输入一种已知物质的量" }
        require(knownMoles.keys.all { it in equation.species.indices }) { "已知物种不在方程式中" }
        require(knownMoles.values.all { it.isFinite() && it > 0.0 && it <= 1e100 }) { "已知物质的量必须为正数且不超过 1e100 mol" }
        val reactants = knownMoles.filterKeys { it < equation.reactants.size }
        if (reactants.isEmpty()) {
            require(knownMoles.size == 1) { "由生成物反推时，请只输入一种生成物；实际产量应填在产率栏" }
            val (index, moles) = knownMoles.entries.single()
            return StoichiometrySolution(moles / equation.coefficients[index], emptyList(), false, true)
        }
        require(reactants.size == knownMoles.size) { "投料栏不要混填反应物与生成物；生成物实测量请填入产率栏" }
        val ratios = reactants.mapValues { (index, amount) -> amount / equation.coefficients[index] }
        val extent = ratios.values.min()
        val limiting = ratios.filterValues { abs(it - extent) <= extent * 1e-9 }.keys.toList()
        return StoichiometrySolution(extent, limiting, reactants.size == equation.reactants.size, false)
    }
}

data class EmpiricalSolution(val atoms: Map<String, Int>, val formula: String, val molarMass: Double, val maximumPercentError: Double)

object EmpiricalFormulaSolver {
    fun solve(percentages: Map<String, Double>, tolerance: Double = 0.15): EmpiricalSolution {
        require(percentages.isNotEmpty() && percentages.size <= 24) { "请输入 1 至 24 种元素的质量百分比" }
        require(tolerance.isFinite() && tolerance > 0 && tolerance <= 1.0) { "允许偏差须大于 0 且不超过 1 个百分点" }
        percentages.forEach { (symbol, percent) ->
            require(Elements.bySymbol.containsKey(symbol)) { "未知元素：$symbol" }
            require(percent.isFinite() && percent > 0 && percent <= 100) { "$symbol 的百分比须在 0 到 100 之间" }
        }
        val sum = percentages.values.sum()
        require(abs(sum - 100.0) <= maxOf(0.5, tolerance * percentages.size)) { "质量百分比之和应接近 100%，当前为 ${reactionFormat(sum)}%" }
        val normalized = percentages.mapValues { it.value * 100.0 / sum }
        val molar = normalized.mapValues { (symbol, percent) -> percent / Elements.bySymbol.getValue(symbol).atomicMass }
        val smallest = molar.values.min()
        val ratios = molar.mapValues { it.value / smallest }
        for (multiplier in 1..24) {
            if (ratios.values.any { !it.isFinite() || it * multiplier > 10000 }) continue
            val raw = ratios.mapValues { (_, ratio) -> (ratio * multiplier).roundToInt().coerceAtLeast(1) }
            val gcd = raw.values.reduce(::integerGcd)
            val atoms = raw.mapValues { it.value / gcd }
            val mass = atoms.entries.sumOf { (symbol, count) -> Elements.bySymbol.getValue(symbol).atomicMass * count }
            val error = atoms.entries.maxOf { (symbol, count) -> abs(count * Elements.bySymbol.getValue(symbol).atomicMass / mass * 100 - normalized.getValue(symbol)) }
            if (error <= tolerance) return EmpiricalSolution(atoms, renderFormula(atoms), mass, error)
        }
        throw IllegalArgumentException("在给定组成精度内未找到小整数比；请检查百分比、提高测量精度或调整允许偏差")
    }

    fun molecularFormula(empirical: EmpiricalSolution, measuredMolarMass: Double): Pair<String, Int> {
        require(measuredMolarMass.isFinite() && measuredMolarMass > 0 && measuredMolarMass <= 1e8) { "分子摩尔质量必须为正数且不超过 1e8 g/mol" }
        val ratio = measuredMolarMass / empirical.molarMass
        require(ratio <= 1e6) { "分子式倍数过大，请检查摩尔质量" }
        val factor = ratio.roundToInt()
        require(factor >= 1 && abs(measuredMolarMass - factor * empirical.molarMass) <= maxOf(0.1, measuredMolarMass * 0.01)) { "给定摩尔质量不是经验式量的合理整数倍（允许 1% 偏差）" }
        require(empirical.atoms.values.all { it.toLong() * factor <= 10_000_000L }) { "分子式下标超出范围，请检查摩尔质量" }
        val atoms = empirical.atoms.mapValues { (_, count) -> count * factor }
        return renderFormula(atoms) to factor
    }

    fun renderFormula(atoms: Map<String, Int>): String {
        val order = if (atoms.containsKey("C")) listOf("C", "H").filter(atoms::containsKey) + atoms.keys.filter { it != "C" && it != "H" }.sorted() else atoms.keys.sorted()
        return order.joinToString("") { symbol -> symbol + if (atoms.getValue(symbol) == 1) "" else atoms.getValue(symbol).toString() }
    }

    private fun integerGcd(a: Int, b: Int): Int = if (b == 0) a else integerGcd(b, a % b)
}

object ReactionTools {
    val tools = listOf(
        ToolDefinition(
            id = "molar_mass", title = "化学式与摩尔质量", subtitle = "复杂括号、晶体水与元素质量分数", category = ToolCategory.CORE,
            formula = "M = Σ aᵢAᵣ,ᵢ；wᵢ = aᵢAᵣ,ᵢ / M × 100%",
            fields = listOf(FieldSpec("formula", "化学式", "CuSO₄·5H₂O", "如 C₆H₁₂O₆、Fe₂(SO₄)₃、[Fe(CN)6]^3-")),
            calculate = ::calculateMolarMass,
        ),
        ToolDefinition(
            id = "balance", title = "化学方程式配平", subtitle = "元素与电荷守恒 · 最简整数系数", category = ToolCategory.CORE,
            formula = "Aν = 0；反应前后各元素原子数与总电荷相等",
            fields = listOf(FieldSpec("equation", "反应方程式", "Fe2(SO4)3 + KOH -> Fe(OH)3 + K2SO4", "用 + 分隔物种；用 -> 或 → 分隔两侧", multiline = true)),
            calculate = ::calculateBalance,
        ),
        ToolDefinition(
            id = "stoichiometry", title = "化学计量与产率", subtitle = "质量、物质的量、气体体积与极限反应物", category = ToolCategory.CORE,
            formula = "n = m/M = PV/(RT)；ξ = min(nᵢ/νᵢ)；η = 实际产量/理论产量 × 100%",
            fields = listOf(
                FieldSpec("equation", "反应方程式", "H2(g) + O2(g) -> H2O(l)", "先指定反应物和生成物，系数自动配平", multiline = true),
                FieldSpec("amounts", "已知量（每行一种物质）", "H2: 4 g\nO2: 32 g", "格式：化学式: 数值 单位；支持 g、kg、mg、mol、mmol、L、mL、个", multiline = true),
                FieldSpec("temperature", "气体温度 / K", "273.15", "仅气体体积换算使用"),
                FieldSpec("pressure", "气体绝对压力 / Pa", "101325", "仅气体体积换算使用"),
                FieldSpec("actualProduct", "实测生成物（可选）", "H2O", "与方程式中的生成物对应"),
                FieldSpec("actualAmount", "实际产量（可选，含单位）", "", "如 30 g；填写后计算产率"),
            ),
            calculate = ::calculateStoichiometry,
        ),
        ToolDefinition(
            id = "empirical", title = "经验式与分子式", subtitle = "由元素分析数据推导最简组成", category = ToolCategory.CORE,
            formula = "nᵢ ∝ wᵢ/Aᵣ,ᵢ；分子式倍数 k = M分子/M经验式",
            fields = listOf(
                FieldSpec("percentages", "元素质量百分比（每行一项）", "C: 40.00\nH: 6.71\nO: 53.29", "如 C: 40.00；数值单位为 %", multiline = true),
                FieldSpec("molarMass", "分子摩尔质量 / g·mol⁻¹（可选）", "180.156", "留空只求经验式"),
                FieldSpec("tolerance", "允许组成偏差 / 百分点", "0.15", "用于处理元素分析数据的舍入误差"),
            ),
            calculate = ::calculateEmpirical,
        ),
    )

    private fun calculateMolarMass(values: Map<String, String>): CalculationResult {
        val parsed = FormulaParser.parse(values["formula"].orEmpty())
        val rows = parsed.composition.map { (symbol, count) ->
            val elementMass = Elements.bySymbol.getValue(symbol).atomicMass * count
            ResultRow("$symbol · $count 个原子", "${reactionFormat(elementMass)} g/mol；${reactionFormat(elementMass / parsed.molarMass * 100)}", "%")
        }
        val steps = parsed.composition.map { (symbol, count) -> "$symbol：$count × ${reactionFormat(Elements.bySymbol.getValue(symbol).atomicMass)} g/mol = ${reactionFormat(Elements.bySymbol.getValue(symbol).atomicMass * count)} g/mol" } +
            listOf("将各元素贡献相加：M(${displayChemicalFormula(parsed.normalizedFormula)}) = ${reactionFormat(parsed.molarMass)} g/mol", "元素质量分数 = 该元素质量贡献 ÷ 总摩尔质量 × 100%；质量分数之和为 100%")
        val notes = mutableListOf("摩尔质量使用离线元素表中的平均原子量；天然同位素组成及舍入会影响末位数，不等同于单同位素精确质量。")
        val representative = parsed.composition.keys.filter { Elements.bySymbol.getValue(it).massIsRepresentative }
        if (representative.isNotEmpty()) notes += "${representative.joinToString("、")} 没有标准原子量，此处使用元素表标注的代表同位素质量；需精确同位素质量时应另行指定。"
        if (parsed.charge != 0) notes += "离子以组成元素原子量之和计算，未扣除或增加电子质量；电荷为 ${parsed.charge}。"
        if (parsed.composition.isEmpty()) notes += "电子在反应计量中的元素计数为零；此处采用忽略电子质量的化学近似。"
        return CalculationResult("${displayChemicalFormula(parsed.normalizedFormula)} 的摩尔质量", reactionFormat(parsed.molarMass), "g/mol", rows, steps, notes)
    }

    private fun calculateBalance(values: Map<String, String>): CalculationResult {
        val equation = EquationBalancer.balance(values["equation"].orEmpty())
        val elements = equation.species.flatMap { it.parsed.composition.keys }.distinct()
        val rows = elements.map { element ->
            val left = equation.reactants.indices.sumOf { equation.coefficients[it].toLong() * (equation.reactants[it].parsed.composition[element] ?: 0) }
            val right = equation.products.indices.sumOf { equation.coefficients[it + equation.reactants.size].toLong() * (equation.products[it].parsed.composition[element] ?: 0) }
            ResultRow("$element 守恒", "$left = $right", "个原子")
        }.toMutableList()
        if (equation.species.any { it.parsed.charge != 0 }) {
            val leftCharge = equation.reactants.indices.sumOf { equation.coefficients[it].toLong() * equation.reactants[it].parsed.charge }
            val rightCharge = equation.products.indices.sumOf { equation.coefficients[it + equation.reactants.size].toLong() * equation.products[it].parsed.charge }
            rows += ResultRow("电荷守恒", "$leftCharge = $rightCharge", "e")
        }
        return CalculationResult(
            "最简整数配平", displayChemicalEquation(equation), rows = rows,
            steps = listOf("解析每个物种的元素组成与电荷。", "建立元素及电荷守恒线性方程组 Aν = 0，使用精确有理数消元。", "将唯一比例解化为最简正整数比：${equation.coefficients.joinToString(" : ")}。", "逐项复核左右两侧的原子数与总电荷。"),
            notes = listOf("配平只验证守恒，不判断反应是否自发、实际发生或产物是否正确。", "离子推荐写为 Fe^3+、SO4^2-，电子写为 e^-；多个独立解会要求进一步限定反应。"),
        )
    }

    private fun calculateStoichiometry(values: Map<String, String>): CalculationResult {
        val equation = EquationBalancer.balance(values["equation"].orEmpty())
        val temperature = positiveNumber(values["temperature"] ?: "273.15", "气体温度")
        val pressure = positiveNumber(values["pressure"] ?: "101325", "气体绝对压力")
        val gasMolarVolume = 8.31446261815324 * temperature / pressure * 1000.0 // L/mol
        require(gasMolarVolume.isFinite() && gasMolarVolume > 0) { "气体条件超出数值范围" }
        val lines = values["amounts"].orEmpty().lines().map(String::trim).filter(String::isNotEmpty)
        require(lines.size in 1..24) { "请输入 1 至 24 行已知量" }
        val known = linkedMapOf<Int, Double>()
        val conversionSteps = mutableListOf<String>()
        val gasInputs = mutableSetOf<Int>()
        for (line in lines) {
            val parts = line.split(Regex("[:：=]"), limit = 2)
            require(parts.size == 2) { "已知量格式应为 化学式: 数值 单位，例如 H2: 4 g" }
            val index = findSpecies(equation, parts[0])
            require(!known.containsKey(index)) { "${displayChemicalFormula(equation.species[index].formula)} 重复输入" }
            val amount = parseAmount(parts[1], equation.species[index].parsed, gasMolarVolume, allowZero = false)
            known[index] = amount.moles
            conversionSteps += "${displayChemicalFormula(equation.species[index].formula)}：${amount.step}"
            if (amount.gas) gasInputs += index
        }
        val solution = StoichiometrySolver.solve(equation, known)
        val rows = mutableListOf<ResultRow>()
        for ((index, species) in equation.species.withIndex()) {
            val moles = solution.extent * equation.coefficients[index]
            val verb = if (index < equation.reactants.size) "消耗" else "理论生成"
            rows += ResultRow("${displayChemicalFormula(species.formula)} · $verb", "${reactionFormat(moles)} mol · ${reactionFormat(moles * species.parsed.molarMass)}", "g")
            rows += ResultRow("${displayChemicalFormula(species.formula)} · 对应粒子数", reactionFormat(moles * 6.02214076e23), "个")
            if (species.parsed.state == "g" || index in gasInputs) rows += ResultRow("${displayChemicalFormula(species.formula)} · 理想气体体积", reactionFormat(moles * gasMolarVolume), "L")
            if (index < equation.reactants.size && known.containsKey(index)) {
                val remaining = (known.getValue(index) - moles).coerceAtLeast(0.0)
                rows += ResultRow("${displayChemicalFormula(species.formula)} · 剩余投料", "${reactionFormat(remaining)} mol · ${reactionFormat(remaining * species.parsed.molarMass)}", "g")
            }
        }
        val steps = mutableListOf("自动配平：${displayChemicalEquation(equation)}")
        steps += conversionSteps
        if (solution.fromProduct) steps += "按指定生成物反推：ξ = n/ν = ${reactionFormat(solution.extent)} mol；假设该产物为理论生成量。"
        else {
            known.forEach { (index, amount) -> steps += "${displayChemicalFormula(equation.species[index].formula)}：n/ν = ${reactionFormat(amount)} mol ÷ ${equation.coefficients[index]} = ${reactionFormat(amount / equation.coefficients[index])} mol" }
            steps += "取最小反应进度：ξ = ${reactionFormat(solution.extent)} mol。各物种的消耗或生成量 nᵢ = νᵢξ。"
        }
        steps += "质量 mᵢ = nᵢ × Mᵢ；粒子数 Nᵢ = nᵢ × 6.02214076×10²³ mol⁻¹。"
        steps += "气态物种：V = nRT/P；T = ${reactionFormat(temperature)} K，P = ${reactionFormat(pressure)} Pa，Vₘ = ${reactionFormat(gasMolarVolume)} L/mol。"
        val notes = mutableListOf("假设仅发生所写反应、纯物质投料、反应达到化学计量终点；理论产量按 100% 转化计算。")
        if (!solution.allReactantsKnown && !solution.fromProduct) {
            val missing = equation.reactants.indices.filter { it !in known }.joinToString("、") { displayChemicalFormula(equation.reactants[it].formula) }
            notes += "未提供 $missing 的投料，假设其足量；极限物质判断仅针对已输入反应物。"
        }
        if (solution.fromProduct) notes += "仅由产物反推所需反应物，无法识别实际投料中的极限反应物。"
        val actualText = values["actualAmount"].orEmpty().trim()
        if (actualText.isNotEmpty()) {
            require(!solution.fromProduct) { "计算产率时请用反应物投料求理论量，再输入实际生成物产量" }
            val index = findSpecies(equation, values["actualProduct"].orEmpty())
            require(index >= equation.reactants.size) { "实际产量必须对应方程式右侧的生成物" }
            val actual = parseAmount(actualText, equation.species[index].parsed, gasMolarVolume, allowZero = true)
            val theoretical = solution.extent * equation.coefficients[index]
            val yield = actual.moles / theoretical * 100.0
            require(yield.isFinite()) { "产率超出数值范围，请检查数值" }
            rows.add(0, ResultRow("${displayChemicalFormula(equation.species[index].formula)} · 产率", reactionFormat(yield), "%"))
            steps += "实际产量换算：${actual.step}；η = ${reactionFormat(actual.moles)} mol / ${reactionFormat(theoretical)} mol × 100% = ${reactionFormat(yield)}%。"
            if (yield > 100.0 + 1e-8) notes += "产率超过 100%，请检查产物纯度、含水量、单位及测量误差。"
        }
        val headline: String
        val answer: String
        if (solution.fromProduct) { headline = "按生成物反推"; answer = "ξ = ${reactionFormat(solution.extent)} mol" }
        else {
            headline = if (solution.allReactantsKnown) "极限反应物" else "限定投料（其余足量）"
            answer = solution.limitingIndices.joinToString("、") { displayChemicalFormula(equation.species[it].formula) }
        }
        return CalculationResult(headline, answer, rows = rows, steps = steps, notes = notes)
    }

    private fun calculateEmpirical(values: Map<String, String>): CalculationResult {
        val composition = linkedMapOf<String, Double>()
        val lines = values["percentages"].orEmpty().lines().map(String::trim).filter(String::isNotEmpty)
        for (line in lines) {
            val parts = line.split(Regex("[:：=]"), limit = 2)
            require(parts.size == 2) { "格式应为 元素: 质量百分比，例如 C: 40.00" }
            val symbol = parts[0].trim()
            require(!composition.containsKey(symbol)) { "元素 $symbol 重复输入" }
            composition[symbol] = positiveNumber(parts[1].trim().removeSuffix("%"), "$symbol 的质量百分比")
        }
        val empirical = EmpiricalFormulaSolver.solve(composition, positiveNumber(values["tolerance"] ?: "0.15", "允许偏差"))
        val rows = mutableListOf(ResultRow("经验式", displayChemicalFormula(empirical.formula)), ResultRow("经验式摩尔质量", reactionFormat(empirical.molarMass), "g/mol"), ResultRow("最大组成拟合偏差", reactionFormat(empirical.maximumPercentError), "百分点"))
        val steps = composition.map { (symbol, percent) -> "$symbol：假设取 100 g 样品，n = ${reactionFormat(percent)} g / ${reactionFormat(Elements.bySymbol.getValue(symbol).atomicMass)} g·mol⁻¹ = ${reactionFormat(percent / Elements.bySymbol.getValue(symbol).atomicMass)} mol。" }.toMutableList()
        steps += "将各物质的量除以最小值，在允许组成偏差内寻找最小整数比，再约去公因数：${displayChemicalFormula(empirical.formula)}。"
        var answer = displayChemicalFormula(empirical.formula)
        val measuredMass = values["molarMass"].orEmpty().trim()
        if (measuredMass.isNotEmpty()) {
            val molecular = EmpiricalFormulaSolver.molecularFormula(empirical, positiveNumber(measuredMass, "分子摩尔质量"))
            answer = displayChemicalFormula(molecular.first)
            rows.add(0, ResultRow("分子式", answer))
            rows += ResultRow("经验式倍数", molecular.second.toString())
            steps += "k = M分子 / M经验式 ≈ ${molecular.second}；所有原子下标乘以 k，得到 $answer。"
        }
        return CalculationResult(if (measuredMass.isEmpty()) "经验式" else "分子式", answer, rows = rows, steps = steps,
            notes = listOf("输入应覆盖样品全部元素；百分比在舍入容差内归一化为 100%。", "经验式受实验精度限制，使用 1 至 24 的分母搜索小整数比；不能单凭组成确定结构或异构体。", "分子式推导要求给定摩尔质量与经验式量的整数倍在 1% 内相符。"))
    }

    private fun findSpecies(equation: BalancedEquation, input: String): Int {
        val parsed = FormulaParser.parse(input.trim())
        val exact = equation.species.indices.filter { equation.species[it].parsed.normalizedFormula == parsed.normalizedFormula && (parsed.state == null || equation.species[it].parsed.state == parsed.state) }
        if (exact.size == 1) return exact.single()
        val equivalent = equation.species.indices.filter { equation.species[it].parsed.composition == parsed.composition && equation.species[it].parsed.charge == parsed.charge && (parsed.state == null || equation.species[it].parsed.state == parsed.state) }
        require(equivalent.size == 1) { if (equivalent.size > 1) "物种 $input 不唯一，请按方程式写出化学式及相态" else "物种 $input 不在反应方程式中" }
        return equivalent.single()
    }

    private data class ConvertedAmount(val moles: Double, val step: String, val gas: Boolean)
    private fun parseAmount(text: String, species: ParsedFormula, gasMolarVolume: Double, allowZero: Boolean): ConvertedAmount {
        val match = Regex("^\\s*([+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?)\\s*(kg|mg|ug|µg|μg|g|mmol|umol|µmol|μmol|mol|mL|L|个)\\s*$").matchEntire(text)
        require(match != null) { "数值后请附单位：g、kg、mg、μg、mol、mmol、μmol、L、mL 或 个" }
        val amount = match.groupValues[1].toDoubleOrNull()
        require(amount != null && amount.isFinite() && amount <= 1e100 && (if (allowZero) amount >= 0.0 else amount > 0.0)) { "数量须为${if (allowZero) "非负" else "正"}有限数，且不超过 1e100" }
        val unit = match.groupValues[2]
        val massScale = mapOf("kg" to 1000.0, "g" to 1.0, "mg" to 1e-3, "ug" to 1e-6, "µg" to 1e-6, "μg" to 1e-6)
        val molScale = mapOf("mol" to 1.0, "mmol" to 1e-3, "umol" to 1e-6, "µmol" to 1e-6, "μmol" to 1e-6)
        val moles: Double
        val step: String
        when {
            unit in massScale -> {
                val molarMass = species.molarMass
                require(molarMass > 0.0) { "电子不能使用质量输入；请使用物质的量或粒子数" }
                val grams = amount * massScale.getValue(unit)
                moles = grams / molarMass
                step = "${reactionFormat(amount)} $unit = ${reactionFormat(grams)} g；n = ${reactionFormat(grams)} g / ${reactionFormat(molarMass)} g·mol⁻¹ = ${reactionFormat(moles)} mol"
            }
            unit in molScale -> {
                moles = amount * molScale.getValue(unit)
                step = "${reactionFormat(amount)} $unit × ${reactionFormat(molScale.getValue(unit))} mol/$unit = ${reactionFormat(moles)} mol"
            }
            unit == "个" -> {
                moles = amount / 6.02214076e23
                step = "n = ${reactionFormat(amount)} 个 / (6.02214076×10²³ 个/mol) = ${reactionFormat(moles)} mol"
            }
            else -> {
                require(species.state == null || species.state == "g") { "体积单位仅用于气体；请检查物种相态或改用质量、物质的量或粒子数" }
                val liters = amount * if (unit == "mL") 0.001 else 1.0
                moles = liters / gasMolarVolume
                step = "${reactionFormat(amount)} $unit = ${reactionFormat(liters)} L；n = V/Vₘ = ${reactionFormat(liters)} L / ${reactionFormat(gasMolarVolume)} L·mol⁻¹ = ${reactionFormat(moles)} mol"
            }
        }
        require(moles.isFinite() && (if (allowZero) moles >= 0 else moles > 0)) { "数量换算超出有效数值范围" }
        return ConvertedAmount(moles, step, unit == "L" || unit == "mL")
    }

    private fun positiveNumber(input: String, label: String): Double {
        val number = input.trim().toDoubleOrNull()
        require(number != null && number.isFinite() && number > 0 && number <= 1e100) { "$label 必须是正有限数，且不超过 1e100" }
        return number
    }
}

private fun reactionFormat(value: Double): String {
    require(value.isFinite()) { "结果超出数值范围，请检查输入与单位" }
    return if (value == 0.0) "0" else String.format(Locale.ROOT, "%.8g", value)
}
