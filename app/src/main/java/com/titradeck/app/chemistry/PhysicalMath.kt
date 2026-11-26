package com.titradeck.app.chemistry

import kotlin.math.*

/** Ideal-solution, ideal-gas and elementary equilibrium models, with SI constants. */
object PhysicalMath {
    const val R = 8.31446261815324
    const val F = 96485.33212331002
    const val NA = 6.02214076e23
    const val ATM = 101325.0

    data class Concentration(val molarity: Double, val molality: Double, val normality: Double, val massFraction: Double)
    data class AcidBase(val ph: Double, val poh: Double, val hydrogen: Double, val hydroxide: Double, val ionizedFraction: Double?)
    data class Gas(val pressure: Double, val volume: Double, val amount: Double, val temperature: Double)
    data class Solubility(val solubility: Double, val ionA: Double, val ionB: Double)
    data class Complex(val bound: Double, val freeMetal: Double, val freeLigand: Double)

    fun concentration(value: Double, kind: String, molarMass: Double, density: Double, equivalents: Double): Concentration {
        nonnegative(value, "浓度"); positive(molarMass, "摩尔质量"); positive(density, "密度"); positive(equivalents, "当量因子")
        val molarity = when (kind) {
            "M" -> value
            "m" -> 1000.0 * density * value / (1000.0 + value * molarMass)
            "N" -> value / equivalents
            "w" -> { require(value < 100.0) { "质量分数必须小于 100%，此模型要求存在溶剂。" }; value / 100.0 * density * 1000.0 / molarMass }
            else -> throw IllegalArgumentException("请选择浓度类型。")
        }
        val solventGrams = 1000.0 * density - molarity * molarMass
        require(solventGrams > 0.0 && solventGrams.isFinite()) { "输入对应的溶剂质量必须大于 0；请检查密度、浓度和摩尔质量。" }
        return Concentration(molarity, 1000.0 * molarity / solventGrams, molarity * equivalents, molarity * molarMass / (1000.0 * density))
    }

    fun acidBase(concentration: Double, kind: String, pK: Double = 4.76, pKw: Double = 14.0): AcidBase {
        nonnegative(concentration, "分析浓度")
        require(concentration <= 100.0) { "分析浓度不能超过 100 mol/L。" }
        require(pKw.isFinite() && pKw in 1.0..30.0) { "pKw 应在 1–30 之间。" }
        val kw = 10.0.pow(-pKw)
        val neutral = sqrt(kw)
        val isAcid = kind.endsWith("acid")
        require(kind in listOf("strong_acid", "strong_base", "weak_acid", "weak_base")) { "请选择酸碱类型。" }
        val weak = kind.startsWith("weak")
        val dominant = if (!weak) {
            (concentration + hypot(concentration, 2.0 * neutral)) / 2.0
        } else {
            require(pK.isFinite() && pK in -10.0..30.0) { "pKa / pKb 应在 −10–30 之间。" }
            val k = 10.0.pow(-pK)
            var low = neutral
            var high = concentration + neutral
            repeat(180) {
                val mid = low + (high - low) / 2.0
                // Charge balance: x = Kw/x + C*K/(K+x). This retains water autoionization.
                if (mid - kw / mid - concentration * k / (k + mid) > 0.0) high = mid else low = mid
            }
            (low + high) / 2.0
        }
        val hydrogen = if (isAcid) dominant else kw / dominant
        val hydroxide = kw / hydrogen
        // Compute the species fraction from mass action, avoiding cancellation near neutral pH.
        val alpha = if (weak && concentration > 0.0) 10.0.pow(-pK) / (10.0.pow(-pK) + dominant) else null
        return AcidBase(-log10(hydrogen), -log10(hydroxide), hydrogen, hydroxide, alpha?.coerceIn(0.0, 1.0))
    }

