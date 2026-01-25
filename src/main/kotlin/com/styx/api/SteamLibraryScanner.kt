package com.styx.api

import java.io.File

object SteamLibraryScanner {
    data class SteamGame(val appId: String, val name: String, val installDir: String)

    fun scanSteamLibrary(): List<SteamGame> {
        val games = mutableListOf<SteamGame>()
        val libraryFolders = findLibraryFolders()

        for (folder in libraryFolders) {
            games.addAll(scanLibraryFolder(folder))
        }

        return games.distinctBy { it.appId }.sortedBy { it.name }
    }

    private fun findLibraryFolders(): List<File> {
        val folders = mutableListOf<File>()
        val homeDir = System.getProperty("user.home")
        val steamDir = File(homeDir, ".steam/steam")

        if (!steamDir.exists()) {
            return emptyList()
        }

        val libraryFoldersVdf = File(steamDir, "steamapps/libraryfolders.vdf")
        if (libraryFoldersVdf.exists()) {
            val content = libraryFoldersVdf.readText()
            val paths = parseLibraryPaths(content)
            for (path in paths) {
                val folder = File(path)
                if (folder.exists() && folder.isDirectory) {
                    folders.add(folder)
                }
            }
        }

        val defaultSteamApps = File(steamDir, "steamapps")
        if (defaultSteamApps.exists() && defaultSteamApps !in folders) {
            folders.add(defaultSteamApps)
        }

        return folders
    }

    private fun parseLibraryPaths(vdfContent: String): List<String> {
        val paths = mutableListOf<String>()
        val lines = vdfContent.lines()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("\"path\"") || trimmed.startsWith("'path'")) {
                val value = extractValue(trimmed)
                if (value != null) {
                    paths.add("$value/steamapps")
                }
            }
        }

        return paths
    }

    private fun scanLibraryFolder(folder: File): List<SteamGame> {
        val games = mutableListOf<SteamGame>()

        val manifests = folder.listFiles { file ->
            file.name.startsWith("appmanifest_") && file.name.endsWith(".acf")
        } ?: return emptyList()

        for (manifest in manifests) {
            try {
                val game = parseManifest(manifest)
                if (game != null) {
                    games.add(game)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return games
    }

    private fun parseManifest(manifestFile: File): SteamGame? {
        val content = manifestFile.readText()
        var appId: String? = null
        var name: String? = null
        var installDir: String? = null

        val lines = content.lines()
        for (line in lines) {
            val trimmed = line.trim()

            when {
                trimmed.startsWith("\"appid\"") || trimmed.startsWith("'appid'") -> {
                    appId = extractValue(trimmed)
                }
                trimmed.startsWith("\"name\"") || trimmed.startsWith("'name'") -> {
                    name = extractValue(trimmed)
                }
                trimmed.startsWith("\"installdir\"") || trimmed.startsWith("'installdir'") -> {
                    installDir = extractValue(trimmed)
                }
            }
        }

        return if (appId != null && name != null && installDir != null) {
            SteamGame(appId, name, installDir)
        } else {
            null
        }
    }

    private fun extractValue(line: String): String? {
        val quotePattern = Regex("[\"']([^\"']*)[\"']\\s+[\"']([^\"']*)[\"']")
        val match = quotePattern.find(line)
        return match?.groupValues?.get(2)
    }
}
