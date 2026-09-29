package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.ConversionResult
import com.example.data.CurrencyRepository
import com.example.data.HistoryRepository
import com.example.engine.CalculatorEngine
import com.example.engine.EvalResult
import com.example.model.CalculationItem
import com.example.worker.CurrencySyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CalculatorViewModel(application: Application) : AndroidViewModel(application) {

    private val historyRepository: HistoryRepository
    private val currencyRepository: CurrencyRepository

    private val _uiState = MutableStateFlow(CalculatorUiState())
    val uiState: StateFlow<CalculatorUiState>

    init {
        val database = AppDatabase.getDatabase(application)
        historyRepository = HistoryRepository(database.historyDao())
        currencyRepository = CurrencyRepository(database.currencyDao(), application)

        // Combine Room history and currencies with UI state
        uiState = combine(
            _uiState,
            historyRepository.allHistory,
            currencyRepository.allCurrencies
        ) { state, history, currencies ->
            state.copy(
                historyList = history,
                currencyList = currencies
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CalculatorUiState()
        )

        // Initialize currency offline database and schedule WorkManager daily sync
        viewModelScope.launch {
            currencyRepository.initializeIfNeeded()
            recalculateCurrency()
            // Schedule daily background sync via WorkManager
            CurrencySyncWorker.scheduleDailySync(application)
            // Background check for latest Frankfurter rates without blocking UI
            currencyRepository.syncRates(force = false)
            recalculateCurrency()
        }
    }

    // ==========================================
    // Mode Switching
    // ==========================================

    fun setAppMode(mode: AppMode) {
        _uiState.update { it.copy(appMode = mode) }
        if (mode == AppMode.CURRENCY) {
            recalculateCurrency()
        }
    }

    // ==========================================
    // Calculator Keypad Actions
    // ==========================================

    fun onDigit(digit: String) {
        _uiState.update { state ->
            val newSteps = if (state.isCalculated && state.finalResult != null) {
                state.steps + CalculationStep(
                    expression = state.expression,
                    result = state.finalResult
                )
            } else {
                state.steps
            }

            val newExpr = if (state.isCalculated) {
                digit
            } else if (state.expression == "0") {
                digit
            } else {
                state.expression + digit
            }

            updateLivePreview(state.copy(
                steps = newSteps,
                expression = newExpr,
                isCalculated = false,
                finalResult = null,
                errorMessage = null
            ))
        }
    }

    fun onDecimal() {
        _uiState.update { state ->
            val expr = state.expression
            val newSteps = if (state.isCalculated && state.finalResult != null) {
                state.steps + CalculationStep(
                    expression = state.expression,
                    result = state.finalResult
                )
            } else {
                state.steps
            }

            val newExpr = if (state.isCalculated || expr.isEmpty()) {
                "0."
            } else {
                val lastToken = getLastNumberToken(expr)
                if (lastToken.contains(".")) {
                    expr
                } else if (expr.endsWith(" ") || expr.endsWith("(") || expr.endsWith("+") || expr.endsWith("−") || expr.endsWith("×") || expr.endsWith("÷")) {
                    expr + "0."
                } else {
                    expr + "."
                }
            }

            updateLivePreview(state.copy(
                steps = newSteps,
                expression = newExpr,
                isCalculated = false,
                finalResult = null,
                errorMessage = null
            ))
        }
    }

    fun onOperator(op: String) {
        val displayOp = when (op) {
            "+" -> "+"
            "-" -> "−"
            "*" -> "×"
            "/" -> "÷"
            "^" -> "^"
            else -> op
        }

        _uiState.update { state ->
            var expr = state.expression
            val isCalc = state.isCalculated

            val newSteps = if (isCalc && state.finalResult != null && state.errorMessage == null) {
                state.steps + CalculationStep(
                    expression = state.expression,
                    result = state.finalResult
                )
            } else {
                state.steps
            }

            val newExpr = if (isCalc && state.finalResult != null && state.errorMessage == null) {
                val cleanResult = state.finalResult.replace(",", "")
                "$cleanResult $displayOp "
            } else if (expr.isEmpty()) {
                if (displayOp == "−") "−" else ""
            } else if (expr.endsWith(" + ") || expr.endsWith(" − ") || expr.endsWith(" × ") || expr.endsWith(" ÷ ") || expr.endsWith(" ^ ")) {
                expr.substring(0, expr.length - 3) + " $displayOp "
            } else if (expr.endsWith(" ")) {
                expr + "$displayOp "
            } else {
                "$expr $displayOp "
            }

            updateLivePreview(state.copy(
                steps = newSteps,
                expression = newExpr,
                isCalculated = false,
                finalResult = null,
                errorMessage = null
            ))
        }
    }

    fun onParenthesis() {
        _uiState.update { state ->
            val expr = state.expression
            val openCount = expr.count { it == '(' }
            val closeCount = expr.count { it == ')' }

            val newExpr = if (state.isCalculated) {
                "("
            } else if (expr.isEmpty()) {
                "("
            } else {
                val lastChar = expr.last()
                val canClose = openCount > closeCount && (lastChar.isDigit() || lastChar == ')' || lastChar == '%' || lastChar == 'π' || lastChar == 'e')
                if (canClose) {
                    "$expr)"
                } else if (lastChar.isDigit() || lastChar == ')' || lastChar == 'π' || lastChar == 'e') {
                    "$expr × ("
                } else if (expr.endsWith(" ")) {
                    "$expr("
                } else {
                    "$expr("
                }
            }

            updateLivePreview(state.copy(
                expression = newExpr,
                isCalculated = false,
                finalResult = null,
                errorMessage = null
            ))
        }
    }

    fun onPercentage() {
        _uiState.update { state ->
            val expr = state.expression
            if (expr.isNotEmpty() && !expr.endsWith(" ") && !expr.endsWith("(")) {
                val newExpr = "$expr%"
                updateLivePreview(state.copy(
                    expression = newExpr,
                    isCalculated = false,
                    finalResult = null,
                    errorMessage = null
                ))
            } else {
                state
            }
        }
    }

    fun onToggleSign() {
        _uiState.update { state ->
            val expr = state.expression
            if (state.isCalculated && state.finalResult != null && state.errorMessage == null) {
                val clean = state.finalResult.replace(",", "")
                val toggled = if (clean.startsWith("-")) clean.substring(1) else "-$clean"
                updateLivePreview(state.copy(
                    expression = toggled,
                    isCalculated = false,
                    finalResult = null
                ))
            } else if (expr.isEmpty()) {
                state.copy(expression = "−")
            } else {
                val lastOpIdx = expr.lastIndexOfAny(charArrayOf('+', '×', '÷'))
                val lastMinusIdx = expr.lastIndexOf('−')
                val targetIdx = maxOf(lastOpIdx, lastMinusIdx)

                val newExpr = if (targetIdx == -1) {
                    if (expr.startsWith("−")) expr.substring(1) else "−$expr"
                } else {
                    val prefix = expr.substring(0, targetIdx + 1)
                    val suffix = expr.substring(targetIdx + 1).trim()
                    if (suffix.startsWith("(-") && suffix.endsWith(")")) {
                        prefix + " " + suffix.substring(2, suffix.length - 1)
                    } else if (suffix.isNotEmpty()) {
                        "$prefix (-$suffix)"
                    } else {
                        expr
                    }
                }
                updateLivePreview(state.copy(expression = newExpr))
            }
        }
    }

    fun onFunction(functionName: String) {
        _uiState.update { state ->
            val expr = state.expression
            val fnCall = when (functionName) {
                "sin" -> "sin("
                "cos" -> "cos("
                "tan" -> "tan("
                "asin" -> "sin⁻¹("
                "acos" -> "cos⁻¹("
                "atan" -> "tan⁻¹("
                "ln" -> "ln("
                "log" -> "log("
                "sqrt" -> "√("
                "cbrt" -> "∛("
                "power2" -> "^2"
                "powerY" -> " ^ "
                "reciprocal" -> "^(-1)"
                else -> "$functionName("
            }

            val newExpr = if (state.isCalculated) {
                if (fnCall.startsWith("^")) {
                    (state.finalResult?.replace(",", "") ?: "") + fnCall
                } else {
                    fnCall
                }
            } else if (expr.isEmpty()) {
                fnCall
            } else {
                val lastChar = expr.last()
                if (fnCall.startsWith("^")) {
                    "$expr$fnCall"
                } else if (lastChar.isDigit() || lastChar == ')' || lastChar == 'π' || lastChar == 'e') {
                    "$expr × $fnCall"
                } else if (expr.endsWith(" ")) {
                    "$expr$fnCall"
                } else {
                    "$expr$fnCall"
                }
            }

            updateLivePreview(state.copy(
                expression = newExpr,
                isCalculated = false,
                finalResult = null,
                errorMessage = null
            ))
        }
    }

    fun onConstant(constant: String) {
        _uiState.update { state ->
            val expr = state.expression
            val newExpr = if (state.isCalculated || expr.isEmpty()) {
                constant
            } else {
                val lastChar = expr.last()
                if (lastChar.isDigit() || lastChar == ')' || lastChar == 'π' || lastChar == 'e') {
                    "$expr × $constant"
                } else if (expr.endsWith(" ")) {
                    "$expr$constant"
                } else {
                    "$expr$constant"
                }
            }
            updateLivePreview(state.copy(
                expression = newExpr,
                isCalculated = false,
                finalResult = null,
                errorMessage = null
            ))
        }
    }

    fun onFactorial() {
        _uiState.update { state ->
            val expr = state.expression
            if (expr.isNotEmpty() && (expr.last().isDigit() || expr.last() == ')')) {
                val newExpr = "$expr!"
                updateLivePreview(state.copy(
                    expression = newExpr,
                    isCalculated = false,
                    finalResult = null,
                    errorMessage = null
                ))
            } else {
                state
            }
        }
    }

    /**
     * Red "✕" Key: Erases digit by digit (not whole) per user instruction!
     */
    fun onBackspace() {
        _uiState.update { state ->
            if (state.isCalculated) {
                state.copy(isCalculated = false, finalResult = null, errorMessage = null)
            } else {
                var expr = state.expression
                if (expr.isEmpty()) {
                    if (state.steps.isNotEmpty()) {
                        val lastStep = state.steps.last()
                        val remainingSteps = state.steps.dropLast(1)
                        return@update updateLivePreview(state.copy(
                            steps = remainingSteps,
                            expression = lastStep.expression,
                            finalResult = null,
                            isCalculated = false
                        ))
                    }
                    return@update state
                }

                val multiCharTokens = listOf(
                    "sin⁻¹(", "cos⁻¹(", "tan⁻¹(",
                    "sin(", "cos(", "tan(",
                    "log(", "ln(", "√(", "∛(",
                    "^(-1)",
                    " + ", " − ", " × ", " ÷ ", " ^ "
                )

                var matchedToken: String? = null
                for (token in multiCharTokens) {
                    if (expr.endsWith(token)) {
                        matchedToken = token
                        break
                    }
                }

                val newExpr = if (matchedToken != null) {
                    expr.substring(0, expr.length - matchedToken.length)
                } else {
                    expr.substring(0, expr.length - 1)
                }

                updateLivePreview(state.copy(
                    expression = newExpr,
                    errorMessage = null
                ))
            }
        }
    }

    /**
     * AC Key: Erases whole (expression, results, and active session) per user instruction!
     */
    fun onAllClear() {
        _uiState.update {
            it.copy(
                steps = emptyList(),
                expression = "",
                livePreview = null,
                finalResult = null,
                isCalculated = false,
                errorMessage = null
            )
        }
    }

    fun onEquals() {
        val currentState = _uiState.value
        val expr = currentState.expression.trim()
        if (expr.isEmpty()) return

        when (val eval = CalculatorEngine.evaluate(
            expr,
            currentState.isRadMode,
            precision = currentState.decimalPrecision,
            grouping = currentState.thousandsGrouping
        )) {
            is EvalResult.Success -> {
                val formatted = eval.formatted
                _uiState.update {
                    it.copy(
                        finalResult = formatted,
                        livePreview = null,
                        isCalculated = true,
                        errorMessage = null
                    )
                }
                viewModelScope.launch {
                    historyRepository.insert(
                        CalculationItem(
                            expression = expr,
                            result = formatted
                        )
                    )
                }
            }
            is EvalResult.Error -> {
                _uiState.update {
                    it.copy(
                        finalResult = eval.message,
                        errorMessage = eval.message,
                        isCalculated = true
                    )
                }
            }
        }
    }

    private fun updateLivePreview(state: CalculatorUiState): CalculatorUiState {
        var expr = state.expression.trim()
        if (expr.isEmpty() || state.isCalculated) {
            return state.copy(livePreview = null)
        }

        // If ends with operator (e.g. 6+6+ or 6*), strip trailing operator and evaluate so user sees live result
        var cleanExpr = expr
        while (cleanExpr.endsWith(" +") || cleanExpr.endsWith(" −") || cleanExpr.endsWith(" ×") || cleanExpr.endsWith(" ÷") || cleanExpr.endsWith(" ^")) {
            cleanExpr = cleanExpr.substring(0, cleanExpr.length - 2).trim()
        }
        if (cleanExpr.endsWith("+") || cleanExpr.endsWith("−") || cleanExpr.endsWith("×") || cleanExpr.endsWith("÷") || cleanExpr.endsWith("^") || cleanExpr.endsWith("(")) {
            cleanExpr = cleanExpr.substring(0, cleanExpr.length - 1).trim()
        }

        if (cleanExpr.isEmpty()) {
            return state.copy(livePreview = null)
        }

        return when (val eval = CalculatorEngine.evaluate(
            cleanExpr,
            state.isRadMode,
            precision = state.decimalPrecision,
            grouping = state.thousandsGrouping
        )) {
            is EvalResult.Success -> state.copy(livePreview = eval.formatted, errorMessage = null)
            is EvalResult.Error -> {
                // Only show error if expression is complete and has actual error like division by zero
                if (eval.message.contains("divide by 0", ignoreCase = true)) {
                    state.copy(livePreview = null, errorMessage = eval.message)
                } else {
                    state.copy(livePreview = null)
                }
            }
        }
    }

    // ==========================================
    // Currency Exchange Actions (Offline-first)
    // ==========================================

    fun onCurrencyDigit(digit: String) {
        _uiState.update { state ->
            val current = state.currencyAmount
            val newAmount = if (current == "0") digit else current + digit
            state.copy(currencyAmount = newAmount)
        }
        recalculateCurrency()
    }

    fun onCurrencyDecimal() {
        _uiState.update { state ->
            val current = state.currencyAmount
            val newAmount = if (!current.contains(".")) {
                if (current.isEmpty()) "0." else "$current."
            } else {
                current
            }
            state.copy(currencyAmount = newAmount)
        }
        recalculateCurrency()
    }

    fun onCurrencyBackspace() {
        _uiState.update { state ->
            val current = state.currencyAmount
            val newAmount = if (current.length <= 1) "0" else current.dropLast(1)
            state.copy(currencyAmount = newAmount)
        }
        recalculateCurrency()
    }

    fun onCurrencyClear() {
        _uiState.update { it.copy(currencyAmount = "0") }
        recalculateCurrency()
    }

    fun onSwapCurrencies() {
        _uiState.update { state ->
            state.copy(
                fromCurrency = state.toCurrency,
                toCurrency = state.fromCurrency
            )
        }
        recalculateCurrency()
    }

    fun openCurrencyPicker(forFrom: Boolean) {
        _uiState.update {
            it.copy(
                isCurrencyPickerOpen = true,
                isPickingForFromCurrency = forFrom,
                currencySearchQuery = ""
            )
        }
    }

    fun closeCurrencyPicker() {
        _uiState.update { it.copy(isCurrencyPickerOpen = false) }
    }

    fun onCurrencySearchQueryChange(query: String) {
        _uiState.update { it.copy(currencySearchQuery = query) }
    }

    fun onSelectCurrency(currencyCode: String) {
        _uiState.update { state ->
            if (state.isPickingForFromCurrency) {
                state.copy(fromCurrency = currencyCode, isCurrencyPickerOpen = false)
            } else {
                state.copy(toCurrency = currencyCode, isCurrencyPickerOpen = false)
            }
        }
        recalculateCurrency()
    }

    fun syncCurrencyRatesManually() {
        if (!currencyRepository.isOnline()) {
            _uiState.update {
                it.copy(snackbarMessage = "No internet connection. Saved rates remain available.")
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isCurrencySyncing = true) }
            val success = currencyRepository.syncRates(force = true)
            recalculateCurrency()
            _uiState.update {
                it.copy(
                    isCurrencySyncing = false,
                    snackbarMessage = if (success) "Exchange rates updated" else "Saved rates remain available."
                )
            }
        }
    }

    private fun recalculateCurrency() {
        val state = _uiState.value
        val amount = state.currencyAmount.toDoubleOrNull() ?: 0.0

        viewModelScope.launch {
            when (val result = currencyRepository.convertLocally(
                amount = amount,
                from = state.fromCurrency,
                to = state.toCurrency
            )) {
                is ConversionResult.Success -> {
                    val rate1 = if (result.rate > 0) {
                        val symbols = java.text.DecimalFormatSymbols(java.util.Locale.US)
                        val df = java.text.DecimalFormat("0.####", symbols)
                        df.format(result.rate)
                    } else "1"

                    _uiState.update {
                        it.copy(
                            currencyConvertedResult = result.formattedResult,
                            currencyRateInfo = "1 ${state.fromCurrency} = $rate1 ${state.toCurrency}",
                            currencyDateInfo = if (result.isCached) "Using cached rate • ${result.rateDate}" else "Live rate • ${result.rateDate}",
                            isCurrencyCached = result.isCached,
                            currencyErrorMessage = null
                        )
                    }
                }
                is ConversionResult.Error -> {
                    _uiState.update {
                        it.copy(
                            currencyConvertedResult = "—",
                            currencyRateInfo = "",
                            currencyDateInfo = "",
                            currencyErrorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    // ==========================================
    // History & Settings Actions
    // ==========================================

    fun toggleScientific() {
        _uiState.update {
            val newMode = if (it.appMode == AppMode.SCIENTIFIC) AppMode.STANDARD else AppMode.SCIENTIFIC
            it.copy(appMode = newMode)
        }
    }

    fun toggleRadMode() {
        _uiState.update { state ->
            val newRadMode = !state.isRadMode
            updateLivePreview(state.copy(isRadMode = newRadMode))
        }
    }

    fun toggleInverse() {
        _uiState.update { it.copy(isInvMode = !it.isInvMode) }
    }

    fun toggleHistory() {
        _uiState.update { it.copy(isHistoryVisible = !it.isHistoryVisible) }
    }

    fun setHistoryVisible(visible: Boolean) {
        _uiState.update { it.copy(isHistoryVisible = visible) }
    }

    fun toggleSettings() {
        _uiState.update { it.copy(isSettingsVisible = !it.isSettingsVisible) }
    }

    fun setSettingsVisible(visible: Boolean) {
        _uiState.update { it.copy(isSettingsVisible = visible) }
    }

    fun clearSessionSteps() {
        _uiState.update { it.copy(steps = emptyList()) }
    }

    fun setThemeMode(mode: String) {
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun setAccentColor(accent: String) {
        _uiState.update { it.copy(accentColor = accent) }
    }

    fun setHapticEnabled(enabled: Boolean) {
        _uiState.update { it.copy(hapticEnabled = enabled) }
    }

    fun setSoundEnabled(enabled: Boolean) {
        _uiState.update { it.copy(soundEnabled = enabled) }
    }

    fun setDecimalPrecision(precision: Int) {
        _uiState.update { state ->
            val newState = state.copy(decimalPrecision = precision)
            updateLivePreview(newState)
        }
    }

    fun setThousandsGrouping(grouping: String) {
        _uiState.update { state ->
            val newState = state.copy(thousandsGrouping = grouping)
            updateLivePreview(newState)
        }
    }

    fun onSelectHistoryItem(item: CalculationItem) {
        _uiState.update {
            it.copy(
                expression = item.expression,
                finalResult = item.result,
                isCalculated = true,
                livePreview = null,
                isHistoryVisible = false
            )
        }
    }

    fun onUseHistoryResult(result: String) {
        val cleanResult = result.replace(",", "")
        _uiState.update { state ->
            val expr = state.expression
            val newExpr = if (state.isCalculated || expr.isEmpty()) {
                cleanResult
            } else {
                val lastChar = expr.last()
                if (lastChar.isDigit() || lastChar == ')' || lastChar == 'π' || lastChar == 'e') {
                    "$expr × $cleanResult"
                } else {
                    "$expr$cleanResult"
                }
            }
            updateLivePreview(state.copy(
                expression = newExpr,
                isCalculated = false,
                isHistoryVisible = false
            ))
        }
    }

    fun onDeleteHistoryItem(id: Long) {
        viewModelScope.launch {
            historyRepository.deleteById(id)
        }
    }

    fun onConfirmClearHistory() {
        viewModelScope.launch {
            historyRepository.clearAll()
            _uiState.update { it.copy(showClearHistoryDialog = false) }
        }
    }

    fun setShowClearHistoryDialog(show: Boolean) {
        _uiState.update { it.copy(showClearHistoryDialog = show) }
    }

    fun setSnackbarMessage(message: String?) {
        _uiState.update { it.copy(snackbarMessage = message) }
    }

    private fun getLastNumberToken(expr: String): String {
        val delimiters = charArrayOf(' ', '+', '−', '×', '÷', '^', '(', ')')
        val lastIdx = expr.lastIndexOfAny(delimiters)
        return if (lastIdx == -1) expr else expr.substring(lastIdx + 1)
    }
}
