package com.titradeck.app

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.titradeck.app.chemistry.CalculationResult
import com.titradeck.app.chemistry.PhysicalTools
import com.titradeck.app.data.AppStore
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class WorkflowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun resetAppearance() {
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("appearance", 0).edit().clear().commit()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
    }

    @Test fun formulaCalculationPersistsAndRestoresInputs() {
        compose.onNodeWithText("解析一个化学式  →").performClick()
        compose.onNodeWithText("化学式", substring = false).performTextReplacement("C6H12O6")
        compose.onNodeWithText("开始计算").performScrollTo().performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("180.156", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("历史", substring = false).performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("恢复输入并查看过程  →").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("恢复输入并查看过程  →")[0].performClick()
        compose.onNodeWithTag("calculator_list").performScrollToNode(hasText("化学式"))
        compose.onNodeWithText("化学式", substring = false).assertTextContains("C6H12O6")
        compose.onNodeWithText("化学式", substring = false).performTextReplacement("Xx2")
        compose.onNodeWithText("开始计算").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("未知元素", substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun switchingTabsPreservesAnUnsubmittedCalculation() {
        compose.onNodeWithText("解析一个化学式  →").performClick()
        compose.onNodeWithText("化学式", substring = false).performTextReplacement("NaCl")
        compose.onNodeWithText("参考", substring = false).performClick()
        compose.onNodeWithText("计算", substring = false).performClick()
        compose.onNodeWithText("化学式", substring = false).assertTextContains("NaCl")
        compose.onNodeWithText("‹  返回", substring = false).performClick()
        compose.onNodeWithTag("home_list").assertExists()
    }

    @Test fun elementDetailsAndAppearanceModeWork() {
        compose.onNodeWithText("参考", substring = false).performClick()
        compose.onNodeWithText("搜索符号、名称或原子序数").performTextInput("Fe")
        compose.onNodeWithText("铁 · Iron").performClick()
        compose.onNodeWithText("原子序数", substring = false).assertExists()
        compose.onNodeWithText("关闭", substring = false).performClick()
        compose.onNodeWithText("设置", substring = false).performClick()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasText("外观模式"))
        compose.onNodeWithText("深色", substring = false).performClick()
        compose.onNodeWithText("计算", substring = false).performClick()
        compose.onNodeWithText("化学，有据可算").assertExists()
    }

    @Test fun periodicTableMarksElementsWithoutStableIsotopes() {
        compose.onNodeWithText("参考", substring = false).performClick()
        compose.onNodeWithTag("reference_list").performScrollToNode(hasText("* 表示没有稳定同位素 · 类金属按非金属颜色显示"))
        compose.onNodeWithText("* 表示没有稳定同位素 · 类金属按非金属颜色显示").assertExists()
        val search = compose.onNodeWithText("搜索符号、名称或原子序数")
        search.performScrollTo().performTextReplacement("83")
        compose.onNodeWithTag("radioactive_marker_Bi", useUnmergedTree = true).assertExists()
        search.performTextReplacement("92")
        compose.onNodeWithTag("radioactive_marker_U", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("铀 · Uranium").performClick()
        compose.onAllNodesWithTag("radioactive_marker_U", useUnmergedTree = true).onFirst().assertExists()
        compose.onNodeWithText("关闭", substring = false).performClick()
        search.performTextReplacement("26")
        compose.onNodeWithTag("radioactive_marker_Fe", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun converterEnablesMolarMassOnlyForDifferentDimensions() {
        compose.onNodeWithTag("home_list").performScrollToNode(hasText("化学单位换算"))
        compose.onNodeWithText("化学单位换算", substring = false).performClick()
        val list = compose.onNodeWithTag("calculator_list")
        list.performScrollToNode(hasText("摩尔质量 / g·mol⁻¹", substring = false))
        val molarMass = compose.onNodeWithText("摩尔质量 / g·mol⁻¹", substring = false)
        molarMass.assertIsNotEnabled()
        list.performScrollToNode(hasText("目标单位", substring = false))
        compose.onNodeWithText("目标单位", substring = false).performClick()
        compose.onNodeWithText("g · 克", substring = false).performClick()
        list.performScrollToNode(hasText("摩尔质量 / g·mol⁻¹", substring = false))
        molarMass.assertIsEnabled().performTextReplacement("18")
        list.performScrollToNode(hasText("目标单位", substring = false))
        compose.onNodeWithText("目标单位", substring = false).performClick()
        compose.onNodeWithText("mmol · 毫摩尔", substring = false).performClick()
        list.performScrollToNode(hasText("摩尔质量 / g·mol⁻¹", substring = false))
        molarMass.assertIsNotEnabled().assertTextContains("18")
    }

    @Test fun converterUpdatesAndSavesOnlyAfterCalculation() {
        compose.onNodeWithTag("home_list").performScrollToNode(hasText("化学单位换算"))
        compose.onNodeWithText("化学单位换算", substring = false).performClick()
        val list = compose.onNodeWithTag("calculator_list")
        list.performScrollToNode(hasText("目标单位", substring = false))
        compose.onNodeWithText("目标单位", substring = false).performClick()
        compose.onNodeWithText("mmol · 毫摩尔", substring = false).performClick()
        compose.onNodeWithText("换算结果", substring = false).assertDoesNotExist()
        compose.onNodeWithText("开始计算").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("1000", substring = false).fetchSemanticsNodes().isNotEmpty() }
        list.performScrollToNode(hasText("目标单位", substring = false))
        compose.onNodeWithText("目标单位", substring = false).performClick()
        compose.onNodeWithText("g · 克", substring = false).performClick()
        list.performScrollToNode(hasText("换算结果", substring = false))
        compose.onNodeWithText("1000", substring = false).assertExists()
        compose.onNodeWithText("58.44", substring = false).assertDoesNotExist()
        compose.onNodeWithText("开始计算").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("58.44", substring = false).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("历史", substring = false).performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("58.44 g", substring = false).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("恢复输入并查看过程  →")[0].performClick()
        list.performScrollToNode(hasText("目标单位", substring = false))
        compose.onNodeWithText("g · 克", substring = false).assertExists()
    }

    @Test fun formulaAndResultButtonsCopyUnicodeText() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        fun clipboardText() = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()

        compose.onNodeWithTag("home_list").performScrollToNode(hasText("化学方程式配平"))
        compose.onNodeWithText("化学方程式配平", substring = false).performClick()
        compose.onNodeWithText("复制公式").performScrollTo().performClick()
        compose.waitUntil(10000) { clipboardText() == "Aν = 0；反应前后各元素原子数与总电荷相等" }
        compose.onNodeWithText("反应方程式", substring = false).performScrollTo().performTextReplacement("H2 + O2 -> H2O")
        compose.onNodeWithText("开始计算").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("复制当前结果").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("复制当前结果").performScrollTo().performClick()
        compose.waitUntil(10000) { clipboardText() == "2 H₂ + O₂ → 2 H₂O" }
        compose.onNodeWithText("复制结果").performScrollTo().performClick()
        compose.waitUntil(10000) { clipboardText()?.contains("2 H₂ + O₂ → 2 H₂O") == true }
    }

    @Test fun historyDeletesOnlySelectedRecord() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = AppStore(context)
        val prefix = "delete-test-${UUID.randomUUID()}"
        val tool = PhysicalTools.tools.single { it.id == "unit_converter" }
        val inputs = tool.fields.associate { it.key to it.defaultValue }
        val firstTitle = "$prefix-first"
        val secondTitle = "$prefix-second"
        store.save(tool.copy(title = firstTitle), inputs, CalculationResult("结果", "1"))
        store.save(tool.copy(title = secondTitle), inputs, CalculationResult("结果", "2"))
        val entries = store.history(query = prefix)
        try {
            compose.onNodeWithText("历史", substring = false).performClick()
            compose.onNodeWithText("搜索工具名称或输入内容").performTextInput(prefix)
            compose.waitUntil(10000) { compose.onAllNodesWithText(secondTitle).fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodesWithText("删除")[0].performClick()
            compose.waitUntil(10000) { store.history(query = prefix).size == 1 }
            org.junit.Assert.assertEquals(firstTitle, store.history(query = prefix).single().title)
            compose.waitUntil(10000) { compose.onAllNodesWithText(secondTitle).fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithText(firstTitle).assertExists()
            compose.onNodeWithText("搜索工具名称或输入内容").performTextReplacement("$prefix-missing")
            compose.waitUntil(10000) { compose.onAllNodesWithText("没有匹配的记录").fetchSemanticsNodes().isNotEmpty() }
        } finally {
            entries.forEach { store.delete(it.id) }
            store.close()
        }
    }

    @Test fun returningFromToolRestoresHomeScrollAndTabSwitchKeepsIt() {
        compose.onNodeWithTag("home_list").performScrollToNode(hasText("化合物查询"))
        compose.onNodeWithText("化合物查询", substring = false).performClick()
        compose.onNodeWithText("‹  返回", substring = false).performClick()
        compose.onNodeWithText("化合物查询", substring = false).assertIsDisplayed()
        compose.onNodeWithText("参考", substring = false).performClick()
        compose.onNodeWithText("计算", substring = false).performClick()
        compose.onNodeWithText("化合物查询", substring = false).assertIsDisplayed()
    }

    @Test fun pubChemLookupShowsPropertiesAndStructure() {
        compose.onNodeWithTag("home_list").performScrollToNode(hasText("化合物查询"))
        compose.onNodeWithText("化合物查询", substring = false).performClick()
        compose.onNodeWithText("查询内容").performTextInput("乙醇")
        compose.onNodeWithText("查询 PubChem").performClick()
        compose.waitUntil(60000) { compose.onAllNodesWithText("PubChem CID 702", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("IUPAC 名称  ethanol").assertExists()
        val list = compose.onNodeWithTag("compound_lookup_list")
        list.performScrollToNode(hasTestTag("show_compound_model"))
        compose.waitUntil(60000) { compose.onAllNodesWithTag("compound_depiction_702").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("show_compound_model").performClick()
        compose.waitUntil(60000) { compose.onAllNodesWithTag("compound_structure_702").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun compoundLookupSubmitsFromTheKeyboard() {
        compose.onNodeWithTag("home_list").performScrollToNode(hasText("化合物查询"))
        compose.onNodeWithText("化合物查询", substring = false).performClick()
        compose.onNodeWithText("CID", substring = false).performClick()
        val query = compose.onNodeWithText("查询内容", substring = false)
        query.performTextInput("0")
        query.performImeAction()
        compose.waitUntil(10000) { compose.onAllNodesWithText("CID 应为正整数。").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun chineseInorganicNameShowsDepiction() {
        compose.onNodeWithTag("home_list").performScrollToNode(hasText("化合物查询"))
        compose.onNodeWithText("化合物查询", substring = false).performClick()
        compose.onNodeWithText("查询内容").performTextInput("氯化钠")
        compose.onNodeWithText("查询 PubChem").performClick()
        compose.waitUntil(60000) { compose.onAllNodesWithText("PubChem CID 5234", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("compound_lookup_list").performScrollToNode(hasTestTag("show_compound_model"))
        compose.waitUntil(60000) { compose.onAllNodesWithTag("compound_depiction_5234").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun selectingIsomersUpdatesDetailsAndStructure() {
        compose.onNodeWithTag("home_list").performScrollToNode(hasText("化合物查询"))
        compose.onNodeWithText("化合物查询", substring = false).performClick()
        compose.onNodeWithText("分子式", substring = false).performClick()
        compose.onNodeWithText("查询内容").performTextInput("C2H6O")
        compose.onNodeWithText("查询 PubChem").performClick()
        compose.waitUntil(60000) { compose.onAllNodesWithText("第 1 页").fetchSemanticsNodes().isNotEmpty() }
        val list = compose.onNodeWithTag("compound_lookup_list")
        list.performScrollToNode(hasText("methoxymethane", substring = false))
        compose.onNodeWithText("methoxymethane", substring = false).performClick()
        compose.waitUntil(30000) { compose.onAllNodesWithText("PubChem CID 8254", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("SMILES  COC").assertIsDisplayed()
        compose.waitUntil(60000) { compose.onAllNodesWithTag("compound_depiction_8254").fetchSemanticsNodes().isNotEmpty() }
        list.performScrollToNode(hasText("ethanol", substring = false))
        compose.onNodeWithText("ethanol", substring = false).performClick()
        compose.waitUntil(30000) { compose.onAllNodesWithText("PubChem CID 702", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("SMILES  CCO").assertIsDisplayed()
        compose.onNodeWithTag("compound_depiction_8254").assertDoesNotExist()
        compose.waitUntil(60000) { compose.onAllNodesWithTag("compound_depiction_702").fetchSemanticsNodes().isNotEmpty() }
        list.performScrollToNode(hasText("下一页", substring = false))
        compose.onNodeWithText("下一页").performClick()
        compose.waitUntil(60000) { compose.onAllNodesWithText("第 2 页").fetchSemanticsNodes().isNotEmpty() }
        list.performScrollToNode(hasText("CID 12201690", substring = true))
        compose.onNodeWithText("ethanol", substring = false).assertDoesNotExist()
        list.performScrollToNode(hasText("上一页", substring = false))
        compose.onNodeWithText("上一页").performClick()
        compose.waitUntil(60000) { compose.onAllNodesWithText("第 1 页").fetchSemanticsNodes().isNotEmpty() }
        list.performScrollToNode(hasText("ethanol", substring = false))
        compose.onNodeWithText("ethanol", substring = false).assertIsDisplayed()
    }

    @Test fun referenceSearchSurvivesOpeningCalculator() {
        compose.onNodeWithText("参考", substring = false).performClick()
        compose.onNodeWithText("常数与参考值").performClick()
        compose.onNodeWithText("搜索常数、电势、Ksp 或 pKa").performTextInput("乙酸")
        compose.onNodeWithText("用于计算").performClick()
        compose.onNodeWithText("‹  返回", substring = false).performClick()
        compose.onNodeWithText("搜索常数、电势、Ksp 或 pKa").assertTextContains("乙酸")
        compose.onNodeWithText("用于计算").assertIsDisplayed()
    }

}
