package com.styx.ui

import com.styx.api.SteamApiHelper
import com.styx.models.Game
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder

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