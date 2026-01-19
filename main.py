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
    QTextEdit,
)
from PySide6.QtCore import Qt, QThread, Signal, QProcess
from PySide6.QtGui import QFont


class GameOutputWindow(QMainWindow):
    """Window for displaying game launch output and debug information"""

    def __init__(self, game_name: str, parent=None):
        super().__init__(parent)
        self.game_name = game_name
        self.init_ui()

    def init_ui(self):
        self.setWindowTitle(f"Game Output - {self.game_name}")
        self.setMinimumSize(700, 500)

        central_widget = QWidget()
        self.setCentralWidget(central_widget)
        layout = QVBoxLayout(central_widget)
        layout.setContentsMargins(10, 10, 10, 10)
        layout.setSpacing(10)

        # Title
        title_label = QLabel(f"Output for: {self.game_name}")
        title_font = QFont()
        title_font.setPointSize(12)
        title_font.setBold(True)
        title_label.setFont(title_font)
        layout.addWidget(title_label)

        # Output text area
        self.output_text = QTextEdit()
        self.output_text.setReadOnly(True)
        self.output_text.setFont(QFont("Monospace", 9))
        layout.addWidget(self.output_text)

        # Button layout
        button_layout = QHBoxLayout()
        button_layout.addStretch()

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

    def append_output(self, text: str):
        """Append text to the output window"""
        self.output_text.append(text)
        # Auto-scroll to bottom
        cursor = self.output_text.textCursor()
        cursor.movePosition(cursor.End)
        self.output_text.setTextCursor(cursor)

    def clear_output(self):
        """Clear all output"""
        self.output_text.clear()


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
        layout.setContentsMargins(10, 10, 10, 10)
        layout.setSpacing(10)

        # Game name
        name_layout = QHBoxLayout()
        name_label = QLabel("Game Name:")
        name_label.setMinimumWidth(100)
        name_layout.addWidget(name_label)
        self.name_input = QLineEdit()
        name_layout.addWidget(self.name_input)
        layout.addLayout(name_layout)

        # Executable
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

        # Wine prefix
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

        # Button layout
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


