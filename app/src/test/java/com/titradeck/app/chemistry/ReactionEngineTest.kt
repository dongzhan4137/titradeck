package com.titradeck.app.chemistry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class ReactionEngineTest {
    @Test fun unicodeBracketsAndHydratesHaveCorrectAtomCounts() {
        assertEquals(mapOf("C" to 6, "H" to 12, "O" to 6), FormulaParser.parse("C₆H₁₂O₆").composition)
        assertEquals(mapOf("Fe" to 2, "S" to 3, "O" to 12), FormulaParser.parse("Fe₂(SO₄)₃").composition)
        val hydrate = FormulaParser.parse("CuSO₄·5H₂O")
        assertEquals(mapOf("Cu" to 1, "S" to 1, "O" to 9, "H" to 10), hydrate.composition)
        assertEquals(249.68, hydrate.molarMass, 0.1)
        assertEquals(mapOf("K" to 4, "O" to 14, "N" to 2, "S" to 4), FormulaParser.parse("K4[ON(SO3)2]2").composition)
    }

    @Test fun ionicNotationKeepsAmmoniumSubscriptAndRecognizesCharges() {
        assertEquals(2, FormulaParser.parse("Fe2+").charge)
        assertEquals(mapOf("Fe" to 1), FormulaParser.parse("Fe2+").composition)
        assertEquals(-2, FormulaParser.parse("SO₄²⁻").charge)
        assertEquals(mapOf("N" to 1, "H" to 4), FormulaParser.parse("NH4+").composition)
        assertEquals(1, FormulaParser.parse("NH4+").charge)
        assertEquals(-3, FormulaParser.parse("[Fe(CN)6]3-").charge)
        assertEquals("aq", FormulaParser.parse("Fe^2+(aq)").state)
    }

    @Test fun unicodeSubscriptsRemainAtomCountsBeforePlainChargeSigns() {
        val oxygen = FormulaParser.parse("O₂+")
        assertEquals(mapOf("O" to 2), oxygen.composition)
        assertEquals(1, oxygen.charge)
        assertEquals("O₂⁺", displayChemicalFormula(oxygen.normalizedFormula))

        val grouped = FormulaParser.parse("[NH₄]₂+")
        assertEquals(mapOf("N" to 2, "H" to 8), grouped.composition)
        assertEquals(1, grouped.charge)
    }

    @Test fun invalidFormulasAreRejectedWithoutPartialResults() {
        listOf("", "Xx2", "H0", "H02", "Mg(OH", "Mg(OH]", "()", "CuSO4··5H2O", "H999999999999", "h2O", "Na^0+").forEach { input ->
            assertThrows("Input: $input", IllegalArgumentException::class.java) { FormulaParser.parse(input) }
        }
    }

    @Test fun balancesCombustionAndComplexSaltsExactly() {
        assertEquals(listOf(2, 7, 4, 6), EquationBalancer.balance("C2H6 + O2 -> CO2 + H2O").coefficients)
        assertEquals(listOf(1, 6, 2, 3), EquationBalancer.balance("Fe2(SO4)3 + KOH -> Fe(OH)3 + K2SO4").coefficients)
        assertEquals(listOf(4, 11, 2, 8), EquationBalancer.balance("FeS2 + O2 -> Fe2O3 + SO2").coefficients)
        assertEquals(listOf(1, 1, 5), EquationBalancer.balance("CuSO4·5H2O -> CuSO4 + H2O").coefficients)
    }

    @Test fun conservesChargeForRedoxAndElectrons() {
        val redox = EquationBalancer.balance("MnO4^- + Fe^2+ + H^+ -> Mn^2+ + Fe^3+ + H2O")
        assertEquals(listOf(1, 5, 8, 1, 5, 4), redox.coefficients)
        assertEquals(listOf(1, 1, 1), EquationBalancer.balance("Fe^2+ -> Fe^3+ + e^-").coefficients)
        assertEquals(listOf(1, 1, 1, 1), EquationBalancer.balance("NH4+ + OH- -> NH3 + H2O").coefficients)
    }

    @Test fun balancesPlainPositiveChargesWithCaseInsensitivePhaseSuffixes() {
        val equation = EquationBalancer.balance("Fe2+ (AQ) -> Fe3+(aQ) + e^-")
        assertEquals(listOf(1, 1, 1), equation.coefficients)
        assertEquals("Fe²⁺(aq) → Fe³⁺(aq) + e⁻", displayChemicalEquation(equation))
        assertEquals(listOf(2, 1, 2, 1), EquationBalancer.balance("H2 + (S)O2 -> H2O + S").coefficients)
    }

    @Test fun impossibleAndAmbiguousSystemsAreNotInvented() {
        assertThrows(IllegalArgumentException::class.java) { EquationBalancer.balance("H2 -> O2") }
        assertThrows(IllegalArgumentException::class.java) { EquationBalancer.balance("C + O2 -> CO + CO2") }
        assertThrows(IllegalArgumentException::class.java) { EquationBalancer.balance("H2 + O2 -> H2O + NaCl") }
    }

    @Test fun boundedInputsRejectExcessiveCountsDepthAndCoefficients() {
        assertThrows(IllegalArgumentException::class.java) { FormulaParser.parse("(".repeat(14) + "H" + ")".repeat(14)) }
        assertThrows(IllegalArgumentException::class.java) { FormulaParser.parse("H10000000H") }
        assertThrows(IllegalArgumentException::class.java) { EquationBalancer.balance("H2 + O2 -> H2000002O") }
        assertThrows(IllegalArgumentException::class.java) { EquationBalancer.balance("0H2 + O2 -> H2O") }
        assertThrows(IllegalArgumentException::class.java) { EquationBalancer.balance("9999999999H2 + O2 -> H2O") }
    }

    @Test fun identifiesLimitingReactantAndExcessFromMoles() {
        val equation = EquationBalancer.balance("H2 + O2 -> H2O")
        val result = StoichiometrySolver.solve(equation, mapOf(0 to 3.0, 1 to 2.0))
        assertEquals(1.5, result.extent, 1e-12)
        assertEquals(listOf(0), result.limitingIndices)
        assertTrue(result.allReactantsKnown)
        assertEquals(0.5, 2.0 - equation.coefficients[1] * result.extent, 1e-12)
    }

    @Test fun distinguishesAssumedExcessAndProductReverseCalculation() {
        val equation = EquationBalancer.balance("H2 + O2 -> H2O")
        val partial = StoichiometrySolver.solve(equation, mapOf(0 to 2.0))
        assertTrue(!partial.allReactantsKnown)
        val reverse = StoichiometrySolver.solve(equation, mapOf(2 to 4.0))
        assertEquals(2.0, reverse.extent, 1e-12)
        assertTrue(reverse.fromProduct)
        assertThrows(IllegalArgumentException::class.java) { StoichiometrySolver.solve(equation, mapOf(0 to 2.0, 2 to 1.0)) }
    }

    @Test fun stoichiometryConvertsGasAtExplicitConditionsAndComputesYield() {
        val tool = ReactionTools.tools.single { it.id == "stoichiometry" }
        val result = tool.calculate(mapOf("equation" to "H2(g) + O2(g) -> H2O(l)", "amounts" to "H2: 2 mol\nO2: 1 mol", "temperature" to "273.15", "pressure" to "101325", "actualProduct" to "H2O", "actualAmount" to "1 mol"))
        assertEquals(50.0, result.rows.single { it.label.contains("产率") }.value.toDouble(), 1e-9)
        assertEquals(44.82794, result.rows.single { it.label.startsWith("H₂(g)") && it.unit == "L" }.value.toDouble(), 0.001)
        assertTrue(result.steps.any { it.contains("6.02214076") })
    }

    @Test fun stoichiometryUsesVolumesOnlyForGasOrUnspecifiedPhases() {
        val tool = ReactionTools.tools.single { it.id == "stoichiometry" }
        val inputs = mapOf("equation" to "H2(g) + O2 -> H2O(l)", "amounts" to "H2: 2 L\nO2: 1 L")
        val result = tool.calculate(inputs)
        assertEquals(2.0, result.rows.single { it.label.startsWith("H₂(g)") && it.unit == "L" }.value.toDouble(), 1e-9)
        assertEquals(1.0, result.rows.single { it.label.startsWith("O₂") && it.unit == "L" }.value.toDouble(), 1e-9)

        val invalidInput = assertThrows(IllegalArgumentException::class.java) {
            tool.calculate(inputs + ("equation" to "H2(g) + O2(l) -> H2O(l)"))
        }
        assertTrue(invalidInput.message.orEmpty().contains("相态"))
        val invalidYield = assertThrows(IllegalArgumentException::class.java) {
            tool.calculate(inputs + mapOf("actualProduct" to "H2O", "actualAmount" to "1 mL"))
        }
        assertTrue(invalidYield.message.orEmpty().contains("相态"))
    }

    @Test fun chemicalDisplayPreservesCoefficientsAndFormatsCharges() {
        assertEquals("CuSO₄·5H₂O", displayChemicalFormula("CuSO4·5H2O"))
        assertEquals("[Fe(CN)₆]³⁻(aq)", displayChemicalFormula("[Fe(CN)6]^3-(aq)"))
        assertEquals("NH₄⁺", displayChemicalFormula("NH4+"))
        assertEquals("Fe³⁺", displayChemicalFormula("Fe3+"))
        assertEquals("2 H₂ + O₂ → 2 H₂O", displayChemicalEquation(EquationBalancer.balance("H2 + O2 -> H2O")))
    }

    @Test fun savedChemicalResultsAreFormattedWithoutChangingNumbersOrSmiles() {
        val original = CalculationResult("旧结果", "2 H2 + O2 → 2 H2O", rows = listOf(ResultRow("H2O · 产率", "50", "%")), steps = listOf("H2O: 2 mol"))
        val shown = displaySavedResult("stoichiometry", mapOf("equation" to "H2 + O2 -> H2O"), original)
        assertEquals("2 H₂ + O₂ → 2 H₂O", shown.value)
        assertEquals("H₂O · 产率", shown.rows.single().label)
        assertEquals("50", shown.rows.single().value)
        assertEquals("H2O: 2 mol", original.steps.single())
        val organic = CalculationResult("结构", "结果", rows = listOf(ResultRow("乙醇 · 分子式", "C2H6O"), ResultRow("乙醇 · SMILES", "C2H6O")))
        val rows = displaySavedResult("organic_lookup", emptyMap(), organic).rows
        assertEquals("C₂H₆O", rows[0].value)
        assertEquals("C2H6O", rows[1].value)
    }

    @Test fun empiricalAndMolecularFormulasFitGlucoseAnalysis() {
        val empirical = EmpiricalFormulaSolver.solve(mapOf("C" to 40.00, "H" to 6.71, "O" to 53.29))
        assertEquals("CH2O", empirical.formula)
        assertEquals("C6H12O6", EmpiricalFormulaSolver.molecularFormula(empirical, 180.156).first)
        assertThrows(IllegalArgumentException::class.java) { EmpiricalFormulaSolver.molecularFormula(empirical, 47.0) }
        assertThrows(IllegalArgumentException::class.java) { EmpiricalFormulaSolver.solve(mapOf("C" to 10.0, "H" to 10.0)) }
    }

    @Test fun unrepresentableDerivedGasVolumesRaiseValidationError() {
        val tool = ReactionTools.tools.single { it.id == "stoichiometry" }
        val failure = assertThrows(IllegalArgumentException::class.java) {
            tool.calculate(mapOf("equation" to "H2(g) + O2(g) -> H2O(l)", "amounts" to "H2: 1e100 mol", "temperature" to "1e100", "pressure" to "1e-200"))
        }
        assertTrue(failure.message.orEmpty().contains("数值范围"))
    }
}
