import sys
import os
import json
import subprocess
from pathlib import Path
from typing import List, Dict, Optional
from PySide6.QtWidgets import (
    QApplication,
    QMainWindow,
    QWidget,
    QVBoxLayout,
    QHBoxLayout,
    QPushButton,
    QListWidget,
    QLabel,
    QFileDialog,
    QComboBox,
    QMessageBox,
    QDialog,
    QLineEdit,
    QListWidgetItem,
    QGroupBox,
)
from PySide6.QtCore import Qt, QThread, Signal


class PrefixScanner(QThread):
    """Background thread for scanning Wine/Proton prefixes"""

    prefixes_found = Signal(list)

    def run(self):
        prefixes = self.scan_wine_prefixes()
        self.prefixes_found.emit(prefixes)

    def scan_wine_prefixes(self) -> List[Dict[str, str]]:
        """Scan for Steam Library directories and find Proton prefixes"""
        prefixes = []

        mount_points = ["/"]

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

        home = Path.home()
        mount_points.append(str(home))

        for mount in mount_points:
            try:
                for root, dirs, files in os.walk(mount, followlinks=False):
                    depth = root[len(mount) :].count(os.sep)
                    if depth > 5:
                        dirs[:] = []
                        continue

                    if "steamapps" in dirs:
                        steamapps_path = Path(root) / "steamapps"
                        compatdata_path = steamapps_path / "compatdata"

                        if compatdata_path.exists():
                            for prefix_dir in compatdata_path.iterdir():
                                if prefix_dir.is_dir():
                                    pfx_path = prefix_dir / "pfx"
                                    if pfx_path.exists():
                                        prefixes.append(
                                            {
                                                "name": f"Proton - {prefix_dir.name}",
                                                "path": str(pfx_path),
                                            }
                                        )

                    # Don't recurse into certain directories
                    dirs[:] = [
                        d
                        for d in dirs
                        if d not in ["proc", "sys", "dev", "run", "tmp", "snap"]
                    ]

            except (PermissionError, OSError):
                continue

        return prefixes


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

        name_layout = QHBoxLayout()
        name_layout.addWidget(QLabel("Game Name:"))
        self.name_input = QLineEdit()
        name_layout.addWidget(self.name_input)
        layout.addLayout(name_layout)

        exe_layout = QHBoxLayout()
        exe_layout.addWidget(QLabel("Executable:"))
        self.exe_input = QLineEdit()
        exe_layout.addWidget(self.exe_input)
        browse_btn = QPushButton("Browse...")
        browse_btn.clicked.connect(self.browse_executable)
        exe_layout.addWidget(browse_btn)
        layout.addLayout(exe_layout)

        prefix_layout = QHBoxLayout()
        prefix_layout.addWidget(QLabel("Wine Prefix:"))
        self.prefix_combo = QComboBox()
        for prefix in self.available_prefixes:
            self.prefix_combo.addItem(prefix["name"], prefix["path"])
        prefix_layout.addWidget(self.prefix_combo)
        browse_prefix_btn = QPushButton("Browse...")
        browse_prefix_btn.clicked.connect(self.browse_prefix)
        prefix_layout.addWidget(browse_prefix_btn)
        layout.addLayout(prefix_layout)

        button_layout = QHBoxLayout()
        ok_btn = QPushButton("Add")
        ok_btn.clicked.connect(self.accept_game)
        cancel_btn = QPushButton("Cancel")
        cancel_btn.clicked.connect(self.reject)
        button_layout.addWidget(ok_btn)
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
            QMessageBox.warning(self, "Invalid Input", "Please enter a game name.")
            return

        if not exe_path or not os.path.exists(exe_path):
            QMessageBox.warning(
                self, "Invalid Input", "Please select a valid executable."
            )
            return

        if not prefix_path or not os.path.exists(prefix_path):
            QMessageBox.warning(
                self, "Invalid Input", "Please select a valid Wine prefix."
            )
            return

        self.game_data = {"name": name, "executable": exe_path, "prefix": prefix_path}
        self.accept()


