package com.titradeck.app.chemistry

import java.math.BigInteger

data class EquationSpecies(val formula: String, val parsed: ParsedFormula)

data class BalancedEquation(
    val reactants: List<EquationSpecies>,
    val products: List<EquationSpecies>,
    val coefficients: List<Int>,
) {
    val species get() = reactants + products
    val formatted: String get() {
        fun side(start: Int, entries: List<EquationSpecies>) = entries.mapIndexed { index, entry ->
            val coefficient = coefficients[start + index]
            (if (coefficient == 1) "" else "$coefficient ") + entry.formula
        }.joinToString(" + ")
        return side(0, reactants) + " → " + side(reactants.size, products)
    }
}

/** Solves the homogeneous conservation system in exact rational arithmetic. */
object EquationBalancer {
    private val phaseBeforeSeparator = Regex("^\\((aq|s|l|g)\\)\\s*(?=\\+|$)", RegexOption.IGNORE_CASE)

    fun balance(input: String): BalancedEquation {
        require(input.length <= 2048) { "方程式最多 2048 个字符" }
        val sides = input.replace("⇌", "→").replace("⟶", "→").replace("->", "→").replace("=>", "→").replace("=", "→").split('→')
        require(sides.size == 2 && sides.all(String::isNotBlank)) { "请输入反应物 → 生成物，例如 H2 + O2 -> H2O" }
        fun parseSide(side: String) = splitSpecies(side).map { text ->
            val enteredCoefficient = Regex("^\\d+").find(text.trim())?.value
            if (enteredCoefficient != null) require(enteredCoefficient.toIntOrNull() in 1..1_000_000) { "已输入的系数必须是 1 至 1000000 的正整数" }
            val stripped = text.trim().replace(Regex("^\\d+\\s*"), "")
            require(stripped.isNotBlank()) { "系数后缺少化学式" }
            val parsed = FormulaParser.parse(stripped)
            val stateText = parsed.state?.let { "($it)" }.orEmpty()
            EquationSpecies(parsed.normalizedFormula + stateText, parsed)
        }
        val reactants = parseSide(sides[0])
        val products = parseSide(sides[1])
        val species = reactants + products
        require(species.size in 2..24) { "每个方程式需包含 2 至 24 个物种" }
        require(reactants.map { it.formula }.distinct().size == reactants.size && products.map { it.formula }.distinct().size == products.size) { "同一侧不能重复输入相同物种" }
        val elements = species.flatMap { it.parsed.composition.keys }.distinct()
        val rows = elements.map { symbol ->
            species.mapIndexed { index, entry -> Rational.of((entry.parsed.composition[symbol] ?: 0) * if (index < reactants.size) 1 else -1) }.toMutableList()
        }.toMutableList()
        if (species.any { it.parsed.charge != 0 }) rows += species.mapIndexed { index, entry -> Rational.of(entry.parsed.charge * if (index < reactants.size) 1 else -1) }.toMutableList()
        require(rows.isNotEmpty()) { "缺少可守恒的元素或电荷" }
        val pivots = mutableListOf<Int>()
        var pivotRow = 0
        for (column in species.indices) {
            val found = (pivotRow until rows.size).firstOrNull { !rows[it][column].isZero } ?: continue
            val temporary = rows[pivotRow]; rows[pivotRow] = rows[found]; rows[found] = temporary
            val divisor = rows[pivotRow][column]
            for (j in species.indices) rows[pivotRow][j] = rows[pivotRow][j] / divisor
            for (i in rows.indices) {
                if (i == pivotRow || rows[i][column].isZero) continue
                val factor = rows[i][column]
                for (j in species.indices) rows[i][j] = rows[i][j] - factor * rows[pivotRow][j]
            }
            pivots += column
            pivotRow++
            if (pivotRow == rows.size) break
        }
        val freeColumns = species.indices.filter { it !in pivots }
        require(freeColumns.isNotEmpty()) { "这些反应物与生成物无法同时满足元素和电荷守恒，请检查化学式" }
        require(freeColumns.size == 1) { "该物种集合有多个独立配平解；请限定反应产物或拆分反应，不能唯一确定化学计量" }
        val solution = MutableList(species.size) { Rational.ZERO }
        solution[freeColumns.single()] = Rational.ONE
        pivots.forEachIndexed { row, column -> solution[column] = -rows[row][freeColumns.single()] }
        val denominatorLcm = solution.fold(BigInteger.ONE) { accumulator, rational -> accumulator / accumulator.gcd(rational.denominator) * rational.denominator }
        var integers = solution.map { it.numerator * (denominatorLcm / it.denominator) }
        if (integers.all { it.signum() < 0 }) integers = integers.map { -it }
        require(integers.all { it.signum() > 0 }) { "无法让所有物种都获得正系数；请移除旁观物种或检查物种所在一侧" }
        val gcd = integers.reduce(BigInteger::gcd)
        val normalized = integers.map { it / gcd }
        require(normalized.all { it <= BigInteger.valueOf(1_000_000) }) { "配平系数过大，请检查输入" }
        // The preceding positive/upper-bound checks make this conversion exact,
        // without the API 31-only BigInteger.intValueExact method.
        val coefficients = normalized.map { it.toInt() }
        // Verify conservation independently after reducing the rational solution.
        for (element in elements) {
            val total = species.indices.sumOf { index -> coefficients[index].toLong() * (species[index].parsed.composition[element] ?: 0) * if (index < reactants.size) 1 else -1 }
            check(total == 0L) { "内部元素守恒校验失败" }
        }
        val totalCharge = species.indices.sumOf { index -> coefficients[index].toLong() * species[index].parsed.charge * if (index < reactants.size) 1 else -1 }
        check(totalCharge == 0L) { "内部电荷守恒校验失败" }
        return BalancedEquation(reactants, products, coefficients)
    }

    private fun splitSpecies(side: String): List<String> {
        val result = mutableListOf<String>()
        var start = 0
        for (position in side.indices) {
            if (side[position] != '+') continue
            val before = side.substring(start, position).trimEnd()
            val after = side.substring(position + 1).trimStart()
            val explicitCharge = Regex("\\^\\d*$").containsMatchIn(before)
            val chargeBeforeSeparator = after.startsWith('+') || phaseBeforeSeparator.containsMatchIn(after)
            if (explicitCharge || chargeBeforeSeparator || after.isEmpty()) continue
            require(before.isNotBlank()) { "加号之间缺少物种" }
            result += before
            start = position + 1
        }
        val last = side.substring(start).trim()
        require(last.isNotBlank()) { "方程式缺少物种" }
        result += last
        return result
    }
}
