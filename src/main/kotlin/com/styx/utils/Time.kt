package com.styx.utils

/**
 * Format the time played given the minutes as a long.
 *
 * @param minutes The minutes
 * @return The formatted time.
 */
fun formatTimePlayed(minutes: Long): String {
    return if (minutes == 0L) {
        "Not yet played"
    } else if (minutes < 60) {
        "Played: ${minutes}m"
    } else {
        val hours = minutes / 60
        val mins = minutes % 60
        if (mins == 0L) {
            "Played: ${hours}h"
        } else {
            "Played: ${hours}h ${mins}m"
        }
    }
}