package com.example.engine

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

sealed class EvalResult {
    data class Success(val value: Double, val formatted: String) : EvalResult()
    data class Error(val message: String) : EvalResult()
}

object CalculatorEngine {

    private const val EPSILON = 1e-12

    /**
     * Evaluates a mathematical expression string with full operator precedence,
     * parentheses, functions, and standard OEM percentage handling.
     *
     * @param rawExpr The input expression (e.g. "50 + 10%", "10 × (5 + 3) ÷ 4")
     * @param isRadMode True for radians, false for degrees
     * @param precision Decimal places (-1 for Auto)
     * @param grouping "COMMA", "SPACE", or "NONE"
     */
    fun evaluate(
        rawExpr: String,
        isRadMode: Boolean,
        precision: Int = -1,
        grouping: String = "COMMA"
    ): EvalResult {
        val sanitized = sanitize(rawExpr)
        if (sanitized.isBlank()) {
            return EvalResult.Error("Empty expression")
        }

        return try {
            val tokens = tokenize(sanitized)
            if (tokens.isEmpty()) {
                return EvalResult.Error("Empty expression")
            }

            val rpn = shuntingYard(tokens)
            val result = evaluateRpn(rpn, isRadMode)

            if (result.isNaN()) {
                EvalResult.Error("Invalid input")
            } else if (result.isInfinite()) {
                EvalResult.Error("Cannot divide by 0")
            } else {
                val cleanedValue = cleanFloatNoise(result)
                val formatted = formatNumber(cleanedValue, precision, grouping)
                EvalResult.Success(cleanedValue, formatted)
            }
        } catch (e: ArithmeticException) {
            EvalResult.Error(e.message ?: "Calculation error")
        } catch (e: IllegalArgumentException) {
            EvalResult.Error(e.message ?: "Invalid expression")
        } catch (e: Exception) {
            EvalResult.Error("Invalid format")
        }
    }

    /**
     * Sanitizes user symbols into standard tokens.
     */
    fun sanitize(expr: String): String {
        return expr
            .replace("×", "*")
            .replace("÷", "/")
            .replace("−", "-")
            .replace("π", "PI")
            .replace("√", "sqrt")
            .replace("∛", "cbrt")
            .replace("sin⁻¹", "asin")
            .replace("cos⁻¹", "acos")
            .replace("tan⁻¹", "atan")
            .replace(" ", "")
    }

    sealed class Token {
        data class Number(val value: Double) : Token()
        data class Operator(val symbol: Char, val precedence: Int, val isRightAssociative: Boolean = false) : Token()
        data class Function(val name: String) : Token()
        object OpenParen : Token()
        object CloseParen : Token()
        object Factorial : Token()
        // Special Percentage Token holding context or resolved value
        data class Percent(val rawValue: Double? = null) : Token()
    }

