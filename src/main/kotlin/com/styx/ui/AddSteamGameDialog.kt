package com.styx.ui

import com.styx.api.SteamApiHelper
import com.styx.models.Game
import com.styx.models.GameType
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder

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
        
        val autodetectBtn = JButton("Autodetect").apply {
            preferredSize = Dimension(100, 28)
            toolTipText = "Search Steam for this game by name"
            addActionListener { autodetectSteamAppId() }
        }
        steamIdPanel.add(autodetectBtn)
        
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

        gameData = Game(name, steamAppId, "", type = GameType.STEAM, steamAppId = steamAppId)
        dispose()
    }

    private fun autodetectSteamAppId() {
        val gameName = nameInput.text.trim()
        if (gameName.isEmpty()) {
            JOptionPane.showMessageDialog(
                this,
                "Enter a game name first.",
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
                            steamIdInput.text = results[selectedIndex].appid
                            if (nameInput.text.trim().isEmpty() || nameInput.text.trim() == gameName) {
                                nameInput.text = results[selectedIndex].name
                            }
                        }
                    }
                }
            }
        }.start()

        progressDialog.isVisible = true
    }
}