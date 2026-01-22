package com.styx.ui

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.border.EmptyBorder

/**
 * The game output window.
 * This is used for debug output from the game.
 */
class GameOutputWindow(
    private val gameName: String,
    parent: JFrame? = null,
    private val onAbort: ((String) -> Unit)? = null,
    private val prefixPath: String? = null,
    private val useProton: Boolean = false,
    private val verboseLogging: Boolean = false,
    private val wineLogLevel: String = "warn+all,fixme-all"
) : JFrame("Game Output - $gameName") {
    private val outputText = JTextArea()
    private val isOutputDisabled = wineLogLevel == "-all"
    private val hideDebugCheckbox = JCheckBox("Hide Wine debug output (improves performance)", isOutputDisabled)
    private val abortBtn = JButton("Abort Launch")
    private val maxDisplayLines = 5000
    private val fullLogBuffer = mutableListOf<String>()
    private val displayBuffer = mutableListOf<String>()
    private var needsUIUpdate = false
    private var updateTimer: Timer? = null

    @Volatile
    private var isClosing = false

    init {
        initUI()
        startUIUpdateTimer()

        defaultCloseOperation = DISPOSE_ON_CLOSE
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) {
                cleanup()
            }
        })
    }

    private fun initUI() {
        minimumSize = Dimension(800, 600)

        val contentPanel = JPanel(BorderLayout(10, 10))
        contentPanel.border = EmptyBorder(10, 10, 10, 10)

        val titleLabel = JLabel("Output for: $gameName")
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 12f)

        val topPanel = JPanel(FlowLayout(FlowLayout.LEFT, 10, 5))
        topPanel.add(hideDebugCheckbox)
        hideDebugCheckbox.toolTipText = "When enabled, prevents output from being captured to improve game performance"
        hideDebugCheckbox.addActionListener {
            updateOutputDisplay()
        }
        contentPanel.add(topPanel, BorderLayout.NORTH)

        contentPanel.add(titleLabel, BorderLayout.NORTH)

        updateOutputDisplay()
        outputText.isEditable = false
        outputText.lineWrap = true
        outputText.wrapStyleWord = true
        outputText.font = Font("Monospaced", Font.PLAIN, 11)
        val scrollPane = JScrollPane(outputText)
        contentPanel.add(scrollPane, BorderLayout.CENTER)

        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0))

        abortBtn.apply {
            preferredSize = Dimension(120, 32)
            background = Color(0xCC, 0x00, 0x00)
            foreground = Color.WHITE
            font = font.deriveFont(Font.BOLD)
            addActionListener {
                onAbort?.invoke(gameName)
            }
        }
        buttonPanel.add(abortBtn)

        if (prefixPath != null) {
            val killServerBtn = JButton("Kill Wineserver").apply {
                preferredSize = Dimension(130, 32)
                toolTipText = "Kill wineserver for this prefix (fixes version mismatch)"
                addActionListener { killWineserver() }
            }
            buttonPanel.add(killServerBtn)
        }

        val saveBtn = JButton("Save Log").apply {
            preferredSize = Dimension(100, 32)
            addActionListener { saveLog() }
        }

        val clearBtn = JButton("Clear Output").apply {
            preferredSize = Dimension(100, 32)
            addActionListener { clearOutput() }
        }

        val closeBtn = JButton("Close").apply {
            preferredSize = Dimension(100, 32)
            addActionListener {
                cleanup()
                dispose()
            }
        }

        buttonPanel.add(saveBtn)
        buttonPanel.add(clearBtn)
        buttonPanel.add(closeBtn)
        contentPanel.add(buttonPanel, BorderLayout.SOUTH)

        contentPane = contentPanel
    }

    private fun startUIUpdateTimer() {
        updateTimer = Timer(200) {
            if (!isClosing) {
                updateUI()
            }
        }
        updateTimer?.start()
    }

    private fun updateOutputDisplay() {
        SwingUtilities.invokeLater {
            if (!isClosing) {
                if (hideDebugCheckbox.isSelected) {
                    if (isOutputDisabled) {
                        outputText.text =
                            "\n\n    (Tumbleweed)\n\n    Output logging is disabled (WINEDEBUG=-all).\n\n    To enable logs, change the Wine log level in the game configuration."
                    } else {
                        outputText.text =
                            "\n\n    All output is hidden for performance.\n\n    Uncheck the box above to view output."
                    }
                } else {
                    synchronized(displayBuffer) {
                        val linesToDisplay = displayBuffer.takeLast(maxDisplayLines)
                        val text = linesToDisplay.joinToString("\n")
                        outputText.text = text
                        if (text.isNotEmpty()) {
                            outputText.caretPosition = outputText.text.length
                        }
                    }
                }
            }
        }
    }

    private fun updateUI() {
        if (!needsUIUpdate || isClosing) return

        if (hideDebugCheckbox.isSelected) {
            needsUIUpdate = false
            return
        }

        synchronized(displayBuffer) {
            if (displayBuffer.isEmpty()) return

            val linesToDisplay = displayBuffer.takeLast(maxDisplayLines)
            val text = linesToDisplay.joinToString("\n")

            SwingUtilities.invokeLater {
                if (!isClosing) {
                    outputText.text = text
                    outputText.caretPosition = outputText.text.length
                }
            }

            needsUIUpdate = false
        }
    }

    fun appendOutput(text: String, color: String? = null) {
        if (isClosing || hideDebugCheckbox.isSelected) return

        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"))
        val logLine = "[$timestamp] $text"

        synchronized(fullLogBuffer) {
            fullLogBuffer.add(logLine)
        }

        synchronized(displayBuffer) {
            displayBuffer.add(logLine)
            if (displayBuffer.size > maxDisplayLines * 2) {
                val toKeep = displayBuffer.takeLast(maxDisplayLines)
                displayBuffer.clear()
                displayBuffer.addAll(toKeep)
            }
            needsUIUpdate = true
        }
    }

    fun clearOutput() {
        synchronized(fullLogBuffer) {
            fullLogBuffer.clear()
        }
        synchronized(displayBuffer) {
            displayBuffer.clear()
        }
        SwingUtilities.invokeLater {
            outputText.text = ""
        }
    }

    fun disableAbortButton() {
        SwingUtilities.invokeLater {
            abortBtn.isEnabled = false
        }
    }

    fun cleanup() {
        isClosing = true
        updateTimer?.stop()
        updateTimer = null
        synchronized(fullLogBuffer) {
            fullLogBuffer.clear()
        }
        synchronized(displayBuffer) {
            displayBuffer.clear()
        }
    }

    private fun saveLog() {
        val chooser = JFileChooser()
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        chooser.selectedFile = File("${gameName}_log_$timestamp.txt")

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                val fullLog = synchronized(fullLogBuffer) {
                    fullLogBuffer.joinToString("\n")
                }
                chooser.selectedFile.writeText(fullLog)
                JOptionPane.showMessageDialog(
                    this,
                    "Log saved to ${chooser.selectedFile.absolutePath}\nTotal lines: ${fullLogBuffer.size}",
                    "Success",
                    JOptionPane.INFORMATION_MESSAGE
                )
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(
                    this,
                    "Failed to save log: ${e.message}",
                    "Error",
                    JOptionPane.WARNING_MESSAGE
                )
            }
        }
    }

    private fun killWineserver() {
        if (prefixPath == null) return

        Thread {
            try {
                appendOutput("")
                appendOutput("═".repeat(60), "#0066cc")
                appendOutput("KILLING WINESERVER FOR PREFIX", "#0066cc")
                appendOutput("═".repeat(60), "#0066cc")
                appendOutput("Prefix: $prefixPath")
                appendOutput("")

                val pb = ProcessBuilder()
                val env = pb.environment()
                env["WINEPREFIX"] = prefixPath

                if (useProton) {
                    env["STEAM_COMPAT_DATA_PATH"] = prefixPath
                    appendOutput("Using Proton environment for wineserver", "#00aa00")
                }

                pb.command("wineserver", "-k")
                appendOutput("Command: wineserver -k")
                appendOutput("")

                val process = pb.start()
                val exitCode = process.waitFor()

                appendOutput("")
                if (exitCode == 0) {
                    appendOutput("Wineserver killed successfully", "#008800")
                    appendOutput("You can now launch the game", "#008800")
                } else {
                    appendOutput("Wineserver command exited with code: $exitCode", "#cc6600")
                    appendOutput("This may be normal if no wineserver was running", "#cc6600")
                }
                appendOutput("═".repeat(60), "#0066cc")
                appendOutput("")
            } catch (e: Exception) {
                appendOutput("ERROR killing wineserver: ${e.message}", "#cc0000")
                e.printStackTrace()
            }
        }.start()
    }

    fun isHidingOutput(): Boolean = hideDebugCheckbox.isSelected
}