    fun gas(unknown: String, pressure: Double, volume: Double, amount: Double, temperature: Double): Gas {
        if (unknown != "p") positive(pressure, "压力")
        if (unknown != "v") positive(volume, "体积")
        if (unknown != "n") positive(amount, "物质的量")
        if (unknown != "t") positive(temperature, "绝对温度")
        val result = when (unknown) {
            "p" -> Gas(amount * R * temperature / volume, volume, amount, temperature)
            "v" -> Gas(pressure, amount * R * temperature / pressure, amount, temperature)
            "n" -> Gas(pressure, volume, pressure * volume / (R * temperature), temperature)
            "t" -> Gas(pressure, volume, amount, pressure * volume / (amount * R))
            else -> throw IllegalArgumentException("请选择待求变量。")
        }
        listOf(result.pressure, result.volume, result.amount, result.temperature).forEach { positive(it, "计算结果") }
        return result
    }

    fun nernst(e0: Double, electrons: Double, temperature: Double, logQ: Double): Double {
        finite(e0, "标准电势"); positive(electrons, "电子数"); positive(temperature, "绝对温度"); finite(logQ, "ln Q")
        require(electrons == floor(electrons)) { "转移电子数必须是正整数。" }
        return finite(e0 - R * temperature / (electrons * F) * logQ, "电极电势")
    }

    fun kcToKp(kc: Double, temperature: Double, deltaN: Double): Double {
        positive(kc, "Kc"); positive(temperature, "绝对温度"); finite(deltaN, "气体计量数变化")
        // c° = 1 mol/L, p° = 1 atm; hence R carries L atm mol^-1 K^-1 here.
        return positive(exp(ln(kc) + deltaN * ln(R * 1000.0 / ATM * temperature)), "Kp")
    }

    fun solubility(ksp: Double, a: Int, b: Int, commonA: Double = 0.0, commonB: Double = 0.0): Solubility {
        positive(ksp, "Ksp"); nonnegative(commonA, "已有 A 离子浓度"); nonnegative(commonB, "已有 B 离子浓度")
        require(a in 1..6 && b in 1..6) { "离子计量数必须为 1–6 的整数。" }
        val target = ln(ksp)
        fun logProduct(s: Double): Double = a * ln(commonA + a * s) + b * ln(commonB + b * s)
        val initial = logProduct(0.0)
        require(initial <= target + 1e-12) { "初始离子积已经超过 Ksp，会发生沉淀；此工具只计算未饱和溶液的继续溶解。" }
        if (abs(initial - target) < 1e-12) return Solubility(0.0, commonA, commonB)
        val pureLog = (target - a * ln(a.toDouble()) - b * ln(b.toDouble())) / (a + b)
        var lowLog = ln(Double.MIN_VALUE)
        var highLog = pureLog
        repeat(220) {
            val mid = (lowLog + highLog) / 2.0
            if (logProduct(exp(mid)) > target) highLog = mid else lowLog = mid
        }
        val s = exp((lowLog + highLog) / 2.0)
        positive(s, "溶解度")
        return Solubility(s, commonA + a * s, commonB + b * s)
    }

    fun complex(kf: Double, metal: Double, ligand: Double): Complex {
        positive(kf, "稳定常数"); nonnegative(metal, "金属总浓度"); nonnegative(ligand, "配体总浓度")
        require(kf in 1e-200..1e200 && metal <= 100.0 && ligand <= 100.0) { "此模型支持 Kf 为 1e−200–1e200，总浓度不超过 100 mol/L。" }
        if (metal == 0.0 || ligand == 0.0) return Complex(0.0, metal, ligand)
        // Rationalized quadratic roots retain both very weak binding and tiny free-ion concentrations.
        val sum = metal + ligand
        val discriminant = hypot(kf * (metal - ligand), sqrt(2.0 * kf * sum + 1.0))
        val bound = 2.0 * kf * metal * ligand / (1.0 + kf * sum + discriminant)
        val difference = abs(metal - ligand)
        val smaller = min(metal, ligand)
        val b = difference + 1.0 / kf
        val freeSmaller = (2.0 * smaller / kf) / (b + hypot(b, 2.0 * sqrt(smaller / kf)))
        return if (metal <= ligand) Complex(bound, freeSmaller, difference + freeSmaller)
        else Complex(bound, difference + freeSmaller, freeSmaller)
    }

