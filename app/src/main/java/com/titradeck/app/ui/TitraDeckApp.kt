@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.titradeck.app.ui

import android.content.ClipData
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.titradeck.app.chemistry.*
import com.titradeck.app.data.*
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ToolCatalog {
    val tools by lazy { ReactionTools.tools + PhysicalTools.tools + StructureTools.tools }
}

@Composable
fun TitraDeckApp() {
    val context = LocalContext.current
    val store = remember { AppStore(context) }
    DisposableEffect(store) { onDispose { store.close() } }
    val scope = rememberCoroutineScope()
    var backgroundHex by remember { mutableStateOf(store.backgroundHex) }
    var themeHex by remember { mutableStateOf(store.themeHex) }
    var appearance by remember { mutableStateOf(store.appearance) }
    var tab by rememberSaveable { mutableStateOf("计算") }
    var returnTab by rememberSaveable { mutableStateOf("计算") }
    val screenStates = rememberSaveableStateHolder()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var inputs by rememberSaveable { mutableStateOf<Map<String, String>>(emptyMap()) }
    var resultJson by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var historyVersion by remember { mutableIntStateOf(0) }
    val tools = ToolCatalog.tools
    val tool = tools.firstOrNull { it.id == selectedId }
    val result = remember(resultJson) { resultJson?.let { runCatching { AppStore.decodeResult(it) }.getOrNull() } }
    fun openTool(next: ToolDefinition, overrides: Map<String, String> = emptyMap(), restored: CalculationResult? = null) {
        returnTab = tab
        inputs = next.fields.associate { it.key to it.defaultValue } + overrides
        selectedId = next.id; resultJson = restored?.let(AppStore::encodeResult); error = null; tab = "计算"
    }
    fun openCompoundLookup() {
        returnTab = tab
        selectedId = "compound_lookup"
        error = null
        tab = "计算"
    }
    fun calculate(current: ToolDefinition, currentInputs: Map<String, String>, save: Boolean) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            try {
                val calculated = withContext(Dispatchers.Default) { current.calculate(currentInputs) }
                if (selectedId == current.id && inputs == currentInputs) resultJson = AppStore.encodeResult(calculated)
                if (save) {
                    withContext(Dispatchers.IO) { store.save(current, currentInputs, calculated) }
                    historyVersion++
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) {
                if (selectedId == current.id) error = failure.message ?: "请检查输入的数值、单位和化学式。"
            } finally { busy = false }
        }
    }
    fun closeTool() { selectedId = null; tab = returnTab; error = null }
    fun selectTab(next: String) {
        if (next != "计算" && selectedId == "compound_lookup") selectedId = null
        tab = next
    }
    BackHandler(enabled = tab == "计算" && selectedId != null) { closeTool() }
    TitraTheme(appearance, backgroundHex, themeHex) {
        BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            val wide = maxWidth >= 840.dp
            Scaffold(
                bottomBar = {
                    if (!wide) NavigationBar(modifier = Modifier.testTag("bottom_navigation"), containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        listOf("计算", "参考", "历史", "设置").forEachIndexed { index, item ->
                            NavigationBarItem(selected = tab == item,
                                modifier = Modifier.weight(1f).testTag("nav_item_$index"),
                                onClick = { selectTab(item) },
                                icon = { NavigationGlyph(index) }, label = { NavigationLabel(index, item) })
                        }
                    }
                },
            ) { insets ->
                Row(Modifier.fillMaxSize().padding(insets)) {
                    if (wide) NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxHeight()) {
                        Spacer(Modifier.height(28.dp))
                        AppBrandIcon(contentDescription = "TitraDeck")
                        Spacer(Modifier.height(24.dp))
                        listOf("计算", "参考", "历史", "设置").forEachIndexed { index, item ->
                            NavigationRailItem(selected = tab == item, onClick = { selectTab(item) },
                                modifier = Modifier.testTag("nav_item_$index"),
                                icon = { NavigationGlyph(index) }, label = { NavigationLabel(index, item) })
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                        Box(Modifier.widthIn(max = if (tab == "参考") 1200.dp else 820.dp).fillMaxSize()) {
                            val screen = if (tab == "计算" && selectedId != null) "tool:$selectedId" else tab
                            AnimatedContent(targetState = screen, label = "页面切换",
                                transitionSpec = {
                                    (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 24 }) togetherWith
                                        (fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 32 })
                                }) { visibleScreen ->
                              screenStates.SaveableStateProvider(visibleScreen) {
                                val visibleTool = if (visibleScreen.startsWith("tool:")) tools.firstOrNull { it.id == visibleScreen.removePrefix("tool:") } else null
                                when {
                                visibleScreen == "tool:compound_lookup" -> CompoundLookupScreen(onBack = { closeTool() })
                                visibleTool != null -> {
                                    CalculatorScreen(visibleTool, inputs, result, error, busy,
                                        onBack = { closeTool() },
                                        onValue = { key, value ->
                                            val changed = inputs + (key to value)
                                            inputs = changed
                                            if (visibleTool.id != "unit_converter") resultJson = null
                                            error = null
                                        }, onCalculate = { calculate(visibleTool, inputs, true) },
                                        onReset = { inputs = visibleTool.fields.associate { it.key to it.defaultValue }; resultJson = null; error = null })
                                }
                                visibleScreen == "计算" -> CalculatorHome(tools, onOpen = { openTool(it) }, onCompoundLookup = { openCompoundLookup() })
                                visibleScreen == "参考" -> ReferenceScreen { constant ->
                                    tools.firstOrNull { it.id == constant.toolId }?.let { next ->
                                        val overrides = constant.inputOverrides + (constant.fieldKey?.let { key -> mapOf(key to constant.value.toString()) } ?: emptyMap())
                                        openTool(next, overrides)
                                    }
                                }
                                visibleScreen == "历史" -> HistoryScreen(store, historyVersion, onRestore = { entry ->
                                    tools.firstOrNull { it.id == entry.toolId }?.let { openTool(it, entry.inputs, displaySavedResult(entry.toolId, entry.inputs, entry.result)) }
                                }, onChanged = { historyVersion++ })
                                else -> SettingsScreen(backgroundHex, appearance, themeHex,
                                    onBackground = { backgroundHex = it; store.backgroundHex = it },
                                    onAppearance = { appearance = it; store.appearance = it },
                                    onThemeColor = { themeHex = it; store.themeHex = it })
                                }
                              }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavigationLabel(index: Int, label: String) {
    Text(label, modifier = Modifier.testTag("nav_label_$index"),
        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center, maxLines = 1)
}

@Composable
fun ScreenHeading(title: String, description: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge)
        Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CalculatorHome(tools: List<ToolDefinition>, onOpen: (ToolDefinition) -> Unit, onCompoundLookup: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    val filtered = tools.filter { (category == null || it.category.name == category) &&
        (query.isBlank() || "${it.title} ${it.subtitle} ${it.formula}".contains(query, true)) }
    val showCompoundLookup = (category == null || category == ToolCategory.STRUCTURE.name) &&
        (query.isBlank() || "化合物查询 PubChem 有机 无机 结构 名称 分子式 SMILES CID".contains(query, true))
    LazyColumn(modifier = Modifier.testTag("home_list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AppBrandIcon(size = 36.dp)
            Spacer(Modifier.width(10.dp))
            Text("TITRADECK", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, letterSpacing = 2.sp)
            Spacer(Modifier.weight(1f)); Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape) {
                Text("核心计算离线可用", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
            }
        } }
        item { ScreenHeading("化学，有据可算", "从一个化学式开始，让每一步都有迹可循。") }
        item { Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("你的随身化学工作台", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("${tools.size} 项计算工具  ·  118 种元素\n公式、单位与计算过程，一起保留。", color = MaterialTheme.colorScheme.onPrimaryContainer)
                FilledTonalButton(onClick = { tools.firstOrNull { it.id == "molar_mass" }?.let(onOpen) }) { Text("解析一个化学式  →") }
            }
        } }
        item { SearchField(query, { query = it }, "搜索计算工具、公式或关键词") }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = category == null, onClick = { category = null }, label = { Text("全部") })
            ToolCategory.entries.forEach { type -> FilterChip(selected = category == type.name, onClick = { category = type.name }, label = { Text(type.label) }) }
        } }
        if (filtered.isEmpty() && !showCompoundLookup) item { EmptyState("未找到匹配工具", "试试“浓度”“气体”或“pH”。") }
        ToolCategory.entries.forEach { type ->
            val group = filtered.filter { it.category == type }
            if (group.isNotEmpty() || (type == ToolCategory.STRUCTURE && showCompoundLookup)) {
                item { Text(type.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                items(group, key = { it.id }) { tool -> ToolCard(tool.title, tool.subtitle, tool.category) { onOpen(tool) } }
                if (type == ToolCategory.STRUCTURE && showCompoundLookup) item(key = "compound_lookup") {
                    ToolCard("化合物查询", "在线查询 PubChem 名称、性质与分子结构", type, onCompoundLookup)
                }
            }
        }
        item { Text("模型有边界，结果有依据。各工具均注明适用条件。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 12.dp)) }
    }
}

@Composable
private fun ToolCard(title: String, subtitle: String, category: ToolCategory, onOpen: () -> Unit) {
    Card(onClick = onOpen, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) { Text(when (category) { ToolCategory.CORE -> "Σ"; ToolCategory.SOLUTION -> "M"; ToolCategory.EQUILIBRIUM -> "⇌"; ToolCategory.PHYSICAL -> "R"; ToolCategory.STRUCTURE -> "C" }, fontSize = 23.sp, color = MaterialTheme.colorScheme.primary) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text("›", fontSize = 26.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large, trailingIcon = { if (value.isNotEmpty()) TextButton(onClick = { onChange("") }) { Text("清除") } })
}

@Composable
private fun CalculatorScreen(tool: ToolDefinition, values: Map<String, String>, result: CalculationResult?, error: String?, busy: Boolean,
    onBack: () -> Unit, onValue: (String, String) -> Unit, onCalculate: () -> Unit, onReset: () -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = rememberCoroutineScope()
    val resultIndex = 4 + tool.fields.size
    LaunchedEffect(result) { if (result != null) listState.animateScrollToItem(resultIndex) }
    Column(Modifier.imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹  返回") }
            Text(tool.category.label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { onReset(); scope.launch { listState.animateScrollToItem(0) } }, enabled = !busy) { Text("示例") }
        }
        LazyColumn(modifier = Modifier.testTag("calculator_list"), state = listState, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { ScreenHeading(tool.title, tool.subtitle) }
            item { FormulaPanel(tool.formula) }
            item { Text("01  填写已知条件", style = MaterialTheme.typography.titleMedium) }
            items(tool.fields, key = { it.key }) { field ->
                InputField(field, values[field.key] ?: "", busy, tool.id, field.key == values["unknown"],
                    enabled = tool.id != "unit_converter" || field.key != "molarMass" ||
                        PhysicalMath.hasDifferentDimensions(values["fromUnit"].orEmpty(), values["toUnit"].orEmpty()),
                    onPreset = { entry ->
                        (entry.inputOverrides + (field.key to entry.value.toString())).forEach { (key, value) -> onValue(key, value) }
                    }) { onValue(field.key, it) }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (error != null) Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                        Text(error, Modifier.padding(16.dp).fillMaxWidth(), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                    Button(onClick = { focus.clearFocus(); keyboard?.hide(); onCalculate() }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
                        if (busy) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(12.dp)) }
                        Text(if (busy) "正在计算…" else "开始计算", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(if (tool.id == "unit_converter") "修改输入后，点击“开始计算”更新结果并保存到历史。" else "计算记录仅保存在本机，可在“历史”中恢复输入。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                AnimatedVisibility(visible = result != null, enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 12 }, exit = fadeOut(tween(120))) {
                    if (result != null) ResultPanel(result, tool.formula, tool.id)
                }
                if (result == null) EmptyState("等待计算", "确认数值和单位，结果与推导过程将在这里显示。")
            }
        }
    }
}

@Composable
private fun InputField(field: FieldSpec, value: String, busy: Boolean, toolId: String, calculated: Boolean, enabled: Boolean,
    onPreset: (ReferenceEntry) -> Unit, onChange: (String) -> Unit) {
    if (field.choices.isNotEmpty()) {
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (!busy) expanded = !expanded }) {
            val selectedLabel = field.choices.firstOrNull { it.value == value }?.label ?: value
            Box(Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)) {
                OutlinedTextField(value = selectedLabel, onValueChange = {}, readOnly = true,
                    modifier = Modifier.fillMaxWidth(), label = { LatexText(field.label) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Transparent,
                        unfocusedTextColor = Color.Transparent,
                        disabledTextColor = Color.Transparent,
                    ),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, shape = MaterialTheme.shapes.medium,
                    supportingText = if (field.hint.isNotBlank()) {{ LatexText(field.hint) }} else null)
                LatexText(selectedLabel, modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 52.dp),
                    style = MaterialTheme.typography.bodyLarge)
            }
            ExposedDropdownMenu(expanded, { expanded = false }) {
                field.choices.forEach { choice -> DropdownMenuItem(text = { LatexText(choice.label) }, onClick = { onChange(choice.value); expanded = false }) }
            }
        }
    } else {
        var constantMenu by remember { mutableStateOf(false) }
        val constants = ReferenceData.entries.filter { it.toolId == toolId && it.fieldKey == field.key }
        Column {
            OutlinedTextField(if (calculated) "" else value, { onChange(it.take(4000)) }, label = { LatexText(field.label) }, enabled = !busy && !calculated && enabled,
                placeholder = if (calculated) {{ Text("由其他已知量求解") }} else null,
                modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, minLines = if (field.multiline) 3 else 1,
                maxLines = if (field.multiline) 8 else 1, singleLine = !field.multiline,
                supportingText = if (field.hint.isNotBlank()) {{ LatexText(field.hint) }} else null,
                trailingIcon = if (constants.isNotEmpty()) {{ TextButton(onClick = { constantMenu = true }) { Text("引用") } }} else null)
            if (constantMenu) AlertDialog(onDismissRequest = { constantMenu = false }, title = { Text("引用参考值") },
                text = { Column(Modifier.verticalScroll(rememberScrollState())) { constants.forEach { entry -> TextButton(onClick = { onPreset(entry); constantMenu = false }) { LatexText("${entry.name} · ${entry.value} ${entry.unit}") } } } },
                confirmButton = { TextButton(onClick = { constantMenu = false }) { Text("取消") } })
        }
    }
}

