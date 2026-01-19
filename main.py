import sys
import os
import json
import subprocess
from pathlib import Path
from typing import List, Dict, Optional
from datetime import datetime
from PySide6.QtWidgets import (
    QApplication,
    QMainWindow,
    QWidget,
    QVBoxLayout,
    QHBoxLayout,
    QPushButton,
    QLabel,
    QFileDialog,
    QComboBox,
    QMessageBox,
    QDialog,
    QLineEdit,
    QGroupBox,
    QTextEdit,
    QScrollArea,
    QFrame,
    QCheckBox,
)
from PySide6.QtCore import Qt, QThread, Signal, QProcess, QTimer
from PySide6.QtGui import QFont, QColor, QTextCursor


class GameOutputWindow(QMainWindow):
    """Window for displaying game launch output and debug information"""

    abort_requested = Signal(str)

    def __init__(self, game_name: str, parent=None):
        super().__init__(parent)
        self.game_name = game_name
        self.init_ui()

    def init_ui(self):
        self.setWindowTitle(f"Game Output - {self.game_name}")
        self.setMinimumSize(800, 600)

        central_widget = QWidget()
        self.setCentralWidget(central_widget)
        layout = QVBoxLayout(central_widget)
        layout.setContentsMargins(10, 10, 10, 10)
        layout.setSpacing(10)

        title_label = QLabel(f"Output for: {self.game_name}")
        title_font = QFont()
        title_font.setPointSize(12)
        title_font.setBold(True)
        title_label.setFont(title_font)
        layout.addWidget(title_label)

        self.verbose_checkbox = QCheckBox("Verbose Mode (show all Wine debug)")
        self.verbose_checkbox.setChecked(False)
        layout.addWidget(self.verbose_checkbox)

        self.output_text = QTextEdit()
        self.output_text.setReadOnly(True)
        self.output_text.setFont(QFont("Monospace", 9))
        layout.addWidget(self.output_text)

        button_layout = QHBoxLayout()
        button_layout.addStretch()

        self.abort_btn = QPushButton("Abort Launch")
        self.abort_btn.setMinimumHeight(32)
        self.abort_btn.setMinimumWidth(100)
        self.abort_btn.setStyleSheet(
            "QPushButton { background-color: #cc0000; color: white; font-weight: bold; }"
        )
        self.abort_btn.clicked.connect(self.abort_launch)
        button_layout.addWidget(self.abort_btn)

        save_btn = QPushButton("Save Log")
        save_btn.setMinimumHeight(32)
        save_btn.setMinimumWidth(100)
        save_btn.clicked.connect(self.save_log)
        button_layout.addWidget(save_btn)

        clear_btn = QPushButton("Clear Output")
        clear_btn.setMinimumHeight(32)
        clear_btn.setMinimumWidth(100)
        clear_btn.clicked.connect(self.clear_output)
        button_layout.addWidget(clear_btn)

        close_btn = QPushButton("Close")
        close_btn.setMinimumHeight(32)
        close_btn.setMinimumWidth(100)
        close_btn.clicked.connect(self.close)
        button_layout.addWidget(close_btn)

        layout.addLayout(button_layout)

    def append_output(self, text: str, color: Optional[str] = None):
        """Append text to the output window with optional color"""
        timestamp = datetime.now().strftime("%H:%M:%S.%f")[:-3]

        if color:
            formatted_text = (
                f'<span style="color: {color};">[{timestamp}] {text}</span>'
            )
            self.output_text.append(formatted_text)
        else:
            self.output_text.append(f"[{timestamp}] {text}")

        cursor = self.output_text.textCursor()
        cursor.movePosition(QTextCursor.MoveOperation.End)
        self.output_text.setTextCursor(cursor)

    def clear_output(self):
        """Clear all output"""
        self.output_text.clear()

    def abort_launch(self):
        """Request abortion of the game launch"""
        self.abort_requested.emit(self.game_name)

    def save_log(self):
        """Save the output log to a file"""
        file_path, _ = QFileDialog.getSaveFileName(
            self,
            "Save Log File",
            f"{self.game_name}_log_{datetime.now().strftime('%Y%m%d_%H%M%S')}.txt",
            "Text Files (*.txt);;All Files (*)",
        )
        if file_path:
            try:
                with open(file_path, "w") as f:
                    f.write(self.output_text.toPlainText())
                QMessageBox.information(self, "Success", f"Log saved to {file_path}")
            except Exception as e:
                QMessageBox.warning(self, "Error", f"Failed to save log: {str(e)}")


