# DeepIntegrator Module

## Overview

DeepIntegrator is an Xposed module for the Wobbz LSPosed Framework designed to enable advanced integration with and exposure of internal or normally inaccessible Android application components. It allows other modules or applications to interact with components that are not explicitly exported, by dynamically modifying system checks or component resolution mechanisms. This module utilizes the `io.github.libxposed.api.XposedInterface` for its core hooking functionalities.

## Features

-   **Component Exposure**: Dynamically makes non-exported activities, services, and broadcast receivers accessible.
-   **Intent Interception & Modification**: May hook into intent resolution or dispatching mechanisms (`PackageManager`, `ActivityManagerService`) to reroute or modify intents targeting internal components.
-   **Dynamic Proxying**: Potentially creates proxy components to facilitate interaction with hidden components.
-   **Permission Bypass (Careful Usage)**: Could be used to bypass certain signature or permission checks that would normally prevent access to internal components (use with extreme caution and understanding of security implications).
-   **Extensibility**: Designed to be used by other modules (like IntentMaster) that need to route intents or interact with components exposed by DeepIntegrator.

## How It Works

DeepIntegrator implements the `io.github.libxposed.api.IXposedModule` interface. Its core functionality is typically initialized during the `onSystemServer` or `onPackageLoaded` lifecycle methods.

1.  **Targeted Hooking**:
    *   DeepIntegrator uses `XposedInterface` (obtained via `param.getXposed()`) to hook critical system services involved in component management and intent resolution. This often includes methods within:
        *   `PackageManagerService` (e.g., for resolving component information, checking export status).
        *   `ActivityManagerService` (e.g., for activity/service starting, broadcast dispatch).
    *   Specific hooks might target methods like `PackageManager.getActivityInfo()`, `PackageManager.resolveIntent()`, or internal checks within AMS.
2.  **Hooker Classes**: Hooks are organized into `io.github.libxposed.api.Hooker` classes for better structure. Each `Hooker` class focuses on a specific aspect of component integration.
3.  **Dynamic Modification**:
    *   When a hooked method is called (e.g., the system tries to resolve an intent or check if a component is exported), DeepIntegrator's hooks can alter the return value or modify parameters.
    *   For example, it might change the `exported` flag of a component's info object on-the-fly or trick the system into believing a component is accessible.
4.  **Configuration-Driven**: The specific components to expose or rules for integration might be defined in a configuration file (e.g., `settings.json`) loaded by the module.

## Configuration

Configuration for DeepIntegrator would typically reside in a `settings.json` file within its module directory. This file might define:

-   A list of components (package name + class name) to expose.
-   Rules for modifying component properties (e.g., overriding the `exported` flag).
-   Target applications or system processes where these integrations should apply.

Example (hypothetical `settings.json` structure):

```json
{
  "exposedComponents": [
    {
      "packageName": "com.example.app",
      "className": "com.example.app.HiddenActivity",
      "expose": true
    },
    {
      "packageName": "com.android.systemui",
      "className": "com.android.systemui.SecretService",
      "forceExported": true
    }
  ],
  "integrationRules": {
    "com.example.anotherapp": {
      "allowAccessToInternalComponentsOf": ["com.example.targetapp"]
    }
  }
}
```

## Use Cases

-   Allowing a third-party launcher to start an unexported activity of an app.
-   Enabling a utility app to bind to an internal service of another application for advanced control.
-   Facilitating communication between apps by exposing specific broadcast receivers that are normally private.
-   Working in tandem with `IntentMaster` to route intents to these newly exposed components.

## Dependencies

-   **Xposed API**: `compileOnly project(':libxposed-api:api')`
    -   Uses `io.github.libxposed.api.XposedInterface` for framework interaction.
    -   Uses `io.github.libxposed.api.Hooker` for organizing hook implementations.
-   Relies on Android Framework APIs related to `PackageManager`, `ActivityManagerService`, and component lifecycle.

## Permissions Declared

DeepIntegrator itself typically does not require specific Android permissions in its manifest, as its power comes from Xposed hooks running within target processes or the system server.

## Scope

DeepIntegrator primarily targets:
-   `android` (system_server process) for hooks related to `PackageManagerService` and `ActivityManagerService`.
-   Specific applications whose components need to be exposed or interacted with, as defined by its configuration.

## Development Environment

This module is developed using Java 17. 