@Composable
fun FormulaPanel(formula: String) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember(formula) { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("使用的公式", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = {
                    scope.launch {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("TitraDeck 公式", mathTextForClipboard(formula))))
                        copied = true
                    }
                }) { Text(if (copied) "已复制" else "复制公式") }
            }
            SelectionContainer { LatexText(formula, style = MaterialTheme.typography.bodyLarge, forceFormula = true) }
        }
    }
}

@Composable
private fun ResultPanel(result: CalculationResult, formula: String, toolId: String) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember(result) { mutableStateOf(false) }
    var valueCopied by remember(result) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("02  结果与过程", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                val output = buildString {
                    appendLine(result.headline); appendLine("${result.value} ${result.unit}"); appendLine(formula)
                    result.rows.forEach { appendLine("${it.label}: ${it.value} ${it.unit}") }
                    result.steps.forEach(::appendLine); result.notes.forEach(::appendLine)
                }
                scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("TitraDeck 计算结果", mathTextForClipboard(output)))); copied = true }
            }) { Text(if (copied) "已复制" else "复制结果") }
        }
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    LatexText(result.headline, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = {
                        val output = listOf(result.value, result.unit).filter { it.isNotBlank() }.joinToString(" ")
                        scope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("TitraDeck 结果", mathTextForClipboard(output))))
                            valueCopied = true
                        }
                    }) { Text(if (valueCopied) "已复制" else "复制当前结果") }
                }
                SelectionContainer {
                    if (toolId == "balance") EquationText(result.value, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.titleLarge)
                    else LatexText(result.value, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.headlineMedium)
                }
                if (result.unit.isNotBlank()) LatexText(result.unit, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        if (result.molecule != null) MoleculeView(result.molecule)
        if (result.rows.isNotEmpty()) ResultTable(result.rows)
        if (result.steps.isNotEmpty()) {
            Text("计算过程 · 单位追踪", style = MaterialTheme.typography.titleMedium)
            SelectionContainer { Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                result.steps.forEachIndexed { index, step -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape, modifier = Modifier.size(26.dp)) {
                        Box(contentAlignment = Alignment.Center) { Text("${index + 1}", style = MaterialTheme.typography.labelMedium) }
                    }
                    LatexText(step, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                } }
            } }
        }
        if (result.notes.isNotEmpty()) Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = .6f), shape = MaterialTheme.shapes.medium) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("适用条件与说明", style = MaterialTheme.typography.titleSmall)
                result.notes.forEach { LatexText("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
internal fun ResultTable(rows: List<ResultRow>) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            rows.forEachIndexed { index, row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LatexText(
                        row.label,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SelectionContainer(Modifier.weight(1.2f)) {
                        LatexText(
                            "${row.value} ${row.unit}".trim(),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        )
                    }
                }
                if (index < rows.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                }
            }
        }
    }
}

