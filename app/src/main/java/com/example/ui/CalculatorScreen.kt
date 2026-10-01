package com.example.ui

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.CurrencyConverterSection
import com.example.ui.components.DisplaySection
import com.example.ui.components.HistorySheet
import com.example.ui.components.KeypadSection
import com.example.ui.components.MemoryBar
import com.example.ui.components.ScientificKeypad
import com.example.ui.components.SettingsSheet
import com.example.ui.components.UnitConverterSection
import com.example.viewmodel.AppMode
import com.example.viewmodel.CalculatorUiState
import com.example.viewmodel.CalculatorViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    viewModel: CalculatorViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Handle back button
    BackHandler(
        enabled = uiState.isSettingsVisible ||
                uiState.isHistoryVisible ||
                uiState.isCurrencyPickerOpen ||
                uiState.isUnitPickerOpen ||
                uiState.appMode != AppMode.STANDARD
    ) {
        when {
            uiState.isSettingsVisible -> viewModel.setSettingsVisible(false)
            uiState.isHistoryVisible -> viewModel.setHistoryVisible(false)
            uiState.isCurrencyPickerOpen -> viewModel.closeCurrencyPicker()
            uiState.isUnitPickerOpen -> viewModel.closeUnitPicker()
            uiState.appMode != AppMode.STANDARD -> viewModel.setAppMode(AppMode.STANDARD)
        }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.setSnackbarMessage(null)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Whole-screen Currency Exchange when currency mode is selected
            if (uiState.appMode == AppMode.CURRENCY) {
                CurrencyConverterSection(
                    uiState = uiState,
                    viewModel = viewModel,
                    onShowSnackbar = { scope.launch { snackbarHostState.showSnackbar(it) } }
                )
            } else if (uiState.appMode == AppMode.UNIT_CONVERTER) {
                UnitConverterSection(
                    uiState = uiState,
                    viewModel = viewModel,
                    onShowSnackbar = { scope.launch { snackbarHostState.showSnackbar(it) } }
                )
            } else if (isLandscape) {
                LandscapeCalculatorLayout(
                    uiState = uiState,
                    viewModel = viewModel,
                    onShowSnackbar = { scope.launch { snackbarHostState.showSnackbar(it) } }
                )
            } else {
                PortraitCalculatorLayout(
                    uiState = uiState,
                    viewModel = viewModel,
                    onShowSnackbar = { scope.launch { snackbarHostState.showSnackbar(it) } }
                )
            }
        }

        // History Modal Bottom Sheet
        if (uiState.isHistoryVisible) {
            HistorySheet(
                uiState = uiState,
                onDismiss = { viewModel.setHistoryVisible(false) },
                onSelectItem = { viewModel.onSelectHistoryItem(it) },
                onUseResult = { viewModel.onUseHistoryResult(it) },
                onDeleteItem = { viewModel.onDeleteHistoryItem(it) },
                onConfirmClearHistory = { viewModel.onConfirmClearHistory() },
                onShowClearDialog = { viewModel.setShowClearHistoryDialog(it) },
                onShowSnackbar = { scope.launch { snackbarHostState.showSnackbar(it) } }
            )
        }

        // Settings Modal Bottom Sheet
        if (uiState.isSettingsVisible) {
            SettingsSheet(
                uiState = uiState,
                viewModel = viewModel,
                onDismiss = { viewModel.setSettingsVisible(false) }
            )
        }
    }
}

@Composable
private fun PortraitCalculatorLayout(
    uiState: CalculatorUiState,
    viewModel: CalculatorViewModel,
    onShowSnackbar: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Controls Bar with single '$' currency icon, unit converter, clean history icon, fx, and settings
        TopControlsRow(
            uiState = uiState,
            onToggleScientific = { viewModel.toggleScientific() },
            onOpenUnitConverter = { viewModel.setAppMode(AppMode.UNIT_CONVERTER) },
            onOpenCurrency = { viewModel.setAppMode(AppMode.CURRENCY) },
            onToggleHistory = { viewModel.toggleHistory() },
            onOpenSettings = { viewModel.setSettingsVisible(true) }
        )

        // Display Area
        DisplaySection(
            uiState = uiState,
            onShowSnackbar = onShowSnackbar,
            modifier = Modifier.weight(1f)
        )

        // Memory Functions Bar (MC, MR, M+, M-, MS)
        MemoryBar(
            uiState = uiState,
            onMemoryClear = { viewModel.onMemoryClear() },
            onMemoryRecall = { viewModel.onMemoryRecall() },
            onMemoryAdd = { viewModel.onMemoryAdd() },
            onMemorySubtract = { viewModel.onMemorySubtract() },
            onMemoryStore = { viewModel.onMemoryStore() }
        )

        // Scientific Keypad (if fx toggled)
        if (uiState.appMode == AppMode.SCIENTIFIC) {
            ScientificKeypad(
                uiState = uiState,
                onToggleRadMode = { viewModel.toggleRadMode() },
                onToggleInverse = { viewModel.toggleInverse() },
                onFunction = { viewModel.onFunction(it) },
                onConstant = { viewModel.onConstant(it) },
                onFactorial = { viewModel.onFactorial() },
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }

        // Standard Numeric Keypad (AC at R1C1, Red ✕ at R1C3, % at R5C1)
        KeypadSection(
            uiState = uiState,
            onDigit = { viewModel.onDigit(it) },
            onOperator = { viewModel.onOperator(it) },
            onDecimal = { viewModel.onDecimal() },
            onPercentage = { viewModel.onPercentage() },
            onToggleSign = { viewModel.onToggleSign() },
            onBackspace = { viewModel.onBackspace() },
            onAllClear = { viewModel.onAllClear() },
            onEquals = { viewModel.onEquals() },
            rowHeight = if (uiState.appMode == AppMode.SCIENTIFIC) 54.dp else 66.dp
        )
    }
}

