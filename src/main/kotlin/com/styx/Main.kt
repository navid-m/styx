package com.styx

import java.awt.*
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.dnd.*
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder
import javax.swing.table.DefaultTableModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlin.io.path.*
import com.formdev.flatlaf.themes.FlatMacDarkLaf
import com.styx.models.Game
import com.styx.models.GameType
import com.styx.models.PrefixInfo

/**
 * Helper object for Steam API operations
 */
object SteamApiHelper {
    data class SteamSearchResult(val appid: String, val name: String)

    fun searchGameByName(gameName: String): List<SteamSearchResult> {
        try {
            val encodedName = java.net.URLEncoder.encode(gameName, "UTF-8")
            val url = java.net.URL("https://steamcommunity.com/actions/SearchApps/$encodedName")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val gson = Gson()
            val listType = object : com.google.gson.reflect.TypeToken<List<SteamSearchResult>>() {}.type
            val results: List<SteamSearchResult> = gson.fromJson(response, listType)

            return results.take(10)
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }
}

/**
 * Wineserver Management Window
 * Displays running wineserver processes and allows killing them
 */
class WineserverManagementWindow(parent: JFrame? = null) : JFrame("Wineserver Management") {
    private val tableModel = DefaultTableModel(arrayOf("PID", "Command", "User"), 0)
    private val table = JTable(tableModel)
    private var refreshTimer: javax.swing.Timer? = null

    init {
        defaultCloseOperation = DISPOSE_ON_CLOSE
        setSize(700, 450)
        setLocationRelativeTo(parent)

        val mainPanel = JPanel(BorderLayout(10, 10))
        mainPanel.border = EmptyBorder(10, 10, 10, 10)

        val titleLabel = JLabel("Running Wineserver Processes")
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 14f)
        mainPanel.add(titleLabel, BorderLayout.NORTH)

        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
        table.fillsViewportHeight = true
        val scrollPane = JScrollPane(table)
        mainPanel.add(scrollPane, BorderLayout.CENTER)

        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0))

        val refreshBtn = JButton("Refresh").apply {
            preferredSize = Dimension(100, 32)
            addActionListener { refreshProcessList() }
        }

        val killSelectedBtn = JButton("Kill Selected").apply {
            preferredSize = Dimension(120, 32)
            addActionListener { killSelectedProcesses() }
        }

        val killAllBtn = JButton("Kill All Wineservers").apply {
            preferredSize = Dimension(170, 32)
            background = Color(0xCC, 0x00, 0x00)
            foreground = Color.WHITE
            font = font.deriveFont(Font.BOLD)
            addActionListener { killAllWineservers() }
        }

        val closeBtn = JButton("Close").apply {
            preferredSize = Dimension(80, 32)
            addActionListener { dispose() }
        }

        buttonPanel.add(refreshBtn)
        buttonPanel.add(killSelectedBtn)
        buttonPanel.add(killAllBtn)
        buttonPanel.add(closeBtn)
        mainPanel.add(buttonPanel, BorderLayout.SOUTH)

        contentPane = mainPanel

        addWindowListener(object : java.awt.event.WindowAdapter() {
            override fun windowClosing(e: java.awt.event.WindowEvent?) {
                stopAutoRefresh()
            }
        })

        startAutoRefresh()
        refreshProcessList()
    }

    private fun startAutoRefresh() {
        refreshTimer = javax.swing.Timer(2000) {
            refreshProcessList()
        }
        refreshTimer?.start()
    }

    private fun stopAutoRefresh() {
        refreshTimer?.stop()
        refreshTimer = null
    }

    private fun refreshProcessList() {
        Thread {
            try {
                val pb = ProcessBuilder("bash", "-c", "ps aux | grep '[w]ineserver' || true")
                val process = pb.start()
                val output = process.inputStream.bufferedReader().readText()
                process.waitFor()

                val processes = mutableListOf<Array<String>>()
                output.lines().forEach { line ->
                    if (line.isNotBlank()) {
                        val parts = line.trim().split(Regex("\\s+"), limit = 11)
                        if (parts.size >= 2) {
                            val user = parts[0]
                            val pid = parts[1]
                            val command = parts.drop(10).joinToString(" ")
                            processes.add(arrayOf(pid, command, user))
                        }
                    }
                }

                SwingUtilities.invokeLater {
                    tableModel.rowCount = 0
                    processes.forEach { tableModel.addRow(it) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun killSelectedProcesses() {
        val selectedRows = table.selectedRows
        if (selectedRows.isEmpty()) {
            JOptionPane.showMessageDialog(
                this,
                "No processes selected",
                "Info",
                JOptionPane.INFORMATION_MESSAGE
            )
            return
        }

        val pids = selectedRows.map { tableModel.getValueAt(it, 0).toString() }
        val result = JOptionPane.showConfirmDialog(
            this,
            "Kill ${pids.size} selected wineserver process(es)?\n\nPIDs: ${pids.joinToString(", ")}",
            "Confirm Kill",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        )

        if (result == JOptionPane.YES_OPTION) {
            Thread {
                try {
                    pids.forEach { pid ->
                        ProcessBuilder("kill", "-9", pid).start().waitFor()
                    }
                    SwingUtilities.invokeLater {
                        refreshProcessList()
                        JOptionPane.showMessageDialog(
                            this,
                            "Selected processes terminated",
                            "Success",
                            JOptionPane.INFORMATION_MESSAGE
                        )
                    }
                } catch (e: Exception) {
                    SwingUtilities.invokeLater {
                        JOptionPane.showMessageDialog(
                            this,
                            "Failed to kill processes: ${e.message}",
                            "Error",
                            JOptionPane.ERROR_MESSAGE
                        )
                    }
                }
            }.start()
        }
    }

    private fun killAllWineservers() {
        val result = JOptionPane.showConfirmDialog(
            this,
            "This will terminate ALL running wineserver processes on your system.\n\n" +
                    "This is useful to fix version mismatches but will affect:\n" +
                    "• All Wine/Proton games currently running\n" +
                    "• Any Wine applications in use\n\n" +
                    "Continue?",
            "Warning: Kill All Wineservers",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        )

        if (result == JOptionPane.YES_OPTION) {
            Thread {
                try {
                    val pb = ProcessBuilder("bash", "-c", "pgrep -x wineserver | xargs -r kill -9")
                    val process = pb.start()
                    process.waitFor()

                    SwingUtilities.invokeLater {
                        refreshProcessList()
                        JOptionPane.showMessageDialog(
                            this,
                            "All wineserver processes have been terminated.\n" +
                                    "You can now launch games with a fresh Wine environment.",
                            "Success",
                            JOptionPane.INFORMATION_MESSAGE
                        )
                    }
                } catch (e: Exception) {
                    SwingUtilities.invokeLater {
                        JOptionPane.showMessageDialog(
                            this,
                            "Failed to kill wineservers: ${e.message}",
                            "Error",
                            JOptionPane.ERROR_MESSAGE
                        )
                    }
                }
            }.start()
        }
    }
}

/**
 * The game output window.
 * This is used for debug output from the game.
 */
class GameOutputWindow(
    private val gameName: String,
    parent: JFrame? = null,
    private val onAbort: ((String) -> Unit)? = null,
    private val prefixPath: String? = null,
    private val useProton: Boolean = false,
    private val verboseLogging: Boolean = false
) : JFrame("Game Output - $gameName") {
    private val outputText = JTextArea()
    private val hideDebugCheckbox = JCheckBox("Hide Wine debug output (improves performance)", !verboseLogging)
    private val abortBtn = JButton("Abort Launch")
    private val maxDisplayLines = 5000
    private val fullLogBuffer = mutableListOf<String>()
    private val displayBuffer = mutableListOf<String>()
    private var needsUIUpdate = false
    private var updateTimer: javax.swing.Timer? = null

    @Volatile
    private var isClosing = false

    init {
        initUI()
        startUIUpdateTimer()

        defaultCloseOperation = DISPOSE_ON_CLOSE
        addWindowListener(object : java.awt.event.WindowAdapter() {
            override fun windowClosing(e: java.awt.event.WindowEvent?) {
                cleanup()
            }
        })
    }

    private fun initUI() {
        minimumSize = Dimension(800, 600)

        val contentPanel = JPanel(BorderLayout(10, 10))
        contentPanel.border = EmptyBorder(10, 10, 10, 10)

        val titleLabel = JLabel("Output for: $gameName")
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 12f)

        val topPanel = JPanel(FlowLayout(FlowLayout.LEFT, 10, 5))
        topPanel.add(hideDebugCheckbox)
        hideDebugCheckbox.toolTipText = "When enabled, prevents output from being captured to improve game performance"
        hideDebugCheckbox.addActionListener {
            updateOutputDisplay()
        }
        contentPanel.add(topPanel, BorderLayout.NORTH)

        contentPanel.add(titleLabel, BorderLayout.NORTH)

        updateOutputDisplay()
        outputText.isEditable = false
        outputText.lineWrap = true
        outputText.wrapStyleWord = true
        outputText.font = Font("Monospaced", Font.PLAIN, 11)
        val scrollPane = JScrollPane(outputText)
        contentPanel.add(scrollPane, BorderLayout.CENTER)

        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0))

        abortBtn.apply {
            preferredSize = Dimension(120, 32)
            background = Color(0xCC, 0x00, 0x00)
            foreground = Color.WHITE
            font = font.deriveFont(Font.BOLD)
            addActionListener {
                onAbort?.invoke(gameName)
            }
        }
        buttonPanel.add(abortBtn)

        if (prefixPath != null) {
            val killServerBtn = JButton("Kill Wineserver").apply {
                preferredSize = Dimension(130, 32)
                toolTipText = "Kill wineserver for this prefix (fixes version mismatch)"
                addActionListener { killWineserver() }
            }
            buttonPanel.add(killServerBtn)
        }

        val saveBtn = JButton("Save Log").apply {
            preferredSize = Dimension(100, 32)
            addActionListener { saveLog() }
        }

        val clearBtn = JButton("Clear Output").apply {
            preferredSize = Dimension(100, 32)
            addActionListener { clearOutput() }
        }

        val closeBtn = JButton("Close").apply {
            preferredSize = Dimension(100, 32)
            addActionListener {
                cleanup()
                dispose()
            }
        }

        buttonPanel.add(saveBtn)
        buttonPanel.add(clearBtn)
        buttonPanel.add(closeBtn)
        contentPanel.add(buttonPanel, BorderLayout.SOUTH)

        contentPane = contentPanel
    }

    private fun startUIUpdateTimer() {
        updateTimer = javax.swing.Timer(200) {
            if (!isClosing) {
                updateUI()
            }
        }
        updateTimer?.start()
    }

    private fun updateOutputDisplay() {
        SwingUtilities.invokeLater {
            if (!isClosing) {
                if (hideDebugCheckbox.isSelected) {
                    outputText.text =
                        "\n\n    (Tumbleweed)\n\n    All output is hidden.\n\n    To re-enable logs, go to the game configuration."
                } else {
                    synchronized(displayBuffer) {
                        val linesToDisplay = displayBuffer.takeLast(maxDisplayLines)
                        val text = linesToDisplay.joinToString("\n")
                        outputText.text = text
                        if (text.isNotEmpty()) {
                            outputText.caretPosition = outputText.text.length
                        }
                    }
                }
            }
        }
    }

    private fun updateUI() {
        if (!needsUIUpdate || isClosing) return

        if (hideDebugCheckbox.isSelected) {
            needsUIUpdate = false
            return
        }

        synchronized(displayBuffer) {
            if (displayBuffer.isEmpty()) return

            val linesToDisplay = displayBuffer.takeLast(maxDisplayLines)
            val text = linesToDisplay.joinToString("\n")

            SwingUtilities.invokeLater {
                if (!isClosing) {
                    outputText.text = text
                    outputText.caretPosition = outputText.text.length
                }
            }

            needsUIUpdate = false
        }
    }

    fun appendOutput(text: String, color: String? = null) {
        if (isClosing || hideDebugCheckbox.isSelected) return

        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"))
        val logLine = "[$timestamp] $text"

        synchronized(fullLogBuffer) {
            fullLogBuffer.add(logLine)
        }

        synchronized(displayBuffer) {
            displayBuffer.add(logLine)
            if (displayBuffer.size > maxDisplayLines * 2) {
                val toKeep = displayBuffer.takeLast(maxDisplayLines)
                displayBuffer.clear()
                displayBuffer.addAll(toKeep)
            }
            needsUIUpdate = true
        }
    }

    fun clearOutput() {
        synchronized(fullLogBuffer) {
            fullLogBuffer.clear()
        }
        synchronized(displayBuffer) {
            displayBuffer.clear()
        }
        SwingUtilities.invokeLater {
            outputText.text = ""
        }
    }

    fun disableAbortButton() {
        SwingUtilities.invokeLater {
            abortBtn.isEnabled = false
        }
    }

    fun cleanup() {
        isClosing = true
        updateTimer?.stop()
        updateTimer = null
        synchronized(fullLogBuffer) {
            fullLogBuffer.clear()
        }
        synchronized(displayBuffer) {
            displayBuffer.clear()
        }
    }

    private fun saveLog() {
        val chooser = JFileChooser()
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        chooser.selectedFile = File("${gameName}_log_$timestamp.txt")

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                val fullLog = synchronized(fullLogBuffer) {
                    fullLogBuffer.joinToString("\n")
                }
                chooser.selectedFile.writeText(fullLog)
                JOptionPane.showMessageDialog(
                    this,
                    "Log saved to ${chooser.selectedFile.absolutePath}\nTotal lines: ${fullLogBuffer.size}",
                    "Success",
                    JOptionPane.INFORMATION_MESSAGE
                )
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(
                    this,
                    "Failed to save log: ${e.message}",
                    "Error",
                    JOptionPane.WARNING_MESSAGE
                )
            }
        }
    }

    private fun killWineserver() {
        if (prefixPath == null) return

        Thread {
            try {
                appendOutput("")
                appendOutput("═".repeat(60), "#0066cc")
                appendOutput("KILLING WINESERVER FOR PREFIX", "#0066cc")
                appendOutput("═".repeat(60), "#0066cc")
                appendOutput("Prefix: $prefixPath")
                appendOutput("")

                val pb = ProcessBuilder()
                val env = pb.environment()
                env["WINEPREFIX"] = prefixPath

                if (useProton) {
                    env["STEAM_COMPAT_DATA_PATH"] = prefixPath
                    appendOutput("Using Proton environment for wineserver", "#00aa00")
                }

                pb.command("wineserver", "-k")
                appendOutput("Command: wineserver -k")
                appendOutput("")

                val process = pb.start()
                val exitCode = process.waitFor()

                appendOutput("")
                if (exitCode == 0) {
                    appendOutput("✓ Wineserver killed successfully", "#008800")
                    appendOutput("You can now launch the game", "#008800")
                } else {
                    appendOutput("⚠ Wineserver command exited with code: $exitCode", "#cc6600")
                    appendOutput("This may be normal if no wineserver was running", "#cc6600")
                }
                appendOutput("═".repeat(60), "#0066cc")
                appendOutput("")
            } catch (e: Exception) {
                appendOutput("ERROR killing wineserver: ${e.message}", "#cc0000")
                e.printStackTrace()
            }
        }.start()
    }

    fun isHidingOutput(): Boolean = hideDebugCheckbox.isSelected
}

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
        path: java.nio.file.Path,
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

