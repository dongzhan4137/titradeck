package com.titradeck.app.chemistry

data class ParsedFormula(
    val normalizedFormula: String,
    val composition: Map<String, Int>,
    val molarMass: Double,
    val charge: Int = 0,
    val state: String? = null,
)

/** Formula-unit parser; supports nested groups, hydrates, states and explicit ionic charges. */
object FormulaParser {
    private const val MAX_COUNT = 10_000_000
    private val subscriptDigits = "₀₁₂₃₄₅₆₇₈₉"
    private val superscriptDigits = "⁰¹²³⁴⁵⁶⁷⁸⁹"

    fun parse(input: String): ParsedFormula {
        require(input.isNotBlank()) { "请输入化学式" }
        require(input.length <= 256) { "化学式最多 256 个字符" }
        var formula = input.filterNot(Char::isWhitespace).replace('−', '-').replace('·', '.').replace('•', '.')
        val stateMatch = Regex("\\((aq|s|l|g)\\)$", RegexOption.IGNORE_CASE).find(formula)
        val state = stateMatch?.groupValues?.get(1)?.lowercase()
        if (stateMatch != null) formula = formula.substring(0, stateMatch.range.first)

        // Unicode superscripts are unambiguously charges; normal subscripts remain atom counts.
        val superscriptMatch = Regex("([⁰¹²³⁴⁵⁶⁷⁸⁹]*)([⁺⁻])$").find(formula)
        if (superscriptMatch != null) {
            val magnitude = superscriptMatch.groupValues[1].map { superscriptDigits.indexOf(it).digitToChar() }.joinToString("")
            val sign = if (superscriptMatch.groupValues[2] == "⁺") "+" else "-"
            formula = formula.substring(0, superscriptMatch.range.first) + "^" + magnitude + sign
        }
        var charge = 0
        val explicitCharge = Regex("\\^(\\d*)([+-])$").find(formula)
        if (explicitCharge != null) {
            charge = chargeMagnitude(explicitCharge.groupValues[1]) * if (explicitCharge.groupValues[2] == "+") 1 else -1
            formula = formula.substring(0, explicitCharge.range.first)
        } else if (formula.endsWith('+') || formula.endsWith('-')) {
            val sign = if (formula.last() == '+') 1 else -1
            formula = formula.dropLast(1)
            // Fe3+ and [Fe(CN)6]3- use plain digits for charge. Unicode subscripts count atoms.
            val magnitude = Regex("^([A-Z][a-z]?)(\\d+)$").matchEntire(formula)
            val bracketMagnitude = Regex("^(.*])(\\d+)$").matchEntire(formula)
            val shorthand = magnitude ?: bracketMagnitude
            if (shorthand != null) {
                charge = sign * chargeMagnitude(shorthand.groupValues[2])
                formula = shorthand.groupValues[1]
            } else charge = sign
        }
        require(!formula.contains('^') && !formula.contains('+') && !formula.contains('-')) { "电荷请写在式末，例如 SO4^2-、Fe^3+；不要输入不明确的连字符" }
        if (formula == "e") {
            require(charge == -1) { "电子请写为 e^-" }
            return ParsedFormula("e^-", emptyMap(), 0.0, -1, state)
        }
        require(formula.isNotEmpty()) { "电荷前缺少化学式" }
        formula = formula.map { character ->
            val digit = subscriptDigits.indexOf(character)
            if (digit >= 0) ('0'.code + digit).toChar() else character
        }.joinToString("")
        val parts = formula.split('.')
        require(parts.none(String::isEmpty)) { "晶体水分隔符两侧都需要化学式" }
        val composition = linkedMapOf<String, Int>()
        for (part in parts) {
            val parser = GroupParser(part)
            val coefficient = parser.readNumber()
            val atoms = parser.readGroup(null, 0)
            require(atoms.isNotEmpty()) { "化学式分段不能为空" }
            for ((symbol, count) in atoms) add(composition, symbol, count.toLong() * coefficient)
        }
        require(composition.isNotEmpty()) { "请输入有效化学式" }
        val mass = composition.entries.sumOf { (symbol, count) -> Elements.bySymbol.getValue(symbol).atomicMass * count }
        val chargeText = if (charge == 0) "" else "^${if (kotlin.math.abs(charge) == 1) "" else kotlin.math.abs(charge)}${if (charge > 0) "+" else "-"}"
        return ParsedFormula(formula.replace('.', '·') + chargeText, composition.toMap(), mass, charge, state)
    }

    private fun chargeMagnitude(value: String): Int {
        val magnitude = if (value.isEmpty()) 1 else value.toIntOrNull()
        require(magnitude != null && magnitude in 1..100) { "电荷绝对值必须在 1 到 100 之间" }
        return magnitude
    }

    private fun add(target: MutableMap<String, Int>, symbol: String, amount: Long) {
        val sum = (target[symbol] ?: 0).toLong() + amount
        require(sum in 1..MAX_COUNT.toLong()) { "元素计数超出范围（每种元素最多 $MAX_COUNT 个）" }
        target[symbol] = sum.toInt()
    }

    private class GroupParser(private val source: String) {
        private var position = 0
        fun readNumber(): Int {
            val start = position
            while (position < source.length && source[position].isDigit()) position++
            if (position == start) return 1
            val text = source.substring(start, position)
            val number = text.toIntOrNull()
            require(number != null && number in 1..MAX_COUNT && !text.startsWith('0')) { "原子数和系数必须是正整数，且不能以 0 开头" }
            return number
        }

        fun readGroup(closing: Char?, depth: Int): Map<String, Int> {
            require(depth <= 12) { "括号嵌套最多 12 层" }
            val atoms = linkedMapOf<String, Int>()
            while (position < source.length) {
                val character = source[position]
                if (character in ")]}") {
                    require(character == closing) { "括号不匹配：位置 ${position + 1}" }
                    require(atoms.isNotEmpty()) { "括号内不能为空" }
                    position++
                    return atoms
                }
                if (character in "([{") {
                    position++
                    val expected = when (character) { '(' -> ')'; '[' -> ']'; else -> '}' }
                    val nested = readGroup(expected, depth + 1)
                    val multiplier = readNumber()
                    nested.forEach { (symbol, count) -> add(atoms, symbol, count.toLong() * multiplier) }
                } else {
                    require(character in 'A'..'Z') { "位置 ${position + 1} 不是有效的元素符号；请注意大小写" }
                    val start = position++
                    if (position < source.length && source[position] in 'a'..'z') position++
                    val symbol = source.substring(start, position)
                    require(Elements.bySymbol.containsKey(symbol)) { "未知元素：$symbol" }
                    add(atoms, symbol, readNumber().toLong())
                }
            }
            require(closing == null) { "缺少右括号 $closing" }
            return atoms
        }
    }
}
