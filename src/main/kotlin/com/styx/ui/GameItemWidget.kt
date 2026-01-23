package com.styx.ui

import com.styx.models.Game
import com.styx.models.GameType
import com.styx.utils.Images
import com.styx.utils.formatTimePlayed
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Desktop
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Image
import java.awt.Insets
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths
import javax.imageio.ImageIO
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.SwingUtilities
import javax.swing.border.AbstractBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel
import kotlin.math.min

class RoundedBorder(private val color: Color, private val thickness: Int, private val radius: Int) : AbstractBorder() {
    override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
        val g2 = g.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.color = color
        g2.stroke = java.awt.BasicStroke(thickness.toFloat())
        g2.drawRoundRect(x, y, width - 1, height - 1, radius, radius)
        g2.dispose()
    }

    override fun getBorderInsets(c: Component): Insets {
        return Insets(thickness, thickness, thickness, thickness)
    }

    override fun isBorderOpaque(): Boolean = false
}

class GameItemWidget(
    private val game: Game,
    private val launcher: GameLauncher,
    private val onLaunch: (Game) -> Unit,
    private val onChangePrefix: (Game) -> Unit,
    private val onProtonManager: (Game) -> Unit,
    private val onPrefixManager: (Game) -> Unit,
    private val onLaunchOptions: (Game) -> Unit,
    private val onSaveGames: () -> Unit,
    private val isGamePlaying: (Game) -> Boolean,
    private val onRename: (Game) -> Unit,
    private val onConfigure: (Game) -> Unit
) : JPanel() {

    private val imageLabel = JLabel()

    init {
        initUI()
    }

    private fun initUI() {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        border = BorderFactory.createCompoundBorder(
            RoundedBorder(Color(48, 47, 47), 1, 15),
            EmptyBorder(5, 5, 5, 5)
        )
        background = Color(34, 35, 36)

        updateImage()
        imageLabel.preferredSize = Dimension(60, 60)
        imageLabel.minimumSize = Dimension(60, 60)
        imageLabel.maximumSize = Dimension(60, 60)
        imageLabel.border = EmptyBorder(0, 0, 0, 0)
        add(imageLabel)
        add(Box.createHorizontalStrut(12))

        val infoPanel = JPanel()
        infoPanel.layout = BoxLayout(infoPanel, BoxLayout.Y_AXIS)
        infoPanel.isOpaque = false

        val nameLabel = JLabel(game.name)
        nameLabel.font = nameLabel.font.deriveFont(Font.PLAIN, 14f)
        nameLabel.foreground = launcher.globalSettings.theme.getGameTitleColorObject()
        infoPanel.add(nameLabel)

        val typeOrPrefixLabel: JLabel = when (game.getGameType()) {
            GameType.NATIVE_LINUX -> JLabel("Type: Native Linux").apply {
                font = font.deriveFont(11f)
                foreground = launcher.globalSettings.theme.getMetadataLabelColorObject()
            }

            GameType.STEAM -> JLabel("Type: Steam").apply {
                font = font.deriveFont(11f)
                foreground = launcher.globalSettings.theme.getMetadataLabelColorObject()
            }

            GameType.WINDOWS -> {
                val number = Regex("/(\\d+)/").find(game.prefix)?.groupValues?.get(1)
                JLabel("Prefix: ${number}").apply {
                    font = font.deriveFont(11f)
                    foreground = Color.LIGHT_GRAY
                }
            }
        }
        infoPanel.add(typeOrPrefixLabel)

        val statusLabel = if (isGamePlaying(game)) {
            JLabel("▶ Playing").apply {
                foreground = Color(0, 255, 0)
                font = font.deriveFont(Font.BOLD, 11f)
            }
        } else {
            JLabel(formatTimePlayed(game.timePlayed)).apply {
                foreground = launcher.globalSettings.theme.getTimePlayedColorObject()
                font = font.deriveFont(11f)
            }
        }
        infoPanel.add(statusLabel)

        add(infoPanel)
        add(Box.createHorizontalGlue())

        if (game.getGameType() == GameType.WINDOWS) {
            val configureBtn = JButton("Configure").apply {
                preferredSize = Dimension(100, 28)
                maximumSize = Dimension(100, 28)
                toolTipText = "Configure game settings (params, winetricks, proton, prefix, logging)"
                addActionListener { onConfigure(game) }
            }
            add(configureBtn)
            add(Box.createHorizontalStrut(5))
        }

        val launchBtn = JButton("Launch").apply {
            preferredSize = Dimension(100, 28)
            maximumSize = Dimension(100, 28)
            font = font.deriveFont(Font.BOLD)
            addActionListener { onLaunch(game) }
        }
        launchBtn.background = Color.DARK_GRAY
        add(launchBtn)

        setupContextMenu()
        setupImageContextMenu()
    }

    private fun updateImage() {
        if (game.imagePath != null) {
            val imageFile = File(game.imagePath!!)
            if (imageFile.exists()) {
                try {
                    val bufferedImage = ImageIO.read(imageFile)
                    if (bufferedImage != null) {
                        val croppedImage = cropAndScaleImage(bufferedImage, 60, 60)
                        val roundedImage = createRoundedImage(croppedImage, 9)
                        imageLabel.icon = ImageIcon(roundedImage)
                    } else {
                        println("ImageIO.read returned null for: ${imageFile.absolutePath}")
                        imageLabel.icon = createPlaceholderIcon()
                    }
                } catch (e: Exception) {
                    println("Error loading image: ${e.message}")
                    e.printStackTrace()
                    imageLabel.icon = createPlaceholderIcon()
                }
            } else {
                println("Image file does not exist: ${imageFile.absolutePath}")
                imageLabel.icon = createPlaceholderIcon()
            }
        } else {
            imageLabel.icon = createPlaceholderIcon()
        }
    }

    private fun createPlaceholderIcon(): Icon {
        val placeholder = BufferedImage(60, 60, BufferedImage.TYPE_INT_ARGB)
        val g2d = placeholder.createGraphics()
        g2d.color = Color.GRAY
        g2d.fillRect(0, 0, 60, 60)
        g2d.color = Color.WHITE
        g2d.drawLine(0, 0, 60, 60)
        g2d.drawLine(60, 0, 0, 60)
        g2d.dispose()
        return ImageIcon(placeholder)
    }

    private fun cropAndScaleImage(image: BufferedImage, targetWidth: Int, targetHeight: Int): BufferedImage {
        val sourceWidth = image.width
        val sourceHeight = image.height
        val sourceAspect = sourceWidth.toDouble() / sourceHeight.toDouble()
        val targetAspect = targetWidth.toDouble() / targetHeight.toDouble()

        val cropWidth: Int
        val cropHeight: Int
        val cropX: Int
        val cropY: Int

        if (sourceAspect > targetAspect) {
            cropHeight = sourceHeight
            cropWidth = (sourceHeight * targetAspect).toInt()
            cropX = (sourceWidth - cropWidth) / 2
            cropY = 0
        } else {
            cropWidth = sourceWidth
            cropHeight = (sourceWidth / targetAspect).toInt()
            cropX = 0
            cropY = (sourceHeight - cropHeight) / 2
        }

        val croppedImage = image.getSubimage(cropX, cropY, cropWidth, cropHeight)
        val scaledImage = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB)
        val g2d = scaledImage.createGraphics()

        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2d.drawImage(croppedImage, 0, 0, targetWidth, targetHeight, null)
        g2d.dispose()

        return scaledImage
    }

    private fun createRoundedImage(image: BufferedImage, cornerRadius: Int): BufferedImage {
        val width = image.width
        val height = image.height
        val output = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g2 = output.createGraphics()

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g2.clip = java.awt.geom.RoundRectangle2D.Float(
            0f, 0f, width.toFloat(), height.toFloat(),
            cornerRadius.toFloat(), cornerRadius.toFloat()
        )
        g2.drawImage(image, 0, 0, width, height, null)
        g2.dispose()

        return output
    }

    private fun setupImageContextMenu() {
        val imagePopupMenu = JPopupMenu()

        val updateArtItem = JMenuItem("Update Art").apply {
            addActionListener {
                updateArtFromGameDirectory()
            }
        }

        val browseImageItem = JMenuItem("Browse for Image...").apply {
            addActionListener {
                browseForImage()
            }
        }

        imagePopupMenu.add(updateArtItem)
        imagePopupMenu.add(browseImageItem)

        imageLabel.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (e.isPopupTrigger) {
                    imagePopupMenu.show(e.component, e.x, e.y)
                }
            }

            override fun mouseReleased(e: MouseEvent) {
                if (e.isPopupTrigger) {
                    imagePopupMenu.show(e.component, e.x, e.y)
                }
            }
        })
    }

    private fun setupContextMenu() {
        val popupMenu = JPopupMenu()

        val statsItem = JMenuItem("Stats").apply {
            addActionListener {
                showStatsWindow()
            }
        }

        val troubleshootItem = JMenuItem("Troubleshoot").apply {
            addActionListener {
                showTroubleshootDialog()
            }
        }

        val renameItem = JMenuItem("Rename").apply {
            addActionListener {
                renameGame()
            }
        }

        val openGameLocationItem = JMenuItem("Open game location").apply {
            addActionListener {
                openGameLocation()
            }
        }

        popupMenu.add(statsItem)
        popupMenu.add(troubleshootItem)
        popupMenu.add(renameItem)
        popupMenu.addSeparator()
        popupMenu.add(openGameLocationItem)

        if (game.getGameType() == GameType.WINDOWS) {
            val openPrefixLocationItem = JMenuItem("Open wine prefix location").apply {
                addActionListener {
                    openPrefixLocation()
                }
            }
            popupMenu.add(openPrefixLocationItem)

            if (!game.steamAppId.isNullOrEmpty()) {
                val protonDbItem = JMenuItem("Proton DB").apply {
                    addActionListener {
                        openProtonDB()
                    }
                }
                popupMenu.add(protonDbItem)
            }
        }

        if (game.getGameType() == GameType.NATIVE_LINUX || game.getGameType() == GameType.STEAM) {
            popupMenu.addSeparator()
            val reconfigureItem = JMenuItem("Reconfigure").apply {
                addActionListener {
                    reconfigureGame()
                }
            }
            popupMenu.add(reconfigureItem)
        }

        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (e.isPopupTrigger) {
                    popupMenu.show(e.component, e.x, e.y)
                }
            }

            override fun mouseReleased(e: MouseEvent) {
                if (e.isPopupTrigger) {
                    popupMenu.show(e.component, e.x, e.y)
                }
            }
        })
    }

    private fun openGameLocation() {
        if (game.getGameType() == GameType.STEAM) {
            JOptionPane.showMessageDialog(
                this,
                "Steam App ID: ${game.executable}\nThis game is launched via Steam.",
                "Steam Game Info",
                JOptionPane.INFORMATION_MESSAGE
            )
            return
        }

        val gameFile = File(game.executable)
        val gameDirectory = gameFile.parentFile

        if (gameDirectory != null && gameDirectory.exists()) {
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(gameDirectory)
                } else {
                    ProcessBuilder("xdg-open", gameDirectory.absolutePath).start()
                }
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(
                    this,
                    "Failed to open game location: ${e.message}",
                    "Error",
                    JOptionPane.ERROR_MESSAGE
                )
            }
        } else {
            JOptionPane.showMessageDialog(
                this,
                "Game location not found: ${gameDirectory?.absolutePath ?: game.executable}",
                "Error",
                JOptionPane.ERROR_MESSAGE
            )
        }
    }

    private fun openPrefixLocation() {
        val prefixDirectory = File(game.prefix)

        if (prefixDirectory.exists()) {
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(prefixDirectory)
                } else {
                    ProcessBuilder("xdg-open", prefixDirectory.absolutePath).start()
                }
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(
                    this,
                    "Failed to open wine prefix location: ${e.message}",
                    "Error",
                    JOptionPane.ERROR_MESSAGE
                )
            }
        } else {
            JOptionPane.showMessageDialog(
                this,
                "Wine prefix not found: ${game.prefix}",
                "Error",
                JOptionPane.ERROR_MESSAGE
            )
        }
    }

    private fun renameGame() {
        val newName = JOptionPane.showInputDialog(
            this,
            "Enter new name for '${game.name}':",
            "Rename Game",
            JOptionPane.QUESTION_MESSAGE,
            null,
            null,
            game.name
        ) as? String

        if (newName != null && newName.isNotBlank() && newName != game.name) {
            game.name = newName
            onRename(game)
        }
    }

    private fun openProtonDB() {
        if (!game.steamAppId.isNullOrEmpty()) {
            try {
                val uri = URI("https://www.protondb.com/app/${game.steamAppId}")
                Desktop.getDesktop().browse(uri)
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(
                    this,
                    "Failed to open ProtonDB: ${e.message}",
                    "Error",
                    JOptionPane.ERROR_MESSAGE
                )
            }
        }
    }

    private fun reconfigureGame() {
        when (game.getGameType()) {
            GameType.NATIVE_LINUX -> {
                val fileChooser = JFileChooser()
                fileChooser.dialogTitle = "Select Native Linux Executable"
                fileChooser.fileSelectionMode = JFileChooser.FILES_ONLY
                fileChooser.currentDirectory = File(game.executable).parentFile

                if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                    val newExecutable = fileChooser.selectedFile.absolutePath
                    val field = game::class.java.getDeclaredField("executable")
                    field.isAccessible = true
                    field.set(game, newExecutable)
                    onSaveGames()
                    JOptionPane.showMessageDialog(
                        this,
                        "Native Linux executable updated to:\n$newExecutable",
                        "Reconfigured",
                        JOptionPane.INFORMATION_MESSAGE
                    )
                }
            }

            GameType.STEAM -> {
                val newSteamId = JOptionPane.showInputDialog(
                    this,
                    "Enter Steam App ID for '${game.name}':",
                    "Reconfigure Steam Game",
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    null,
                    game.executable
                ) as? String

                if (newSteamId != null && newSteamId.isNotBlank()) {
                    val field = game::class.java.getDeclaredField("executable")
                    field.isAccessible = true
                    field.set(game, newSteamId)
                    onSaveGames()
                    JOptionPane.showMessageDialog(
                        this,
                        "Steam App ID updated to: $newSteamId",
                        "Reconfigured",
                        JOptionPane.INFORMATION_MESSAGE
                    )
                }
            }

            GameType.WINDOWS -> {
                // Should NEVER happen.
            }

            null -> {

            }
        }
    }

    private fun showTroubleshootDialog() {
        val parent = SwingUtilities.getWindowAncestor(this) as? JFrame
        if (parent != null) {
            val troubleshootDialog = TroubleshootDialog(game, parent)
            troubleshootDialog.isVisible = true
        }
    }

    private fun showStatsWindow() {
        val statsWindow = JDialog(SwingUtilities.getWindowAncestor(this) as? JFrame, "Stats - ${game.name}", false)
        statsWindow.defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
        statsWindow.setSize(400, 400)
        statsWindow.minimumSize = Dimension(400, 400)
        statsWindow.setLocationRelativeTo(this)

        val mainPanel = JPanel(BorderLayout())
        mainPanel.border = EmptyBorder(20, 20, 20, 20)
        mainPanel.background = Color(34, 35, 36)

        val titleLabel = JLabel("Statistics for ${game.name}")
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 16f)
        titleLabel.border = EmptyBorder(0, 0, 20, 0)
        titleLabel.foreground = launcher.globalSettings.theme.getGameTitleColorObject()
        mainPanel.add(titleLabel, BorderLayout.NORTH)

        val tableData = arrayOf(
            arrayOf("Time Played", formatTimePlayed(game.timePlayed)),
            arrayOf("Times Opened", game.timesOpened.toString()),
            arrayOf("Times Crashed", game.timesCrashed.toString()),
            arrayOf("Wineprefix Path", game.prefix),
            arrayOf("Compatibility Layer", game.protonVersion ?: "Wine (default)"),
            arrayOf("Game Size", "Calculating..."),
            arrayOf("Wine Prefix Size", "Calculating...")
        )

        val columnNames = arrayOf("Statistic", "Value")
        val tableModel = DefaultTableModel(tableData, columnNames)
        val table = JTable(tableModel)
        table.font = table.font.deriveFont(14f)
        table.rowHeight = 25
        table.setShowGrid(true)
        table.gridColor = Color(60, 60, 60)
        table.setEnabled(false)
        table.background = Color(45, 45, 48)
        table.foreground = Color.WHITE

        val tableHeader = table.tableHeader
        tableHeader.background = Color(34, 35, 36)
        tableHeader.foreground = Color.WHITE
        tableHeader.font = tableHeader.font.deriveFont(Font.BOLD)

        table.setDefaultRenderer(Any::class.java, object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                table: JTable,
                value: Any?,
                isSelected: Boolean,
                hasFocus: Boolean,
                row: Int,
                column: Int
            ): Component {
                val cell = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
                cell.foreground = Color.WHITE
                if (row == 3) {
                    cell.background = Color(60, 63, 45)
                    cell.font = cell.font.deriveFont(Font.BOLD)
                } else if (row == 4) {
                    cell.background = Color(45, 50, 60)
                } else {
                    cell.background = Color(45, 45, 48)
                }
                return cell
            }
        })

        val scrollPane = JScrollPane(table)
        mainPanel.add(scrollPane, BorderLayout.CENTER)

        Thread {
            val gameSize = calculateDirectorySize(File(game.executable).parentFile)
            SwingUtilities.invokeLater {
                tableModel.setValueAt(formatFileSize(gameSize), 5, 1)
            }
        }.start()

        Thread {
            val prefixSize = calculateDirectorySize(File(game.prefix))
            SwingUtilities.invokeLater {
                tableModel.setValueAt(formatFileSize(prefixSize), 6, 1)
            }
        }.start()

        val buttonPanel = JPanel()
        val closeButton = JButton("Close")
        closeButton.addActionListener { statsWindow.dispose() }
        buttonPanel.add(closeButton)
        mainPanel.add(buttonPanel, BorderLayout.SOUTH)

        statsWindow.contentPane.add(mainPanel)
        statsWindow.isVisible = true
    }

    private fun calculateDirectorySize(directory: File?): Long {
        if (directory == null || !directory.exists()) return 0L

        var size = 0L
        try {
            Files.walk(directory.toPath()).use { stream ->
                stream.forEach { path ->
                    try {
                        if (Files.isRegularFile(path)) {
                            size += Files.size(path)
                        }
                    } catch (e: Exception) {
                        // Skip files that can't be accessed
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return size
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.2f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.2f MB".format(mb)
        val gb = mb / 1024.0
        return "%.2f GB".format(gb)
    }

    private fun updateArtFromGameDirectory() {
        val gameDir = File(game.executable).parentFile
        if (gameDir != null && gameDir.exists()) {
            val imageFiles = mutableListOf<File>()

            fun searchImages(dir: File) {
                dir.listFiles()?.forEach { file ->
                    if (file.isDirectory) {
                        searchImages(file)
                    } else if (file.extension.lowercase() in Images.imageExtensions) {
                        imageFiles.add(file)
                    }
                }
            }

            searchImages(gameDir)

            if (imageFiles.isNotEmpty()) {
                val firstImage = imageFiles.first()
                val copiedImagePath = copyImageToConfigDir(firstImage)
                if (copiedImagePath != null) {
                    game.imagePath = copiedImagePath
                    updateImage()
                    onSaveGames()
                    JOptionPane.showMessageDialog(
                        this,
                        "Updated art to: ${firstImage.name}",
                        "Art Updated",
                        JOptionPane.INFORMATION_MESSAGE
                    )
                } else {
                    JOptionPane.showMessageDialog(
                        this,
                        "Failed to copy image to config directory.",
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                    )
                }
            } else {
                JOptionPane.showMessageDialog(
                    this,
                    "No image files found in the game directory.",
                    "No Images Found",
                    JOptionPane.INFORMATION_MESSAGE
                )
            }
        } else {
            JOptionPane.showMessageDialog(
                this,
                "Game directory not found.",
                "Error",
                JOptionPane.ERROR_MESSAGE
            )
        }
    }

    private fun browseForImage() {
        val chooser = JFileChooser().apply {
            fileFilter = FileNameExtensionFilter(
                "Image Files", "jpg", "jpeg", "png", "gif", "bmp", "webp"
            )
        }

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            val selectedFile = chooser.selectedFile
            val copiedImagePath = copyImageToConfigDir(selectedFile)
            if (copiedImagePath != null) {
                game.imagePath = copiedImagePath
                updateImage()
                onSaveGames()
                JOptionPane.showMessageDialog(
                    this,
                    "Updated art to: ${selectedFile.name}",
                    "Art Updated",
                    JOptionPane.INFORMATION_MESSAGE
                )
            } else {
                JOptionPane.showMessageDialog(
                    this,
                    "Failed to copy image to config directory.",
                    "Error",
                    JOptionPane.ERROR_MESSAGE
                )
            }
        }
    }

    private fun copyImageToConfigDir(sourceFile: File): String? {
        try {
            val configDir = Paths.get(System.getProperty("user.home"), ".config", "styx").toFile()
            configDir.mkdirs()

            val artDir = File(configDir, "art")
            artDir.mkdirs()

            val gameSafeName = game.name.replace(Regex("[^a-zA-Z0-9.-]"), "_")
            val extension = sourceFile.extension
            val targetFileName = "${gameSafeName}_${
                sourceFile.nameWithoutExtension.substring(
                    0,
                    min(20, sourceFile.nameWithoutExtension.length)
                )
            }.$extension"
            val targetFile = File(artDir, targetFileName)

            sourceFile.copyTo(targetFile, overwrite = true)

            return targetFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}