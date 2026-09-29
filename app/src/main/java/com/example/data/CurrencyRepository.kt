package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.model.CurrencyInfoEntity
import com.example.model.CurrencyRateEntity
import com.example.model.SyncMetadataEntity
import com.example.model.getCountryFlag
import com.example.model.getCurrencySymbol
import com.example.network.FrankfurterClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

sealed class ConversionResult {
    data class Success(
        val convertedAmount: Double,
        val formattedResult: String,
        val rate: Double,
        val rateDate: String,
        val isCached: Boolean
    ) : ConversionResult()

    data class Error(val message: String) : ConversionResult()
}

class CurrencyRepository(
    private val currencyDao: CurrencyDao,
    private val context: Context? = null
) {

    val allCurrencies: Flow<List<CurrencyInfoEntity>> = currencyDao.getAllCurrencies()

    suspend fun initializeIfNeeded() = withContext(Dispatchers.IO) {
        if (currencyDao.getCurrenciesCount() < 50) {
            seedDefaultCurrencies()
        }
        if (currencyDao.getRatesCount() < 50) {
            seedInitialRates()
        }
    }

    fun isOnline(): Boolean {
        if (context == null) return true
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun convertLocally(amount: Double, from: String, to: String): ConversionResult = withContext(Dispatchers.IO) {
        if (amount < 0) {
            return@withContext ConversionResult.Error("Amount must be positive")
        }

        // Same currency conversion (e.g. USD -> USD)
        if (from.equals(to, ignoreCase = true)) {
            val formatted = formatCurrencyAmount(amount, to)
            return@withContext ConversionResult.Success(
                convertedAmount = amount,
                formattedResult = formatted,
                rate = 1.0,
                rateDate = "Live",
                isCached = false
            )
        }

        var rateDate = ""
        var effectiveRate: Double? = null

        // 1. Direct rate
        val direct = currencyDao.getRate(from, to)
        if (direct != null && direct.rate > 0) {
            effectiveRate = direct.rate
            rateDate = direct.rateDate
        } else {
            // 2. Inverse rate
            val inverse = currencyDao.getRate(to, from)
            if (inverse != null && inverse.rate > 0) {
                effectiveRate = 1.0 / inverse.rate
                rateDate = inverse.rateDate
            } else {
                // 3. Bridge via EUR
                val fromEur = if (from == "EUR") 1.0 else currencyDao.getRate("EUR", from)?.rate
                val toEur = if (to == "EUR") 1.0 else currencyDao.getRate("EUR", to)?.rate

                if (fromEur != null && fromEur > 0 && toEur != null && toEur > 0) {
                    effectiveRate = toEur / fromEur
                    rateDate = currencyDao.getRate("EUR", to)?.rateDate ?: ""
                } else {
                    // 4. Bridge via USD
                    val fromUsd = if (from == "USD") 1.0 else currencyDao.getRate("USD", from)?.rate
                    val toUsd = if (to == "USD") 1.0 else currencyDao.getRate("USD", to)?.rate
                    if (fromUsd != null && fromUsd > 0 && toUsd != null && toUsd > 0) {
                        effectiveRate = toUsd / fromUsd
                        rateDate = currencyDao.getRate("USD", to)?.rateDate ?: ""
                    }
                }
            }
        }

        if (effectiveRate == null || effectiveRate <= 0.0) {
            return@withContext ConversionResult.Error("No saved rate available for $from → $to. Connect to the internet once to update.")
        }

        val converted = try {
            val bdAmount = BigDecimal(amount.toString())
            val bdRate = BigDecimal(effectiveRate.toString())
            val bdResult = bdAmount.multiply(bdRate, MathContext(12, RoundingMode.HALF_UP))
            bdResult.toDouble()
        } catch (e: Exception) {
            amount * effectiveRate
        }

        val formatted = formatCurrencyAmount(converted, to)
        ConversionResult.Success(
            convertedAmount = converted,
            formattedResult = formatted,
            rate = effectiveRate,
            rateDate = if (rateDate.isBlank()) "Recent" else rateDate,
            isCached = true
        )
    }

    suspend fun syncRates(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        try {
            // If offline, gracefully keep existing Room data without any error
            if (!isOnline()) {
                return@withContext false
            }

            val syncMeta = currencyDao.getMetadata(KEY_LAST_SYNC)
            val now = System.currentTimeMillis()
            val ONE_DAY_MS = 24 * 60 * 60 * 1000L

            if (!force && syncMeta != null && (now - syncMeta.timestamp) < ONE_DAY_MS) {
                return@withContext true // Already fresh within 24 hours
            }

            // Fetch currencies from Frankfurter
            try {
                val currenciesMap = FrankfurterClient.apiService.getCurrencies()
                val currencyEntities = currenciesMap.map { (code, name) ->
                    CurrencyInfoEntity(
                        code = code,
                        name = "${getCountryNameForCode(code)} • $name",
                        symbol = getCurrencySymbol(code),
                        flag = getCountryFlag(code)
                    )
                }
                if (currencyEntities.isNotEmpty()) {
                    currencyDao.insertCurrencies(currencyEntities)
                }
            } catch (_: Exception) {}

            // Fetch rates against EUR
            val ratesResponse = FrankfurterClient.apiService.getLatestRates(base = "EUR")
            val date = ratesResponse.date ?: "Today"
            val rateEntities = mutableListOf<CurrencyRateEntity>()

            rateEntities.add(
                CurrencyRateEntity(
                    baseCurrency = "EUR",
                    quoteCurrency = "EUR",
                    rate = 1.0,
                    rateDate = date
                )
            )

            for ((quote, rateVal) in ratesResponse.rates) {
                rateEntities.add(
                    CurrencyRateEntity(
                        baseCurrency = "EUR",
                        quoteCurrency = quote,
                        rate = rateVal,
                        rateDate = date
                    )
                )
            }

            currencyDao.insertRates(rateEntities)
            currencyDao.insertMetadata(
                SyncMetadataEntity(
                    key = KEY_LAST_SYNC,
                    value = date,
                    timestamp = now
                )
            )
            true
        } catch (e: Exception) {
            // Always keep existing Room rates intact on any network error
            false
        }
    }

    suspend fun getLastSyncMetadata(): SyncMetadataEntity? = withContext(Dispatchers.IO) {
        currencyDao.getMetadata(KEY_LAST_SYNC)
    }

    private suspend fun seedDefaultCurrencies() {
        val list = listOf(
            CurrencyInfoEntity("USD", "United States • US Dollar", "$", "🇺🇸"),
            CurrencyInfoEntity("EUR", "European Union • Euro", "€", "🇪🇺"),
            CurrencyInfoEntity("GBP", "United Kingdom • British Pound", "£", "🇬🇧"),
            CurrencyInfoEntity("INR", "India • Indian Rupee", "₹", "🇮🇳"),
            CurrencyInfoEntity("JPY", "Japan • Japanese Yen", "¥", "🇯🇵"),
            CurrencyInfoEntity("CAD", "Canada • Canadian Dollar", "CA$", "🇨🇦"),
            CurrencyInfoEntity("AUD", "Australia • Australian Dollar", "A$", "🇦🇺"),
            CurrencyInfoEntity("CHF", "Switzerland • Swiss Franc", "CHF", "🇨🇭"),
            CurrencyInfoEntity("CNY", "China • Chinese Yuan", "¥", "🇨🇳"),
            CurrencyInfoEntity("AED", "United Arab Emirates • UAE Dirham", "AED", "🇦🇪"),
            CurrencyInfoEntity("SAR", "Saudi Arabia • Saudi Riyal", "SAR", "🇸🇦"),
            CurrencyInfoEntity("SGD", "Singapore • Singapore Dollar", "S$", "🇸🇬"),
            CurrencyInfoEntity("KRW", "South Korea • South Korean Won", "₩", "🇰🇷"),
            CurrencyInfoEntity("BRL", "Brazil • Brazilian Real", "R$", "🇧🇷"),
            CurrencyInfoEntity("MXN", "Mexico • Mexican Peso", "MX$", "🇲🇽"),
            CurrencyInfoEntity("RUB", "Russia • Russian Ruble", "₽", "🇷🇺"),
            CurrencyInfoEntity("ZAR", "South Africa • South African Rand", "R", "🇿🇦"),
            CurrencyInfoEntity("TRY", "Turkey • Turkish Lira", "₺", "🇹🇷"),
            CurrencyInfoEntity("IDR", "Indonesia • Indonesian Rupiah", "Rp", "🇮🇩"),
            CurrencyInfoEntity("MYR", "Malaysia • Malaysian Ringgit", "RM", "🇲🇾"),
            CurrencyInfoEntity("PHP", "Philippines • Philippine Peso", "₱", "🇵🇭"),
            CurrencyInfoEntity("THB", "Thailand • Thai Baht", "฿", "🇹🇭"),
            CurrencyInfoEntity("VND", "Vietnam • Vietnamese Dong", "₫", "🇻🇳"),
            CurrencyInfoEntity("PKR", "Pakistan • Pakistani Rupee", "Rs", "🇵🇰"),
            CurrencyInfoEntity("BDT", "Bangladesh • Bangladeshi Taka", "৳", "🇧🇩"),
            CurrencyInfoEntity("EGP", "Egypt • Egyptian Pound", "E£", "🇪🇬"),
            CurrencyInfoEntity("NGN", "Nigeria • Nigerian Naira", "₦", "🇳🇬"),
            CurrencyInfoEntity("KES", "Kenya • Kenyan Shilling", "KSh", "🇰🇪"),
            CurrencyInfoEntity("NZD", "New Zealand • New Zealand Dollar", "NZ$", "🇳🇿"),
            CurrencyInfoEntity("SEK", "Sweden • Swedish Krona", "kr", "🇸🇪"),
            CurrencyInfoEntity("NOK", "Norway • Norwegian Krone", "kr", "🇳🇴"),
            CurrencyInfoEntity("DKK", "Denmark • Danish Krone", "kr", "🇩🇰"),
            CurrencyInfoEntity("PLN", "Poland • Polish Zloty", "zł", "🇵🇱"),
            CurrencyInfoEntity("CZK", "Czech Republic • Czech Koruna", "Kč", "🇨🇿"),
            CurrencyInfoEntity("HUF", "Hungary • Hungarian Forint", "Ft", "🇭🇺"),
            CurrencyInfoEntity("ILS", "Israel • Israeli New Shekel", "₪", "🇮🇱"),
            CurrencyInfoEntity("CLP", "Chile • Chilean Peso", "$", "🇨🇱"),
            CurrencyInfoEntity("COP", "Colombia • Colombian Peso", "$", "🇨🇴"),
            CurrencyInfoEntity("ARS", "Argentina • Argentine Peso", "$", "🇦🇷"),
            CurrencyInfoEntity("QAR", "Qatar • Qatari Riyal", "QR", "🇶🇦"),
            CurrencyInfoEntity("KWD", "Kuwait • Kuwaiti Dinar", "KD", "🇰🇼"),
            CurrencyInfoEntity("OMR", "Oman • Omani Rial", "OR", "🇴🇲"),
            CurrencyInfoEntity("BHD", "Bahrain • Bahraini Dinar", "BD", "🇧🇭"),
            CurrencyInfoEntity("HKD", "Hong Kong • Hong Kong Dollar", "HK$", "🇭🇰"),
            CurrencyInfoEntity("TWD", "Taiwan • New Taiwan Dollar", "NT$", "🇹🇼"),
            CurrencyInfoEntity("LKR", "Sri Lanka • Sri Lankan Rupee", "Rs", "🇱🇰"),
            CurrencyInfoEntity("NPR", "Nepal • Nepalese Rupee", "Rs", "🇳🇵"),
            CurrencyInfoEntity("GHS", "Ghana • Ghanaian Cedi", "GH₵", "🇬🇭"),
            CurrencyInfoEntity("MAD", "Morocco • Moroccan Dirham", "MAD", "🇲🇦"),
            CurrencyInfoEntity("DZD", "Algeria • Algerian Dinar", "DA", "🇩🇿"),
            CurrencyInfoEntity("PEN", "Peru • Peruvian Sol", "S/.", "🇵🇪"),
            CurrencyInfoEntity("RON", "Romania • Romanian Leu", "lei", "🇷🇴"),
            CurrencyInfoEntity("BGN", "Bulgaria • Bulgarian Lev", "лв", "🇧🇬"),
            CurrencyInfoEntity("ISK", "Iceland • Icelandic Krona", "kr", "🇮🇸"),
            CurrencyInfoEntity("UAH", "Ukraine • Ukrainian Hryvnia", "₴", "🇺🇦"),
            CurrencyInfoEntity("JOD", "Jordan • Jordanian Dinar", "JD", "🇯🇴"),
            CurrencyInfoEntity("IQD", "Iraq • Iraqi Dinar", "IQD", "🇮🇶")
        )
        currencyDao.insertCurrencies(list)
    }

    private suspend fun seedInitialRates() {
        val date = "2026-09-25"
        val rates = listOf(
            CurrencyRateEntity("EUR", "EUR", 1.0, date),
            CurrencyRateEntity("EUR", "USD", 1.085, date),
            CurrencyRateEntity("EUR", "INR", 90.50, date),
            CurrencyRateEntity("EUR", "GBP", 0.835, date),
            CurrencyRateEntity("EUR", "JPY", 158.40, date),
            CurrencyRateEntity("EUR", "CAD", 1.485, date),
            CurrencyRateEntity("EUR", "AUD", 1.625, date),
            CurrencyRateEntity("EUR", "CHF", 0.945, date),
            CurrencyRateEntity("EUR", "CNY", 7.650, date),
            CurrencyRateEntity("EUR", "AED", 3.985, date),
            CurrencyRateEntity("EUR", "SAR", 4.070, date),
            CurrencyRateEntity("EUR", "SGD", 1.420, date),
            CurrencyRateEntity("EUR", "KRW", 1460.0, date),
            CurrencyRateEntity("EUR", "BRL", 5.950, date),
            CurrencyRateEntity("EUR", "MXN", 21.20, date),
            CurrencyRateEntity("EUR", "RUB", 98.50, date),
            CurrencyRateEntity("EUR", "ZAR", 19.10, date),
            CurrencyRateEntity("EUR", "TRY", 37.20, date),
            CurrencyRateEntity("EUR", "IDR", 16800.0, date),
            CurrencyRateEntity("EUR", "MYR", 4.65, date),
            CurrencyRateEntity("EUR", "PHP", 61.20, date),
            CurrencyRateEntity("EUR", "THB", 35.80, date),
            CurrencyRateEntity("EUR", "VND", 26900.0, date),
            CurrencyRateEntity("EUR", "PKR", 302.0, date),
            CurrencyRateEntity("EUR", "BDT", 130.0, date),
            CurrencyRateEntity("EUR", "EGP", 53.0, date),
            CurrencyRateEntity("EUR", "NGN", 1750.0, date),
            CurrencyRateEntity("EUR", "KES", 140.0, date),
            CurrencyRateEntity("EUR", "NZD", 1.745, date),
            CurrencyRateEntity("EUR", "SEK", 11.20, date),
            CurrencyRateEntity("EUR", "NOK", 11.45, date),
            CurrencyRateEntity("EUR", "DKK", 7.46, date),
            CurrencyRateEntity("EUR", "PLN", 4.30, date),
            CurrencyRateEntity("EUR", "CZK", 25.10, date),
            CurrencyRateEntity("EUR", "HUF", 395.0, date),
            CurrencyRateEntity("EUR", "ILS", 4.05, date),
            CurrencyRateEntity("EUR", "CLP", 1010.0, date),
            CurrencyRateEntity("EUR", "COP", 4500.0, date),
            CurrencyRateEntity("EUR", "ARS", 1060.0, date),
            CurrencyRateEntity("EUR", "QAR", 3.95, date),
            CurrencyRateEntity("EUR", "KWD", 0.334, date),
            CurrencyRateEntity("EUR", "OMR", 0.418, date),
            CurrencyRateEntity("EUR", "BHD", 0.409, date),
            CurrencyRateEntity("EUR", "HKD", 8.45, date),
            CurrencyRateEntity("EUR", "TWD", 34.50, date),
            CurrencyRateEntity("EUR", "LKR", 325.0, date),
            CurrencyRateEntity("EUR", "NPR", 145.0, date),
            CurrencyRateEntity("EUR", "GHS", 17.20, date),
            CurrencyRateEntity("EUR", "MAD", 10.75, date),
            CurrencyRateEntity("EUR", "DZD", 145.0, date),
            CurrencyRateEntity("EUR", "PEN", 4.08, date),
            CurrencyRateEntity("EUR", "RON", 4.97, date),
            CurrencyRateEntity("EUR", "BGN", 1.956, date),
            CurrencyRateEntity("EUR", "ISK", 148.0, date),
            CurrencyRateEntity("EUR", "UAH", 44.50, date),
            CurrencyRateEntity("EUR", "JOD", 0.769, date),
            CurrencyRateEntity("EUR", "IQD", 1420.0, date)
        )
        currencyDao.insertRates(rates)
    }

    private fun getCountryNameForCode(code: String): String {
        return when (code.uppercase()) {
            "USD" -> "United States"
            "EUR" -> "European Union"
            "GBP" -> "United Kingdom"
            "INR" -> "India"
            "JPY" -> "Japan"
            "CNY" -> "China"
            "CAD" -> "Canada"
            "AUD" -> "Australia"
            "CHF" -> "Switzerland"
            "AED" -> "United Arab Emirates"
            "SAR" -> "Saudi Arabia"
            "SGD" -> "Singapore"
            "KRW" -> "South Korea"
            "BRL" -> "Brazil"
            "MXN" -> "Mexico"
            "RUB" -> "Russia"
            "ZAR" -> "South Africa"
            "TRY" -> "Turkey"
            "IDR" -> "Indonesia"
            "MYR" -> "Malaysia"
            "PHP" -> "Philippines"
            "THB" -> "Thailand"
            "VND" -> "Vietnam"
            "PKR" -> "Pakistan"
            "BDT" -> "Bangladesh"
            "EGP" -> "Egypt"
            "NGN" -> "Nigeria"
            "KES" -> "Kenya"
            "NZD" -> "New Zealand"
            "SEK" -> "Sweden"
            "NOK" -> "Norway"
            "DKK" -> "Denmark"
            "PLN" -> "Poland"
            "CZK" -> "Czech Republic"
            "HUF" -> "Hungary"
            "ILS" -> "Israel"
            "CLP" -> "Chile"
            "COP" -> "Colombia"
            "ARS" -> "Argentina"
            "QAR" -> "Qatar"
            "KWD" -> "Kuwait"
            "OMR" -> "Oman"
            "BHD" -> "Bahrain"
            "HKD" -> "Hong Kong"
            "TWD" -> "Taiwan"
            "LKR" -> "Sri Lanka"
            "NPR" -> "Nepal"
            "GHS" -> "Ghana"
            "MAD" -> "Morocco"
            "DZD" -> "Algeria"
            "PEN" -> "Peru"
            "RON" -> "Romania"
            "BGN" -> "Bulgaria"
            "ISK" -> "Iceland"
            "UAH" -> "Ukraine"
            "JOD" -> "Jordan"
            "IQD" -> "Iraq"
            else -> code
        }
    }

    private fun formatCurrencyAmount(amount: Double, currencyCode: String): String {
        val symbols = DecimalFormatSymbols(Locale.US)
        val pattern = when (currencyCode.uppercase()) {
            "JPY", "KRW", "IDR", "VND", "HUF", "CLP", "PYG", "IQD" -> "#,##0"
            else -> "#,##0.00"
        }
        val df = DecimalFormat(pattern, symbols)
        return df.format(amount)
    }

    companion object {
        const val KEY_LAST_SYNC = "last_successful_sync"
    }
}
