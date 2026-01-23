package com.styx.ui

import com.styx.api.SteamApi
import com.styx.models.Game
import com.styx.models.PrefixInfo
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.io.File
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.filechooser.FileNameExtensionFilter

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
        chooser.fileFilter = FileNameExtensionFilter(
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
        var steamAppId = steamAppIdInput.text.trim().takeIf { it.isNotEmpty() }

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

        if (steamAppId == null) {
            try {
                val results = SteamApi.searchGameByName(name)
                if (results.isNotEmpty()) {
                    steamAppId = results[0].appid
                }
            } catch (e: Exception) {
                // Ignore.
            }
        }

        gameData = Game(name, exePath, prefixPath, steamAppId = steamAppId)
        dispose()
    }

    private fun findSteamAppId() {
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
            val results = SteamApi.searchGameByName(gameName)

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