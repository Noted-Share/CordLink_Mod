#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

ORIGINAL="gradle.properties"
BACKUP="gradle.properties.bak"

# Save original
cp "$ORIGINAL" "$BACKUP"

mkdir -p "build/multiversion"

for dir in versions/*/; do
    version=$(basename "$dir")
    echo "========================================="
    echo "Building for Minecraft $version..."
    echo "========================================="

    # Merge: base properties + version-specific overrides
    cp "$BACKUP" "$ORIGINAL"
    echo "" >> "$ORIGINAL"
    cat "$dir/gradle.properties" >> "$ORIGINAL"

    ./gradlew clean build --no-daemon || {
        echo "FAILED: Minecraft $version"
        cp "$BACKUP" "$ORIGINAL"
        exit 1
    }

    # Copy output JAR
    find build/libs -name "minecord-*.jar" ! -name "*-sources.jar" -exec cp {} "build/multiversion/minecord-${version}.jar" \;
    echo "OK: minecord-${version}.jar"
done

# Restore original
cp "$BACKUP" "$ORIGINAL"
rm "$BACKUP"

echo ""
echo "========================================="
echo "All versions built successfully!"
echo "Output: build/multiversion/"
ls -la build/multiversion/
echo "========================================="
