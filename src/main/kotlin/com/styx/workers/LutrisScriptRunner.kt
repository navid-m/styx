package com.styx.workers

import com.styx.models.*
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.net.URL
import java.nio.file.Files
import javax.swing.SwingUtilities

class LutrisScriptRunner(
    private val script: InstallScript,
    private val gameDir: String,
    private val outputCallback: (String, String?) -> Unit
) {
    private val downloadedFiles = mutableMapOf<String, String>()
    
    fun executeInstaller(): Boolean {
        outputCallback("=== Starting Script Execution ===", "#0066cc")
        outputCallback("Game Directory: $gameDir", null)
        outputCallback("", null)
        
        if (!downloadFiles()) {
            outputCallback("ERROR: Failed to download required files", "#cc0000")
            return false
        }
        
        val tasks = script.installer ?: emptyList()
        for ((index, taskWrapper) in tasks.withIndex()) {
            val task = taskWrapper.task
            outputCallback("", null)
            outputCallback("--- Task ${index + 1}/${tasks.size}: ${task.name} ---", "#0066cc")
            task.description?.let { outputCallback("Description: $it", "#00aa00") }
            
            if (!executeTask(task)) {
                outputCallback("ERROR: Task '${task.name}' failed", "#cc0000")
                return false
            }
        }
        
        outputCallback("", null)
        outputCallback("=== Installation Script Completed Successfully ===", "#008800")
        return true
    }
    
    private fun downloadFiles(): Boolean {
        val files = script.files ?: return true
        
        if (files.isEmpty()) return true
        
        outputCallback("=== Downloading Files ===", "#0066cc")
        
        for (file in files) {
            val fileId = file.id
            val url = file.url
            
            outputCallback("Downloading $fileId from $url...", null)
            
            try {
                val fileName = url.substringAfterLast('/')
                val downloadPath = File(gameDir, "downloads")
                downloadPath.mkdirs()
                val destFile = File(downloadPath, fileName)
                
                if (destFile.exists()) {
                    outputCallback("  File already exists, skipping download", "#00aa00")
                } else {
                    URL(url).openStream().use { input ->
                        Files.copy(input, destFile.toPath())
                    }
                    outputCallback("  Downloaded to: ${destFile.absolutePath}", "#008800")
                }
                
                downloadedFiles[fileId] = destFile.absolutePath
            } catch (e: Exception) {
                outputCallback("  ERROR: Failed to download: ${e.message}", "#cc0000")
                return false
            }
        }
        
        outputCallback("All files downloaded successfully", "#008800")
        return true
    }
    
    private fun executeTask(task: ScriptableTaskDetails): Boolean {
        return when (task.name) {
            "create_prefix" -> createPrefix(task)
            "winetricks" -> runWinetricks(task)
            "wineexec" -> runWineExec(task)
            "winekill" -> runWineKill(task)
            else -> {
                outputCallback("WARNING: Unknown task type '${task.name}', skipping", "#cc6600")
                true
            }
        }
    }
    
    private fun createPrefix(task: ScriptableTaskDetails): Boolean {
        val prefix = resolveVariable(task.prefix ?: gameDir)
        val arch = task.arch ?: "win64"
        
        outputCallback("Creating Wine prefix at: $prefix", null)
        outputCallback("Architecture: $arch", null)
        
        val prefixDir = File(prefix)
        if (prefixDir.exists()) {
            outputCallback("Prefix already exists, skipping creation", "#00aa00")
            return true
        }
        
        try {
            prefixDir.mkdirs()
            
            val pb = ProcessBuilder("wineboot", "--init")
            pb.environment()["WINEPREFIX"] = prefix
            pb.environment()["WINEARCH"] = arch
            pb.environment()["WINEDEBUG"] = "-all"
            
            val process = pb.start()
            val exitCode = process.waitFor()
            
            if (exitCode == 0) {
                outputCallback("Wine prefix created successfully", "#008800")
                return true
            } else {
                outputCallback("ERROR: wineboot exited with code $exitCode", "#cc0000")
                return false
            }
        } catch (e: Exception) {
            outputCallback("ERROR: ${e.message}", "#cc0000")
            return false
        }
    }
    
    private fun runWinetricks(task: ScriptableTaskDetails): Boolean {
        val prefix = resolveVariable(task.prefix ?: gameDir)
        val apps = task.app?.split(" ") ?: emptyList()
        
        if (apps.isEmpty()) {
            outputCallback("WARNING: No apps specified for winetricks", "#cc6600")
            return true
        }
        
        outputCallback("Running winetricks with: ${apps.joinToString(", ")}", null)
        
        try {
            val pb = ProcessBuilder()
            val command = mutableListOf("winetricks", "-q")
            command.addAll(apps)
            pb.command(command)
            pb.environment()["WINEPREFIX"] = prefix
            
            val process = pb.start()
            
            Thread {
                process.inputStream.bufferedReader().use { reader ->
                    reader.lineSequence().forEach { line ->
                        SwingUtilities.invokeLater {
                            outputCallback("  [winetricks] $line", null)
                        }
                    }
                }
            }.start()
            
            val exitCode = process.waitFor()
            
            if (exitCode == 0) {
                outputCallback("Winetricks completed successfully", "#008800")
                return true
            } else {
                outputCallback("WARNING: Winetricks exited with code $exitCode", "#cc6600")
                return true
            }
        } catch (e: Exception) {
            outputCallback("ERROR: ${e.message}", "#cc0000")
            return false
        }
    }
    
    private fun runWineExec(task: ScriptableTaskDetails): Boolean {
        val prefix = resolveVariable(task.prefix ?: gameDir)
        val executableId = task.executable ?: run {
            outputCallback("ERROR: No executable specified", "#cc0000")
            return false
        }
        
        val executablePath = downloadedFiles[executableId] ?: run {
            outputCallback("ERROR: Executable '$executableId' not found in downloaded files", "#cc0000")
            return false
        }
        
        outputCallback("Executing: $executablePath", null)
        task.exclude_processes?.let {
            outputCallback("Monitoring for processes: $it", "#00aa00")
        }
        
        try {
            val pb = ProcessBuilder("wine", executablePath)
            pb.environment()["WINEPREFIX"] = prefix
            pb.environment()["WINEDEBUG"] = "-all"
            
            val process = pb.start()
            
            if (task.exclude_processes != null) {
                val excludeList = task.exclude_processes.split(" ").map { it.trim() }
                outputCallback("Waiting for processes: ${excludeList.joinToString(", ")}", null)
                
                Thread {
                    while (process.isAlive) {
                        Thread.sleep(2000)
                        
                        try {
                            val psProcess = ProcessBuilder("pgrep", "-f", excludeList.firstOrNull() ?: "").start()
                            if (psProcess.waitFor() == 0) {
                                outputCallback("Detected process, continuing...", "#00aa00")
                                break
                            }
                        } catch (e: Exception) {
                            // Ignore
                        }
                    }
                }.start()
            }
            
            val exitCode = process.waitFor()
            outputCallback("Executable finished with exit code: $exitCode", if (exitCode == 0) "#008800" else "#cc6600")
            return true
        } catch (e: Exception) {
            outputCallback("ERROR: ${e.message}", "#cc0000")
            return false
        }
    }
    
    private fun runWineKill(task: ScriptableTaskDetails): Boolean {
        val prefix = resolveVariable(task.prefix ?: gameDir)
        
        outputCallback("Killing all Wine processes in prefix...", null)
        
        try {
            val pb = ProcessBuilder("wineserver", "-k")
            pb.environment()["WINEPREFIX"] = prefix
            
            val process = pb.start()
            val exitCode = process.waitFor()
            
            Thread.sleep(1000)
            
            outputCallback("Wine processes terminated", "#008800")
            return true
        } catch (e: Exception) {
            outputCallback("WARNING: ${e.message}", "#cc6600")
            return true
        }
    }
    
    private fun resolveVariable(value: String): String {
        return value.replace("\$GAMEDIR", gameDir)
    }
    
    companion object {
        fun parseYaml(yamlContent: String): InstallScript? {
            return try {
                val yaml = Yaml()
                val data = yaml.load<Map<String, Any>>(yamlContent)
                val files = (data["files"] as? List<*>)?.mapNotNull { fileData ->
                    when (fileData) {
                        is Map<*, *> -> {
                            val id = fileData.keys.firstOrNull()?.toString() ?: return@mapNotNull null
                            val url = fileData[id]?.toString() ?: return@mapNotNull null
                            LutrisFile(id, url)
                        }
                        else -> null
                    }
                }
                
                val gameData = data["game"] as? Map<*, *>
                val game = gameData?.let {
                    LutrisGame(
                        arch = it["arch"]?.toString(),
                        exe = it["exe"]?.toString(),
                        prefix = it["prefix"]?.toString()
                    )
                }
                
                val installerData = data["installer"] as? List<*>
                val installer = installerData?.mapNotNull { taskData ->
                    val taskMap = taskData as? Map<*, *> ?: return@mapNotNull null
                    val taskDetails = taskMap["task"] as? Map<*, *> ?: return@mapNotNull null
                    
                    ScriptableTask(
                        ScriptableTaskDetails(
                            name = taskDetails["name"]?.toString() ?: return@mapNotNull null,
                            arch = taskDetails["arch"]?.toString(),
                            prefix = taskDetails["prefix"]?.toString(),
                            description = taskDetails["description"]?.toString(),
                            app = taskDetails["app"]?.toString(),
                            executable = taskDetails["executable"]?.toString(),
                            exclude_processes = taskDetails["exclude_processes"]?.toString()
                        )
                    )
                }
                
                val systemData = data["system"] as? Map<*, *>
                val system = systemData?.let {
                    val envData = it["env"] as? Map<*, *>
                    val env = envData?.mapNotNull { (k, v) ->
                        k.toString() to v.toString()
                    }?.toMap()
                    ScriptableSystem(env)
                }
                
                val wineData = data["wine"] as? Map<*, *>
                val wine = wineData?.let {
                    val overridesData = it["overrides"] as? Map<*, *>
                    val overrides = overridesData?.mapNotNull { (k, v) ->
                        k.toString() to v.toString()
                    }?.toMap()
                    LutrisWine(overrides)
                }
                
                InstallScript(files, game, installer, system, wine)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
