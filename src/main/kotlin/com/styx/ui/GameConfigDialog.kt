package com.styx.ui

import com.styx.api.SteamApiHelper
import com.styx.models.Game
import com.styx.models.GameType
import com.styx.utils.formatTimePlayed
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Desktop
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.io.File
import java.net.URI
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder

class GameConfigDialog(
    private val game: Game,
    private val launcher: GameLauncher,
    private val onLaunchOptions: (Game) -> Unit,
    private val onPrefixManager: (Game) -> Unit,
    private val onProtonManager: (Game) -> Unit,
    private val onChangePrefix: (Game) -> Unit,
    private val onRename: (Game) -> Unit
) : JDialog(launcher, "Configure - ${game.name}", true) {

    private val wineLogLevels = mapOf(
        "Disabled (-all)" to "-all",
        "Errors Only (err+all)" to "err+all",
        "Warnings (warn+all)" to "warn+all",
        "Default (warn+all,fixme-all)" to "warn+all,fixme-all",
        "Verbose (+all)" to "+all"
    )

    private val logLevelComboBox = JComboBox(wineLogLevels.keys.toTypedArray())
    private val steamAppIdInput = JTextField(20)
    private val cpuGovernorComboBox =
        JComboBox(arrayOf("Default (system)", "performance", "powersave", "ondemand", "conservative", "schedutil"))

    init {
        initUI()
    }

    private fun initUI() {
        minimumSize = Dimension(750, 690)
        preferredSize = Dimension(750, 690)
        maximumSize = Dimension(780, 720)

        val mainPanel = JPanel(BorderLayout(15, 15))
        mainPanel.border = EmptyBorder(15, 15, 15, 15)

        val titleLabel = JLabel("Game Configuration")
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 16f)
        mainPanel.add(titleLabel, BorderLayout.NORTH)

        val centerPanel = JPanel()
        centerPanel.layout = BoxLayout(centerPanel, BoxLayout.X_AXIS)

        val leftPanel = JPanel()
        leftPanel.layout = BoxLayout(leftPanel, BoxLayout.Y_AXIS)
        leftPanel.alignmentX = LEFT_ALIGNMENT

        val loggingPanel = JPanel()
        loggingPanel.layout = BoxLayout(loggingPanel, BoxLayout.Y_AXIS)
        loggingPanel.border = BorderFactory.createTitledBorder("Wine Debug Level")
        loggingPanel.alignmentX = LEFT_ALIGNMENT
        loggingPanel.maximumSize = Dimension(400, 120)

        val logLevelPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 5))
        logLevelPanel.add(JLabel("Log Level:"))

        val currentLogLevel = if (game.verboseLogging) {
            "+all"
        } else {
            game.wineLogLevel
        }

        wineLogLevels.entries.find { it.value == currentLogLevel }?.let { entry ->
            logLevelComboBox.selectedItem = entry.key
        }

        logLevelPanel.add(logLevelComboBox)
        loggingPanel.add(logLevelPanel)
        loggingPanel.add(Box.createVerticalStrut(5))

        val loggingInfoLabel =
            JLabel("<html><small>Higher log levels generate more output but can help diagnose issues</small></html>")
        loggingInfoLabel.foreground = Color(0x88, 0x88, 0x88)
        loggingInfoLabel.alignmentX = LEFT_ALIGNMENT
        loggingPanel.add(loggingInfoLabel)

        leftPanel.add(loggingPanel)
        leftPanel.add(Box.createVerticalStrut(15))

        val cpuGovernorPanel = JPanel()
        cpuGovernorPanel.layout = BoxLayout(cpuGovernorPanel, BoxLayout.Y_AXIS)
        cpuGovernorPanel.border = BorderFactory.createTitledBorder("CPU Governor")
        cpuGovernorPanel.alignmentX = LEFT_ALIGNMENT
        cpuGovernorPanel.maximumSize = Dimension(400, 120)

        val governorPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 5))
        governorPanel.add(JLabel("Governor:"))

        val currentGovernor = game.cpuGovernor
        if (currentGovernor != null) {
            cpuGovernorComboBox.selectedItem = currentGovernor
        } else {
            cpuGovernorComboBox.selectedItem = "Default (system)"
        }

        governorPanel.add(cpuGovernorComboBox)
        cpuGovernorPanel.add(governorPanel)
        cpuGovernorPanel.add(Box.createVerticalStrut(5))

        val governorInfoLabel =
            JLabel("<html><small>Control CPU frequency scaling for this game (requires root privileges)</small></html>")
        governorInfoLabel.foreground = Color(0x88, 0x88, 0x88)
        governorInfoLabel.alignmentX = LEFT_ALIGNMENT
        cpuGovernorPanel.add(governorInfoLabel)

        leftPanel.add(cpuGovernorPanel)
        leftPanel.add(Box.createVerticalStrut(15))

        val protonDbPanel = JPanel()
        protonDbPanel.layout = BoxLayout(protonDbPanel, BoxLayout.Y_AXIS)
        protonDbPanel.border = BorderFactory.createTitledBorder("ProtonDB Integration")
        protonDbPanel.alignmentX = LEFT_ALIGNMENT
        protonDbPanel.maximumSize = Dimension(400, 120)

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

        leftPanel.add(protonDbPanel)
        leftPanel.add(Box.createVerticalStrut(15))

        val actionsPanel = JPanel()
        actionsPanel.layout = BoxLayout(actionsPanel, BoxLayout.Y_AXIS)
        actionsPanel.border = BorderFactory.createTitledBorder("Configuration Actions")
        actionsPanel.alignmentX = LEFT_ALIGNMENT
        actionsPanel.maximumSize = Dimension(400, 250)

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
        leftPanel.add(actionsPanel)
        leftPanel.add(Box.createVerticalStrut(15))

        val lutrisPanel = JPanel(BorderLayout(5, 5))
        lutrisPanel.border = BorderFactory.createTitledBorder("Install Script")
        lutrisPanel.alignmentX = LEFT_ALIGNMENT
        lutrisPanel.maximumSize = Dimension(400, 90)

        val scriptButtonPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 0))

        val runScriptBtn = JButton("Run Script").apply {
            preferredSize = Dimension(120, 28)
            isEnabled = game.lutrisScriptPath != null
        }

        val clearScriptBtn = JButton("Clear").apply {
            preferredSize = Dimension(80, 28)
            isEnabled = game.lutrisScriptPath != null
        }

        val scriptLabel = JLabel(
            if (game.lutrisScriptPath != null) {
                "<html><small>Script loaded: ${File(game.lutrisScriptPath!!).name}</small></html>"
            } else {
                "<html><small>No script loaded</small></html>"
            }
        )
        scriptLabel.foreground = Color(0x88, 0x88, 0x88)
        scriptLabel.horizontalAlignment = SwingConstants.RIGHT

        val loadScriptBtn = JButton("Load Script...").apply {
            preferredSize = Dimension(120, 28)
            addActionListener {
                loadLutrisScript(scriptLabel, runScriptBtn, clearScriptBtn)
            }
        }
        scriptButtonPanel.add(loadScriptBtn)

        runScriptBtn.addActionListener {
            runLutrisScript()
        }
        scriptButtonPanel.add(runScriptBtn)

        clearScriptBtn.addActionListener {
            game.lutrisScriptPath = null
            scriptLabel.text = "<html><small>No script loaded</small></html>"
            runScriptBtn.isEnabled = false
            clearScriptBtn.isEnabled = false
        }
        scriptButtonPanel.add(clearScriptBtn)

        lutrisPanel.add(scriptButtonPanel, BorderLayout.NORTH)
        lutrisPanel.add(scriptLabel, BorderLayout.SOUTH)

        leftPanel.add(lutrisPanel)
        leftPanel.add(Box.createVerticalGlue())

        val rightPanel = JPanel()
        rightPanel.layout = BoxLayout(rightPanel, BoxLayout.Y_AXIS)
        rightPanel.preferredSize = Dimension(320, 120)
        rightPanel.alignmentX = LEFT_ALIGNMENT

        val additionalActionsPanel = JPanel()
        additionalActionsPanel.layout = BoxLayout(additionalActionsPanel, BoxLayout.Y_AXIS)
        additionalActionsPanel.border = BorderFactory.createTitledBorder("Quick Actions")
        additionalActionsPanel.alignmentX = LEFT_ALIGNMENT

        val troubleshootBtn = JButton("Troubleshoot Game").apply {
            maximumSize = Dimension(Short.MAX_VALUE.toInt(), 32)
            alignmentX = LEFT_ALIGNMENT
            toolTipText = "Diagnose potential issues causing crashes or poor performance"
            addActionListener {
                showTroubleshootDialog()
            }
        }
        additionalActionsPanel.add(troubleshootBtn)
        additionalActionsPanel.add(Box.createVerticalStrut(8))

        val statsBtn = JButton("Show Statistics").apply {
            maximumSize = Dimension(Short.MAX_VALUE.toInt(), 32)
            alignmentX = LEFT_ALIGNMENT
            toolTipText = "View game statistics"
            addActionListener {
                showStatistics()
            }
        }
        additionalActionsPanel.add(statsBtn)
        additionalActionsPanel.add(Box.createVerticalStrut(8))

        val renameBtn = JButton("Rename Game").apply {
            maximumSize = Dimension(Short.MAX_VALUE.toInt(), 32)
            alignmentX = LEFT_ALIGNMENT
            toolTipText = "Rename this game"
            addActionListener {
                renameGame()
            }
        }
        additionalActionsPanel.add(renameBtn)
        additionalActionsPanel.add(Box.createVerticalStrut(8))

        val openGameLocationBtn = JButton("Open Game Location").apply {
            maximumSize = Dimension(Short.MAX_VALUE.toInt(), 32)
            alignmentX = LEFT_ALIGNMENT
            toolTipText = "Open the game's directory in file manager"
            addActionListener {
                openGameLocation()
            }
        }
        additionalActionsPanel.add(openGameLocationBtn)

        if (game.getGameType() == GameType.WINDOWS) {
            additionalActionsPanel.add(Box.createVerticalStrut(8))
            val openPrefixBtn = JButton("Open Wine Prefix Location").apply {
                maximumSize = Dimension(Short.MAX_VALUE.toInt(), 32)
                alignmentX = LEFT_ALIGNMENT
                toolTipText = "Open the wine prefix directory"
                addActionListener {
                    openPrefixLocation()
                }
            }
            additionalActionsPanel.add(openPrefixBtn)

            if (!game.steamAppId.isNullOrEmpty()) {
                additionalActionsPanel.add(Box.createVerticalStrut(8))
                val protonDbBtn = JButton("Open ProtonDB").apply {
                    maximumSize = Dimension(Short.MAX_VALUE.toInt(), 32)
                    alignmentX = LEFT_ALIGNMENT
                    toolTipText = "Open this game's ProtonDB page"
                    addActionListener {
                        openProtonDB()
                    }
                }
                additionalActionsPanel.add(protonDbBtn)
            }
        }

        rightPanel.add(additionalActionsPanel)
        rightPanel.add(Box.createVerticalGlue())
        centerPanel.add(leftPanel)
        centerPanel.add(Box.createHorizontalStrut(15))
        centerPanel.add(rightPanel)
        mainPanel.add(centerPanel, BorderLayout.CENTER)

        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0))

        val saveBtn = JButton("Save").apply {
            preferredSize = Dimension(80, 32)
            addActionListener {
                val selectedLogLevel = logLevelComboBox.selectedItem as? String
                game.wineLogLevel = wineLogLevels[selectedLogLevel] ?: "warn+all,fixme-all"
                game.verboseLogging = false
                game.steamAppId = steamAppIdInput.text.trim().takeIf { it.isNotEmpty() }

                val selectedGovernor = cpuGovernorComboBox.selectedItem as? String
                game.cpuGovernor = when (selectedGovernor) {
                    "Default (system)" -> null
                    else -> selectedGovernor
                }

                dispose()
            }
        }

        val cancelBtn = JButton("Cancel").apply {
            preferredSize = Dimension(80, 32)
            addActionListener { dispose() }
        }

        buttonPanel.add(saveBtn)
        buttonPanel.add(cancelBtn)

        mainPanel.add(buttonPanel, BorderLayout.SOUTH)

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

    private fun showTroubleshootDialog() {
        val troubleshootDialog = TroubleshootDialog(game, launcher)
        troubleshootDialog.isVisible = true
    }

    private fun showStatistics() {
        val statsWindow = JDialog(this, "Stats - ${game.name}", true)
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
            arrayOf("Compatibility Layer", game.protonVersion ?: "Wine (default)")
        )

        val columnNames = arrayOf("Statistic", "Value")
        val tableModel = javax.swing.table.DefaultTableModel(tableData, columnNames)
        val table = javax.swing.JTable(tableModel)
        table.font = table.font.deriveFont(14f)
        table.rowHeight = 30
        table.setEnabled(false)

        val scrollPane = javax.swing.JScrollPane(table)
        mainPanel.add(scrollPane, BorderLayout.CENTER)

        statsWindow.contentPane = mainPanel
        statsWindow.isVisible = true
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
            title = "Configure - ${game.name}"
        }
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

    private fun loadLutrisScript(statusLabel: JLabel, runBtn: JButton, clearBtn: JButton) {
        val fileChooser = javax.swing.JFileChooser()
        fileChooser.dialogTitle = "Select Install Script"
        fileChooser.fileFilter = javax.swing.filechooser.FileNameExtensionFilter("YAML files", "yaml", "yml")

        if (fileChooser.showOpenDialog(this) == javax.swing.JFileChooser.APPROVE_OPTION) {
            val selectedFile = fileChooser.selectedFile
            game.lutrisScriptPath = selectedFile.absolutePath
            statusLabel.text = "<html><small>Script loaded: ${selectedFile.name}</small></html>"
            runBtn.isEnabled = true
            clearBtn.isEnabled = true

            JOptionPane.showMessageDialog(
                this,
                "Script loaded successfully.\nUse 'Run Script' to execute it.",
                "Success",
                JOptionPane.INFORMATION_MESSAGE
            )
        }
    }

    private fun runLutrisScript() {
        val scriptPath = game.lutrisScriptPath ?: return

        val scriptFile = File(scriptPath)
        if (!scriptFile.exists()) {
            JOptionPane.showMessageDialog(
                this,
                "Script file not found: $scriptPath",
                "Error",
                JOptionPane.ERROR_MESSAGE
            )
            return
        }

        val result = JOptionPane.showConfirmDialog(
            this,
            "This will execute the install script.\n\n" +
                    "The script may:\n" +
                    "- Download files\n" +
                    "- Create/modify Wine prefix\n" +
                    "- Install dependencies via winetricks\n" +
                    "- Run installers\n\n" +
                    "Continue?",
            "Run Script",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        )

        if (result != JOptionPane.YES_OPTION) return

        val outputWindow = com.styx.ui.GameOutputWindow(
            gameName = "${game.name} - Install Script",
            parent = launcher,
            onAbort = {},
            prefixPath = game.prefix,
            useProton = false,
            verboseLogging = true,
            wineLogLevel = "+all"
        )
        outputWindow.isVisible = true

        Thread {
            try {
                val yamlContent = scriptFile.readText()
                val lutrisScript = com.styx.workers.LutrisScriptRunner.parseYaml(yamlContent)

                if (lutrisScript == null) {
                    SwingUtilities.invokeLater {
                        outputWindow.appendOutput("ERROR: Failed to parse script", "#cc0000")
                        JOptionPane.showMessageDialog(
                            this,
                            "Failed to parse script. Check YAML syntax.",
                            "Parse Error",
                            JOptionPane.ERROR_MESSAGE
                        )
                    }
                    return@Thread
                }

                val gameDir = File(game.prefix).parentFile?.absolutePath ?: game.prefix
                val runner = com.styx.workers.LutrisScriptRunner(
                    lutrisScript,
                    gameDir
                ) { message, color ->
                    SwingUtilities.invokeLater {
                        outputWindow.appendOutput(message, color)
                    }
                }

                val success = runner.executeInstaller()

                SwingUtilities.invokeLater {
                    if (success) {
                        lutrisScript.game?.let { gameConfig ->
                            gameConfig.exe?.let { exe ->
                                val resolvedExe = exe.replace("\$GAMEDIR", gameDir)
                                val exePath = File(gameDir, resolvedExe)
                                if (exePath.exists()) {
                                    game.executable = exePath.absolutePath
                                    outputWindow.appendOutput("", null)
                                    outputWindow.appendOutput("Updated game executable: ${game.executable}", "#0066cc")
                                }
                            }

                            gameConfig.prefix?.let { prefix ->
                                val resolvedPrefix = prefix.replace("\$GAMEDIR", gameDir)
                                game.prefix = resolvedPrefix
                                outputWindow.appendOutput("Updated game prefix: ${game.prefix}", "#0066cc")
                            }
                        }

                        lutrisScript.system?.env?.let { env ->
                            outputWindow.appendOutput("", null)
                            outputWindow.appendOutput("Applying environment variables from script...", "#0066cc")
                            env.forEach { (key, value) ->
                                game.launchOptions[key] = value.replace("\$GAMEDIR", gameDir)
                                outputWindow.appendOutput("  $key=$value", null)
                            }
                        }

                        lutrisScript.wine?.overrides?.let { overrides ->
                            outputWindow.appendOutput("", null)
                            outputWindow.appendOutput("Wine DLL overrides from script:", "#0066cc")
                            val overrideString = overrides.entries.joinToString(";") { "${it.key}=${it.value}" }
                            game.launchOptions["WINEDLLOVERRIDES"] = overrideString
                            outputWindow.appendOutput("  WINEDLLOVERRIDES=$overrideString", null)
                        }

                        outputWindow.appendOutput("", null)
                        outputWindow.appendOutput(
                            "Note: Game configuration has been updated. Make sure to save your changes.",
                            "#00aa00"
                        )

                        JOptionPane.showMessageDialog(
                            this,
                            "Script executed successfully.\n\nGame configuration has been updated from the script.",
                            "Success",
                            JOptionPane.INFORMATION_MESSAGE
                        )
                    } else {
                        JOptionPane.showMessageDialog(
                            this,
                            "Script execution failed. Check the output window for details.",
                            "Error",
                            JOptionPane.ERROR_MESSAGE
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                SwingUtilities.invokeLater {
                    outputWindow.appendOutput("FATAL ERROR: ${e.message}", "#cc0000")
                    JOptionPane.showMessageDialog(
                        this,
                        "Error executing script: ${e.message}",
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                    )
                }
            }
        }.start()
    }
}