class PrefixScanner(QThread):
    """Background thread for scanning Wine/Proton prefixes"""

    prefixes_found = Signal(list)

    def run(self):
        prefixes = self.scan_wine_prefixes()
        self.prefixes_found.emit(prefixes)

    def scan_wine_prefixes(self) -> List[Dict[str, str]]:
        """Scan for Steam Library directories and find Proton prefixes"""
        prefixes = []
        seen_paths = set()

        home = Path.home()
        common_locations = [
            home / ".steam" / "steam" / "steamapps" / "compatdata",
            home / ".local" / "share" / "Steam" / "steamapps" / "compatdata",
            Path("/") / "usr" / "share" / "Steam" / "steamapps" / "compatdata",
        ]

        for compatdata_path in common_locations:
            if compatdata_path.exists():
                try:
                    for prefix_dir in compatdata_path.iterdir():
                        if prefix_dir.is_dir():
                            pfx_path = prefix_dir / "pfx"
                            if pfx_path.exists():
                                pfx_path_str = str(pfx_path)
                                if pfx_path_str not in seen_paths:
                                    seen_paths.add(pfx_path_str)
                                    prefixes.append(
                                        {
                                            "name": f"Proton - {prefix_dir.name}",
                                            "path": pfx_path_str,
                                        }
                                    )
                except (PermissionError, OSError):
                    continue

        mount_points = []

        try:
            if os.path.exists("/mnt"):
                mount_points.extend(
                    [
                        f"/mnt/{d}"
                        for d in os.listdir("/mnt")
                        if os.path.isdir(f"/mnt/{d}")
                    ]
                )
        except (PermissionError, OSError):
            pass

        try:
            if os.path.exists("/media"):
                for user in os.listdir("/media"):
                    user_path = f"/media/{user}"
                    try:
                        if os.path.isdir(user_path):
                            mount_points.extend(
                                [
                                    f"{user_path}/{d}"
                                    for d in os.listdir(user_path)
                                    if os.path.isdir(f"{user_path}/{d}")
                                ]
                            )
                    except (PermissionError, OSError):
                        continue
        except (PermissionError, OSError):
            pass

        skip_dirs = {
            "proc",
            "sys",
            "dev",
            "run",
            "tmp",
            "snap",
            "var",
            "boot",
            "srv",
            "lost+found",
            ".cache",
            ".local/share/Trash",
            "node_modules",
            ".git",
            ".svn",
            "__pycache__",
            "venv",
            "virtualenv",
            "site-packages",
            "Windows",
            "Program Files",
            "Program Files (x86)",
            "windows",
            "dosdevices",
            "drive_c",
        }

        for mount in mount_points:
            try:
                for root, dirs, files in os.walk(mount, followlinks=False):
                    depth = root[len(mount) :].count(os.sep)

                    if depth > 3:
                        dirs[:] = []
                        continue

                    if "steamapps" in dirs:
                        steamapps_path = Path(root) / "steamapps"
                        compatdata_path = steamapps_path / "compatdata"

                        if compatdata_path.exists():
                            try:
                                for prefix_dir in compatdata_path.iterdir():
                                    if prefix_dir.is_dir():
                                        pfx_path = prefix_dir / "pfx"
                                        if pfx_path.exists():
                                            pfx_path_str = str(pfx_path)
                                            if pfx_path_str not in seen_paths:
                                                seen_paths.add(pfx_path_str)
                                                prefixes.append(
                                                    {
                                                        "name": f"Proton - {prefix_dir.name}",
                                                        "path": pfx_path_str,
                                                    }
                                                )
                            except (PermissionError, OSError):
                                pass

                        dirs.remove("steamapps")

                    dirs[:] = [
                        d for d in dirs if d not in skip_dirs and not d.startswith(".")
                    ]

            except (PermissionError, OSError):
                continue

        return prefixes


class RenameGameDialog(QDialog):
    """Dialog for renaming a game"""

    def __init__(self, game: Dict[str, str], parent=None):
        super().__init__(parent)
        self.game = game
        self.new_name = None
        self.init_ui()

    def init_ui(self):
        self.setWindowTitle(f"Rename Game - {self.game['name']}")
        self.setMinimumWidth(450)

        layout = QVBoxLayout()
        layout.setContentsMargins(10, 10, 10, 10)
        layout.setSpacing(10)

        current_group = QGroupBox("Current Name")
        current_layout = QVBoxLayout()
        current_label = QLabel(self.game["name"])
        current_label.setWordWrap(True)
        current_layout.addWidget(current_label)
        current_group.setLayout(current_layout)
        layout.addWidget(current_group)

        new_group = QGroupBox("New Name")
        new_layout = QVBoxLayout()

        name_layout = QHBoxLayout()
        name_label = QLabel("Game Name:")
        name_label.setMinimumWidth(100)
        name_layout.addWidget(name_label)

        self.name_input = QLineEdit()
        self.name_input.setText(self.game["name"])
        self.name_input.setPlaceholderText("Enter new game name")
        name_layout.addWidget(self.name_input)

        new_layout.addLayout(name_layout)
        new_group.setLayout(new_layout)
        layout.addWidget(new_group)

        button_layout = QHBoxLayout()
        button_layout.addStretch()

        ok_btn = QPushButton("Rename")
        ok_btn.setMinimumWidth(100)
        ok_btn.setMinimumHeight(32)
        ok_btn.clicked.connect(self.accept_rename)
        button_layout.addWidget(ok_btn)

        cancel_btn = QPushButton("Cancel")
        cancel_btn.setMinimumWidth(80)
        cancel_btn.setMinimumHeight(32)
        cancel_btn.clicked.connect(self.reject)
        button_layout.addWidget(cancel_btn)

        layout.addLayout(button_layout)

        self.setLayout(layout)

    def accept_rename(self):
        new_name = self.name_input.text().strip()

        if not new_name:
            QMessageBox.warning(self, "Error", "Game name cannot be empty.")
            return

        self.new_name = new_name
        self.accept()