@Composable
private fun LandscapeCalculatorLayout(
    uiState: CalculatorUiState,
    viewModel: CalculatorViewModel,
    onShowSnackbar: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Left Column: Display and Scientific Keypad
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            TopControlsRow(
                uiState = uiState,
                onToggleScientific = { viewModel.toggleScientific() },
                onOpenUnitConverter = { viewModel.setAppMode(AppMode.UNIT_CONVERTER) },
                onOpenCurrency = { viewModel.setAppMode(AppMode.CURRENCY) },
                onToggleHistory = { viewModel.toggleHistory() },
                onOpenSettings = { viewModel.setSettingsVisible(true) }
            )

            DisplaySection(
                uiState = uiState,
                onShowSnackbar = onShowSnackbar,
                modifier = Modifier.weight(1f)
            )

            MemoryBar(
                uiState = uiState,
                onMemoryClear = { viewModel.onMemoryClear() },
                onMemoryRecall = { viewModel.onMemoryRecall() },
                onMemoryAdd = { viewModel.onMemoryAdd() },
                onMemorySubtract = { viewModel.onMemorySubtract() },
                onMemoryStore = { viewModel.onMemoryStore() }
            )

            ScientificKeypad(
                uiState = uiState,
                onToggleRadMode = { viewModel.toggleRadMode() },
                onToggleInverse = { viewModel.toggleInverse() },
                onFunction = { viewModel.onFunction(it) },
                onConstant = { viewModel.onConstant(it) },
                onFactorial = { viewModel.onFactorial() },
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }

        // Right Column: Standard Numeric & Operator Keypad
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center
        ) {
            KeypadSection(
                uiState = uiState,
                onDigit = { viewModel.onDigit(it) },
                onOperator = { viewModel.onOperator(it) },
                onDecimal = { viewModel.onDecimal() },
                onPercentage = { viewModel.onPercentage() },
                onToggleSign = { viewModel.onToggleSign() },
                onBackspace = { viewModel.onBackspace() },
                onAllClear = { viewModel.onAllClear() },
                onEquals = { viewModel.onEquals() },
                rowHeight = 52.dp
            )
        }
    }
}

@Composable
private fun TopControlsRow(
    uiState: CalculatorUiState,
    onToggleScientific: () -> Unit,
    onOpenUnitConverter: () -> Unit,
    onOpenCurrency: () -> Unit,
    onToggleHistory: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App Name Branding ("Salim")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Calculate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Salim",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Controls: fx toggle, Unit Converter, Currency Exchange ($), History (clean), Settings
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // fx (Scientific Mode Toggle)
            IconButton(
                onClick = onToggleScientific,
                modifier = Modifier.testTag("btn_toggle_fx"),
                colors = if (uiState.appMode == AppMode.SCIENTIFIC) {
                    IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                } else {
                    IconButtonDefaults.iconButtonColors()
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Science,
                    contentDescription = "Scientific Mode",
                    tint = if (uiState.appMode == AppMode.SCIENTIFIC) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Unit Converter Button
            IconButton(
                onClick = onOpenUnitConverter,
                modifier = Modifier.testTag("btn_unit_converter"),
                colors = if (uiState.appMode == AppMode.UNIT_CONVERTER) {
                    IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                } else {
                    IconButtonDefaults.iconButtonColors()
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Straighten,
                    contentDescription = "Unit Converter",
                    tint = if (uiState.appMode == AppMode.UNIT_CONVERTER) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Single '$' Exchange Rate Icon on main screen
            IconButton(
                onClick = onOpenCurrency,
                modifier = Modifier.testTag("btn_currency_mode"),
                colors = if (uiState.appMode == AppMode.CURRENCY) {
                    IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                } else {
                    IconButtonDefaults.iconButtonColors()
                }
            ) {
                Icon(
                    imageVector = Icons.Default.AttachMoney,
                    contentDescription = "Currency Exchange",
                    tint = if (uiState.appMode == AppMode.CURRENCY) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // History Button (Clean icon, NO number badge)
            IconButton(
                onClick = onToggleHistory,
                modifier = Modifier.testTag("btn_history")
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "History",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Settings Tab Button (Gear Icon)
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.testTag("btn_settings")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
