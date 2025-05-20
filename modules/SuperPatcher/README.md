# SuperPatcher Module

## Overview

SuperPatcher is an advanced Xposed module for the Wobbz LSPosed Framework designed for applying low-level and highly flexible patches to applications and the Android framework. It provides capabilities for direct bytecode manipulation, dynamic modification of class structures, and implementing complex hooking scenarios that may go beyond standard method hooking. This module utilizes `io.github.libxposed.api.XposedInterface` and is intended for developers who require fine-grained control over code execution.

**Warning**: This module provides powerful, low-level patching capabilities. Incorrectly configured or applied patches can easily lead to application instability, crashes, or system malfunctions. Use with extreme caution and a thorough understanding of the target code.

## Features

-   **Bytecode Manipulation**: Potential to modify method bodies, add or remove instructions at runtime (e.g., using a bundled ASM or similar library if `libxposed-api` supports class transformation callbacks).
-   **Dynamic Class Modification**: Add fields or methods to existing classes, or change class hierarchies (with limitations).
-   **Flexible Hooking**: Define custom hook logic that can modify execution flow in complex ways.
-   **Configuration-Driven Patching**: Define patches via external configuration files, allowing for updates without recompiling the module.
-   **Targeted Application**: Apply patches selectively to specific applications or processes.

## How It Works

SuperPatcher implements `io.github.libxposed.api.IXposedModule`. Patches are typically applied when target application packages are loaded.

1.  **Initialization (`onPackageLoaded`)**: When a target application is loaded (`param.getAppInfo().getPackageName()`):
    *   SuperPatcher loads its patch configurations, which specify the classes, methods, and transformations to apply.
    *   It uses `XposedInterface` (`param.getXposed()`) for standard method hooking via `Hooker` classes if simple entry/exit modifications are needed.
2.  **Class Transformation (Hypothetical)**:
    *   If `libxposed-api` or the Xposed framework provides a mechanism for class file transformation (e.g., similar to `IXposedHookLoadPackage.handleLoadPackage` in original Xposed with a `ClassFileTransformer`), SuperPatcher would register a transformer.
    *   When a target class is loaded, the transformer receives its bytecode. It then applies the defined modifications (e.g., using ASM to inject code, modify methods) before the class is defined by the classloader.
3.  **Method Hooking for Patching**: For less invasive patches or to trigger more complex logic:
    *   Standard `Hooker` classes can be used to intercept method calls.
    *   Within the hook methods (`@BeforeHook`, `@AfterHook`, `@ReplaceHook`), SuperPatcher can execute custom logic, call other methods, or alter results based on its patch rules.
4.  **Patch Configuration**: Patches are defined externally, likely in a `settings.json` or a dedicated patch definition format. This configuration would specify:
    *   Target class and method (or field).
    *   Type of patch (e.g., bytecode injection, method replacement, field value change).
    *   The actual code or data for the patch.

## Configuration

Patch configurations for SuperPatcher would be detailed and specific, likely residing in a `patches.json` or `settings.json` file. The structure would need to be robust enough to define various types of patches.

Example (conceptual `patches.json` structure):

```json
{
  "patches": [
    {
      "name": "Modify Login Check",
      "enabled": true,
      "packageName": "com.example.app",
      "className": "com.example.app.AuthService",
      "methodName": "isUserPremium",
      "methodSig": "()Z", // Returns boolean
      "action": "REPLACE_RETURN_TRUE" // Custom action for SuperPatcher to interpret
    },
    {
      "name": "Add Debug Log to Method",
      "enabled": true,
      "packageName": "com.example.anotherapp",
      "className": "com.example.anotherapp.Utils",
      "methodName": "processData",
      "methodSig": "(Ljava/lang/String;)V",
      "action": "INJECT_BYTECODE_START",
      "bytecode": "GETSTATIC java/lang/System out Ljava/io/PrintStream; LDC \"SuperPatcher: processData called\"; INVOKEVIRTUAL java/io/PrintStream println (Ljava/lang/String;)V" // Example ASM-like instructions
    }
  ]
}
```

## Use Cases

-   Bypassing specific security checks or feature restrictions within an application.
-   Injecting custom logging or debugging code into opaque methods.
-   Dynamically altering application behavior for testing or customization.
-   Implementing workarounds for bugs in third-party libraries where source code is unavailable.

## Dependencies

-   **Xposed API**: `compileOnly project(':libxposed-api:api')`
    -   Uses `io.github.libxposed.api.XposedInterface`.
    -   Uses `io.github.libxposed.api.Hooker`.
    -   May require specific APIs for class transformation if supported by `libxposed-api`.
-   **Bytecode Manipulation Library (Optional)**: Could bundle a library like ASM if class transformation is a core feature and not directly provided by the Xposed API.

## Permissions Declared

SuperPatcher typically requires no specific Android permissions in its manifest.

## Scope

Targets applications specified in its patch configurations. Hooks may be applied within the app's process during `onPackageLoaded`.

## Development Environment

This module is developed using Java 17. 