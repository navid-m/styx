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

    init {
        initUI()
    }

    private fun initUI() {
        minimumSize = Dimension(450, 450)

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
        loggingPanel.border = BorderFactory.createTitledBorder("Wine Debug Level")
        loggingPanel.alignmentX = LEFT_ALIGNMENT

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
        mainPanel.add(Box.createVerticalStrut(15))

        val additionalActionsPanel = JPanel()
        additionalActionsPanel.layout = BoxLayout(additionalActionsPanel, BoxLayout.Y_AXIS)
        additionalActionsPanel.border = BorderFactory.createTitledBorder("Quick Actions")
        additionalActionsPanel.alignmentX = LEFT_ALIGNMENT

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
                val protonDbBtn = JButton("Open ProtonDB Page").apply {
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

        mainPanel.add(additionalActionsPanel)
        mainPanel.add(Box.createVerticalGlue())

        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0))
        buttonPanel.alignmentX = LEFT_ALIGNMENT
        buttonPanel.maximumSize = Dimension(Short.MAX_VALUE.toInt(), 40)

        val saveBtn = JButton("Save").apply {
            preferredSize = Dimension(80, 32)
            addActionListener {
                val selectedLogLevel = logLevelComboBox.selectedItem as? String
                game.wineLogLevel = wineLogLevels[selectedLogLevel] ?: "warn+all,fixme-all"
                game.verboseLogging = false
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
}