class ChangePrefixDialog(
    private val game: Game,
    private val availablePrefixes: List<PrefixInfo>,
    parent: JFrame
) : JDialog(parent, "Change Wine Prefix - ${game.name}", true) {

    private val prefixCombo = JComboBox<PrefixComboItem>()
    var newPrefix: String? = null

    init {
        initUI()
    }

    private fun initUI() {
        minimumSize = Dimension(550, 200)

        val mainPanel = JPanel()
        mainPanel.layout = BoxLayout(mainPanel, BoxLayout.Y_AXIS)
        mainPanel.border = EmptyBorder(10, 10, 10, 10)

        val currentGroup = JPanel(BorderLayout())
        currentGroup.border = BorderFactory.createTitledBorder("Current Prefix")
        val currentLabel = JLabel(game.prefix)
        currentLabel.border = EmptyBorder(5, 5, 5, 5)
        currentGroup.add(currentLabel, BorderLayout.CENTER)
        mainPanel.add(currentGroup)
        mainPanel.add(Box.createVerticalStrut(10))

        val newGroup = JPanel()
        newGroup.layout = BoxLayout(newGroup, BoxLayout.Y_AXIS)
        newGroup.border = BorderFactory.createTitledBorder("Select New Prefix")

        val prefixPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 5))
        prefixPanel.add(JLabel("Wine Prefix:"))

        availablePrefixes.forEach { prefix ->
            prefixCombo.addItem(PrefixComboItem(prefix.name, prefix.path))
            if (prefix.path == game.prefix) {
                prefixCombo.selectedIndex = prefixCombo.itemCount - 1
            }
        }

        prefixPanel.add(prefixCombo)

        val browseBtn = JButton("Browse...").apply {
            preferredSize = Dimension(100, 28)
            addActionListener { browsePrefix() }
        }

        prefixPanel.add(browseBtn)
        newGroup.add(prefixPanel)

        if (game.protonPath != null) {
            val protonPrefixPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 5))
            val useProtonPrefixBtn = JButton("Use Proton Wineprefix").apply {
                preferredSize = Dimension(180, 32)
                toolTipText = "Set this game to use its Proton compatdata wineprefix"
                addActionListener { useProtonCompatdataPrefix() }
            }
            protonPrefixPanel.add(useProtonPrefixBtn)
            newGroup.add(protonPrefixPanel)
        }

        mainPanel.add(newGroup)
        mainPanel.add(Box.createVerticalStrut(10))

        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0))
        val okBtn = JButton("Change Prefix").apply {
            preferredSize = Dimension(120, 32)
            addActionListener { acceptChange() }
        }
        val cancelBtn = JButton("Cancel").apply {
            preferredSize = Dimension(80, 32)
            addActionListener { dispose() }
        }

        buttonPanel.add(okBtn)
        buttonPanel.add(cancelBtn)
        mainPanel.add(buttonPanel)

        contentPane = mainPanel
        pack()
        setLocationRelativeTo(parent)
    }

    private fun browsePrefix() {
        val chooser = JFileChooser()
        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        chooser.dialogTitle = "Select Wine Prefix Directory"

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            val dirPath = chooser.selectedFile.absolutePath
            prefixCombo.addItem(PrefixComboItem("Custom - ${chooser.selectedFile.name}", dirPath))
            prefixCombo.selectedIndex = prefixCombo.itemCount - 1
        }
    }

    private fun acceptChange() {
        val selected = prefixCombo.selectedItem as? PrefixComboItem
        val newPrefixPath = selected?.path

        if (newPrefixPath == null || !File(newPrefixPath).exists()) {
            JOptionPane.showMessageDialog(
                this,
                "Select a valid Wine prefix.",
                "Invalid Input",
                JOptionPane.WARNING_MESSAGE
            )
            return
        }

        if (newPrefixPath == game.prefix) {
            JOptionPane.showMessageDialog(
                this,
                "The selected prefix is already set for this game.",
                "No Change",
                JOptionPane.INFORMATION_MESSAGE
            )
            return
        }

        newPrefix = newPrefixPath
        dispose()
    }

    private fun useProtonCompatdataPrefix() {
        val protonPath = game.protonPath ?: return
        val home = Paths.get(System.getProperty("user.home"))
        val compatdataLocations = listOf(
            home.resolve(".steam/steam/steamapps/compatdata"),
            home.resolve(".local/share/Steam/steamapps/compatdata")
        )

        var foundPrefix: String? = null
        for (compatdataPath in compatdataLocations) {
            if (Files.exists(compatdataPath)) {
                try {
                    Files.list(compatdataPath).use { stream ->
                        stream.filter { Files.isDirectory(it) }
                            .forEach { prefixDir ->
                                val pfxPath = prefixDir.resolve("pfx")
                                if (Files.exists(pfxPath)) {
                                    if (pfxPath.toString() == game.prefix) {
                                        foundPrefix = pfxPath.toString()
                                        return@forEach
                                    }
                                }
                            }
                    }
                } catch (e: Exception) {
                    // Continue searching
                }
            }
        }

        if (foundPrefix == null) {
            val protonPrefixes = mutableListOf<PrefixInfo>()
            for (compatdataPath in compatdataLocations) {
                if (Files.exists(compatdataPath)) {
                    try {
                        Files.list(compatdataPath).use { stream ->
                            stream.filter { Files.isDirectory(it) }
                                .forEach { prefixDir ->
                                    val pfxPath = prefixDir.resolve("pfx")
                                    if (Files.exists(pfxPath)) {
                                        protonPrefixes.add(
                                            PrefixInfo("Proton - ${prefixDir.fileName}", pfxPath.toString())
                                        )
                                    }
                                }
                        }
                    } catch (e: Exception) {
                        // Continue
                    }
                }
            }

            if (protonPrefixes.isEmpty()) {
                JOptionPane.showMessageDialog(
                    this,
                    "No Proton compatdata prefixes found.\nProton prefixes are typically created when a game runs with Proton.",
                    "No Proton Prefixes",
                    JOptionPane.INFORMATION_MESSAGE
                )
                return
            }

            val selected = JOptionPane.showInputDialog(
                this,
                "Select a Proton compatdata prefix:",
                "Choose Proton Prefix",
                JOptionPane.PLAIN_MESSAGE,
                null,
                protonPrefixes.map { it.name }.toTypedArray(),
                protonPrefixes[0].name
            ) as? String

            if (selected != null) {
                foundPrefix = protonPrefixes.find { it.name == selected }?.path
            }
        }

        if (foundPrefix != null) {
            prefixCombo.addItem(PrefixComboItem("Proton Compatdata", foundPrefix!!))
            prefixCombo.selectedIndex = prefixCombo.itemCount - 1

            JOptionPane.showMessageDialog(
                this,
                "Proton wineprefix selected. Click 'Change Prefix' to apply.",
                "Prefix Selected",
                JOptionPane.INFORMATION_MESSAGE
            )
        }
    }

    data class PrefixComboItem(val name: String, val path: String) {
        override fun toString() = name
    }
}

class GameConfigDialog(
    private val game: Game,
    parent: JFrame,
    private val onLaunchOptions: (Game) -> Unit,
    private val onPrefixManager: (Game) -> Unit,
    private val onProtonManager: (Game) -> Unit,
    private val onChangePrefix: (Game) -> Unit
) : JDialog(parent, "Configure - ${game.name}", true) {

    private val verboseCheckbox = JCheckBox("Enable Verbose Logging (show all Wine debug)", game.verboseLogging)
    private val steamAppIdInput = JTextField(20)

    init {
        initUI()
    }

    private fun initUI() {
        minimumSize = Dimension(450, 400)

        val mainPanel = JPanel()
        mainPanel.layout = BoxLayout(mainPanel, BoxLayout.Y_AXIS)
        mainPanel.border = EmptyBorder(15, 15, 15, 15)

        val titleLabel = JLabel("Game Configuration")
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 16f)
        titleLabel.alignmentX = Component.LEFT_ALIGNMENT
        mainPanel.add(titleLabel)
        mainPanel.add(Box.createVerticalStrut(15))

        val loggingPanel = JPanel()
        loggingPanel.layout = BoxLayout(loggingPanel, BoxLayout.Y_AXIS)
        loggingPanel.border = BorderFactory.createTitledBorder("Logging Options")
        loggingPanel.alignmentX = Component.LEFT_ALIGNMENT

        verboseCheckbox.alignmentX = Component.LEFT_ALIGNMENT
        verboseCheckbox.toolTipText = "When enabled, shows all Wine debug output for this game"
        loggingPanel.add(verboseCheckbox)
        loggingPanel.add(Box.createVerticalStrut(5))

        val loggingInfoLabel =
            JLabel("<html><small>Verbose logging can help diagnose issues but generates a lot of output</small></html>")
        loggingInfoLabel.foreground = Color(0x88, 0x88, 0x88)
        loggingInfoLabel.alignmentX = Component.LEFT_ALIGNMENT
        loggingPanel.add(loggingInfoLabel)

        mainPanel.add(loggingPanel)
        mainPanel.add(Box.createVerticalStrut(15))

        val protonDbPanel = JPanel()
        protonDbPanel.layout = BoxLayout(protonDbPanel, BoxLayout.Y_AXIS)
        protonDbPanel.border = BorderFactory.createTitledBorder("ProtonDB Integration")
        protonDbPanel.alignmentX = Component.LEFT_ALIGNMENT

        val steamIdPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 5))
        steamIdPanel.add(JLabel("Steam App ID:"))
        steamAppIdInput.text = game.steamAppId ?: ""
        steamIdPanel.add(steamAppIdInput)

        val findSteamIdBtn = JButton("Find").apply {
            preferredSize = Dimension(80, 28)
            toolTipText = "Search Steam for this game"
            addActionListener { findSteamAppId() }
        }
        steamIdPanel.add(findSteamIdBtn)

        val pdbInfoLabel = JLabel("<html><small>Enter Steam App ID to enable ProtonDB button</small></html>")
        pdbInfoLabel.foreground = Color(0x88, 0x88, 0x88)
        pdbInfoLabel.alignmentX = Component.LEFT_ALIGNMENT

        protonDbPanel.add(steamIdPanel)
        protonDbPanel.add(Box.createVerticalStrut(5))
        protonDbPanel.add(pdbInfoLabel)

        mainPanel.add(protonDbPanel)
        mainPanel.add(Box.createVerticalStrut(15))

        val actionsPanel = JPanel()
        actionsPanel.layout = BoxLayout(actionsPanel, BoxLayout.Y_AXIS)
        actionsPanel.border = BorderFactory.createTitledBorder("Configuration Actions")
        actionsPanel.alignmentX = Component.LEFT_ALIGNMENT

        val paramsBtn = JButton("Launch Parameters...").apply {
            maximumSize = Dimension(Short.MAX_VALUE.toInt(), 32)
            alignmentX = Component.LEFT_ALIGNMENT
            toolTipText = "Configure launch options and environment variables"
            addActionListener {
                onLaunchOptions(game)
            }
        }
        actionsPanel.add(paramsBtn)
        actionsPanel.add(Box.createVerticalStrut(8))

        val winetricksBtn = JButton("Winetricks...").apply {
            maximumSize = Dimension(Short.MAX_VALUE.toInt(), 32)
            alignmentX = Component.LEFT_ALIGNMENT
            toolTipText = "Wineprefix Manager (install Windows components, configure Wine)"
            addActionListener {
                onPrefixManager(game)
            }
        }
        actionsPanel.add(winetricksBtn)
        actionsPanel.add(Box.createVerticalStrut(8))

        val protonBtn = JButton("Proton Manager...").apply {
            maximumSize = Dimension(Short.MAX_VALUE.toInt(), 32)
            alignmentX = Component.LEFT_ALIGNMENT
            toolTipText = "Select and manage Proton compatibility layer"
            addActionListener {
                onProtonManager(game)
            }
        }
        actionsPanel.add(protonBtn)
        actionsPanel.add(Box.createVerticalStrut(8))

        val changePrefixBtn = JButton("Change Wineprefix...").apply {
            maximumSize = Dimension(Short.MAX_VALUE.toInt(), 32)
            alignmentX = Component.LEFT_ALIGNMENT
            toolTipText = "Change the Wine prefix used by this game"
            addActionListener {
                onChangePrefix(game)
            }
        }

        actionsPanel.add(changePrefixBtn)
        mainPanel.add(actionsPanel)
        mainPanel.add(Box.createVerticalGlue())

        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0))
        buttonPanel.alignmentX = Component.LEFT_ALIGNMENT
        buttonPanel.maximumSize = Dimension(Short.MAX_VALUE.toInt(), 40)

        val saveBtn = JButton("Save").apply {
            preferredSize = Dimension(80, 32)
            addActionListener {
                game.verboseLogging = verboseCheckbox.isSelected
                game.steamAppId = steamAppIdInput.text.trim().takeIf { it.isNotEmpty() }
                dispose()
            }
        }

        val cancelBtn = JButton("Cancel").apply {
            preferredSize = Dimension(80, 32)
            addActionListener { dispose() }
        }

        buttonPanel.add(saveBtn)
        buttonPanel.add(cancelBtn)

        mainPanel.add(Box.createVerticalStrut(10))
        mainPanel.add(buttonPanel)

        contentPane = mainPanel
        pack()
        setLocationRelativeTo(parent)
    }

    private fun findSteamAppId() {
        val gameName = game.name

        val progressDialog = JDialog(this, "Searching Steam...", true)
        val progressLabel = JLabel("Searching for '$gameName' on Steam...")
        progressLabel.border = EmptyBorder(20, 20, 20, 20)
        progressDialog.contentPane.add(progressLabel)
        progressDialog.pack()
        progressDialog.setLocationRelativeTo(this)

        Thread {
            val results = SteamApiHelper.searchGameByName(gameName)

            SwingUtilities.invokeLater {
                progressDialog.dispose()

                if (results.isEmpty()) {
                    JOptionPane.showMessageDialog(
                        this,
                        "No results found for '$gameName'.",
                        "No Results",
                        JOptionPane.INFORMATION_MESSAGE
                    )
                } else {
                    val options = results.map { "${it.name} (${it.appid})" }.toTypedArray()
                    val selected = JOptionPane.showInputDialog(
                        this,
                        "Select the correct game:",
                        "Steam Search Results",
                        JOptionPane.QUESTION_MESSAGE,
                        null,
                        options,
                        options[0]
                    )

                    if (selected != null) {
                        val selectedIndex = options.indexOf(selected)
                        if (selectedIndex >= 0) {
                            steamAppIdInput.text = results[selectedIndex].appid
                        }
                    }
                }
            }
        }.start()

        progressDialog.isVisible = true
    }
}

