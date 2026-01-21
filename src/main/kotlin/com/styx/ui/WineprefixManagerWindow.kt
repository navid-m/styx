package com.styx.ui

import java.awt.BorderLayout
import java.awt.Desktop
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class WineprefixManagerWindow(private val parent: GameLauncher) : JFrame("Wineprefix Manager") {
    private val tableModel = object : DefaultTableModel(arrayOf("Name", "Path", "Size on Disk"), 0) {
        override fun isCellEditable(row: Int, column: Int) = false
    }
    private val table = JTable(tableModel)
    private val warningPrefsFile = Paths.get(System.getProperty("user.home"), ".config", "styx", "warnings.json")
    private var warningPrefs = WarningPreferences()

    data class WarningPreferences(
        var showDeleteWarning: Boolean = true,
        var showRenameWarning: Boolean = true
    )

    init {
        loadWarningPreferences()
        initUI()
        loadPrefixes()
    }

    private fun loadWarningPreferences() {
        if (warningPrefsFile.exists()) {
            try {
                val json = warningPrefsFile.readText()
                warningPrefs = com.google.gson.Gson().fromJson(json, WarningPreferences::class.java)
            } catch (e: Exception) {
                // Ignore.
            }
        }
    }

    private fun saveWarningPreferences() {
        try {
            warningPrefsFile.parent.toFile().mkdirs()
            val json = com.google.gson.Gson().toJson(warningPrefs)
            warningPrefsFile.writeText(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun initUI() {
        defaultCloseOperation = DISPOSE_ON_CLOSE
        minimumSize = Dimension(800, 500)
        setLocationRelativeTo(parent)

        val mainPanel = JPanel(BorderLayout(10, 10))
        mainPanel.border = EmptyBorder(15, 15, 15, 15)

        val titleLabel = JLabel("Wineprefix Manager")
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 16f)
        mainPanel.add(titleLabel, BorderLayout.NORTH)

        table.fillsViewportHeight = true
        table.rowHeight = 25
        
        val sizeRenderer = object : DefaultTableCellRenderer() {
            override fun setValue(value: Any?) {
                text = when (value) {
                    is String -> value
                    else -> ""
                }
                horizontalAlignment = SwingConstants.RIGHT
            }
        }
        table.columnModel.getColumn(2).cellRenderer = sizeRenderer
        table.columnModel.getColumn(0).preferredWidth = 150
        table.columnModel.getColumn(1).preferredWidth = 400
        table.columnModel.getColumn(2).preferredWidth = 120

        val scrollPane = JScrollPane(table)
        mainPanel.add(scrollPane, BorderLayout.CENTER)

        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 10, 0))
        
        val createBtn = JButton("Create New Prefix").apply {
            preferredSize = Dimension(150, 32)
            addActionListener { createNewPrefix() }
        }
        buttonPanel.add(createBtn)

        val renameBtn = JButton("Rename").apply {
            preferredSize = Dimension(100, 32)
            addActionListener { renameSelectedPrefix() }
        }
        buttonPanel.add(renameBtn)

        val deleteBtn = JButton("Delete").apply {
            preferredSize = Dimension(100, 32)
            addActionListener { deleteSelectedPrefix() }
        }
        buttonPanel.add(deleteBtn)

        val openBtn = JButton("Open in File Manager").apply {
            preferredSize = Dimension(170, 32)
            addActionListener { openSelectedPrefix() }
        }
        buttonPanel.add(openBtn)

        val refreshBtn = JButton("Refresh").apply {
            preferredSize = Dimension(100, 32)
            addActionListener { loadPrefixes() }
        }
        buttonPanel.add(refreshBtn)

        mainPanel.add(buttonPanel, BorderLayout.SOUTH)

        contentPane = mainPanel
        pack()
    }

    private fun loadPrefixes() {
        tableModel.rowCount = 0
        
        object : SwingWorker<List<TemporaryPrefixData>, Void>() {
            override fun doInBackground(): List<TemporaryPrefixData> {
                val prefixes = mutableListOf<TemporaryPrefixData>()
                val homePath = System.getProperty("user.home")
                
                val defaultWinePrefix = File(homePath, ".wine")
                if (defaultWinePrefix.exists()) {
                    prefixes.add(TemporaryPrefixData(
                        "Default (~/.wine)",
                        defaultWinePrefix.absolutePath,
                        calculateDirectorySize(defaultWinePrefix)
                    ))
                }
                
                val commonLocations = listOf(
                    "$homePath/.steam/steam/steamapps/compatdata",
                    "$homePath/.local/share/Steam/steamapps/compatdata",
                    "/usr/share/Steam/steamapps/compatdata"
                )
                
                for (compatdataPathStr in commonLocations) {
                    val compatDataPath = File(compatdataPathStr)
                    if (compatDataPath.exists() && compatDataPath.isDirectory) {
                        compatDataPath.listFiles()?.forEach { appDir ->
                            if (appDir.isDirectory) {
                                val pfxDir = File(appDir, "pfx")
                                if (pfxDir.exists() && pfxDir.isDirectory) {
                                    prefixes.add(TemporaryPrefixData(
                                        "Proton: ${appDir.name}",
                                        pfxDir.absolutePath,
                                        calculateDirectorySize(pfxDir)
                                    ))
                                }
                            }
                        }
                    }
                }

                return prefixes
            }

            override fun done() {
                try {
                    val prefixes = get()
                    prefixes.forEach { prefix ->
                        tableModel.addRow(arrayOf(prefix.name, prefix.path, formatSize(prefix.size)))
                    }
                } catch (e: Exception) {
                    JOptionPane.showMessageDialog(
                        this@WineprefixManagerWindow,
                        "Error loading prefixes: ${e.message}",
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                    )
                }
            }
        }.execute()
    }

    private fun calculateDirectorySize(directory: File): Long {
        var size = 0L
        try {
            Files.walk(directory.toPath()).use { stream ->
                size = stream
                    .filter { Files.isRegularFile(it) }
                    .mapToLong { 
                        try { Files.size(it) } catch (e: Exception) { 0L }
                    }
                    .sum()
            }
        } catch (e: Exception) {
            // Ignore.
        }
        return size
    }

    private fun formatSize(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        
        return when {
            gb >= 1.0 -> String.format("%.2f GB", gb)
            mb >= 1.0 -> String.format("%.2f MB", mb)
            kb >= 1.0 -> String.format("%.2f KB", kb)
            else -> "$bytes bytes"
        }
    }

    private fun createNewPrefix() {
        val nameInput = JTextField(20)
        val panel = JPanel(FlowLayout(FlowLayout.LEFT))
        panel.add(JLabel("Prefix Name:"))
        panel.add(nameInput)

        val result = JOptionPane.showConfirmDialog(
            this,
            panel,
            "Create New Wineprefix",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE
        )

        if (result == JOptionPane.OK_OPTION) {
            val name = nameInput.text.trim()
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(
                    this,
                    "Please enter a prefix name.",
                    "Invalid Input",
                    JOptionPane.WARNING_MESSAGE
                )
                return
            }

            val compatDataPath = File(System.getProperty("user.home"), ".local/share/Steam/steamapps/compatdata")
            compatDataPath.mkdirs()
            val newPrefix = File(compatDataPath, name)

            if (newPrefix.exists()) {
                JOptionPane.showMessageDialog(
                    this,
                    "A prefix with this name already exists.",
                    "Duplicate Name",
                    JOptionPane.WARNING_MESSAGE
                )
                return
            }

            val progressDialog = JDialog(this, "Creating Prefix...", true)
            val progressLabel = JLabel("Initializing wineprefix, please wait...")
            progressLabel.border = EmptyBorder(20, 20, 20, 20)
            progressDialog.add(progressLabel)
            progressDialog.pack()
            progressDialog.setLocationRelativeTo(this)

            object : SwingWorker<Boolean, Void>() {
                override fun doInBackground(): Boolean {
                    return try {
                        newPrefix.mkdirs()
                        val pb = ProcessBuilder("wineboot", "--init")
                        pb.environment()["WINEPREFIX"] = newPrefix.absolutePath
                        pb.environment()["WINEDEBUG"] = "-all"
                        val process = pb.start()
                        process.waitFor()
                        process.exitValue() == 0
                    } catch (e: Exception) {
                        e.printStackTrace()
                        false
                    }
                }

                override fun done() {
                    progressDialog.dispose()
                    try {
                        if (get()) {
                            JOptionPane.showMessageDialog(
                                this@WineprefixManagerWindow,
                                "Wineprefix created successfully at:\n${newPrefix.absolutePath}",
                                "Success",
                                JOptionPane.INFORMATION_MESSAGE
                            )
                            loadPrefixes()
                        } else {
                            JOptionPane.showMessageDialog(
                                this@WineprefixManagerWindow,
                                "Failed to create wineprefix.\nMake sure Wine is installed.",
                                "Error",
                                JOptionPane.ERROR_MESSAGE
                            )
                        }
                    } catch (e: Exception) {
                        JOptionPane.showMessageDialog(
                            this@WineprefixManagerWindow,
                            "Error: ${e.message}",
                            "Error",
                            JOptionPane.ERROR_MESSAGE
                        )
                    }
                }
            }.execute()

            SwingUtilities.invokeLater {
                progressDialog.isVisible = true
            }
        }
    }

    private fun renameSelectedPrefix() {
        val selectedRow = table.selectedRow
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(
                this,
                "Please select a prefix to rename.",
                "No Selection",
                JOptionPane.WARNING_MESSAGE
            )
            return
        }

        val currentName = tableModel.getValueAt(selectedRow, 0) as String
        val currentPath = tableModel.getValueAt(selectedRow, 1) as String

        if (currentName == "Default (~/.wine)") {
            JOptionPane.showMessageDialog(
                this,
                "Cannot rename the default wineprefix.",
                "Invalid Operation",
                JOptionPane.WARNING_MESSAGE
            )
            return
        }

        if (currentName.startsWith("Proton:")) {
            JOptionPane.showMessageDialog(
                this,
                "Cannot rename Proton prefixes.\nThese are managed by Steam.",
                "Invalid Operation",
                JOptionPane.WARNING_MESSAGE
            )
            return
        }

        if (warningPrefs.showRenameWarning) {
            val dontShowAgainCheckbox = JCheckBox("Don't show this warning again")
            val message = JPanel(BorderLayout())
            message.add(JLabel("<html><b>Warning:</b> Renaming a wineprefix may break games<br>that reference this prefix.<br><br>Continue?</html>"), BorderLayout.CENTER)
            message.add(dontShowAgainCheckbox, BorderLayout.SOUTH)

            val result = JOptionPane.showConfirmDialog(
                this,
                message,
                "Rename Warning",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            )

            if (dontShowAgainCheckbox.isSelected) {
                warningPrefs.showRenameWarning = false
                saveWarningPreferences()
            }

            if (result != JOptionPane.YES_OPTION) {
                return
            }
        }

        val newNameInput = JTextField(currentName, 20)
        val panel = JPanel(FlowLayout(FlowLayout.LEFT))
        panel.add(JLabel("New Name:"))
        panel.add(newNameInput)

        val result = JOptionPane.showConfirmDialog(
            this,
            panel,
            "Rename Wineprefix",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE
        )

        if (result == JOptionPane.OK_OPTION) {
            val newName = newNameInput.text.trim()
            if (newName.isEmpty() || newName == currentName) {
                return
            }

            val oldDir = File(currentPath)
            val newDir = File(oldDir.parent, newName)

            if (newDir.exists()) {
                JOptionPane.showMessageDialog(
                    this,
                    "A prefix with this name already exists.",
                    "Duplicate Name",
                    JOptionPane.WARNING_MESSAGE
                )
                return
            }

            if (oldDir.renameTo(newDir)) {
                JOptionPane.showMessageDialog(
                    this,
                    "Prefix renamed successfully.",
                    "Success",
                    JOptionPane.INFORMATION_MESSAGE
                )
                loadPrefixes()
            } else {
                JOptionPane.showMessageDialog(
                    this,
                    "Failed to rename prefix.",
                    "Error",
                    JOptionPane.ERROR_MESSAGE
                )
            }
        }
    }

    private fun deleteSelectedPrefix() {
        val selectedRow = table.selectedRow
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(
                this,
                "Please select a prefix to delete.",
                "No Selection",
                JOptionPane.WARNING_MESSAGE
            )
            return
        }

        val name = tableModel.getValueAt(selectedRow, 0) as String
        val path = tableModel.getValueAt(selectedRow, 1) as String

        if (name == "Default (~/.wine)") {
            JOptionPane.showMessageDialog(
                this,
                "Cannot delete the default wineprefix.\nPlease delete it manually if needed.",
                "Invalid Operation",
                JOptionPane.WARNING_MESSAGE
            )
            return
        }

        if (name.startsWith("Proton:")) {
            JOptionPane.showMessageDialog(
                this,
                "Cannot delete Proton prefixes.\nThese are managed by Steam and should be cleaned through Steam's disk cleanup tools.",
                "Invalid Operation",
                JOptionPane.WARNING_MESSAGE
            )
            return
        }

        if (warningPrefs.showDeleteWarning) {
            val dontShowAgainCheckbox = JCheckBox("Don't show this warning again")
            val message = JPanel(BorderLayout(5, 5))
            message.add(JLabel("<html><b>Warning:</b> This will permanently delete the wineprefix:<br><br>$path<br><br>This action cannot be undone. Continue?</html>"), BorderLayout.CENTER)
            message.add(dontShowAgainCheckbox, BorderLayout.SOUTH)

            val result = JOptionPane.showConfirmDialog(
                this,
                message,
                "Delete Warning",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            )

            if (dontShowAgainCheckbox.isSelected) {
                warningPrefs.showDeleteWarning = false
                saveWarningPreferences()
            }

            if (result != JOptionPane.YES_OPTION) {
                return
            }
        }

        val dir = File(path)
        val success = dir.deleteRecursively()

        if (success) {
            JOptionPane.showMessageDialog(
                this,
                "Wineprefix deleted successfully.",
                "Success",
                JOptionPane.INFORMATION_MESSAGE
            )
            loadPrefixes()
        } else {
            JOptionPane.showMessageDialog(
                this,
                "Failed to delete wineprefix.",
                "Error",
                JOptionPane.ERROR_MESSAGE
            )
        }
    }

    private fun openSelectedPrefix() {
        val selectedRow = table.selectedRow
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(
                this,
                "Please select a prefix to open.",
                "No Selection",
                JOptionPane.WARNING_MESSAGE
            )
            return
        }

        val path = tableModel.getValueAt(selectedRow, 1) as String
        val dir = File(path)

        if (!dir.exists()) {
            JOptionPane.showMessageDialog(
                this,
                "Directory does not exist: $path",
                "Error",
                JOptionPane.ERROR_MESSAGE
            )
            return
        }

        try {
            Desktop.getDesktop().open(dir)
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(
                this,
                "Failed to open directory: ${e.message}",
                "Error",
                JOptionPane.ERROR_MESSAGE
            )
        }
    }

    data class TemporaryPrefixData(val name: String, val path: String, val size: Long)
}
