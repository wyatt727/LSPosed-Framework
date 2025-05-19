#!/system/bin/sh
# LSPosed Framework SELinux Context Management
# This script runs after the filesystem is mounted but before Zygote starts

MODDIR=${0%/*}
FRAMEWORK_DATA_DIR="/data/data/com.wobbz.lsposedframework/files"
FRAMEWORK_LOG="/data/local/tmp/framework_selinux.log"

# Log function
log() {
  echo "$(date +"%Y-%m-%d %H:%M:%S") - $1" >> "$FRAMEWORK_LOG"
}

log "Starting SELinux context fixing for LSPosed Framework"

# Create necessary directories
mkdir -p "$FRAMEWORK_DATA_DIR"
mkdir -p "$FRAMEWORK_DATA_DIR/updates"
mkdir -p "$FRAMEWORK_DATA_DIR/overlays"
mkdir -p "$FRAMEWORK_DATA_DIR/logs"

# Set permissions
chmod 755 "$FRAMEWORK_DATA_DIR"
chmod 755 "$FRAMEWORK_DATA_DIR/updates"
chmod 755 "$FRAMEWORK_DATA_DIR/overlays"
chmod 755 "$FRAMEWORK_DATA_DIR/logs"

# Context mapping for different file types
# Format: path_pattern:context
CONTEXT_MAP=(
  "$FRAMEWORK_DATA_DIR:u:object_r:app_data_file:s0"
  "$FRAMEWORK_DATA_DIR/updates:u:object_r:app_data_file:s0"
  "$FRAMEWORK_DATA_DIR/overlays:u:object_r:overlay_service_app_data_file:s0"
  "$FRAMEWORK_DATA_DIR/logs:u:object_r:app_data_file:s0"
  "$FRAMEWORK_DATA_DIR/*.json:u:object_r:app_data_file:s0"
  "$FRAMEWORK_DATA_DIR/overlays/*.apk:u:object_r:system_file:s0"
)

# Fix contexts based on the mapping
for mapping in "${CONTEXT_MAP[@]}"; do
  path="${mapping%%:*}"
  context="${mapping#*:}"
  
  # Check if path exists (use wildcards for patterns)
  for actual_path in $path; do
    if [ -e "$actual_path" ]; then
      log "Setting context $context for $actual_path"
      chcon -R "$context" "$actual_path" || log "Failed to set context for $actual_path"
    fi
  done
done

# OnePlus OxygenOS specific context fixes
if [ -f "/system/build.prop" ] && grep -q "ro.oxygen.version" "/system/build.prop"; then
  log "Detected OxygenOS, applying specific context fixes"
  
  # OxygenOS has stricter contexts for overlay files
  for overlay in "$FRAMEWORK_DATA_DIR/overlays"/*.apk; do
    if [ -f "$overlay" ]; then
      log "Setting OxygenOS-specific context for overlay: $overlay"
      chcon "u:object_r:vendor_overlay_file:s0" "$overlay" || log "Failed to set OxygenOS context for $overlay"
    fi
  done
fi

# Check SELinux status
selinux_status=$(getenforce)
log "Current SELinux status: $selinux_status"

log "SELinux context fixing completed"
exit 0 