class ProtonManagerDialog(
    private val game: Game,
    parent: JFrame
) : JDialog(parent, "Proton Manager - ${game.name}", true) {
    private val protonCombo = JComboBox<ProtonComboItem>()
    private val customPathInput = JTextField(30)
    private val availableProtons = mutableListOf<ProtonInfo>()
    var selectedProton: ProtonInfo? = null

    data class ProtonInfo(
        val name: String,
        val path: String,
        val protonBin: String
    )

    data class ProtonComboItem(val name: String, val info: ProtonInfo?) {
        override fun toString() = name
    }

    init {
        initUI()
    }

    private fun initUI() {
        minimumSize = Dimension(600, 500)

        val mainPanel = JPanel()
        mainPanel.layout = BoxLayout(mainPanel, BoxLayout.Y_AXIS)
        mainPanel.border = EmptyBorder(10, 10, 10, 10)

        val currentGroup = JPanel(BorderLayout())
        currentGroup.border = BorderFactory.createTitledBorder("Current Configuration")
        val currentProton = game.protonVersion ?: "Wine (default)"
        val currentLabel = JLabel("Current: $currentProton")
        currentLabel.border = EmptyBorder(5, 5, 5, 5)
        currentGroup.add(currentLabel, BorderLayout.CENTER)
        mainPanel.add(currentGroup)
        mainPanel.add(Box.createVerticalStrut(10))

        val scanBtn = JButton("Scan for Proton Versions").apply {
            preferredSize = Dimension(200, 32)
            maximumSize = Dimension(Short.MAX_VALUE.toInt(), 32)
            addActionListener { scanProtonVersions() }
        }
        mainPanel.add(scanBtn)
        mainPanel.add(Box.createVerticalStrut(10))

        val protonGroup = JPanel()
        protonGroup.layout = BoxLayout(protonGroup, BoxLayout.Y_AXIS)
        protonGroup.border = BorderFactory.createTitledBorder("Available Proton Versions")

        protonCombo.addItem(ProtonComboItem("Wine (default)", null))
        protonCombo.maximumSize = Dimension(Short.MAX_VALUE.toInt(), 30)
        protonGroup.add(protonCombo)
        protonGroup.add(Box.createVerticalStrut(10))

        val infoLabel = JLabel(
            "<html>Proton versions are typically found in:<br>" +
                    "• ~/.steam/steam/steamapps/common/<br>" +
                    "• ~/.steam/steam/compatibilitytools.d/<br>" +
                    "• Custom paths you specify</html>"
        )
        infoLabel.foreground = Color(0x66, 0x66, 0x66)
        infoLabel.border = EmptyBorder(5, 5, 5, 5)
        protonGroup.add(infoLabel)
        protonGroup.add(Box.createVerticalStrut(10))

        val browsePanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 5))
        browsePanel.add(JLabel("Custom Proton Path:"))
        customPathInput.isEditable = false
        browsePanel.add(customPathInput)

        val browseBtn = JButton("Browse...").apply {
            preferredSize = Dimension(100, 28)
            addActionListener { browseCustomProton() }
        }
        browsePanel.add(browseBtn)

        protonGroup.add(browsePanel)
        mainPanel.add(protonGroup)
        mainPanel.add(Box.createVerticalStrut(10))

        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0))

        val clearBtn = JButton("Clear (Use Wine)").apply {
            preferredSize = Dimension(130, 32)
            addActionListener {
                protonCombo.selectedIndex = 0
                selectedProton = null
                dispose()
            }
        }

        val applyBtn = JButton("Apply Proton Version").apply {
            preferredSize = Dimension(150, 32)
            addActionListener { applyProton() }
        }

        val cancelBtn = JButton("Cancel").apply {
            preferredSize = Dimension(80, 32)
            addActionListener { dispose() }
        }

        buttonPanel.add(clearBtn)
        buttonPanel.add(applyBtn)
        buttonPanel.add(cancelBtn)

        mainPanel.add(Box.createVerticalGlue())
        mainPanel.add(buttonPanel)

        contentPane = mainPanel
        pack()
        setLocationRelativeTo(parent)

        scanProtonVersions()
    }

    private fun scanProtonVersions() {
        protonCombo.removeAllItems()
        protonCombo.addItem(ProtonComboItem("Wine (default)", null))
        availableProtons.clear()

        val steamPaths = listOf(
            Paths.get(System.getProperty("user.home"), ".steam", "steam", "steamapps", "common"),
            Paths.get(System.getProperty("user.home"), ".steam", "steam", "compatibilitytools.d"),
            Paths.get(System.getProperty("user.home"), ".local", "share", "Steam", "steamapps", "common"),
            Paths.get(System.getProperty("user.home"), ".local", "share", "Steam", "compatibilitytools.d")
        )

        steamPaths.forEach { steamPath ->
            if (Files.exists(steamPath)) {
                try {
                    Files.list(steamPath).use { stream ->
                        stream.filter { Files.isDirectory(it) && it.fileName.toString().lowercase().contains("proton") }
                            .forEach { item ->
                                val protonBin = item.resolve("proton")
                                if (Files.exists(protonBin)) {
                                    val protonInfo = ProtonInfo(
                                        name = item.fileName.toString(),
                                        path = item.toString(),
                                        protonBin = protonBin.toString()
                                    )
                                    availableProtons.add(protonInfo)
                                    protonCombo.addItem(
                                        ProtonComboItem(
                                            "${item.fileName} (${steamPath.fileName})",
                                            protonInfo
                                        )
                                    )
                                }
                            }
                    }
                } catch (e: Exception) {
                    // Ignore permission errors
                }
            }
        }

        if (availableProtons.isNotEmpty()) {
            JOptionPane.showMessageDialog(
                this,
                "Found ${availableProtons.size} Proton version(s).",
                "Scan Complete",
                JOptionPane.INFORMATION_MESSAGE
            )
        } else {
            JOptionPane.showMessageDialog(
                this,
                "No Proton versions found in standard locations.",
                "Scan Complete",
                JOptionPane.INFORMATION_MESSAGE
            )
        }

        val currentProton = game.protonVersion
        if (currentProton != null && currentProton != "Wine (default)") {
            for (i in 0 until protonCombo.itemCount) {
                val item = protonCombo.getItemAt(i)
                if (item.info?.name == currentProton) {
                    protonCombo.selectedIndex = i
                    break
                }
            }
        }
    }

    private fun browseCustomProton() {
        val chooser = JFileChooser()
        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        chooser.dialogTitle = "Select Proton Installation Directory"

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            val protonPath = chooser.selectedFile.toPath()
            val protonBin = protonPath.resolve("proton")

            if (!Files.exists(protonBin)) {
                JOptionPane.showMessageDialog(
                    this,
                    "The selected directory does not contain a 'proton' executable.",
                    "Invalid Proton Directory",
                    JOptionPane.WARNING_MESSAGE
                )
                return
            }

            customPathInput.text = protonPath.toString()
            val protonInfo = ProtonInfo(
                name = "Custom - ${protonPath.fileName}",
                path = protonPath.toString(),
                protonBin = protonBin.toString()
            )
            protonCombo.addItem(ProtonComboItem(protonInfo.name, protonInfo))
            protonCombo.selectedIndex = protonCombo.itemCount - 1
        }
    }

    private fun applyProton() {
        val selected = protonCombo.selectedItem as? ProtonComboItem
        selectedProton = selected?.info
        dispose()
    }
}

class LaunchOptionsDialog(
    private val game: Game,
    parent: JFrame
) : JDialog(parent, "Launch Options - ${game.name}", true) {

    private val tableModel = javax.swing.table.DefaultTableModel(arrayOf("Variable", "Value"), 0)
    private val table = JTable(tableModel)
    val launchOptions = mutableMapOf<String, String>()

    init {
        initUI()
        loadExistingOptions()
    }

    private fun initUI() {
        minimumSize = Dimension(600, 400)

        val mainPanel = JPanel(BorderLayout(10, 10))
        mainPanel.border = EmptyBorder(10, 10, 10, 10)

        val titleLabel = JLabel("Environment Variables / Launch Options")
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 11f)
        titleLabel.border = EmptyBorder(0, 0, 10, 0)
        mainPanel.add(titleLabel, BorderLayout.NORTH)

        val infoLabel = JLabel(
            "<html>Add environment variables that will be set when launching this game.<br>" +
                    "Examples: DXVK_CONFIG_FILE, MANGOHUD, PROTON_USE_WINED3D, etc.</html>"
        )
        infoLabel.foreground = Color(0x66, 0x66, 0x66)
        infoLabel.border = EmptyBorder(0, 0, 10, 0)
        val topPanel = JPanel(BorderLayout())
        topPanel.add(infoLabel, BorderLayout.NORTH)
        mainPanel.add(topPanel, BorderLayout.NORTH)

        table.fillsViewportHeight = true
        val scrollPane = JScrollPane(table)
        mainPanel.add(scrollPane, BorderLayout.CENTER)

        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0))

        val addBtn = JButton("Add Variable").apply {
            preferredSize = Dimension(120, 32)
            addActionListener { addRow() }
        }

        val removeBtn = JButton("Remove Selected").apply {
            preferredSize = Dimension(140, 32)
            addActionListener { removeSelectedRow() }
        }

        val presetBtn = JButton("Common Presets...").apply {
            preferredSize = Dimension(150, 32)
            addActionListener { showPresets() }
        }

        buttonPanel.add(addBtn)
        buttonPanel.add(removeBtn)
        buttonPanel.add(presetBtn)

        val bottomPanel = JPanel(BorderLayout())
        bottomPanel.add(buttonPanel, BorderLayout.WEST)

        val saveButtonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0))

        val saveBtn = JButton("Save").apply {
            preferredSize = Dimension(100, 32)
            addActionListener { saveOptions() }
        }

        val cancelBtn = JButton("Cancel").apply {
            preferredSize = Dimension(80, 32)
            addActionListener { dispose() }
        }

        saveButtonPanel.add(saveBtn)
        saveButtonPanel.add(cancelBtn)
        bottomPanel.add(saveButtonPanel, BorderLayout.EAST)

        mainPanel.add(bottomPanel, BorderLayout.SOUTH)

        contentPane = mainPanel
        pack()
        setLocationRelativeTo(parent)
    }

    private fun loadExistingOptions() {
        launchOptions.clear()
        game.launchOptions.forEach { (key, value) ->
            tableModel.addRow(arrayOf(key, value))
            launchOptions[key] = value
        }
    }

    private fun addRow() {
        tableModel.addRow(arrayOf("", ""))
        val lastRow = tableModel.rowCount - 1
        table.editCellAt(lastRow, 0)
        table.requestFocus()
    }

    private fun removeSelectedRow() {
        val selectedRow = table.selectedRow
        if (selectedRow >= 0) {
            tableModel.removeRow(selectedRow)
        } else {
            JOptionPane.showMessageDialog(
                this,
                "You need to select a row to remove.",
                "No Selection",
                JOptionPane.INFORMATION_MESSAGE
            )
        }
    }

    private fun showPresets() {
        val presets = mapOf(
            "Enable DXVK HUD" to ("DXVK_HUD" to "fps,devinfo"),
            "Enable MangoHUD" to ("MANGOHUD" to "1"),
            "Enable DXVK Async" to ("DXVK_ASYNC" to "1"),
            "Force WineD3D" to ("PROTON_USE_WINED3D" to "1"),
            "Enable DXVK State Cache" to ("DXVK_STATE_CACHE" to "1"),
            "Disable NVAPI" to ("DXVK_NVAPI_DISABLE" to "1"),
            "Enable Esync" to ("WINEESYNC" to "1"),
            "Enable Fsync" to ("WINEFSYNC" to "1"),
            "Set DXVK Log Level (Info)" to ("DXVK_LOG_LEVEL" to "info")
        )

        val options = presets.keys.toTypedArray()
        val choice = JOptionPane.showInputDialog(
            this,
            "Select a preset to add:",
            "Common Presets",
            JOptionPane.PLAIN_MESSAGE,
            null,
            options,
            options[0]
        ) as? String

        if (choice != null) {
            val (key, value) = presets[choice]!!
            tableModel.addRow(arrayOf(key, value))
        }
    }

    private fun saveOptions() {
        launchOptions.clear()
        for (i in 0 until tableModel.rowCount) {
            val key = tableModel.getValueAt(i, 0)?.toString()?.trim() ?: ""
            val value = tableModel.getValueAt(i, 1)?.toString()?.trim() ?: ""
            if (key.isNotEmpty()) {
                launchOptions[key] = value
            }
        }
        dispose()
    }
}

