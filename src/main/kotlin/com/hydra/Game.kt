package com.hydra

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
    var type: GameType? = null
) {
    // Helper to get type, defaulting to WINDOWS for backward compatibility
    fun getGameType(): GameType = type ?: GameType.WINDOWS
}