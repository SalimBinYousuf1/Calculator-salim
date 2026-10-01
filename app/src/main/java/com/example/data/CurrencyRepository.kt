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
        val count = currencyDao.getCurrenciesCount()
        if (count < 100) {
            seedDefaultCurrencies()
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
            // Fallback gracefully without app crash
            return@withContext ConversionResult.Error("No rate available for $from → $to")
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
            if (!isOnline()) {
                return@withContext false
            }

            val syncMeta = currencyDao.getMetadata(KEY_LAST_SYNC)
            val now = System.currentTimeMillis()
            val oneDayMillis = 24 * 60 * 60 * 1000L

            if (!force && syncMeta != null) {
                val lastSyncTime = syncMeta.timestamp
                if (now - lastSyncTime < oneDayMillis) {
                    return@withContext true
                }
            }

            val response = FrankfurterClient.api.getLatestRates("EUR")
            val base = response.base ?: "EUR"
            val date = response.date ?: ""
            val rates = response.rates

            val rateEntities = mutableListOf<CurrencyRateEntity>()
            rateEntities.add(CurrencyRateEntity(base, base, 1.0, date))

            for ((quote, rate) in rates) {
                rateEntities.add(CurrencyRateEntity(base, quote, rate, date))
            }

            currencyDao.insertRates(rateEntities)

            // Try to sync currency names if available
            try {
                val currenciesMap = FrankfurterClient.api.getCurrencies()
                val updatedCurrencies = currenciesMap.map { (code, name) ->
                    val country = getCountryNameForCode(code)
                    val fullDisplayName = if (country.isNotBlank() && country != code) {
                        "$country • $name"
                    } else {
                        name
                    }
                    CurrencyInfoEntity(
                        code = code,
                        name = fullDisplayName,
                        symbol = getCurrencySymbol(code),
                        flag = getCountryFlag(code)
                    )
                }
                if (updatedCurrencies.isNotEmpty()) {
                    currencyDao.insertCurrencies(updatedCurrencies)
                }
            } catch (_: Exception) {
                // Non-fatal if currencies endpoint is unreachable
            }

            currencyDao.insertMetadata(
                SyncMetadataEntity(
                    key = KEY_LAST_SYNC,
                    value = date,
                    timestamp = now
                )
            )

            true
        } catch (_: Exception) {
            // Graceful fallback: Network error doesn't break the app; offline cache continues
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
            CurrencyInfoEntity("KES", "Kenya • Kenyan Shilling", "Sh", "🇰🇪"),
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
            CurrencyInfoEntity("IQD", "Iraq • Iraqi Dinar", "IQD", "🇮🇶"),
            CurrencyInfoEntity("AFN", "Afghanistan • Afghan Afghani", "؋", "🇦🇫"),
            CurrencyInfoEntity("ALL", "Albania • Albanian Lek", "L", "🇦🇱"),
            CurrencyInfoEntity("AMD", "Armenia • Armenian Dram", "֏", "🇦🇲"),
            CurrencyInfoEntity("AOA", "Angola • Angolan Kwanza", "Kz", "🇦🇴"),
            CurrencyInfoEntity("AZN", "Azerbaijan • Azerbaijani Manat", "₼", "🇦🇿"),
            CurrencyInfoEntity("BAM", "Bosnia & Herzegovina • Convertible Mark", "KM", "🇧🇦"),
            CurrencyInfoEntity("BBD", "Barbados • Barbadian Dollar", "$", "🇧🇧"),
            CurrencyInfoEntity("BIF", "Burundi • Burundian Franc", "Fr", "🇧🇮"),
            CurrencyInfoEntity("BND", "Brunei • Brunei Dollar", "$", "🇧🇳"),
            CurrencyInfoEntity("BOB", "Bolivia • Bolivian Boliviano", "Bs.", "🇧🇴"),
            CurrencyInfoEntity("BSD", "Bahamas • Bahamian Dollar", "$", "🇧🇸"),
            CurrencyInfoEntity("BTN", "Bhutan • Bhutanese Ngultrum", "Nu.", "🇧🇹"),
            CurrencyInfoEntity("BWP", "Botswana • Botswana Pula", "P", "🇧🇼"),
            CurrencyInfoEntity("BYN", "Belarus • Belarusian Ruble", "Br", "🇧🇾"),
            CurrencyInfoEntity("BZD", "Belize • Belize Dollar", "$", "🇧🇿"),
            CurrencyInfoEntity("CDF", "DR Congo • Congolese Franc", "Fr", "🇨🇩"),
            CurrencyInfoEntity("CRC", "Costa Rica • Costa Rican Colón", "₡", "🇨🇷"),
            CurrencyInfoEntity("CUP", "Cuba • Cuban Peso", "₱", "🇨🇺"),
            CurrencyInfoEntity("CVE", "Cape Verde • Cape Verdean Escudo", "$", "🇨🇻"),
            CurrencyInfoEntity("DJF", "Djibouti • Djiboutian Franc", "Fr", "🇩🇯"),
            CurrencyInfoEntity("DOP", "Dominican Republic • Dominican Peso", "RD$", "🇩🇴"),
            CurrencyInfoEntity("ERN", "Eritrea • Eritrean Nakfa", "Nfk", "🇪🇷"),
            CurrencyInfoEntity("ETB", "Ethiopia • Ethiopian Birr", "Br", "🇪🇹"),
            CurrencyInfoEntity("FJD", "Fiji • Fijian Dollar", "$", "🇫🇯"),
            CurrencyInfoEntity("GEL", "Georgia • Georgian Lari", "₾", "🇬🇪"),
            CurrencyInfoEntity("GMD", "Gambia • Gambian Dalasi", "D", "🇬🇲"),
            CurrencyInfoEntity("GNF", "Guinea • Guinean Franc", "Fr", "🇬🇳"),
            CurrencyInfoEntity("GTQ", "Guatemala • Guatemalan Quetzal", "Q", "🇬🇹"),
            CurrencyInfoEntity("GYD", "Guyana • Guyanese Dollar", "$", "🇬🇾"),
            CurrencyInfoEntity("HNL", "Honduras • Honduran Lempira", "L", "🇭🇳"),
            CurrencyInfoEntity("HTG", "Haiti • Haitian Gourde", "G", "🇭🇹"),
            CurrencyInfoEntity("JMD", "Jamaica • Jamaican Dollar", "$", "🇯🇲"),
            CurrencyInfoEntity("KGS", "Kyrgyzstan • Kyrgyzstani Som", "с", "🇰🇬"),
            CurrencyInfoEntity("KHR", "Cambodia • Cambodian Riel", "៛", "🇰🇭"),
            CurrencyInfoEntity("KMF", "Comoros • Comorian Franc", "Fr", "🇰🇲"),
            CurrencyInfoEntity("KZT", "Kazakhstan • Kazakhstani Tenge", "₸", "🇰🇿"),
            CurrencyInfoEntity("LAK", "Laos • Lao Kip", "₭", "🇱🇦"),
            CurrencyInfoEntity("LBP", "Lebanon • Lebanese Pound", "LL", "🇱🇧"),
            CurrencyInfoEntity("LRD", "Liberia • Liberian Dollar", "L$", "🇱🇷"),
            CurrencyInfoEntity("LSL", "Lesotho • Lesotho Loti", "L", "🇱🇸"),
            CurrencyInfoEntity("LYD", "Libya • Libyan Dinar", "LD", "🇱🇾"),
            CurrencyInfoEntity("MDL", "Moldova • Moldovan Leu", "L", "🇲🇩"),
            CurrencyInfoEntity("MGA", "Madagascar • Malagasy Ariary", "Ar", "🇲🇬"),
            CurrencyInfoEntity("MKD", "North Macedonia • Macedonian Denar", "ден", "🇲🇰"),
            CurrencyInfoEntity("MMK", "Myanmar • Myanmar Kyat", "K", "🇲🇲"),
            CurrencyInfoEntity("MNT", "Mongolia • Mongolian Tögrög", "₮", "🇲🇳"),
            CurrencyInfoEntity("MOP", "Macau • Macanese Pataca", "MOP$", "🇲🇴"),
            CurrencyInfoEntity("MRU", "Mauritania • Mauritanian Ouguiya", "UM", "🇲🇷"),
            CurrencyInfoEntity("MUR", "Mauritius • Mauritian Rupee", "Rs", "🇲🇺"),
            CurrencyInfoEntity("MVR", "Maldives • Maldivian Rufiyaa", "Rf", "🇲🇻"),
            CurrencyInfoEntity("MWK", "Malawi • Malawian Kwacha", "MK", "🇲🇼"),
            CurrencyInfoEntity("MZN", "Mozambique • Mozambican Metical", "MT", "🇲🇿"),
            CurrencyInfoEntity("NAD", "Namibia • Namibian Dollar", "$", "🇳🇦"),
            CurrencyInfoEntity("NIO", "Nicaragua • Nicaraguan Córdoba", "C$", "🇳🇮"),
            CurrencyInfoEntity("PAB", "Panama • Panamanian Balboa", "B/.", "🇵🇦"),
            CurrencyInfoEntity("PGK", "Papua New Guinea • Papua New Guinean Kina", "K", "🇵🇬"),
            CurrencyInfoEntity("PYG", "Paraguay • Paraguayan Guaraní", "₲", "🇵🇾"),
            CurrencyInfoEntity("RSD", "Serbia • Serbian Dinar", "дин", "🇷🇸"),
            CurrencyInfoEntity("RWF", "Rwanda • Rwandan Franc", "Fr", "🇷🇼"),
            CurrencyInfoEntity("SCR", "Seychelles • Seychellois Rupee", "Rs", "🇸🇨"),
            CurrencyInfoEntity("SDG", "Sudan • Sudanese Pound", "£", "🇸🇩"),
            CurrencyInfoEntity("SLE", "Sierra Leone • Sierra Leonean Leone", "Le", "🇸🇱"),
            CurrencyInfoEntity("SOS", "Somalia • Somali Shilling", "Sh", "🇸🇴"),
            CurrencyInfoEntity("SRD", "Suriname • Surinamese Dollar", "$", "🇸🇷"),
            CurrencyInfoEntity("SSP", "South Sudan • South Sudanese Pound", "£", "🇸🇸"),
            CurrencyInfoEntity("STN", "São Tomé & Príncipe • Dobra", "Db", "🇸🇹"),
            CurrencyInfoEntity("SYP", "Syria • Syrian Pound", "£S", "🇸🇾"),
            CurrencyInfoEntity("SZL", "Eswatini • Swazi Lilangeni", "E", "🇸🇿"),
            CurrencyInfoEntity("TJS", "Tajikistan • Tajikistani Somoni", "SM", "🇹🇯"),
            CurrencyInfoEntity("TMT", "Turkmenistan • Turkmenistani Manat", "T", "🇹🇲"),
            CurrencyInfoEntity("TND", "Tunisia • Tunisian Dinar", "DT", "🇹🇳"),
            CurrencyInfoEntity("TOP", "Tonga • Tongan Paʻanga", "T$", "🇹🇴"),
            CurrencyInfoEntity("TTD", "Trinidad & Tobago • Trinidad & Tobago Dollar", "$", "🇹🇹"),
            CurrencyInfoEntity("TZS", "Tanzania • Tanzanian Shilling", "Sh", "🇹🇿"),
            CurrencyInfoEntity("UGX", "Uganda • Ugandan Shilling", "Sh", "🇺🇬"),
            CurrencyInfoEntity("UYU", "Uruguay • Uruguayan Peso", "\$U", "🇺🇾"),
            CurrencyInfoEntity("UZS", "Uzbekistan • Uzbekistani Som", "so'm", "🇺🇿"),
            CurrencyInfoEntity("VES", "Venezuela • Venezuelan Bolívar", "Bs.", "🇻🇪"),
            CurrencyInfoEntity("VUV", "Vanuatu • Vanuatu Vatu", "VT", "🇻🇺"),
            CurrencyInfoEntity("WST", "Samoa • Samoan Tālā", "WS$", "🇼🇸"),
            CurrencyInfoEntity("XAF", "Central Africa • Central African CFA Franc", "FCFA", "🇨🇲"),
            CurrencyInfoEntity("XOF", "West Africa • West African CFA Franc", "CFA", "🇸🇳"),
            CurrencyInfoEntity("XPF", "French Polynesia • CFP Franc", "₣", "🇵🇫"),
            CurrencyInfoEntity("YER", "Yemen • Yemeni Rial", "﷼", "🇾🇪"),
            CurrencyInfoEntity("ZMW", "Zambia • Zambian Kwacha", "K", "🇿🇲"),
            CurrencyInfoEntity("ZWL", "Zimbabwe • Zimbabwean Dollar", "Z$", "🇿🇼")
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
            CurrencyRateEntity("EUR", "IQD", 1420.0, date),
            CurrencyRateEntity("EUR", "AFN", 74.0, date),
            CurrencyRateEntity("EUR", "ALL", 100.5, date),
            CurrencyRateEntity("EUR", "AMD", 420.0, date),
            CurrencyRateEntity("EUR", "AOA", 1005.0, date),
            CurrencyRateEntity("EUR", "AZN", 1.84, date),
            CurrencyRateEntity("EUR", "BAM", 1.956, date),
            CurrencyRateEntity("EUR", "BBD", 2.17, date),
            CurrencyRateEntity("EUR", "BIF", 3150.0, date),
            CurrencyRateEntity("EUR", "BND", 1.42, date),
            CurrencyRateEntity("EUR", "BOB", 7.50, date),
            CurrencyRateEntity("EUR", "BSD", 1.085, date),
            CurrencyRateEntity("EUR", "BTN", 90.50, date),
            CurrencyRateEntity("EUR", "BWP", 14.60, date),
            CurrencyRateEntity("EUR", "BYN", 3.55, date),
            CurrencyRateEntity("EUR", "BZD", 2.18, date),
            CurrencyRateEntity("EUR", "CDF", 3100.0, date),
            CurrencyRateEntity("EUR", "CRC", 560.0, date),
            CurrencyRateEntity("EUR", "CUP", 26.0, date),
            CurrencyRateEntity("EUR", "CVE", 110.25, date),
            CurrencyRateEntity("EUR", "DJF", 193.0, date),
            CurrencyRateEntity("EUR", "DOP", 64.5, date),
            CurrencyRateEntity("EUR", "ERN", 16.25, date),
            CurrencyRateEntity("EUR", "ETB", 130.0, date),
            CurrencyRateEntity("EUR", "FJD", 2.45, date),
            CurrencyRateEntity("EUR", "GEL", 2.95, date),
            CurrencyRateEntity("EUR", "GMD", 76.0, date),
            CurrencyRateEntity("EUR", "GNF", 9350.0, date),
            CurrencyRateEntity("EUR", "GTQ", 8.40, date),
            CurrencyRateEntity("EUR", "GYD", 227.0, date),
            CurrencyRateEntity("EUR", "HNL", 27.0, date),
            CurrencyRateEntity("EUR", "HTG", 143.0, date),
            CurrencyRateEntity("EUR", "JMD", 170.0, date),
            CurrencyRateEntity("EUR", "KGS", 94.0, date),
            CurrencyRateEntity("EUR", "KHR", 4420.0, date),
            CurrencyRateEntity("EUR", "KMF", 492.0, date),
            CurrencyRateEntity("EUR", "KZT", 520.0, date),
            CurrencyRateEntity("EUR", "LAK", 23900.0, date),
            CurrencyRateEntity("EUR", "LBP", 97000.0, date),
            CurrencyRateEntity("EUR", "LRD", 212.0, date),
            CurrencyRateEntity("EUR", "LSL", 19.10, date),
            CurrencyRateEntity("EUR", "LYD", 5.25, date),
            CurrencyRateEntity("EUR", "MDL", 19.30, date),
            CurrencyRateEntity("EUR", "MGA", 4950.0, date),
            CurrencyRateEntity("EUR", "MKD", 61.50, date),
            CurrencyRateEntity("EUR", "MMK", 2280.0, date),
            CurrencyRateEntity("EUR", "MNT", 3700.0, date),
            CurrencyRateEntity("EUR", "MOP", 8.70, date),
            CurrencyRateEntity("EUR", "MRU", 43.0, date),
            CurrencyRateEntity("EUR", "MUR", 50.50, date),
            CurrencyRateEntity("EUR", "MVR", 16.70, date),
            CurrencyRateEntity("EUR", "MWK", 1880.0, date),
            CurrencyRateEntity("EUR", "MZN", 69.30, date),
            CurrencyRateEntity("EUR", "NAD", 19.10, date),
            CurrencyRateEntity("EUR", "NIO", 40.0, date),
            CurrencyRateEntity("EUR", "PAB", 1.085, date),
            CurrencyRateEntity("EUR", "PGK", 4.30, date),
            CurrencyRateEntity("EUR", "PYG", 8400.0, date),
            CurrencyRateEntity("EUR", "RSD", 117.20, date),
            CurrencyRateEntity("EUR", "RWF", 1480.0, date),
            CurrencyRateEntity("EUR", "SCR", 15.20, date),
            CurrencyRateEntity("EUR", "SDG", 650.0, date),
            CurrencyRateEntity("EUR", "SLE", 24.50, date),
            CurrencyRateEntity("EUR", "SOS", 620.0, date),
            CurrencyRateEntity("EUR", "SRD", 38.0, date),
            CurrencyRateEntity("EUR", "SSP", 1400.0, date),
            CurrencyRateEntity("EUR", "STN", 24.50, date),
            CurrencyRateEntity("EUR", "SYP", 14100.0, date),
            CurrencyRateEntity("EUR", "SZL", 19.10, date),
            CurrencyRateEntity("EUR", "TJS", 11.60, date),
            CurrencyRateEntity("EUR", "TMT", 3.80, date),
            CurrencyRateEntity("EUR", "TND", 3.35, date),
            CurrencyRateEntity("EUR", "TOP", 2.55, date),
            CurrencyRateEntity("EUR", "TTD", 7.35, date),
            CurrencyRateEntity("EUR", "TZS", 2950.0, date),
            CurrencyRateEntity("EUR", "UGX", 4020.0, date),
            CurrencyRateEntity("EUR", "UYU", 44.50, date),
            CurrencyRateEntity("EUR", "UZS", 13800.0, date),
            CurrencyRateEntity("EUR", "VES", 40.0, date),
            CurrencyRateEntity("EUR", "VUV", 130.0, date),
            CurrencyRateEntity("EUR", "WST", 2.95, date),
            CurrencyRateEntity("EUR", "XAF", 655.957, date),
            CurrencyRateEntity("EUR", "XOF", 655.957, date),
            CurrencyRateEntity("EUR", "XPF", 119.33, date),
            CurrencyRateEntity("EUR", "YER", 271.0, date),
            CurrencyRateEntity("EUR", "ZMW", 29.50, date),
            CurrencyRateEntity("EUR", "ZWL", 28.0, date)
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
            "AFN" -> "Afghanistan"
            "ALL" -> "Albania"
            "AMD" -> "Armenia"
            "AOA" -> "Angola"
            "AZN" -> "Azerbaijan"
            "BAM" -> "Bosnia & Herzegovina"
            "BBD" -> "Barbados"
            "BIF" -> "Burundi"
            "BND" -> "Brunei"
            "BOB" -> "Bolivia"
            "BSD" -> "Bahamas"
            "BTN" -> "Bhutan"
            "BWP" -> "Botswana"
            "BYN" -> "Belarus"
            "BZD" -> "Belize"
            "CDF" -> "DR Congo"
            "CRC" -> "Costa Rica"
            "CUP" -> "Cuba"
            "CVE" -> "Cape Verde"
            "DJF" -> "Djibouti"
            "DOP" -> "Dominican Republic"
            "ERN" -> "Eritrea"
            "ETB" -> "Ethiopia"
            "FJD" -> "Fiji"
            "GEL" -> "Georgia"
            "GMD" -> "Gambia"
            "GNF" -> "Guinea"
            "GTQ" -> "Guatemala"
            "GYD" -> "Guyana"
            "HNL" -> "Honduras"
            "HTG" -> "Haiti"
            "JMD" -> "Jamaica"
            "KGS" -> "Kyrgyzstan"
            "KHR" -> "Cambodia"
            "KMF" -> "Comoros"
            "KZT" -> "Kazakhstan"
            "LAK" -> "Laos"
            "LBP" -> "Lebanon"
            "LRD" -> "Liberia"
            "LSL" -> "Lesotho"
            "LYD" -> "Libya"
            "MDL" -> "Moldova"
            "MGA" -> "Madagascar"
            "MKD" -> "North Macedonia"
            "MMK" -> "Myanmar"
            "MNT" -> "Mongolia"
            "MOP" -> "Macau"
            "MRU" -> "Mauritania"
            "MUR" -> "Mauritius"
            "MVR" -> "Maldives"
            "MWK" -> "Malawi"
            "MZN" -> "Mozambique"
            "NAD" -> "Namibia"
            "NIO" -> "Nicaragua"
            "PAB" -> "Panama"
            "PGK" -> "Papua New Guinea"
            "PYG" -> "Paraguay"
            "RSD" -> "Serbia"
            "RWF" -> "Rwanda"
            "SCR" -> "Seychelles"
            "SDG" -> "Sudan"
            "SLE" -> "Sierra Leone"
            "SOS" -> "Somalia"
            "SRD" -> "Suriname"
            "SSP" -> "South Sudan"
            "STN" -> "São Tomé & Príncipe"
            "SYP" -> "Syria"
            "SZL" -> "Eswatini"
            "TJS" -> "Tajikistan"
            "TMT" -> "Turkmenistan"
            "TND" -> "Tunisia"
            "TOP" -> "Tonga"
            "TTD" -> "Trinidad & Tobago"
            "TZS" -> "Tanzania"
            "UGX" -> "Uganda"
            "UYU" -> "Uruguay"
            "UZS" -> "Uzbekistan"
            "VES" -> "Venezuela"
            "VUV" -> "Vanuatu"
            "WST" -> "Samoa"
            "XAF" -> "Central Africa"
            "XOF" -> "West Africa"
            "XPF" -> "French Polynesia"
            "YER" -> "Yemen"
            "ZMW" -> "Zambia"
            "ZWL" -> "Zimbabwe"
            else -> code
        }
    }

    private fun formatCurrencyAmount(amount: Double, currencyCode: String): String {
        val symbols = DecimalFormatSymbols(Locale.US)
        val pattern = when (currencyCode.uppercase()) {
            "JPY", "KRW", "IDR", "VND", "HUF", "CLP", "PYG", "IQD", "BIF", "CDF", "GNF", "LAK", "LBP", "MGA", "MMK", "RWF", "UGX", "VUV", "UZS" -> "#,##0"
            else -> "#,##0.00"
        }
        val df = DecimalFormat(pattern, symbols)
        return df.format(amount)
    }

    companion object {
        const val KEY_LAST_SYNC = "last_successful_sync"
    }
}
