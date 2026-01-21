package com.styx.ui

import com.styx.models.Game
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.border.EmptyBorder
import javax.swing.table.DefaultTableModel

class LaunchOptionsDialog(
    private val game: Game,
    parent: JFrame
) : JDialog(parent, "Launch Options - ${game.name}", true) {

    private val tableModel = DefaultTableModel(arrayOf("Variable", "Value"), 0)
    private val table = JTable(tableModel)
    private val flagsTableModel = DefaultTableModel(arrayOf("Flag"), 0)
    private val flagsTable = JTable(flagsTableModel)
    val launchOptions = mutableMapOf<String, String>()
    val singletonFlags = mutableListOf<String>()

    init {
        initUI()
        loadExistingOptions()
    }

    private fun initUI() {
        minimumSize = Dimension(650, 550)

        val mainPanel = JPanel(BorderLayout(10, 10))
        mainPanel.border = EmptyBorder(10, 10, 10, 10)

        val titleLabel = JLabel("Launch Options")
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 14f)
        titleLabel.border = EmptyBorder(0, 0, 15, 0)
        mainPanel.add(titleLabel, BorderLayout.NORTH)

        val centerPanel = JPanel()
        centerPanel.layout = javax.swing.BoxLayout(centerPanel, javax.swing.BoxLayout.Y_AXIS)

        val envPanel = JPanel(BorderLayout(5, 5))
        envPanel.border = javax.swing.BorderFactory.createTitledBorder("Environment Variables (KEY=VALUE)")
        
        val infoLabel = JLabel(
            "<html><small>Add environment variables that will be set when launching this game.<br>" +
                    "Examples: DXVK_CONFIG_FILE, MANGOHUD, PROTON_USE_WINED3D, etc.</small></html>"
        )
        infoLabel.foreground = Color(0x66, 0x66, 0x66)
        infoLabel.border = EmptyBorder(5, 5, 10, 5)
        envPanel.add(infoLabel, BorderLayout.NORTH)

        table.fillsViewportHeight = true
        table.preferredScrollableViewportSize = Dimension(600, 150)
        val scrollPane = JScrollPane(table)
        envPanel.add(scrollPane, BorderLayout.CENTER)

        val envButtonPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 5))
        val addBtn = JButton("Add").apply {
            preferredSize = Dimension(100, 28)
            addActionListener { addRow() }
        }
        val removeBtn = JButton("Remove").apply {
            preferredSize = Dimension(100, 28)
            addActionListener { removeSelectedRow() }
        }
        val presetBtn = JButton("Presets...").apply {
            preferredSize = Dimension(100, 28)
            addActionListener { showPresets() }
        }
        envButtonPanel.add(addBtn)
        envButtonPanel.add(removeBtn)
        envButtonPanel.add(presetBtn)
        envPanel.add(envButtonPanel, BorderLayout.SOUTH)

        centerPanel.add(envPanel)
        centerPanel.add(javax.swing.Box.createVerticalStrut(15))

        val flagsPanel = JPanel(BorderLayout(5, 5))
        flagsPanel.border = javax.swing.BorderFactory.createTitledBorder("Standalone Flags")
        
        val flagsInfoLabel = JLabel(
            "<html><small>Add standalone flags/arguments without values (e.g., -d3d9, --no-sandbox, etc.)</small></html>"
        )
        flagsInfoLabel.foreground = Color(0x66, 0x66, 0x66)
        flagsInfoLabel.border = EmptyBorder(5, 5, 10, 5)
        flagsPanel.add(flagsInfoLabel, BorderLayout.NORTH)

        flagsTable.fillsViewportHeight = true
        flagsTable.preferredScrollableViewportSize = Dimension(600, 120)
        val flagsScrollPane = JScrollPane(flagsTable)
        flagsPanel.add(flagsScrollPane, BorderLayout.CENTER)

        val flagsButtonPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 5))
        val addFlagBtn = JButton("Add").apply {
            preferredSize = Dimension(100, 28)
            addActionListener { addFlagRow() }
        }
        val removeFlagBtn = JButton("Remove").apply {
            preferredSize = Dimension(100, 28)
            addActionListener { removeSelectedFlagRow() }
        }
        flagsButtonPanel.add(addFlagBtn)
        flagsButtonPanel.add(removeFlagBtn)
        flagsPanel.add(flagsButtonPanel, BorderLayout.SOUTH)

        centerPanel.add(flagsPanel)

        mainPanel.add(centerPanel, BorderLayout.CENTER)
        
        val bottomPanel = JPanel(BorderLayout(10, 0))

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
        
        singletonFlags.clear()
        game.singletonFlags?.forEach { flag ->
            flagsTableModel.addRow(arrayOf(flag))
            singletonFlags.add(flag)
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
    
    private fun addFlagRow() {
        flagsTableModel.addRow(arrayOf(""))
        val lastRow = flagsTableModel.rowCount - 1
        flagsTable.editCellAt(lastRow, 0)
        flagsTable.requestFocus()
    }
    
    private fun removeSelectedFlagRow() {
        val selectedRow = flagsTable.selectedRow
        if (selectedRow >= 0) {
            flagsTableModel.removeRow(selectedRow)
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
            "Enable MangoHUD Detailed" to ("MANGOHUD" to "1"),
            "Set DXVK Log Level (Info)" to ("DXVK_LOG_LEVEL" to "info"),
            "Set DXVK Log Level (Error)" to ("DXVK_LOG_LEVEL" to "error"),
            "Enable Wine Debug Logs" to ("WINEDEBUG" to "+loaddll,+seh"),
            "Enable DXVK Async" to ("DXVK_ASYNC" to "1"),
            "Enable DXVK State Cache" to ("DXVK_STATE_CACHE" to "1"),
            "Disable DXVK State Cache" to ("DXVK_STATE_CACHE" to "0"),
            "Force WineD3D (OpenGL)" to ("PROTON_USE_WINED3D" to "1"),
            "Disable NVAPI" to ("DXVK_NVAPI_DISABLE" to "1"),
            "Hide NVIDIA GPU" to ("DXVK_HIDE_NVIDIA_GPU" to "1"),
            "Disable DXVK" to ("PROTON_NO_DXVK" to "1"),
            "Enable Esync" to ("WINEESYNC" to "1"),
            "Disable Esync" to ("WINEESYNC" to "0"),
            "Enable Fsync" to ("WINEFSYNC" to "1"),
            "Disable Fsync" to ("WINEFSYNC" to "0"),
            "Disable Vulkan" to ("PROTON_NO_VULKAN" to "1"),
            "Force Vulkan ICD Loader" to ("VK_ICD_FILENAMES" to "/usr/share/vulkan/icd.d"),
            "Disable Proton Runtime" to ("STEAM_RUNTIME" to "0"),
            "Force Large Address Aware" to ("WINE_LARGE_ADDRESS_AWARE" to "1"),
            "Disable Fullscreen Optimizations" to ("PROTON_NO_FSYNC" to "1"),
            "Use Native D3D Compiler" to ("PROTON_USE_D9VK" to "1"),
            "Disable Gamepad Support" to ("SDL_GAMECONTROLLER_IGNORE_DEVICES" to "1"),
            "Force Raw Mouse Input" to ("PROTON_FORCE_MOUSE_INPUT" to "1")
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
        
        singletonFlags.clear()
        for (i in 0 until flagsTableModel.rowCount) {
            val flag = flagsTableModel.getValueAt(i, 0)?.toString()?.trim() ?: ""
            if (flag.isNotEmpty()) {
                singletonFlags.add(flag)
            }
        }
        
        dispose()
    }
}