    fun activationEnergy(k1: Double, k2: Double, t1: Double, t2: Double): Double {
        positive(k1, "k₁"); positive(k2, "k₂"); positive(t1, "T₁"); positive(t2, "T₂")
        require(abs(t1 - t2) > 1e-9) { "求活化能时，两次温度必须不同。" }
        return finite(R * (ln(k2) - ln(k1)) / (1.0 / t1 - 1.0 / t2), "活化能")
    }

    fun rateAtTemperature(k1: Double, activationEnergy: Double, t1: Double, t2: Double): Double {
        positive(k1, "k₁"); finite(activationEnergy, "活化能"); positive(t1, "T₁"); positive(t2, "T₂")
        return positive(exp(ln(k1) - activationEnergy / R * (1.0 / t2 - 1.0 / t1)), "k₂")
    }

    data class UnitDef(val id: String, val label: String, val dimension: String, val factor: Double, val offset: Double = 0.0)
    data class ConversionInputRequirement(
        val key: String,
        val label: String,
        val defaultValue: String,
        val unitKey: String? = null,
        val unitLabel: String = "",
        val choices: List<Choice> = emptyList(),
        val defaultUnit: String = "",
        val unitFactors: Map<String, Double> = emptyMap(),
        val resultLabel: String = label,
        val resultUnit: String = "",
        val hint: String = "",
    )
    val units = listOf(
        UnitDef("g", "g · 克", "mass", 1.0), UnitDef("mg", "mg · 毫克", "mass", 1e-3),
        UnitDef("ug", "μg · 微克", "mass", 1e-6), UnitDef("kg", "kg · 千克", "mass", 1000.0),
        UnitDef("L", "L · 升", "volume", 1.0), UnitDef("mL", "mL · 毫升", "volume", 1e-3),
        UnitDef("uL", "μL · 微升", "volume", 1e-6), UnitDef("m3", "m³ · 立方米", "volume", 1000.0),
        UnitDef("mol", "mol · 摩尔", "amount", 1.0), UnitDef("mmol", "mmol · 毫摩尔", "amount", 1e-3),
        UnitDef("umol", "μmol · 微摩尔", "amount", 1e-6), UnitDef("kmol", "kmol · 千摩尔", "amount", 1000.0),
        UnitDef("particles", "个 · 粒子数", "amount", 1.0 / NA),
        UnitDef("M", "mol/L · 摩尔浓度", "molar_concentration", 1.0),
        UnitDef("mM", "mmol/L · 毫摩尔浓度", "molar_concentration", 1e-3),
        UnitDef("uM", "μmol/L · 微摩尔浓度", "molar_concentration", 1e-6),
        UnitDef("g/L", "g/L · 质量浓度", "mass_concentration", 1.0),
        UnitDef("mg/L", "mg/L · 质量浓度", "mass_concentration", 1e-3),
        UnitDef("Pa", "Pa · 帕", "pressure", 1.0), UnitDef("kPa", "kPa · 千帕", "pressure", 1000.0),
        UnitDef("atm", "atm · 标准大气压", "pressure", ATM), UnitDef("bar", "bar · 巴", "pressure", 100000.0),
        UnitDef("mmHg", "mmHg · 毫米汞柱", "pressure", ATM / 760.0),
        UnitDef("K", "K · 开尔文", "temperature", 1.0), UnitDef("C", "°C · 摄氏度", "temperature", 1.0, 273.15),
    )

    fun hasDifferentDimensions(from: String, to: String): Boolean {
        val source = units.firstOrNull { it.id == from } ?: return false
        val target = units.firstOrNull { it.id == to } ?: return false
        return source.dimension != target.dimension
    }

    private val densityFactors = mapOf("g/mL" to 1000.0, "kg/L" to 1000.0, "g/L" to 1.0, "kg/m3" to 1.0)
    private val molarMassFactors = mapOf("g/mol" to 1.0, "kg/mol" to 1000.0, "mg/mol" to 1e-3)
    private val molarVolumeFactors = mapOf("L/mol" to 1.0, "m3/mol" to 1000.0, "mL/mol" to 1e-3)
    private val volumeFactors = mapOf("L" to 1.0, "mL" to 1e-3, "uL" to 1e-6, "m3" to 1000.0)
    private val massFactors = mapOf("g" to 1.0, "kg" to 1000.0, "mg" to 1e-3, "ug" to 1e-6)

