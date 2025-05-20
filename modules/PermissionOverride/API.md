# PermissionOverride Module API

## Overview

The PermissionOverride module does not expose a direct programmatic API for other Xposed modules to dynamically request permission changes. Its core function is to modify Android's permission system for targeted applications based on its own declarative configuration defined in a `settings.json` file.

This module utilizes `io.github.libxposed.api.XposedInterface` and `io.github.libxposed.api.Hooker` classes to hook relevant system methods (e.g., in `PackageManagerService`, `Context`) to enforce these configured permission overrides. Hooks are typically active in `onSystemServer` for system-wide effects and `onPackageLoaded` for app-specific contexts.

**Warning**: Modifying application permissions can have significant security and privacy implications. Configure this module with a clear understanding of the permissions and potential consequences.

## Configuration (via `settings.json`)

PermissionOverride rules are defined in a `settings.json` file within the module's configuration directory. The primary element is `permissionOverrides`, an array of rule objects.

### Rule Object Structure

Each object in the `permissionOverrides` array defines a rule with the following properties:

-   **`packageName`** (String, Required):
    The package name of the application to which this rule applies.
    Example: `"com.example.targetapp"`

-   **`permission`** (String, Required):
    The full name of the Android permission to override.
    Example: `"android.permission.READ_CONTACTS"`

-   **`action`** (String, Required):
    The action to take for this permission. Supported values:
    -   `"GRANT"`: Force grant this permission to the application.
    -   `"DENY"`: Force deny this permission to the application, even if requested or previously granted.
    -   `"FAKE_GRANT"`: (Conceptual) Make the app believe it has the permission, but the underlying system behavior might still be restricted if the permission isn't truly granted at the OS level. Use with caution and understanding of its effects.
    -   `"FAKE_DENY"`: (Conceptual) Make the app believe it is denied the permission, which might alter its behavior, even if the permission could be granted by the user. 
    Example: `"GRANT"`

-   **`enabled`** (Boolean, Optional):
    Set to `true` to enable the rule, `false` to disable it. If omitted, defaults to `true`.
    Example: `true`

### Example `settings.json`

```json
{
  "permissionOverrides": [
    {
      "packageName": "com.example.problematicapp",
      "permission": "android.permission.ACCESS_FINE_LOCATION",
      "action": "DENY",
      "enabled": true
    },
    {
      "packageName": "com.example.legacyapp",
      "permission": "android.permission.WRITE_EXTERNAL_STORAGE",
      "action": "GRANT"
    },
    {
      "packageName": "com.sample.testfeatures",
      "permission": "android.permission.CAMERA",
      "action": "FAKE_DENY",
      "enabled": false
    }
  ],
  "globalSettings": {
    "verboseLogging": false,
    "defaultGrantUndefinedPermissions": false // Example global setting
  }
}
```

### Global Settings (Optional)

The `globalSettings` object can contain overall settings for the module, such as:
-   `verboseLogging` (Boolean): Enable detailed logging from PermissionOverride.
-   Other module-specific global behaviors (e.g., default actions for unconfigured permissions, though this should be used with extreme caution).

## Internal Hooking Mechanism (Informational)

-   PermissionOverride hooks key methods in the Android framework related to permission checking and granting.
-   System-level hooks (in `onSystemServer`) often target services like `PackageManagerService` (e.g., `checkPermission`, `grantRuntimePermission`).
-   Application-level hooks (in `onPackageLoaded`) target methods like `Context.checkSelfPermission()`, `PackageManager.checkPermission()` within the app's process.
-   The logic within these hooks consults the loaded configuration to determine if an override should be applied for the current package and permission check.

## Development Environment

This module is developed using Java 17. 