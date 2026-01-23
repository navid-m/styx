package com.styx.ui

import com.styx.models.Game
import com.styx.models.PrefixInfo
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import javax.swing.*
import javax.swing.border.EmptyBorder

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
                } catch (_: Exception) {
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