@Composable
fun EmptyState(title: String, description: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HistoryScreen(store: AppStore, version: Int, onRestore: (HistoryEntry) -> Unit, onChanged: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var limit by rememberSaveable { mutableIntStateOf(100) }
    var history by remember { mutableStateOf<List<HistoryEntry>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var clearConfirmation by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(version, query, limit) { history = withContext(Dispatchers.IO) { store.history(limit, query) }; loaded = true }
    if (!loaded) return
    LazyColumn(modifier = Modifier.testTag("history_list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { ScreenHeading("计算历史", "输入、结果和原始公式，全部保存在本机。") }
        item { SearchField(query, { query = it; limit = 100 }, "搜索工具名称或输入内容") }
        if (history.isNotEmpty()) item { TextButton(onClick = { clearConfirmation = true }) { Text("清空全部历史", color = MaterialTheme.colorScheme.error) } }
        if (history.isEmpty()) item {
            if (query.isBlank()) EmptyState("这里还没有记录", "完成一次计算后，即可在这里恢复输入。")
            else EmptyState("没有匹配的记录", "请更换关键词或清除搜索。")
        }
        items(history, key = { it.id }) { entry ->
            Card(onClick = { onRestore(entry) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(entry.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(entry.timestamp)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (entry.toolId == "balance") EquationText(displaySavedResult(entry.toolId, entry.inputs, entry.result).value, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth())
                    else LatexText("${displaySavedResult(entry.toolId, entry.inputs, entry.result).value} ${entry.result.unit}", color = MaterialTheme.colorScheme.primary)
                    LatexText(entry.formula, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, forceFormula = true)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("恢复输入并查看过程  →", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                        TextButton(onClick = {
                            scope.launch { withContext(Dispatchers.IO) { store.delete(entry.id) }; onChanged() }
                        }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
        if (history.size >= limit) item { OutlinedButton(onClick = { limit += 100 }, modifier = Modifier.fillMaxWidth()) { Text("加载更早记录") } }
    }
    if (clearConfirmation) AlertDialog(onDismissRequest = { clearConfirmation = false }, title = { Text("清空全部历史？") },
        text = { Text("将删除本机保存的所有计算记录，此操作无法撤销。") },
        confirmButton = { TextButton(onClick = { scope.launch { withContext(Dispatchers.IO) { store.clearHistory() }; onChanged(); clearConfirmation = false } }) { Text("清空", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { clearConfirmation = false }) { Text("取消") } })
}