class ChangePrefixDialog(QDialog):
    """Dialog for changing a game's Wine prefix"""

    def __init__(
        self,
        game: Dict[str, str],
        available_prefixes: List[Dict[str, str]],
        parent=None,
    ):
        super().__init__(parent)
        self.game = game
        self.available_prefixes = available_prefixes
        self.new_prefix = None
        self.init_ui()

    def init_ui(self):
        self.setWindowTitle(f"Change Wine Prefix - {self.game['name']}")
        self.setMinimumWidth(550)

        layout = QVBoxLayout()
        layout.setContentsMargins(10, 10, 10, 10)
        layout.setSpacing(10)

        current_group = QGroupBox("Current Prefix")
        current_layout = QVBoxLayout()
        current_label = QLabel(self.game["prefix"])
        current_label.setWordWrap(True)
        current_layout.addWidget(current_label)
        current_group.setLayout(current_layout)
        layout.addWidget(current_group)

        new_group = QGroupBox("Select New Prefix")
        new_layout = QVBoxLayout()

        prefix_layout = QHBoxLayout()
        prefix_label = QLabel("Wine Prefix:")
        prefix_label.setMinimumWidth(100)
        prefix_layout.addWidget(prefix_label)

        self.prefix_combo = QComboBox()
        for prefix in self.available_prefixes:
            self.prefix_combo.addItem(prefix["name"], prefix["path"])
            if prefix["path"] == self.game["prefix"]:
                self.prefix_combo.setCurrentIndex(self.prefix_combo.count() - 1)

        prefix_layout.addWidget(self.prefix_combo)

        browse_btn = QPushButton("Browse...")
        browse_btn.setMaximumWidth(100)
        browse_btn.setMinimumHeight(28)
        browse_btn.clicked.connect(self.browse_prefix)
        prefix_layout.addWidget(browse_btn)

        new_layout.addLayout(prefix_layout)
        new_group.setLayout(new_layout)
        layout.addWidget(new_group)

        button_layout = QHBoxLayout()
        button_layout.addStretch()

        ok_btn = QPushButton("Change Prefix")
        ok_btn.setMinimumWidth(120)
        ok_btn.setMinimumHeight(32)
        ok_btn.clicked.connect(self.accept_change)
        button_layout.addWidget(ok_btn)

        cancel_btn = QPushButton("Cancel")
        cancel_btn.setMinimumWidth(80)
        cancel_btn.setMinimumHeight(32)
        cancel_btn.clicked.connect(self.reject)
        button_layout.addWidget(cancel_btn)

        layout.addLayout(button_layout)

        self.setLayout(layout)

    def browse_prefix(self):
        dir_path = QFileDialog.getExistingDirectory(
            self, "Select Wine Prefix Directory"
        )
        if dir_path:
            self.prefix_combo.addItem(f"Custom - {Path(dir_path).name}", dir_path)
            self.prefix_combo.setCurrentIndex(self.prefix_combo.count() - 1)

    def accept_change(self):
        new_prefix_path = self.prefix_combo.currentData()

        if not new_prefix_path or not os.path.exists(new_prefix_path):
            QMessageBox.warning(self, "Invalid Input", "Select a valid Wine prefix.")
            return

        if new_prefix_path == self.game["prefix"]:
            QMessageBox.information(
                self, "No Change", "The selected prefix is already set for this game."
            )
            return

        self.new_prefix = new_prefix_path
        self.accept()


