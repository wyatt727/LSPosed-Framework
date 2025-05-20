# DebugAll Module

## Overview

The DebugAll module for the Wobbz LSPosed Framework allows users to dynamically set debugging flags for selected applications. This can be useful for enabling debugging features, profiling, or other development-related options on apps that normally have them disabled. It utilizes the `io.github.libxposed.api.XposedInterface` for its core hooking capabilities.

## Features

-   **Selective Application Targeting**: Users can specify which applications should have their debug flags modified.
-   **Debug Flag Configuration**: Applies standard Android `ApplicationInfo` flags:
    -   `FLAG_DEBUGGABLE`: Makes the application debuggable.
    -   `FLAG_ENABLE_PROFILING`: Enables profiling capabilities.
    -   `FLAG_EXTERNAL_STORAGE_LEGACY`: (Potentially) enables legacy external storage behavior.
-   **Configurable Debug Levels**: Supports different levels (`info`, `debug`, `verbose`) which determine which set of flags are applied:
    -   `info`: Sets `FLAG_DEBUGGABLE`.
    -   `debug`: Sets `FLAG_DEBUGGABLE` and `FLAG_ENABLE_PROFILING`.
    -   `verbose`: Sets `FLAG_DEBUGGABLE`, `FLAG_ENABLE_PROFILING`, and `FLAG_EXTERNAL_STORAGE_LEGACY`.
-   **Settings Integration**: Loads configuration (target apps, verbose logging, debug level) from a settings mechanism (e.g., a `settings.json` file).

## How It Works

DebugAll implements the `io.github.libxposed.api.IXposedModule` interface. Its primary logic for modifying application flags typically executes when the system server is loading package information.

1.  **Initialization (`onSystemServer`)**: This method is called when the system server process is started.
    *   The module loads its settings, including the list of `targetApps` and the desired `debugLevel`.
    *   It uses `XposedInterface` (obtained via `param.getXposed()`) to find and hook methods involved in package parsing and `ApplicationInfo` generation. A common target is a method within `android.content.pm.PackageParser` (or its modern equivalents like `android.content.pm.parsing.pkg.PackageImpl`) that finalizes the `ApplicationInfo` object, such as `toAppInfoWithoutState` or similar.
2.  **Hooker Implementation**: The hook logic is encapsulated in an `io.github.libxposed.api.Hooker` class.
    *   An `@AfterHook` is typically used on the target method (e.g., `toAppInfoWithoutState`).
    *   Inside the hook, it retrieves the `ApplicationInfo` object from `param.getResult()`.
    *   It checks if the package name associated with this `ApplicationInfo` (e.g., `param.getArgs()[0].packageName` if the first argument to `toAppInfoWithoutState` is `PackageParser.Package` or similar, or by accessing `ApplicationInfo.packageName`) is in the `targetApps` list (or if all apps are targeted).
    *   If the application is targeted, the hook modifies the `ApplicationInfo.flags` field by OR-ing the appropriate debug flags based on the configured `debugLevel`.
3.  **Settings Reload**: If settings can be changed at runtime, a mechanism to reload them and potentially re-apply hooks or update behavior would be needed, though the new API does not have a direct `onHotReload` equivalent. This might involve broadcast listeners or other IPC if a UI is present.

## Configuration

Configuration is typically managed via a `settings.json` file for this module:

-   `targetApps` (String Array): A list of package names to target (e.g., `["com.example.app1", "com.example.app2"]`). If empty or not present, the module might target all apps (use with extreme caution) or have a default behavior.
-   `verboseLogging` (Boolean): Enables more detailed logging from this module if `true`.
-   `debugLevel` (String): Determines the set of debug flags to apply. Can be one of:
    -   `"info"`: Enables `FLAG_DEBUGGABLE`.
    -   `"debug"`: Enables `FLAG_DEBUGGABLE` and `FLAG_ENABLE_PROFILING`.
    -   `"verbose"`: Enables `FLAG_DEBUGGABLE`, `FLAG_ENABLE_PROFILING`, and `FLAG_EXTERNAL_STORAGE_LEGACY`.
    (Default is usually `"info"` if not specified).

