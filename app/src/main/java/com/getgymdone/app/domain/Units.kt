package com.getgymdone.app.domain

private const val KG_PER_LB = 0.45359237

enum class WeightUnit(val label: String) {
    Kg("kg"), Lbs("lbs");
    companion object {
        fun fromStored(s: String) = if (s.equals("lbs", ignoreCase = true)) Lbs else Kg
    }
}

fun Double.kgToDisplay(unit: WeightUnit): Double = when (unit) {
    WeightUnit.Kg -> this
    WeightUnit.Lbs -> this / KG_PER_LB
}

fun Double.displayToKg(unit: WeightUnit): Double = when (unit) {
    WeightUnit.Kg -> this
    WeightUnit.Lbs -> this * KG_PER_LB
}

/**
 * The smallest natural plate jump in this unit's display terms — 2.5 kg or 5 lb. Drives both the
 * in-workout ± weight stepper and the increment used for progressive-overload suggestions.
 */
val WeightUnit.displayStep: Double
    get() = when (this) {
        WeightUnit.Kg -> 2.5
        WeightUnit.Lbs -> 5.0
    }
