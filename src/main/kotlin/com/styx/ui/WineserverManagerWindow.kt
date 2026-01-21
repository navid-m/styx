package com.styx.ui

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.border.EmptyBorder
import javax.swing.table.DefaultTableModel

/**
 * Wineserver Management Window
 * Displays running wineserver processes and allows killing them
 */
class WineserverManagerWindow(parent: JFrame? = null) : JFrame("Wineserver Management") {
    private val tableModel = DefaultTableModel(arrayOf("PID", "Command", "User"), 0)
    private val table = JTable(tableModel)
    private var refreshTimer: Timer? = null

    init {
        defaultCloseOperation = DISPOSE_ON_CLOSE
        setSize(700, 450)
        setLocationRelativeTo(parent)

        val mainPanel = JPanel(BorderLayout(10, 10))
        mainPanel.border = EmptyBorder(10, 10, 10, 10)

        val titleLabel = JLabel("Running Wineserver Processes")
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 14f)
        mainPanel.add(titleLabel, BorderLayout.NORTH)

        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
        table.fillsViewportHeight = true
        val scrollPane = JScrollPane(table)
        mainPanel.add(scrollPane, BorderLayout.CENTER)

        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0))

        val refreshBtn = JButton("Refresh").apply {
            preferredSize = Dimension(100, 32)
            addActionListener { refreshProcessList() }
        }

        val killSelectedBtn = JButton("Kill Selected").apply {
            preferredSize = Dimension(120, 32)
            addActionListener { killSelectedProcesses() }
        }

        val killAllBtn = JButton("Kill All Wineservers").apply {
            preferredSize = Dimension(170, 32)
            background = Color(0xCC, 0x00, 0x00)
            foreground = Color.WHITE
            font = font.deriveFont(Font.BOLD)
            addActionListener { killAllWineservers() }
        }

        val closeBtn = JButton("Close").apply {
            preferredSize = Dimension(80, 32)
            addActionListener { dispose() }
        }

        buttonPanel.add(refreshBtn)
        buttonPanel.add(killSelectedBtn)
        buttonPanel.add(killAllBtn)
        buttonPanel.add(closeBtn)
        mainPanel.add(buttonPanel, BorderLayout.SOUTH)

        contentPane = mainPanel

        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) {
                stopAutoRefresh()
            }
        })

        startAutoRefresh()
        refreshProcessList()
    }

    private fun startAutoRefresh() {
        refreshTimer = Timer(2000) {
            refreshProcessList()
        }
        refreshTimer?.start()
    }

    private fun stopAutoRefresh() {
        refreshTimer?.stop()
        refreshTimer = null
    }

    private fun refreshProcessList() {
        Thread {
            try {
                val pb = ProcessBuilder("bash", "-c", "ps aux | grep '[w]ineserver' || true")
                val process = pb.start()
                val output = process.inputStream.bufferedReader().readText()
                process.waitFor()

                val processes = mutableListOf<Array<String>>()
                output.lines().forEach { line ->
                    if (line.isNotBlank()) {
                        val parts = line.trim().split(Regex("\\s+"), limit = 11)
                        if (parts.size >= 2) {
                            val user = parts[0]
                            val pid = parts[1]
                            val command = parts.drop(10).joinToString(" ")
                            processes.add(arrayOf(pid, command, user))
                        }
                    }
                }

                SwingUtilities.invokeLater {
                    tableModel.rowCount = 0
                    processes.forEach { tableModel.addRow(it) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun killSelectedProcesses() {
        val selectedRows = table.selectedRows
        if (selectedRows.isEmpty()) {
            JOptionPane.showMessageDialog(
                this,
                "No processes selected",
                "Info",
                JOptionPane.INFORMATION_MESSAGE
            )
            return
        }

        val pids = selectedRows.map { tableModel.getValueAt(it, 0).toString() }
        val result = JOptionPane.showConfirmDialog(
            this,
            "Kill ${pids.size} selected wineserver process(es)?\n\nPIDs: ${pids.joinToString(", ")}",
            "Confirm Kill",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        )

        if (result == JOptionPane.YES_OPTION) {
            Thread {
                try {
                    pids.forEach { pid ->
                        ProcessBuilder("kill", "-9", pid).start().waitFor()
                    }
                    SwingUtilities.invokeLater {
                        refreshProcessList()
                        JOptionPane.showMessageDialog(
                            this,
                            "Selected processes terminated",
                            "Success",
                            JOptionPane.INFORMATION_MESSAGE
                        )
                    }
                } catch (e: Exception) {
                    SwingUtilities.invokeLater {
                        JOptionPane.showMessageDialog(
                            this,
                            "Failed to kill processes: ${e.message}",
                            "Error",
                            JOptionPane.ERROR_MESSAGE
                        )
                    }
                }
            }.start()
        }
    }

    private fun killAllWineservers() {
        val result = JOptionPane.showConfirmDialog(
            this,
            "This will terminate ALL running wineserver processes on your system.\n\n" +
                    "This is useful to fix version mismatches but will affect:\n" +
                    "• All Wine/Proton games currently running\n" +
                    "• Any Wine applications in use\n\n" +
                    "Continue?",
            "Warning: Kill All Wineservers",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        )

        if (result == JOptionPane.YES_OPTION) {
            Thread {
                try {
                    val pb = ProcessBuilder("bash", "-c", "pgrep -x wineserver | xargs -r kill -9")
                    val process = pb.start()
                    process.waitFor()

                    SwingUtilities.invokeLater {
                        refreshProcessList()
                        JOptionPane.showMessageDialog(
                            this,
                            "All wineserver processes have been terminated.\n" +
                                    "You can now launch games with a fresh Wine environment.",
                            "Success",
                            JOptionPane.INFORMATION_MESSAGE
                        )
                    }
                } catch (e: Exception) {
                    SwingUtilities.invokeLater {
                        JOptionPane.showMessageDialog(
                            this,
                            "Failed to kill wineservers: ${e.message}",
                            "Error",
                            JOptionPane.ERROR_MESSAGE
                        )
                    }
                }
            }.start()
        }
    }
}