package com.styx.models

/**
 * UI theming data.
 */
data class Theme(
    var gameTitleColor: String = "#FFFFFF",
    var timePlayedColor: String = "#03FCFC",
    var metadataLabelColor: String = "#64C8FF"
) {
    fun getGameTitleColorObject(): java.awt.Color {
        return java.awt.Color.decode(gameTitleColor)
    }

    fun getTimePlayedColorObject(): java.awt.Color {
        return java.awt.Color.decode(timePlayedColor)
    }

    fun getMetadataLabelColorObject(): java.awt.Color {
        return java.awt.Color.decode(metadataLabelColor)
    }
}
