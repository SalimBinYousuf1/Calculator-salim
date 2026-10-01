package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.UnitCategory
import com.example.model.UnitRegistry
import com.example.ui.theme.ClearRed
import com.example.viewmodel.AppMode
import com.example.viewmodel.CalculatorUiState
import com.example.viewmodel.CalculatorViewModel

@Composable
fun UnitConverterSection(
    uiState: CalculatorUiState,
    viewModel: CalculatorViewModel,
    onShowSnackbar: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val amountScrollState = rememberScrollState()
    val resultScrollState = rememberScrollState()

    BackHandler {
        viewModel.setAppMode(AppMode.STANDARD)
    }

    val copyToClipboard: (String) -> Unit = { text ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Salim Unit Conversion", text)
        clipboard.setPrimaryClip(clip)
        onShowSnackbar("Copied $text")
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Bar: Back, Title, and Categories selector
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.setAppMode(AppMode.STANDARD) },
                    modifier = Modifier.testTag("btn_unit_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to calculator",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Unit Converter",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Category Chips Row (Length, Mass, Temperature, Area, Volume, Speed, Time, Data)
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(UnitCategory.values()) { category ->
                    val isSelected = uiState.unitCategory == category
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setUnitCategory(category) },
                        label = { Text(category.displayName, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }
        }

        // Conversion Card Container
        val currentUnits = UnitRegistry.unitsByCategory[uiState.unitCategory] ?: emptyList()
        val fromUnit = currentUnits.firstOrNull { it.id == uiState.unitFromId } ?: currentUnits.firstOrNull()
        val toUnit = currentUnits.firstOrNull { it.id == uiState.unitToId } ?: currentUnits.getOrNull(1)

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // FROM UNIT CARD
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { viewModel.openUnitPicker(isFrom = true) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = fromUnit?.name ?: "",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "▼",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "(${fromUnit?.symbol ?: ""})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Amount Input
                        Box(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .horizontalScroll(amountScrollState),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Text(
                                text = uiState.unitInputAmount,
                                fontSize = if (uiState.unitInputAmount.length > 12) 24.sp else 30.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                softWrap = false,
                                maxLines = 1,
                                letterSpacing = 0.5.sp,
                                modifier = Modifier.testTag("text_unit_amount")
                            )
                        }
                    }
                }

                // SWAP BUTTON
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    FilledIconButton(
                        onClick = { viewModel.onSwapUnits() },
                        modifier = Modifier
                            .size(42.dp)
                            .testTag("btn_swap_units"),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = "Swap units",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // TO UNIT CARD
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { viewModel.openUnitPicker(isFrom = false) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = toUnit?.name ?: "",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "▼",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "(${toUnit?.symbol ?: ""})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Converted Result & Copy Button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .horizontalScroll(resultScrollState),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Text(
                                    text = uiState.unitConvertedResult,
                                    fontSize = if (uiState.unitConvertedResult.length > 12) 24.sp else 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontFamily = FontFamily.SansSerif,
                                    softWrap = false,
                                    maxLines = 1,
                                    letterSpacing = 0.5.sp,
                                    modifier = Modifier.testTag("text_unit_result")
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            FilledIconButton(
                                onClick = {
                                    val text = "${uiState.unitConvertedResult} ${toUnit?.symbol ?: ""}"
                                    copyToClipboard(text)
                                },
                                modifier = Modifier
                                    .size(34.dp)
                                    .testTag("btn_copy_unit_result"),
                                shape = CircleShape,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy converted result",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // UNIT CONVERTER KEYPAD
        val rowHeight = 60.dp

        @Composable
        fun androidx.compose.foundation.layout.RowScope.UBtn(
            text: String,
            type: ButtonType,
            fontSize: androidx.compose.ui.unit.TextUnit = 24.sp,
            testTag: String = "btn_u_$text",
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
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                UBtn("AC", ButtonType.FUNCTION, 20.sp, "btn_u_ac") { viewModel.onUnitClear() }
                UBtn("7", ButtonType.NUMBER, 24.sp, "btn_u_7") { viewModel.onUnitDigit("7") }
                UBtn("8", ButtonType.NUMBER, 24.sp, "btn_u_8") { viewModel.onUnitDigit("8") }
                UBtn("9", ButtonType.NUMBER, 24.sp, "btn_u_9") { viewModel.onUnitDigit("9") }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                UBtn(
                    text = "✕",
                    type = ButtonType.CLEAR_RED,
                    fontSize = 20.sp,
                    testTag = "btn_u_backspace",
                    content = {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Delete digit",
                            tint = ClearRed
                        )
                    },
                    onClick = { viewModel.onUnitBackspace() }
                )
                UBtn("4", ButtonType.NUMBER, 24.sp, "btn_u_4") { viewModel.onUnitDigit("4") }
                UBtn("5", ButtonType.NUMBER, 24.sp, "btn_u_5") { viewModel.onUnitDigit("5") }
                UBtn("6", ButtonType.NUMBER, 24.sp, "btn_u_6") { viewModel.onUnitDigit("6") }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                UBtn(".", ButtonType.NUMBER, 26.sp, "btn_u_dot") { viewModel.onUnitDecimal() }
                UBtn("1", ButtonType.NUMBER, 24.sp, "btn_u_1") { viewModel.onUnitDigit("1") }
                UBtn("2", ButtonType.NUMBER, 24.sp, "btn_u_2") { viewModel.onUnitDigit("2") }
                UBtn("3", ButtonType.NUMBER, 24.sp, "btn_u_3") { viewModel.onUnitDigit("3") }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                UBtn("0", ButtonType.NUMBER, 24.sp, "btn_u_0") { viewModel.onUnitDigit("0") }
                UBtn("00", ButtonType.NUMBER, 22.sp, "btn_u_00") {
                    viewModel.onUnitDigit("0")
                    viewModel.onUnitDigit("0")
                }
            }
        }
    }

    // UNIT PICKER BOTTOM SHEET
    if (uiState.isUnitPickerOpen) {
        UnitPickerSheet(
            category = uiState.unitCategory,
            selectedUnitId = if (uiState.isPickingFromUnit) uiState.unitFromId else uiState.unitToId,
            onDismiss = { viewModel.closeUnitPicker() },
            onSelect = { viewModel.onSelectUnit(it) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitPickerSheet(
    category: UnitCategory,
    selectedUnitId: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val units = UnitRegistry.unitsByCategory[category] ?: emptyList()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("unit_picker_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            Text(
                text = "Select ${category.displayName} Unit",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(units) { unit ->
                    val isSelected = unit.id == selectedUnitId
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelect(unit.id) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = unit.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = unit.symbol,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