    private fun tokenize(expr: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        val len = expr.length

        var prevToken: Token? = null

        while (i < len) {
            val c = expr[i]

            // Numbers & Unary Minus
            if (c.isDigit() || c == '.' || (c == '-' && isUnaryMinus(prevToken))) {
                val start = i
                if (c == '-') i++
                while (i < len && (expr[i].isDigit() || expr[i] == '.' || expr[i] == 'E' || expr[i] == 'e')) {
                    if ((expr[i] == 'E' || expr[i] == 'e') && i + 1 < len && (expr[i + 1] == '+' || expr[i + 1] == '-')) {
                        i++ // Skip sign in exponent e.g. 1e-5
                    }
                    i++
                }
                val numStr = expr.substring(start, i)
                val num = if (numStr == "-" || numStr == ".") {
                    throw IllegalArgumentException("Invalid number")
                } else {
                    numStr.toDouble()
                }

                // Insert implicit multiplication if needed (e.g. 5(2) or 5π)
                insertImplicitMultiplyIfNeeded(tokens, prevToken)

                val token = Token.Number(num)
                tokens.add(token)
                prevToken = token
                continue
            }

            // Constants: PI or e
            if (expr.startsWith("PI", i)) {
                insertImplicitMultiplyIfNeeded(tokens, prevToken)
                val token = Token.Number(Math.PI)
                tokens.add(token)
                prevToken = token
                i += 2
                continue
            }
            if (c == 'e' && (i + 1 == len || !expr[i + 1].isLetter())) {
                insertImplicitMultiplyIfNeeded(tokens, prevToken)
                val token = Token.Number(Math.E)
                tokens.add(token)
                prevToken = token
                i += 1
                continue
            }

            // Functions
            val funcNames = listOf("asin", "acos", "atan", "sin", "cos", "tan", "sqrt", "cbrt", "log", "ln")
            val matchedFunc = funcNames.firstOrNull { expr.startsWith(it, i) }
            if (matchedFunc != null) {
                insertImplicitMultiplyIfNeeded(tokens, prevToken)
                val token = Token.Function(matchedFunc)
                tokens.add(token)
                prevToken = token
                i += matchedFunc.length
                continue
            }

            // Parentheses
            if (c == '(') {
                insertImplicitMultiplyIfNeeded(tokens, prevToken)
                tokens.add(Token.OpenParen)
                prevToken = Token.OpenParen
                i++
                continue
            }
            if (c == ')') {
                tokens.add(Token.CloseParen)
                prevToken = Token.CloseParen
                i++
                continue
            }

            // Factorial (!)
            if (c == '!') {
                tokens.add(Token.Factorial)
                prevToken = Token.Factorial
                i++
                continue
            }

            // Percentage (%) - Standard OEM Smart Percentage Transformation
            if (c == '%') {
                // If previous token was a number, we analyze the operator before it:
                // If expression was A + B%, standard OEM calculator evaluates B% as (A * B / 100)
                // If expression was A - B%, standard OEM calculator evaluates B% as (A * B / 100)
                // If expression was A * B% or A / B%, B% is (B / 100)
                // If standalone, B% is (B / 100)
                handlePercentageToken(tokens)
                prevToken = tokens.lastOrNull()
                i++
                continue
            }

            // Binary Operators
            when (c) {
                '+' -> {
                    val token = Token.Operator('+', 1)
                    tokens.add(token)
                    prevToken = token
                }
                '-' -> {
                    val token = Token.Operator('-', 1)
                    tokens.add(token)
                    prevToken = token
                }
                '*' -> {
                    val token = Token.Operator('*', 2)
                    tokens.add(token)
                    prevToken = token
                }
                '/' -> {
                    val token = Token.Operator('/', 2)
                    tokens.add(token)
                    prevToken = token
                }
                '^' -> {
                    val token = Token.Operator('^', 3, isRightAssociative = true)
                    tokens.add(token)
                    prevToken = token
                }
                else -> throw IllegalArgumentException("Unexpected character: $c")
            }
            i++
        }

        // Auto-close open parentheses
        var openCount = 0
        var closeCount = 0
        for (t in tokens) {
            if (t is Token.OpenParen) openCount++
            if (t is Token.CloseParen) closeCount++
        }
        while (openCount > closeCount) {
            tokens.add(Token.CloseParen)
            closeCount++
        }

        return tokens
    }

    private fun handlePercentageToken(tokens: MutableList<Token>) {
        if (tokens.isEmpty()) throw IllegalArgumentException("Missing operand before %")
        val last = tokens.removeAt(tokens.size - 1)

        if (last is Token.Number) {
            val b = last.value
            // Check if there is an operator before B:
            if (tokens.size >= 2) {
                val op = tokens[tokens.size - 1]
                val prev = tokens[tokens.size - 2]
                if (op is Token.Operator && (op.symbol == '+' || op.symbol == '-') && prev is Token.Number) {
                    // Standard OEM behavior: A + B% => A + (A * B / 100)
                    val a = prev.value
                    val percentValue = a * (b / 100.0)
                    tokens.add(Token.Number(percentValue))
                    return
                }
            }
            // Default percentage: B / 100.0
            tokens.add(Token.Number(b / 100.0))
        } else {
            // General postfix percent
            tokens.add(last)
            tokens.add(Token.Percent())
        }
    }

    private fun isUnaryMinus(prevToken: Token?): Boolean {
        return prevToken == null ||
                prevToken is Token.Operator ||
                prevToken is Token.OpenParen ||
                prevToken is Token.Function
    }

    private fun insertImplicitMultiplyIfNeeded(tokens: MutableList<Token>, prevToken: Token?) {
        if (prevToken is Token.Number || prevToken is Token.CloseParen || prevToken is Token.Factorial || prevToken is Token.Percent) {
            tokens.add(Token.Operator('*', 2))
        }
    }