class AddGameDialog(
    private val availablePrefixes: List<PrefixInfo>,
    parent: JFrame
) : JDialog(parent, "Add Game", true) {

    private val nameInput = JTextField(30)
    private val exeInput = JTextField(30)
    private val prefixCombo = JComboBox<PrefixComboItem>()
    private val steamAppIdInput = JTextField(20)
    var gameData: Game? = null

    init {
        initUI()
    }

    private fun initUI() {
        minimumSize = Dimension(500, 200)

        val mainPanel = JPanel()
        mainPanel.layout = BoxLayout(mainPanel, BoxLayout.Y_AXIS)
        mainPanel.border = EmptyBorder(10, 10, 10, 10)

        val namePanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 5))
        val nameLabel = JLabel("Game Name:")
        nameLabel.preferredSize = Dimension(100, 25)
        namePanel.add(nameLabel)
        namePanel.add(nameInput)
        mainPanel.add(namePanel)

        val exePanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 5))
        val exeLabel = JLabel("Executable:")
        exeLabel.preferredSize = Dimension(100, 25)
        exePanel.add(exeLabel)
        exePanel.add(exeInput)
        val browseBtn = JButton("Browse...").apply {
            preferredSize = Dimension(100, 28)
            addActionListener { browseExecutable() }
        }
        exePanel.add(browseBtn)
        mainPanel.add(exePanel)

        val prefixPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 5))
        val prefixLabel = JLabel("Wine Prefix:")
        prefixLabel.preferredSize = Dimension(100, 25)
        prefixPanel.add(prefixLabel)

        availablePrefixes.forEach { prefix ->
            prefixCombo.addItem(PrefixComboItem(prefix.name, prefix.path))
        }

        prefixPanel.add(prefixCombo)
        val browsePrefixBtn = JButton("Browse...").apply {
            preferredSize = Dimension(100, 28)
            addActionListener { browsePrefix() }
        }
        prefixPanel.add(browsePrefixBtn)
        mainPanel.add(prefixPanel)

        val steamAppIdPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 5))
        val steamAppIdLabel = JLabel("Steam App ID:")
        steamAppIdLabel.preferredSize = Dimension(100, 25)
        steamAppIdPanel.add(steamAppIdLabel)
        steamAppIdPanel.add(steamAppIdInput)

        val findSteamIdBtn = JButton("Find").apply {
            preferredSize = Dimension(80, 28)
            toolTipText = "Search Steam for this game"
            addActionListener { findSteamAppId() }
        }
        steamAppIdPanel.add(findSteamIdBtn)

        val infoLabel = JLabel("(Optional)")
        infoLabel.foreground = Color(0x88, 0x88, 0x88)
        infoLabel.font = infoLabel.font.deriveFont(10f)
        steamAppIdPanel.add(infoLabel)
        mainPanel.add(steamAppIdPanel)

        mainPanel.add(Box.createVerticalStrut(10))

        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0))

        val okBtn = JButton("Add").apply {
            preferredSize = Dimension(80, 32)
            addActionListener { acceptGame() }
        }

        val cancelBtn = JButton("Cancel").apply {
            preferredSize = Dimension(80, 32)
            addActionListener { dispose() }
        }

        buttonPanel.add(okBtn)
        buttonPanel.add(cancelBtn)
        mainPanel.add(buttonPanel)

        contentPane = mainPanel
        pack()
        setLocationRelativeTo(parent)
    }

    private fun browseExecutable() {
        val chooser = JFileChooser()
        chooser.dialogTitle = "Select Game Executable"
        chooser.fileFilter = javax.swing.filechooser.FileNameExtensionFilter(
            "Executable files (*.exe, *.cmd, *.bat, *.bin)",
            "exe", "cmd", "bat", "bin"
        )

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            exeInput.text = chooser.selectedFile.absolutePath
            if (nameInput.text.isEmpty()) {
                nameInput.text = chooser.selectedFile.nameWithoutExtension
            }
        }
    }

    private fun browsePrefix() {
        val chooser = JFileChooser()
        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        chooser.dialogTitle = "Select Wine Prefix Directory"

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            val dirPath = chooser.selectedFile.absolutePath
            prefixCombo.addItem(PrefixComboItem("Custom - ${chooser.selectedFile.name}", dirPath))
            prefixCombo.selectedIndex = prefixCombo.itemCount - 1
        }
    }

    private fun acceptGame() {
        val name = nameInput.text.trim()
        val exePath = exeInput.text.trim()
        val selected = prefixCombo.selectedItem as? PrefixComboItem
        val prefixPath = selected?.path
        val steamAppId = steamAppIdInput.text.trim().takeIf { it.isNotEmpty() }

        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter a game name.", "Invalid Input", JOptionPane.WARNING_MESSAGE)
            return
        }

        if (exePath.isEmpty() || !File(exePath).exists()) {
            JOptionPane.showMessageDialog(
                this,
                "Select a valid executable.",
                "Invalid Input",
                JOptionPane.WARNING_MESSAGE
            )
            return
        }

        if (prefixPath == null || !File(prefixPath).exists()) {
            JOptionPane.showMessageDialog(
                this,
                "Select a valid Wine prefix.",
                "Invalid Input",
                JOptionPane.WARNING_MESSAGE
            )
            return
        }

        gameData = Game(name, exePath, prefixPath, steamAppId = steamAppId)
        dispose()
    }

    private fun findSteamAppId() {
        val gameName = nameInput.text.trim()
        if (gameName.isEmpty()) {
            JOptionPane.showMessageDialog(
                this,
                "Please enter a game name first.",
                "No Game Name",
                JOptionPane.WARNING_MESSAGE
            )
            return
        }

        val progressDialog = JDialog(this, "Searching Steam...", true)
        val progressLabel = JLabel("Searching for '$gameName' on Steam...")
        progressLabel.border = EmptyBorder(20, 20, 20, 20)
        progressDialog.contentPane.add(progressLabel)
        progressDialog.pack()
        progressDialog.setLocationRelativeTo(this)

        Thread {
            val results = SteamApiHelper.searchGameByName(gameName)

            SwingUtilities.invokeLater {
                progressDialog.dispose()

                if (results.isEmpty()) {
                    JOptionPane.showMessageDialog(
                        this,
                        "No results found for '$gameName'.",
                        "No Results",
                        JOptionPane.INFORMATION_MESSAGE
                    )
                } else {
                    val options = results.map { "${it.name} (${it.appid})" }.toTypedArray()
                    val selected = JOptionPane.showInputDialog(
                        this,
                        "Select the correct game:",
                        "Steam Search Results",
                        JOptionPane.QUESTION_MESSAGE,
                        null,
                        options,
                        options[0]
                    )

                    if (selected != null) {
                        val selectedIndex = options.indexOf(selected)
                        if (selectedIndex >= 0) {
                            steamAppIdInput.text = results[selectedIndex].appid
                        }
                    }
                }
            }
        }.start()

        progressDialog.isVisible = true
    }

    data class PrefixComboItem(val name: String, val path: String) {
        override fun toString() = name
    }
}

class AddNativeGameDialog(parent: JFrame) : JDialog(parent, "Add Native Linux Game", true) {

    private val nameInput = JTextField(30)
    private val exeInput = JTextField(30)
    var gameData: Game? = null

    init {
        initUI()
    }

    private fun initUI() {
        minimumSize = Dimension(500, 180)

        val mainPanel = JPanel()
        mainPanel.layout = BoxLayout(mainPanel, BoxLayout.Y_AXIS)
        mainPanel.border = EmptyBorder(10, 10, 10, 10)

        val namePanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 5))
        val nameLabel = JLabel("Game Name:")
        nameLabel.preferredSize = Dimension(100, 25)
        namePanel.add(nameLabel)
        namePanel.add(nameInput)
        mainPanel.add(namePanel)

        val exePanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 5))
        val exeLabel = JLabel("Executable:")
        exeLabel.preferredSize = Dimension(100, 25)
        exePanel.add(exeLabel)
        exePanel.add(exeInput)
        val browseBtn = JButton("Browse...").apply {
            preferredSize = Dimension(100, 28)
            addActionListener { browseExecutable() }
        }
        exePanel.add(browseBtn)
        mainPanel.add(exePanel)

        mainPanel.add(Box.createVerticalStrut(10))

        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0))

        val okBtn = JButton("Add").apply {
            preferredSize = Dimension(80, 32)
            addActionListener { acceptGame() }
        }

        val cancelBtn = JButton("Cancel").apply {
            preferredSize = Dimension(80, 32)
            addActionListener { dispose() }
        }

        buttonPanel.add(okBtn)
        buttonPanel.add(cancelBtn)
        mainPanel.add(buttonPanel)

        contentPane = mainPanel
        pack()
        setLocationRelativeTo(parent)
    }

    private fun browseExecutable() {
        val chooser = JFileChooser()
        chooser.dialogTitle = "Select Native Linux Executable"
        chooser.fileSelectionMode = JFileChooser.FILES_ONLY

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            exeInput.text = chooser.selectedFile.absolutePath
            if (nameInput.text.isEmpty()) {
                nameInput.text = chooser.selectedFile.nameWithoutExtension
            }
        }
    }

    private fun acceptGame() {
        val name = nameInput.text.trim()
        val exePath = exeInput.text.trim()

        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter a game name.", "Invalid Input", JOptionPane.WARNING_MESSAGE)
            return
        }

        if (exePath.isEmpty() || !File(exePath).exists()) {
            JOptionPane.showMessageDialog(
                this,
                "Select a valid executable.",
                "Invalid Input",
                JOptionPane.WARNING_MESSAGE
            )
            return
        }

        gameData = Game(name, exePath, "", type = GameType.NATIVE_LINUX)
        dispose()
    }
}

class AddSteamGameDialog(parent: JFrame) : JDialog(parent, "Add Steam Game", true) {

    private val nameInput = JTextField(30)
    private val steamIdInput = JTextField(30)
    var gameData: Game? = null

    init {
        initUI()
    }

    private fun initUI() {
        minimumSize = Dimension(500, 180)

        val mainPanel = JPanel()
        mainPanel.layout = BoxLayout(mainPanel, BoxLayout.Y_AXIS)
        mainPanel.border = EmptyBorder(10, 10, 10, 10)

        val namePanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 5))
        val nameLabel = JLabel("Game Name:")
        nameLabel.preferredSize = Dimension(100, 25)
        namePanel.add(nameLabel)
        namePanel.add(nameInput)
        mainPanel.add(namePanel)

        val steamIdPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 5))
        val steamIdLabel = JLabel("Steam App ID:")
        steamIdLabel.preferredSize = Dimension(100, 25)
        steamIdPanel.add(steamIdLabel)
        steamIdPanel.add(steamIdInput)
        mainPanel.add(steamIdPanel)

        val helpLabel = JLabel("<html><i>Example: For Counter-Strike 2, use 730</i></html>")
        helpLabel.font = helpLabel.font.deriveFont(10f)
        helpLabel.border = EmptyBorder(0, 110, 5, 0)
        mainPanel.add(helpLabel)

        mainPanel.add(Box.createVerticalStrut(10))

        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0))

        val okBtn = JButton("Add").apply {
            preferredSize = Dimension(80, 32)
            addActionListener { acceptGame() }
        }

        val cancelBtn = JButton("Cancel").apply {
            preferredSize = Dimension(80, 32)
            addActionListener { dispose() }
        }

        buttonPanel.add(okBtn)
        buttonPanel.add(cancelBtn)
        mainPanel.add(buttonPanel)

        contentPane = mainPanel
        pack()
        setLocationRelativeTo(parent)
    }

    private fun acceptGame() {
        val name = nameInput.text.trim()
        val steamAppId = steamIdInput.text.trim()

        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter a game name.", "Invalid Input", JOptionPane.WARNING_MESSAGE)
            return
        }

        if (steamAppId.isEmpty()) {
            JOptionPane.showMessageDialog(
                this,
                "Enter a valid Steam App ID.",
                "Invalid Input",
                JOptionPane.WARNING_MESSAGE
            )
            return
        }

        if (!steamAppId.all { it.isDigit() }) {
            JOptionPane.showMessageDialog(
                this,
                "Steam App ID must be numeric.",
                "Invalid Input",
                JOptionPane.WARNING_MESSAGE
            )
            return
        }

        gameData = Game(name, steamAppId, "", type = GameType.STEAM)
        dispose()
    }
}