class AddGameDialog(QDialog):
    """Dialog for adding a new game"""

    def __init__(self, available_prefixes: List[Dict[str, str]], parent=None):
        super().__init__(parent)
        self.available_prefixes = available_prefixes
        self.game_data = None
        self.init_ui()

    def init_ui(self):
        self.setWindowTitle("Add Game")
        self.setMinimumWidth(500)

        layout = QVBoxLayout()
        layout.setContentsMargins(10, 10, 10, 10)
        layout.setSpacing(10)

        name_layout = QHBoxLayout()
        name_label = QLabel("Game Name:")
        name_label.setMinimumWidth(100)
        name_layout.addWidget(name_label)
        self.name_input = QLineEdit()
        name_layout.addWidget(self.name_input)
        layout.addLayout(name_layout)

        exe_layout = QHBoxLayout()
        exe_label = QLabel("Executable:")
        exe_label.setMinimumWidth(100)
        exe_layout.addWidget(exe_label)
        self.exe_input = QLineEdit()
        exe_layout.addWidget(self.exe_input)
        browse_btn = QPushButton("Browse...")
        browse_btn.setMaximumWidth(100)
        browse_btn.setMinimumHeight(28)
        browse_btn.clicked.connect(self.browse_executable)
        exe_layout.addWidget(browse_btn)
        layout.addLayout(exe_layout)

        prefix_layout = QHBoxLayout()
        prefix_label = QLabel("Wine Prefix:")
        prefix_label.setMinimumWidth(100)
        prefix_layout.addWidget(prefix_label)
        self.prefix_combo = QComboBox()
        for prefix in self.available_prefixes:
            self.prefix_combo.addItem(prefix["name"], prefix["path"])
        prefix_layout.addWidget(self.prefix_combo)
        browse_prefix_btn = QPushButton("Browse...")
        browse_prefix_btn.setMaximumWidth(100)
        browse_prefix_btn.setMinimumHeight(28)
        browse_prefix_btn.clicked.connect(self.browse_prefix)
        prefix_layout.addWidget(browse_prefix_btn)
        layout.addLayout(prefix_layout)

        button_layout = QHBoxLayout()
        button_layout.addStretch()

        ok_btn = QPushButton("Add")
        ok_btn.setMinimumWidth(80)
        ok_btn.setMinimumHeight(32)
        ok_btn.clicked.connect(self.accept_game)
        button_layout.addWidget(ok_btn)

        cancel_btn = QPushButton("Cancel")
        cancel_btn.setMinimumWidth(80)
        cancel_btn.setMinimumHeight(32)
        cancel_btn.clicked.connect(self.reject)
        button_layout.addWidget(cancel_btn)

        layout.addLayout(button_layout)

        self.setLayout(layout)

    def browse_executable(self):
        file_path, _ = QFileDialog.getOpenFileName(
            self,
            "Select Game Executable",
            "",
            "Executables (*.exe *.bat *.cmd);;All Files (*)",
        )
        if file_path:
            self.exe_input.setText(file_path)
            # Auto-fill name if empty
            if not self.name_input.text():
                self.name_input.setText(Path(file_path).stem)

    def browse_prefix(self):
        dir_path = QFileDialog.getExistingDirectory(
            self, "Select Wine Prefix Directory"
        )
        if dir_path:
            # Add as custom prefix
            self.prefix_combo.addItem(f"Custom - {Path(dir_path).name}", dir_path)
            self.prefix_combo.setCurrentIndex(self.prefix_combo.count() - 1)

    def accept_game(self):
        name = self.name_input.text().strip()
        exe_path = self.exe_input.text().strip()
        prefix_path = self.prefix_combo.currentData()

        if not name:
            QMessageBox.warning(self, "Invalid Input", "Enter a game name.")
            return

        if not exe_path or not os.path.exists(exe_path):
            QMessageBox.warning(self, "Invalid Input", "Select a valid executable.")
            return

        if not prefix_path or not os.path.exists(prefix_path):
            QMessageBox.warning(self, "Invalid Input", "Select a valid Wine prefix.")
            return

        self.game_data = {"name": name, "executable": exe_path, "prefix": prefix_path}
        self.accept()


class GameItemWidget(QWidget):
    """Custom widget for displaying a game item with buttons"""

    launch_clicked = Signal(dict)
    change_prefix_clicked = Signal(dict)
    rename_clicked = Signal(dict)

    def __init__(self, game: Dict[str, str], parent=None):
        super().__init__(parent)
        self.game = game
        self.init_ui()

    def init_ui(self):
        layout = QHBoxLayout()
        layout.setContentsMargins(5, 5, 5, 5)
        layout.setSpacing(10)

        info_layout = QVBoxLayout()
        info_layout.setSpacing(2)

        name_label = QLabel(self.game["name"])
        name_font = QFont()
        name_font.setPointSize(10)
        name_font.setBold(True)
        name_label.setFont(name_font)
        info_layout.addWidget(name_label)

        prefix_label = QLabel(f"Prefix: {Path(self.game['prefix']).name}")
        prefix_font = QFont()
        prefix_font.setPointSize(8)
        prefix_label.setFont(prefix_font)
        prefix_label.setStyleSheet("color: #666;")
        info_layout.addWidget(prefix_label)

        layout.addLayout(info_layout, 1)

        rename_btn = QPushButton("Rename")
        rename_btn.setMinimumHeight(28)
        rename_btn.setMaximumWidth(100)
        rename_btn.clicked.connect(lambda: self.rename_clicked.emit(self.game))
        layout.addWidget(rename_btn)

        change_prefix_btn = QPushButton("Change Prefix")
        change_prefix_btn.setMinimumHeight(28)
        change_prefix_btn.setMaximumWidth(120)
        change_prefix_btn.clicked.connect(
            lambda: self.change_prefix_clicked.emit(self.game)
        )
        layout.addWidget(change_prefix_btn)

        launch_btn = QPushButton("Launch")
        launch_btn.setMinimumHeight(28)
        launch_btn.setMaximumWidth(80)
        launch_font = QFont()
        launch_font.setBold(True)
        launch_btn.setFont(launch_font)
        launch_btn.clicked.connect(lambda: self.launch_clicked.emit(self.game))
        layout.addWidget(launch_btn)

        self.setLayout(layout)
        self.setFrameStyle(QFrame.Box | QFrame.Plain)
        self.setStyleSheet(
            "GameItemWidget { border: 1px solid #ccc; border-radius: 4px; background-color: #f9f9f9; }"
        )

    def setFrameStyle(self, style):
        """Make this widget have a frame"""
        pass  # Handled by stylesheet

    def update_game(self, game: Dict[str, str]):
        """Update the widget with new game data"""
        self.game = game
        # Refresh the UI
        self.deleteLater()


