package com.styx.ui

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.styx.workers.PrefixScanner
import com.styx.utils.formatTimePlayed
import com.styx.models.Game
import com.styx.models.GameType
import com.styx.models.PrefixInfo
import com.styx.models.GlobalSettings
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.dnd.DnDConstants
import java.awt.dnd.DragGestureEvent
import java.awt.dnd.DragGestureListener
import java.awt.dnd.DragSource
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import java.nio.file.Paths
import javax.imageio.ImageIO
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JScrollPane
import javax.swing.JTabbedPane
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.SwingWorker
import javax.swing.border.EmptyBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.math.min

class GameLauncher : JFrame("Styx") {
    private val games = mutableListOf<Game>()
    private val categories = mutableListOf<String>()
    private var availablePrefixes = listOf<PrefixInfo>()
    private val configFile = Paths.get(System.getProperty("user.home"), ".config", "styx", "games.json")
    private val categoriesFile = Paths.get(System.getProperty("user.home"), ".config", "styx", "categories.json")
    private val settingsFile = Paths.get(System.getProperty("user.home"), ".config", "styx", "settings.json")
    private val gameProcesses = mutableMapOf<String, Process>()
    private val outputWindows = mutableMapOf<String, GameOutputWindow>()
    private val tabbedPane = JTabbedPane()
    private val categoryPanels = mutableMapOf<String, JPanel>()
    private val statusLabel = JLabel("Ready")
    private val logReaderThreads = mutableListOf<Thread>()
    private val gameStartTimes = mutableMapOf<String, Long>()
    private val searchField = JTextField()
    var globalSettings = GlobalSettings()

    @Volatile
    private var isShuttingDown = false

    private val gson = Gson()

    init {
        initUI()
        loadCategories()
        loadSettings()
        loadGames()
        scanPrefixes()

        Runtime.getRuntime().addShutdownHook(Thread {
            cleanup()
        })
    }

