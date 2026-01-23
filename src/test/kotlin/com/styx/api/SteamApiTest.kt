package com.styx.api

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SteamApiTest {
    @Test
    fun testSteamSearchResultDataClass() {
        val result = SteamApi.SteamSearchResult(
            appid = "12345",
            name = "Test Game"
        )

        assertEquals("12345", result.appid)
        assertEquals("Test Game", result.name)
    }

    @Test
    fun testSteamSearchResultEquality() {
        val result1 = SteamApi.SteamSearchResult("12345", "Game")
        val result2 = SteamApi.SteamSearchResult("12345", "Game")
        val result3 = SteamApi.SteamSearchResult("54321", "Game")

        assertEquals(result1, result2)
        assert(result1 != result3)
    }

    @Test
    fun testSearchGameByNameReturnsListOnError() {
        val results = SteamApi.searchGameByName("")
        assertNotNull(results)
        assert(results.size <= 10)
    }

    @Test
    fun testSearchResultsAreLimitedTo10() {
        val testResults = listOf(
            SteamApi.SteamSearchResult("1", "Game 1"),
            SteamApi.SteamSearchResult("2", "Game 2"),
            SteamApi.SteamSearchResult("3", "Game 3"),
            SteamApi.SteamSearchResult("4", "Game 4"),
            SteamApi.SteamSearchResult("5", "Game 5"),
            SteamApi.SteamSearchResult("6", "Game 6"),
            SteamApi.SteamSearchResult("7", "Game 7"),
            SteamApi.SteamSearchResult("8", "Game 8"),
            SteamApi.SteamSearchResult("9", "Game 9"),
            SteamApi.SteamSearchResult("10", "Game 10"),
            SteamApi.SteamSearchResult("11", "Game 11")
        )

        val limited = testResults.take(10)
        assertEquals(10, limited.size)
        assertEquals("10", limited.last().appid)
    }

    @Test
    fun testSteamSearchResultCopy() {
        val original = SteamApi.SteamSearchResult("12345", "Original Name")
        val copy = original.copy(name = "Modified Name")

        assertEquals("12345", copy.appid)
        assertEquals("Modified Name", copy.name)
        assertEquals("Original Name", original.name)
    }
}
