package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "currency_rates", primaryKeys = ["baseCurrency", "quoteCurrency"])
data class CurrencyRateEntity(
    val baseCurrency: String,
    val quoteCurrency: String,
    val rate: Double,
    val rateDate: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "currencies")
data class CurrencyInfoEntity(
    @PrimaryKey val code: String,
    val name: String,
    val symbol: String = getCurrencySymbol(code),
    val flag: String = getCountryFlag(code)
)

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey val key: String,
    val value: String,
    val timestamp: Long = System.currentTimeMillis()
)

fun getCurrencySymbol(code: String): String {
    return when (code.uppercase()) {
        "USD" -> "$"
        "EUR" -> "€"
        "GBP" -> "£"
        "INR" -> "₹"
        "JPY", "CNY" -> "¥"
        "CAD" -> "CA$"
        "AUD" -> "A$"
        "CHF" -> "CHF"
        "AED" -> "AED"
        "SAR" -> "SAR"
        "KRW" -> "₩"
        "BRL" -> "R$"
        "RUB" -> "₽"
        "MXN" -> "MX$"
        "SGD" -> "S$"
        "HKD" -> "HK$"
        "NZD" -> "NZ$"
        "TRY" -> "₺"
        "ZAR" -> "R"
        "SEK", "NOK", "DKK", "ISK" -> "kr"
        "PLN" -> "zł"
        "THB" -> "฿"
        "IDR" -> "Rp"
        "MYR" -> "RM"
        "PHP" -> "₱"
        "VND" -> "₫"
        "CZK" -> "Kč"
        "ILS" -> "₪"
        "HUF" -> "Ft"
        "EGP" -> "E£"
        "PKR", "LKR", "NPR", "MUR" -> "Rs"
        "BDT" -> "৳"
        "NGN" -> "₦"
        "KES", "TZS", "UGX" -> "Sh"
        "QAR" -> "QR"
        "KWD" -> "KD"
        "OMR" -> "OR"
        "BHD" -> "BD"
        "TWD" -> "NT$"
        "ARS", "CLP", "COP" -> "$"
        "PEN" -> "S/."
        "BGN" -> "лв"
        "RON" -> "lei"
        "UAH" -> "₴"
        "GHS" -> "GH₵"
        "MAD" -> "MAD"
        "DZD" -> "DA"
        else -> code
    }
}

fun getCountryFlag(code: String): String {
    return when (code.uppercase()) {
        "USD" -> "🇺🇸"
        "EUR" -> "🇪🇺"
        "GBP" -> "🇬🇧"
        "INR" -> "🇮🇳"
        "JPY" -> "🇯🇵"
        "CNY" -> "🇨🇳"
        "CAD" -> "🇨🇦"
        "AUD" -> "🇦🇺"
        "CHF" -> "🇨🇭"
        "AED" -> "🇦🇪"
        "SAR" -> "🇸🇦"
        "SGD" -> "🇸🇬"
        "KRW" -> "🇰🇷"
        "BRL" -> "🇧🇷"
        "MXN" -> "🇲🇽"
        "IDR" -> "🇮🇩"
        "MYR" -> "🇲🇾"
        "PHP" -> "🇵🇭"
        "THB" -> "🇹🇭"
        "VND" -> "🇻🇳"
        "PKR" -> "🇵🇰"
        "BDT" -> "🇧🇩"
        "TRY" -> "🇹🇷"
        "ZAR" -> "🇿🇦"
        "EGP" -> "🇪🇬"
        "NGN" -> "🇳🇬"
        "KES" -> "🇰🇪"
        "RUB" -> "🇷🇺"
        "NZD" -> "🇳🇿"
        "SEK" -> "🇸🇪"
        "NOK" -> "🇳🇴"
        "DKK" -> "🇩🇰"
        "PLN" -> "🇵🇱"
        "CZK" -> "🇨🇿"
        "HUF" -> "🇭🇺"
        "ILS" -> "🇮🇱"
        "CLP" -> "🇨🇱"
        "COP" -> "🇨🇴"
        "ARS" -> "🇦🇷"
        "QAR" -> "🇶🇦"
        "KWD" -> "🇰🇼"
        "OMR" -> "🇴🇲"
        "BHD" -> "🇧🇭"
        "HKD" -> "🇭🇰"
        "TWD" -> "🇹🇼"
        "LKR" -> "🇱🇰"
        "NPR" -> "🇳🇵"
        "GHS" -> "🇬🇭"
        "MAD" -> "🇲🇦"
        "DZD" -> "🇩🇿"
        "PEN" -> "🇵🇪"
        "RON" -> "🇷🇴"
        "BGN" -> "🇧🇬"
        "ISK" -> "🇮🇸"
        "UAH" -> "🇺🇦"
        "JOD" -> "🇯🇴"
        "IQD" -> "🇮🇶"
        "CRC" -> "🇨🇷"
        "DOP" -> "🇩🇴"
        "UYU" -> "🇺🇾"
        "BOB" -> "🇧🇴"
        "PYG" -> "🇵🇾"
        "MMK" -> "🇲🇲"
        "KHR" -> "🇰🇭"
        else -> "🌐"
    }
}