    private fun initUI() {
        minimumSize = Dimension(900, 600)
        defaultCloseOperation = DO_NOTHING_ON_CLOSE

        try {
            val iconUrl = javaClass.getResource("/icon.jpg")
            if (iconUrl != null) {
                val iconImage = ImageIO.read(iconUrl)
                setIconImage(iconImage)
            }
        } catch (e: Exception) {
            System.err.println("Failed to load application icon: ${e.message}")
        }

        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) {
                handleApplicationClose()
            }
        })

        createMenuBar()

        val mainPanel = JPanel(BorderLayout(10, 10))
        mainPanel.border = EmptyBorder(10, 10, 10, 10)

        val topPanel = JPanel(BorderLayout(10, 5))
        val searchPanel = JPanel(BorderLayout(5, 0))
        searchField.preferredSize = Dimension(300, 30)
        searchField.toolTipText = "Search library..."
        searchPanel.add(searchField, BorderLayout.CENTER)

        searchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) {
                filterGames()
            }

            override fun removeUpdate(e: DocumentEvent?) {
                filterGames()
            }

            override fun changedUpdate(e: DocumentEvent?) {
                filterGames()
            }
        })

        topPanel.add(searchPanel, BorderLayout.SOUTH)
        mainPanel.add(topPanel, BorderLayout.NORTH)

        tabbedPane.border = BorderFactory.createTitledBorder("")
        mainPanel.add(tabbedPane, BorderLayout.CENTER)

        val fullBottomPanel = JPanel(BorderLayout())
        val buttonRowPanel = JPanel(BorderLayout())
        val leftButtonPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0))
        val removeBtn = JButton("Remove Game").apply {
            preferredSize = Dimension(120, 32)
            addActionListener { removeGame() }
        }

        val rescanBtn = JButton("Rescan Prefixes").apply {
            preferredSize = Dimension(150, 32)
            addActionListener { scanPrefixes() }
        }

        leftButtonPanel.add(removeBtn)
        leftButtonPanel.add(rescanBtn)

        val rightButtonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0))

        val addNativeBtn = JButton("Add Native").apply {
            preferredSize = Dimension(120, 32)
            toolTipText = "Add Native Linux Game"
            addActionListener { addNativeGame() }
        }

        val addSteamBtn = JButton("Add Steam").apply {
            preferredSize = Dimension(120, 32)
            toolTipText = "Add Steam Game"
            addActionListener { addSteamGame() }
        }

        val addBtn = JButton("Add Windows").apply {
            preferredSize = Dimension(140, 32)
            font = font.deriveFont(Font.PLAIN)
            toolTipText = "Add Windows Game (Wine/Proton)"
            addActionListener { addGame() }
        }

        rightButtonPanel.add(addNativeBtn)
        rightButtonPanel.add(addSteamBtn)
        rightButtonPanel.add(addBtn)

        buttonRowPanel.add(leftButtonPanel, BorderLayout.WEST)
        buttonRowPanel.add(rightButtonPanel, BorderLayout.EAST)

        fullBottomPanel.add(buttonRowPanel, BorderLayout.NORTH)

        val statusPanel = JPanel(BorderLayout())
        statusPanel.border = EmptyBorder(5, 10, 5, 10)
        statusPanel.add(statusLabel, BorderLayout.WEST)
        fullBottomPanel.add(statusPanel, BorderLayout.SOUTH)
        mainPanel.add(fullBottomPanel, BorderLayout.SOUTH)

        contentPane = mainPanel

        pack()
        setLocationRelativeTo(null)
    }

    private fun createMenuBar() {
        val menuBar = JMenuBar()
        menuBar.background = Color(34, 35, 36)
        val fileMenu = JMenu("File")

        val newCategoryItem = JMenuItem("New Category").apply {
            addActionListener { createNewCategory() }
            accelerator = KeyStroke.getKeyStroke("control N")
        }
        fileMenu.add(newCategoryItem)

        val deleteCategoryItem = JMenuItem("Delete Category").apply {
            addActionListener { deleteCurrentCategory() }
            accelerator = KeyStroke.getKeyStroke("control shift D")
        }
        fileMenu.add(deleteCategoryItem)
        fileMenu.addSeparator()

        val quitItem = JMenuItem("Quit").apply {
            addActionListener { handleApplicationClose() }
            accelerator = KeyStroke.getKeyStroke("control Q")
        }

        fileMenu.add(quitItem)

        val toolsMenu = JMenu("Tools")
        val settingsItem = JMenuItem("Settings").apply {
            addActionListener { openSettings() }
            accelerator = KeyStroke.getKeyStroke("control COMMA")
        }
        toolsMenu.add(settingsItem)
        toolsMenu.addSeparator()

        val wineserverMgmtItem = JMenuItem("Wineserver Management").apply {
            addActionListener { openWineserverManagement() }
            accelerator = KeyStroke.getKeyStroke("control K")
        }
        toolsMenu.add(wineserverMgmtItem)

        val helpMenu = JMenu("Help")
        val aboutItem = JMenuItem("About").apply {
            addActionListener { showAboutDialog() }
        }

        helpMenu.add(aboutItem)
        menuBar.add(fileMenu)
        menuBar.add(toolsMenu)
        menuBar.add(helpMenu)

        jMenuBar = menuBar
    }

    private fun openWineserverManagement() {
        val window = WineserverManagementWindow(this)
        window.isVisible = true
    }

    private fun showAboutDialog() {
        val message = """
            <html>
            <h2>Styx</h2>
            <hr />
            <p>v0.0.2</p>
            <hr />
            <br />
            <p>A game launcher for Linux</p>
            <br>
            <p>By Navid M</p>
            <br />
            <a href="https://github.com/navid-m/styx">github.com/navid-m/styx</a>
            </ul>
            </html>
        """.trimIndent()

        JOptionPane.showMessageDialog(
            this,
            message,
            "About Styx",
            JOptionPane.INFORMATION_MESSAGE
        )
    }

    private fun scanPrefixes() {
        statusLabel.text = "Scanning for Wine/Proton prefixes..."

        val scanner = PrefixScanner()
        scanner.execute()

        scanner.addPropertyChangeListener { evt ->
            if (evt.propertyName == "state" && evt.newValue == SwingWorker.StateValue.DONE) {
                availablePrefixes = scanner.get()
                statusLabel.text = "Found ${availablePrefixes.size} Wine/Proton prefix(es)"
            }
        }
    }

    private fun loadGames() {
        if (configFile.exists()) {
            try {
                val json = configFile.readText()
                val type = object : TypeToken<List<Game>>() {}.type
                games.clear()
                val loadedGames: List<Game> = gson.fromJson(json, type)
                games.addAll(loadedGames)
                refreshGamesList()
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(
                    this,
                    "Failed to load games: ${e.message}",
                    "Error",
                    JOptionPane.WARNING_MESSAGE
                )
            }
        }
    }

    private fun loadCategories() {
        if (categoriesFile.exists()) {
            try {
                val json = categoriesFile.readText()
                val type = object : TypeToken<List<String>>() {}.type
                categories.clear()
                val loadedCategories: List<String> = gson.fromJson(json, type)
                categories.addAll(loadedCategories)
            } catch (e: Exception) {
                System.err.println("Failed to load categories: ${e.message}")
            }
        }

        if (!categories.contains("All")) {
            categories.add(0, "All")
        }
    }

    private fun loadSettings() {
        if (settingsFile.exists()) {
            try {
                val json = settingsFile.readText()
                globalSettings = gson.fromJson(json, GlobalSettings::class.java) ?: GlobalSettings()
            } catch (e: Exception) {
                System.err.println("Failed to load settings: ${e.message}")
                globalSettings = GlobalSettings()
            }
        } else {
            globalSettings = GlobalSettings()
        }
    }

    fun saveSettings() {
        try {
            settingsFile.parent.createDirectories()
            val json = gson.toJson(globalSettings)
            settingsFile.writeText(json)
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(
                this,
                "Failed to save settings: ${e.message}",
                "Error",
                JOptionPane.WARNING_MESSAGE
            )
        }
    }

    private fun openSettings() {
        val dialog = SettingsDialog(this)
        dialog.isVisible = true
    }

    private fun saveCategories() {
        try {
            categoriesFile.parent.createDirectories()
            val json = gson.toJson(categories)
            categoriesFile.writeText(json)
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(
                this,
                "Failed to save categories: ${e.message}",
                "Error",
                JOptionPane.WARNING_MESSAGE
            )
        }
    }

    private fun createNewCategory() {
        val categoryName = JOptionPane.showInputDialog(
            this,
            "Enter category name:",
            "New Category",
            JOptionPane.PLAIN_MESSAGE
        )

        if (!categoryName.isNullOrBlank()) {
            val trimmedName = categoryName.trim()
            if (categories.contains(trimmedName)) {
                JOptionPane.showMessageDialog(
                    this,
                    "Category '$trimmedName' already exists.",
                    "Duplicate Category",
                    JOptionPane.WARNING_MESSAGE
                )
                return
            }

            categories.add(trimmedName)
            saveCategories()
            refreshGamesList()
            statusLabel.text = "Created category: $trimmedName"
        }
    }

    private fun deleteCurrentCategory() {
        val selectedIndex = tabbedPane.selectedIndex
        if (selectedIndex < 0) return

        val categoryName = tabbedPane.getTitleAt(selectedIndex)

        if (categoryName == "All") {
            JOptionPane.showMessageDialog(
                this,
                "Cannot delete the 'All' category.",
                "Cannot Delete",
                JOptionPane.WARNING_MESSAGE
            )
            return
        }

        val result = JOptionPane.showConfirmDialog(
            this,
            "Delete category '$categoryName'?\n\nGames in this category will be moved to 'All'.",
            "Confirm Delete",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        )

        if (result == JOptionPane.YES_OPTION) {
            games.filter { it.category == categoryName }.forEach { it.category = "All" }

            categories.remove(categoryName)
            saveCategories()
            saveGames()
            refreshGamesList()
            statusLabel.text = "Deleted category: $categoryName"
        }
    }

    private fun saveGames() {
        try {
            configFile.parent.createDirectories()
            val json = gson.toJson(games)
            configFile.writeText(json)
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(
                this,
                "Failed to save games: ${e.message}",
                "Error",
                JOptionPane.WARNING_MESSAGE
            )
        }
    }

    fun refreshGamesList() {
        refreshGamesList(null)
    }

    private fun refreshGamesList(filter: String?) {
        tabbedPane.removeAll()
        categoryPanels.clear()

        val searchFilter = filter?.trim()?.lowercase()

        categories.forEach { category ->
            val panel = JPanel()
            panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
            panel.border = EmptyBorder(10, 5, 5, 5)

            setupDragAndDrop(panel, category)

            val gamesInCategory = if (category == "All") {
                if (searchFilter.isNullOrBlank()) {
                    games
                } else {
                    games.filter { it.name.lowercase().contains(searchFilter) }
                }
            } else {
                if (searchFilter.isNullOrBlank()) {
                    games.filter { it.category == category }
                } else {
                    games.filter { it.category == category && it.name.lowercase().contains(searchFilter) }
                }
            }

            gamesInCategory.forEach { game ->
                val gameWidget = GameItemWidgetWithImage(
                    game,
                    this,
                    ::launchGame,
                    ::changeGamePrefix,
                    ::openProtonManager,
                    ::openPrefixManager,
                    ::openLaunchOptions,
                    ::saveGames,
                    ::isGamePlaying,
                    ::renameGame,
                    ::openGameConfig
                )
                gameWidget.maximumSize = Dimension(Int.MAX_VALUE, 70)
                makeDraggable(gameWidget, game)
                panel.add(gameWidget)
                panel.add(Box.createVerticalStrut(5))
            }

            panel.add(Box.createVerticalGlue())

            val scrollPane = JScrollPane(panel)
            scrollPane.verticalScrollBar.unitIncrement = 16
            scrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            scrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER

            tabbedPane.addTab(category, scrollPane)
            categoryPanels[category] = panel
        }

        tabbedPane.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (e.isPopupTrigger) {
                    showTabContextMenu(e)
                }
            }

            override fun mouseReleased(e: MouseEvent) {
                if (e.isPopupTrigger) {
                    showTabContextMenu(e)
                }
            }
        })

        setupTabDropTargets()

        tabbedPane.revalidate()
        tabbedPane.repaint()
    }

    private fun showTabContextMenu(e: MouseEvent) {
        val tabIndex = tabbedPane.indexAtLocation(e.x, e.y)
        if (tabIndex >= 0) {
            val categoryName = tabbedPane.getTitleAt(tabIndex)
            val popup = JPopupMenu()

            if (categoryName != "All") {
                val deleteItem = JMenuItem("Delete Category").apply {
                    addActionListener {
                        tabbedPane.selectedIndex = tabIndex
                        deleteCurrentCategory()
                    }
                }
                popup.add(deleteItem)
            }

            if (popup.componentCount > 0) {
                popup.show(e.component, e.x, e.y)
            }
        }
    }

    private fun setupTabDropTargets() {
        tabbedPane.dropTarget = DropTarget(tabbedPane, object : DropTargetAdapter() {
            private var draggedGameName: String? = null

            override fun dragEnter(dtde: DropTargetDragEvent) {
                dtde.acceptDrag(DnDConstants.ACTION_MOVE)
            }

            override fun dragOver(dtde: DropTargetDragEvent) {
                val location = dtde.location
                val tabIndex = tabbedPane.indexAtLocation(location.x, location.y)

                if (tabIndex >= 0 && tabIndex != tabbedPane.selectedIndex) {
                    tabbedPane.selectedIndex = tabIndex
                }

                dtde.acceptDrag(DnDConstants.ACTION_MOVE)
            }

            override fun drop(dtde: DropTargetDropEvent) {
                try {
                    val location = dtde.location
                    val tabIndex = tabbedPane.indexAtLocation(location.x, location.y)

                    if (tabIndex >= 0) {
                        val categoryName = tabbedPane.getTitleAt(tabIndex)

                        dtde.acceptDrop(DnDConstants.ACTION_MOVE)
                        val transferable = dtde.transferable
                        val gameName = transferable.getTransferData(DataFlavor.stringFlavor) as String

                        val game = games.find { it.name == gameName }
                        if (game != null && categoryName != "All" && game.category != categoryName) {
                            game.category = categoryName
                            saveGames()
                            refreshGamesList()
                            tabbedPane.selectedIndex = tabIndex
                            statusLabel.text = "Moved ${game.name} to $categoryName"
                            dtde.dropComplete(true)
                            return
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                dtde.dropComplete(false)
            }
        })
    }

    private fun setupDragAndDrop(panel: JPanel, category: String) {
        panel.dropTarget = DropTarget(panel, object : DropTargetAdapter() {
            override fun drop(dtde: DropTargetDropEvent) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_MOVE)
                    val transferable = dtde.transferable
                    val gameName = transferable.getTransferData(DataFlavor.stringFlavor) as String

                    val game = games.find { it.name == gameName }
                    if (game != null && category != "All" && game.category != category) {
                        game.category = category
                        saveGames()
                        refreshGamesList()
                        statusLabel.text = "Moved ${game.name} to $category"
                    }

                    dtde.dropComplete(true)
                } catch (e: Exception) {
                    e.printStackTrace()
                    dtde.dropComplete(false)
                }
            }
        })
    }

    private fun makeDraggable(widget: JPanel, game: Game) {
        val dragSource = DragSource.getDefaultDragSource()
        dragSource.createDefaultDragGestureRecognizer(
            widget,
            DnDConstants.ACTION_MOVE,
            object : DragGestureListener {
                override fun dragGestureRecognized(dge: DragGestureEvent) {
                    val transferable = object : Transferable {
                        override fun getTransferDataFlavors(): Array<DataFlavor> {
                            return arrayOf(DataFlavor.stringFlavor)
                        }

                        override fun isDataFlavorSupported(flavor: DataFlavor): Boolean {
                            return flavor == DataFlavor.stringFlavor
                        }

                        override fun getTransferData(flavor: DataFlavor): Any {
                            if (flavor == DataFlavor.stringFlavor) {
                                return game.name
                            }
                            throw UnsupportedFlavorException(flavor)
                        }
                    }

                    dge.startDrag(DragSource.DefaultMoveDrop, transferable)
                }
            }
        )
    }

    private fun renameGame(game: Game) {
        saveGames()
        refreshGamesList()
    }

    private fun filterGames() {
        val searchText = searchField.text
        refreshGamesList(searchText)
    }

    private fun isGamePlaying(game: Game): Boolean {
        return gameProcesses.containsKey(game.name)
    }

    private fun addGame() {
        val dialog = AddGameDialog(availablePrefixes, this)
        dialog.isVisible = true

        dialog.gameData?.let { newGame ->
            autoSetGameImage(newGame)
            games.add(newGame)
            saveGames()
            refreshGamesList()
            statusLabel.text = "Added ${newGame.name}"
        }
    }

    private fun addNativeGame() {
        val dialog = AddNativeGameDialog(this)
        dialog.isVisible = true

        dialog.gameData?.let { newGame ->
            autoSetGameImage(newGame)
            games.add(newGame)
            saveGames()
            refreshGamesList()
            statusLabel.text = "Added native Linux game: ${newGame.name}"
        }
    }

    private fun addSteamGame() {
        val dialog = AddSteamGameDialog(this)
        dialog.isVisible = true

        dialog.gameData?.let { newGame ->
            autoSetGameImage(newGame)
            games.add(newGame)
            saveGames()
            refreshGamesList()
            statusLabel.text = "Added Steam game: ${newGame.name}"
        }
    }

    private fun autoSetGameImage(game: Game) {
        if (game.getGameType() == GameType.STEAM) {
            return
        }

        val gameDir = File(game.executable).parentFile
        if (gameDir != null && gameDir.exists()) {
            val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
            val imageFiles = mutableListOf<File>()

            fun searchImages(dir: File) {
                dir.listFiles()?.forEach { file ->
                    if (file.isDirectory) {
                        searchImages(file)
                    } else if (file.extension.lowercase() in imageExtensions) {
                        imageFiles.add(file)
                    }
                }
            }

            searchImages(gameDir)

            if (imageFiles.isNotEmpty()) {
                val firstImage = imageFiles.first()
                val copiedImagePath = copyImageToConfigDir(game, firstImage)
                if (copiedImagePath != null) {
                    game.imagePath = copiedImagePath
                }
            }
        }
    }

    private fun copyImageToConfigDir(game: Game, sourceFile: File): String? {
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

    private fun removeGame() {
        if (games.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No games in the library.", "No Games", JOptionPane.INFORMATION_MESSAGE)
            return
        }

        val gameNames = games.map { it.name }.toTypedArray()
        val selected = JOptionPane.showInputDialog(
            this,
            "Select game to remove:",
            "Remove Game",
            JOptionPane.QUESTION_MESSAGE,
            null,
            gameNames,
            gameNames[0]
        ) as? String

        if (selected != null) {
            val game = games.find { it.name == selected }
            if (game != null) {
                val result = JOptionPane.showConfirmDialog(
                    this,
                    "Remove '${game.name}' from the library?",
                    "Confirm Removal",
                    JOptionPane.YES_NO_OPTION
                )

                if (result == JOptionPane.YES_OPTION) {
                    games.remove(game)
                    saveGames()
                    refreshGamesList()
                    statusLabel.text = "Removed ${game.name}"
                }
            }
        }
    }

    private fun changeGamePrefix(game: Game) {
        val dialog = ChangePrefixDialog(game, availablePrefixes, this)
        dialog.isVisible = true

        dialog.newPrefix?.let { newPrefix ->
            val gameInList = games.find { it.name == game.name }
            if (gameInList != null) {
                val oldPrefix = gameInList.prefix
                gameInList.prefix = newPrefix
                saveGames()
                refreshGamesList()
                statusLabel.text =
                    "Changed prefix for ${game.name} from ${Paths.get(oldPrefix).fileName} to ${Paths.get(newPrefix).fileName}"
            }
        }
    }

    private fun openProtonManager(game: Game) {
        val dialog = ProtonManagerDialog(game, this)
        dialog.isVisible = true

        val gameInList = games.find { it.name == game.name }
        if (gameInList != null) {
            if (dialog.selectedProton == null) {
                gameInList.protonVersion = null
                gameInList.protonPath = null
                gameInList.protonBin = null
                statusLabel.text = "Set ${game.name} to use Wine (default)"
            } else {
                val proton = dialog.selectedProton!!
                gameInList.protonVersion = proton.name
                gameInList.protonPath = proton.path
                gameInList.protonBin = proton.protonBin
                statusLabel.text = "Set ${game.name} to use ${proton.name}"
            }
            saveGames()
            refreshGamesList()
        }
    }

    private fun openGameConfig(game: Game) {
        val dialog = GameConfigDialog(
            game,
            this,
            ::openLaunchOptions,
            ::openPrefixManager,
            ::openProtonManager,
            ::changeGamePrefix
        )
        dialog.isVisible = true
        saveGames()
        refreshGamesList()
    }

    private fun openPrefixManager(game: Game) {
        val prefixPath = game.prefix

        if (!File(prefixPath).exists()) {
            JOptionPane.showMessageDialog(
                this,
                "Wine prefix not found: $prefixPath",
                "Error",
                JOptionPane.ERROR_MESSAGE
            )
            return
        }

        Thread {
            try {
                val whichProcess = ProcessBuilder("which", "winetricks").start()
                whichProcess.waitFor()

                if (whichProcess.exitValue() != 0) {
                    SwingUtilities.invokeLater {
                        JOptionPane.showMessageDialog(
                            this,
                            "Winetricks is not installed or not found in PATH.\n\n" +
                                    "Install winetricks to use this feature:\n" +
                                    "• Ubuntu/Debian: sudo apt install winetricks\n" +
                                    "• Arch: sudo pacman -S winetricks\n" +
                                    "• Fedora: sudo dnf install winetricks",
                            "Winetricks Not Found",
                            JOptionPane.WARNING_MESSAGE
                        )
                    }
                    return@Thread
                }

                SwingUtilities.invokeLater {
                    statusLabel.text = "Launching Winetricks for ${game.name}..."
                }

                val processBuilder = ProcessBuilder()
                processBuilder.environment()["WINEPREFIX"] = prefixPath
                processBuilder.command("winetricks")

                val process = processBuilder.start()
                val exitCode = process.waitFor()

                SwingUtilities.invokeLater {
                    if (exitCode == 0) {
                        statusLabel.text = "Winetricks closed for ${game.name}"
                    } else {
                        statusLabel.text = "Winetricks exited with code $exitCode for ${game.name}"
                    }
                }

            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    JOptionPane.showMessageDialog(
                        this,
                        "Failed to launch Winetricks: ${e.message}",
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                    )
                    statusLabel.text = "Failed to launch Winetricks"
                }
            }
        }.start()
    }

    private fun openLaunchOptions(game: Game) {
        val gameInList = games.find { it.name == game.name }

        if (gameInList != null) {
            val dialog = LaunchOptionsDialog(gameInList, this)
            dialog.isVisible = true

            gameInList.launchOptions.clear()
            gameInList.launchOptions.putAll(dialog.launchOptions)

            saveGames()
            statusLabel.text = "Updated launch options for ${game.name}"
        }
    }

    private fun checkWineAvailability(outputWindow: GameOutputWindow): Boolean {
        try {
            outputWindow.appendOutput("=== Pre-flight checks ===", "#0066cc")

            val whichProcess = ProcessBuilder("which", "wine").start()
            whichProcess.waitFor()

            if (whichProcess.exitValue() == 0) {
                val winePath = whichProcess.inputStream.bufferedReader().readText().trim()
                outputWindow.appendOutput("Wine found at: $winePath", "#008800")
            } else {
                outputWindow.appendOutput("WARNING: 'wine' command not found in PATH", "#cc6600")
                return false
            }

            val versionProcess = ProcessBuilder("wine", "--version").start()
            versionProcess.waitFor()

            if (versionProcess.exitValue() == 0) {
                val version = versionProcess.inputStream.bufferedReader().readText().trim()
                outputWindow.appendOutput("Wine version: $version", "#008800")
            } else {
                val error = versionProcess.errorStream.bufferedReader().readText().trim()
                outputWindow.appendOutput("Could not get Wine version: $error", "#cc6600")
            }

            outputWindow.appendOutput("")
            return true

        } catch (e: Exception) {
            outputWindow.appendOutput("ERROR checking Wine: ${e.message}", "#cc0000")
            outputWindow.appendOutput("")
            return false
        }
    }

    private fun launchNativeLinuxGame(game: Game) {
        val exePath = game.executable
        val gameName = game.name

        if (!File(exePath).exists()) {
            JOptionPane.showMessageDialog(this, "Executable not found: $exePath", "Error", JOptionPane.ERROR_MESSAGE)
            return
        }

        val outputWindow = GameOutputWindow(
            gameName = gameName,
            parent = this,
            onAbort = { abortGameName -> abortGameLaunch(abortGameName) },
            verboseLogging = game.verboseLogging
        )
        outputWindows[gameName] = outputWindow
        outputWindow.isVisible = true

        outputWindow.appendOutput("═".repeat(60), "#0066cc")
        outputWindow.appendOutput("LAUNCHING NATIVE LINUX GAME: $gameName", "#0066cc")
        outputWindow.appendOutput("═".repeat(60), "#0066cc")
        outputWindow.appendOutput("")

        outputWindow.appendOutput("=== Launch Configuration ===", "#0066cc")
        outputWindow.appendOutput("Executable: $exePath")
        outputWindow.appendOutput("Working Directory: ${File(exePath).parent}")
        outputWindow.appendOutput("Type: Native Linux Binary", "#00aa00")
        outputWindow.appendOutput("")

        try {
            val processBuilder = ProcessBuilder()
            processBuilder.directory(File(exePath).parentFile)
            processBuilder.command(File(exePath).absolutePath)

            outputWindow.appendOutput("=== Starting Native Process ===", "#0066cc")
            outputWindow.appendOutput("Command: ${File(exePath).absolutePath}")
            outputWindow.appendOutput("")

            val process = processBuilder.start()
            gameProcesses[gameName] = process

            game.timesOpened++
            val startTime = System.currentTimeMillis()

            Thread {
                val stdout = process.inputStream.bufferedReader()
                val stderr = process.errorStream.bufferedReader()

                Thread {
                    try {
                        stdout.lines().forEach { line ->
                            outputWindow.appendOutput(line)
                        }
                    } catch (e: Exception) {
                        // Stream closed. Ignore.
                    }
                }.start()

                Thread {
                    try {
                        stderr.lines().forEach { line ->
                            outputWindow.appendOutput(line, "#cc6600")
                        }
                    } catch (e: Exception) {
                        // Stream closed. Ignore.
                    }
                }.start()

                val exitCode = process.waitFor()
                val endTime = System.currentTimeMillis()
                val duration = endTime - startTime
                game.timePlayed += duration

                gameProcesses.remove(gameName)

                SwingUtilities.invokeLater {
                    outputWindow.appendOutput("")
                    outputWindow.appendOutput("═".repeat(60), "#0066cc")
                    if (exitCode == 0) {
                        outputWindow.appendOutput("GAME EXITED NORMALLY", "#00aa00")
                    } else {
                        outputWindow.appendOutput("GAME CRASHED (Exit code: $exitCode)", "#cc0000")
                        game.timesCrashed++
                    }
                    outputWindow.appendOutput("Session Duration: ${formatTimePlayed(duration)}", "#0066cc")
                    outputWindow.appendOutput("═".repeat(60), "#0066cc")
                    saveGames()
                    refreshGamesList()
                }
            }.start()

            statusLabel.text = "Launched ${game.name}"
            refreshGamesList()

        } catch (e: Exception) {
            outputWindow.appendOutput("ERROR: ${e.message}", "#cc0000")
            e.printStackTrace()
            game.timesCrashed++
            gameProcesses.remove(gameName)
            saveGames()
            refreshGamesList()
        }
    }

    private fun launchSteamGame(game: Game) {
        val steamAppId = game.executable
        val gameName = game.name

        val outputWindow = GameOutputWindow(
            gameName = gameName,
            parent = this,
            onAbort = { abortGameName -> abortGameLaunch(abortGameName) },
            verboseLogging = game.verboseLogging
        )
        outputWindows[gameName] = outputWindow
        outputWindow.isVisible = true

        outputWindow.appendOutput("═".repeat(60), "#0066cc")
        outputWindow.appendOutput("LAUNCHING STEAM GAME: $gameName", "#0066cc")
        outputWindow.appendOutput("═".repeat(60), "#0066cc")
        outputWindow.appendOutput("")

        outputWindow.appendOutput("=== Launch Configuration ===", "#0066cc")
        outputWindow.appendOutput("Steam App ID: $steamAppId")
        outputWindow.appendOutput("Type: Steam Game", "#00aa00")
        outputWindow.appendOutput("")

        try {
            val steamUrl = "steam://rungameid/$steamAppId"
            outputWindow.appendOutput("=== Starting Steam ===", "#0066cc")
            outputWindow.appendOutput("Opening: $steamUrl")
            outputWindow.appendOutput("")

            val processBuilder = ProcessBuilder("xdg-open", steamUrl)
            val process = processBuilder.start()

            game.timesOpened++

            Thread {
                process.waitFor()

                SwingUtilities.invokeLater {
                    outputWindow.appendOutput("")
                    outputWindow.appendOutput("═".repeat(60), "#0066cc")
                    outputWindow.appendOutput("Steam launcher opened successfully", "#00aa00")
                    outputWindow.appendOutput("Note: Game will launch through Steam client", "#0066cc")
                    outputWindow.appendOutput("═".repeat(60), "#0066cc")
                    saveGames()
                }
            }.start()

            statusLabel.text = "Launched ${game.name} via Steam"
            refreshGamesList()

        } catch (e: Exception) {
            outputWindow.appendOutput("ERROR: ${e.message}", "#cc0000")
            e.printStackTrace()
            game.timesCrashed++
            saveGames()
            refreshGamesList()
        }
    }

    private fun launchGame(game: Game) {
        when (game.getGameType()) {
            GameType.NATIVE_LINUX -> {
                launchNativeLinuxGame(game)
                return
            }

            GameType.STEAM -> {
                launchSteamGame(game)
                return
            }

            GameType.WINDOWS -> {
                // Continue with Wine/Proton launch...
            }

            null -> {

            }
        }

        val exePath = game.executable
        val prefixPath = game.prefix
        val gameName = game.name

        if (!File(exePath).exists()) {
            JOptionPane.showMessageDialog(this, "Executable not found: $exePath", "Error", JOptionPane.ERROR_MESSAGE)
            return
        }

        if (!File(prefixPath).exists()) {
            JOptionPane.showMessageDialog(
                this,
                "Wine prefix not found: $prefixPath",
                "Error",
                JOptionPane.ERROR_MESSAGE
            )
            return
        }

        val outputWindow = GameOutputWindow(
            gameName = gameName,
            parent = this,
            onAbort = { abortGameName -> abortGameLaunch(abortGameName) },
            prefixPath = prefixPath,
            useProton = game.protonBin != null,
            verboseLogging = game.verboseLogging
        )
        outputWindows[gameName] = outputWindow
        outputWindow.isVisible = true

        outputWindow.appendOutput("═".repeat(60), "#0066cc")
        outputWindow.appendOutput("LAUNCHING: $gameName", "#0066cc")
        outputWindow.appendOutput("═".repeat(60), "#0066cc")
        outputWindow.appendOutput("")

        if (!checkWineAvailability(outputWindow)) {
            outputWindow.appendOutput("Pre-flight checks failed. Aborting launch.", "#cc0000")
            return
        }

        outputWindow.appendOutput("=== Launch Configuration ===", "#0066cc")
        outputWindow.appendOutput("Executable: $exePath")
        outputWindow.appendOutput("Working Directory: ${File(exePath).parent}")
        outputWindow.appendOutput("Wine Prefix: $prefixPath")

        val useProton = game.protonBin != null
        if (useProton) {
            val protonVersion = game.protonVersion ?: "Unknown"
            outputWindow.appendOutput("Compatibility Layer: $protonVersion", "#00aa00")
        } else {
            outputWindow.appendOutput("Compatibility Layer: Wine (default)", "#00aa00")
        }

        if (File(prefixPath).exists()) {
            val systemReg = File(prefixPath, "system.reg")
            val userReg = File(prefixPath, "user.reg")
            if (systemReg.exists() && userReg.exists()) {
                outputWindow.appendOutput("Wine prefix validation: OK", "#008800")
            } else {
                outputWindow.appendOutput("WARNING: Wine prefix may be incomplete or corrupted", "#cc6600")
            }
        }

        outputWindow.appendOutput("")

        try {
            val processBuilder = ProcessBuilder()
            val env = processBuilder.environment()
            env["WINEPREFIX"] = prefixPath

            if (useProton) {
                env["STEAM_COMPAT_DATA_PATH"] = prefixPath
                env["STEAM_COMPAT_CLIENT_INSTALL_PATH"] = game.protonPath ?: ""
            }

            globalSettings.globalFlags.forEach { (key, value) ->
                env[key] = value
            }

            game.launchOptions.forEach { (key, value) ->
                env[key] = value
            }

            if (game.verboseLogging) {
                env["WINEDEBUG"] = "+all"
                outputWindow.appendOutput("Verbose mode enabled: WINEDEBUG=+all", "#0066cc")
            } else {
                env["WINEDEBUG"] = "warn+all,fixme-all"
                outputWindow.appendOutput("Debug mode: WINEDEBUG=warn+all,fixme-all", "#0066cc")
            }

            env["WINEDLLOVERRIDES"] = "winemenubuilder.exe=d"
            env["DISPLAY"] = ":0"

            outputWindow.appendOutput("")
            outputWindow.appendOutput("=== Environment Variables ===", "#0066cc")
            env.filter {
                it.key.startsWith("WINE") ||
                        it.key == "DISPLAY" ||
                        it.key.startsWith("STEAM_COMPAT") ||
                        it.key.startsWith("DXVK") ||
                        it.key.startsWith("PROTON") ||
                        it.key == "MANGOHUD" || game.launchOptions.containsKey(it.key)
            }.forEach { (key, value) ->
                outputWindow.appendOutput("  $key=$value")
            }
            if (game.launchOptions.isNotEmpty()) {
                outputWindow.appendOutput(
                    "Custom launch options: ${game.launchOptions.size ?: 0} variable(s)",
                    "#00aa00"
                )
            }
            outputWindow.appendOutput("")

            outputWindow.appendOutput("=== Cleaning up old wineserver ===", "#0066cc")
            try {
                val killServerPB = ProcessBuilder()
                val killEnv = killServerPB.environment()
                killEnv["WINEPREFIX"] = prefixPath
                if (useProton) {
                    killEnv["STEAM_COMPAT_DATA_PATH"] = prefixPath
                }
                killServerPB.command("wineserver", "-k")
                val killProcess = killServerPB.start()
                killProcess.waitFor()
                outputWindow.appendOutput("Terminated any existing wineserver for this prefix", "#008800")
            } catch (e: Exception) {
                outputWindow.appendOutput("Note: wineserver cleanup skipped (${e.message})", "#cc6600")
            }
            outputWindow.appendOutput("")

            processBuilder.directory(File(exePath).parentFile)

            if (useProton) {
                val protonBin = game.protonBin!!
                processBuilder.command(protonBin, "run", File(exePath).absolutePath)

                outputWindow.appendOutput("=== Starting Proton Process ===", "#0066cc")
                outputWindow.appendOutput("Command: $protonBin run ${File(exePath).absolutePath}")
                outputWindow.appendOutput("")
            } else {
                processBuilder.command("wine", File(exePath).absolutePath)

                outputWindow.appendOutput("=== Starting Wine Process ===", "#0066cc")
                outputWindow.appendOutput("Command: wine ${File(exePath).absolutePath}")
                outputWindow.appendOutput("")
            }

            val process = processBuilder.start()
            gameProcesses[gameName] = process

            Thread.sleep(100)

            if (!process.isAlive) {
                outputWindow.appendOutput("ERROR: Process failed to start", "#cc0000")
                outputWindow.appendOutput("The Wine process terminated immediately", "#cc0000")
                gameProcesses.remove(gameName)
                statusLabel.text = "Launch failed"
                return
            }

            val pid = process.pid()
            outputWindow.appendOutput("")
            outputWindow.appendOutput("Process started successfully (PID: $pid).", "#008800")
            outputWindow.appendOutput("─".repeat(60))
            outputWindow.appendOutput("")

            gameStartTimes[gameName] = System.currentTimeMillis()

            games.find { it.name == gameName }?.let { game ->
                game.timesOpened++
                saveGames()
            }

            statusLabel.text = "$gameName is running (PID: $pid)"
            refreshGamesList()

            val stdoutThread = Thread {
                try {
                    process.inputStream.bufferedReader().use { reader ->
                        while (!isShuttingDown && process.isAlive) {
                            val line = reader.readLine() ?: break
                            if (line.isNotBlank()) {
                                SwingUtilities.invokeLater {
                                    if (!isShuttingDown) {
                                        outputWindow.appendOutput("[OUT] ${line.trim()}")
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (!isShuttingDown) {
                        SwingUtilities.invokeLater {
                            outputWindow.appendOutput("[ERROR reading stdout: ${e.message}]", "#cc0000")
                        }
                    }
                }
            }.apply {
                isDaemon = true
                name = "stdout-reader-$gameName"
            }
            stdoutThread.start()
            logReaderThreads.add(stdoutThread)

            val stderrThread = Thread {
                try {
                    process.errorStream.bufferedReader().use { reader ->
                        while (!isShuttingDown && process.isAlive) {
                            val line = reader.readLine() ?: break
                            if (line.isNotBlank()) {
                                SwingUtilities.invokeLater {
                                    if (!isShuttingDown) {
                                        val trimmed = line.trim()
                                        when {
                                            "err:" in trimmed.lowercase() || "error" in trimmed.lowercase() ->
                                                outputWindow.appendOutput("[ERR] $trimmed", "#cc0000")

                                            "warn:" in trimmed.lowercase() || "warning" in trimmed.lowercase() ->
                                                outputWindow.appendOutput("[WARN] $trimmed", "#cc6600")

                                            "fixme:" in trimmed.lowercase() -> {
                                                if (game.verboseLogging) {
                                                    outputWindow.appendOutput("[FIXME] $trimmed", "#666666")
                                                }
                                            }

                                            else -> outputWindow.appendOutput("[ERR] $trimmed")
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (!isShuttingDown) {
                        SwingUtilities.invokeLater {
                            outputWindow.appendOutput("[ERROR reading stderr: ${e.message}]", "#cc0000")
                        }
                    }
                }
            }.apply {
                isDaemon = true
                name = "stderr-reader-$gameName"
            }
            stderrThread.start()
            logReaderThreads.add(stderrThread)
            val monitorThread = Thread {
                val exitCode = process.waitFor()
                val startTime = gameStartTimes[gameName]

                if (!isShuttingDown) {
                    SwingUtilities.invokeLater {
                        if (startTime != null) {
                            val endTime = System.currentTimeMillis()
                            val sessionMinutes = (endTime - startTime) / 1000 / 60

                            games.find { it.name == gameName }?.let { game ->
                                game.timePlayed += sessionMinutes
                                if (exitCode != 0) {
                                    game.timesCrashed++
                                }
                                saveGames()
                            }

                            gameStartTimes.remove(gameName)
                        }

                        outputWindow.appendOutput("")
                        outputWindow.appendOutput("═".repeat(60), "#0066cc")
                        outputWindow.appendOutput("PROCESS FINISHED", "#0066cc")
                        outputWindow.appendOutput("═".repeat(60), "#0066cc")

                        val color = if (exitCode == 0) "#008800" else "#cc0000"
                        outputWindow.appendOutput("Exit Code: $exitCode", color)

                        if (exitCode != 0) {
                            outputWindow.appendOutput(
                                "Non-zero exit code indicates the game may have crashed or encountered an error.",
                                "#cc6600"
                            )
                        }

                        outputWindow.disableAbortButton()
                        gameProcesses.remove(gameName)
                        logReaderThreads.removeAll { !it.isAlive }
                        statusLabel.text = "$gameName exited (code: $exitCode)"
                        refreshGamesList()
                    }
                }
            }.apply {
                isDaemon = true
                name = "process-monitor-$gameName"
            }
            monitorThread.start()
            logReaderThreads.add(monitorThread)

        } catch (e: Exception) {
            outputWindow.appendOutput("", "#cc0000")
            outputWindow.appendOutput("CRITICAL ERROR: ${e.message}", "#cc0000")
            outputWindow.appendOutput("Exception type: ${e.javaClass.simpleName}", "#cc0000")
            outputWindow.appendOutput("")
            outputWindow.appendOutput("Traceback:", "#cc0000")
            e.stackTrace.take(10).forEach { element ->
                outputWindow.appendOutput("  at $element", "#cc0000")
            }

            outputWindow.disableAbortButton()
            JOptionPane.showMessageDialog(
                this,
                "Failed to launch game: ${e.message}",
                "Launch Error",
                JOptionPane.ERROR_MESSAGE
            )
            statusLabel.text = "Launch failed"
        }
    }

    private fun abortGameLaunch(gameName: String) {
        val process = gameProcesses[gameName]
        if (process != null && process.isAlive) {
            val result = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to abort the launch of '$gameName'?",
                "Abort Launch",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
            )

            if (result == JOptionPane.YES_OPTION) {
                val outputWindow = outputWindows[gameName]
                outputWindow?.appendOutput("")
                outputWindow?.appendOutput("═".repeat(60), "#cc6600")
                outputWindow?.appendOutput("ABORTING LAUNCH", "#cc6600")
                outputWindow?.appendOutput("═".repeat(60), "#cc6600")
                outputWindow?.appendOutput("User requested abort. Terminating process...", "#cc6600")

                process.destroy()
                Thread.sleep(1000)

                if (process.isAlive) {
                    process.destroyForcibly()
                }

                outputWindow?.appendOutput("Process terminated.", "#cc0000")
                outputWindow?.disableAbortButton()

                val startTime = gameStartTimes[gameName]
                if (startTime != null) {
                    val endTime = System.currentTimeMillis()
                    val sessionMinutes = (endTime - startTime) / 1000 / 60

                    games.find { it.name == gameName }?.let { game ->
                        game.timePlayed += sessionMinutes
                        saveGames()
                    }

                    gameStartTimes.remove(gameName)
                }

                gameProcesses.remove(gameName)
                logReaderThreads.removeAll { !it.isAlive }
                statusLabel.text = "Aborted launch of $gameName"
                refreshGamesList()
            }
        } else {
            JOptionPane.showMessageDialog(
                this,
                "No active process found for '$gameName'.",
                "Info",
                JOptionPane.INFORMATION_MESSAGE
            )
        }
    }

    private fun handleApplicationClose() {
        if (gameProcesses.isNotEmpty()) {
            val result = JOptionPane.showConfirmDialog(
                this,
                "There are ${gameProcesses.size} game(s) still running.\nDo you want to terminate them and exit?",
                "Confirm Exit",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            )

            if (result != JOptionPane.YES_OPTION) {
                return
            }
        }

        cleanup()
        dispose()
        System.exit(0)
    }

    private fun cleanup() {
        isShuttingDown = true

        outputWindows.values.forEach { window ->
            try {
                window.cleanup()
                window.dispose()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        outputWindows.clear()

        gameProcesses.values.forEach { process ->
            try {
                if (process.isAlive) {
                    process.destroy()
                    Thread.sleep(500)
                    if (process.isAlive) {
                        process.destroyForcibly()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        gameProcesses.clear()

        logReaderThreads.forEach { thread ->
            try {
                if (thread.isAlive) {
                    thread.interrupt()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        logReaderThreads.clear()
    }
}