    private fun requirement(
        key: String,
        label: String,
        defaultValue: String,
        unitKey: String,
        unitLabel: String,
        choices: List<Choice>,
        defaultUnit: String,
        unitFactors: Map<String, Double>,
        resultLabel: String,
        resultUnit: String,
        hint: String = "",
    ) = ConversionInputRequirement(key, label, defaultValue, unitKey, unitLabel, choices, defaultUnit, unitFactors, resultLabel, resultUnit, hint)

    private fun choices(vararg values: Pair<String, String>) = values.map { Choice(it.first, it.second) }

    fun conversionRequirements(from: String, to: String): List<ConversionInputRequirement> {
        val source = units.firstOrNull { it.id == from } ?: return emptyList()
        val target = units.firstOrNull { it.id == to } ?: return emptyList()
        if (source.dimension == target.dimension) return emptyList()
        val dimensions = setOf(source.dimension, target.dimension)
        val molarMass = requirement(
            "molarMass", "跨量纲所需摩尔质量", "58.44", "molarMassUnit", "摩尔质量单位",
            choices("g/mol" to "g·mol⁻¹", "kg/mol" to "kg·mol⁻¹", "mg/mol" to "mg·mol⁻¹"),
            "g/mol", molarMassFactors, "所需摩尔质量", "g/mol",
            "跨质量与物质的量、质量浓度与摩尔浓度换算时使用",
        )
        val density = requirement(
            "density", "跨量纲所需密度", "", "densityUnit", "密度单位",
            choices("g/mL" to "g·mL⁻¹", "kg/L" to "kg·L⁻¹", "g/L" to "g·L⁻¹", "kg/m3" to "kg·m⁻³"),
            "g/mL", densityFactors, "所需密度", "g/L",
            "质量与体积之间换算需要样品密度",
        )
        val molarVolume = requirement(
            "molarVolume", "跨量纲所需摩尔体积", "", "molarVolumeUnit", "摩尔体积单位",
            choices("L/mol" to "L·mol⁻¹", "m3/mol" to "m³·mol⁻¹", "mL/mol" to "mL·mol⁻¹"),
            "L/mol", molarVolumeFactors, "所需摩尔体积", "L/mol",
            "气体体积与物质的量之间换算需要指定温度和压力下的摩尔体积",
        )
        val solutionVolume = requirement(
            "solutionVolume", "换算所需溶液体积", "", "solutionVolumeUnit", "溶液体积单位",
            choices("L" to "L", "mL" to "mL", "uL" to "μL", "m3" to "m³"),
            "L", volumeFactors, "所需溶液体积", "L",
            "总量与浓度之间换算需要指定溶液体积",
        )
        val referenceAmount = requirement(
            "referenceAmount", "换算所需物质的量", "", "referenceAmountUnit", "物质的量单位",
            choices("mol" to "mol", "mmol" to "mmol", "umol" to "μmol", "kmol" to "kmol"),
            "mol", mapOf("mol" to 1.0, "mmol" to 1e-3, "umol" to 1e-6, "kmol" to 1e3), "所需物质的量", "mol",
            "体积与浓度之间换算需要指定参考物质的量",
        )
        val referenceMass = requirement(
            "referenceMass", "换算所需质量", "", "referenceMassUnit", "质量单位",
            choices("g" to "g", "kg" to "kg", "mg" to "mg", "ug" to "μg"),
            "g", massFactors, "所需质量", "g",
            "体积与质量浓度之间换算需要指定参考质量",
        )
        return when {
            dimensions == setOf("mass", "amount") || dimensions == setOf("molar_concentration", "mass_concentration") -> listOf(molarMass)
            dimensions == setOf("mass", "volume") -> listOf(density)
            dimensions == setOf("amount", "volume") -> listOf(molarVolume)
            dimensions == setOf("amount", "molar_concentration") || dimensions == setOf("mass", "mass_concentration") -> listOf(solutionVolume)
            dimensions == setOf("volume", "molar_concentration") -> listOf(referenceAmount)
            dimensions == setOf("volume", "mass_concentration") -> listOf(referenceMass)
            dimensions == setOf("mass", "molar_concentration") || dimensions == setOf("amount", "mass_concentration") -> listOf(molarMass, solutionVolume)
            else -> emptyList()
        }
    }

