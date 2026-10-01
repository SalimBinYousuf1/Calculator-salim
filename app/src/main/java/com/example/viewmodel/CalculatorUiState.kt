package com.example.viewmodel

import com.example.model.CalculationItem
import com.example.model.CurrencyInfoEntity
import com.example.model.UnitCategory
import java.util.UUID

enum class AppMode {
    STANDARD,
    SCIENTIFIC,
    CURRENCY,
    UNIT_CONVERTER
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

    // Memory Functions (MC, MR, M+, M-, MS)
    val memoryValue: Double = 0.0,
    val isMemorySet: Boolean = false,

    // Repeated Equals State (OEM Feature)
    val lastBinaryOp: Char? = null,
    val lastBinaryOperand: Double? = null,

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

    // Unit Converter State
    val unitCategory: UnitCategory = UnitCategory.LENGTH,
    val unitFromId: String = "m",
    val unitToId: String = "ft",
    val unitInputAmount: String = "1",
    val unitConvertedResult: String = "3.28084",
    val isUnitPickerOpen: Boolean = false,
    val isPickingFromUnit: Boolean = true,

    // Customization & Settings (Defaults: LIGHT theme, EMERALD green accent)
    val themeMode: String = "LIGHT",
    val accentColor: String = "EMERALD",
    val hapticEnabled: Boolean = true,
    val soundEnabled: Boolean = false,
    val decimalPrecision: Int = -1, // -1 for Auto, or 2, 4, 6, 8
    val thousandsGrouping: String = "COMMA" // COMMA, SPACE, NONE
)
