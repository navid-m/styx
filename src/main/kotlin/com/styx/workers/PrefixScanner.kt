package com.styx.workers

import com.styx.models.PrefixInfo
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.SwingWorker
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

class PrefixScanner : SwingWorker<List<PrefixInfo>, Void>() {
    override fun doInBackground(): List<PrefixInfo> {
        return scanWinePrefixes()
    }

    private fun scanWinePrefixes(): List<PrefixInfo> {
        val prefixes = mutableListOf<PrefixInfo>()
        val seenPaths = mutableSetOf<String>()

        val startTime = System.currentTimeMillis()

        val home = Paths.get(System.getProperty("user.home"))
        val commonLocations = listOf(
            home.resolve(".steam/steam/steamapps/compatdata"),
            home.resolve(".local/share/Steam/steamapps/compatdata"),
            Paths.get("/usr/share/Steam/steamapps/compatdata")
        )

        for (compatdataPath in commonLocations) {
            if (compatdataPath.exists()) {
                try {
                    compatdataPath.listDirectoryEntries().forEach { prefixDir ->
                        if (prefixDir.isDirectory()) {
                            val pfxPath = prefixDir.resolve("pfx")
                            if (pfxPath.exists()) {
                                val pfxPathStr = pfxPath.absolutePathString()
                                if (pfxPathStr !in seenPaths) {
                                    seenPaths.add(pfxPathStr)
                                    prefixes.add(PrefixInfo("Proton - ${prefixDir.name}", pfxPathStr))
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    println("[SCAN] Error scanning $compatdataPath: ${e.message}")
                }
            }
        }

        val mountPoints = mutableListOf<String>()

        try {
            val mntPath = Paths.get("/mnt")
            if (mntPath.exists()) {
                mntPath.listDirectoryEntries().forEach { dir ->
                    if (dir.isDirectory()) {
                        mountPoints.add(dir.absolutePathString())
                    }
                }
            }
        } catch (e: Exception) {
            println("[SCAN] Error scanning /mnt: ${e.message}")
        }

        try {
            val mediaPath = Paths.get("/media")
            if (mediaPath.exists()) {
                mediaPath.listDirectoryEntries().forEach { userPath ->
                    try {
                        if (userPath.isDirectory()) {
                            userPath.listDirectoryEntries().forEach { dir ->
                                if (dir.isDirectory()) {
                                    mountPoints.add(dir.absolutePathString())
                                }
                            }
                        }
                    } catch (e: Exception) {
                        println("[SCAN] Error scanning ${userPath}: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            println("[SCAN] Error scanning /media: ${e.message}")
        }

        val skipDirs = setOf(
            "proc", "sys", "dev", "run", "tmp", "snap", "var", "boot", "srv",
            "lost+found", ".cache", ".local/share/Trash", "node_modules", ".git",
            ".svn", "__pycache__", "venv", "virtualenv", "site-packages",
            "Windows", "Program Files", "Program Files (x86)", "windows",
            "dosdevices", "drive_c"
        )

        for (mount in mountPoints) {
            try {
                scanMountForPrefixes(Paths.get(mount), seenPaths, prefixes, skipDirs, 0, 1)
            } catch (e: Exception) {
                println("[SCAN] Error scanning mount $mount: ${e.message}")
            }
        }

        return prefixes
    }

    private fun scanMountForPrefixes(
        path: Path,
        seenPaths: MutableSet<String>,
        prefixes: MutableList<PrefixInfo>,
        skipDirs: Set<String>,
        depth: Int,
        maxDepth: Int
    ) {
        if (depth > maxDepth) {
            return
        }
        if (!path.exists() || !path.isDirectory()) return

        try {
            val steamappsPath = path.resolve("steamapps")
            if (steamappsPath.exists() && steamappsPath.isDirectory()) {
                println("[SCAN] Found steamapps at: $path")
                val compatdataPath = steamappsPath.resolve("compatdata")
                if (compatdataPath.exists() && compatdataPath.isDirectory()) {
                    try {
                        compatdataPath.listDirectoryEntries().forEach { prefixDir ->
                            if (prefixDir.isDirectory()) {
                                val pfxPath = prefixDir.resolve("pfx")
                                if (pfxPath.exists()) {
                                    val pfxPathStr = pfxPath.absolutePathString()
                                    if (pfxPathStr !in seenPaths) {
                                        seenPaths.add(pfxPathStr)
                                        prefixes.add(PrefixInfo("Proton - ${prefixDir.name}", pfxPathStr))
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        println("[SCAN] Error processing compatdata: ${e.message}")
                    }
                }
                return
            }

            val entries = path.listDirectoryEntries()
            var skippedCount = 0
            var scannedCount = 0

            for (entry in entries) {
                val fileName = entry.fileName.toString()
                if (entry.isDirectory()) {
                    if (fileName in skipDirs || fileName.startsWith(".")) {
                        skippedCount++
                    } else {
                        scannedCount++
                        scanMountForPrefixes(entry, seenPaths, prefixes, skipDirs, depth + 1, maxDepth)
                    }
                }
            }

        } catch (e: Exception) {
            println("[SCAN] Error at $path: ${e.message}")
        }
    }
}