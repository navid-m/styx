package com.styx.ui

import com.styx.models.GlobalSettings
import com.styx.models.Theme
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.border.TitledBorder

class SettingsDialog(private val parent: GameLauncher) : JDialog(parent, "Settings", true) {
    private val gameTitleColorPanel = JPanel()
    private val timePlayedColorPanel = JPanel()
    private val metadataColorPanel = JPanel()
    private val globalFlagsArea = JTextArea(5, 40)
    
    private var currentSettings = GlobalSettings()

    init {
        currentSettings.theme = Theme(
            parent.globalSettings.theme.gameTitleColor,
            parent.globalSettings.theme.timePlayedColor,
            parent.globalSettings.theme.metadataLabelColor
        )
        currentSettings.globalFlags = parent.globalSettings.globalFlags.toMutableMap()
        
        initUI()
    }

    private fun initUI() {
        minimumSize = Dimension(600, 500)
        preferredSize = Dimension(600, 500)
        
        val mainPanel = JPanel(BorderLayout(10, 10))
        mainPanel.border = EmptyBorder(15, 15, 15, 15)
        
        val contentPanel = JPanel()
        contentPanel.layout = BoxLayout(contentPanel, BoxLayout.Y_AXIS)
        
        contentPanel.add(createThemeSection())
        contentPanel.add(Box.createVerticalStrut(20))
        contentPanel.add(createGlobalFlagsSection())
        
        mainPanel.add(JScrollPane(contentPanel), BorderLayout.CENTER)
        mainPanel.add(createButtonPanel(), BorderLayout.SOUTH)
        
        add(mainPanel)
        pack()
        setLocationRelativeTo(parent)
    }

    private fun createThemeSection(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = TitledBorder("Theme Colors")
        panel.alignmentX = LEFT_ALIGNMENT
        
        panel.add(createColorRow("Game Title Color:", gameTitleColorPanel, currentSettings.theme::gameTitleColor))
        panel.add(Box.createVerticalStrut(10))
        panel.add(createColorRow("Time Played Color:", timePlayedColorPanel, currentSettings.theme::timePlayedColor))
        panel.add(Box.createVerticalStrut(10))
        panel.add(createColorRow("Metadata Label Color:", metadataColorPanel, currentSettings.theme::metadataLabelColor))
        
        return panel
    }

    private fun createColorRow(label: String, colorPanel: JPanel, colorProperty: kotlin.reflect.KMutableProperty0<String>): JPanel {
        val row = JPanel(FlowLayout(FlowLayout.LEFT, 10, 5))
        row.alignmentX = LEFT_ALIGNMENT
        
        val labelComponent = JLabel(label)
        labelComponent.preferredSize = Dimension(180, 25)
        row.add(labelComponent)
        
        colorPanel.preferredSize = Dimension(50, 25)
        colorPanel.border = BorderFactory.createLineBorder(Color.BLACK, 1)
        colorPanel.background = Color.decode(colorProperty.get())
        colorPanel.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        
        colorPanel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                val newColor = JColorChooser.showDialog(
                    this@SettingsDialog,
                    "Choose $label",
                    colorPanel.background
                )
                if (newColor != null) {
                    colorPanel.background = newColor
                    colorProperty.set(String.format("#%02X%02X%02X", newColor.red, newColor.green, newColor.blue))
                }
            }
        })
        
        row.add(colorPanel)
        
        val colorCodeLabel = JLabel(colorProperty.get())
        colorCodeLabel.font = Font("Monospaced", Font.PLAIN, 12)
        colorPanel.addPropertyChangeListener { evt ->
            if (evt.propertyName == "background") {
                val color = colorPanel.background
                val hex = String.format("#%02X%02X%02X", color.red, color.green, color.blue)
                colorCodeLabel.text = hex
            }
        }
        row.add(colorCodeLabel)
        
        return row
    }

    private fun createGlobalFlagsSection(): JPanel {
        val panel = JPanel(BorderLayout(5, 5))
        panel.border = TitledBorder("Global Flags")
        panel.alignmentX = LEFT_ALIGNMENT
        
        val infoLabel = JLabel("<html>These flags are applied to ALL games.<br>Format: KEY=VALUE (one per line)<br>Per-game flags override global flags.</html>")
        infoLabel.border = EmptyBorder(5, 5, 10, 5)
        panel.add(infoLabel, BorderLayout.NORTH)
        
        globalFlagsArea.font = Font("Monospaced", Font.PLAIN, 12)
        globalFlagsArea.lineWrap = false
        
        val flagsText = currentSettings.globalFlags.entries.joinToString("\n") { "${it.key}=${it.value}" }
        globalFlagsArea.text = flagsText
        
        panel.add(JScrollPane(globalFlagsArea), BorderLayout.CENTER)
        
        return panel
    }

    private fun createButtonPanel(): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.RIGHT, 10, 0))
        
        val saveButton = JButton("Save").apply {
            preferredSize = Dimension(100, 30)
            addActionListener {
                saveSettings()
            }
        }
        
        val cancelButton = JButton("Cancel").apply {
            preferredSize = Dimension(100, 30)
            addActionListener {
                dispose()
            }
        }
        
        panel.add(cancelButton)
        panel.add(saveButton)
        
        return panel
    }

    private fun saveSettings() {
        try {
            val flags = mutableMapOf<String, String>()
            globalFlagsArea.text.lines().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty() && trimmed.contains("=")) {
                    val parts = trimmed.split("=", limit = 2)
                    if (parts.size == 2) {
                        flags[parts[0].trim()] = parts[1].trim()
                    }
                }
            }
            
            currentSettings.globalFlags = flags
            
            parent.globalSettings = currentSettings
            parent.saveSettings()
            parent.refreshGamesList()
            
            JOptionPane.showMessageDialog(
                this,
                "Settings saved successfully!",
                "Success",
                JOptionPane.INFORMATION_MESSAGE
            )
            
            dispose()
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(
                this,
                "Failed to save settings: ${e.message}",
                "Error",
                JOptionPane.ERROR_MESSAGE
            )
        }
    }
}
