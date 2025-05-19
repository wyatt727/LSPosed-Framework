# DebugAll Module

## Overview

The DebugAll module for the Wobbz LSPosed Framework allows users to dynamically set debugging flags for selected applications. This can be useful for enabling debugging features, profiling, or other development-related options on apps that normally have them disabled.

## Features

-   **Selective Application Targeting**: Users can specify which applications should have their debug flags modified.
-   **Debug Flag Configuration**: Applies standard Android `ApplicationInfo` flags:
    -   `FLAG_DEBUGGABLE`: Makes the application debuggable.
    -   `FLAG_ENABLE_PROFILING`: Enables profiling capabilities.
    -   `FLAG_EXTERNAL_STORAGE_LEGACY`: (Potentially) enables legacy external storage behavior.
-   **Configurable Debug Levels**: Supports different levels (`info`, `debug`, `verbose`) which determine which set of flags are applied.
    -   `info`: Sets `FLAG_DEBUGGABLE`.
    -   `debug`: Sets `FLAG_DEBUGGABLE` and `FLAG_ENABLE_PROFILING`.
    -   `verbose`: Sets `FLAG_DEBUGGABLE`, `FLAG_ENABLE_PROFILING`, and `FLAG_EXTERNAL_STORAGE_LEGACY`.
-   **Settings Integration**: Loads configuration (target apps, verbose logging, debug level) from the framework's `SettingsHelper`.
-   **Hot-Reload Support**: Allows the module to be updated and settings reloaded at runtime.
-   **Analytics Integration**: Uses `AnalyticsManager` to track hook performance (if available).

## How It Works

1.  **Initialization (`initZygote`)**:
    *   Attempts to initialize `mContext`, `AnalyticsManager`, and `SettingsHelper`.
    *   Loads settings for target applications, verbose logging, and the desired debug level.
2.  **Package Loading (`handleLoadPackage`)**:
    *   Checks if the currently loading package (`lpparam.packageName`) is among the `targetApps`.
    *   If targeted, it hooks the `toAppInfoWithoutState` method in `android.content.pm.PackageParser.Package`.
    *   In the `afterHookedMethod` callback, it modifies the `ApplicationInfo` result by OR-ing the configured debug flags based on the `debugLevel` setting.
3.  **Hot Reload (`onHotReload`)**:
    *   Unhooks all existing method hooks.
    *   Reloads settings.

## Configuration

Configuration is managed via `settings.json` for this module (expected at `com.wobbz.debugall/settings.json` via `SettingsHelper`):

-   `targetApps` (String Array): A list of package names to target (e.g., `["com.example.app1", "com.example.app2"]`). If empty, all apps are targeted (use with caution).
-   `verboseLogging` (Boolean): Enables more detailed logging from this module if `true`.
-   `debugLevel` (String): Determines the set of debug flags to apply. Can be one of:
    -   `"info"`: Enables `FLAG_DEBUGGABLE`.
    -   `"debug"`: Enables `FLAG_DEBUGGABLE` and `FLAG_ENABLE_PROFILING`.
    -   `"verbose"`: Enables `FLAG_DEBUGGABLE`, `FLAG_ENABLE_PROFILING`, and `FLAG_EXTERNAL_STORAGE_LEGACY`.
    (Default is `"info"` if not specified).

## Dependencies

-   `com.wobbz.framework.IHotReloadable`
-   `com.wobbz.framework.IModulePlugin`
-   `com.wobbz.framework.analytics.AnalyticsManager`
-   `com.wobbz.framework.development.LoggingHelper`
-   `com.wobbz.framework.ui.models.SettingsHelper`

## Permissions Declared

-   `android.permission.READ_LOGS` (though not explicitly used in the current code for reading, only for Xposed logging).

## Scope

Targets: `android`, `com.android.systemui` (and any app specified in `targetApps`). 