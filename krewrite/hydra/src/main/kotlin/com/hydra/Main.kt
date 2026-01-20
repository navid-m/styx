package com.hydra

import java.awt.*
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlin.io.path.*
import com.formdev.flatlaf.themes.FlatMacDarkLaf

data class Game(
    val name: String,
    val executable: String,
    var prefix: String,
    var protonVersion: String? = null,
    var protonPath: String? = null,
    var protonBin: String? = null,
    var launchOptions: MutableMap<String, String> = mutableMapOf(),
    var imagePath: String? = null
)

data class PrefixInfo(
    val name: String,
    val path: String
)

/**
 * The game output window.
 * This is used for debug output from the game.
 */
class GameOutputWindow(
    private val gameName: String,
    parent: JFrame? = null,
    private val onAbort: ((String) -> Unit)? = null
) : JFrame("Game Output - $gameName") {
    private val outputText = JTextArea()
    private val verboseCheckbox = JCheckBox("Verbose Mode (show all Wine debug)", false)
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

        contentPanel.add(titleLabel, BorderLayout.NORTH)

        val topPanel = JPanel(BorderLayout())
        topPanel.add(verboseCheckbox, BorderLayout.WEST)
        contentPanel.add(topPanel, BorderLayout.NORTH)
        outputText.isEditable = false
        outputText.font = Font("Monospaced", Font.PLAIN, 9)
        outputText.lineWrap = false
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

    private fun updateUI() {
        if (!needsUIUpdate || isClosing) return

        synchronized(displayBuffer) {
            if (displayBuffer.isEmpty()) return

            val text = displayBuffer.takeLast(maxDisplayLines).joinToString("\n")

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
        if (isClosing) return

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

    fun isVerboseMode(): Boolean = verboseCheckbox.isSelected
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

    data class PrefixComboItem(val name: String, val path: String) {
        override fun toString() = name
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

        gameData = Game(name, exePath, prefixPath)
        dispose()
    }

    data class PrefixComboItem(val name: String, val path: String) {
        override fun toString() = name
    }
}

class GameItemWidget(
    private val game: Game,
    private val onLaunch: (Game) -> Unit,
    private val onChangePrefix: (Game) -> Unit,
    private val onProtonManager: (Game) -> Unit,
    private val onPrefixManager: (Game) -> Unit,
    private val onLaunchOptions: (Game) -> Unit
) : JPanel() {

    init {
        initUI()
    }

    private fun initUI() {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        border = BorderFactory.createCompoundBorder(
            LineBorder(Color(0xCC, 0xCC, 0xCC), 1, true),
            EmptyBorder(5, 5, 5, 5)
        )
        background = Color(0x00, 0x00, 0x00)

        val infoPanel = JPanel()
        infoPanel.layout = BoxLayout(infoPanel, BoxLayout.Y_AXIS)
        infoPanel.isOpaque = false

        val nameLabel = JLabel(game.name)
        nameLabel.font = nameLabel.font.deriveFont(Font.BOLD, 10f)
        nameLabel.foreground = Color.WHITE
        infoPanel.add(nameLabel)

        val prefixLabel = JLabel("Prefix: ${Paths.get(game.prefix).fileName}")
        prefixLabel.font = prefixLabel.font.deriveFont(8f)
        prefixLabel.foreground = Color.LIGHT_GRAY
        infoPanel.add(prefixLabel)

        add(infoPanel)
        add(Box.createHorizontalGlue())

        val loBtn = JButton("Params").apply {
            preferredSize = Dimension(100, 28)
            maximumSize = Dimension(100, 28)
            toolTipText = "Launch Options"
            addActionListener { onLaunchOptions(game) }
        }
        add(loBtn)
        add(Box.createHorizontalStrut(5))

        val wpmBtn = JButton("Winetricks").apply {
            preferredSize = Dimension(100, 28)
            maximumSize = Dimension(100, 28)
            toolTipText = "Wineprefix Manager (Winetricks)"
            addActionListener { onPrefixManager(game) }
        }
        add(wpmBtn)
        add(Box.createHorizontalStrut(5))

        val pmwBtn = JButton("Proton").apply {
            preferredSize = Dimension(90, 28)
            maximumSize = Dimension(90, 28)
            toolTipText = "Proton Manager Window"
            addActionListener { onProtonManager(game) }
        }
        add(pmwBtn)
        add(Box.createHorizontalStrut(5))

        val changePrefixBtn = JButton("Change Prefix").apply {
            preferredSize = Dimension(120, 28)
            maximumSize = Dimension(120, 28)
            addActionListener { onChangePrefix(game) }
        }
        add(changePrefixBtn)
        add(Box.createHorizontalStrut(5))

        val launchBtn = JButton("Launch").apply {
            preferredSize = Dimension(100, 28)
            maximumSize = Dimension(100, 28)
            font = font.deriveFont(Font.BOLD)
            addActionListener { onLaunch(game) }
        }
        add(launchBtn)

        setupContextMenu()
    }

    private fun setupContextMenu() {
        val popupMenu = JPopupMenu()

        val openGameLocationItem = JMenuItem("Open game location").apply {
            addActionListener {
                openGameLocation()
            }
        }

        val openPrefixLocationItem = JMenuItem("Open wine prefix location").apply {
            addActionListener {
                openPrefixLocation()
            }
        }

        popupMenu.add(openGameLocationItem)
        popupMenu.add(openPrefixLocationItem)

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
}

class GameItemWidgetWithImage(
    private val game: Game,
    private val onLaunch: (Game) -> Unit,
    private val onChangePrefix: (Game) -> Unit,
    private val onProtonManager: (Game) -> Unit,
    private val onPrefixManager: (Game) -> Unit,
    private val onLaunchOptions: (Game) -> Unit,
    private val onSaveGames: () -> Unit
) : JPanel() {

    private val imageLabel = JLabel()

    init {
        initUI()
    }

    private fun initUI() {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        border = BorderFactory.createCompoundBorder(
            LineBorder(Color(0xCC, 0xCC, 0xCC), 1, true),
            EmptyBorder(5, 5, 5, 5)
        )
        background = Color(0x00, 0x00, 0x00)

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

        val number = Regex("/(\\d+)/").find(game.prefix)?.groupValues?.get(1)
        val prefixLabel = JLabel("Prefix: ${number}")
        prefixLabel.font = prefixLabel.font.deriveFont(11f)
        prefixLabel.foreground = Color.LIGHT_GRAY
        infoPanel.add(prefixLabel)

        add(infoPanel)
        add(Box.createHorizontalGlue())

        val loBtn = JButton("Params").apply {
            preferredSize = Dimension(100, 28)
            maximumSize = Dimension(100, 28)
            toolTipText = "Launch Options"
            addActionListener { onLaunchOptions(game) }
        }
        add(loBtn)
        add(Box.createHorizontalStrut(5))

        val wpmBtn = JButton("Winetricks").apply {
            preferredSize = Dimension(100, 28)
            maximumSize = Dimension(100, 28)
            toolTipText = "Wineprefix Manager (Winetricks)"
            addActionListener { onPrefixManager(game) }
        }
        add(wpmBtn)
        add(Box.createHorizontalStrut(5))

        val pmwBtn = JButton("Proton").apply {
            preferredSize = Dimension(90, 28)
            maximumSize = Dimension(90, 28)
            toolTipText = "Proton Manager Window"
            addActionListener { onProtonManager(game) }
        }
        add(pmwBtn)
        add(Box.createHorizontalStrut(5))

        val changePrefixBtn = JButton("Change Prefix").apply {
            preferredSize = Dimension(120, 28)
            maximumSize = Dimension(120, 28)
            addActionListener { onChangePrefix(game) }
        }
        add(changePrefixBtn)
        add(Box.createHorizontalStrut(5))

        val launchBtn = JButton("Launch").apply {
            preferredSize = Dimension(100, 28)
            maximumSize = Dimension(100, 28)
            font = font.deriveFont(Font.BOLD)
            addActionListener { onLaunch(game) }
        }
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

        val openGameLocationItem = JMenuItem("Open game location").apply {
            addActionListener {
                openGameLocation()
            }
        }

        val openPrefixLocationItem = JMenuItem("Open wine prefix location").apply {
            addActionListener {
                openPrefixLocation()
            }
        }

        popupMenu.add(openGameLocationItem)
        popupMenu.add(openPrefixLocationItem)

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
            val configDir = Paths.get(System.getProperty("user.home"), ".config", "hydra").toFile()
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

class GameLauncher : JFrame("Hydra") {
    private val games = mutableListOf<Game>()
    private var availablePrefixes = listOf<PrefixInfo>()
    private val configFile = Paths.get(System.getProperty("user.home"), ".config", "hydra", "games.json")
    private val gameProcesses = mutableMapOf<String, Process>()
    private val outputWindows = mutableMapOf<String, GameOutputWindow>()
    private val gamesContainer = JPanel()
    private val statusLabel = JLabel("Ready")
    private val logReaderThreads = mutableListOf<Thread>()

    @Volatile
    private var isShuttingDown = false

    private val gson = Gson()

    init {
        initUI()
        loadGames()
        scanPrefixes()

        Runtime.getRuntime().addShutdownHook(Thread {
            cleanup()
        })
    }

    private fun initUI() {
        minimumSize = Dimension(800, 600)
        defaultCloseOperation = DO_NOTHING_ON_CLOSE

        addWindowListener(object : java.awt.event.WindowAdapter() {
            override fun windowClosing(e: java.awt.event.WindowEvent?) {
                handleApplicationClose()
            }
        })

        val mainPanel = JPanel(BorderLayout(10, 10))
        mainPanel.border = EmptyBorder(10, 10, 10, 10)

        val titleLabel = JLabel("Game Library")
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 14f)
        mainPanel.add(titleLabel, BorderLayout.NORTH)

        gamesContainer.layout = BoxLayout(gamesContainer, BoxLayout.Y_AXIS)
        gamesContainer.border = EmptyBorder(10, 5, 5, 5)

        val scrollPane = JScrollPane(gamesContainer)
        scrollPane.border = BorderFactory.createTitledBorder("")
        mainPanel.add(scrollPane, BorderLayout.CENTER)

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

        val addBtn = JButton("Add Game").apply {
            preferredSize = Dimension(150, 35)
            font = font.deriveFont(Font.BOLD)
            addActionListener { addGame() }
        }

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
                loadedGames.forEach { game ->
                    if (game.launchOptions == null) {
                        game.launchOptions = mutableMapOf()
                    }
                    if (game.imagePath == null) {
                        game.imagePath = null
                    }
                }
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
        gamesContainer.removeAll()

        games.forEach { game ->
            val gameWidget =
                GameItemWidgetWithImage(
                    game,
                    ::launchGame,
                    ::changeGamePrefix,
                    ::openProtonManager,
                    ::openPrefixManager,
                    ::openLaunchOptions,
                    ::saveGames
                )
            gameWidget.maximumSize = Dimension(Int.MAX_VALUE, 70)
            gamesContainer.add(gameWidget)
            gamesContainer.add(Box.createVerticalStrut(5))
        }

        gamesContainer.add(Box.createVerticalGlue())
        gamesContainer.revalidate()
        gamesContainer.repaint()
    }

    private fun addGame() {
        val dialog = AddGameDialog(availablePrefixes, this)
        dialog.isVisible = true

        dialog.gameData?.let { newGame ->
            games.add(newGame)
            saveGames()
            refreshGamesList()
            statusLabel.text = "Added ${newGame.name}"
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
            if (gameInList.launchOptions == null) {
                gameInList.launchOptions = mutableMapOf()
            }

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

    private fun launchGame(game: Game) {
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

        val outputWindow = GameOutputWindow(gameName, this) { abortGameName ->
            abortGameLaunch(abortGameName)
        }
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

            if (outputWindow.isVerboseMode()) {
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
                        it.key == "MANGOHUD" ||
                        (game.launchOptions.containsKey(it.key) == true)
            }.forEach { (key, value) ->
                outputWindow.appendOutput("  $key=$value")
            }
            if (game.launchOptions.isNotEmpty() == true) {
                outputWindow.appendOutput(
                    "Custom launch options: ${game.launchOptions.size ?: 0} variable(s)",
                    "#00aa00"
                )
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

            statusLabel.text = "$gameName is running (PID: $pid)"

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
                                                if (outputWindow.isVerboseMode()) {
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

                if (!isShuttingDown) {
                    SwingUtilities.invokeLater {
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

                gameProcesses.remove(gameName)
                logReaderThreads.removeAll { !it.isAlive }
                statusLabel.text = "Aborted launch of $gameName"
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
