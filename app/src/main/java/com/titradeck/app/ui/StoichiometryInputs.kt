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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.titradeck.app.chemistry.FieldSpec
import com.titradeck.app.chemistry.StoichiometryUnits
import com.titradeck.app.chemistry.maxStoichiometryKnownAmounts
import com.titradeck.app.chemistry.stoichiometryAmountSeparator

private const val defaultUnit = "g"
private val quantityParts = Regex("^(.*?)\\s*(${StoichiometryUnits.pattern})$")

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
    onChange: (String) -> Unit,
) {
    if (field.key == "amounts") {
        KnownAmountInputs(field.label, value, enabled, onChange)
    } else {
        var blankUnit by rememberSaveable { mutableStateOf(defaultUnit) }
        val quantity = decodeQuantity(value, blankUnit)
        QuantityInput(field.label, quantity, enabled, Modifier.fillMaxWidth()) { updated ->
            blankUnit = updated.unit
            val encoded = if (updated.number.isBlank()) "" else updated.encode()
            if (encoded != value) onChange(encoded)
        }
    }
}

@Composable
private fun KnownAmountInputs(label: String, value: String, enabled: Boolean, onChange: (String) -> Unit) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = amount.formula,
                    onValueChange = { formula ->
                        if (formula.none { it in "\r\n:：=" }) {
                            updateAmount(index, amount.copy(formula = formula.take(4000)))
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    label = { Text("化学式", maxLines = 1) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                )
                QuantityInput("数值", amount.quantity, enabled, Modifier.weight(2f)) { quantity ->
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
    label: String,
    quantity: Quantity,
    enabled: Boolean,
    modifier: Modifier,
    onChange: (Quantity) -> Unit,
) {
    OutlinedTextField(
        value = quantity.number,
        onValueChange = { number ->
            if (number.all { it in "0123456789.+-eE" }) {
                onChange(quantity.copy(number = number.take(4000)))
            }
        },
        modifier = modifier,
        enabled = enabled,
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
