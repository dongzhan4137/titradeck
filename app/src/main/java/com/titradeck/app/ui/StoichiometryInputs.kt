package com.titradeck.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.titradeck.app.chemistry.FieldSpec
import com.titradeck.app.chemistry.InputKind
import com.titradeck.app.chemistry.StoichiometryUnits
import com.titradeck.app.chemistry.maxStoichiometryKnownAmounts
import com.titradeck.app.chemistry.stoichiometryAmountSeparator

private const val defaultUnit = "g"
private val quantityParts = Regex("^(.*?)\\s*(${StoichiometryUnits.pattern})$")
private typealias KeyboardFocusHandler = (String, InputKind, String, Int, Int, (String) -> Unit) -> Unit

private data class Quantity(val number: String = "", val unit: String = defaultUnit) {
    fun encode(): String = "$number $unit".trim()
}

private data class KnownAmount(val formula: String = "", val quantity: Quantity = Quantity()) {
    fun encode(): String = "$formula: ${quantity.encode()}"
}

private fun decodeQuantity(text: String, fallbackUnit: String = defaultUnit): Quantity {
    val trimmed = text.trim()
    val match = quantityParts.matchEntire(trimmed) ?: return Quantity(trimmed, fallbackUnit)
    return Quantity(match.groupValues[1].trimEnd(), match.groupValues[2])
}

private fun decodeKnownAmount(line: String): KnownAmount {
    val parts = line.split(stoichiometryAmountSeparator, limit = 2)
    return KnownAmount(parts[0].trimStart(), decodeQuantity(parts.getOrElse(1) { "" }))
}

@Composable
internal fun StoichiometryQuantityInput(
    field: FieldSpec,
    value: String,
    enabled: Boolean,
    onKeyboardFocus: KeyboardFocusHandler,
    customKeyboardActive: (String) -> Boolean,
    keyboardSelection: (String) -> TextRange?,
    onSelectionChanged: (String, TextRange) -> Unit,
    onChange: (String) -> Unit,
) {
    if (field.key == "amounts") {
        KnownAmountInputs(field.label, value, enabled, onKeyboardFocus, customKeyboardActive, keyboardSelection, onSelectionChanged, onChange)
    } else {
        var blankUnit by rememberSaveable { mutableStateOf(defaultUnit) }
        val quantity = decodeQuantity(value, blankUnit)
        QuantityInput(field.key, field.label, quantity, enabled, Modifier.fillMaxWidth(), onKeyboardFocus, customKeyboardActive, keyboardSelection, onSelectionChanged) { updated ->
            blankUnit = updated.unit
            val encoded = if (updated.number.isBlank()) "" else updated.encode()
            if (encoded != value) onChange(encoded)
        }
    }
}

@Composable
private fun KnownAmountInputs(label: String, value: String, enabled: Boolean, onKeyboardFocus: KeyboardFocusHandler, customKeyboardActive: (String) -> Boolean, keyboardSelection: (String) -> TextRange?, onSelectionChanged: (String, TextRange) -> Unit, onChange: (String) -> Unit) {
    val amounts = remember(value) {
        value.lineSequence().filter(String::isNotBlank).map(::decodeKnownAmount).toList()
            .ifEmpty { listOf(KnownAmount()) }
    }
    fun updateAmounts(updated: List<KnownAmount>) {
        onChange(updated.joinToString("\n", transform = KnownAmount::encode))
    }
    fun updateAmount(index: Int, amount: KnownAmount) {
        updateAmounts(amounts.toMutableList().apply { set(index, amount) })
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        amounts.forEachIndexed { index, amount ->
            val latestAmount by rememberUpdatedState(amount)
            val updateFormula by rememberUpdatedState<(String) -> Unit> { formula ->
                updateAmount(index, latestAmount.copy(formula = formula))
            }
            val formulaId = "amounts:$index:formula"
            val formulaValue = TextFieldValue(amount.formula, keyboardSelection(formulaId) ?: TextRange(amount.formula.length))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = formulaValue,
                    onValueChange = { changed ->
                        if (changed.text.none { it in "\r\n:：=" }) {
                            updateAmount(index, amount.copy(formula = changed.text.take(4000)))
                            onSelectionChanged(formulaId, changed.selection)
                        }
                    },
                    modifier = Modifier.weight(1f).onFocusChanged {
                        if (it.isFocused && enabled) {
                            onKeyboardFocus(formulaId, InputKind.CHEMICAL_FORMULA, amount.formula, formulaValue.selection.start, formulaValue.selection.end) { formula -> updateFormula(formula) }
                        }
                    },
                    enabled = enabled,
                    readOnly = customKeyboardActive(formulaId),
                    label = { Text("化学式", maxLines = 1) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                )
                QuantityInput("amounts:$index:quantity", "数值", amount.quantity, enabled, Modifier.weight(2f), onKeyboardFocus, customKeyboardActive, keyboardSelection, onSelectionChanged) { quantity ->
                    updateAmount(index, amount.copy(quantity = quantity))
                }
                IconButton(
                    onClick = { updateAmounts(amounts.filterIndexed { position, _ -> position != index }) },
                    modifier = Modifier.semantics { contentDescription = "移除第 ${index + 1} 项已知量" },
                    enabled = enabled && (amounts.size > 1 || amount != KnownAmount()),
                ) {
                    Text("×", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        TextButton(
            onClick = { updateAmounts(amounts + KnownAmount()) },
            enabled = enabled && amounts.size < maxStoichiometryKnownAmounts,
        ) {
            Text("添加已知量")
        }
    }
}

@Composable
private fun QuantityInput(
    id: String,
    label: String,
    quantity: Quantity,
    enabled: Boolean,
    modifier: Modifier,
    onKeyboardFocus: KeyboardFocusHandler,
    customKeyboardActive: (String) -> Boolean,
    keyboardSelection: (String) -> TextRange?,
    onSelectionChanged: (String, TextRange) -> Unit,
    onChange: (Quantity) -> Unit,
) {
    val latestQuantity by rememberUpdatedState(quantity)
    val updateNumber by rememberUpdatedState<(String) -> Unit> { number -> onChange(latestQuantity.copy(number = number)) }
    val textValue = TextFieldValue(quantity.number, keyboardSelection(id) ?: TextRange(quantity.number.length))
    OutlinedTextField(
        value = textValue,
        onValueChange = { changed ->
            if (changed.text.all { it in "0123456789.+-eE" }) {
                onChange(quantity.copy(number = changed.text.take(4000)))
                onSelectionChanged(id, changed.selection)
            }
        },
        modifier = modifier.onFocusChanged {
            if (it.isFocused && enabled) {
                onKeyboardFocus(id, InputKind.NUMBER, quantity.number, textValue.selection.start, textValue.selection.end) { number -> updateNumber(number) }
            }
        },
        enabled = enabled,
        readOnly = customKeyboardActive(id),
        label = { Text(label) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
        trailingIcon = {
            UnitDropdown(
                "$label 单位",
                StoichiometryUnits.choices,
                StoichiometryUnits.canonical(quantity.unit),
                enabled,
            ) { unit -> onChange(quantity.copy(unit = unit)) }
        },
    )
}