    fun convert(
        value: Double,
        from: String,
        to: String,
        molarMass: Double? = null,
        density: Double? = null,
        molarVolume: Double? = null,
        solutionVolume: Double? = null,
        referenceAmount: Double? = null,
        referenceMass: Double? = null,
    ): Double {
        finite(value, "数值")
        val source = units.firstOrNull { it.id == from } ?: throw IllegalArgumentException("请选择起始单位。")
        val target = units.firstOrNull { it.id == to } ?: throw IllegalArgumentException("请选择目标单位。")
        var base = value * source.factor + source.offset
        require(base >= 0.0) { "质量、体积、浓度、绝对压力和物质的量不能为负；温度不能低于绝对零度。" }
        if (source.dimension != target.dimension) {
            val dimensions = setOf(source.dimension, target.dimension)
            fun required(parameter: Double?, label: String) = positive(parameter ?: throw IllegalArgumentException("请输入有效的$label。"), label)
            base = when {
                dimensions == setOf("mass", "amount") -> {
                    val mw = required(molarMass, "摩尔质量")
                    if (source.dimension == "amount") base * mw else base / mw
                }
                dimensions == setOf("molar_concentration", "mass_concentration") -> {
                    val mw = required(molarMass, "摩尔质量")
                    if (source.dimension == "molar_concentration") base * mw else base / mw
                }
                dimensions == setOf("mass", "volume") -> {
                    val rho = required(density, "密度")
                    if (source.dimension == "volume") base * rho else base / rho
                }
                dimensions == setOf("amount", "volume") -> {
                    val vm = required(molarVolume, "摩尔体积")
                    if (source.dimension == "amount") base * vm else base / vm
                }
                dimensions == setOf("amount", "molar_concentration") -> {
                    val volume = required(solutionVolume, "溶液体积")
                    if (source.dimension == "amount") base / volume else base * volume
                }
                dimensions == setOf("mass", "mass_concentration") -> {
                    val volume = required(solutionVolume, "溶液体积")
                    if (source.dimension == "mass") base / volume else base * volume
                }
                dimensions == setOf("volume", "molar_concentration") -> {
                    val amount = required(referenceAmount, "参考物质的量")
                    if (source.dimension == "volume") amount / base else amount / base
                }
                dimensions == setOf("volume", "mass_concentration") -> {
                    val mass = required(referenceMass, "参考质量")
                    if (source.dimension == "volume") mass / base else mass / base
                }
                dimensions == setOf("mass", "molar_concentration") -> {
                    val mw = required(molarMass, "摩尔质量")
                    val volume = required(solutionVolume, "溶液体积")
                    if (source.dimension == "mass") base / mw / volume else base * volume * mw
                }
                dimensions == setOf("amount", "mass_concentration") -> {
                    val mw = required(molarMass, "摩尔质量")
                    val volume = required(solutionVolume, "溶液体积")
                    if (source.dimension == "amount") base * mw / volume else base * volume / mw
                }
                else -> throw IllegalArgumentException("这两个量纲暂不支持直接换算，请先选择兼容单位。")
            }
        }
        return finite((base - target.offset) / target.factor, "换算结果")
    }

    internal fun finite(value: Double, label: String): Double {
        require(value.isFinite()) { "$label 超出可计算范围，请检查输入与单位。" }
        return value
    }
    internal fun positive(value: Double, label: String): Double {
        finite(value, label); require(value > 0.0) { "$label 必须大于 0。" }; return value
    }
    internal fun nonnegative(value: Double, label: String): Double {
        finite(value, label); require(value >= 0.0) { "$label 不能为负。" }; return value
    }
}
