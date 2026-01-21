package com.styx.ui

import com.styx.api.SteamApiHelper
import com.styx.utils.formatTimePlayed
import com.styx.models.Game
import com.styx.models.GameType
import com.styx.models.PrefixInfo
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Desktop
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Image
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths
import javax.imageio.ImageIO
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JDialog
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel
import kotlin.math.min

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
        titleLabel.alignmentX = LEFT_ALIGNMENT
        mainPanel.add(titleLabel)
        mainPanel.add(Box.createVerticalStrut(15))

        val loggingPanel = JPanel()
        loggingPanel.layout = BoxLayout(loggingPanel, BoxLayout.Y_AXIS)
        loggingPanel.border = BorderFactory.createTitledBorder("Logging Options")
        loggingPanel.alignmentX = LEFT_ALIGNMENT

        verboseCheckbox.alignmentX = LEFT_ALIGNMENT
        verboseCheckbox.toolTipText = "When enabled, shows all Wine debug output for this game"
        loggingPanel.add(verboseCheckbox)
        loggingPanel.add(Box.createVerticalStrut(5))

        val loggingInfoLabel =
            JLabel("<html><small>Verbose logging can help diagnose issues but generates a lot of output</small></html>")
        loggingInfoLabel.foreground = Color(0x88, 0x88, 0x88)
        loggingInfoLabel.alignmentX = LEFT_ALIGNMENT
        loggingPanel.add(loggingInfoLabel)

        mainPanel.add(loggingPanel)
        mainPanel.add(Box.createVerticalStrut(15))

        val protonDbPanel = JPanel()
        protonDbPanel.layout = BoxLayout(protonDbPanel, BoxLayout.Y_AXIS)
        protonDbPanel.border = BorderFactory.createTitledBorder("ProtonDB Integration")
        protonDbPanel.alignmentX = LEFT_ALIGNMENT

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
        pdbInfoLabel.alignmentX = LEFT_ALIGNMENT

        protonDbPanel.add(steamIdPanel)
        protonDbPanel.add(Box.createVerticalStrut(5))
        protonDbPanel.add(pdbInfoLabel)

        mainPanel.add(protonDbPanel)
        mainPanel.add(Box.createVerticalStrut(15))

        val actionsPanel = JPanel()
        actionsPanel.layout = BoxLayout(actionsPanel, BoxLayout.Y_AXIS)
        actionsPanel.border = BorderFactory.createTitledBorder("Configuration Actions")
        actionsPanel.alignmentX = LEFT_ALIGNMENT

        val paramsBtn = JButton("Launch Parameters...").apply {
            maximumSize = Dimension(Short.MAX_VALUE.toInt(), 32)
            alignmentX = LEFT_ALIGNMENT
            toolTipText = "Configure launch options and environment variables"
            addActionListener {
                onLaunchOptions(game)
            }
        }
        actionsPanel.add(paramsBtn)
        actionsPanel.add(Box.createVerticalStrut(8))

        val winetricksBtn = JButton("Winetricks...").apply {
            maximumSize = Dimension(Short.MAX_VALUE.toInt(), 32)
            alignmentX = LEFT_ALIGNMENT
            toolTipText = "Wineprefix Manager (install Windows components, configure Wine)"
            addActionListener {
                onPrefixManager(game)
            }
        }
        actionsPanel.add(winetricksBtn)
        actionsPanel.add(Box.createVerticalStrut(8))

        val protonBtn = JButton("Proton Manager...").apply {
            maximumSize = Dimension(Short.MAX_VALUE.toInt(), 32)
            alignmentX = LEFT_ALIGNMENT
            toolTipText = "Select and manage Proton compatibility layer"
            addActionListener {
                onProtonManager(game)
            }
        }
        actionsPanel.add(protonBtn)
        actionsPanel.add(Box.createVerticalStrut(8))

        val changePrefixBtn = JButton("Change Wineprefix...").apply {
            maximumSize = Dimension(Short.MAX_VALUE.toInt(), 32)
            alignmentX = LEFT_ALIGNMENT
            toolTipText = "Change the Wine prefix used by this game"
            addActionListener {
                onChangePrefix(game)
            }
        }

        actionsPanel.add(changePrefixBtn)
        mainPanel.add(actionsPanel)
        mainPanel.add(Box.createVerticalGlue())

        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0))
        buttonPanel.alignmentX = LEFT_ALIGNMENT
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

    private val tableModel = DefaultTableModel(arrayOf("Variable", "Value"), 0)
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

class AddNativeGameDialog(private val launcher: GameLauncher) : JDialog(launcher, "Add Native Linux Game", true) {

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

class GameItemWidgetWithImage(
    private val game: Game,
    private val launcher: GameLauncher,
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
        nameLabel.foreground = launcher.globalSettings.theme.getGameTitleColorObject()
        infoPanel.add(nameLabel)

        val typeOrPrefixLabel: JLabel = when (game.getGameType()) {
            GameType.NATIVE_LINUX -> JLabel("Type: Native Linux").apply {
                font = font.deriveFont(11f)
                foreground = launcher.globalSettings.theme.getMetadataLabelColorObject()
            }

            GameType.STEAM -> JLabel("Type: Steam").apply {
                font = font.deriveFont(11f)
                foreground = launcher.globalSettings.theme.getMetadataLabelColorObject()
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
                foreground = launcher.globalSettings.theme.getTimePlayedColorObject()
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
                    val bufferedImage = ImageIO.read(imageFile)
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

        imageLabel.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (e.isPopupTrigger) {
                    imagePopupMenu.show(e.component, e.x, e.y)
                }
            }

            override fun mouseReleased(e: MouseEvent) {
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

        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (e.isPopupTrigger) {
                    popupMenu.show(e.component, e.x, e.y)
                }
            }

            override fun mouseReleased(e: MouseEvent) {
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
                val uri = URI("https://www.protondb.com/app/${game.steamAppId}")
                Desktop.getDesktop().browse(uri)
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
        titleLabel.foreground = launcher.globalSettings.theme.getGameTitleColorObject()
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
        val tableModel = DefaultTableModel(tableData, columnNames)
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
            fileFilter = FileNameExtensionFilter(
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
                    min(20, sourceFile.nameWithoutExtension.length)
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