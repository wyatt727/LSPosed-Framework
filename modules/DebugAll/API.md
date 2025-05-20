# DebugAll Module API

## Overview

The DebugAll module does not expose a direct programmatic API for other Xposed modules to call. Its primary function is to modify `ApplicationInfo` flags for targeted applications based on its own configuration. This is achieved by using `io.github.libxposed.api.XposedInterface` and `io.github.libxposed.api.Hooker` classes to hook relevant system methods during the `onSystemServerLoaded` phase of the Xposed module lifecycle.

Interaction with this module is primarily through its settings, typically managed via a `settings.json` file.

## Configuration (via `settings.json`)

-   **`targetApps`**: 
    -   Type: `String[]` (JSON array of strings)
    -   Description: A list of package names for which debug flags should be modified. If this list is empty or not provided, the module targets all applications.
    -   Example: `["com.example.someapp", "org.another.debugtarget"]`

-   **`verboseLogging`**:
    -   Type: `Boolean`
    -   Description: If `true`, enables more detailed debug logging from the DebugAll module itself.
    -   Default: `false`

-   **`debugLevel`**:
    -   Type: `String`
    -   Description: Defines which set of debug flags to apply to target applications. Supported values:
        -   `"info"`: Applies `ApplicationInfo.FLAG_DEBUGGABLE`.
        -   `"debug"`: Applies `ApplicationInfo.FLAG_DEBUGGABLE | ApplicationInfo.FLAG_PROFILEABLE_BY_SHELL` (modern profiling flag).
        -   `"verbose"`: Applies `ApplicationInfo.FLAG_DEBUGGABLE | ApplicationInfo.FLAG_PROFILEABLE_BY_SHELL | ApplicationInfo.FLAG_LEGACY_STORAGE` (Note: This is primarily for compatibility with older Android versions).
    -   Default: `"info"`

## Implementation Details

### Hooks and Targets

The module hooks methods involved in `ApplicationInfo` generation within the system server (process `android`). Specifically:

- On modern Android versions (12+): Methods in `android.content.pm.parsing.pkg.PackageImpl` or `com.android.server.pm.ComputerEngine` that generate the `ApplicationInfo`.
- On older Android versions: `android.content.pm.PackageParser$Package.toAppInfoWithoutState(int)` or equivalent methods.

The hooks are implemented as `io.github.libxposed.api.Hooker` implementations that intercept and modify the `ApplicationInfo.flags` field before the `ApplicationInfo` object is returned and used by the system.

### Flag Constants

The module uses the following standard Android flag constants:

- `ApplicationInfo.FLAG_DEBUGGABLE` (0x00000002): Enables debugging features for the app
- `ApplicationInfo.FLAG_PROFILEABLE_BY_SHELL` (0x00000004): Allows profiling from shell/ADB
- `ApplicationInfo.FLAG_LEGACY_STORAGE` (0x00400000): Grants legacy external storage behavior on newer Android versions

These flags are applied after checking if the application is in the targeted list, ensuring modifications are applied only to specified apps.

## Hot Reload Support

This module implements `@HotReloadable` for dynamic configuration updates. When hot reloaded:

1. All active hooks are removed
2. Settings are reloaded from storage
3. Hooks are re-applied with the new configuration

## Development Environment

This module is developed using Java 17 and implements:
- `IModulePlugin` for framework integration
- `IHotReloadable` for configuration reloading
- Modern `io.github.libxposed.api` interfaces 