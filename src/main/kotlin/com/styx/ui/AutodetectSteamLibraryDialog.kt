package com.styx.ui

import com.styx.api.SteamLibraryScanner
import com.styx.models.Game
import com.styx.models.GameType
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.*
import javax.swing.border.EmptyBorder

class AutodetectSteamLibraryDialog(parent: JFrame, existingGames: List<Game>) : JDialog(parent, "Autodetect Steam Library", true) {
    private val checkBoxes = mutableMapOf<String, JCheckBox>()
    private val detectedGames = mutableListOf<SteamLibraryScanner.SteamGame>()
    val selectedGames = mutableListOf<Game>()
    private val existingGameNames = existingGames.map { it.name.lowercase() }.toSet()

    init {
        initUI()
    }

    private fun initUI() {
        minimumSize = Dimension(700, 500)

        val mainPanel = JPanel(BorderLayout(10, 10))
        mainPanel.border = EmptyBorder(15, 15, 15, 15)

        val titleLabel = JLabel("Select games to add to your Styx library:")
        titleLabel.font = titleLabel.font.deriveFont(14f)
        mainPanel.add(titleLabel, BorderLayout.NORTH)

        val progressDialog = JDialog(this, "Scanning Steam Library...", true)
        val progressLabel = JLabel("Scanning your Steam library for installed games...")
        progressLabel.border = EmptyBorder(30, 30, 30, 30)
        progressDialog.contentPane.add(progressLabel)
        progressDialog.pack()
        progressDialog.setLocationRelativeTo(this)

        Thread {
            val games = SteamLibraryScanner.scanSteamLibrary()

            SwingUtilities.invokeLater {
                progressDialog.dispose()

                if (games.isEmpty()) {
                    JOptionPane.showMessageDialog(
                        this,
                        "No Steam games found. Make sure Steam is installed.",
                        "No Games Found",
                        JOptionPane.INFORMATION_MESSAGE
                    )
                    dispose()
                } else {
                    detectedGames.addAll(games)
                    populateGamesList(mainPanel)
                    pack()
                    setLocationRelativeTo(parent)
                }
            }
        }.start()

        contentPane = mainPanel
        pack()
        setLocationRelativeTo(parent)

        progressDialog.isVisible = true
    }

    private fun populateGamesList(mainPanel: JPanel) {
        val gamesPanel = JPanel()
        gamesPanel.layout = BoxLayout(gamesPanel, BoxLayout.Y_AXIS)

        var newGamesCount = 0
        var duplicatesCount = 0

        for (game in detectedGames) {
            val isDuplicate = existingGameNames.contains(game.name.lowercase())
            
            val checkBox = JCheckBox(game.name).apply {
                isSelected = !isDuplicate
                isEnabled = !isDuplicate
                if (isDuplicate) {
                    toolTipText = "Game already exists in library"
                    text = "$text (Already in library)"
                }
            }

            if (isDuplicate) {
                duplicatesCount++
            } else {
                newGamesCount++
            }

            checkBoxes[game.appId] = checkBox
            gamesPanel.add(checkBox)
        }

        val scrollPane = JScrollPane(gamesPanel)
        scrollPane.preferredSize = Dimension(650, 350)
        scrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS
        mainPanel.add(scrollPane, BorderLayout.CENTER)

        val statsPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        val statsLabel = JLabel("Found ${detectedGames.size} games ($newGamesCount new, $duplicatesCount already in library)")
        statsLabel.font = statsLabel.font.deriveFont(11f)
        statsPanel.add(statsLabel)

        val buttonPanel = JPanel(BorderLayout())
        buttonPanel.add(statsPanel, BorderLayout.WEST)

        val actionButtonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0))

        val selectAllBtn = JButton("Select All").apply {
            preferredSize = Dimension(100, 32)
            addActionListener { toggleAllCheckboxes(true) }
        }

        val deselectAllBtn = JButton("Deselect All").apply {
            preferredSize = Dimension(110, 32)
            addActionListener { toggleAllCheckboxes(false) }
        }

        val addBtn = JButton("Add Selected").apply {
            preferredSize = Dimension(120, 32)
            addActionListener { addSelectedGames() }
        }

        val cancelBtn = JButton("Cancel").apply {
            preferredSize = Dimension(90, 32)
            addActionListener { dispose() }
        }

        actionButtonPanel.add(selectAllBtn)
        actionButtonPanel.add(deselectAllBtn)
        actionButtonPanel.add(addBtn)
        actionButtonPanel.add(cancelBtn)

        buttonPanel.add(actionButtonPanel, BorderLayout.EAST)
        mainPanel.add(buttonPanel, BorderLayout.SOUTH)
    }

    private fun toggleAllCheckboxes(selected: Boolean) {
        for (checkBox in checkBoxes.values) {
            if (checkBox.isEnabled) {
                checkBox.isSelected = selected
            }
        }
    }

    private fun addSelectedGames() {
        val selected = detectedGames.filter { game ->
            checkBoxes[game.appId]?.isSelected == true
        }

        if (selected.isEmpty()) {
            JOptionPane.showMessageDialog(
                this,
                "No games selected.",
                "No Selection",
                JOptionPane.WARNING_MESSAGE
            )
            return
        }

        for (game in selected) {
            val newGame = Game(
                name = game.name,
                executable = game.appId,
                prefix = "",
                type = GameType.STEAM,
                steamAppId = game.appId
            )
            selectedGames.add(newGame)
        }

        dispose()
    }
}