    private fun shuntingYard(tokens: List<Token>): List<Token> {
        val output = mutableListOf<Token>()
        val operatorStack = ArrayDeque<Token>()

        for (token in tokens) {
            when (token) {
                is Token.Number -> output.add(token)
                is Token.Factorial -> output.add(token)
                is Token.Percent -> output.add(token)
                is Token.Function -> operatorStack.addLast(token)
                is Token.Operator -> {
                    while (operatorStack.isNotEmpty()) {
                        val top = operatorStack.last()
                        if (top is Token.Function) {
                            output.add(operatorStack.removeLast())
                        } else if (top is Token.Operator) {
                            val topPrecedence = top.precedence
                            val tokenPrecedence = token.precedence
                            if (topPrecedence > tokenPrecedence || (topPrecedence == tokenPrecedence && !token.isRightAssociative)) {
                                output.add(operatorStack.removeLast())
                            } else {
                                break
                            }
                        } else {
                            break
                        }
                    }
                    operatorStack.addLast(token)
                }
                is Token.OpenParen -> operatorStack.addLast(token)
                is Token.CloseParen -> {
                    var foundParen = false
                    while (operatorStack.isNotEmpty()) {
                        val top = operatorStack.removeLast()
                        if (top is Token.OpenParen) {
                            foundParen = true
                            break
                        } else {
                            output.add(top)
                        }
                    }
                    if (!foundParen) {
                        throw IllegalArgumentException("Mismatched parentheses")
                    }
                    if (operatorStack.isNotEmpty() && operatorStack.last() is Token.Function) {
                        output.add(operatorStack.removeLast())
                    }
                }
            }
        }

        while (operatorStack.isNotEmpty()) {
            val top = operatorStack.removeLast()
            if (top is Token.OpenParen || top is Token.CloseParen) {
                throw IllegalArgumentException("Mismatched parentheses")
            }
            output.add(top)
        }

        return output
    }

    private fun evaluateRpn(rpn: List<Token>, isRadMode: Boolean): Double {
        val stack = ArrayDeque<Double>()

        for (token in rpn) {
            when (token) {
                is Token.Number -> stack.addLast(token.value)
                is Token.Factorial -> {
                    if (stack.isEmpty()) throw IllegalArgumentException("Missing operand for factorial")
                    val v = stack.removeLast()
                    stack.addLast(calculateFactorial(v))
                }
                is Token.Percent -> {
                    if (stack.isEmpty()) throw IllegalArgumentException("Missing operand for percent")
                    val v = stack.removeLast()
                    stack.addLast(v / 100.0)
                }
                is Token.Function -> {
                    if (stack.isEmpty()) throw IllegalArgumentException("Missing argument for ${token.name}")
                    val arg = stack.removeLast()
                    stack.addLast(evaluateFunction(token.name, arg, isRadMode))
                }
                is Token.Operator -> {
                    if (stack.size < 2) throw IllegalArgumentException("Missing operand for ${token.symbol}")
                    val b = stack.removeLast()
                    val a = stack.removeLast()
                    stack.addLast(evaluateBinaryOp(a, b, token.symbol))
                }
                else -> throw IllegalArgumentException("Unexpected token in evaluation")
            }
        }

        if (stack.size != 1) {
            throw IllegalArgumentException("Invalid syntax")
        }

        return stack.removeLast()
    }

