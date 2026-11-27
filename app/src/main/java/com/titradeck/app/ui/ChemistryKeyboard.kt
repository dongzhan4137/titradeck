package com.titradeck.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.titradeck.app.chemistry.InputKind

@Composable
internal fun ChemistryKeyboard(
    kind: InputKind,
    onInsert: (String) -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit,
) {
    var letterMode by remember(kind) { mutableStateOf(false) }
    val title = when (kind) {
        InputKind.NUMBER -> "数字键盘"
        InputKind.CHEMICAL_FORMULA -> "化学式键盘"
        InputKind.CHEMICAL_EQUATION -> "方程式键盘"
    }
    val rows = when {
        kind == InputKind.NUMBER -> listOf(
            listOf("7", "8", "9", "−", "⌫"),
            listOf("4", "5", "6", ".", ","),
            listOf("1", "2", "3", "e", "+"),
            listOf("0", "(", ")", "清空", "完成"),
        )
        letterMode -> listOf(
            listOf("A", "B", "C", "D", "E", "F", "G", "H"),
            listOf("I", "J", "K", "L", "M", "N", "O", "P"),
            listOf("Q", "R", "S", "T", "U", "V", "W", "X"),
            listOf("Y", "Z", "元素", "⌫", "清空", "完成"),
        )
        else -> {
            val controls = if (kind == InputKind.CHEMICAL_EQUATION) {
                listOf("·", "^", "+", "−", "→", "⇌", ":", "字母")
            } else {
                listOf("·", "^", "+", "−", "(aq)", "字母", "⌫", "清空")
            }
            val phaseControls = if (kind == InputKind.CHEMICAL_EQUATION) listOf("(aq)", "(g)", "(l)", "(s)", "⌫", "清空", "完成") else listOf("完成")
            listOf(
                listOf("H", "C", "N", "O", "S", "P"),
                listOf("F", "Cl", "Br", "I", "Na", "K"),
                listOf("Mg", "Ca", "Fe", "Cu", "Al", "Si"),
                listOf("0", "1", "2", "3", "4", "5", "6", "7"),
                listOf("8", "9", "₀", "₁", "₂", "₃", "₄", "₅"),
                listOf("₆", "₇", "₈", "₉", "(", ")", "[", "]"),
                controls,
                phaseControls,
            )
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onDone, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("隐藏") }
            }
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { key ->
                        Button(
                            onClick = {
                                when (key) {
                                    "⌫" -> onDelete()
                                    "清空" -> onClear()
                                    "完成" -> onDone()
                                    "字母" -> letterMode = true
                                    "元素" -> letterMode = false
                                    "空格" -> onInsert(" ")
                                    else -> onInsert(key)
                                }
                            },
                            modifier = Modifier.weight(1f).height(42.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp),
                        ) {
                            Text(key, fontSize = 14.sp, textAlign = TextAlign.Center, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
