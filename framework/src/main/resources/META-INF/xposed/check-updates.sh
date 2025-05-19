#!/system/bin/sh
# LSPosed Framework Update Checker
# This script checks for updates using GitHub API

# Configuration
GITHUB_REPO="wobbz/LSPosedFramework"
CURRENT_VERSION="1.0.0" # Will be replaced dynamically during build
CURRENT_VERSION_CODE="1" # Will be replaced dynamically during build
UPDATE_FILE="/data/data/com.wobbz.lsposedframework/files/update_info.json"
LOG_FILE="/data/local/tmp/update_check.log"
GITHUB_API_URL="https://api.github.com/repos/$GITHUB_REPO/releases/latest"

# Log function
log() {
  echo "$(date +"%Y-%m-%d %H:%M:%S") - $1" >> "$LOG_FILE"
  echo "$1"
}

# Make sure curl or wget is available
if command -v curl >/dev/null 2>&1; then
  DOWNLOAD_CMD="curl -sSL"
elif command -v wget >/dev/null 2>&1; then
  DOWNLOAD_CMD="wget -q -O -"
else
  log "Error: Neither curl nor wget found. Cannot check for updates."
  exit 1
fi

# Create parent directory for update file if it doesn't exist
mkdir -p "$(dirname "$UPDATE_FILE")"

# Check internet connectivity
if ! ping -c 1 api.github.com >/dev/null 2>&1; then
  log "No internet connection available. Skipping update check."
  exit 0
fi

# Get latest release info from GitHub
log "Checking for updates from GitHub ($GITHUB_REPO)..."
release_info=$($DOWNLOAD_CMD "$GITHUB_API_URL" 2>/dev/null)

if [ -z "$release_info" ]; then
  log "Failed to get release information from GitHub."
  exit 1
fi

# Extract version information from release info
# Using grep and sed to extract the version string
latest_version=$(echo "$release_info" | grep '"tag_name"' | sed -E 's/.*"tag_name": *"v?([^"]+)".*/\1/')
latest_version_code=$(echo "$release_info" | grep '"id"' | head -n 1 | sed -E 's/.*"id": *([0-9]+).*/\1/')

if [ -z "$latest_version" ]; then
  log "Failed to extract version information from release."
  exit 1
fi

log "Current version: $CURRENT_VERSION (code: $CURRENT_VERSION_CODE)"
log "Latest version: $latest_version (code: $latest_version_code)"

# Compare versions
if [ "$latest_version" != "$CURRENT_VERSION" ]; then
  # Get download URL for the APK asset
  download_url=$(echo "$release_info" | grep -A 5 '"browser_download_url".*\.apk"' | grep '"browser_download_url"' | head -n 1 | sed -E 's/.*"browser_download_url": *"([^"]+)".*/\1/')
  
  # Get changelog
  changelog=$(echo "$release_info" | grep -A 50 '"body"' | head -n 20 | sed -E 's/.*"body": *"([^"]+)".*/\1/' | sed 's/\\r\\n/\n/g')
  
  # Check if this is a newer version or just a different version
  is_newer="false"
  if [ "$latest_version_code" -gt "$CURRENT_VERSION_CODE" ]; then
    is_newer="true"
  fi
  
  # Create update info JSON
  cat > "$UPDATE_FILE" << EOF
{
  "latestVersion": "$latest_version",
  "latestVersionCode": $latest_version_code,
  "currentVersion": "$CURRENT_VERSION",
  "currentVersionCode": $CURRENT_VERSION_CODE,
  "downloadUrl": "$download_url",
  "changelog": "$changelog",
  "isNewer": $is_newer,
  "checkTime": $(date +%s)
}
EOF
  
  log "Update available! Information saved to $UPDATE_FILE"
  exit 0
else
  # Create update info JSON showing no update is available
  cat > "$UPDATE_FILE" << EOF
{
  "latestVersion": "$latest_version",
  "latestVersionCode": $latest_version_code,
  "currentVersion": "$CURRENT_VERSION",
  "currentVersionCode": $CURRENT_VERSION_CODE,
  "isNewer": false,
  "checkTime": $(date +%s)
}
EOF
  
  log "No update available. You are running the latest version."
  exit 0
fi 