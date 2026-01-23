package com.styx.utils

import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Parser for extracting Steam game statistics from VDF files.
 */
object SteamStatsParser {

    data class SteamGameStats(
        val playtime: Long? = null,
        val playtime2weeks: Long? = null,
        val lastPlayed: Long? = null,
        val lastLaunch: Long? = null,
        val lastExit: Long? = null,
        val cloudSyncState: String? = null
    )

    /**
     * Gets Steam statistics for a game from the localconfig.vdf file.
     */
    fun getGameStats(steamAppId: String): SteamGameStats? {
        try {
            val vdfFile = findLocalConfigVdf() ?: return null

            if (!vdfFile.exists()) {
                return null
            }

            val content = vdfFile.readText()
            return parseStatsForApp(content, steamAppId)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Finds the Steam localconfig.vdf file.
     */
    private fun findLocalConfigVdf(): File? {
        val homeDir = System.getProperty("user.home")
        val steamUserdataDir = File(homeDir, ".steam/steam/userdata")

        if (!steamUserdataDir.exists() || !steamUserdataDir.isDirectory) {
            return null
        }

        val userDirs = steamUserdataDir.listFiles { file ->
            file.isDirectory && file.name.all { it.isDigit() }
        }

        if (userDirs.isNullOrEmpty()) {
            return null
        }

        val userDir = userDirs.first()
        return File(userDir, "config/localconfig.vdf")
    }

    /**
     * Parses VDF content to extract stats for a specific app.
     */
    private fun parseStatsForApp(vdfContent: String, steamAppId: String): SteamGameStats? {
        val lines = vdfContent.lines()
        var inTargetApp = false
        var braceDepth = 0
        var targetBraceDepth = 0

        var playtime: Long? = null
        var playtime2weeks: Long? = null
        var lastPlayed: Long? = null
        var lastLaunch: Long? = null
        var lastExit: Long? = null
        var cloudSyncState: String? = null

        for (line in lines) {
            val trimmed = line.trim()

            if (trimmed.contains("{")) {
                braceDepth++
            }

            if ((trimmed == "\"$steamAppId\"" || trimmed == "'$steamAppId'") && !inTargetApp) {
                inTargetApp = true
                targetBraceDepth = braceDepth
                continue
            }

            if (inTargetApp) {
                if (trimmed.contains("}")) {
                    braceDepth--
                    if (braceDepth < targetBraceDepth) {
                        break
                    }
                }

                when {
                    trimmed.startsWith("\"Playtime2wks\"") || trimmed.startsWith("'Playtime2wks'") -> {
                        playtime2weeks = extractValue(trimmed)?.toLongOrNull()
                    }

                    trimmed.startsWith("\"Playtime\"") || trimmed.startsWith("'Playtime'") -> {
                        playtime = extractValue(trimmed)?.toLongOrNull()
                    }

                    trimmed.startsWith("\"LastPlayed\"") || trimmed.startsWith("'LastPlayed'") -> {
                        lastPlayed = extractValue(trimmed)?.toLongOrNull()
                    }

                    trimmed.startsWith("\"lastlaunch\"") || trimmed.startsWith("'lastlaunch'") -> {
                        lastLaunch = extractValue(trimmed)?.toLongOrNull()
                    }

                    trimmed.startsWith("\"lastexit\"") || trimmed.startsWith("'lastexit'") -> {
                        lastExit = extractValue(trimmed)?.toLongOrNull()
                    }

                    trimmed.startsWith("\"last_sync_state\"") || trimmed.startsWith("'last_sync_state'") -> {
                        cloudSyncState = extractValue(trimmed)
                    }
                }
            }

            if (trimmed.contains("}")) {
                braceDepth--
            }
        }

        if (inTargetApp && (playtime != null || lastPlayed != null)) {
            return SteamGameStats(playtime, playtime2weeks, lastPlayed, lastLaunch, lastExit, cloudSyncState)
        }

        return null
    }

    private fun extractValue(line: String): String? {
        val parts = line.split(Regex("\\s+"))
        if (parts.size >= 2) {
            return parts.last().trim('"', '\'')
        }
        return null
    }

    /**
     * Formats a Unix timestamp to a readable date string.
     */
    fun formatTimestamp(timestamp: Long?): String {
        if (timestamp == null || timestamp == 0L) return "Never"

        try {
            val date = Date(timestamp * 1000)
            val formatter = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            return formatter.format(date)
        } catch (e: Exception) {
            return "Invalid date"
        }
    }

    /**
     * Formats playtime in minutes to hours/minutes.
     */
    fun formatPlaytime(minutes: Long?): String {
        if (minutes == null || minutes == 0L) return "0 minutes"

        val hours = minutes / 60
        val mins = minutes % 60

        return when {
            hours == 0L -> "${mins}m"
            mins == 0L -> "${hours}h"
            else -> "${hours}h ${mins}m"
        }
    }
}
