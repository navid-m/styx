package com.styx.models

/**
 * Some installation script, originally in YAML format.
 */
data class InstallScript(
    val files: List<LutrisFile>? = null,
    val game: LutrisGame? = null,
    val installer: List<ScriptableTask>? = null,
    val system: ScriptableSystem? = null,
    val wine: LutrisWine? = null
)

