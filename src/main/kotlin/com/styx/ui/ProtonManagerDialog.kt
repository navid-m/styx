package com.styx.ui

import com.styx.models.Game
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.nio.file.Files
import java.nio.file.Paths
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JDialog
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.border.EmptyBorder

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