package com.styx.models

/**
 * The architecture, executable path and wine-prefix of some Lutris game.
 */
data class LutrisGame(
    val arch: String? = null,
    val exe: String? = null,
    val prefix: String? = null
)