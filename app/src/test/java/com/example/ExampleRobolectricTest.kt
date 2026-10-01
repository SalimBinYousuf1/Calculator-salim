package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.ConversionResult
import com.example.data.CurrencyRepository
import com.example.engine.CalculatorEngine
import com.example.engine.EvalResult
import com.example.model.UnitCategory
import com.example.model.UnitRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Salim", appName)
  }

  @Test
  fun `test basic arithmetic`() {
    val resAdd = CalculatorEngine.evaluate("1 + 1", isRadMode = false)
    assertTrue(resAdd is EvalResult.Success)
    assertEquals("2", (resAdd as EvalResult.Success).formatted)

    val resSub = CalculatorEngine.evaluate("10 − 5", isRadMode = false)
    assertTrue(resSub is EvalResult.Success)
    assertEquals("5", (resSub as EvalResult.Success).formatted)

    val resMul = CalculatorEngine.evaluate("4 × 5", isRadMode = false)
    assertTrue(resMul is EvalResult.Success)
    assertEquals("20", (resMul as EvalResult.Success).formatted)

    val resDiv = CalculatorEngine.evaluate("20 ÷ 4", isRadMode = false)
    assertTrue(resDiv is EvalResult.Success)
    assertEquals("5", (resDiv as EvalResult.Success).formatted)
  }

  @Test
  fun `test operator precedence`() {
    val res1 = CalculatorEngine.evaluate("2 + 3 × 4", isRadMode = false)
    assertTrue(res1 is EvalResult.Success)
    assertEquals("14", (res1 as EvalResult.Success).formatted)

    val res2 = CalculatorEngine.evaluate("20 ÷ 5 + 3", isRadMode = false)
    assertTrue(res2 is EvalResult.Success)
    assertEquals("7", (res2 as EvalResult.Success).formatted)
  }

  @Test
  fun `test parentheses and nested parentheses`() {
    val res1 = CalculatorEngine.evaluate("(2 + 3) × 4", isRadMode = false)
    assertTrue(res1 is EvalResult.Success)
    assertEquals("20", (res1 as EvalResult.Success).formatted)

    val res2 = CalculatorEngine.evaluate("2 × (3 + 4)", isRadMode = false)
    assertTrue(res2 is EvalResult.Success)
    assertEquals("14", (res2 as EvalResult.Success).formatted)

    val res3 = CalculatorEngine.evaluate("((2 + 3) × 4) + 5", isRadMode = false)
    assertTrue(res3 is EvalResult.Success)
    assertEquals("25", (res3 as EvalResult.Success).formatted)
  }

  @Test
  fun `test decimal arithmetic and precision`() {
    // Strips IEEE floating noise: 0.1 + 0.2 = 0.3
    val resDec = CalculatorEngine.evaluate("0.1 + 0.2", isRadMode = false)
    assertTrue(resDec is EvalResult.Success)
    assertEquals("0.3", (resDec as EvalResult.Success).formatted)

    val resMul = CalculatorEngine.evaluate("1.5 × 2", isRadMode = false)
    assertTrue(resMul is EvalResult.Success)
    assertEquals("3", (resMul as EvalResult.Success).formatted)

    val resDot = CalculatorEngine.evaluate(".5 + .5", isRadMode = false)
    assertTrue(resDot is EvalResult.Success)
    assertEquals("1", (resDot as EvalResult.Success).formatted)
  }

  @Test
  fun `test negative numbers and unary minus`() {
    val res1 = CalculatorEngine.evaluate("-5", isRadMode = false)
    assertTrue(res1 is EvalResult.Success)
    assertEquals("-5", (res1 as EvalResult.Success).formatted)

    val res2 = CalculatorEngine.evaluate("-5 × -2", isRadMode = false)
    assertTrue(res2 is EvalResult.Success)
    assertEquals("10", (res2 as EvalResult.Success).formatted)

    val res3 = CalculatorEngine.evaluate("5 + -2", isRadMode = false)
    assertTrue(res3 is EvalResult.Success)
    assertEquals("3", (res3 as EvalResult.Success).formatted)
  }

  @Test
  fun `test OEM percentage behavior`() {
    // 50 + 10% = 55 (i.e. 50 + 10% of 50)
    val resAddPercent = CalculatorEngine.evaluate("50 + 10%", isRadMode = false)
    assertTrue(resAddPercent is EvalResult.Success)
    assertEquals("55", (resAddPercent as EvalResult.Success).formatted)

    // 100 - 10% = 90
    val resSubPercent = CalculatorEngine.evaluate("100 − 10%", isRadMode = false)
    assertTrue(resSubPercent is EvalResult.Success)
    assertEquals("90", (resSubPercent as EvalResult.Success).formatted)

    // 100 * 10% = 10
    val resMulPercent = CalculatorEngine.evaluate("100 × 10%", isRadMode = false)
    assertTrue(resMulPercent is EvalResult.Success)
    assertEquals("10", (resMulPercent as EvalResult.Success).formatted)

    // Standalone 25% = 0.25
    val resStandalone = CalculatorEngine.evaluate("25%", isRadMode = false)
    assertTrue(resStandalone is EvalResult.Success)
    assertEquals("0.25", (resStandalone as EvalResult.Success).formatted)
  }

  @Test
  fun `test scientific calculations`() {
    val sinDeg = CalculatorEngine.evaluate("sin(90)", isRadMode = false)
    assertTrue(sinDeg is EvalResult.Success)
    assertEquals("1", (sinDeg as EvalResult.Success).formatted)

    val cosDeg = CalculatorEngine.evaluate("cos(0)", isRadMode = false)
    assertTrue(cosDeg is EvalResult.Success)
    assertEquals("1", (cosDeg as EvalResult.Success).formatted)

    val sqrtVal = CalculatorEngine.evaluate("√(144)", isRadMode = false)
    assertTrue(sqrtVal is EvalResult.Success)
    assertEquals("12", (sqrtVal as EvalResult.Success).formatted)

    val factorialVal = CalculatorEngine.evaluate("5!", isRadMode = false)
    assertTrue(factorialVal is EvalResult.Success)
    assertEquals("120", (factorialVal as EvalResult.Success).formatted)
  }

  @Test
  fun `test division by zero handling`() {
    val divZero = CalculatorEngine.evaluate("10 ÷ 0", isRadMode = false)
    assertTrue(divZero is EvalResult.Error)
    assertEquals("Cannot divide by 0", (divZero as EvalResult.Error).message)
  }

  @Test
  fun `test long digits calculation without errors`() {
    val longExpr = "123456789 + 987654321"
    val res = CalculatorEngine.evaluate(longExpr, isRadMode = false)
    assertTrue(res is EvalResult.Success)
    assertEquals("1,111,111,110", (res as EvalResult.Success).formatted)
  }

  @Test
  fun `test unit conversions`() {
    // Length: 1 meter to feet = ~3.28084
    val mToFt = UnitRegistry.convert(1.0, "m", "ft", UnitCategory.LENGTH)
    assertTrue(Math.abs(mToFt - 3.28084) < 0.001)

    // Mass: 1 kg to lb = ~2.20462
    val kgToLb = UnitRegistry.convert(1.0, "kg", "lb", UnitCategory.MASS)
    assertTrue(Math.abs(kgToLb - 2.20462) < 0.001)

    // Temperature: 100 Celsius to Fahrenheit = 212
    val cToF = UnitRegistry.convert(100.0, "C", "F", UnitCategory.TEMPERATURE)
    assertEquals(212.0, cToF, 0.001)

    // Temperature: 0 Celsius to Kelvin = 273.15
    val cToK = UnitRegistry.convert(0.0, "C", "K", UnitCategory.TEMPERATURE)
    assertEquals(273.15, cToK, 0.001)

    // Data: 1 GB to MB = 1024 MB
    val gbToMb = UnitRegistry.convert(1.0, "GB", "MB", UnitCategory.DATA)
    assertEquals(1024.0, gbToMb, 0.001)
  }

  @Test
  fun `test currency offline conversion in repository`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val database = AppDatabase.getDatabase(context)
    val repo = CurrencyRepository(database.currencyDao(), context)
    repo.initializeIfNeeded()

    // Same currency: USD -> USD
    val sameRes = repo.convertLocally(100.0, "USD", "USD")
    assertTrue(sameRes is ConversionResult.Success)
    assertEquals("100.00", (sameRes as ConversionResult.Success).formattedResult)

    // 0 amount: 0 USD -> INR
    val zeroRes = repo.convertLocally(0.0, "USD", "INR")
    assertTrue(zeroRes is ConversionResult.Success)
    assertEquals("0.00", (zeroRes as ConversionResult.Success).formattedResult)

    // 100 USD -> INR with cached rates
    val convRes = repo.convertLocally(100.0, "USD", "INR")
    assertTrue(convRes is ConversionResult.Success)
    val successConv = convRes as ConversionResult.Success
    assertTrue(successConv.convertedAmount > 0)
    assertTrue(successConv.isCached)

    // Reverse: INR -> USD
    val revRes = repo.convertLocally(successConv.convertedAmount, "INR", "USD")
    assertTrue(revRes is ConversionResult.Success)
    val revSuccess = revRes as ConversionResult.Success
    assertTrue(Math.abs(revSuccess.convertedAmount - 100.0) < 0.1)

    // Other country: AED -> INR
    val aedRes = repo.convertLocally(100.0, "AED", "INR")
    assertTrue(aedRes is ConversionResult.Success)
  }
}
