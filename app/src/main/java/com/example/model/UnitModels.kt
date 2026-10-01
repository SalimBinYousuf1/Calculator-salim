package com.example.model

enum class UnitCategory(val displayName: String, val iconName: String) {
    LENGTH("Length", "straighten"),
    MASS("Mass", "scale"),
    TEMPERATURE("Temperature", "thermostat"),
    AREA("Area", "crop_square"),
    VOLUME("Volume", "local_drink"),
    SPEED("Speed", "speed"),
    TIME("Time", "schedule"),
    DATA("Data", "sd_storage")
}

data class UnitDefinition(
    val id: String,
    val name: String,
    val symbol: String,
    val factorToBase: Double = 1.0 // Ratio against standard base unit (e.g. meter, kg, second)
)

object UnitRegistry {

    val unitsByCategory: Map<UnitCategory, List<UnitDefinition>> = mapOf(
        UnitCategory.LENGTH to listOf(
            UnitDefinition("m", "Meter", "m", 1.0),
            UnitDefinition("km", "Kilometer", "km", 1000.0),
            UnitDefinition("cm", "Centimeter", "cm", 0.01),
            UnitDefinition("mm", "Millimeter", "mm", 0.001),
            UnitDefinition("mi", "Mile", "mi", 1609.344),
            UnitDefinition("yd", "Yard", "yd", 0.9144),
            UnitDefinition("ft", "Foot", "ft", 0.3048),
            UnitDefinition("in", "Inch", "in", 0.0254)
        ),
        UnitCategory.MASS to listOf(
            UnitDefinition("kg", "Kilogram", "kg", 1.0),
            UnitDefinition("g", "Gram", "g", 0.001),
            UnitDefinition("mg", "Milligram", "mg", 0.000001),
            UnitDefinition("t", "Metric Ton", "t", 1000.0),
            UnitDefinition("lb", "Pound", "lb", 0.45359237),
            UnitDefinition("oz", "Ounce", "oz", 0.028349523125)
        ),
        UnitCategory.TEMPERATURE to listOf(
            UnitDefinition("C", "Celsius", "°C"),
            UnitDefinition("F", "Fahrenheit", "°F"),
            UnitDefinition("K", "Kelvin", "K")
        ),
        UnitCategory.AREA to listOf(
            UnitDefinition("m2", "Square Meter", "m²", 1.0),
            UnitDefinition("km2", "Square Kilometer", "km²", 1_000_000.0),
            UnitDefinition("ft2", "Square Foot", "ft²", 0.09290304),
            UnitDefinition("in2", "Square Inch", "in²", 0.00064516),
            UnitDefinition("ha", "Hectare", "ha", 10_000.0),
            UnitDefinition("ac", "Acre", "ac", 4046.8564224)
        ),
        UnitCategory.VOLUME to listOf(
            UnitDefinition("L", "Liter", "L", 1.0),
            UnitDefinition("mL", "Milliliter", "mL", 0.001),
            UnitDefinition("m3", "Cubic Meter", "m³", 1000.0),
            UnitDefinition("gal", "Gallon (US)", "gal", 3.785411784),
            UnitDefinition("qt", "Quart (US)", "qt", 0.946352946),
            UnitDefinition("pt", "Pint (US)", "pt", 0.473176473),
            UnitDefinition("cup", "Cup (US)", "cup", 0.2365882365)
        ),
        UnitCategory.SPEED to listOf(
            UnitDefinition("mps", "Meters per second", "m/s", 1.0),
            UnitDefinition("kmh", "Kilometers per hour", "km/h", 0.2777777778),
            UnitDefinition("mph", "Miles per hour", "mph", 0.44704),
            UnitDefinition("knot", "Knot", "kn", 0.514444444)
        ),
        UnitCategory.TIME to listOf(
            UnitDefinition("s", "Second", "s", 1.0),
            UnitDefinition("ms", "Millisecond", "ms", 0.001),
            UnitDefinition("min", "Minute", "min", 60.0),
            UnitDefinition("h", "Hour", "h", 3600.0),
            UnitDefinition("d", "Day", "d", 86400.0),
            UnitDefinition("wk", "Week", "wk", 604800.0),
            UnitDefinition("yr", "Year", "yr", 31536000.0)
        ),
        UnitCategory.DATA to listOf(
            UnitDefinition("B", "Byte", "B", 1.0),
            UnitDefinition("KB", "Kilobyte", "KB", 1024.0),
            UnitDefinition("MB", "Megabyte", "MB", 1_048_576.0),
            UnitDefinition("GB", "Gigabyte", "GB", 1_073_741_824.0),
            UnitDefinition("TB", "Terabyte", "TB", 1_099_511_627_776.0)
        )
    )

    fun convert(amount: Double, fromUnitId: String, toUnitId: String, category: UnitCategory): Double {
        if (fromUnitId == toUnitId) return amount

        if (category == UnitCategory.TEMPERATURE) {
            // Precise temperature conversions
            val celsius = when (fromUnitId) {
                "C" -> amount
                "F" -> (amount - 32.0) * (5.0 / 9.0)
                "K" -> amount - 273.15
                else -> amount
            }
            return when (toUnitId) {
                "C" -> celsius
                "F" -> (celsius * (9.0 / 5.0)) + 32.0
                "K" -> celsius + 273.15
                else -> celsius
            }
        }

        val units = unitsByCategory[category] ?: return amount
        val fromDef = units.firstOrNull { it.id == fromUnitId } ?: return amount
        val toDef = units.firstOrNull { it.id == toUnitId } ?: return amount

        val inBase = amount * fromDef.factorToBase
        return inBase / toDef.factorToBase
    }
}