## Code Example: Hooking a Package Parsing Method

Below is a conceptual example. The exact class and method names for package parsing can change between Android versions.

```java
package com.wobbz.debugall.hooks;

import android.content.pm.ApplicationInfo;
import io.github.libxposed.api.Hooker;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.annotations.AfterHook;
import java.util.List;

// Assuming PackageParser.Package class or similar structure exists
// import android.content.pm.PackageParser.Package;

public class AppInfoFlagsHook implements Hooker {

    private final List<String> targetApps;
    private final String debugLevel;
    // Potentially LoggingHelper or other dependencies

    public AppInfoFlagsHook(List<String> targetApps, String debugLevel) {
        this.targetApps = targetApps;
        this.debugLevel = debugLevel;
    }

    // The target class and method will vary based on Android version.
    // This is a placeholder for a method that processes/generates ApplicationInfo.
    // e.g., @AfterHook(target = android.content.pm.PackageParser.Package.class, method = "toAppInfoWithoutState")
    // Or for newer Android versions, it might be in android.content.pm.parsing.pkg.PackageImpl
    @AfterHook(targetName = "android.content.pm.parsing.pkg.PackageImpl", method = "toAppInfoWithoutState")
    public static void afterToAppInfoWithoutState(XposedInterface.AfterHookParam param) {
        ApplicationInfo appInfo = (ApplicationInfo) param.getResult();
        if (appInfo == null) return;

        // Get instance of the Hooker
        AppInfoFlagsHook hookInstance = (AppInfoFlagsHook) param.getHooker();

        // Check if this app should be processed
        // The way to get packageName might differ based on actual hooked method's args
        // String packageName = ((Package) param.getThisObject()).packageName; 
        String packageName = appInfo.packageName;

        if (hookInstance.targetApps.isEmpty() || hookInstance.targetApps.contains(packageName)) {
            int flagsToAdd = 0;
            switch (hookInstance.debugLevel.toLowerCase()) {
                case "info":
                    flagsToAdd |= ApplicationInfo.FLAG_DEBUGGABLE;
                    break;
                case "debug":
                    flagsToAdd |= ApplicationInfo.FLAG_DEBUGGABLE;
                    flagsToAdd |= ApplicationInfo.FLAG_ALLOW_PROFILE;
                    break;
                case "verbose":
                    flagsToAdd |= ApplicationInfo.FLAG_DEBUGGABLE;
                    flagsToAdd |= ApplicationInfo.FLAG_ALLOW_PROFILE; 
                    // FLAG_EXTERNAL_STORAGE_LEGACY is deprecated and might not be available
                    // or have the intended effect on modern Android versions.
                    // flagsToAdd |= ApplicationInfo.FLAG_EXTERNAL_STORAGE_LEGACY;
                    break;
            }
            appInfo.flags |= flagsToAdd;
            param.setResult(appInfo); // Not strictly necessary if modifying object in place, but good practice.
        }
    }
}

// In your main module class implementing IXposedModule:
// public class DebugAllModule implements IXposedModule {
//     @Override
//     public void onSystemServerLoaded(XposedInterface.SystemServerLoadedParam param) {
//         List<String> targetApps = ...; // Load from settings
//         String debugLevel = ...;    // Load from settings
//         param.getXposed().hook(new AppInfoFlagsHook(targetApps, debugLevel), null /* System server doesn't have specific app classloader */);
//     }
//     // ... other IXposedModule methods
// }
```

## Dependencies

-   **Xposed API**: `compileOnly project(':libxposed-api:api')`
    -   Uses `io.github.libxposed.api.XposedInterface` for framework interaction.
    -   Uses `io.github.libxposed.api.Hooker` for organizing hook implementations.
-   Android Framework APIs (e.g., `android.content.pm.ApplicationInfo`).

## Permissions Declared

Typically, none required in the manifest for this module's core functionality.

## Scope

Targets: `android` (system_server process) for modifying `ApplicationInfo` globally before apps are fully started. The effects are then seen by the targeted applications.

## Development Environment

This module is developed using Java 17. 