class GameItemWidgetWithImage(
    private val game: Game,
    private val onLaunch: (Game) -> Unit,
    private val onChangePrefix: (Game) -> Unit,
    private val onProtonManager: (Game) -> Unit,
    private val onPrefixManager: (Game) -> Unit,
    private val onLaunchOptions: (Game) -> Unit,
    private val onSaveGames: () -> Unit,
    private val isGamePlaying: (Game) -> Boolean,
    private val onRename: (Game) -> Unit,
    private val onConfigure: (Game) -> Unit
) : JPanel() {

    private val imageLabel = JLabel()

    init {
        initUI()
    }

    private fun initUI() {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        border = BorderFactory.createCompoundBorder(
            LineBorder(Color.GRAY, 1, true),
            EmptyBorder(5, 5, 5, 5)
        )
        background = Color(34, 35, 36)

        updateImage()
        imageLabel.preferredSize = Dimension(60, 60)
        imageLabel.minimumSize = Dimension(60, 60)
        imageLabel.maximumSize = Dimension(60, 60)
        imageLabel.border = BorderFactory.createEtchedBorder()
        add(imageLabel)
        add(Box.createHorizontalStrut(5))

        val infoPanel = JPanel()
        infoPanel.layout = BoxLayout(infoPanel, BoxLayout.Y_AXIS)
        infoPanel.isOpaque = false

        val nameLabel = JLabel(game.name)
        nameLabel.font = nameLabel.font.deriveFont(Font.PLAIN, 14f)
        nameLabel.foreground = Color.WHITE
        infoPanel.add(nameLabel)

        val typeOrPrefixLabel: JLabel = when (game.getGameType()) {
            GameType.NATIVE_LINUX -> JLabel("Type: Native Linux").apply {
                font = font.deriveFont(11f)
                foreground = Color(100, 200, 255)
            }

            GameType.STEAM -> JLabel("Type: Steam").apply {
                font = font.deriveFont(11f)
                foreground = Color(102, 153, 255)
            }

            GameType.WINDOWS -> {
                val number = Regex("/(\\d+)/").find(game.prefix)?.groupValues?.get(1)
                JLabel("Prefix: ${number}").apply {
                    font = font.deriveFont(11f)
                    foreground = Color.LIGHT_GRAY
                }
            }
        }
        infoPanel.add(typeOrPrefixLabel)

        val statusLabel = if (isGamePlaying(game)) {
            JLabel("▶ Playing").apply {
                foreground = Color(0, 255, 0)
                font = font.deriveFont(Font.BOLD, 11f)
            }
        } else {
            JLabel(formatTimePlayed(game.timePlayed)).apply {
                foreground = Color(3, 252, 252)
                font = font.deriveFont(11f)
            }
        }
        infoPanel.add(statusLabel)

        add(infoPanel)
        add(Box.createHorizontalGlue())

        if (game.getGameType() == GameType.WINDOWS) {
            val configureBtn = JButton("Configure").apply {
                preferredSize = Dimension(100, 28)
                maximumSize = Dimension(100, 28)
                toolTipText = "Configure game settings (params, winetricks, proton, prefix, logging)"
                addActionListener { onConfigure(game) }
            }
            add(configureBtn)
            add(Box.createHorizontalStrut(5))
        }

        val launchBtn = JButton("Launch").apply {
            preferredSize = Dimension(100, 28)
            maximumSize = Dimension(100, 28)
            font = font.deriveFont(Font.BOLD)
            addActionListener { onLaunch(game) }
        }
        launchBtn.background = Color.DARK_GRAY
        add(launchBtn)

        setupContextMenu()
        setupImageContextMenu()
    }

    private fun updateImage() {
        if (game.imagePath != null) {
            val imageFile = File(game.imagePath!!)
            if (imageFile.exists()) {
                try {
                    val bufferedImage = javax.imageio.ImageIO.read(imageFile)
                    if (bufferedImage != null) {
                        val scaledImage = bufferedImage.getScaledInstance(60, 60, Image.SCALE_SMOOTH)
                        imageLabel.icon = ImageIcon(scaledImage)
                    } else {
                        println("ImageIO.read returned null for: ${imageFile.absolutePath}")
                        imageLabel.icon = createPlaceholderIcon()
                    }
                } catch (e: Exception) {
                    println("Error loading image: ${e.message}")
                    e.printStackTrace()
                    imageLabel.icon = createPlaceholderIcon()
                }
            } else {
                println("Image file does not exist: ${imageFile.absolutePath}")
                imageLabel.icon = createPlaceholderIcon()
            }
        } else {
            imageLabel.icon = createPlaceholderIcon()
        }
    }

    private fun createPlaceholderIcon(): Icon {
        val placeholder = BufferedImage(60, 60, BufferedImage.TYPE_INT_ARGB)
        val g2d = placeholder.createGraphics()
        g2d.color = Color.GRAY
        g2d.fillRect(0, 0, 60, 60)
        g2d.color = Color.WHITE
        g2d.drawLine(0, 0, 60, 60)
        g2d.drawLine(60, 0, 0, 60)
        g2d.dispose()
        return ImageIcon(placeholder)
    }

    private fun setupImageContextMenu() {
        val imagePopupMenu = JPopupMenu()

        val updateArtItem = JMenuItem("Update Art").apply {
            addActionListener {
                updateArtFromGameDirectory()
            }
        }

        val browseImageItem = JMenuItem("Browse for Image...").apply {
            addActionListener {
                browseForImage()
            }
        }

        imagePopupMenu.add(updateArtItem)
        imagePopupMenu.add(browseImageItem)

        imageLabel.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mousePressed(e: java.awt.event.MouseEvent) {
                if (e.isPopupTrigger) {
                    imagePopupMenu.show(e.component, e.x, e.y)
                }
            }

            override fun mouseReleased(e: java.awt.event.MouseEvent) {
                if (e.isPopupTrigger) {
                    imagePopupMenu.show(e.component, e.x, e.y)
                }
            }
        })
    }

    private fun setupContextMenu() {
        val popupMenu = JPopupMenu()

        val statsItem = JMenuItem("Stats").apply {
            addActionListener {
                showStatsWindow()
            }
        }

        val renameItem = JMenuItem("Rename").apply {
            addActionListener {
                renameGame()
            }
        }

        val openGameLocationItem = JMenuItem("Open game location").apply {
            addActionListener {
                openGameLocation()
            }
        }

        popupMenu.add(statsItem)
        popupMenu.add(renameItem)
        popupMenu.addSeparator()
        popupMenu.add(openGameLocationItem)

        if (game.getGameType() == GameType.WINDOWS) {
            val openPrefixLocationItem = JMenuItem("Open wine prefix location").apply {
                addActionListener {
                    openPrefixLocation()
                }
            }
            popupMenu.add(openPrefixLocationItem)

            if (!game.steamAppId.isNullOrEmpty()) {
                val protonDbItem = JMenuItem("Proton DB").apply {
                    addActionListener {
                        openProtonDB()
                    }
                }
                popupMenu.add(protonDbItem)
            }
        }

        if (game.getGameType() == GameType.NATIVE_LINUX || game.getGameType() == GameType.STEAM) {
            popupMenu.addSeparator()
            val reconfigureItem = JMenuItem("Reconfigure").apply {
                addActionListener {
                    reconfigureGame()
                }
            }
            popupMenu.add(reconfigureItem)
        }

        addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mousePressed(e: java.awt.event.MouseEvent) {
                if (e.isPopupTrigger) {
                    popupMenu.show(e.component, e.x, e.y)
                }
            }

            override fun mouseReleased(e: java.awt.event.MouseEvent) {
                if (e.isPopupTrigger) {
                    popupMenu.show(e.component, e.x, e.y)
                }
            }
        })
    }

    private fun openGameLocation() {
        if (game.getGameType() == GameType.STEAM) {
            JOptionPane.showMessageDialog(
                this,
                "Steam App ID: ${game.executable}\nThis game is launched via Steam.",
                "Steam Game Info",
                JOptionPane.INFORMATION_MESSAGE
            )
            return
        }

        val gameFile = File(game.executable)
        val gameDirectory = gameFile.parentFile

        if (gameDirectory != null && gameDirectory.exists()) {
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(gameDirectory)
                } else {
                    ProcessBuilder("xdg-open", gameDirectory.absolutePath).start()
                }
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(
                    this,
                    "Failed to open game location: ${e.message}",
                    "Error",
                    JOptionPane.ERROR_MESSAGE
                )
            }
        } else {
            JOptionPane.showMessageDialog(
                this,
                "Game location not found: ${gameDirectory?.absolutePath ?: game.executable}",
                "Error",
                JOptionPane.ERROR_MESSAGE
            )
        }
    }

    private fun openPrefixLocation() {
        val prefixDirectory = File(game.prefix)

        if (prefixDirectory.exists()) {
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(prefixDirectory)
                } else {
                    ProcessBuilder("xdg-open", prefixDirectory.absolutePath).start()
                }
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(
                    this,
                    "Failed to open wine prefix location: ${e.message}",
                    "Error",
                    JOptionPane.ERROR_MESSAGE
                )
            }
        } else {
            JOptionPane.showMessageDialog(
                this,
                "Wine prefix not found: ${game.prefix}",
                "Error",
                JOptionPane.ERROR_MESSAGE
            )
        }
    }

    private fun renameGame() {
        val newName = JOptionPane.showInputDialog(
            this,
            "Enter new name for '${game.name}':",
            "Rename Game",
            JOptionPane.QUESTION_MESSAGE,
            null,
            null,
            game.name
        ) as? String

        if (newName != null && newName.isNotBlank() && newName != game.name) {
            game.name = newName
            onRename(game)
        }
    }

    private fun openProtonDB() {
        if (!game.steamAppId.isNullOrEmpty()) {
            try {
                val uri = java.net.URI("https://www.protondb.com/app/${game.steamAppId}")
                java.awt.Desktop.getDesktop().browse(uri)
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(
                    this,
                    "Failed to open ProtonDB: ${e.message}",
                    "Error",
                    JOptionPane.ERROR_MESSAGE
                )
            }
        }
    }

    private fun reconfigureGame() {
        when (game.getGameType()) {
            GameType.NATIVE_LINUX -> {
                val fileChooser = JFileChooser()
                fileChooser.dialogTitle = "Select Native Linux Executable"
                fileChooser.fileSelectionMode = JFileChooser.FILES_ONLY
                fileChooser.currentDirectory = File(game.executable).parentFile

                if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                    val newExecutable = fileChooser.selectedFile.absolutePath
                    val field = game::class.java.getDeclaredField("executable")
                    field.isAccessible = true
                    field.set(game, newExecutable)
                    onSaveGames()
                    JOptionPane.showMessageDialog(
                        this,
                        "Native Linux executable updated to:\n$newExecutable",
                        "Reconfigured",
                        JOptionPane.INFORMATION_MESSAGE
                    )
                }
            }

            GameType.STEAM -> {
                val newSteamId = JOptionPane.showInputDialog(
                    this,
                    "Enter Steam App ID for '${game.name}':",
                    "Reconfigure Steam Game",
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    null,
                    game.executable
                ) as? String

                if (newSteamId != null && newSteamId.isNotBlank()) {
                    val field = game::class.java.getDeclaredField("executable")
                    field.isAccessible = true
                    field.set(game, newSteamId)
                    onSaveGames()
                    JOptionPane.showMessageDialog(
                        this,
                        "Steam App ID updated to: $newSteamId",
                        "Reconfigured",
                        JOptionPane.INFORMATION_MESSAGE
                    )
                }
            }

            GameType.WINDOWS -> {
                // Should NEVER happen.
            }

            null -> {

            }
        }
    }

    private fun showStatsWindow() {
        val statsWindow = JDialog(SwingUtilities.getWindowAncestor(this) as? JFrame, "Stats - ${game.name}", false)
        statsWindow.defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
        statsWindow.setSize(400, 400)
        statsWindow.minimumSize = Dimension(400, 400)
        statsWindow.setLocationRelativeTo(this)

        val mainPanel = JPanel(BorderLayout())
        mainPanel.border = EmptyBorder(20, 20, 20, 20)
        mainPanel.background = Color(34, 35, 36)

        val titleLabel = JLabel("Statistics for ${game.name}")
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 16f)
        titleLabel.border = EmptyBorder(0, 0, 20, 0)
        titleLabel.foreground = Color.WHITE
        mainPanel.add(titleLabel, BorderLayout.NORTH)

        val tableData = arrayOf(
            arrayOf("Time Played", formatTimePlayed(game.timePlayed)),
            arrayOf("Times Opened", game.timesOpened.toString()),
            arrayOf("Times Crashed", game.timesCrashed.toString()),
            arrayOf("Wineprefix Path", game.prefix),
            arrayOf("Compatibility Layer", game.protonVersion ?: "Wine (default)"),
            arrayOf("Game Size", "Calculating..."),
            arrayOf("Wine Prefix Size", "Calculating...")
        )

        val columnNames = arrayOf("Statistic", "Value")
        val tableModel = javax.swing.table.DefaultTableModel(tableData, columnNames)
        val table = JTable(tableModel)
        table.font = table.font.deriveFont(14f)
        table.rowHeight = 25
        table.setShowGrid(true)
        table.gridColor = Color(60, 60, 60)
        table.setEnabled(false)
        table.background = Color(45, 45, 48)
        table.foreground = Color.WHITE

        val tableHeader = table.tableHeader
        tableHeader.background = Color(34, 35, 36)
        tableHeader.foreground = Color.WHITE
        tableHeader.font = tableHeader.font.deriveFont(Font.BOLD)

        table.setDefaultRenderer(Any::class.java, object : javax.swing.table.DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                table: JTable,
                value: Any?,
                isSelected: Boolean,
                hasFocus: Boolean,
                row: Int,
                column: Int
            ): java.awt.Component {
                val cell = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
                cell.foreground = Color.WHITE
                if (row == 3) {
                    cell.background = Color(60, 63, 45)
                    cell.font = cell.font.deriveFont(Font.BOLD)
                } else if (row == 4) {
                    cell.background = Color(45, 50, 60)
                } else {
                    cell.background = Color(45, 45, 48)
                }
                return cell
            }
        })

        val scrollPane = JScrollPane(table)
        mainPanel.add(scrollPane, BorderLayout.CENTER)

        Thread {
            val gameSize = calculateDirectorySize(File(game.executable).parentFile)
            SwingUtilities.invokeLater {
                tableModel.setValueAt(formatFileSize(gameSize), 5, 1)
            }
        }.start()

        Thread {
            val prefixSize = calculateDirectorySize(File(game.prefix))
            SwingUtilities.invokeLater {
                tableModel.setValueAt(formatFileSize(prefixSize), 6, 1)
            }
        }.start()

        val buttonPanel = JPanel()
        val closeButton = JButton("Close")
        closeButton.addActionListener { statsWindow.dispose() }
        buttonPanel.add(closeButton)
        mainPanel.add(buttonPanel, BorderLayout.SOUTH)

        statsWindow.contentPane.add(mainPanel)
        statsWindow.isVisible = true
    }

    private fun calculateDirectorySize(directory: File?): Long {
        if (directory == null || !directory.exists()) return 0L

        var size = 0L
        try {
            Files.walk(directory.toPath()).use { stream ->
                stream.forEach { path ->
                    try {
                        if (Files.isRegularFile(path)) {
                            size += Files.size(path)
                        }
                    } catch (e: Exception) {
                        // Skip files that can't be accessed
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return size
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.2f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.2f MB".format(mb)
        val gb = mb / 1024.0
        return "%.2f GB".format(gb)
    }

    private fun updateArtFromGameDirectory() {
        val gameDir = File(game.executable).parentFile
        if (gameDir != null && gameDir.exists()) {
            val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp")

            val imageFiles = mutableListOf<File>()

            fun searchImages(dir: File) {
                dir.listFiles()?.forEach { file ->
                    if (file.isDirectory) {
                        searchImages(file)
                    } else if (file.extension.lowercase() in imageExtensions) {
                        imageFiles.add(file)
                    }
                }
            }

            searchImages(gameDir)

            if (imageFiles.isNotEmpty()) {
                val firstImage = imageFiles.first()
                val copiedImagePath = copyImageToConfigDir(firstImage)
                if (copiedImagePath != null) {
                    game.imagePath = copiedImagePath
                    updateImage()
                    onSaveGames()
                    JOptionPane.showMessageDialog(
                        this,
                        "Updated art to: ${firstImage.name}",
                        "Art Updated",
                        JOptionPane.INFORMATION_MESSAGE
                    )
                } else {
                    JOptionPane.showMessageDialog(
                        this,
                        "Failed to copy image to config directory.",
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                    )
                }
            } else {
                JOptionPane.showMessageDialog(
                    this,
                    "No image files found in the game directory.",
                    "No Images Found",
                    JOptionPane.INFORMATION_MESSAGE
                )
            }
        } else {
            JOptionPane.showMessageDialog(
                this,
                "Game directory not found.",
                "Error",
                JOptionPane.ERROR_MESSAGE
            )
        }
    }

    private fun browseForImage() {
        val chooser = JFileChooser().apply {
            fileFilter = javax.swing.filechooser.FileNameExtensionFilter(
                "Image Files", "jpg", "jpeg", "png", "gif", "bmp", "webp"
            )
        }

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            val selectedFile = chooser.selectedFile
            val copiedImagePath = copyImageToConfigDir(selectedFile)
            if (copiedImagePath != null) {
                game.imagePath = copiedImagePath
                updateImage()
                onSaveGames()
                JOptionPane.showMessageDialog(
                    this,
                    "Updated art to: ${selectedFile.name}",
                    "Art Updated",
                    JOptionPane.INFORMATION_MESSAGE
                )
            } else {
                JOptionPane.showMessageDialog(
                    this,
                    "Failed to copy image to config directory.",
                    "Error",
                    JOptionPane.ERROR_MESSAGE
                )
            }
        }
    }

    private fun copyImageToConfigDir(sourceFile: File): String? {
        try {
            val configDir = Paths.get(System.getProperty("user.home"), ".config", "Styx").toFile()
            configDir.mkdirs()

            val artDir = File(configDir, "art")
            artDir.mkdirs()

            val gameSafeName = game.name.replace(Regex("[^a-zA-Z0-9.-]"), "_")
            val extension = sourceFile.extension
            val targetFileName = "${gameSafeName}_${
                sourceFile.nameWithoutExtension.substring(
                    0,
                    kotlin.math.min(20, sourceFile.nameWithoutExtension.length)
                )
            }.$extension"
            val targetFile = File(artDir, targetFileName)

            sourceFile.copyTo(targetFile, overwrite = true)

            return targetFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}

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

class GameLauncher : JFrame("Styx") {
    private val games = mutableListOf<Game>()
    private val categories = mutableListOf<String>()
    private var availablePrefixes = listOf<PrefixInfo>()
    private val configFile = Paths.get(System.getProperty("user.home"), ".config", "styx", "games.json")
    private val categoriesFile = Paths.get(System.getProperty("user.home"), ".config", "styx", "categories.json")
    private val gameProcesses = mutableMapOf<String, Process>()
    private val outputWindows = mutableMapOf<String, GameOutputWindow>()
    private val tabbedPane = JTabbedPane()
    private val categoryPanels = mutableMapOf<String, JPanel>()
    private val statusLabel = JLabel("Ready")
    private val logReaderThreads = mutableListOf<Thread>()
    private val gameStartTimes = mutableMapOf<String, Long>()
    private val searchField = JTextField()

    @Volatile
    private var isShuttingDown = false

    private val gson = Gson()

    init {
        initUI()
        loadCategories()
        loadGames()
        scanPrefixes()

        Runtime.getRuntime().addShutdownHook(Thread {
            cleanup()
        })
    }

    private fun initUI() {
        minimumSize = Dimension(900, 600)
        defaultCloseOperation = DO_NOTHING_ON_CLOSE

        try {
            val iconUrl = javaClass.getResource("/icon.jpg")
            if (iconUrl != null) {
                val iconImage = javax.imageio.ImageIO.read(iconUrl)
                setIconImage(iconImage)
            }
        } catch (e: Exception) {
            System.err.println("Failed to load application icon: ${e.message}")
        }

        addWindowListener(object : java.awt.event.WindowAdapter() {
            override fun windowClosing(e: java.awt.event.WindowEvent?) {
                handleApplicationClose()
            }
        })

        createMenuBar()

        val mainPanel = JPanel(BorderLayout(10, 10))
        mainPanel.border = EmptyBorder(10, 10, 10, 10)

        val topPanel = JPanel(BorderLayout(10, 5))
        val searchPanel = JPanel(BorderLayout(5, 0))
        searchField.preferredSize = Dimension(300, 30)
        searchField.toolTipText = "Search library..."
        searchPanel.add(searchField, BorderLayout.CENTER)

        searchField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) {
                filterGames()
            }

            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) {
                filterGames()
            }

            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) {
                filterGames()
            }
        })

        topPanel.add(searchPanel, BorderLayout.SOUTH)
        mainPanel.add(topPanel, BorderLayout.NORTH)

        tabbedPane.border = BorderFactory.createTitledBorder("")
        mainPanel.add(tabbedPane, BorderLayout.CENTER)

        val fullBottomPanel = JPanel(BorderLayout())
        val buttonRowPanel = JPanel(BorderLayout())
        val leftButtonPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0))
        val removeBtn = JButton("Remove Game").apply {
            preferredSize = Dimension(120, 32)
            addActionListener { removeGame() }
        }

        val rescanBtn = JButton("Rescan Prefixes").apply {
            preferredSize = Dimension(150, 32)
            addActionListener { scanPrefixes() }
        }

        leftButtonPanel.add(removeBtn)
        leftButtonPanel.add(rescanBtn)

        val rightButtonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0))

        val addNativeBtn = JButton("Add Native").apply {
            preferredSize = Dimension(120, 32)
            toolTipText = "Add Native Linux Game"
            addActionListener { addNativeGame() }
        }

        val addSteamBtn = JButton("Add Steam").apply {
            preferredSize = Dimension(120, 32)
            toolTipText = "Add Steam Game"
            addActionListener { addSteamGame() }
        }

        val addBtn = JButton("Add Windows").apply {
            preferredSize = Dimension(140, 32)
            font = font.deriveFont(Font.PLAIN)
            toolTipText = "Add Windows Game (Wine/Proton)"
            addActionListener { addGame() }
        }

        rightButtonPanel.add(addNativeBtn)
        rightButtonPanel.add(addSteamBtn)
        rightButtonPanel.add(addBtn)

        buttonRowPanel.add(leftButtonPanel, BorderLayout.WEST)
        buttonRowPanel.add(rightButtonPanel, BorderLayout.EAST)

        fullBottomPanel.add(buttonRowPanel, BorderLayout.NORTH)

        val statusPanel = JPanel(BorderLayout())
        statusPanel.border = EmptyBorder(5, 10, 5, 10)
        statusPanel.add(statusLabel, BorderLayout.WEST)
        fullBottomPanel.add(statusPanel, BorderLayout.SOUTH)
        mainPanel.add(fullBottomPanel, BorderLayout.SOUTH)

        contentPane = mainPanel

        pack()
        setLocationRelativeTo(null)
    }

    private fun createMenuBar() {
        val menuBar = JMenuBar()
        menuBar.background = Color(34, 35, 36)
        val fileMenu = JMenu("File")

        val newCategoryItem = JMenuItem("New Category").apply {
            addActionListener { createNewCategory() }
            accelerator = KeyStroke.getKeyStroke("control N")
        }
        fileMenu.add(newCategoryItem)

        val deleteCategoryItem = JMenuItem("Delete Category").apply {
            addActionListener { deleteCurrentCategory() }
            accelerator = KeyStroke.getKeyStroke("control shift D")
        }
        fileMenu.add(deleteCategoryItem)
        fileMenu.addSeparator()

        val quitItem = JMenuItem("Quit").apply {
            addActionListener { handleApplicationClose() }
            accelerator = KeyStroke.getKeyStroke("control Q")
        }

        fileMenu.add(quitItem)

        val toolsMenu = JMenu("Tools")
        val wineserverMgmtItem = JMenuItem("Wineserver Management").apply {
            addActionListener { openWineserverManagement() }
            accelerator = KeyStroke.getKeyStroke("control K")
        }
        toolsMenu.add(wineserverMgmtItem)

        val helpMenu = JMenu("Help")
        val aboutItem = JMenuItem("About").apply {
            addActionListener { showAboutDialog() }
        }

        helpMenu.add(aboutItem)
        menuBar.add(fileMenu)
        menuBar.add(toolsMenu)
        menuBar.add(helpMenu)

        jMenuBar = menuBar
    }

    private fun openWineserverManagement() {
        val window = WineserverManagementWindow(this)
        window.isVisible = true
    }

    private fun showAboutDialog() {
        val message = """
            <html>
            <h2>Styx</h2>
            <hr />
            <p>v0.0.2</p>
            <hr />
            <br />
            <p>A game launcher for Linux</p>
            <br>
            <p>By Navid M</p>
            <br />
            <a href="https://github.com/navid-m/styx">github.com/navid-m/styx</a>
            </ul>
            </html>
        """.trimIndent()

        JOptionPane.showMessageDialog(
            this,
            message,
            "About Styx",
            JOptionPane.INFORMATION_MESSAGE
        )
    }

    private fun scanPrefixes() {
        statusLabel.text = "Scanning for Wine/Proton prefixes..."

        val scanner = PrefixScanner()
        scanner.execute()

        scanner.addPropertyChangeListener { evt ->
            if (evt.propertyName == "state" && evt.newValue == SwingWorker.StateValue.DONE) {
                availablePrefixes = scanner.get()
                statusLabel.text = "Found ${availablePrefixes.size} Wine/Proton prefix(es)"
            }
        }
    }

    private fun loadGames() {
        if (configFile.exists()) {
            try {
                val json = configFile.readText()
                val type = object : TypeToken<List<Game>>() {}.type
                games.clear()
                val loadedGames: List<Game> = gson.fromJson(json, type)
                games.addAll(loadedGames)
                refreshGamesList()
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(
                    this,
                    "Failed to load games: ${e.message}",
                    "Error",
                    JOptionPane.WARNING_MESSAGE
                )
            }
        }
    }

    private fun loadCategories() {
        if (categoriesFile.exists()) {
            try {
                val json = categoriesFile.readText()
                val type = object : TypeToken<List<String>>() {}.type
                categories.clear()
                val loadedCategories: List<String> = gson.fromJson(json, type)
                categories.addAll(loadedCategories)
            } catch (e: Exception) {
                System.err.println("Failed to load categories: ${e.message}")
            }
        }

        if (!categories.contains("All")) {
            categories.add(0, "All")
        }
    }

    private fun saveCategories() {
        try {
            categoriesFile.parent.createDirectories()
            val json = gson.toJson(categories)
            categoriesFile.writeText(json)
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(
                this,
                "Failed to save categories: ${e.message}",
                "Error",
                JOptionPane.WARNING_MESSAGE
            )
        }
    }

    private fun createNewCategory() {
        val categoryName = JOptionPane.showInputDialog(
            this,
            "Enter category name:",
            "New Category",
            JOptionPane.PLAIN_MESSAGE
        )

        if (!categoryName.isNullOrBlank()) {
            val trimmedName = categoryName.trim()
            if (categories.contains(trimmedName)) {
                JOptionPane.showMessageDialog(
                    this,
                    "Category '$trimmedName' already exists.",
                    "Duplicate Category",
                    JOptionPane.WARNING_MESSAGE
                )
                return
            }

            categories.add(trimmedName)
            saveCategories()
            refreshGamesList()
            statusLabel.text = "Created category: $trimmedName"
        }
    }

    private fun deleteCurrentCategory() {
        val selectedIndex = tabbedPane.selectedIndex
        if (selectedIndex < 0) return

        val categoryName = tabbedPane.getTitleAt(selectedIndex)

        if (categoryName == "All") {
            JOptionPane.showMessageDialog(
                this,
                "Cannot delete the 'All' category.",
                "Cannot Delete",
                JOptionPane.WARNING_MESSAGE
            )
            return
        }

        val result = JOptionPane.showConfirmDialog(
            this,
            "Delete category '$categoryName'?\n\nGames in this category will be moved to 'All'.",
            "Confirm Delete",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        )

        if (result == JOptionPane.YES_OPTION) {
            games.filter { it.category == categoryName }.forEach { it.category = "All" }

            categories.remove(categoryName)
            saveCategories()
            saveGames()
            refreshGamesList()
            statusLabel.text = "Deleted category: $categoryName"
        }
    }

    private fun saveGames() {
        try {
            configFile.parent.createDirectories()
            val json = gson.toJson(games)
            configFile.writeText(json)
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(
                this,
                "Failed to save games: ${e.message}",
                "Error",
                JOptionPane.WARNING_MESSAGE
            )
        }
    }

    private fun refreshGamesList() {
        refreshGamesList(null)
    }

    private fun refreshGamesList(filter: String?) {
        tabbedPane.removeAll()
        categoryPanels.clear()

        val searchFilter = filter?.trim()?.lowercase()

        categories.forEach { category ->
            val panel = JPanel()
            panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
            panel.border = EmptyBorder(10, 5, 5, 5)

            setupDragAndDrop(panel, category)

            val gamesInCategory = if (category == "All") {
                if (searchFilter.isNullOrBlank()) {
                    games
                } else {
                    games.filter { it.name.lowercase().contains(searchFilter) }
                }
            } else {
                if (searchFilter.isNullOrBlank()) {
                    games.filter { it.category == category }
                } else {
                    games.filter { it.category == category && it.name.lowercase().contains(searchFilter) }
                }
            }

            gamesInCategory.forEach { game ->
                val gameWidget = GameItemWidgetWithImage(
                    game,
                    ::launchGame,
                    ::changeGamePrefix,
                    ::openProtonManager,
                    ::openPrefixManager,
                    ::openLaunchOptions,
                    ::saveGames,
                    ::isGamePlaying,
                    ::renameGame,
                    ::openGameConfig
                )
                gameWidget.maximumSize = Dimension(Int.MAX_VALUE, 70)
                makeDraggable(gameWidget, game)
                panel.add(gameWidget)
                panel.add(Box.createVerticalStrut(5))
            }

            panel.add(Box.createVerticalGlue())

            val scrollPane = JScrollPane(panel)
            scrollPane.verticalScrollBar.unitIncrement = 16
            scrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            scrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER

            tabbedPane.addTab(category, scrollPane)
            categoryPanels[category] = panel
        }

        tabbedPane.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mousePressed(e: java.awt.event.MouseEvent) {
                if (e.isPopupTrigger) {
                    showTabContextMenu(e)
                }
            }

            override fun mouseReleased(e: java.awt.event.MouseEvent) {
                if (e.isPopupTrigger) {
                    showTabContextMenu(e)
                }
            }
        })

        setupTabDropTargets()

        tabbedPane.revalidate()
        tabbedPane.repaint()
    }

    private fun showTabContextMenu(e: java.awt.event.MouseEvent) {
        val tabIndex = tabbedPane.indexAtLocation(e.x, e.y)
        if (tabIndex >= 0) {
            val categoryName = tabbedPane.getTitleAt(tabIndex)
            val popup = JPopupMenu()

            if (categoryName != "All") {
                val deleteItem = JMenuItem("Delete Category").apply {
                    addActionListener {
                        tabbedPane.selectedIndex = tabIndex
                        deleteCurrentCategory()
                    }
                }
                popup.add(deleteItem)
            }

            if (popup.componentCount > 0) {
                popup.show(e.component, e.x, e.y)
            }
        }
    }

    private fun setupTabDropTargets() {
        tabbedPane.dropTarget = DropTarget(tabbedPane, object : DropTargetAdapter() {
            private var draggedGameName: String? = null

            override fun dragEnter(dtde: DropTargetDragEvent) {
                dtde.acceptDrag(DnDConstants.ACTION_MOVE)
            }

            override fun dragOver(dtde: DropTargetDragEvent) {
                val location = dtde.location
                val tabIndex = tabbedPane.indexAtLocation(location.x, location.y)

                if (tabIndex >= 0 && tabIndex != tabbedPane.selectedIndex) {
                    tabbedPane.selectedIndex = tabIndex
                }

                dtde.acceptDrag(DnDConstants.ACTION_MOVE)
            }

            override fun drop(dtde: DropTargetDropEvent) {
                try {
                    val location = dtde.location
                    val tabIndex = tabbedPane.indexAtLocation(location.x, location.y)

                    if (tabIndex >= 0) {
                        val categoryName = tabbedPane.getTitleAt(tabIndex)

                        dtde.acceptDrop(DnDConstants.ACTION_MOVE)
                        val transferable = dtde.transferable
                        val gameName = transferable.getTransferData(DataFlavor.stringFlavor) as String

                        val game = games.find { it.name == gameName }
                        if (game != null && categoryName != "All" && game.category != categoryName) {
                            game.category = categoryName
                            saveGames()
                            refreshGamesList()
                            tabbedPane.selectedIndex = tabIndex
                            statusLabel.text = "Moved ${game.name} to $categoryName"
                            dtde.dropComplete(true)
                            return
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                dtde.dropComplete(false)
            }
        })
    }

    private fun setupDragAndDrop(panel: JPanel, category: String) {
        panel.dropTarget = DropTarget(panel, object : DropTargetAdapter() {
            override fun drop(dtde: DropTargetDropEvent) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_MOVE)
                    val transferable = dtde.transferable
                    val gameName = transferable.getTransferData(DataFlavor.stringFlavor) as String

                    val game = games.find { it.name == gameName }
                    if (game != null && category != "All" && game.category != category) {
                        game.category = category
                        saveGames()
                        refreshGamesList()
                        statusLabel.text = "Moved ${game.name} to $category"
                    }

                    dtde.dropComplete(true)
                } catch (e: Exception) {
                    e.printStackTrace()
                    dtde.dropComplete(false)
                }
            }
        })
    }

    private fun makeDraggable(widget: JPanel, game: Game) {
        val dragSource = DragSource.getDefaultDragSource()
        dragSource.createDefaultDragGestureRecognizer(
            widget,
            DnDConstants.ACTION_MOVE,
            object : DragGestureListener {
                override fun dragGestureRecognized(dge: DragGestureEvent) {
                    val transferable = object : Transferable {
                        override fun getTransferDataFlavors(): Array<DataFlavor> {
                            return arrayOf(DataFlavor.stringFlavor)
                        }

                        override fun isDataFlavorSupported(flavor: DataFlavor): Boolean {
                            return flavor == DataFlavor.stringFlavor
                        }

                        override fun getTransferData(flavor: DataFlavor): Any {
                            if (flavor == DataFlavor.stringFlavor) {
                                return game.name
                            }
                            throw UnsupportedFlavorException(flavor)
                        }
                    }

                    dge.startDrag(DragSource.DefaultMoveDrop, transferable)
                }
            }
        )
    }

    private fun renameGame(game: Game) {
        saveGames()
        refreshGamesList()
    }

    private fun filterGames() {
        val searchText = searchField.text
        refreshGamesList(searchText)
    }

    private fun isGamePlaying(game: Game): Boolean {
        return gameProcesses.containsKey(game.name)
    }

    private fun addGame() {
        val dialog = AddGameDialog(availablePrefixes, this)
        dialog.isVisible = true

        dialog.gameData?.let { newGame ->
            autoSetGameImage(newGame)
            games.add(newGame)
            saveGames()
            refreshGamesList()
            statusLabel.text = "Added ${newGame.name}"
        }
    }

    private fun addNativeGame() {
        val dialog = AddNativeGameDialog(this)
        dialog.isVisible = true

        dialog.gameData?.let { newGame ->
            autoSetGameImage(newGame)
            games.add(newGame)
            saveGames()
            refreshGamesList()
            statusLabel.text = "Added native Linux game: ${newGame.name}"
        }
    }

    private fun addSteamGame() {
        val dialog = AddSteamGameDialog(this)
        dialog.isVisible = true

        dialog.gameData?.let { newGame ->
            autoSetGameImage(newGame)
            games.add(newGame)
            saveGames()
            refreshGamesList()
            statusLabel.text = "Added Steam game: ${newGame.name}"
        }
    }

    private fun autoSetGameImage(game: Game) {
        if (game.getGameType() == GameType.STEAM) {
            return
        }

        val gameDir = File(game.executable).parentFile
        if (gameDir != null && gameDir.exists()) {
            val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
            val imageFiles = mutableListOf<File>()

            fun searchImages(dir: File) {
                dir.listFiles()?.forEach { file ->
                    if (file.isDirectory) {
                        searchImages(file)
                    } else if (file.extension.lowercase() in imageExtensions) {
                        imageFiles.add(file)
                    }
                }
            }

            searchImages(gameDir)

            if (imageFiles.isNotEmpty()) {
                val firstImage = imageFiles.first()
                val copiedImagePath = copyImageToConfigDir(game, firstImage)
                if (copiedImagePath != null) {
                    game.imagePath = copiedImagePath
                }
            }
        }
    }

    private fun copyImageToConfigDir(game: Game, sourceFile: File): String? {
        try {
            val configDir = Paths.get(System.getProperty("user.home"), ".config", "styx").toFile()
            configDir.mkdirs()

            val artDir = File(configDir, "art")
            artDir.mkdirs()

            val gameSafeName = game.name.replace(Regex("[^a-zA-Z0-9.-]"), "_")
            val extension = sourceFile.extension
            val targetFileName = "${gameSafeName}_${
                sourceFile.nameWithoutExtension.substring(
                    0,
                    kotlin.math.min(20, sourceFile.nameWithoutExtension.length)
                )
            }.$extension"
            val targetFile = File(artDir, targetFileName)

            sourceFile.copyTo(targetFile, overwrite = true)

            return targetFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun removeGame() {
        if (games.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No games in the library.", "No Games", JOptionPane.INFORMATION_MESSAGE)
            return
        }

        val gameNames = games.map { it.name }.toTypedArray()
        val selected = JOptionPane.showInputDialog(
            this,
            "Select game to remove:",
            "Remove Game",
            JOptionPane.QUESTION_MESSAGE,
            null,
            gameNames,
            gameNames[0]
        ) as? String

        if (selected != null) {
            val game = games.find { it.name == selected }
            if (game != null) {
                val result = JOptionPane.showConfirmDialog(
                    this,
                    "Remove '${game.name}' from the library?",
                    "Confirm Removal",
                    JOptionPane.YES_NO_OPTION
                )

                if (result == JOptionPane.YES_OPTION) {
                    games.remove(game)
                    saveGames()
                    refreshGamesList()
                    statusLabel.text = "Removed ${game.name}"
                }
            }
        }
    }

    private fun changeGamePrefix(game: Game) {
        val dialog = ChangePrefixDialog(game, availablePrefixes, this)
        dialog.isVisible = true

        dialog.newPrefix?.let { newPrefix ->
            val gameInList = games.find { it.name == game.name }
            if (gameInList != null) {
                val oldPrefix = gameInList.prefix
                gameInList.prefix = newPrefix
                saveGames()
                refreshGamesList()
                statusLabel.text =
                    "Changed prefix for ${game.name} from ${Paths.get(oldPrefix).fileName} to ${Paths.get(newPrefix).fileName}"
            }
        }
    }

    private fun openProtonManager(game: Game) {
        val dialog = ProtonManagerDialog(game, this)
        dialog.isVisible = true

        val gameInList = games.find { it.name == game.name }
        if (gameInList != null) {
            if (dialog.selectedProton == null) {
                gameInList.protonVersion = null
                gameInList.protonPath = null
                gameInList.protonBin = null
                statusLabel.text = "Set ${game.name} to use Wine (default)"
            } else {
                val proton = dialog.selectedProton!!
                gameInList.protonVersion = proton.name
                gameInList.protonPath = proton.path
                gameInList.protonBin = proton.protonBin
                statusLabel.text = "Set ${game.name} to use ${proton.name}"
            }
            saveGames()
            refreshGamesList()
        }
    }

    private fun openGameConfig(game: Game) {
        val dialog = GameConfigDialog(
            game,
            this,
            ::openLaunchOptions,
            ::openPrefixManager,
            ::openProtonManager,
            ::changeGamePrefix
        )
        dialog.isVisible = true
        saveGames()
        refreshGamesList()
    }

    private fun openPrefixManager(game: Game) {
        val prefixPath = game.prefix

        if (!File(prefixPath).exists()) {
            JOptionPane.showMessageDialog(
                this,
                "Wine prefix not found: $prefixPath",
                "Error",
                JOptionPane.ERROR_MESSAGE
            )
            return
        }

        Thread {
            try {
                val whichProcess = ProcessBuilder("which", "winetricks").start()
                whichProcess.waitFor()

                if (whichProcess.exitValue() != 0) {
                    SwingUtilities.invokeLater {
                        JOptionPane.showMessageDialog(
                            this,
                            "Winetricks is not installed or not found in PATH.\n\n" +
                                    "Install winetricks to use this feature:\n" +
                                    "• Ubuntu/Debian: sudo apt install winetricks\n" +
                                    "• Arch: sudo pacman -S winetricks\n" +
                                    "• Fedora: sudo dnf install winetricks",
                            "Winetricks Not Found",
                            JOptionPane.WARNING_MESSAGE
                        )
                    }
                    return@Thread
                }

                SwingUtilities.invokeLater {
                    statusLabel.text = "Launching Winetricks for ${game.name}..."
                }

                val processBuilder = ProcessBuilder()
                processBuilder.environment()["WINEPREFIX"] = prefixPath
                processBuilder.command("winetricks")

                val process = processBuilder.start()
                val exitCode = process.waitFor()

                SwingUtilities.invokeLater {
                    if (exitCode == 0) {
                        statusLabel.text = "Winetricks closed for ${game.name}"
                    } else {
                        statusLabel.text = "Winetricks exited with code $exitCode for ${game.name}"
                    }
                }

            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    JOptionPane.showMessageDialog(
                        this,
                        "Failed to launch Winetricks: ${e.message}",
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                    )
                    statusLabel.text = "Failed to launch Winetricks"
                }
            }
        }.start()
    }

    private fun openLaunchOptions(game: Game) {
        val gameInList = games.find { it.name == game.name }

        if (gameInList != null) {
            val dialog = LaunchOptionsDialog(gameInList, this)
            dialog.isVisible = true

            gameInList.launchOptions.clear()
            gameInList.launchOptions.putAll(dialog.launchOptions)

            saveGames()
            statusLabel.text = "Updated launch options for ${game.name}"
        }
    }

    private fun checkWineAvailability(outputWindow: GameOutputWindow): Boolean {
        try {
            outputWindow.appendOutput("=== Pre-flight checks ===", "#0066cc")

            val whichProcess = ProcessBuilder("which", "wine").start()
            whichProcess.waitFor()

            if (whichProcess.exitValue() == 0) {
                val winePath = whichProcess.inputStream.bufferedReader().readText().trim()
                outputWindow.appendOutput("Wine found at: $winePath", "#008800")
            } else {
                outputWindow.appendOutput("WARNING: 'wine' command not found in PATH", "#cc6600")
                return false
            }

            val versionProcess = ProcessBuilder("wine", "--version").start()
            versionProcess.waitFor()

            if (versionProcess.exitValue() == 0) {
                val version = versionProcess.inputStream.bufferedReader().readText().trim()
                outputWindow.appendOutput("Wine version: $version", "#008800")
            } else {
                val error = versionProcess.errorStream.bufferedReader().readText().trim()
                outputWindow.appendOutput("Could not get Wine version: $error", "#cc6600")
            }

            outputWindow.appendOutput("")
            return true

        } catch (e: Exception) {
            outputWindow.appendOutput("ERROR checking Wine: ${e.message}", "#cc0000")
            outputWindow.appendOutput("")
            return false
        }
    }

    private fun launchNativeLinuxGame(game: Game) {
        val exePath = game.executable
        val gameName = game.name

        if (!File(exePath).exists()) {
            JOptionPane.showMessageDialog(this, "Executable not found: $exePath", "Error", JOptionPane.ERROR_MESSAGE)
            return
        }

        val outputWindow = GameOutputWindow(
            gameName = gameName,
            parent = this,
            onAbort = { abortGameName -> abortGameLaunch(abortGameName) },
            verboseLogging = game.verboseLogging
        )
        outputWindows[gameName] = outputWindow
        outputWindow.isVisible = true

        outputWindow.appendOutput("═".repeat(60), "#0066cc")
        outputWindow.appendOutput("LAUNCHING NATIVE LINUX GAME: $gameName", "#0066cc")
        outputWindow.appendOutput("═".repeat(60), "#0066cc")
        outputWindow.appendOutput("")

        outputWindow.appendOutput("=== Launch Configuration ===", "#0066cc")
        outputWindow.appendOutput("Executable: $exePath")
        outputWindow.appendOutput("Working Directory: ${File(exePath).parent}")
        outputWindow.appendOutput("Type: Native Linux Binary", "#00aa00")
        outputWindow.appendOutput("")

        try {
            val processBuilder = ProcessBuilder()
            processBuilder.directory(File(exePath).parentFile)
            processBuilder.command(File(exePath).absolutePath)

            outputWindow.appendOutput("=== Starting Native Process ===", "#0066cc")
            outputWindow.appendOutput("Command: ${File(exePath).absolutePath}")
            outputWindow.appendOutput("")

            val process = processBuilder.start()
            gameProcesses[gameName] = process

            game.timesOpened++
            val startTime = System.currentTimeMillis()

            Thread {
                val stdout = process.inputStream.bufferedReader()
                val stderr = process.errorStream.bufferedReader()

                Thread {
                    try {
                        stdout.lines().forEach { line ->
                            outputWindow.appendOutput(line)
                        }
                    } catch (e: Exception) {
                        // Stream closed. Ignore.
                    }
                }.start()

                Thread {
                    try {
                        stderr.lines().forEach { line ->
                            outputWindow.appendOutput(line, "#cc6600")
                        }
                    } catch (e: Exception) {
                        // Stream closed. Ignore.
                    }
                }.start()

                val exitCode = process.waitFor()
                val endTime = System.currentTimeMillis()
                val duration = endTime - startTime
                game.timePlayed += duration

                gameProcesses.remove(gameName)

                SwingUtilities.invokeLater {
                    outputWindow.appendOutput("")
                    outputWindow.appendOutput("═".repeat(60), "#0066cc")
                    if (exitCode == 0) {
                        outputWindow.appendOutput("GAME EXITED NORMALLY", "#00aa00")
                    } else {
                        outputWindow.appendOutput("GAME CRASHED (Exit code: $exitCode)", "#cc0000")
                        game.timesCrashed++
                    }
                    outputWindow.appendOutput("Session Duration: ${formatTimePlayed(duration)}", "#0066cc")
                    outputWindow.appendOutput("═".repeat(60), "#0066cc")
                    saveGames()
                    refreshGamesList()
                }
            }.start()

            statusLabel.text = "Launched ${game.name}"
            refreshGamesList()

        } catch (e: Exception) {
            outputWindow.appendOutput("ERROR: ${e.message}", "#cc0000")
            e.printStackTrace()
            game.timesCrashed++
            gameProcesses.remove(gameName)
            saveGames()
            refreshGamesList()
        }
    }

    private fun launchSteamGame(game: Game) {
        val steamAppId = game.executable
        val gameName = game.name

        val outputWindow = GameOutputWindow(
            gameName = gameName,
            parent = this,
            onAbort = { abortGameName -> abortGameLaunch(abortGameName) },
            verboseLogging = game.verboseLogging
        )
        outputWindows[gameName] = outputWindow
        outputWindow.isVisible = true

        outputWindow.appendOutput("═".repeat(60), "#0066cc")
        outputWindow.appendOutput("LAUNCHING STEAM GAME: $gameName", "#0066cc")
        outputWindow.appendOutput("═".repeat(60), "#0066cc")
        outputWindow.appendOutput("")

        outputWindow.appendOutput("=== Launch Configuration ===", "#0066cc")
        outputWindow.appendOutput("Steam App ID: $steamAppId")
        outputWindow.appendOutput("Type: Steam Game", "#00aa00")
        outputWindow.appendOutput("")

        try {
            val steamUrl = "steam://rungameid/$steamAppId"
            outputWindow.appendOutput("=== Starting Steam ===", "#0066cc")
            outputWindow.appendOutput("Opening: $steamUrl")
            outputWindow.appendOutput("")

            val processBuilder = ProcessBuilder("xdg-open", steamUrl)
            val process = processBuilder.start()

            game.timesOpened++

            Thread {
                process.waitFor()

                SwingUtilities.invokeLater {
                    outputWindow.appendOutput("")
                    outputWindow.appendOutput("═".repeat(60), "#0066cc")
                    outputWindow.appendOutput("Steam launcher opened successfully", "#00aa00")
                    outputWindow.appendOutput("Note: Game will launch through Steam client", "#0066cc")
                    outputWindow.appendOutput("═".repeat(60), "#0066cc")
                    saveGames()
                }
            }.start()

            statusLabel.text = "Launched ${game.name} via Steam"
            refreshGamesList()

        } catch (e: Exception) {
            outputWindow.appendOutput("ERROR: ${e.message}", "#cc0000")
            e.printStackTrace()
            game.timesCrashed++
            saveGames()
            refreshGamesList()
        }
    }

    private fun launchGame(game: Game) {
        when (game.getGameType()) {
            GameType.NATIVE_LINUX -> {
                launchNativeLinuxGame(game)
                return
            }

            GameType.STEAM -> {
                launchSteamGame(game)
                return
            }

            GameType.WINDOWS -> {
                // Continue with Wine/Proton launch...
            }

            null -> {

            }
        }

        val exePath = game.executable
        val prefixPath = game.prefix
        val gameName = game.name

        if (!File(exePath).exists()) {
            JOptionPane.showMessageDialog(this, "Executable not found: $exePath", "Error", JOptionPane.ERROR_MESSAGE)
            return
        }

        if (!File(prefixPath).exists()) {
            JOptionPane.showMessageDialog(
                this,
                "Wine prefix not found: $prefixPath",
                "Error",
                JOptionPane.ERROR_MESSAGE
            )
            return
        }

        val outputWindow = GameOutputWindow(
            gameName = gameName,
            parent = this,
            onAbort = { abortGameName -> abortGameLaunch(abortGameName) },
            prefixPath = prefixPath,
            useProton = game.protonBin != null,
            verboseLogging = game.verboseLogging
        )
        outputWindows[gameName] = outputWindow
        outputWindow.isVisible = true

        outputWindow.appendOutput("═".repeat(60), "#0066cc")
        outputWindow.appendOutput("LAUNCHING: $gameName", "#0066cc")
        outputWindow.appendOutput("═".repeat(60), "#0066cc")
        outputWindow.appendOutput("")

        if (!checkWineAvailability(outputWindow)) {
            outputWindow.appendOutput("Pre-flight checks failed. Aborting launch.", "#cc0000")
            return
        }

        outputWindow.appendOutput("=== Launch Configuration ===", "#0066cc")
        outputWindow.appendOutput("Executable: $exePath")
        outputWindow.appendOutput("Working Directory: ${File(exePath).parent}")
        outputWindow.appendOutput("Wine Prefix: $prefixPath")

        val useProton = game.protonBin != null
        if (useProton) {
            val protonVersion = game.protonVersion ?: "Unknown"
            outputWindow.appendOutput("Compatibility Layer: $protonVersion", "#00aa00")
        } else {
            outputWindow.appendOutput("Compatibility Layer: Wine (default)", "#00aa00")
        }

        if (File(prefixPath).exists()) {
            val systemReg = File(prefixPath, "system.reg")
            val userReg = File(prefixPath, "user.reg")
            if (systemReg.exists() && userReg.exists()) {
                outputWindow.appendOutput("Wine prefix validation: OK", "#008800")
            } else {
                outputWindow.appendOutput("WARNING: Wine prefix may be incomplete or corrupted", "#cc6600")
            }
        }

        outputWindow.appendOutput("")

        try {
            val processBuilder = ProcessBuilder()
            val env = processBuilder.environment()
            env["WINEPREFIX"] = prefixPath

            if (useProton) {
                env["STEAM_COMPAT_DATA_PATH"] = prefixPath
                env["STEAM_COMPAT_CLIENT_INSTALL_PATH"] = game.protonPath ?: ""
            }

            game.launchOptions.forEach { (key, value) ->
                env[key] = value
            }

            if (game.verboseLogging) {
                env["WINEDEBUG"] = "+all"
                outputWindow.appendOutput("Verbose mode enabled: WINEDEBUG=+all", "#0066cc")
            } else {
                env["WINEDEBUG"] = "warn+all,fixme-all"
                outputWindow.appendOutput("Debug mode: WINEDEBUG=warn+all,fixme-all", "#0066cc")
            }

            env["WINEDLLOVERRIDES"] = "winemenubuilder.exe=d"
            env["DISPLAY"] = ":0"

            outputWindow.appendOutput("")
            outputWindow.appendOutput("=== Environment Variables ===", "#0066cc")
            env.filter {
                it.key.startsWith("WINE") ||
                        it.key == "DISPLAY" ||
                        it.key.startsWith("STEAM_COMPAT") ||
                        it.key.startsWith("DXVK") ||
                        it.key.startsWith("PROTON") ||
                        it.key == "MANGOHUD" || game.launchOptions.containsKey(it.key)
            }.forEach { (key, value) ->
                outputWindow.appendOutput("  $key=$value")
            }
            if (game.launchOptions.isNotEmpty()) {
                outputWindow.appendOutput(
                    "Custom launch options: ${game.launchOptions.size ?: 0} variable(s)",
                    "#00aa00"
                )
            }
            outputWindow.appendOutput("")

            outputWindow.appendOutput("=== Cleaning up old wineserver ===", "#0066cc")
            try {
                val killServerPB = ProcessBuilder()
                val killEnv = killServerPB.environment()
                killEnv["WINEPREFIX"] = prefixPath
                if (useProton) {
                    killEnv["STEAM_COMPAT_DATA_PATH"] = prefixPath
                }
                killServerPB.command("wineserver", "-k")
                val killProcess = killServerPB.start()
                killProcess.waitFor()
                outputWindow.appendOutput("Terminated any existing wineserver for this prefix", "#008800")
            } catch (e: Exception) {
                outputWindow.appendOutput("Note: wineserver cleanup skipped (${e.message})", "#cc6600")
            }
            outputWindow.appendOutput("")

            processBuilder.directory(File(exePath).parentFile)

            if (useProton) {
                val protonBin = game.protonBin!!
                processBuilder.command(protonBin, "run", File(exePath).absolutePath)

                outputWindow.appendOutput("=== Starting Proton Process ===", "#0066cc")
                outputWindow.appendOutput("Command: $protonBin run ${File(exePath).absolutePath}")
                outputWindow.appendOutput("")
            } else {
                processBuilder.command("wine", File(exePath).absolutePath)

                outputWindow.appendOutput("=== Starting Wine Process ===", "#0066cc")
                outputWindow.appendOutput("Command: wine ${File(exePath).absolutePath}")
                outputWindow.appendOutput("")
            }

            val process = processBuilder.start()
            gameProcesses[gameName] = process

            Thread.sleep(100)

            if (!process.isAlive) {
                outputWindow.appendOutput("ERROR: Process failed to start", "#cc0000")
                outputWindow.appendOutput("The Wine process terminated immediately", "#cc0000")
                gameProcesses.remove(gameName)
                statusLabel.text = "Launch failed"
                return
            }

            val pid = process.pid()
            outputWindow.appendOutput("")
            outputWindow.appendOutput("Process started successfully (PID: $pid).", "#008800")
            outputWindow.appendOutput("─".repeat(60))
            outputWindow.appendOutput("")

            gameStartTimes[gameName] = System.currentTimeMillis()

            games.find { it.name == gameName }?.let { game ->
                game.timesOpened++
                saveGames()
            }

            statusLabel.text = "$gameName is running (PID: $pid)"
            refreshGamesList()

            val stdoutThread = Thread {
                try {
                    process.inputStream.bufferedReader().use { reader ->
                        while (!isShuttingDown && process.isAlive) {
                            val line = reader.readLine() ?: break
                            if (line.isNotBlank()) {
                                SwingUtilities.invokeLater {
                                    if (!isShuttingDown) {
                                        outputWindow.appendOutput("[OUT] ${line.trim()}")
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (!isShuttingDown) {
                        SwingUtilities.invokeLater {
                            outputWindow.appendOutput("[ERROR reading stdout: ${e.message}]", "#cc0000")
                        }
                    }
                }
            }.apply {
                isDaemon = true
                name = "stdout-reader-$gameName"
            }
            stdoutThread.start()
            logReaderThreads.add(stdoutThread)

            val stderrThread = Thread {
                try {
                    process.errorStream.bufferedReader().use { reader ->
                        while (!isShuttingDown && process.isAlive) {
                            val line = reader.readLine() ?: break
                            if (line.isNotBlank()) {
                                SwingUtilities.invokeLater {
                                    if (!isShuttingDown) {
                                        val trimmed = line.trim()
                                        when {
                                            "err:" in trimmed.lowercase() || "error" in trimmed.lowercase() ->
                                                outputWindow.appendOutput("[ERR] $trimmed", "#cc0000")

                                            "warn:" in trimmed.lowercase() || "warning" in trimmed.lowercase() ->
                                                outputWindow.appendOutput("[WARN] $trimmed", "#cc6600")

                                            "fixme:" in trimmed.lowercase() -> {
                                                if (game.verboseLogging) {
                                                    outputWindow.appendOutput("[FIXME] $trimmed", "#666666")
                                                }
                                            }

                                            else -> outputWindow.appendOutput("[ERR] $trimmed")
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (!isShuttingDown) {
                        SwingUtilities.invokeLater {
                            outputWindow.appendOutput("[ERROR reading stderr: ${e.message}]", "#cc0000")
                        }
                    }
                }
            }.apply {
                isDaemon = true
                name = "stderr-reader-$gameName"
            }
            stderrThread.start()
            logReaderThreads.add(stderrThread)
            val monitorThread = Thread {
                val exitCode = process.waitFor()
                val startTime = gameStartTimes[gameName]

                if (!isShuttingDown) {
                    SwingUtilities.invokeLater {
                        if (startTime != null) {
                            val endTime = System.currentTimeMillis()
                            val sessionMinutes = (endTime - startTime) / 1000 / 60

                            games.find { it.name == gameName }?.let { game ->
                                game.timePlayed += sessionMinutes
                                if (exitCode != 0) {
                                    game.timesCrashed++
                                }
                                saveGames()
                            }

                            gameStartTimes.remove(gameName)
                        }

                        outputWindow.appendOutput("")
                        outputWindow.appendOutput("═".repeat(60), "#0066cc")
                        outputWindow.appendOutput("PROCESS FINISHED", "#0066cc")
                        outputWindow.appendOutput("═".repeat(60), "#0066cc")

                        val color = if (exitCode == 0) "#008800" else "#cc0000"
                        outputWindow.appendOutput("Exit Code: $exitCode", color)

                        if (exitCode != 0) {
                            outputWindow.appendOutput(
                                "Non-zero exit code indicates the game may have crashed or encountered an error.",
                                "#cc6600"
                            )
                        }

                        outputWindow.disableAbortButton()
                        gameProcesses.remove(gameName)
                        logReaderThreads.removeAll { !it.isAlive }
                        statusLabel.text = "$gameName exited (code: $exitCode)"
                        refreshGamesList()
                    }
                }
            }.apply {
                isDaemon = true
                name = "process-monitor-$gameName"
            }
            monitorThread.start()
            logReaderThreads.add(monitorThread)

        } catch (e: Exception) {
            outputWindow.appendOutput("", "#cc0000")
            outputWindow.appendOutput("CRITICAL ERROR: ${e.message}", "#cc0000")
            outputWindow.appendOutput("Exception type: ${e.javaClass.simpleName}", "#cc0000")
            outputWindow.appendOutput("")
            outputWindow.appendOutput("Traceback:", "#cc0000")
            e.stackTrace.take(10).forEach { element ->
                outputWindow.appendOutput("  at $element", "#cc0000")
            }

            outputWindow.disableAbortButton()
            JOptionPane.showMessageDialog(
                this,
                "Failed to launch game: ${e.message}",
                "Launch Error",
                JOptionPane.ERROR_MESSAGE
            )
            statusLabel.text = "Launch failed"
        }
    }

    private fun abortGameLaunch(gameName: String) {
        val process = gameProcesses[gameName]
        if (process != null && process.isAlive) {
            val result = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to abort the launch of '$gameName'?",
                "Abort Launch",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
            )

            if (result == JOptionPane.YES_OPTION) {
                val outputWindow = outputWindows[gameName]
                outputWindow?.appendOutput("")
                outputWindow?.appendOutput("═".repeat(60), "#cc6600")
                outputWindow?.appendOutput("ABORTING LAUNCH", "#cc6600")
                outputWindow?.appendOutput("═".repeat(60), "#cc6600")
                outputWindow?.appendOutput("User requested abort. Terminating process...", "#cc6600")

                process.destroy()
                Thread.sleep(1000)

                if (process.isAlive) {
                    process.destroyForcibly()
                }

                outputWindow?.appendOutput("Process terminated.", "#cc0000")
                outputWindow?.disableAbortButton()

                val startTime = gameStartTimes[gameName]
                if (startTime != null) {
                    val endTime = System.currentTimeMillis()
                    val sessionMinutes = (endTime - startTime) / 1000 / 60

                    games.find { it.name == gameName }?.let { game ->
                        game.timePlayed += sessionMinutes
                        saveGames()
                    }

                    gameStartTimes.remove(gameName)
                }

                gameProcesses.remove(gameName)
                logReaderThreads.removeAll { !it.isAlive }
                statusLabel.text = "Aborted launch of $gameName"
                refreshGamesList()
            }
        } else {
            JOptionPane.showMessageDialog(
                this,
                "No active process found for '$gameName'.",
                "Info",
                JOptionPane.INFORMATION_MESSAGE
            )
        }
    }

    private fun handleApplicationClose() {
        if (gameProcesses.isNotEmpty()) {
            val result = JOptionPane.showConfirmDialog(
                this,
                "There are ${gameProcesses.size} game(s) still running.\nDo you want to terminate them and exit?",
                "Confirm Exit",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            )

            if (result != JOptionPane.YES_OPTION) {
                return
            }
        }

        cleanup()
        dispose()
        System.exit(0)
    }

    private fun cleanup() {
        isShuttingDown = true

        outputWindows.values.forEach { window ->
            try {
                window.cleanup()
                window.dispose()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        outputWindows.clear()

        gameProcesses.values.forEach { process ->
            try {
                if (process.isAlive) {
                    process.destroy()
                    Thread.sleep(500)
                    if (process.isAlive) {
                        process.destroyForcibly()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        gameProcesses.clear()

        logReaderThreads.forEach { thread ->
            try {
                if (thread.isAlive) {
                    thread.interrupt()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        logReaderThreads.clear()
    }
}


fun main(args: Array<String>) {
    FlatMacDarkLaf.setup()
    SwingUtilities.invokeLater {
        val launcher = GameLauncher()
        launcher.isVisible = true
    }
}
