package com.salmansaleem.i220904

import java.io.Serializable

data class VanishMode(
    val enabled: Boolean = false,        // Whether Vanish Mode is active
    val timestamp: Long = System.currentTimeMillis() // When it was toggled
) : Serializable