class GameLauncher(QMainWindow):
    """Main window for the Wine/Proton game launcher"""

    def __init__(self):
        super().__init__()
        self.games: List[Dict[str, str]] = []
        self.available_prefixes: List[Dict[str, str]] = []
        self.config_file = Path.home() / ".config" / "hydra" / "games.json"
        self.init_ui()
        self.load_games()
        self.scan_prefixes()

    def init_ui(self):
        self.setWindowTitle("Hydra - Wine/Proton Game Launcher")
        self.setMinimumSize(800, 600)

        # Central widget
        central_widget = QWidget()
        self.setCentralWidget(central_widget)
        main_layout = QVBoxLayout(central_widget)

        # Title
        title_label = QLabel("Game Library")
        title_label.setStyleSheet("font-size: 18px; font-weight: bold; padding: 10px;")
        main_layout.addWidget(title_label)

        # Games list
        list_group = QGroupBox("Games")
        list_layout = QVBoxLayout()
        self.games_list = QListWidget()
        self.games_list.itemDoubleClicked.connect(self.launch_selected_game)
        list_layout.addWidget(self.games_list)
        list_group.setLayout(list_layout)
        main_layout.addWidget(list_group)

        # Buttons
        button_layout = QHBoxLayout()

        add_btn = QPushButton("Add Game")
        add_btn.clicked.connect(self.add_game)
        button_layout.addWidget(add_btn)

        remove_btn = QPushButton("Remove Game")
        remove_btn.clicked.connect(self.remove_game)
        button_layout.addWidget(remove_btn)

        launch_btn = QPushButton("Launch Game")
        launch_btn.clicked.connect(self.launch_selected_game)
        launch_btn.setStyleSheet("font-weight: bold; padding: 8px;")
        button_layout.addWidget(launch_btn)

        rescan_btn = QPushButton("Rescan Prefixes")
        rescan_btn.clicked.connect(self.scan_prefixes)
        button_layout.addWidget(rescan_btn)

        main_layout.addLayout(button_layout)

        # Status bar
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
        self.games_list.clear()
        for game in self.games:
            item = QListWidgetItem(game["name"])
            item.setData(Qt.UserRole, game)
            self.games_list.addItem(item)

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
        current_item = self.games_list.currentItem()
        if not current_item:
            QMessageBox.information(
                self, "No Selection", "Please select a game to remove."
            )
            return

        game = current_item.data(Qt.UserRole)
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

    def launch_selected_game(self):
        """Launch the selected game with its Wine prefix"""
        current_item = self.games_list.currentItem()
        if not current_item:
            QMessageBox.information(
                self, "No Selection", "Please select a game to launch."
            )
            return

        game = current_item.data(Qt.UserRole)
        self.launch_game(game)

    def launch_game(self, game: Dict[str, str]):
        """Launch a game using Wine with the specified prefix"""
        exe_path = game["executable"]
        prefix_path = game["prefix"]

        if not os.path.exists(exe_path):
            QMessageBox.critical(self, "Error", f"Executable not found: {exe_path}")
            return

        if not os.path.exists(prefix_path):
            QMessageBox.critical(self, "Error", f"Wine prefix not found: {prefix_path}")
            return

        try:
            env = os.environ.copy()
            env["WINEPREFIX"] = prefix_path

            working_dir = str(Path(exe_path).parent)

            exe_path_abs = os.path.abspath(exe_path)

            self.statusBar().showMessage(f"Launching {game['name']}...")

            process = subprocess.Popen(
                ["wine", exe_path_abs],
                env=env,
                cwd=working_dir,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )

            self.statusBar().showMessage(
                f"Launched {game['name']} (PID: {process.pid})"
            )

        except Exception as e:
            QMessageBox.critical(
                self, "Launch Error", f"Failed to launch game: {str(e)}"
            )
            self.statusBar().showMessage("Launch failed")


def main():
    app = QApplication(sys.argv)
    app.setApplicationName("Hydra")

    window = GameLauncher()
    window.show()

    sys.exit(app.exec())


if __name__ == "__main__":
    main()
