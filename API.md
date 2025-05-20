# Wobbz LSPosed Modular Framework API

## Overview

This document describes the Application Programming Interfaces (APIs), annotations, and conventions provided by the Wobbz LSPosed Modular Framework itself. This framework builds upon the `libxposed-api` (included locally in this project) and provides additional structures, utilities, and lifecycle enhancements to streamline module development.

For the API of the underlying Xposed hooking capabilities, modules should refer to the `io.github.libxposed.api` (e.g., `XposedInterface`, `Hooker`, `IXposedModule` and its lifecycle methods like `onPackageLoaded`, `onSystemServerLoaded`, etc.).

Individual modules (`/modules/*`) may also have their own `API.md` files describing their specific functionalities or configuration schemas.

## 1. Core Framework Annotations

These annotations are processed by the framework to understand module metadata and capabilities.

### `@XposedPlugin`

-   **Purpose**: Defines essential metadata for an Xposed module. This annotation replaces the traditional `xposed_init` file and `module.prop` for basic information, enabling compile-time validation and easier management.
-   **Usage**: Apply to your main module class that implements `io.github.libxposed.api.IXposedModule`.
-   **Attributes**:
    -   `name` (String, required): The human-readable name of the module.
    -   `version` (String, required): The version string of the module (e.g., "1.0.0").
    -   `description` (String, optional): A brief description of what the module does.
    -   `author` (String, optional): The author(s) of the module.
    -   `scope` (String[], optional): An array of package names that this module primarily targets. This helps in organizing and potentially filtering modules in a management UI.
        Example: `scope = {"com.android.systemui", "com.example.app"}`
    -   `minFrameworkVersion` (int, optional): The minimum version of the Wobbz LSPosed Framework this module is compatible with (if versioning is implemented for the framework itself).

```java
import io.github.libxposed.api.IXposedModule;
// Assuming annotations are in a package like com.wobbz.framework.annotations
import com.wobbz.framework.annotations.XposedPlugin;
import com.wobbz.framework.annotations.HotReloadable;

@XposedPlugin(
    name = "My Awesome Module",
    version = "1.0.1",
    description = "Demonstrates framework features.",
    author = "WobbzDev",
    scope = {"com.android.settings"}
)
@HotReloadable
public class MyAwesomeModule implements IXposedModule {
    // ... module implementation ...
}
```

### `@HotReloadable`

-   **Purpose**: Marks a module as capable of supporting hot-reloading of its code.
-   **Usage**: Apply to your main module class alongside `@XposedPlugin`.
-   **Effect**: When this annotation is present, the framework will attempt to call the `onHotReload(String reloadedPackage)` method on the module instance after new code has been pushed to the device without a full reboot.

## 2. Framework-Specific Lifecycle Methods

### `onHotReload(String reloadedPackage)`

-   **Signature**: `void onHotReload(String reloadedPackage) throws Throwable;` (This might be part of a framework-specific interface that modules annotated with `@HotReloadable` should implement, or invoked via reflection).
-   **Purpose**: Called by the framework after a module's code has been updated on the fly.
-   **Implementation Requirements**:
    1.  **Unhook**: The module MUST unhook all its existing Xposed hooks.
    2.  **Clean State**: Release any resources, clear caches, or reset state that might be stale from the previous code version.
    3.  **Re-apply Hooks**: Re-initialize and re-apply all necessary Xposed hooks using the new code.
    4.  **Reload Configuration**: If necessary, reload any external configuration files.
-   **`reloadedPackage` Parameter**: This parameter *may* indicate the specific application package context for which the reload is being triggered, allowing for more targeted re-hooking if the module hooks multiple packages. If the hot-reload affects the entire module globally (e.g., system_server hooks), this parameter might be less relevant or a special value.

Refer to the main `README.md` section "Hook Implementation Patterns" for an example of how to manage hooks for hot-reloading.

## 3. Inter-Module Communication & Services (Conceptual `FeatureManager`)

The Wobbz LSPosed Framework *may* provide a centralized mechanism for modules to expose and consume services from one another, going beyond typical Xposed interactions. This is often facilitated by a `FeatureManager` or a similar service locator pattern.

### `FeatureManager` (Hypothetical)

-   **Purpose**: A central registry where modules can publish their services (API implementations) and other modules can look them up.
-   **Registration (Conceptual)**: A module offering a service would register an instance of its API implementation with the `FeatureManager` under a unique service name.
    ```java
    // Inside MyServiceProviderModule
    // MyServiceApi implementation = new MyServiceImpl();
    // featureManager.registerService("com.wobbz.MyService", implementation);
    ```
-   **Consumption (Conceptual)**:
    ```java
    // Inside MyServiceConsumerModule
    // MyServiceApi myService = (MyServiceApi) featureManager.getService("com.wobbz.MyService");
    // if (myService != null) {
    //     myService.performAction();
    // }
    ```

**Considerations for Direct Inter-Module APIs**:
-   **Dependencies**: Consuming modules would typically need a compile-time dependency on the API interfaces of the providing modules (e.g., `implementation project(':ProviderModuleApiJar')` or directly `implementation project(':ProviderModule')` if types are shared carefully).
-   **Versioning**: Service contracts (APIs) should be versioned to avoid runtime compatibility issues.
-   **Coupling**: Direct API calls create tighter coupling than traditional Xposed hook interactions. This should be used when the benefits of a direct, structured API outweigh the increased coupling.
-   **Availability**: Service consumption logic must handle cases where the provider module or service is not available or active.

Modules like `DeepIntegrator` and `SuperPatcher` have `API.md` files that discuss such hypothetical service APIs, illustrating how they might be structured.

## 4. Shared Framework Utilities (If Provided)

The framework might offer centralized instances or helper classes for common tasks:

-   **`LoggingHelper`**: A standardized logging utility that might automatically tag logs with the module name or respect global logging levels.
-   **`SettingsHelper`**: A utility to simplify loading and accessing module settings from their respective `settings.json` files, potentially with type safety or caching.

Usage of these would be documented here if they are concrete framework components designed for shared use.

## 5. Relationship with `libxposed-api`

-   The Wobbz LSPosed Framework uses `libxposed-api` (version included in `libxposed-api/`) as the foundational library for all Xposed hooking operations.
-   Modules **must** use `io.github.libxposed.api.XposedInterface` for finding classes/methods and applying hooks (`Hooker` implementations).
-   Modules **must** implement `io.github.libxposed.api.IXposedModule` and its standard lifecycle methods (`onInit`, `onZygote`, `onSystemServerLoaded`, `onPackageLoaded`).
-   This Wobbz framework builds *additional* conventions, annotations (like `@XposedPlugin`, `@HotReloadable`), and potential services (like `FeatureManager`, `onHotReload` lifecycle) *on top of* the base `libxposed-api`. 