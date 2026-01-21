package com.styx.models

/**
 * The global settings model.
 */
data class GlobalSettings(
    var theme: Theme = Theme(),
    var globalFlags: MutableMap<String, String> = mutableMapOf()
)