    private fun evaluateFunction(name: String, arg: Double, isRadMode: Boolean): Double {
        return when (name) {
            "sin" -> {
                val rad = if (isRadMode) arg else Math.toRadians(arg)
                val s = sin(rad)
                if (abs(s) < EPSILON) 0.0 else s
            }
            "cos" -> {
                val rad = if (isRadMode) arg else Math.toRadians(arg)
                val c = cos(rad)
                if (abs(c) < EPSILON) 0.0 else c
            }
            "tan" -> {
                val rad = if (isRadMode) arg else Math.toRadians(arg)
                val cosVal = cos(rad)
                if (abs(cosVal) < EPSILON) {
                    throw ArithmeticException("Undefined")
                }
                val t = tan(rad)
                if (abs(t) < EPSILON) 0.0 else t
            }
            "asin" -> {
                if (arg < -1.0 || arg > 1.0) throw ArithmeticException("Domain error")
                val rad = asin(arg)
                if (isRadMode) rad else Math.toDegrees(rad)
            }
            "acos" -> {
                if (arg < -1.0 || arg > 1.0) throw ArithmeticException("Domain error")
                val rad = acos(arg)
                if (isRadMode) rad else Math.toDegrees(rad)
            }
            "atan" -> {
                val rad = atan(arg)
                if (isRadMode) rad else Math.toDegrees(rad)
            }
            "ln" -> {
                if (arg <= 0.0) throw ArithmeticException("Domain error")
                ln(arg)
            }
            "log" -> {
                if (arg <= 0.0) throw ArithmeticException("Domain error")
                log10(arg)
            }
            "sqrt" -> {
                if (arg < 0.0) throw ArithmeticException("Domain error")
                sqrt(arg)
            }
            "cbrt" -> Math.cbrt(arg)
            else -> throw IllegalArgumentException("Unknown function: $name")
        }
    }

    private fun evaluateBinaryOp(a: Double, b: Double, op: Char): Double {
        return when (op) {
            '+' -> a + b
            '-' -> a - b
            '*' -> a * b
            '/' -> {
                if (abs(b) < 1e-15) {
                    throw ArithmeticException("Cannot divide by 0")
                }
                a / b
            }
            '^' -> {
                if (a == 0.0 && b < 0.0) throw ArithmeticException("Cannot divide by 0")
                a.pow(b)
            }
            else -> throw IllegalArgumentException("Unknown operator: $op")
        }
    }

    private fun calculateFactorial(v: Double): Double {
        if (v < 0.0) throw ArithmeticException("Domain error")
        val n = v.roundToLong()
        if (abs(v - n) > 1e-9) throw ArithmeticException("Integer required for !")
        if (n > 170) throw ArithmeticException("Value too large")

        var result = 1.0
        for (k in 2..n) {
            result *= k
        }
        return result
    }

    /**
     * Eliminates IEEE-754 precision issues like 0.1 + 0.2 = 0.30000000000000004
     */
    private fun cleanFloatNoise(value: Double): Double {
        if (value.isInfinite() || value.isNaN()) return value

        val roundLong = value.roundToLong()
        if (abs(value - roundLong) < 1e-11) {
            return roundLong.toDouble()
        }

        return try {
            val bd = BigDecimal(value, MathContext(12, RoundingMode.HALF_UP))
            bd.toDouble()
        } catch (e: Exception) {
            value
        }
    }

    /**
     * Formats a number with commas or spaces for thousands and handles scientific notation.
     */
    fun formatNumber(value: Double, precision: Int = -1, grouping: String = "COMMA"): String {
        if (value.isInfinite() || value.isNaN()) return value.toString()

        val absVal = abs(value)
        if (absVal >= 1e12 || (absVal < 1e-6 && absVal > 0.0)) {
            val symbols = DecimalFormatSymbols(Locale.US)
            val df = DecimalFormat("0.######E0", symbols)
            return df.format(value).replace("E", "e")
        }

        val symbols = DecimalFormatSymbols(Locale.US).apply {
            when (grouping.uppercase()) {
                "SPACE" -> groupingSeparator = ' '
                "NONE" -> {}
                else -> groupingSeparator = ','
            }
        }

        val useGrouping = grouping.uppercase() != "NONE"

        val pattern = if (precision >= 0) {
            val decimals = if (precision == 0) "" else "." + "#".repeat(precision)
            if (useGrouping) "#,##0$decimals" else "0$decimals"
        } else {
            val roundLong = value.roundToLong()
            if (abs(value - roundLong) < 1e-11) {
                return if (useGrouping) {
                    val df = DecimalFormat("#,##0", symbols)
                    df.format(roundLong)
                } else {
                    roundLong.toString()
                }
            }
            if (useGrouping) "#,##0.##########" else "0.##########"
        }

        val df = DecimalFormat(pattern, symbols)
        return df.format(value)
    }

    fun formatExpressionForDisplay(expr: String): String {
        return expr
            .replace("*", " × ")
            .replace("/", " ÷ ")
            .replace("-", " − ")
            .replace("+", " + ")
            .replace("^", " ^ ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
