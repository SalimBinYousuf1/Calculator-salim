package com.example.viewmodel

import com.example.model.CalculationItem
import com.example.model.CurrencyInfoEntity
import java.util.UUID

enum class AppMode {
    STANDARD,
    SCIENTIFIC,
    CURRENCY
}

data class CalculationStep(
    val id: String = UUID.randomUUID().toString(),
    val expression: String,
    val result: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class CalculatorUiState(
    // Mode
    val appMode: AppMode = AppMode.STANDARD,

    // Calculator Session
    val steps: List<CalculationStep> = emptyList(),
    val expression: String = "",
    val livePreview: String? = null,
    val finalResult: String? = null,
    val isCalculated: Boolean = false,
    val errorMessage: String? = null,
    val isRadMode: Boolean = false, // false = DEG, true = RAD
    val isInvMode: Boolean = false,
    val isHistoryVisible: Boolean = false,
    val isSettingsVisible: Boolean = false,
    val historyList: List<CalculationItem> = emptyList(),
    val showClearHistoryDialog: Boolean = false,
    val snackbarMessage: String? = null,

    // Currency Exchange State (All Countries & Currencies)
    val currencyAmount: String = "100",
    val fromCurrency: String = "USD",
    val toCurrency: String = "INR",
    val currencyConvertedResult: String = "",
    val currencyRateInfo: String = "",
    val currencyDateInfo: String = "",
    val isCurrencyCached: Boolean = true,
    val isCurrencySyncing: Boolean = false,
    val isCurrencyPickerOpen: Boolean = false,
    val isPickingForFromCurrency: Boolean = true,
    val currencySearchQuery: String = "",
    val currencyList: List<CurrencyInfoEntity> = emptyList(),
    val currencyErrorMessage: String? = null,

    // Customization & Settings (Defaults: LIGHT theme, EMERALD green accent)
    val themeMode: String = "LIGHT", // Default Light per user request!
    val accentColor: String = "EMERALD", // Default Green per user request!
    val hapticEnabled: Boolean = true,
    val soundEnabled: Boolean = false, // Keypad touch sound on/off per user request!
    val decimalPrecision: Int = -1, // -1 for Auto, or 2, 4, 6, 8
    val thousandsGrouping: String = "COMMA" // COMMA, SPACE, NONE
)
