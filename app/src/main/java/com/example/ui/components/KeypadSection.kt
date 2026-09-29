package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ClearRed
import com.example.viewmodel.CalculatorUiState

@Composable
fun KeypadSection(
    uiState: CalculatorUiState,
    onDigit: (String) -> Unit,
    onOperator: (String) -> Unit,
    onDecimal: () -> Unit,
    onPercentage: () -> Unit,
    onToggleSign: () -> Unit,
    onBackspace: () -> Unit,
    onAllClear: () -> Unit,
    onEquals: () -> Unit,
    rowHeight: Dp = 68.dp,
    modifier: Modifier = Modifier
) {
    @Composable
    fun RowScope.KeypadBtn(
        text: String,
        type: ButtonType,
        fontSize: TextUnit,
        testTag: String,
        content: (@Composable () -> Unit)? = null,
        onClick: () -> Unit
    ) {
        CalculatorButton(
            text = text,
            type = type,
            fontSize = fontSize,
            testTag = testTag,
            hapticEnabled = uiState.hapticEnabled,
            soundEnabled = uiState.soundEnabled,
            modifier = Modifier.weight(1f),
            content = content,
            onClick = onClick
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Row 1: AC, ±, Red ✕ (erases digit by digit), ÷
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            KeypadBtn(
                text = "AC",
                type = ButtonType.FUNCTION,
                fontSize = 22.sp,
                testTag = "btn_all_clear",
                onClick = onAllClear
            )
            KeypadBtn(
                text = "±",
                type = ButtonType.FUNCTION,
                fontSize = 24.sp,
                testTag = "btn_toggle_sign",
                onClick = onToggleSign
            )
            KeypadBtn(
                text = "✕",
                type = ButtonType.CLEAR_RED,
                fontSize = 22.sp,
                testTag = "btn_backspace",
                content = {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Delete digit",
                        tint = ClearRed
                    )
                },
                onClick = onBackspace
            )
            KeypadBtn(
                text = "÷",
                type = ButtonType.OPERATOR,
                fontSize = 28.sp,
                testTag = "btn_divide",
                onClick = { onOperator("/") }
            )
        }

        // Row 2: 7, 8, 9, ×
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            KeypadBtn("7", ButtonType.NUMBER, 26.sp, "btn_7") { onDigit("7") }
            KeypadBtn("8", ButtonType.NUMBER, 26.sp, "btn_8") { onDigit("8") }
            KeypadBtn("9", ButtonType.NUMBER, 26.sp, "btn_9") { onDigit("9") }
            KeypadBtn("×", ButtonType.OPERATOR, 28.sp, "btn_multiply") { onOperator("*") }
        }

        // Row 3: 4, 5, 6, −
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            KeypadBtn("4", ButtonType.NUMBER, 26.sp, "btn_4") { onDigit("4") }
            KeypadBtn("5", ButtonType.NUMBER, 26.sp, "btn_5") { onDigit("5") }
            KeypadBtn("6", ButtonType.NUMBER, 26.sp, "btn_6") { onDigit("6") }
            KeypadBtn("−", ButtonType.OPERATOR, 28.sp, "btn_subtract") { onOperator("-") }
        }

        // Row 4: 1, 2, 3, +
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            KeypadBtn("1", ButtonType.NUMBER, 26.sp, "btn_1") { onDigit("1") }
            KeypadBtn("2", ButtonType.NUMBER, 26.sp, "btn_2") { onDigit("2") }
            KeypadBtn("3", ButtonType.NUMBER, 26.sp, "btn_3") { onDigit("3") }
            KeypadBtn("+", ButtonType.OPERATOR, 28.sp, "btn_add") { onOperator("+") }
        }

        // Row 5: %, 0, ., =
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            KeypadBtn("%", ButtonType.FUNCTION, 24.sp, "btn_percent", onClick = onPercentage)
            KeypadBtn("0", ButtonType.NUMBER, 26.sp, "btn_0") { onDigit("0") }
            KeypadBtn(".", ButtonType.NUMBER, 28.sp, "btn_dot", onClick = onDecimal)
            KeypadBtn("=", ButtonType.ACCENT, 30.sp, "btn_equals", onClick = onEquals)
        }
    }
}
