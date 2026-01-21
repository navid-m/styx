package com.styx.models

import org.junit.Test
import kotlin.test.assertEquals

class GameTypeTest {
    @Test
    fun testGameTypeValues() {
        assertEquals(3, GameType.values().size)
        
        val types = GameType.values()
        assert(types.contains(GameType.WINDOWS))
        assert(types.contains(GameType.NATIVE_LINUX))
        assert(types.contains(GameType.STEAM))
    }

    @Test
    fun testGameTypeValueOf() {
        assertEquals(GameType.WINDOWS, GameType.valueOf("WINDOWS"))
        assertEquals(GameType.NATIVE_LINUX, GameType.valueOf("NATIVE_LINUX"))
        assertEquals(GameType.STEAM, GameType.valueOf("STEAM"))
    }

    @Test
    fun testGameTypeOrdinality() {
        assertEquals(0, GameType.WINDOWS.ordinal)
        assertEquals(1, GameType.NATIVE_LINUX.ordinal)
        assertEquals(2, GameType.STEAM.ordinal)
    }

    @Test
    fun testGameTypeNames() {
        assertEquals("WINDOWS", GameType.WINDOWS.name)
        assertEquals("NATIVE_LINUX", GameType.NATIVE_LINUX.name)
        assertEquals("STEAM", GameType.STEAM.name)
    }
}
