package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CurrencyInfoEntity
import com.example.ui.theme.ClearRed
import com.example.viewmodel.AppMode
import com.example.viewmodel.CalculatorUiState
import com.example.viewmodel.CalculatorViewModel

@Composable
fun CurrencyConverterSection(
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
        val clip = ClipData.newPlainText("Salim Exchange Result", text)
        clipboard.setPrimaryClip(clip)
        onShowSnackbar("Copied $text")
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Bar: Back to Calculator, Screen Title, and Manual Sync Refresh
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { viewModel.setAppMode(AppMode.STANDARD) },
                    modifier = Modifier.testTag("btn_currency_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to calculator",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Currency Exchange",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            IconButton(
                onClick = { viewModel.syncCurrencyRatesManually() },
                modifier = Modifier
                    .size(40.dp)
                    .testTag("btn_sync_rates"),
                enabled = !uiState.isCurrencySyncing
            ) {
                if (uiState.isCurrencySyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh exchange rates",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // Conversion Card Container (Whole screen upper section)
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
                // FROM CURRENCY CARD
                CurrencyCard(
                    title = "From",
                    code = uiState.fromCurrency,
                    currencyList = uiState.currencyList,
                    onClick = { viewModel.openCurrencyPicker(forFrom = true) },
                    trailingContent = {
                        // Amount text in a horizontally scrollable container to prevent digit jumbling
                        Box(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .horizontalScroll(amountScrollState),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Text(
                                text = uiState.currencyAmount,
                                fontSize = if (uiState.currencyAmount.length > 12) 24.sp else 30.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                softWrap = false,
                                maxLines = 1,
                                letterSpacing = 0.5.sp,
                                modifier = Modifier.testTag("text_currency_amount")
                            )
                        }
                    }
                )

                // SWAP BUTTON ROW
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    FilledIconButton(
                        onClick = { viewModel.onSwapCurrencies() },
                        modifier = Modifier
                            .size(42.dp)
                            .testTag("btn_swap_currencies"),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = "Swap currencies",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // TO CURRENCY CARD
                CurrencyCard(
                    title = "To",
                    code = uiState.toCurrency,
                    currencyList = uiState.currencyList,
                    onClick = { viewModel.openCurrencyPicker(forFrom = false) },
                    trailingContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            // Converted result in a horizontally scrollable container to prevent digit jumbling
                            Box(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .horizontalScroll(resultScrollState),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Text(
                                    text = uiState.currencyConvertedResult,
                                    fontSize = if (uiState.currencyConvertedResult.length > 12) 26.sp else 34.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontFamily = FontFamily.SansSerif,
                                    softWrap = false,
                                    maxLines = 1,
                                    letterSpacing = 0.5.sp,
                                    modifier = Modifier.testTag("text_currency_result")
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            FilledIconButton(
                                onClick = {
                                    val text = "${uiState.currencyConvertedResult} ${uiState.toCurrency}"
                                    copyToClipboard(text)
                                },
                                modifier = Modifier
                                    .size(34.dp)
                                    .testTag("btn_copy_currency_result"),
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
                )

                // RATE DETAILS & FRESHNESS FOOTER
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    if (uiState.currencyRateInfo.isNotBlank()) {
                        Text(
                            text = uiState.currencyRateInfo,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (uiState.currencyDateInfo.isNotBlank()) {
                        Text(
                            text = uiState.currencyDateInfo,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                    if (uiState.currencyErrorMessage != null) {
                        Text(
                            text = uiState.currencyErrorMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // FULL SCREEN KEYPAD FOR CURRENCY INPUT
        CurrencyFullKeypad(
            uiState = uiState,
            onDigit = { viewModel.onCurrencyDigit(it) },
            onDecimal = { viewModel.onCurrencyDecimal() },
            onBackspace = { viewModel.onCurrencyBackspace() },
            onClear = { viewModel.onCurrencyClear() }
        )
    }

    // SEARCHABLE COUNTRY & CURRENCY PICKER BOTTOM SHEET
    if (uiState.isCurrencyPickerOpen) {
        CountryCurrencyPickerSheet(
            uiState = uiState,
            onDismiss = { viewModel.closeCurrencyPicker() },
            onSelect = { viewModel.onSelectCurrency(it) },
            onSearchQueryChange = { viewModel.onCurrencySearchQueryChange(it) }
        )
    }
}

@Composable
private fun CurrencyCard(
    title: String,
    code: String,
    currencyList: List<CurrencyInfoEntity>,
    onClick: () -> Unit,
    trailingContent: @Composable () -> Unit
) {
    val info = currencyList.firstOrNull { it.code.equals(code, ignoreCase = true) }
    val flag = info?.flag ?: "🌐"
    val name = info?.name ?: code

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Flag + Code + Country name
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Text(
                    text = flag,
                    fontSize = 24.sp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = code,
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
                        text = name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Right: Trailing content (Amount or Result)
            trailingContent()
        }
    }
}

@Composable
private fun CurrencyFullKeypad(
    uiState: CalculatorUiState,
    onDigit: (String) -> Unit,
    onDecimal: () -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit
) {
    val rowHeight = 60.dp

    @Composable
    fun androidx.compose.foundation.layout.RowScope.CBtn(
        text: String,
        type: ButtonType,
        fontSize: androidx.compose.ui.unit.TextUnit = 24.sp,
        testTag: String = "btn_cur_$text",
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
        // Row 1: AC, 7, 8, 9
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CBtn("AC", ButtonType.FUNCTION, 20.sp, "btn_cur_ac", onClick = onClear)
            CBtn("7", ButtonType.NUMBER, 24.sp, "btn_cur_7") { onDigit("7") }
            CBtn("8", ButtonType.NUMBER, 24.sp, "btn_cur_8") { onDigit("8") }
            CBtn("9", ButtonType.NUMBER, 24.sp, "btn_cur_9") { onDigit("9") }
        }

        // Row 2: ✕ (Backspace), 4, 5, 6
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CBtn(
                text = "✕",
                type = ButtonType.CLEAR_RED,
                fontSize = 20.sp,
                testTag = "btn_cur_backspace",
                content = {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Delete digit",
                        tint = ClearRed
                    )
                },
                onClick = onBackspace
            )
            CBtn("4", ButtonType.NUMBER, 24.sp, "btn_cur_4") { onDigit("4") }
            CBtn("5", ButtonType.NUMBER, 24.sp, "btn_cur_5") { onDigit("5") }
            CBtn("6", ButtonType.NUMBER, 24.sp, "btn_cur_6") { onDigit("6") }
        }

        // Row 3: ., 1, 2, 3
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CBtn(".", ButtonType.NUMBER, 26.sp, "btn_cur_dot", onClick = onDecimal)
            CBtn("1", ButtonType.NUMBER, 24.sp, "btn_cur_1") { onDigit("1") }
            CBtn("2", ButtonType.NUMBER, 24.sp, "btn_cur_2") { onDigit("2") }
            CBtn("3", ButtonType.NUMBER, 24.sp, "btn_cur_3") { onDigit("3") }
        }

        // Row 4: 0, 00 (spanned)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CBtn("0", ButtonType.NUMBER, 24.sp, "btn_cur_0") { onDigit("0") }
            CBtn("00", ButtonType.NUMBER, 22.sp, "btn_cur_00") {
                onDigit("0")
                onDigit("0")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountryCurrencyPickerSheet(
    uiState: CalculatorUiState,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val filteredList = uiState.currencyList.filter {
        val q = uiState.currencySearchQuery.trim().lowercase()
        if (q.isEmpty()) true
        else it.code.lowercase().contains(q) ||
                it.name.lowercase().contains(q) ||
                it.symbol.lowercase().contains(q)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("currency_picker_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            Text(
                text = if (uiState.isPickingForFromCurrency) "Select Source Country / Currency" else "Select Target Country / Currency",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Search input (supports country name, currency name, code, symbol)
            OutlinedTextField(
                value = uiState.currencySearchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_currency_search"),
                placeholder = { Text("Search by country, currency, or code (e.g. India, USD, €)") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (uiState.currencySearchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Currencies List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(440.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(items = filteredList, key = { it.code }) { currency ->
                    val isSelected = if (uiState.isPickingForFromCurrency) {
                        currency.code.equals(uiState.fromCurrency, ignoreCase = true)
                    } else {
                        currency.code.equals(uiState.toCurrency, ignoreCase = true)
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelect(currency.code) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = currency.flag,
                                fontSize = 28.sp
                            )

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = currency.code,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "(${currency.symbol})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text = currency.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
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
