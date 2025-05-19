# DebugAll Module API

## Overview

The DebugAll module does not expose a direct programmatic API for other modules to call. Its primary function is to modify `ApplicationInfo` flags for targeted applications based on its own configuration.

Interaction with this module is primarily through its settings, which are managed by the framework's `SettingsHelper`.

## Configuration (via `settings.json` for `com.wobbz.debugall`)

-   **`targetApps`**: 
    -   Type: `String[]` (JSON array of strings)
    -   Description: A list of package names for which debug flags should be modified. If this list is empty or not provided, the module may target all applications (behavior to be confirmed based on implementation details in `handleLoadPackage`).
    -   Example: `["com.example.someapp", "org.another.debugtarget"]`

-   **`verboseLogging`**:
    -   Type: `Boolean`
    -   Description: If `true`, enables more detailed debug logging from the DebugAll module itself.
    -   Default: `false`

-   **`debugLevel`**:
    -   Type: `String`
    -   Description: Defines which set of debug flags to apply to target applications. Supported values:
        -   `"info"`: Applies `ApplicationInfo.FLAG_DEBUGGABLE`.
        -   `"debug"`: Applies `ApplicationInfo.FLAG_DEBUGGABLE | ApplicationInfo.FLAG_ENABLE_PROFILING`.
        -   `"verbose"`: Applies `ApplicationInfo.FLAG_DEBUGGABLE | ApplicationInfo.FLAG_ENABLE_PROFILING | ApplicationInfo.FLAG_EXTERNAL_STORAGE_LEGACY`.
    -   Default: `"info"`

## Internal Workings (for informational purposes)

-   **Hooks**: `android.content.pm.PackageParser.Package.toAppInfoWithoutState(int)`.
-   **Context Initialization**: Similar to `NetworkGuardModule`, `DebugAllModule` attempts to initialize its context in `initZygote` using `XposedHelpers.getObjectField(startupParam, "modulePath")` which is problematic as `modulePath` is a String. This may lead to `AnalyticsManager` and `SettingsHelper` not being initialized correctly until/unless a valid context is obtained later (e.g., in `handleLoadPackage` or if `XposedBridge.sInitialApplication` is available).
-   **Debug Flags Map**: The `debugFlags` map (`Map<String, Integer>`) is initialized with flag names and values but is not directly used in the current logic for applying flags in `handleLoadPackage`. Instead, flags are applied based on a string comparison of `debugLevel`. This could be a point for future refinement to make flag application more dynamic based on settings.

## Hot Reloading

Implements `IHotReloadable`. The `onHotReload()` method:
- Cleans up existing Xposed hooks.
- Reloads settings via `loadSettings()`. 