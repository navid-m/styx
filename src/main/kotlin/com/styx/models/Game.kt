package com.styx.models

/**
 * Contains all data regarding the game itself.
 */
data class Game(
    var name: String,
    var executable: String,
    var prefix: String,
    var protonVersion: String? = null,
    var protonPath: String? = null,
    var protonBin: String? = null,
    var launchOptions: MutableMap<String, String> = mutableMapOf(),
    var singletonFlags: MutableList<String>? = mutableListOf(),
    var imagePath: String? = null,
    var timePlayed: Long = 0,
    var timesOpened: Int = 0,
    var timesCrashed: Int = 0,
    var type: GameType? = null,
    var verboseLogging: Boolean = false,
    var wineLogLevel: String = "warn+all,fixme-all",
    var category: String = "All",
    var steamAppId: String? = null,
    var cpuGovernor: String? = null,
    var lutrisScriptPath: String? = null
) {
    /**
     * Helper to get game type.
     *
     * Defaults to windows for backwards compatibility.
     */
    fun getGameType(): GameType = type ?: GameType.WINDOWS
}