class GameLauncher(QMainWindow):
    """Main window for the Wine/Proton game launcher"""

    def __init__(self):
        super().__init__()
        self.games: List[Dict[str, str]] = []
        self.available_prefixes: List[Dict[str, str]] = []
        self.config_file = Path.home() / ".config" / "hydra" / "games.json"
        self.game_processes: Dict[str, QProcess] = {}
        self.output_windows: Dict[str, GameOutputWindow] = {}
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
        main_layout.setContentsMargins(10, 10, 10, 10)
        main_layout.setSpacing(10)

        # Title
        title_label = QLabel("Game Library")
        title_font = QFont()
        title_font.setPointSize(14)
        title_font.setBold(True)
        title_label.setFont(title_font)
        main_layout.addWidget(title_label)

        # Games list
        list_group = QGroupBox("Games")
        list_layout = QVBoxLayout()
        list_layout.setContentsMargins(5, 10, 5, 5)
        self.games_list = QListWidget()
        self.games_list.itemDoubleClicked.connect(self.launch_selected_game)
        list_layout.addWidget(self.games_list)
        list_group.setLayout(list_layout)
        main_layout.addWidget(list_group)

        # Buttons
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

        launch_btn = QPushButton("Launch Game")
        launch_btn.setMinimumHeight(32)
        launch_btn.clicked.connect(self.launch_selected_game)
        launch_font = QFont()
        launch_font.setBold(True)
        launch_btn.setFont(launch_font)
        button_layout.addWidget(launch_btn)

        rescan_btn = QPushButton("Rescan Prefixes")
        rescan_btn.setMinimumHeight(32)
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
        game_name = game["name"]

        if not os.path.exists(exe_path):
            QMessageBox.critical(self, "Error", f"Executable not found: {exe_path}")
            return

        if not os.path.exists(prefix_path):
            QMessageBox.critical(self, "Error", f"Wine prefix not found: {prefix_path}")
            return

        # Create output window
        output_window = GameOutputWindow(game_name, self)
        self.output_windows[game_name] = output_window
        output_window.show()

        # Log initial information
        output_window.append_output(f"=== Launching {game_name} ===")
        output_window.append_output(f"Executable: {exe_path}")
        output_window.append_output(f"Wine Prefix: {prefix_path}")
        output_window.append_output(f"Working Directory: {Path(exe_path).parent}")
        output_window.append_output("")

        try:
            # Create QProcess for better control and output capture
            process = QProcess()
            self.game_processes[game_name] = process

            # Set up environment
            env = QProcess.systemEnvironment()
            env.append(f"WINEPREFIX={prefix_path}")
            # Disable Wine debug output to prevent hanging on configuration dialogs
            env.append("WINEDEBUG=-all")
            # Disable Wine GUI dialogs
            env.append("WINEDLLOVERRIDES=winemenubuilder.exe=d")
            process.setEnvironment(env)

            # Set working directory
            working_dir = str(Path(exe_path).parent)
            process.setWorkingDirectory(working_dir)

            # Connect signals for output capture
            process.readyReadStandardOutput.connect(
                lambda: self.handle_stdout(game_name)
            )
            process.readyReadStandardError.connect(
                lambda: self.handle_stderr(game_name)
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

            # Launch the game
            exe_path_abs = os.path.abspath(exe_path)
            process.start("wine", [exe_path_abs])

            self.statusBar().showMessage(f"Launching {game_name}...")
            output_window.append_output("Starting Wine process...")

        except Exception as e:
            output_window.append_output(f"ERROR: {str(e)}")
            QMessageBox.critical(
                self, "Launch Error", f"Failed to launch game: {str(e)}"
            )
            self.statusBar().showMessage("Launch failed")

    def handle_stdout(self, game_name: str):
        """Handle standard output from game process"""
        if game_name in self.game_processes:
            process = self.game_processes[game_name]
            output = (
                process.readAllStandardOutput().data().decode("utf-8", errors="replace")
            )
            if game_name in self.output_windows and output.strip():
                self.output_windows[game_name].append_output(output.rstrip())

    def handle_stderr(self, game_name: str):
        """Handle standard error from game process"""
        if game_name in self.game_processes:
            process = self.game_processes[game_name]
            output = (
                process.readAllStandardError().data().decode("utf-8", errors="replace")
            )
            if game_name in self.output_windows and output.strip():
                self.output_windows[game_name].append_output(
                    f"[STDERR] {output.rstrip()}"
                )

    def handle_started(self, game_name: str):
        """Handle game process started"""
        if game_name in self.game_processes:
            process = self.game_processes[game_name]
            pid = process.processId()
            if game_name in self.output_windows:
                self.output_windows[game_name].append_output(
                    f"Process started with PID: {pid}"
                )
                self.output_windows[game_name].append_output("")
            self.statusBar().showMessage(f"Launched {game_name} (PID: {pid})")

    def handle_finished(self, game_name: str, exit_code: int, exit_status):
        """Handle game process finished"""
        if game_name in self.output_windows:
            self.output_windows[game_name].append_output("")
            self.output_windows[game_name].append_output(f"=== Process Finished ===")
            self.output_windows[game_name].append_output(f"Exit Code: {exit_code}")
            self.output_windows[game_name].append_output(
                f"Exit Status: {exit_status.name if hasattr(exit_status, 'name') else str(exit_status)}"
            )

        if game_name in self.game_processes:
            del self.game_processes[game_name]

        self.statusBar().showMessage(f"{game_name} exited with code {exit_code}")

    def handle_error(self, game_name: str, error):
        """Handle game process error"""
        error_messages = {
            QProcess.FailedToStart: "Failed to start Wine process",
            QProcess.Crashed: "Wine process crashed",
            QProcess.Timedout: "Wine process timed out",
            QProcess.WriteError: "Write error occurred",
            QProcess.ReadError: "Read error occurred",
            QProcess.UnknownError: "Unknown error occurred",
        }

        error_msg = error_messages.get(error, f"Error code: {error}")

        if game_name in self.output_windows:
            self.output_windows[game_name].append_output(f"ERROR: {error_msg}")

        QMessageBox.critical(
            self, "Launch Error", f"Failed to launch {game_name}: {error_msg}"
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
