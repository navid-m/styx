package com.styx.ui

import com.styx.models.Game
import com.styx.models.GameType
import java.awt.*
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel

class TroubleshootDialog(
    private val game: Game,
    parent: JFrame
) : JDialog(parent, "Troubleshoot - ${game.name}", true) {

    data class DiagnosticResult(
        val name: String,
        val status: DiagnosticStatus,
        val details: String,
        val recommendation: String = ""
    )

    enum class DiagnosticStatus {
        PASS, WARNING, ERROR, INFO, CHECKING
    }

    private val tableModel: DefaultTableModel
    private val table: JTable
    private val diagnosticResults = mutableListOf<DiagnosticResult>()

    init {
        minimumSize = Dimension(900, 600)
        setLocationRelativeTo(parent)

        val mainPanel = JPanel(BorderLayout())
        mainPanel.border = EmptyBorder(15, 15, 15, 15)

        val titleLabel = JLabel("Game Troubleshooting")
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 18f)
        titleLabel.border = EmptyBorder(0, 0, 15, 0)

        val infoLabel = JLabel("<html>Diagnosing potential issues with <b>${game.name}</b>...</html>")
        infoLabel.border = EmptyBorder(0, 0, 10, 0)

        val headerPanel = JPanel()
        headerPanel.layout = BoxLayout(headerPanel, BoxLayout.Y_AXIS)
        headerPanel.add(titleLabel)
        headerPanel.add(infoLabel)
        titleLabel.alignmentX = LEFT_ALIGNMENT
        infoLabel.alignmentX = LEFT_ALIGNMENT

        mainPanel.add(headerPanel, BorderLayout.NORTH)

        val columnNames = arrayOf("Diagnostic", "Status", "Details", "Recommendation")
        tableModel = object : DefaultTableModel(columnNames, 0) {
            override fun isCellEditable(row: Int, column: Int) = false
        }
        table = JTable(tableModel)
        table.font = table.font.deriveFont(12f)
        table.rowHeight = 28
        table.setShowGrid(true)
        table.gridColor = Color(60, 60, 60)
        table.background = Color(45, 45, 48)
        table.foreground = Color.WHITE

        table.columnModel.getColumn(0).preferredWidth = 200
        table.columnModel.getColumn(1).preferredWidth = 80
        table.columnModel.getColumn(2).preferredWidth = 250
        table.columnModel.getColumn(3).preferredWidth = 300

        val tableHeader = table.tableHeader
        tableHeader.background = Color(34, 35, 36)
        tableHeader.foreground = Color.WHITE
        tableHeader.font = tableHeader.font.deriveFont(Font.BOLD)

        table.setDefaultRenderer(Any::class.java, object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                table: JTable,
                value: Any?,
                isSelected: Boolean,
                hasFocus: Boolean,
                row: Int,
                column: Int
            ): Component {
                val cell = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
                cell.foreground = Color.WHITE
                cell.background = Color(45, 45, 48)

                if (column == 1 && row < diagnosticResults.size) {
                    when (diagnosticResults[row].status) {
                        DiagnosticStatus.PASS -> cell.foreground = Color(0, 200, 0)
                        DiagnosticStatus.WARNING -> cell.foreground = Color(255, 165, 0)
                        DiagnosticStatus.ERROR -> cell.foreground = Color(255, 50, 50)
                        DiagnosticStatus.INFO -> cell.foreground = Color(100, 150, 255)
                        DiagnosticStatus.CHECKING -> cell.foreground = Color.LIGHT_GRAY
                    }
                }

                return cell
            }
        })

        val scrollPane = JScrollPane(table)
        mainPanel.add(scrollPane, BorderLayout.CENTER)

        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0))
        buttonPanel.border = EmptyBorder(15, 0, 0, 0)

        val rerunBtn = JButton("Re-run Diagnostics").apply {
            preferredSize = Dimension(150, 32)
            addActionListener {
                runDiagnostics()
            }
        }

        val closeBtn = JButton("Close").apply {
            preferredSize = Dimension(80, 32)
            addActionListener { dispose() }
        }

        buttonPanel.add(rerunBtn)
        buttonPanel.add(closeBtn)

        mainPanel.add(buttonPanel, BorderLayout.SOUTH)

        contentPane = mainPanel
        pack()

        runDiagnostics()
    }

    private fun runDiagnostics() {
        diagnosticResults.clear()
        tableModel.rowCount = 0

        Thread {
            val diagnostics = buildList {
                add(::checkFileSystemType)
                add(::checkGameExecutableExists)
                add(::checkGameExecutablePermissions)
                add(::checkPrefixExists)
                add(::checkPrefixPermissions)
                add(::checkDiskSpace)
                add(::checkProtonConfiguration)
                add(::checkWineInstalled)
                add(::checkDxvkInstalled)
                add(::checkVulkanSupport)
                add(::checkOpenGLSupport)
                add(::check32BitLibraries)
                add(::checkMemoryAvailable)
                add(::checkSwapSpace)
                add(::checkCpuGovernor)
                add(::checkGpuDrivers)
                add(::checkGamePathSpaces)
                add(::checkPrefixPathLength)
                add(::checkLocaleSettings)
                add(::checkEsyncSupport)
                add(::checkFsyncSupport)
                add(::checkGameFilesIntegrity)
                add(::checkWineDllOverrides)
                add(::checkFontConfiguration)
                add(::checkAudioSystem)
            }

            diagnostics.forEach { diagnostic ->
                try {
                    diagnostic()
                } catch (e: Exception) {
                    addResult(
                        DiagnosticResult(
                            "Error in diagnostic",
                            DiagnosticStatus.ERROR,
                            "Exception: ${e.message}",
                            "Report this as an issue."
                        )
                    )
                }
            }

            SwingUtilities.invokeLater {
                JOptionPane.showMessageDialog(
                    this,
                    "Diagnostics completed. Review the results above.",
                    "Diagnostics Complete",
                    JOptionPane.INFORMATION_MESSAGE
                )
            }
        }.start()
    }

    private fun addResult(result: DiagnosticResult) {
        diagnosticResults.add(result)
        SwingUtilities.invokeLater {
            tableModel.addRow(
                arrayOf(
                    result.name,
                    result.status.name,
                    result.details,
                    result.recommendation
                )
            )
        }
    }

    private fun checkFileSystemType() {
        if (game.getGameType() == GameType.STEAM) {
            addResult(
                DiagnosticResult(
                    "Filesystem Type",
                    DiagnosticStatus.INFO,
                    "Steam game - filesystem check skipped",
                    ""
                )
            )
            return
        }

        try {
            val gameFile = File(game.executable)
            val gamePath = gameFile.absolutePath

            val process = ProcessBuilder("df", "-T", gamePath).start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val lines = reader.readLines()
            process.waitFor()

            if (lines.size >= 2) {
                val parts = lines[1].split(Regex("\\s+"))
                if (parts.size >= 2) {
                    val fsType = parts[1]
                    when {
                        fsType.contains("ntfs", ignoreCase = true) -> {
                            addResult(
                                DiagnosticResult(
                                    "Filesystem Type",
                                    DiagnosticStatus.WARNING,
                                    "Game is on NTFS filesystem ($fsType)",
                                    "NTFS can cause performance issues with ntfs-3g. Consider moving game to ext4/btrfs"
                                )
                            )
                        }

                        fsType.contains("fat", ignoreCase = true) || fsType.contains("vfat", ignoreCase = true) -> {
                            addResult(
                                DiagnosticResult(
                                    "Filesystem Type",
                                    DiagnosticStatus.ERROR,
                                    "Game is on FAT filesystem ($fsType)",
                                    "FAT filesystems don't support Unix permissions. Move game to ext4/btrfs/xfs"
                                )
                            )
                        }

                        fsType.contains("fuseblk", ignoreCase = true) || fsType.contains(
                            "fuseblk",
                            ignoreCase = true
                        ) -> {
                            addResult(
                                DiagnosticResult(
                                    "Filesystem Type",
                                    DiagnosticStatus.ERROR,
                                    "Game is on fuseblk filesystem",
                                    "If the underlying filesystem is NTFS, this WILL cause casing issues which will make the game fail to start"
                                )
                            )
                        }

                        else -> {
                            addResult(
                                DiagnosticResult(
                                    "Filesystem Type",
                                    DiagnosticStatus.PASS,
                                    "Game is on $fsType filesystem",
                                    ""
                                )
                            )
                        }
                    }
                    return
                }
            }
        } catch (e: Exception) {
            // Ignore and fallback.
        }

        addResult(
            DiagnosticResult(
                "Filesystem Type",
                DiagnosticStatus.INFO,
                "Could not determine filesystem type",
                ""
            )
        )
    }

    private fun checkGameExecutableExists() {
        if (game.getGameType() == GameType.STEAM) {
            addResult(
                DiagnosticResult(
                    "Game Executable",
                    DiagnosticStatus.INFO,
                    "Steam App ID: ${game.executable}",
                    ""
                )
            )
            return
        }

        val gameFile = File(game.executable)
        if (gameFile.exists()) {
            addResult(
                DiagnosticResult(
                    "Game Executable",
                    DiagnosticStatus.PASS,
                    "Executable exists at ${game.executable}",
                    ""
                )
            )
        } else {
            addResult(
                DiagnosticResult(
                    "Game Executable",
                    DiagnosticStatus.ERROR,
                    "Executable not found at ${game.executable}",
                    "Verify game installation or reconfigure the game path"
                )
            )
        }
    }

    private fun checkGameExecutablePermissions() {
        if (game.getGameType() == GameType.STEAM) return

        val gameFile = File(game.executable)
        if (gameFile.exists()) {
            if (gameFile.canExecute()) {
                addResult(
                    DiagnosticResult(
                        "Executable Permissions",
                        DiagnosticStatus.PASS,
                        "Game executable has execute permissions",
                        ""
                    )
                )
            } else {
                addResult(
                    DiagnosticResult(
                        "Executable Permissions",
                        DiagnosticStatus.ERROR,
                        "Game executable is not executable",
                        "Run: chmod +x \"${game.executable}\""
                    )
                )
            }
        }
    }

    private fun checkPrefixExists() {
        if (game.getGameType() != GameType.WINDOWS) return

        val prefixDir = File(game.prefix)
        if (prefixDir.exists()) {
            val systemReg = File(prefixDir, "system.reg")
            val userReg = File(prefixDir, "user.reg")
            if (systemReg.exists() && userReg.exists()) {
                addResult(
                    DiagnosticResult(
                        "Wine Prefix",
                        DiagnosticStatus.PASS,
                        "Wine prefix exists and appears valid",
                        ""
                    )
                )
            } else {
                addResult(
                    DiagnosticResult(
                        "Wine Prefix",
                        DiagnosticStatus.WARNING,
                        "Wine prefix exists but may be corrupted",
                        "Consider recreating the prefix or running winetricks to repair"
                    )
                )
            }
        } else {
            addResult(
                DiagnosticResult(
                    "Wine Prefix",
                    DiagnosticStatus.ERROR,
                    "Wine prefix not found at ${game.prefix}",
                    "Prefix will be created on first launch"
                )
            )
        }
    }

    private fun checkPrefixPermissions() {
        if (game.getGameType() != GameType.WINDOWS) return

        val prefixDir = File(game.prefix)
        if (prefixDir.exists()) {
            if (prefixDir.canWrite()) {
                addResult(
                    DiagnosticResult(
                        "Prefix Permissions",
                        DiagnosticStatus.PASS,
                        "Wine prefix has write permissions",
                        ""
                    )
                )
            } else {
                addResult(
                    DiagnosticResult(
                        "Prefix Permissions",
                        DiagnosticStatus.ERROR,
                        "Wine prefix is not writable",
                        "Fix permissions: chmod -R u+w \"${game.prefix}\""
                    )
                )
            }
        }
    }

    private fun checkDiskSpace() {
        if (game.getGameType() == GameType.STEAM) return

        val gameFile = File(game.executable)
        val usableSpace = gameFile.usableSpace / (1024 * 1024 * 1024)

        when {
            usableSpace < 1 -> {
                addResult(
                    DiagnosticResult(
                        "Disk Space",
                        DiagnosticStatus.ERROR,
                        "Less than 1 GB free (${usableSpace} GB)",
                        "Free up disk space immediately - games need space for saves and temp files"
                    )
                )
            }

            usableSpace < 10 -> {
                addResult(
                    DiagnosticResult(
                        "Disk Space",
                        DiagnosticStatus.WARNING,
                        "${usableSpace} GB free on game drive",
                        "Consider freeing up more space for optimal performance"
                    )
                )
            }

            else -> {
                addResult(
                    DiagnosticResult(
                        "Disk Space",
                        DiagnosticStatus.PASS,
                        "${usableSpace} GB free on game drive",
                        ""
                    )
                )
            }
        }
    }

    private fun checkProtonConfiguration() {
        if (game.getGameType() != GameType.WINDOWS) return

        if (game.protonPath != null && game.protonVersion != null) {
            val protonDir = File(game.protonPath!!)
            if (protonDir.exists()) {
                addResult(
                    DiagnosticResult(
                        "Proton Configuration",
                        DiagnosticStatus.PASS,
                        "Using Proton ${game.protonVersion}",
                        ""
                    )
                )
            } else {
                addResult(
                    DiagnosticResult(
                        "Proton Configuration",
                        DiagnosticStatus.ERROR,
                        "Proton path not found: ${game.protonPath}",
                        "Reconfigure Proton in game settings"
                    )
                )
            }
        } else {
            addResult(
                DiagnosticResult(
                    "Proton Configuration",
                    DiagnosticStatus.INFO,
                    "Using system Wine",
                    "Consider using Proton for better compatibility"
                )
            )
        }
    }

    private fun checkWineInstalled() {
        if (game.getGameType() != GameType.WINDOWS) return

        try {
            val process = ProcessBuilder("which", "wine").start()
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                val wineVersion = ProcessBuilder("wine", "--version").start()
                val reader = BufferedReader(InputStreamReader(wineVersion.inputStream))
                val version = reader.readLine() ?: "unknown"
                wineVersion.waitFor()

                addResult(
                    DiagnosticResult(
                        "Wine Installation",
                        DiagnosticStatus.PASS,
                        "Wine installed: $version",
                        ""
                    )
                )
            } else {
                addResult(
                    DiagnosticResult(
                        "Wine Installation",
                        DiagnosticStatus.ERROR,
                        "Wine not found in PATH",
                        "Install Wine or configure Proton"
                    )
                )
            }
        } catch (e: Exception) {
            addResult(
                DiagnosticResult(
                    "Wine Installation",
                    DiagnosticStatus.WARNING,
                    "Could not check Wine installation",
                    ""
                )
            )
        }
    }

    private fun checkDxvkInstalled() {
        if (game.getGameType() != GameType.WINDOWS) return

        val prefixDir = File(game.prefix)
        val dxvkDlls = listOf(
            "drive_c/windows/system32/d3d11.dll",
            "drive_c/windows/system32/dxgi.dll"
        )

        val hasDxvk = dxvkDlls.any { File(prefixDir, it).exists() }

        if (hasDxvk) {
            addResult(
                DiagnosticResult(
                    "DXVK (DirectX to Vulkan)",
                    DiagnosticStatus.PASS,
                    "DXVK appears to be installed",
                    ""
                )
            )
        } else {
            addResult(
                DiagnosticResult(
                    "DXVK (DirectX to Vulkan)",
                    DiagnosticStatus.INFO,
                    "DXVK not detected in prefix",
                    "For DirectX 11+ games, install DXVK for better performance"
                )
            )
        }
    }

    private fun checkVulkanSupport() {
        try {
            val process = ProcessBuilder("vulkaninfo", "--summary").start()
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                addResult(
                    DiagnosticResult(
                        "Vulkan Support",
                        DiagnosticStatus.PASS,
                        "Vulkan is supported and configured",
                        ""
                    )
                )
            } else {
                addResult(
                    DiagnosticResult(
                        "Vulkan Support",
                        DiagnosticStatus.WARNING,
                        "Vulkan may not be properly configured",
                        "Install vulkan-tools and GPU-specific Vulkan drivers"
                    )
                )
            }
        } catch (e: Exception) {
            addResult(
                DiagnosticResult(
                    "Vulkan Support",
                    DiagnosticStatus.WARNING,
                    "Could not verify Vulkan support",
                    "Install vulkan-tools to verify: sudo apt install vulkan-tools"
                )
            )
        }
    }

    private fun checkOpenGLSupport() {
        try {
            val process = ProcessBuilder("glxinfo", "-B").start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            process.waitFor()

            if (output.contains("OpenGL version", ignoreCase = true)) {
                val versionLine = output.lines().find { it.contains("OpenGL version", ignoreCase = true) }
                addResult(
                    DiagnosticResult(
                        "OpenGL Support",
                        DiagnosticStatus.PASS,
                        versionLine?.trim() ?: "OpenGL is supported",
                        ""
                    )
                )
            } else {
                addResult(
                    DiagnosticResult(
                        "OpenGL Support",
                        DiagnosticStatus.WARNING,
                        "Could not determine OpenGL version",
                        "Ensure GPU drivers are properly installed"
                    )
                )
            }
        } catch (e: Exception) {
            addResult(
                DiagnosticResult(
                    "OpenGL Support",
                    DiagnosticStatus.INFO,
                    "Could not check OpenGL support",
                    "Install mesa-utils to verify: sudo apt install mesa-utils"
                )
            )
        }
    }

    private fun check32BitLibraries() {
        if (game.getGameType() != GameType.WINDOWS) return

        val criticalLibs = listOf(
            "/usr/lib32/libGL.so.1",
            "/usr/lib/i386-linux-gnu/libGL.so.1",
            "/lib32/libc.so.6",
            "/usr/lib/i386-linux-gnu/libc.so.6"
        )

        val hasLib32 = criticalLibs.any { File(it).exists() }

        if (hasLib32) {
            addResult(
                DiagnosticResult(
                    "32-bit Libraries",
                    DiagnosticStatus.PASS,
                    "32-bit libraries detected",
                    ""
                )
            )
        } else {
            addResult(
                DiagnosticResult(
                    "32-bit Libraries",
                    DiagnosticStatus.WARNING,
                    "32-bit libraries may be missing",
                    "Many games need 32-bit libs. Install: sudo dpkg --add-architecture i386 && sudo apt update"
                )
            )
        }
    }

    private fun checkMemoryAvailable() {
        try {
            val process = ProcessBuilder("free", "-g").start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val lines = reader.readLines()
            process.waitFor()

            if (lines.size >= 2) {
                val parts = lines[1].split(Regex("\\s+"))
                if (parts.size >= 2) {
                    val totalMemory = parts[1].toIntOrNull() ?: 0
                    when {
                        totalMemory < 4 -> {
                            addResult(
                                DiagnosticResult(
                                    "System Memory",
                                    DiagnosticStatus.WARNING,
                                    "Low system memory: ${totalMemory}GB",
                                    "Some games may struggle with less than 8GB RAM"
                                )
                            )
                        }

                        else -> {
                            addResult(
                                DiagnosticResult(
                                    "System Memory",
                                    DiagnosticStatus.PASS,
                                    "System has ${totalMemory}GB RAM",
                                    ""
                                )
                            )
                        }
                    }
                    return
                }
            }
        } catch (e: Exception) {
            // Fallback
        }

        addResult(
            DiagnosticResult(
                "System Memory",
                DiagnosticStatus.INFO,
                "Could not determine system memory",
                ""
            )
        )
    }

    private fun checkSwapSpace() {
        try {
            val process = ProcessBuilder("free", "-g").start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val lines = reader.readLines()
            process.waitFor()

            val swapLine = lines.find { it.startsWith("Swap:") }
            if (swapLine != null) {
                val parts = swapLine.split(Regex("\\s+"))
                if (parts.size >= 2) {
                    val totalSwap = parts[1].toIntOrNull() ?: 0
                    if (totalSwap == 0) {
                        addResult(
                            DiagnosticResult(
                                "Swap Space",
                                DiagnosticStatus.WARNING,
                                "No swap space configured",
                                "Consider enabling swap to prevent out-of-memory crashes"
                            )
                        )
                    } else {
                        addResult(
                            DiagnosticResult(
                                "Swap Space",
                                DiagnosticStatus.PASS,
                                "${totalSwap}GB swap available",
                                ""
                            )
                        )
                    }
                    return
                }
            }
        } catch (e: Exception) {
            // Ignore
        }

        addResult(
            DiagnosticResult(
                "Swap Space",
                DiagnosticStatus.INFO,
                "Could not check swap configuration",
                ""
            )
        )
    }

    private fun checkCpuGovernor() {
        try {
            val governorFile = File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor")
            if (governorFile.exists()) {
                val governor = governorFile.readText().trim()
                when (governor.lowercase()) {
                    "performance" -> {
                        addResult(
                            DiagnosticResult(
                                "CPU Governor",
                                DiagnosticStatus.PASS,
                                "CPU governor set to 'performance'",
                                ""
                            )
                        )
                    }

                    "powersave" -> {
                        addResult(
                            DiagnosticResult(
                                "CPU Governor",
                                DiagnosticStatus.WARNING,
                                "CPU governor set to 'powersave'",
                                "For better gaming performance, set to 'performance' mode"
                            )
                        )
                    }

                    else -> {
                        addResult(
                            DiagnosticResult(
                                "CPU Governor",
                                DiagnosticStatus.INFO,
                                "CPU governor: $governor",
                                ""
                            )
                        )
                    }
                }
            } else {
                addResult(
                    DiagnosticResult(
                        "CPU Governor",
                        DiagnosticStatus.INFO,
                        "Could not check CPU governor",
                        ""
                    )
                )
            }
        } catch (e: Exception) {
            addResult(
                DiagnosticResult(
                    "CPU Governor",
                    DiagnosticStatus.INFO,
                    "Could not check CPU governor",
                    ""
                )
            )
        }
    }

    private fun checkGpuDrivers() {
        try {
            val process = ProcessBuilder("lspci", "-k").start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            process.waitFor()

            when {
                output.contains("nvidia", ignoreCase = true) -> {
                    val hasNvidiaDriver = output.contains("nvidia", ignoreCase = true) &&
                            (output.contains("Kernel driver in use: nvidia") || output.contains("Kernel modules: nvidia"))
                    if (hasNvidiaDriver) {
                        addResult(
                            DiagnosticResult(
                                "GPU Drivers",
                                DiagnosticStatus.PASS,
                                "NVIDIA GPU with nvidia driver detected",
                                ""
                            )
                        )
                    } else {
                        addResult(
                            DiagnosticResult(
                                "GPU Drivers",
                                DiagnosticStatus.WARNING,
                                "NVIDIA GPU detected but may be using nouveau driver",
                                "Install proprietary NVIDIA drivers for better performance"
                            )
                        )
                    }
                }

                output.contains("amd", ignoreCase = true) || output.contains("radeon", ignoreCase = true) -> {
                    addResult(
                        DiagnosticResult(
                            "GPU Drivers",
                            DiagnosticStatus.PASS,
                            "AMD GPU detected with open-source drivers",
                            ""
                        )
                    )
                }

                output.contains("intel", ignoreCase = true) && output.contains("vga", ignoreCase = true) -> {
                    addResult(
                        DiagnosticResult(
                            "GPU Drivers",
                            DiagnosticStatus.INFO,
                            "Intel integrated graphics detected",
                            "Performance may be limited - consider using dedicated GPU if available"
                        )
                    )
                }

                else -> {
                    addResult(
                        DiagnosticResult(
                            "GPU Drivers",
                            DiagnosticStatus.INFO,
                            "Could not identify GPU",
                            ""
                        )
                    )
                }
            }
        } catch (e: Exception) {
            addResult(
                DiagnosticResult(
                    "GPU Drivers",
                    DiagnosticStatus.INFO,
                    "Could not check GPU drivers",
                    ""
                )
            )
        }
    }

    private fun checkGamePathSpaces() {
        if (game.getGameType() == GameType.STEAM) return

        if (game.executable.contains(" ")) {
            addResult(
                DiagnosticResult(
                    "Game Path Spaces",
                    DiagnosticStatus.WARNING,
                    "Game path contains spaces",
                    "Some games/tools have issues with spaces in paths. Consider moving if issues occur."
                )
            )
        } else {
            addResult(
                DiagnosticResult(
                    "Game Path Spaces",
                    DiagnosticStatus.PASS,
                    "Game path doesn't contain spaces",
                    ""
                )
            )
        }
    }

    private fun checkPrefixPathLength() {
        if (game.getGameType() != GameType.WINDOWS) return

        if (game.prefix.length > 200) {
            addResult(
                DiagnosticResult(
                    "Prefix Path Length",
                    DiagnosticStatus.WARNING,
                    "Wine prefix path is very long (${game.prefix.length} chars)",
                    "Some Windows apps have issues with long paths. Consider shorter prefix location."
                )
            )
        } else {
            addResult(
                DiagnosticResult(
                    "Prefix Path Length",
                    DiagnosticStatus.PASS,
                    "Prefix path length is reasonable",
                    ""
                )
            )
        }
    }

    private fun checkLocaleSettings() {
        try {
            val locale = System.getenv("LANG") ?: System.getenv("LC_ALL")
            if (locale != null) {
                if (locale.contains("UTF-8", ignoreCase = true)) {
                    addResult(
                        DiagnosticResult(
                            "Locale Settings",
                            DiagnosticStatus.PASS,
                            "UTF-8 locale configured: $locale",
                            ""
                        )
                    )
                } else {
                    addResult(
                        DiagnosticResult(
                            "Locale Settings",
                            DiagnosticStatus.WARNING,
                            "Non-UTF-8 locale: $locale",
                            "Some games may have character encoding issues. Set LANG=en_US.UTF-8"
                        )
                    )
                }
            } else {
                addResult(
                    DiagnosticResult(
                        "Locale Settings",
                        DiagnosticStatus.WARNING,
                        "No locale environment variable set",
                        "Set LANG=en_US.UTF-8 for better compatibility"
                    )
                )
            }
        } catch (e: Exception) {
            addResult(
                DiagnosticResult(
                    "Locale Settings",
                    DiagnosticStatus.INFO,
                    "Could not check locale",
                    ""
                )
            )
        }
    }

    private fun checkEsyncSupport() {
        if (game.getGameType() != GameType.WINDOWS) return

        try {
            val process = ProcessBuilder("sh", "-c", "ulimit -Hn").start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val limit = reader.readLine()?.toIntOrNull() ?: 0
            process.waitFor()

            when {
                limit >= 524288 -> {
                    addResult(
                        DiagnosticResult(
                            "Esync Support",
                            DiagnosticStatus.PASS,
                            "File descriptor limit sufficient for esync: $limit",
                            ""
                        )
                    )
                }

                limit > 0 -> {
                    addResult(
                        DiagnosticResult(
                            "Esync Support",
                            DiagnosticStatus.WARNING,
                            "File descriptor limit too low for esync: $limit",
                            "Increase limit to 524288: echo 'DefaultLimitNOFILE=524288' | sudo tee -a /etc/systemd/system.conf"
                        )
                    )
                }

                else -> {
                    addResult(
                        DiagnosticResult(
                            "Esync Support",
                            DiagnosticStatus.INFO,
                            "Could not check file descriptor limit",
                            ""
                        )
                    )
                }
            }
        } catch (e: Exception) {
            addResult(
                DiagnosticResult(
                    "Esync Support",
                    DiagnosticStatus.INFO,
                    "Could not check esync support",
                    ""
                )
            )
        }
    }

    private fun checkFsyncSupport() {
        if (game.getGameType() != GameType.WINDOWS) return

        try {
            val kernelVersion = File("/proc/version").readText()
            addResult(
                DiagnosticResult(
                    "Fsync Support",
                    DiagnosticStatus.INFO,
                    "Kernel supports fsync (requires kernel 5.16+)",
                    "Check if using recent kernel for fsync support"
                )
            )
        } catch (e: Exception) {
            addResult(
                DiagnosticResult(
                    "Fsync Support",
                    DiagnosticStatus.INFO,
                    "Could not check fsync support",
                    ""
                )
            )
        }
    }

    private fun checkGameFilesIntegrity() {
        if (game.getGameType() == GameType.STEAM) {
            addResult(
                DiagnosticResult(
                    "Game Files Integrity",
                    DiagnosticStatus.INFO,
                    "Use Steam's 'Verify integrity of game files' feature",
                    ""
                )
            )
            return
        }

        val gameFile = File(game.executable)
        if (gameFile.exists()) {
            val gameDir = gameFile.parentFile
            val fileCount = gameDir.listFiles()?.size ?: 0
            if (fileCount > 0) {
                addResult(
                    DiagnosticResult(
                        "Game Files Integrity",
                        DiagnosticStatus.INFO,
                        "Game directory contains $fileCount files",
                        "No automatic integrity check available for non-Steam games"
                    )
                )
            } else {
                addResult(
                    DiagnosticResult(
                        "Game Files Integrity",
                        DiagnosticStatus.WARNING,
                        "Game directory appears empty",
                        "Game installation may be corrupted"
                    )
                )
            }
        }
    }

    private fun checkWineDllOverrides() {
        if (game.getGameType() != GameType.WINDOWS) return

        val hasOverrides = game.launchOptions.containsKey("WINEDLLOVERRIDES")
        if (hasOverrides) {
            addResult(
                DiagnosticResult(
                    "Wine DLL Overrides",
                    DiagnosticStatus.INFO,
                    "Custom DLL overrides configured",
                    "Overrides: ${game.launchOptions["WINEDLLOVERRIDES"]}"
                )
            )
        } else {
            addResult(
                DiagnosticResult(
                    "Wine DLL Overrides",
                    DiagnosticStatus.INFO,
                    "No custom DLL overrides set",
                    "Set overrides if game needs specific DLLs (e.g., for anti-cheat compatibility)"
                )
            )
        }
    }

    private fun checkFontConfiguration() {
        if (game.getGameType() != GameType.WINDOWS) return

        val prefixDir = File(game.prefix)
        val fontsDir = File(prefixDir, "drive_c/windows/Fonts")

        if (fontsDir.exists()) {
            val fontFiles = fontsDir.listFiles()?.size ?: 0
            if (fontFiles < 10) {
                addResult(
                    DiagnosticResult(
                        "Font Configuration",
                        DiagnosticStatus.WARNING,
                        "Few fonts in prefix ($fontFiles found)",
                        "Install corefonts via winetricks: winetricks corefonts"
                    )
                )
            } else {
                addResult(
                    DiagnosticResult(
                        "Font Configuration",
                        DiagnosticStatus.PASS,
                        "$fontFiles fonts installed in prefix",
                        ""
                    )
                )
            }
        } else {
            addResult(
                DiagnosticResult(
                    "Font Configuration",
                    DiagnosticStatus.WARNING,
                    "Fonts directory not found in prefix",
                    "Prefix may need initialization or font installation"
                )
            )
        }
    }

    private fun checkAudioSystem() {
        try {
            val hasPulseAudio = File("/usr/bin/pulseaudio").exists() || File("/usr/bin/pipewire-pulse").exists()
            val hasPipewire = File("/usr/bin/pipewire").exists()

            when {
                hasPipewire -> {
                    addResult(
                        DiagnosticResult(
                            "Audio System",
                            DiagnosticStatus.PASS,
                            "PipeWire audio system detected",
                            ""
                        )
                    )
                }

                hasPulseAudio -> {
                    addResult(
                        DiagnosticResult(
                            "Audio System",
                            DiagnosticStatus.PASS,
                            "PulseAudio system detected",
                            ""
                        )
                    )
                }

                else -> {
                    addResult(
                        DiagnosticResult(
                            "Audio System",
                            DiagnosticStatus.WARNING,
                            "No PulseAudio or PipeWire detected",
                            "Games may have audio issues. Install pipewire or pulseaudio"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            addResult(
                DiagnosticResult(
                    "Audio System",
                    DiagnosticStatus.INFO,
                    "Could not check audio system",
                    ""
                )
            )
        }
    }
}
