package com.bellinicine.core.model

import kotlinx.serialization.Serializable

/**
 * Complete set of After Effects blend modes for composite calculations.
 */
@Serializable
enum class BlendMode(val displayName: String, val category: String) {
    // Normal
    NORMAL("Normal", "Normal"),
    DISSOLVE("Dissolve", "Normal"),

    // Subtractive / Darken
    DARKEN("Darken", "Darken"),
    MULTIPLY("Multiply", "Darken"),
    COLOR_BURN("Color Burn", "Darken"),
    LINEAR_BURN("Linear Burn", "Darken"),
    DARKER_COLOR("Darker Color", "Darken"),

    // Additive / Lighten
    ADD("Add", "Lighten"),
    LIGHTEN("Lighten", "Lighten"),
    SCREEN("Screen", "Lighten"),
    COLOR_DODGE("Color Dodge", "Lighten"),
    LINEAR_DODGE("Linear Dodge", "Lighten"),
    LIGHTER_COLOR("Lighter Color", "Lighten"),

    // Complex / Contrast
    OVERLAY("Overlay", "Contrast"),
    SOFT_LIGHT("Soft Light", "Contrast"),
    HARD_LIGHT("Hard Light", "Contrast"),
    VIVID_LIGHT("Vivid Light", "Contrast"),
    LINEAR_LIGHT("Linear Light", "Contrast"),
    PIN_LIGHT("Pin Light", "Contrast"),
    HARD_MIX("Hard Mix", "Contrast"),

    // Difference / Inversion
    DIFFERENCE("Difference", "Inversion"),
    EXCLUSION("Exclusion", "Inversion"),
    SUBTRACT("Subtract", "Inversion"),
    DIVIDE("Divide", "Inversion"),

    // Component / Color
    HUE("Hue", "Color"),
    SATURATION("Saturation", "Color"),
    COLOR("Color", "Color"),
    LUMINOSITY("Luminosity", "Color");

    companion object {
        fun fromName(name: String): BlendMode =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: NORMAL
    }
}
