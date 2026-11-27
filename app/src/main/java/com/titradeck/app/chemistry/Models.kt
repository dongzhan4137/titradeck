package com.titradeck.app.chemistry

enum class ToolCategory(val label: String) {
    CORE("化学基础"), SOLUTION("溶液与酸碱"), EQUILIBRIUM("平衡与电化学"),
    PHYSICAL("物理化学"), STRUCTURE("结构与有机")
}

data class Choice(val value: String, val label: String)
enum class InputKind { NUMBER, CHEMICAL_FORMULA, CHEMICAL_EQUATION }
data class FieldSpec(
    val key: String,
    val label: String,
    val defaultValue: String = "",
    val hint: String = "",
    val choices: List<Choice> = emptyList(),
    val multiline: Boolean = false,
    val unitKey: String? = null,
    val inputKind: InputKind = InputKind.NUMBER,
)
data class ResultRow(val label: String, val value: String, val unit: String = "")
data class MoleculeAtom(val symbol: String, val x: Float, val y: Float, val z: Float = 0f,
    val lonePairs: Int = 0, val formalCharge: Int = 0)
data class MoleculeBond(val from: Int, val to: Int, val order: Int = 1)
data class MoleculeModel(val atoms: List<MoleculeAtom>, val bonds: List<MoleculeBond>, val caption: String)
data class CalculationResult(
    val headline: String,
    val value: String,
    val unit: String = "",
    val rows: List<ResultRow> = emptyList(),
    val steps: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
    val molecule: MoleculeModel? = null,
)
data class ToolDefinition(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: ToolCategory,
    val formula: String,
    val fields: List<FieldSpec>,
    val calculate: (Map<String, String>) -> CalculationResult,
)
