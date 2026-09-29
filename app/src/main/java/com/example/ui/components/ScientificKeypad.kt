package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.CalculatorUiState

@Composable
fun ScientificKeypad(
    uiState: CalculatorUiState,
    onToggleRadMode: () -> Unit,
    onToggleInverse: () -> Unit,
    onFunction: (String) -> Unit,
    onConstant: (String) -> Unit,
    onFactorial: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Row 1: DEG/RAD, INV, sin / sin⁻¹, cos / cos⁻¹, tan / tan⁻¹
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CalculatorButton(
                text = if (uiState.isRadMode) "RAD" else "DEG",
                type = ButtonType.SCIENTIFIC,
                fontSize = 14.sp,
                isActive = uiState.isRadMode,
                modifier = Modifier.weight(1f),
                onClick = onToggleRadMode
            )
            CalculatorButton(
                text = "INV",
                type = ButtonType.SCIENTIFIC,
                fontSize = 14.sp,
                isActive = uiState.isInvMode,
                modifier = Modifier.weight(1f),
                onClick = onToggleInverse
            )
            CalculatorButton(
                text = if (uiState.isInvMode) "sin⁻¹" else "sin",
                type = ButtonType.SCIENTIFIC,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f),
                onClick = { onFunction(if (uiState.isInvMode) "asin" else "sin") }
            )
            CalculatorButton(
                text = if (uiState.isInvMode) "cos⁻¹" else "cos",
                type = ButtonType.SCIENTIFIC,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f),
                onClick = { onFunction(if (uiState.isInvMode) "acos" else "cos") }
            )
            CalculatorButton(
                text = if (uiState.isInvMode) "tan⁻¹" else "tan",
                type = ButtonType.SCIENTIFIC,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f),
                onClick = { onFunction(if (uiState.isInvMode) "atan" else "tan") }
            )
        }

        // Row 2: ln / eˣ, log / 10ˣ, 1/x, e, π
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CalculatorButton(
                text = if (uiState.isInvMode) "eˣ" else "ln",
                type = ButtonType.SCIENTIFIC,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f),
                onClick = {
                    if (uiState.isInvMode) {
                        onConstant("e")
                        onFunction("powerY")
                    } else {
                        onFunction("ln")
                    }
                }
            )
            CalculatorButton(
                text = if (uiState.isInvMode) "10ˣ" else "log",
                type = ButtonType.SCIENTIFIC,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f),
                onClick = {
                    if (uiState.isInvMode) {
                        onConstant("10")
                        onFunction("powerY")
                    } else {
                        onFunction("log")
                    }
                }
            )
            CalculatorButton(
                text = "1/x",
                type = ButtonType.SCIENTIFIC,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f),
                onClick = { onFunction("reciprocal") }
            )
            CalculatorButton(
                text = "e",
                type = ButtonType.SCIENTIFIC,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f),
                onClick = { onConstant("e") }
            )
            CalculatorButton(
                text = "π",
                type = ButtonType.SCIENTIFIC,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f),
                onClick = { onConstant("π") }
            )
        }

        // Row 3: x², xʸ, √, ∛, x!
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CalculatorButton(
                text = "x²",
                type = ButtonType.SCIENTIFIC,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f),
                onClick = { onFunction("power2") }
            )
            CalculatorButton(
                text = "xʸ",
                type = ButtonType.SCIENTIFIC,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f),
                onClick = { onFunction("powerY") }
            )
            CalculatorButton(
                text = "√",
                type = ButtonType.SCIENTIFIC,
                fontSize = 17.sp,
                modifier = Modifier.weight(1f),
                onClick = { onFunction("sqrt") }
            )
            CalculatorButton(
                text = "∛",
                type = ButtonType.SCIENTIFIC,
                fontSize = 17.sp,
                modifier = Modifier.weight(1f),
                onClick = { onFunction("cbrt") }
            )
            CalculatorButton(
                text = "x!",
                type = ButtonType.SCIENTIFIC,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f),
                onClick = onFactorial
            )
        }
    }
}
