package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.ConversionResult
import com.example.data.CurrencyRepository
import com.example.engine.CalculatorEngine
import com.example.engine.EvalResult
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
  fun `test standard calculations in engine`() {
    val res1 = CalculatorEngine.evaluate("2 + 2", isRadMode = false)
    assertTrue(res1 is EvalResult.Success)
    assertEquals("4", (res1 as EvalResult.Success).formatted)

    val res2 = CalculatorEngine.evaluate("10 × (5 + 3) ÷ 4", isRadMode = false)
    assertTrue(res2 is EvalResult.Success)
    assertEquals("20", (res2 as EvalResult.Success).formatted)

    val res3 = CalculatorEngine.evaluate("50 + 10%", isRadMode = false)
    assertTrue(res3 is EvalResult.Success)
    assertEquals("50.1", (res3 as EvalResult.Success).formatted)
  }

  @Test
  fun `test long digits calculation without errors`() {
    val longExpr = "123456789 + 987654321"
    val res = CalculatorEngine.evaluate(longExpr, isRadMode = false)
    assertTrue(res is EvalResult.Success)
    assertEquals("1,111,111,110", (res as EvalResult.Success).formatted)
  }

  @Test
  fun `test scientific calculations in engine`() {
    val sinDeg = CalculatorEngine.evaluate("sin(90)", isRadMode = false)
    assertTrue(sinDeg is EvalResult.Success)
    assertEquals("1", (sinDeg as EvalResult.Success).formatted)

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
  fun `test precision and grouping`() {
    val resPrec = CalculatorEngine.evaluate("10 ÷ 3", isRadMode = false, precision = 2)
    assertTrue(resPrec is EvalResult.Success)
    assertEquals("3.33", (resPrec as EvalResult.Success).formatted)

    val resSpace = CalculatorEngine.evaluate("1000 × 1000", isRadMode = false, grouping = "SPACE")
    assertTrue(resSpace is EvalResult.Success)
    assertEquals("1 000 000", (resSpace as EvalResult.Success).formatted)
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

    // 0.01 USD -> INR
    val smallRes = repo.convertLocally(0.01, "USD", "INR")
    assertTrue(smallRes is ConversionResult.Success)

    // Large value
    val largeRes = repo.convertLocally(1_000_000.0, "USD", "EUR")
    assertTrue(largeRes is ConversionResult.Success)

    // Other country: AED -> INR
    val aedRes = repo.convertLocally(100.0, "AED", "INR")
    assertTrue(aedRes is ConversionResult.Success)
  }
}
