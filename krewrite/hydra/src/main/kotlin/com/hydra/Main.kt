package com.hydra

import java.awt.*
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

data class Game(
    val name: String,
    val executable: String,
    var prefix: String
)

data class PrefixInfo(
    val name: String,
    val path: String
)

/**
 * The game output window.
 * This is used for debug output from the game.
 */
class GameOutputWindow(private val gameName: String, parent: JFrame? = null) : JFrame("Game Output - $gameName") {
    private val outputText = JTextPane()
    private val verboseCheckbox = JCheckBox("Verbose Mode (show all Wine debug)", false)

    init {
        initUI()
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
        val scrollPane = JScrollPane(outputText)
        contentPanel.add(scrollPane, BorderLayout.CENTER)

        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0))

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
            addActionListener { dispose() }
        }

        buttonPanel.add(saveBtn)
        buttonPanel.add(clearBtn)
        buttonPanel.add(closeBtn)
        contentPanel.add(buttonPanel, BorderLayout.SOUTH)

        contentPane = contentPanel
    }

    fun appendOutput(text: String, color: String? = null) {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"))
        val doc = outputText.styledDocument
        val style = outputText.addStyle("Style", null)

        if (color != null) {
            javax.swing.text.StyleConstants.setForeground(style, Color.decode(color))
        } else {
            javax.swing.text.StyleConstants.setForeground(style, Color.BLACK)
        }

        try {
            doc.insertString(doc.length, "[$timestamp] $text\n", style)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        outputText.caretPosition = doc.length
    }

    fun clearOutput() {
        outputText.text = ""
    }

    private fun saveLog() {
        val chooser = JFileChooser()
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        chooser.selectedFile = File("${gameName}_log_$timestamp.txt")

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                chooser.selectedFile.writeText(outputText.text)
                JOptionPane.showMessageDialog(
                    this,
                    "Log saved to ${chooser.selectedFile.absolutePath}",
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

        println("[SCAN] Starting Wine prefix scan...")
        val startTime = System.currentTimeMillis()

        val home = Paths.get(System.getProperty("user.home"))
        val commonLocations = listOf(
            home.resolve(".steam/steam/steamapps/compatdata"),
            home.resolve(".local/share/Steam/steamapps/compatdata"),
            Paths.get("/usr/share/Steam/steamapps/compatdata")
        )

        println("[SCAN] Checking common Steam locations...")
        for (compatdataPath in commonLocations) {
            if (compatdataPath.exists()) {
                println("[SCAN] Found: $compatdataPath")
                try {
                    compatdataPath.listDirectoryEntries().forEach { prefixDir ->
                        if (prefixDir.isDirectory()) {
                            val pfxPath = prefixDir.resolve("pfx")
                            if (pfxPath.exists()) {
                                val pfxPathStr = pfxPath.absolutePathString()
                                if (pfxPathStr !in seenPaths) {
                                    seenPaths.add(pfxPathStr)
                                    prefixes.add(PrefixInfo("Proton - ${prefixDir.name}", pfxPathStr))
                                    println("[SCAN] Added prefix: ${prefixDir.name}")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    println("[SCAN] Error scanning $compatdataPath: ${e.message}")
                }
            }
        }
        println("[SCAN] Common locations done. Found ${prefixes.size} prefixes so far.")

        val mountPoints = mutableListOf<String>()

        try {
            val mntPath = Paths.get("/mnt")
            if (mntPath.exists()) {
                println("[SCAN] Checking /mnt...")
                mntPath.listDirectoryEntries().forEach { dir ->
                    if (dir.isDirectory()) {
                        mountPoints.add(dir.absolutePathString())
                        println("[SCAN] Added mount point: ${dir.fileName}")
                    }
                }
            }
        } catch (e: Exception) {
            println("[SCAN] Error scanning /mnt: ${e.message}")
        }

        try {
            val mediaPath = Paths.get("/media")
            if (mediaPath.exists()) {
                println("[SCAN] Checking /media...")
                mediaPath.listDirectoryEntries().forEach { userPath ->
                    try {
                        if (userPath.isDirectory()) {
                            userPath.listDirectoryEntries().forEach { dir ->
                                if (dir.isDirectory()) {
                                    mountPoints.add(dir.absolutePathString())
                                    println("[SCAN] Added mount point: ${dir.fileName}")
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

        println("[SCAN] Found ${mountPoints.size} mount points to scan")

        val skipDirs = setOf(
            "proc", "sys", "dev", "run", "tmp", "snap", "var", "boot", "srv",
            "lost+found", ".cache", ".local/share/Trash", "node_modules", ".git",
            ".svn", "__pycache__", "venv", "virtualenv", "site-packages",
            "Windows", "Program Files", "Program Files (x86)", "windows",
            "dosdevices", "drive_c"
        )

        for (mount in mountPoints) {
            try {
                println("[SCAN] Scanning mount: $mount")
                val mountStart = System.currentTimeMillis()
                scanMountForPrefixes(Paths.get(mount), seenPaths, prefixes, skipDirs, 0, 1)
                val mountTime = System.currentTimeMillis() - mountStart
                println("[SCAN] Finished $mount in ${mountTime}ms")
            } catch (e: Exception) {
                println("[SCAN] Error scanning mount $mount: ${e.message}")
            }
        }

        val totalTime = System.currentTimeMillis() - startTime
        println("[SCAN] Total scan completed in ${totalTime}ms. Found ${prefixes.size} prefixes total.")
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
            println("[SCAN] Max depth reached at: $path")
            return
        }
        if (!path.exists() || !path.isDirectory()) return

        println("[SCAN] [Depth $depth] Scanning: $path")

        try {
            // Check if steamapps exists in this directory
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
                                        println("[SCAN] Added prefix from mount: ${prefixDir.name}")
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        println("[SCAN] Error processing compatdata: ${e.message}")
                    }
                }
                // Don't descend into steamapps directory
                return
            }

            // Recursively scan subdirectories, but skip directories in skipDirs
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
            
            if (skippedCount > 0 || scannedCount > 0) {
                println("[SCAN] [Depth $depth] $path: scanned $scannedCount dirs, skipped $skippedCount dirs")
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
    private val onChangePrefix: (Game) -> Unit
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
        background = Color(0xF9, 0xF9, 0xF9)

        val infoPanel = JPanel()
        infoPanel.layout = BoxLayout(infoPanel, BoxLayout.Y_AXIS)
        infoPanel.isOpaque = false

        val nameLabel = JLabel(game.name)
        nameLabel.font = nameLabel.font.deriveFont(Font.BOLD, 10f)
        nameLabel.foreground = Color.BLACK
        infoPanel.add(nameLabel)

        val prefixLabel = JLabel("Prefix: ${Paths.get(game.prefix).fileName}")
        prefixLabel.font = prefixLabel.font.deriveFont(8f)
        prefixLabel.foreground = Color(0x66, 0x66, 0x66)
        infoPanel.add(prefixLabel)

        add(infoPanel)
        add(Box.createHorizontalGlue())

        val changePrefixBtn = JButton("Change Prefix").apply {
            preferredSize = Dimension(120, 28)
            maximumSize = Dimension(120, 28)
            addActionListener { onChangePrefix(game) }
        }
        add(changePrefixBtn)
        add(Box.createHorizontalStrut(5))

        val launchBtn = JButton("Launch").apply {
            preferredSize = Dimension(80, 28)
            maximumSize = Dimension(80, 28)
            font = font.deriveFont(Font.BOLD)
            addActionListener { onLaunch(game) }
        }
        add(launchBtn)
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

    private val gson = Gson()

    init {
        initUI()
        loadGames()
        scanPrefixes()
    }

    private fun initUI() {
        minimumSize = Dimension(800, 600)
        defaultCloseOperation = EXIT_ON_CLOSE

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

        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0))

        val addBtn = JButton("Add Game").apply {
            preferredSize = Dimension(120, 32)
            addActionListener { addGame() }
        }

        val removeBtn = JButton("Remove Game").apply {
            preferredSize = Dimension(120, 32)
            addActionListener { removeGame() }
        }

        val rescanBtn = JButton("Rescan Prefixes").apply {
            preferredSize = Dimension(150, 32)
            addActionListener { scanPrefixes() }
        }

        buttonPanel.add(addBtn)
        buttonPanel.add(removeBtn)
        buttonPanel.add(rescanBtn)

        mainPanel.add(buttonPanel, BorderLayout.SOUTH)

        contentPane = mainPanel

        val statusPanel = JPanel(BorderLayout())
        statusPanel.border = EmptyBorder(5, 10, 5, 10)
        statusPanel.add(statusLabel, BorderLayout.WEST)
        contentPane.add(statusPanel, BorderLayout.SOUTH)

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
                games.addAll(gson.fromJson(json, type))
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
            val gameWidget = GameItemWidget(game, ::launchGame, ::changeGamePrefix)
            gameWidget.maximumSize = Dimension(Int.MAX_VALUE, 60)
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

        val outputWindow = GameOutputWindow(gameName, this)
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
            env.filter { it.key.startsWith("WINE") || it.key == "DISPLAY" }.forEach { (key, value) ->
                outputWindow.appendOutput("  $key=$value")
            }
            outputWindow.appendOutput("")

            processBuilder.directory(File(exePath).parentFile)
            processBuilder.command("wine", File(exePath).absolutePath)

            outputWindow.appendOutput("=== Starting Wine Process ===", "#0066cc")
            outputWindow.appendOutput("Command: wine ${File(exePath).absolutePath}")
            outputWindow.appendOutput("")

            val process = processBuilder.start()
            gameProcesses[gameName] = process

            // Wait briefly to ensure process starts
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
            outputWindow.appendOutput("✓ Process started successfully (PID: $pid)", "#008800")
            outputWindow.appendOutput("─".repeat(60))
            outputWindow.appendOutput("")

            statusLabel.text = "$gameName is running (PID: $pid)"

            // Thread for reading stdout
            Thread {
                try {
                    process.inputStream.bufferedReader().use { reader ->
                        reader.lineSequence().forEach { line ->
                            if (line.isNotBlank()) {
                                SwingUtilities.invokeLater {
                                    outputWindow.appendOutput("[OUT] ${line.trim()}")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    SwingUtilities.invokeLater {
                        outputWindow.appendOutput("[ERROR reading stdout: ${e.message}]", "#cc0000")
                    }
                }
            }.start()

            // Thread for reading stderr with color coding
            Thread {
                try {
                    process.errorStream.bufferedReader().use { reader ->
                        reader.lineSequence().forEach { line ->
                            if (line.isNotBlank()) {
                                SwingUtilities.invokeLater {
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
                } catch (e: Exception) {
                    SwingUtilities.invokeLater {
                        outputWindow.appendOutput("[ERROR reading stderr: ${e.message}]", "#cc0000")
                    }
                }
            }.start()

            // Thread for monitoring process completion
            Thread {
                val exitCode = process.waitFor()

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

                    gameProcesses.remove(gameName)
                    statusLabel.text = "$gameName exited (code: $exitCode)"
                }
            }.start()

        } catch (e: Exception) {
            outputWindow.appendOutput("", "#cc0000")
            outputWindow.appendOutput("CRITICAL ERROR: ${e.message}", "#cc0000")
            outputWindow.appendOutput("Exception type: ${e.javaClass.simpleName}", "#cc0000")
            outputWindow.appendOutput("")
            outputWindow.appendOutput("Traceback:", "#cc0000")
            e.stackTrace.take(10).forEach { element ->
                outputWindow.appendOutput("  at $element", "#cc0000")
            }

            JOptionPane.showMessageDialog(
                this,
                "Failed to launch game: ${e.message}",
                "Launch Error",
                JOptionPane.ERROR_MESSAGE
            )
            statusLabel.text = "Launch failed"
        }
    }
}

fun main(args: Array<String>) {
    SwingUtilities.invokeLater {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val launcher = GameLauncher()
        launcher.isVisible = true
    }
}

