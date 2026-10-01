package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.TextUnit
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
    @Composable
    fun RowScope.SciBtn(
        text: String,
        fontSize: TextUnit = 15.sp,
        isActive: Boolean = false,
        onClick: () -> Unit
    ) {
        CalculatorButton(
            text = text,
            type = ButtonType.SCIENTIFIC,
            fontSize = fontSize,
            testTag = "btn_sci_$text",
            isActive = isActive,
            hapticEnabled = uiState.hapticEnabled,
            soundEnabled = uiState.soundEnabled,
            modifier = Modifier.weight(1f),
            onClick = onClick
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Row 1: DEG/RAD, INV, sin / sin⁻¹, cos / cos⁻¹, tan / tan⁻¹
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SciBtn(
                text = if (uiState.isRadMode) "RAD" else "DEG",
                fontSize = 13.sp,
                isActive = uiState.isRadMode,
                onClick = onToggleRadMode
            )
            SciBtn(
                text = "INV",
                fontSize = 13.sp,
                isActive = uiState.isInvMode,
                onClick = onToggleInverse
            )
            SciBtn(
                text = if (uiState.isInvMode) "sin⁻¹" else "sin",
                fontSize = 14.sp,
                onClick = { onFunction(if (uiState.isInvMode) "asin" else "sin") }
            )
            SciBtn(
                text = if (uiState.isInvMode) "cos⁻¹" else "cos",
                fontSize = 14.sp,
                onClick = { onFunction(if (uiState.isInvMode) "acos" else "cos") }
            )
            SciBtn(
                text = if (uiState.isInvMode) "tan⁻¹" else "tan",
                fontSize = 14.sp,
                onClick = { onFunction(if (uiState.isInvMode) "atan" else "tan") }
            )
        }

        // Row 2: ln / eˣ, log / 10ˣ, 1/x, e, π
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SciBtn(
                text = if (uiState.isInvMode) "eˣ" else "ln",
                fontSize = 14.sp,
                onClick = {
                    if (uiState.isInvMode) {
                        onConstant("e")
                        onFunction("powerY")
                    } else {
                        onFunction("ln")
                    }
                }
            )
            SciBtn(
                text = if (uiState.isInvMode) "10ˣ" else "log",
                fontSize = 14.sp,
                onClick = {
                    if (uiState.isInvMode) {
                        onConstant("10")
                        onFunction("powerY")
                    } else {
                        onFunction("log")
                    }
                }
            )
            SciBtn(
                text = "1/x",
                fontSize = 14.sp,
                onClick = { onFunction("reciprocal") }
            )
            SciBtn(
                text = "e",
                fontSize = 15.sp,
                onClick = { onConstant("e") }
            )
            SciBtn(
                text = "π",
                fontSize = 15.sp,
                onClick = { onConstant("π") }
            )
        }

        // Row 3: x², xʸ, √, ∛, x!
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SciBtn("x²", fontSize = 14.sp) { onFunction("power2") }
            SciBtn("xʸ", fontSize = 14.sp) { onFunction("powerY") }
            SciBtn("√", fontSize = 16.sp) { onFunction("sqrt") }
            SciBtn("∛", fontSize = 16.sp) { onFunction("cbrt") }
            SciBtn("x!", fontSize = 14.sp, onClick = onFactorial)
        }
    }
}
