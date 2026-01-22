#!/bin/bash

set -e

if [ -z "$1" ]; then
    echo "Error: Provide the path to the Styx JAR file"
    echo "Usage: $0 /path/to/styx.jar"
    exit 1
fi

JAR_PATH="$1"

if [ ! -f "$JAR_PATH" ]; then
    echo "Error: JAR file not found: $JAR_PATH"
    exit 1
fi

JAR_PATH=$(realpath "$JAR_PATH")

echo "Installing Styx desktop entry..."
echo "JAR location: $JAR_PATH"

DESKTOP_DIR="$HOME/.local/share/applications"
mkdir -p "$DESKTOP_DIR"

DESKTOP_FILE="$DESKTOP_DIR/styx.desktop"
ICON_PATH=""

if command -v unzip &> /dev/null; then
    ICON_DIR="$HOME/.local/share/icons/hicolor/256x256/apps"
    mkdir -p "$ICON_DIR"
    
    if unzip -l "$JAR_PATH" | grep -q "icon.jpg\|icon.png"; then
        echo "Extracting icon from JAR..."
        unzip -o -j "$JAR_PATH" "icon.jpg" -d "$ICON_DIR" 2>/dev/null && ICON_PATH="$ICON_DIR/icon.jpg" || true
        unzip -o -j "$JAR_PATH" "icon.png" -d "$ICON_DIR" 2>/dev/null && ICON_PATH="$ICON_DIR/icon.png" || true
        
        if [ -n "$ICON_PATH" ]; then
            NEW_ICON_PATH="$ICON_DIR/styx.${ICON_PATH##*.}"
            mv "$ICON_PATH" "$NEW_ICON_PATH" 2>/dev/null || true
            ICON_PATH="$NEW_ICON_PATH"
        fi
    fi
fi

cat > "$DESKTOP_FILE" << EOF
[Desktop Entry]
Version=1.0
Type=Application
Name=Styx
Comment=Game Launcher
Exec=java -jar "$JAR_PATH"
Terminal=false
Categories=Game;
StartupNotify=true
EOF

if [ -n "$ICON_PATH" ] && [ -f "$ICON_PATH" ]; then
    echo "Icon=$ICON_PATH" >> "$DESKTOP_FILE"
    echo "Icon extracted and added to desktop entry"
fi

chmod +x "$DESKTOP_FILE"

if command -v update-desktop-database &> /dev/null; then
    echo "Updating desktop database..."
    update-desktop-database "$DESKTOP_DIR" 2>/dev/null || true
fi

echo ""
echo "Desktop entry created successfully."
echo "  Location: $DESKTOP_FILE"
echo ""
echo "Styx should now appear in the application menu under 'Games'."
echo "You can also run it from the command line with: java -jar $JAR_PATH"
