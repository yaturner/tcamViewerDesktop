#!/bin/sh
# Builds a real, self-contained .AppImage from the app-image jpackage output.
#
# Compose Desktop's own `./gradlew packageAppImage` task only produces a plain
# directory (jpackage's "app-image" type) — not the single-file .AppImage
# format from appimage.org. This script wraps that directory into one using
# appimagetool (https://github.com/AppImage/appimagetool), which this script
# downloads to ./build/appimagetool if not already on PATH.
#
# Usage: ./scripts/build-appimage.sh [version]
# Output: build/tcamViewerDesktop-<version>-<arch>.AppImage (arch from `uname -m`,
# e.g. x86_64 or aarch64 — matches both jpackage's host-arch output and
# appimagetool's own per-arch release asset naming)

set -eu

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

ARCH="$(uname -m)"
VERSION="${1:-$(grep -oP '(?<=packageVersion = ")[^"]+' app/build.gradle.kts)}"
APP_IMAGE_DIR="app/build/compose/binaries/main/app/tcamViewerDesktop"
APPDIR="build/AppDir"
OUTPUT="build/tcamViewerDesktop-${VERSION}-${ARCH}.AppImage"

echo "Building app-image (jpackage output)..."
./gradlew packageAppImage

echo "Assembling AppDir..."
rm -rf "$APPDIR"
mkdir -p "$APPDIR"
cp -r "$APP_IMAGE_DIR"/* "$APPDIR/"
cp "$APPDIR/lib/tcamViewerDesktop.png" "$APPDIR/tcamviewerdesktop.png"

cat > "$APPDIR/AppRun" << 'EOF'
#!/bin/sh
HERE="$(dirname "$(readlink -f "${0}")")"
exec "${HERE}/bin/tcamViewerDesktop" "$@"
EOF
chmod +x "$APPDIR/AppRun"

cat > "$APPDIR/tcamviewerdesktop.desktop" << 'EOF'
[Desktop Entry]
Type=Application
Name=tCam Viewer Desktop
Comment=Desktop viewer for the tCam thermal imaging camera
Exec=tcamViewerDesktop
Icon=tcamviewerdesktop
Categories=Graphics;Viewer;
Terminal=false
EOF

APPIMAGETOOL="$(command -v appimagetool || echo "$REPO_ROOT/build/appimagetool")"
if ! command -v appimagetool >/dev/null 2>&1 && [ ! -x "$APPIMAGETOOL" ]; then
    echo "Downloading appimagetool..."
    curl -fsSL -o "$APPIMAGETOOL" \
        "https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-${ARCH}.AppImage"
    chmod +x "$APPIMAGETOOL"
fi

echo "Packaging AppImage..."
ARCH="$ARCH" "$APPIMAGETOOL" "$APPDIR" "$OUTPUT"

echo "Done: $OUTPUT"