class GameLauncher(QMainWindow):
    """Main window for the Wine/Proton game launcher"""

    def __init__(self):
        super().__init__()
        self.games: List[Dict[str, str]] = []
        self.available_prefixes: List[Dict[str, str]] = []
        self.config_file = Path.home() / ".config" / "hydra" / "games.json"
        self.game_processes: Dict[str, QProcess] = {}
        self.output_windows: Dict[str, GameOutputWindow] = {}
        self.process_timers: Dict[str, QTimer] = {}
        self.init_ui()
        self.load_games()
        self.scan_prefixes()

    def init_ui(self):
        self.setWindowTitle("Hydra - Wine/Proton Game Launcher")
        self.setMinimumSize(800, 600)

        central_widget = QWidget()
        self.setCentralWidget(central_widget)
        main_layout = QVBoxLayout(central_widget)
        main_layout.setContentsMargins(10, 10, 10, 10)
        main_layout.setSpacing(10)

        title_label = QLabel("Game Library")
        title_font = QFont()
        title_font.setPointSize(14)
        title_font.setBold(True)
        title_label.setFont(title_font)
        main_layout.addWidget(title_label)

        list_group = QGroupBox("")
        list_layout = QVBoxLayout()
        list_layout.setContentsMargins(5, 10, 5, 5)

        self.games_container = QWidget()
        self.games_layout = QVBoxLayout(self.games_container)
        self.games_layout.setContentsMargins(0, 0, 0, 0)
        self.games_layout.setSpacing(5)
        self.games_layout.addStretch()

        scroll_area = QScrollArea()
        scroll_area.setWidgetResizable(True)
        scroll_area.setWidget(self.games_container)
        list_layout.addWidget(scroll_area)

        list_group.setLayout(list_layout)
        main_layout.addWidget(list_group)

        self.game_widgets = []

        button_layout = QHBoxLayout()
        button_layout.setSpacing(8)

        add_btn = QPushButton("Add Game")
        add_btn.setMinimumHeight(32)
        add_btn.clicked.connect(self.add_game)
        button_layout.addWidget(add_btn)

        remove_btn = QPushButton("Remove Game")
        remove_btn.setMinimumHeight(32)
        remove_btn.clicked.connect(self.remove_game)
        button_layout.addWidget(remove_btn)

        rescan_btn = QPushButton("Rescan Prefixes")
        rescan_btn.setMinimumHeight(32)
        rescan_btn.clicked.connect(self.scan_prefixes)
        button_layout.addWidget(rescan_btn)

        main_layout.addLayout(button_layout)

        self.statusBar().showMessage("Ready")

    def scan_prefixes(self):
        """Start background scan for Wine prefixes"""
        self.statusBar().showMessage("Scanning for Wine/Proton prefixes...")
        self.scanner = PrefixScanner()
        self.scanner.prefixes_found.connect(self.on_prefixes_found)
        self.scanner.start()

    def on_prefixes_found(self, prefixes: List[Dict[str, str]]):
        """Handle found prefixes"""
        self.available_prefixes = prefixes
        self.statusBar().showMessage(f"Found {len(prefixes)} Wine/Proton prefix(es)")

    def load_games(self):
        """Load games from JSON file"""
        if self.config_file.exists():
            try:
                with open(self.config_file, "r") as f:
                    self.games = json.load(f)
                self.refresh_games_list()
            except Exception as e:
                QMessageBox.warning(self, "Error", f"Failed to load games: {str(e)}")

    def save_games(self):
        """Save games to JSON file"""
        try:
            self.config_file.parent.mkdir(parents=True, exist_ok=True)
            with open(self.config_file, "w") as f:
                json.dump(self.games, f, indent=2)
        except Exception as e:
            QMessageBox.warning(self, "Error", f"Failed to save games: {str(e)}")

    def refresh_games_list(self):
        """Refresh the games list widget"""
        for widget in self.game_widgets:
            widget.deleteLater()
        self.game_widgets.clear()

        if self.games_layout.count() > 0:
            item = self.games_layout.takeAt(self.games_layout.count() - 1)
            if item:
                item.invalidate()

        for game in self.games:
            game_widget = GameItemWidget(game)
            game_widget.launch_clicked.connect(self.launch_game)
            game_widget.change_prefix_clicked.connect(self.change_game_prefix)
            game_widget.rename_clicked.connect(self.rename_game)
            self.games_layout.insertWidget(self.games_layout.count(), game_widget)
            self.game_widgets.append(game_widget)

        self.games_layout.addStretch()

    def add_game(self):
        """Open dialog to add a new game"""
        dialog = AddGameDialog(self.available_prefixes, self)
        if dialog.exec() == QDialog.Accepted:
            self.games.append(dialog.game_data)
            self.save_games()
            self.refresh_games_list()
            self.statusBar().showMessage(f"Added {dialog.game_data['name']}")

    def remove_game(self):
        """Remove selected game"""
        if not self.games:
            QMessageBox.information(self, "No Games", "No games in the library.")
            return

        from PySide6.QtWidgets import QInputDialog

        game_names = [game["name"] for game in self.games]
        game_name, ok = QInputDialog.getItem(
            self, "Remove Game", "Select game to remove:", game_names, 0, False
        )

        if ok and game_name:
            game = next((g for g in self.games if g["name"] == game_name), None)
            if game:
                reply = QMessageBox.question(
                    self,
                    "Confirm Removal",
                    f"Remove '{game['name']}' from the library?",
                    QMessageBox.Yes | QMessageBox.No,
                )

                if reply == QMessageBox.Yes:
                    self.games.remove(game)
                    self.save_games()
                    self.refresh_games_list()
                    self.statusBar().showMessage(f"Removed {game['name']}")

    def change_game_prefix(self, game: Dict[str, str]):
        """Open dialog to change a game's Wine prefix"""
        dialog = ChangePrefixDialog(game, self.available_prefixes, self)
        if dialog.exec() == QDialog.Accepted:
            for g in self.games:
                if g["name"] == game["name"]:
                    old_prefix = g["prefix"]
                    g["prefix"] = dialog.new_prefix
                    self.save_games()
                    self.refresh_games_list()
                    self.statusBar().showMessage(
                        f"Changed prefix for {game['name']} from {Path(old_prefix).name} to {Path(dialog.new_prefix).name}"
                    )
                    break

    def rename_game(self, game: Dict[str, str]):
        """Open dialog to rename a game"""
        dialog = RenameGameDialog(game, self)
        if dialog.exec() == QDialog.Accepted:
            for g in self.games:
                if g["name"] == game["name"]:
                    old_name = g["name"]
                    g["name"] = dialog.new_name
                    self.save_games()
                    self.refresh_games_list()
                    self.statusBar().showMessage(
                        f"Renamed '{old_name}' to '{dialog.new_name}'"
                    )
                    break

    def check_wine_availability(self, output_window: GameOutputWindow):
        """Check if Wine is available and log version info"""
        try:
            output_window.append_output("=== Pre-flight checks ===", "#0066cc")

            result = subprocess.run(["which", "wine"], capture_output=True, text=True)
            if result.returncode == 0:
                wine_path = result.stdout.strip()
                output_window.append_output(f"Wine found at: {wine_path}", "#008800")
            else:
                output_window.append_output(
                    "WARNING: 'wine' command not found in PATH", "#cc6600"
                )
                return False

            result = subprocess.run(
                ["wine", "--version"], capture_output=True, text=True, timeout=5
            )
            if result.returncode == 0:
                version = result.stdout.strip()
                output_window.append_output(f"Wine version: {version}", "#008800")
            else:
                output_window.append_output(
                    f"Could not get Wine version: {result.stderr.strip()}", "#cc6600"
                )

            output_window.append_output("")
            return True

        except subprocess.TimeoutExpired:
            output_window.append_output(
                "WARNING: Wine version check timed out", "#cc6600"
            )
            output_window.append_output("")
            return True
        except Exception as e:
            output_window.append_output(f"ERROR checking Wine: {str(e)}", "#cc0000")
            output_window.append_output("")
            return False

    def launch_game(self, game: Dict[str, str]):
        """Launch a game using Wine with the specified prefix"""
        exe_path = game["executable"]
        prefix_path = game["prefix"]
        game_name = game["name"]

        if not os.path.exists(exe_path):
            QMessageBox.critical(self, "Error", f"Executable not found: {exe_path}")
            return

        if not os.path.exists(prefix_path):
            QMessageBox.critical(self, "Error", f"Wine prefix not found: {prefix_path}")
            return

        output_window = GameOutputWindow(game_name, self)
        self.output_windows[game_name] = output_window
        output_window.abort_requested.connect(self.abort_game_launch)
        output_window.show()

        output_window.append_output("═" * 60, "#0066cc")
        output_window.append_output(f"LAUNCHING: {game_name}", "#0066cc")
        output_window.append_output("═" * 60, "#0066cc")
        output_window.append_output("")

        if not self.check_wine_availability(output_window):
            output_window.append_output(
                "Pre-flight checks failed. Aborting launch.", "#cc0000"
            )
            return

        output_window.append_output("=== Launch Configuration ===", "#0066cc")
        output_window.append_output(f"Executable: {exe_path}")
        output_window.append_output(f"Working Directory: {Path(exe_path).parent}")
        output_window.append_output(f"Wine Prefix: {prefix_path}")

        if os.path.exists(prefix_path):
            system_reg = Path(prefix_path) / "system.reg"
            user_reg = Path(prefix_path) / "user.reg"
            if system_reg.exists() and user_reg.exists():
                output_window.append_output("Wine prefix validation: OK", "#008800")
            else:
                output_window.append_output(
                    "WARNING: Wine prefix may be incomplete or corrupted", "#cc6600"
                )

        output_window.append_output("")

        try:
            process = QProcess()
            self.game_processes[game_name] = process

            env = QProcess.systemEnvironment()
            env.append(f"WINEPREFIX={prefix_path}")

            if output_window.verbose_checkbox.isChecked():
                env.append("WINEDEBUG=+all")
                output_window.append_output(
                    "Verbose mode enabled: WINEDEBUG=+all", "#0066cc"
                )
            else:
                env.append("WINEDEBUG=warn+all,fixme-all")
                output_window.append_output(
                    "Debug mode: WINEDEBUG=warn+all,fixme-all", "#0066cc"
                )

            env.append("WINEDLLOVERRIDES=winemenubuilder.exe=d")
            env.append("DISPLAY=:0")

            output_window.append_output("")
            output_window.append_output("=== Environment Variables ===", "#0066cc")
            for var in env:
                if any(var.startswith(prefix) for prefix in ["WINE", "DISPLAY"]):
                    output_window.append_output(f"  {var}")
            output_window.append_output("")

            process.setEnvironment(env)

            working_dir = str(Path(exe_path).parent)
            process.setWorkingDirectory(working_dir)

            process.setProcessChannelMode(QProcess.MergedChannels)

            process.readyReadStandardOutput.connect(
                lambda: self.handle_output(game_name)
            )
            process.readyReadStandardError.connect(
                lambda: self.handle_output(game_name)
            )
            process.started.connect(lambda: self.handle_started(game_name))
            process.finished.connect(
                lambda exit_code, exit_status: self.handle_finished(
                    game_name, exit_code, exit_status
                )
            )
            process.errorOccurred.connect(
                lambda error: self.handle_error(game_name, error)
            )
            process.stateChanged.connect(
                lambda state: self.handle_state_changed(game_name, state)
            )

            timer = QTimer()
            timer.timeout.connect(lambda: self.check_process_status(game_name))
            timer.start(2000)
            self.process_timers[game_name] = timer

            exe_path_abs = os.path.abspath(exe_path)

            output_window.append_output("=== Starting Wine Process ===", "#0066cc")
            output_window.append_output(f"Command: wine {exe_path_abs}")
            output_window.append_output("")

            process.start("wine", [exe_path_abs])

            if not process.waitForStarted(5000):
                output_window.append_output(
                    "ERROR: Process failed to start within 5 seconds", "#cc0000"
                )
                output_window.append_output(
                    f"Process state: {process.state()}", "#cc0000"
                )
                output_window.append_output(
                    f"Process error: {process.errorString()}", "#cc0000"
                )

            self.statusBar().showMessage(f"Launching {game_name}...")

        except Exception as e:
            output_window.append_output("", "#cc0000")
            output_window.append_output(f"CRITICAL ERROR: {str(e)}", "#cc0000")
            output_window.append_output(
                f"Exception type: {type(e).__name__}", "#cc0000"
            )

            import traceback

            tb = traceback.format_exc()
            output_window.append_output("Traceback:", "#cc0000")
            for line in tb.split("\n"):
                output_window.append_output(f"  {line}", "#cc0000")

            QMessageBox.critical(
                self, "Launch Error", f"Failed to launch game: {str(e)}"
            )
            self.statusBar().showMessage("Launch failed")

    def handle_output(self, game_name: str):
        """Handle all output (stdout and stderr) from game process"""
        if game_name not in self.game_processes:
            return

        process = self.game_processes[game_name]

        stdout_data = process.readAllStandardOutput().data()
        stderr_data = process.readAllStandardError().data()

        if game_name in self.output_windows:
            output_window = self.output_windows[game_name]

            if stdout_data:
                try:
                    output = stdout_data.decode("utf-8", errors="replace")
                    for line in output.split("\n"):
                        if line.strip():
                            output_window.append_output(f"[OUT] {line.rstrip()}")
                except Exception as e:
                    output_window.append_output(
                        f"[ERROR decoding stdout: {e}]", "#cc0000"
                    )

            if stderr_data:
                try:
                    output = stderr_data.decode("utf-8", errors="replace")
                    for line in output.split("\n"):
                        if line.strip():
                            if "err:" in line.lower() or "error" in line.lower():
                                output_window.append_output(
                                    f"[ERR] {line.rstrip()}", "#cc0000"
                                )
                            elif "warn:" in line.lower() or "warning" in line.lower():
                                output_window.append_output(
                                    f"[WARN] {line.rstrip()}", "#cc6600"
                                )
                            elif "fixme:" in line.lower():
                                if output_window.verbose_checkbox.isChecked():
                                    output_window.append_output(
                                        f"[FIXME] {line.rstrip()}", "#666666"
                                    )
                            else:
                                output_window.append_output(f"[ERR] {line.rstrip()}")
                except Exception as e:
                    output_window.append_output(
                        f"[ERROR decoding stderr: {e}]", "#cc0000"
                    )

    def handle_state_changed(self, game_name: str, state):
        """Handle process state changes"""
        if game_name not in self.output_windows:
            return

        output_window = self.output_windows[game_name]

        state_names = {
            QProcess.NotRunning: "Not Running",
            QProcess.Starting: "Starting",
            QProcess.Running: "Running",
        }

        state_name = state_names.get(state, f"Unknown State ({state})")
        output_window.append_output(f"Process state changed: {state_name}", "#0066cc")

    def check_process_status(self, game_name: str):
        """Periodically check process status"""
        if game_name not in self.game_processes:
            if game_name in self.process_timers:
                self.process_timers[game_name].stop()
                del self.process_timers[game_name]
            return

        process = self.game_processes[game_name]

        if game_name in self.output_windows:
            state = process.state()
            if state == QProcess.NotRunning and process.exitCode() == -1:
                # Process hasn't started yet - this might indicate a problem
                self.output_windows[game_name].append_output(
                    "Process check: Still waiting to start...", "#cc6600"
                )

    def handle_started(self, game_name: str):
        """Handle game process started"""
        if game_name in self.game_processes:
            process = self.game_processes[game_name]
            pid = process.processId()
            if game_name in self.output_windows:
                self.output_windows[game_name].append_output("")
                self.output_windows[game_name].append_output(
                    f"✓ Process started successfully (PID: {pid})", "#008800"
                )
                self.output_windows[game_name].append_output("─" * 60)
                self.output_windows[game_name].append_output("")
            self.statusBar().showMessage(f"{game_name} is running (PID: {pid})")

    def abort_game_launch(self, game_name: str):
        """Abort a game launch by terminating its process"""
        if game_name in self.game_processes:
            process = self.game_processes[game_name]
            if process.state() != QProcess.NotRunning:
                reply = QMessageBox.question(
                    self,
                    "Abort Launch",
                    f"Are you sure you want to abort the launch of '{game_name}'?",
                    QMessageBox.Yes | QMessageBox.No,
                )

                if reply == QMessageBox.Yes:
                    if game_name in self.output_windows:
                        self.output_windows[game_name].append_output("")
                        self.output_windows[game_name].append_output(
                            "═" * 60, "#cc6600"
                        )
                        self.output_windows[game_name].append_output(
                            "ABORTING LAUNCH", "#cc6600"
                        )
                        self.output_windows[game_name].append_output(
                            "═" * 60, "#cc6600"
                        )
                        self.output_windows[game_name].append_output(
                            "User requested abort. Terminating process...", "#cc6600"
                        )

                    process.kill()
                    process.waitForFinished(3000)

                    if game_name in self.output_windows:
                        self.output_windows[game_name].append_output(
                            "Process terminated.", "#cc0000"
                        )
                        self.output_windows[game_name].abort_btn.setEnabled(False)

                    self.statusBar().showMessage(f"Aborted launch of {game_name}")
            else:
                if game_name in self.output_windows:
                    self.output_windows[game_name].append_output(
                        "Cannot abort: Process is not running.", "#cc6600"
                    )
        else:
            QMessageBox.information(
                self, "Info", f"No active process found for '{game_name}'."
            )

    def handle_finished(self, game_name: str, exit_code: int, exit_status):
        """Handle game process finished"""
        if game_name in self.output_windows:
            self.output_windows[game_name].append_output("")
            self.output_windows[game_name].append_output("═" * 60, "#0066cc")
            self.output_windows[game_name].append_output("PROCESS FINISHED", "#0066cc")
            self.output_windows[game_name].append_output("═" * 60, "#0066cc")

            color = "#008800" if exit_code == 0 else "#cc0000"
            self.output_windows[game_name].append_output(
                f"Exit Code: {exit_code}", color
            )

            status_name = (
                exit_status.name if hasattr(exit_status, "name") else str(exit_status)
            )
            self.output_windows[game_name].append_output(
                f"Exit Status: {status_name}", color
            )

            if exit_code != 0:
                self.output_windows[game_name].append_output(
                    "Non-zero exit code indicates the game may have crashed or encountered an error.",
                    "#cc6600",
                )

            self.output_windows[game_name].abort_btn.setEnabled(False)

        if game_name in self.game_processes:
            del self.game_processes[game_name]

        if game_name in self.process_timers:
            self.process_timers[game_name].stop()
            del self.process_timers[game_name]

        status_msg = f"{game_name} exited (code: {exit_code})"
        self.statusBar().showMessage(status_msg)

    def handle_error(self, game_name: str, error):
        """Handle game process error"""
        error_messages = {
            QProcess.FailedToStart: "Failed to start Wine process - check if Wine is installed",
            QProcess.Crashed: "Wine process crashed unexpectedly",
            QProcess.Timedout: "Wine process timed out",
            QProcess.WriteError: "Write error occurred",
            QProcess.ReadError: "Read error occurred",
            QProcess.UnknownError: "Unknown error occurred",
        }

        error_msg = error_messages.get(error, f"Error code: {error}")

        if game_name in self.output_windows:
            self.output_windows[game_name].append_output("")
            self.output_windows[game_name].append_output("═" * 60, "#cc0000")
            self.output_windows[game_name].append_output(
                f"PROCESS ERROR: {error_msg}", "#cc0000"
            )
            self.output_windows[game_name].append_output("═" * 60, "#cc0000")

            if error == QProcess.FailedToStart:
                self.output_windows[game_name].append_output(
                    "Troubleshooting steps:", "#cc6600"
                )
                self.output_windows[game_name].append_output(
                    "1. Verify Wine is installed: wine --version"
                )
                self.output_windows[game_name].append_output(
                    "2. Check if the executable path is correct"
                )
                self.output_windows[game_name].append_output(
                    "3. Verify the Wine prefix exists and is valid"
                )
                self.output_windows[game_name].append_output(
                    "4. Try running the command manually from terminal"
                )

            self.output_windows[game_name].abort_btn.setEnabled(False)

        QMessageBox.critical(
            self, "Launch Error", f"Failed to launch {game_name}:\n\n{error_msg}"
        )
        self.statusBar().showMessage(f"Launch failed: {error_msg}")


def main():
    app = QApplication(sys.argv)
    app.setApplicationName("Hydra")

    window = GameLauncher()
    window.show()

    sys.exit(app.exec())


if __name__ == "__main__":
    main()
