package com.styx.models

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GameTest {
    @Test
    fun testGameCreationWithDefaultValues() {
        val game = Game(
            name = "Test Game",
            executable = "/path/to/game.exe",
            prefix = "/path/to/prefix"
        )
        
        assertEquals("Test Game", game.name)
        assertEquals("/path/to/game.exe", game.executable)
        assertEquals("/path/to/prefix", game.prefix)
        assertNull(game.protonVersion)
        assertNull(game.protonPath)
        assertNull(game.protonBin)
        assertEquals(0, game.launchOptions.size)
        assertNull(game.imagePath)
        assertEquals(0L, game.timePlayed)
        assertEquals(0, game.timesOpened)
        assertEquals(0, game.timesCrashed)
        assertNull(game.type)
        assertEquals(false, game.verboseLogging)
        assertEquals("All", game.category)
        assertNull(game.steamAppId)
    }

    @Test
    fun testGameCreationWithAllParameters() {
        val launchOptions = mutableMapOf("option1" to "value1", "option2" to "value2")
        
        val game = Game(
            name = "Advanced Game",
            executable = "/path/to/advanced.exe",
            prefix = "/path/to/advanced/prefix",
            protonVersion = "Proton 8.0",
            protonPath = "/path/to/proton",
            protonBin = "/path/to/proton/bin",
            launchOptions = launchOptions,
            imagePath = "/path/to/image.png",
            timePlayed = 120L,
            timesOpened = 5,
            timesCrashed = 1,
            type = GameType.WINDOWS,
            verboseLogging = true,
            category = "Action",
            steamAppId = "12345"
        )
        
        assertEquals("Advanced Game", game.name)
        assertEquals("/path/to/advanced.exe", game.executable)
        assertEquals("/path/to/advanced/prefix", game.prefix)
        assertEquals("Proton 8.0", game.protonVersion)
        assertEquals("/path/to/proton", game.protonPath)
        assertEquals("/path/to/proton/bin", game.protonBin)
        assertEquals(2, game.launchOptions.size)
        assertEquals("value1", game.launchOptions["option1"])
        assertEquals("/path/to/image.png", game.imagePath)
        assertEquals(120L, game.timePlayed)
        assertEquals(5, game.timesOpened)
        assertEquals(1, game.timesCrashed)
        assertEquals(GameType.WINDOWS, game.type)
        assertEquals(true, game.verboseLogging)
        assertEquals("Action", game.category)
        assertEquals("12345", game.steamAppId)
    }

    @Test
    fun testGetGameTypeDefaultsToWindows() {
        val game = Game(
            name = "Legacy Game",
            executable = "/path/to/game.exe",
            prefix = "/path/to/prefix",
            type = null
        )
        
        assertEquals(GameType.WINDOWS, game.getGameType())
    }

    @Test
    fun testGetGameTypeReturnsSetType() {
        val gameWindows = Game(
            name = "Windows Game",
            executable = "/path/to/game.exe",
            prefix = "/path/to/prefix",
            type = GameType.WINDOWS
        )
        assertEquals(GameType.WINDOWS, gameWindows.getGameType())

        val gameNative = Game(
            name = "Native Game",
            executable = "/path/to/game",
            prefix = "/path/to/prefix",
            type = GameType.NATIVE_LINUX
        )
        assertEquals(GameType.NATIVE_LINUX, gameNative.getGameType())

        val gameSteam = Game(
            name = "Steam Game",
            executable = "steam://rungameid/12345",
            prefix = "/path/to/prefix",
            type = GameType.STEAM
        )
        assertEquals(GameType.STEAM, gameSteam.getGameType())
    }

    @Test
    fun testGameMutableProperties() {
        val game = Game(
            name = "Mutable Game",
            executable = "/path/to/game.exe",
            prefix = "/path/to/prefix"
        )
        
        game.name = "Updated Name"
        assertEquals("Updated Name", game.name)
        
        game.prefix = "/new/prefix/path"
        assertEquals("/new/prefix/path", game.prefix)
        
        game.protonVersion = "Proton 9.0"
        assertEquals("Proton 9.0", game.protonVersion)
        
        game.timePlayed = 60L
        assertEquals(60L, game.timePlayed)
        
        game.timesOpened = 3
        assertEquals(3, game.timesOpened)
        
        game.timesCrashed = 1
        assertEquals(1, game.timesCrashed)
        
        game.verboseLogging = true
        assertEquals(true, game.verboseLogging)
        
        game.category = "RPG"
        assertEquals("RPG", game.category)
    }

    @Test
    fun testLaunchOptionsCanBeModified() {
        val game = Game(
            name = "Options Game",
            executable = "/path/to/game.exe",
            prefix = "/path/to/prefix"
        )
        
        game.launchOptions["DXVK_HUD"] = "fps"
        game.launchOptions["PROTON_LOG"] = "1"
        
        assertEquals(2, game.launchOptions.size)
        assertEquals("fps", game.launchOptions["DXVK_HUD"])
        assertEquals("1", game.launchOptions["PROTON_LOG"])
        
        game.launchOptions.remove("DXVK_HUD")
        assertEquals(1, game.launchOptions.size)
        assertNull(game.launchOptions["DXVK_HUD"])
    }

    @Test
    fun testDataClassCopy() {
        val original = Game(
            name = "Original Game",
            executable = "/path/to/game.exe",
            prefix = "/path/to/prefix",
            timePlayed = 100L
        )
        
        val copy = original.copy(name = "Copied Game", timePlayed = 200L)
        
        assertEquals("Copied Game", copy.name)
        assertEquals("/path/to/game.exe", copy.executable)
        assertEquals(200L, copy.timePlayed)
    }
}
