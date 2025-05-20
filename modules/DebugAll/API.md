# DebugAll Module API

## Overview

The DebugAll module does not expose a direct programmatic API for other Xposed modules to call. Its primary function is to modify `ApplicationInfo` flags for targeted applications based on its own configuration. This is achieved by using `io.github.libxposed.api.XposedInterface` and `io.github.libxposed.api.Hooker` classes to hook relevant system methods during the `onSystemServer` phase of the Xposed module lifecycle.

Interaction with this module is primarily through its settings, typically managed via a `settings.json` file.

## Configuration (via `settings.json`)

-   **`targetApps`**: 
    -   Type: `String[]` (JSON array of strings)
    -   Description: A list of package names for which debug flags should be modified. If this list is empty or not provided, the module may target all applications (confirm behavior based on implementation).
    -   Example: `["com.example.someapp", "org.another.debugtarget"]`

-   **`verboseLogging`**:
    -   Type: `Boolean`
    -   Description: If `true`, enables more detailed debug logging from the DebugAll module itself.
    -   Default: `false`

-   **`debugLevel`**:
    -   Type: `String`
    -   Description: Defines which set of debug flags to apply to target applications. Supported values:
        -   `"info"`: Applies `ApplicationInfo.FLAG_DEBUGGABLE`.
        -   `"debug"`: Applies `ApplicationInfo.FLAG_DEBUGGABLE | ApplicationInfo.FLAG_ALLOW_PROFILE` (or equivalent modern profiling flag).
        -   `"verbose"`: Applies `ApplicationInfo.FLAG_DEBUGGABLE | ApplicationInfo.FLAG_ALLOW_PROFILE | ApplicationInfo.FLAG_EXTERNAL_STORAGE_LEGACY` (Note: `FLAG_EXTERNAL_STORAGE_LEGACY` may be deprecated or ineffective on modern Android versions).
    -   Default: `"info"`

## Internal Hooking (Informational)

-   The module typically hooks methods involved in `ApplicationInfo` generation within the system server (process `android`). An example target is `toAppInfoWithoutState` in classes like `android.content.pm.PackageParser.Package` or its modern equivalents (e.g., `android.content.pm.parsing.pkg.PackageImpl`).
-   The hook, implemented as an `io.github.libxposed.api.Hooker`, modifies the `ApplicationInfo.flags` field for targeted packages before the `ApplicationInfo` object is finalized and used by the system.

## Development Environment

This module is developed using Java 17. 