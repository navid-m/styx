package com.styx.models

data class Game(
    var name: String,
    val executable: String,
    var prefix: String,
    var protonVersion: String? = null,
    var protonPath: String? = null,
    var protonBin: String? = null,
    var launchOptions: MutableMap<String, String> = mutableMapOf(),
    var imagePath: String? = null,
    var timePlayed: Long = 0,
    var timesOpened: Int = 0,
    var timesCrashed: Int = 0,
    var type: GameType? = null,
    var verboseLogging: Boolean = false,
    var category: String = "All"
) {
    /**
     * Helper to get game type.
     *
     * Defaults to windows for backwards compatibility.
     */
    fun getGameType(): GameType = type ?: GameType.WINDOWS
}