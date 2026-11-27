package com.titradeck.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.titradeck.app.chemistry.MoleculeModel
import com.titradeck.app.chemistry.ResultRow
import com.titradeck.app.chemistry.displayChemicalFormula
import com.titradeck.app.data.PubChem
import com.titradeck.app.data.PubChemCompound
import com.titradeck.app.data.PubChemSearchResult
import com.titradeck.app.data.saveCompoundImageToGallery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext

private sealed interface CompoundGraphic {
    data class Depiction(val image: ImageBitmap, val png: ByteArray) : CompoundGraphic
    data class Model(val molecule: MoleculeModel) : CompoundGraphic
}

@Composable
fun CompoundLookupScreen(onBack: () -> Unit) {
    val repository = remember { PubChem() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current.applicationContext
    val listState = rememberLazyListState()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var query by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf("name") }
    var page by remember { mutableStateOf<PubChemSearchResult?>(null) }
    var selected by remember { mutableStateOf<PubChemCompound?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pendingExportCid by rememberSaveable { mutableStateOf<Int?>(null) }
    var pendingExportHeavyAtoms by rememberSaveable { mutableIntStateOf(0) }
    var pendingPng by remember { mutableStateOf<ByteArray?>(null) }
    var exporting by remember { mutableStateOf(false) }
    fun saveToGallery(cid: Int, heavyAtomCount: Int, png: ByteArray?) {
        exporting = true
        scope.launch {
            val message = try {
                withContext(Dispatchers.IO) {
                    saveCompoundImageToGallery(context, cid, png ?: repository.depiction(cid, heavyAtomCount))
                }
                "图片已保存到 TitraDeck 相册"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                "无法保存到相册，请重试"
            } finally {
                exporting = false
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val storagePermission = Manifest.permission.WRITE_EXTERNAL_STORAGE
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val cid = pendingExportCid
        val heavyAtomCount = pendingExportHeavyAtoms
        val png = pendingPng
        pendingExportCid = null
        pendingExportHeavyAtoms = 0
        pendingPng = null
        if (cid != null && granted) {
            saveToGallery(cid, heavyAtomCount, png)
        } else if (cid != null) {
            Toast.makeText(context, "保存到相册需要存储权限", Toast.LENGTH_SHORT).show()
        }
    }
    val currentPage = page
    val results = currentPage?.compounds.orEmpty()
    val showCandidates = currentPage != null &&
        (currentPage.pageIndex > 0 || currentPage.hasNext || results.size > 1)

    fun requestResults(newSearch: Boolean, request: () -> PubChemSearchResult) {
        if (busy) return
        busy = true
        error = null
        if (newSearch) {
            page = null
            selected = null
        }
        scope.launch {
            try {
                val response = runInterruptible(Dispatchers.IO) { request() }
                page = response
                selected = response.compounds.singleOrNull()
                    ?.takeIf { response.pageIndex == 0 && !response.hasNext }
                if (response.compounds.isEmpty()) error = "未找到匹配的化合物。"
                if (!newSearch) listState.requestScrollToItem(4)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.message ?: "查询失败，请检查网络连接。"
            } finally {
                busy = false
            }
        }
    }

    fun submitSearch() {
        if (busy || query.isBlank()) return
        val submittedQuery = query
        val submittedMode = mode
        focus.clearFocus()
        keyboard?.hide()
        requestResults(newSearch = true) { repository.search(submittedQuery, submittedMode) }
    }

    Column(Modifier.imePadding()) {
        TextButton(onClick = onBack) { Text("‹  返回") }
        LazyColumn(state = listState, modifier = Modifier.testTag("compound_lookup_list"), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { ScreenHeading("化合物查询", "在线查询 PubChem 的有机与无机化合物。") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("name" to "名称", "formula" to "分子式", "smiles" to "SMILES", "cid" to "CID").forEach { (value, label) ->
                        FilterChip(selected = mode == value, enabled = !busy, onClick = { mode = value }, label = { Text(label) })
                    }
                }
            }
            item {
                OutlinedTextField(query, { query = it.take(200) }, enabled = !busy, label = { Text("查询内容") },
                    placeholder = { Text(when (mode) {
                        "formula" -> "例如 C2H6O"
                        "cid" -> "例如 702"
                        "smiles" -> "例如 CCO"
                        else -> "例如 乙醇 或 ethanol"
                    }) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submitSearch() }))
            }
            item {
                Button(enabled = !busy && query.isNotBlank(), modifier = Modifier.fillMaxWidth(), onClick = ::submitSearch) {
                    Text(if (busy) "正在查询…" else "查询 PubChem")
                }
            }
            if (showCandidates) {
                item(key = "compound_page") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        TextButton(enabled = !busy && currentPage.pageIndex > 0, onClick = {
                            requestResults(newSearch = false) { repository.loadPage(currentPage, currentPage.pageIndex - 1) }
                        }) { Text("上一页") }
                        Text("第 ${currentPage.pageIndex + 1} 页")
                        TextButton(enabled = !busy && currentPage.hasNext, onClick = {
                            requestResults(newSearch = false) { repository.loadPage(currentPage, currentPage.pageIndex + 1) }
                        }) { Text("下一页") }
                    }
                }
            }
            error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            currentPage?.translatedName?.let { name ->
                item { Text("按英文译名 $name 查询 · MyMemory", style = MaterialTheme.typography.bodySmall) }
            }
            selected?.let { compound ->
                item(key = "details-${compound.cid}") {
                    ResultTable(
                        listOf(
                            ResultRow("IUPAC 名称", compound.name),
                            ResultRow("分子式", displayChemicalFormula(compound.formula)),
                            ResultRow("分子量", compound.molecularWeight),
                            ResultRow("SMILES", compound.smiles),
                            ResultRow("PubChem CID", compound.cid.toString()),
                        ),
                    )
                }
                item(key = "structure-${compound.cid}") {
                    CompoundStructure(compound, repository, canSave = !exporting && pendingExportCid == null) { png ->
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                            context.checkSelfPermission(storagePermission) != PackageManager.PERMISSION_GRANTED
                        ) {
                            pendingExportCid = compound.cid
                            pendingExportHeavyAtoms = compound.heavyAtomCount
                            pendingPng = png
                            permissionLauncher.launch(storagePermission)
                        } else {
                            saveToGallery(compound.cid, compound.heavyAtomCount, png)
                        }
                    }
                }
            }
            if (showCandidates) {
                item { Text("请选择化合物", style = MaterialTheme.typography.titleMedium) }
                items(results, key = { it.cid }) { compound ->
                    Card(enabled = !busy, onClick = {
                        selected = compound
                        val detailIndex = 5 + (if (error != null) 1 else 0) +
                            (if (currentPage?.translatedName != null) 1 else 0)
                        listState.requestScrollToItem(detailIndex)
                    }, colors = CardDefaults.cardColors(
                        containerColor = if (selected?.cid == compound.cid) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(compound.name, style = MaterialTheme.typography.titleSmall)
                            LatexText("${displayChemicalFormula(compound.formula)} · CID ${compound.cid}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompoundStructure(
    compound: PubChemCompound,
    repository: PubChem,
    canSave: Boolean,
    onSave: (ByteArray) -> Unit,
) {
    var showModel by rememberSaveable { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val modelSelected = showModel
    val graphic by key(modelSelected, retry) {
        produceState<Result<CompoundGraphic>?>(null) {
            value = try {
                Result.success(withContext(Dispatchers.IO) {
                    if (modelSelected) {
                        val molecule = requireNotNull(repository.structure(compound.cid))
                        CompoundGraphic.Model(molecule)
                    } else {
                        val data = repository.depiction(compound.cid, compound.heavyAtomCount)
                        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(data, 0, data.size))
                        CompoundGraphic.Depiction(bitmap.asImageBitmap(), data)
                    }
                })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Result.failure(failure)
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !showModel, onClick = { showModel = false }, label = { Text("二维结构") })
            FilterChip(selected = showModel, onClick = { showModel = true },
                modifier = Modifier.testTag("show_compound_model"), label = { Text("分子模型") })
        }
        val result = graphic
        when (val drawing = result?.getOrNull()) {
            is CompoundGraphic.Depiction -> {
                CompoundDepictionView(compound, drawing.image, canSave, onSave = { onSave(drawing.png) })
            }
            is CompoundGraphic.Model -> {
                Box(Modifier.testTag("compound_structure_${compound.cid}")) {
                    MoleculeView(drawing.molecule, allowFullscreen = true)
                }
            }
            null -> if (result == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                Text(if (showModel) "暂时无法显示分子模型。" else "暂时无法显示二维结构。", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { retry++ }) { Text("重试获取结构") }
            }
        }
    }
}
