package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.CalculatorUiState

@Composable
fun MemoryBar(
    uiState: CalculatorUiState,
    onMemoryClear: () -> Unit,
    onMemoryRecall: () -> Unit,
    onMemoryAdd: () -> Unit,
    onMemorySubtract: () -> Unit,
    onMemoryStore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // MC
        TextButton(
            onClick = onMemoryClear,
            enabled = uiState.isMemorySet,
            modifier = Modifier.testTag("btn_mc")
        ) {
            Text(
                text = "MC",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (uiState.isMemorySet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            )
        }

        // MR
        TextButton(
            onClick = onMemoryRecall,
            enabled = uiState.isMemorySet,
            modifier = Modifier.testTag("btn_mr")
        ) {
            Text(
                text = "MR",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (uiState.isMemorySet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            )
        }

        // M+
        TextButton(
            onClick = onMemoryAdd,
            modifier = Modifier.testTag("btn_m_plus")
        ) {
            Text(
                text = "M+",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // M-
        TextButton(
            onClick = onMemorySubtract,
            modifier = Modifier.testTag("btn_m_minus")
        ) {
            Text(
                text = "M−",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // MS
        TextButton(
            onClick = onMemoryStore,
            modifier = Modifier.testTag("btn_ms")
        ) {
            Text(
                text = "MS",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
