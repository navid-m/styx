package com.styx.utils

import org.junit.Test
import kotlin.test.assertEquals

class FormatTimePlayedTest {

    @Test
    fun testZeroMinutesReturnsNotYetPlayed() {
        val result = formatTimePlayed(0L)
        assertEquals("Not yet played", result)
    }

    @Test
    fun testMinutesLessThan60() {
        assertEquals("Played: 1m", formatTimePlayed(1L))
        assertEquals("Played: 30m", formatTimePlayed(30L))
        assertEquals("Played: 59m", formatTimePlayed(59L))
    }

    @Test
    fun testExactlyOneHour() {
        val result = formatTimePlayed(60L)
        assertEquals("Played: 1h", result)
    }

    @Test
    fun testMultipleHoursNoMinutes() {
        assertEquals("Played: 2h", formatTimePlayed(120L))
        assertEquals("Played: 5h", formatTimePlayed(300L))
        assertEquals("Played: 10h", formatTimePlayed(600L))
        assertEquals("Played: 24h", formatTimePlayed(1440L))
    }

    @Test
    fun testHoursWithMinutes() {
        assertEquals("Played: 1h 1m", formatTimePlayed(61L))
        assertEquals("Played: 1h 30m", formatTimePlayed(90L))
        assertEquals("Played: 2h 15m", formatTimePlayed(135L))
        assertEquals("Played: 3h 45m", formatTimePlayed(225L))
        assertEquals("Played: 10h 59m", formatTimePlayed(659L))
    }

    @Test
    fun testLargeValues() {
        assertEquals("Played: 100h", formatTimePlayed(6000L))
        assertEquals("Played: 100h 30m", formatTimePlayed(6030L))
        assertEquals("Played: 999h 59m", formatTimePlayed(59999L))
    }

    @Test
    fun testEdgeCases() {
        assertEquals("Played: 1h 59m", formatTimePlayed(119L))
        assertEquals("Played: 2h", formatTimePlayed(120L))
        assertEquals("Played: 2h 1m", formatTimePlayed(121L))
    }
}
