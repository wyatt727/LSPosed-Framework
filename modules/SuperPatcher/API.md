# SuperPatcher Module API Documentation

## Overview

SuperPatcher is an advanced Xposed module for applying low-level and flexible patches. Its primary mode of operation is through declarative patches defined in its configuration files (`patches.json` or `settings.json`).

Additionally, SuperPatcher *may* expose a direct service API for other trusted modules within the Wobbz LSPosed Framework to dynamically request patch applications. This is contingent on a framework mechanism like a `FeatureManager` for service discovery and assumes the calling module has a legitimate need for such dynamic, low-level intervention that cannot be addressed by standard Xposed hooks within the calling module itself.

This module utilizes `io.github.libxposed.api.XposedInterface` and `io.github.libxposed.api.Hooker` for its own internal Xposed hooking needs when applying its configured patches.

**Warning**: SuperPatcher provides powerful, low-level patching. Incorrect use, whether via configuration or a service API, can easily lead to instability or crashes. This API should only be used by modules that understand the precise implications of the patches they request.

## 1. Configuration-Based Patching (Primary Mechanism)

SuperPatcher primarily applies patches defined in its own configuration files (e.g., `patches.json`). See the `SuperPatcher/README.md` for details on the structure of these patch definitions. This is the recommended way to utilize SuperPatcher's capabilities.

## 2. Service API (Hypothetical, for Dynamic Patch Requests)

If the Wobbz framework provides a service discovery mechanism (e.g., `FeatureManager`), SuperPatcher might offer methods for other modules to request patches at runtime.

**Note on Accessibility & Usage**:
-   The `SuperPatcherModule` class and its API methods must be public.
-   The calling module would likely need SuperPatcher as a direct dependency (e.g., `implementation project(':SuperPatcher')`) or use reflection.
-   This API is intended for scenarios where a module needs to programmatically decide to apply a very specific, low-level patch that SuperPatcher is uniquely equipped to handle (e.g., complex bytecode manipulation if SuperPatcher bundles ASM and exposes it this way, or a generic way to apply predefined named patches).
-   **Alternative**: Instead of a direct API, modules could also trigger SuperPatcher by modifying its configuration file and signaling SuperPatcher to reload it, if such a mechanism is implemented.

### Hypothetical Service Methods

```java
// Hypothetical: Get the SuperPatcher service via a Wobbz FeatureManager
// Object service = FeatureManager.getInstance(context).getService("com.wobbz.SuperPatcher.service");
// SuperPatcherModule superPatcherInstance = (SuperPatcherModule) service;

/**
 * Applies a patch defined by a PatchDefinition object.
 * @param patch The PatchDefinition object.
 * @return True if the patch request was accepted and attempted, false otherwise.
 */
// boolean success = superPatcherInstance.applyPatch(PatchDefinition patch);

/**
 * Requests a hook to be applied. 
 * This is more complex as it needs to bridge to libxposed-api's Hooker model.
 * SuperPatcher might internally create and register a Hooker instance based on this request.
 * Callbacks would be an issue unless simplified to mere replacement or very basic before/after.
 */
// boolean hookRequested = superPatcherInstance.requestDynamicHook(DynamicHookRequest request);
```

### `PatchDefinition` Structure (Conceptual)

This structure would be used if `applyPatch` is exposed. It mirrors what might be in a JSON configuration.

```java
public class PatchDefinition {
    String patchName;
    boolean enabled;
    String targetPackageName;
    String targetClassName;
    String targetMethodName; // Or fieldName
    String targetMethodSig;  // Or fieldSignature
    PatchActionType actionType;
    Map<String, Object> actionParameters; // e.g., replacement value, bytecode to inject

    public enum PatchActionType {
        REPLACE_METHOD_RETURN_CONSTANT,
        INJECT_BYTECODE_START, // Parameters would include bytecode string/array
        MODIFY_FIELD_VALUE,
        // ... other low-level patch types
    }

    // Builder pattern for construction
    public static class Builder { /* ... */ }
}
```

**Important Considerations for a Dynamic API**:
-   **Complexity**: Dynamically managing Xposed hooks (especially bytecode manipulation) requested by other modules is significantly more complex than modules managing their own static `Hooker` instances.
-   **Security/Stability**: The potential for destabilizing the system or target app is high.
-   **Alternatives**: Often, it's better for the calling module to implement its own hooks using `libxposed-api`. A service API from SuperPatcher would only be justified for very specialized, shared low-level patching routines it offers.

## Development Environment

This module is developed using Java 17. 