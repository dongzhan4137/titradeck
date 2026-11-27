package com.titradeck.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.titradeck.app.chemistry.InputKind

private enum class KeyboardPage { SYMBOLS, ELEMENTS, LETTERS }

private val elementSymbols = listOf(
    "H", "He", "Li", "Be", "B", "C", "N", "O", "F", "Ne",
    "Na", "Mg", "Al", "Si", "P", "S", "Cl", "Ar", "K", "Ca",
    "Sc", "Ti", "V", "Cr", "Mn", "Fe", "Co", "Ni", "Cu", "Zn",
    "Ga", "Ge", "As", "Se", "Br", "Kr", "Rb", "Sr", "Y", "Zr",
    "Nb", "Mo", "Tc", "Ru", "Rh", "Pd", "Ag", "Cd", "In", "Sn",
    "Sb", "Te", "I", "Xe", "Cs", "Ba", "La", "Ce", "Pr", "Nd",
    "Pm", "Sm", "Eu", "Gd", "Tb", "Dy", "Ho", "Er", "Tm", "Yb",
    "Lu", "Hf", "Ta", "W", "Re", "Os", "Ir", "Pt", "Au", "Hg",
    "Tl", "Pb", "Bi", "Po", "At", "Rn", "Fr", "Ra", "Ac", "Th",
    "Pa", "U", "Np", "Pu", "Am", "Cm", "Bk", "Cf", "Es", "Fm",
    "Md", "No", "Lr", "Rf", "Db", "Sg", "Bh", "Hs", "Mt", "Ds",
    "Rg", "Cn", "Nh", "Fl", "Mc", "Lv", "Ts", "Og",
)

private val qwertyRows = listOf(
    listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
    listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
    listOf("Z", "X", "C", "V", "B", "N", "M"),
)

@Composable
internal fun ChemistryKeyboard(
    kind: InputKind,
    onInsert: (String) -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    onNewline: () -> Unit,
    onDone: () -> Unit,
    onSystemKeyboard: () -> Unit,
) {
    var page by remember(kind) {
        mutableStateOf(if (kind == InputKind.CHEMICAL_FORMULA) KeyboardPage.ELEMENTS else KeyboardPage.SYMBOLS)
    }
    var shift by remember { mutableStateOf(true) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                PageButton("数字/符号", page == KeyboardPage.SYMBOLS, Modifier.weight(1f)) { page = KeyboardPage.SYMBOLS }
                PageButton("元素", page == KeyboardPage.ELEMENTS, Modifier.weight(1f)) { page = KeyboardPage.ELEMENTS }
                PageButton("字母", page == KeyboardPage.LETTERS, Modifier.weight(1f)) { page = KeyboardPage.LETTERS }
                PageButton("系统键盘", false, Modifier.weight(1f), onSystemKeyboard)
            }
            Box(
                modifier = Modifier.fillMaxWidth().height(220.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                when (page) {
                    KeyboardPage.SYMBOLS -> SymbolsPage(kind, onInsert)
                    KeyboardPage.ELEMENTS -> ElementsPage(onInsert)
                    KeyboardPage.LETTERS -> LettersPage(shift, { shift = !shift }, onInsert)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                KeyButton("空格", Modifier.weight(2f).height(44.dp)) { onInsert(" ") }
                KeyButton("删除", Modifier.weight(1f).height(44.dp)) { onDelete() }
                KeyButton("换行", Modifier.weight(1f).height(44.dp)) { onNewline() }
                KeyButton("清空", Modifier.weight(1f).height(44.dp)) { onClear() }
                KeyButton("完成", Modifier.weight(1f).height(44.dp)) { onDone() }
            }
        }
    }
}

@Composable
private fun SymbolsPage(
    kind: InputKind,
    onInsert: (String) -> Unit,
) {
    val rows = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        listOf("+", "−", "×", "÷", "=", "≈", "^", "·", "(", ")"),
        listOf("⁰", "¹", "²", "³", "⁴", "⁵", "⁶", "⁷", "⁸", "⁹"),
        listOf("₀", "₁", "₂", "₃", "₄", "₅", "₆", "₇", "₈", "₉"),
        if (kind == InputKind.CHEMICAL_EQUATION) {
            listOf("(aq)", "(s)", "(g)", "(l)", "→", "⟶", "⇌", "⟷", "⟺", ":")
        } else {
            listOf("(aq)", "(s)", "(g)", "(l)", "→", "⟶", "⟷", "⟺", "[", "]")
        },
    )
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        rows.forEach { row -> KeyRow(row) { onInsert(it) } }
    }
}

@Composable
private fun ElementsPage(onInsert: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        elementSymbols.chunked(8).forEach { row -> KeyRow(row) { onInsert(it) } }
    }
}

@Composable
private fun LettersPage(shift: Boolean, onShift: () -> Unit, onInsert: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        qwertyRows.forEachIndexed { index, row ->
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                if (index == 2) {
                    KeyButton(if (shift) "⇧" else "⇩", Modifier.weight(1.4f).fillMaxHeight()) { onShift() }
                }
                row.forEach { key ->
                    KeyButton(if (shift) key else key.lowercase(), Modifier.weight(1f).fillMaxHeight()) { onInsert(if (shift) key else key.lowercase()) }
                }
            }
        }
    }
}

@Composable
private fun KeyRow(keys: List<String>, onKey: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        keys.forEach { key -> KeyButton(key, Modifier.weight(1f)) { onKey(key) } }
    }
}

@Composable
private fun PageButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) { Text(label, fontSize = 12.sp, maxLines = 1) }
}

@Composable
private fun KeyButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 40.dp),
        contentPadding = PaddingValues(horizontal = 1.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
    ) { Text(label, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 1) }
}
