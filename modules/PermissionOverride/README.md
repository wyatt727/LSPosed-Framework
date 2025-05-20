# PermissionOverride Module

## Overview

PermissionOverride is an Xposed module for the Wobbz LSPosed Framework that allows fine-grained control over Android application permissions. It enables users to grant, revoke, or modify the permission status for specific applications, overriding the standard Android permission model. This module uses `io.github.libxposed.api.XposedInterface` for its core hooking functionalities.

**Warning**: Modifying application permissions can have significant security and privacy implications. Granting permissions inappropriately can expose sensitive data or system functions, while revoking essential permissions can cause applications to malfunction. Use this module responsibly and with a clear understanding of the permissions involved.

## Features

-   **Dynamic Permission Control**: Grant or revoke any Android permission for targeted applications at runtime.
-   **Fake Permission States**: Make an application believe it has a permission that it hasn't been granted, or vice-versa.
-   **Rule-Based Configuration**: Define permission override rules on a per-app and per-permission basis through a configuration file.
-   **Selective Targeting**: Apply overrides only to specified applications.
-   **Compatibility Focus**: Aims to work across different Android versions by targeting fundamental permission-checking mechanisms.

## How It Works

PermissionOverride implements the `io.github.libxposed.api.IXposedModule` interface. It applies its permission modifications by hooking key methods in the Android system involved in permission checking and granting.

1.  **System-Level Hooks (`onSystemServer`)**: 
    *   To globally affect permission checks, hooks are often placed in system services like `PackageManagerService` or `UserManagerService`. Methods responsible for checking or granting permissions (e.g., `checkPermission`, `grantRuntimePermission`) are targeted.
    *   `XposedInterface` (from `param.getXposed()`) is used to find and hook these methods.
    *   `Hooker` classes encapsulate the logic for each hooked method.
    *   When a permission check occurs system-wide, these hooks can intercept the call and return a modified result based on the module's configuration for the calling package and permission.
2.  **Application-Level Hooks (`onPackageLoaded`)**:
    *   For more specific overrides or to influence how an app perceives its own permissions, hooks can be applied within the application's process itself.
    *   Common targets include `Context.checkSelfPermission()`, `Context.checkCallingOrSelfPermission()`, `PackageManager.checkPermission()`.
    *   When a target app (`param.getAppInfo().getPackageName()`) loads, these hooks are activated.
    *   The hooks consult the PermissionOverride configuration. If a rule exists for the current app and the permission being checked, the hook will alter the result of the permission check (e.g., return `PackageManager.PERMISSION_GRANTED` or `PackageManager.PERMISSION_DENIED`).
3.  **Configuration Loading**: The module loads its rules from a configuration file (e.g., `settings.json`) which specifies which permissions to override for which apps, and what the new state should be (grant, deny, fake).

## Configuration

PermissionOverride rules are typically defined in a `settings.json` file. This file would contain a list of rules specifying the target application, the permission name, and the desired override action.

Example (conceptual `settings.json` structure):

```json
{
  "permissionOverrides": [
    {
      "packageName": "com.example.problematicapp",
      "permission": "android.permission.READ_CONTACTS",
      "action": "DENY" // or "GRANT" or "FAKE_GRANT"
    },
    {
      "packageName": "com.example.legacyapp",
      "permission": "android.permission.WRITE_EXTERNAL_STORAGE",
      "action": "GRANT"
    },
    {
      "packageName": "com.example.testapp",
      "permission": "android.permission.CAMERA",
      "action": "FAKE_DENY" // App thinks it's denied, but system might still grant if requested at runtime
    }
  ],
  "defaultAction": "PASSTHROUGH" // What to do if no rule matches
}
```

## Use Cases

-   Granting a required permission to an older application that doesn't correctly request it on newer Android versions.
-   Denying a specific permission (e.g., location access) to an application that is overly aggressive in its data collection, without uninstalling it.
-   Testing application behavior under different permission scenarios without manually toggling permissions in system settings.
-   Allowing applications modified by `IntentMaster` or `DeepIntegrator` to function if they now require permissions the original app did not have.

## Dependencies

-   **Xposed API**: `compileOnly project(':libxposed-api:api')`
    -   Uses `io.github.libxposed.api.XposedInterface` for framework interaction.
    -   Uses `io.github.libxposed.api.Hooker` for organizing hook implementations.
-   Android Framework APIs related to permissions (`android.content.pm.PackageManager`, `android.content.Context`, etc.).

## Permissions Declared

PermissionOverride itself typically does not require specific Android permissions in its manifest. Its capabilities derive from Xposed hooks.

## Scope

-   Targets `android` (system_server process) for system-wide permission hooks.
-   Targets specific applications as configured for app-level permission checks.

## Development Environment

This module is developed using Java 17. 