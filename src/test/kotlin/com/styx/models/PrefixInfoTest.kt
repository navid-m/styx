package com.styx.models

import org.junit.Test
import kotlin.test.assertEquals

class PrefixInfoTest {
    @Test
    fun testPrefixInfoCreation() {
        val prefixInfo = PrefixInfo(
            name = "Proton - 12345",
            path = "/home/user/.steam/steam/steamapps/compatdata/12345/pfx"
        )
        
        assertEquals("Proton - 12345", prefixInfo.name)
        assertEquals("/home/user/.steam/steam/steamapps/compatdata/12345/pfx", prefixInfo.path)
    }

    @Test
    fun testPrefixInfoWithDifferentFormats() {
        val protonPrefix = PrefixInfo(
            name = "Proton - Game Name",
            path = "/path/to/prefix"
        )
        assertEquals("Proton - Game Name", protonPrefix.name)
        
        val customPrefix = PrefixInfo(
            name = "Custom Wine Prefix",
            path = "/home/user/wine-prefixes/custom"
        )
        assertEquals("Custom Wine Prefix", customPrefix.name)
    }

    @Test
    fun testDataClassEquality() {
        val prefix1 = PrefixInfo("Test", "/path/to/prefix")
        val prefix2 = PrefixInfo("Test", "/path/to/prefix")
        val prefix3 = PrefixInfo("Test", "/different/path")
        
        assertEquals(prefix1, prefix2)
        assert(prefix1 != prefix3)
    }

    @Test
    fun testDataClassCopy() {
        val original = PrefixInfo("Original", "/original/path")
        val copy = original.copy(name = "Modified")
        
        assertEquals("Modified", copy.name)
        assertEquals("/original/path", copy.path)
    }
}
