# DeepIntegrator Module API Documentation

## Overview

DeepIntegrator enables the exposure of hidden app components and facilitates deeper integration between apps by:

1. Making components (Activities, Services, ContentProviders, BroadcastReceivers) exported
2. Bypassing permission checks for components
3. Adding or modifying intent filters to enhance component discovery

This module utilizes `io.github.libxposed.api.XposedInterface` and `io.github.libxposed.api.Hooker` classes for its core Xposed hooking functionality (e.g., modifying `PackageManagerService` behavior). This document also provides details on how DeepIntegrator might be interacted with from other modules, particularly IntentMaster, through a direct service API if provided by the Wobbz framework.

## Key Features

- **Component Exposure**: Make any app component exported and accessible through Xposed hooks
- **Permission Bypass**: Remove or alter permission requirements for components via Xposed hooks
- **Intent Filter Injection**: Add custom intent filters to components via Xposed hooks
- **Component Access Logging**: Track component access attempts and modifications

## Integration with IntentMaster

IntentMaster and DeepIntegrator are designed to work together to provide complete control over app-to-app communication:

1. **DeepIntegrator**: Makes hidden or restricted components accessible by modifying their properties at runtime using Xposed
2. **IntentMaster**: Routes and modifies intents sent between components, potentially targeting components exposed by DeepIntegrator

### Workflow Example

1. Use DeepIntegrator to expose a hidden activity in App A
2. Use IntentMaster to:
   - Redirect intents from App B to the newly exposed activity in App A
   - Modify intent extras as needed before delivery
   - Log intent traffic for debugging

### Configuration Recommendations

For optimal integration between DeepIntegrator and IntentMaster:

1. First configure DeepIntegrator to expose the necessary components
2. Then configure IntentMaster rules to interact with those components
3. Use consistent component naming between both modules

## Service API (If Provided by Wobbz Framework)

DeepIntegrator *may* expose a direct service API for other modules within the Wobbz LSPosed Framework. This is dependent on a framework mechanism like a `FeatureManager`.

**Note on Accessibility**: For another module to call these methods directly:
-   The `DeepIntegratorModule` class and these methods must be public
-   The calling module would likely need DeepIntegrator as a direct dependency (e.g., `implementation project(':DeepIntegrator')` in Gradle, not just `compileOnly`) to resolve types directly, or use reflection if calling across different classloaders (which is complex and error-prone)

```java
// Hypothetical: Get the DeepIntegrator service via a Wobbz FeatureManager
// Object service = FeatureManager.getInstance(context).getService("com.wobbz.DeepIntegrator.service");
// DeepIntegratorModule moduleInstance = (DeepIntegratorModule) service;

// Example: Access logs (if moduleInstance is correctly obtained and method is public)
// List<ComponentAccessLog> logs = moduleInstance.getAccessLogs();

// Example: Add component configuration dynamically (if method is public)
/*
ComponentConfig config = new ComponentConfig(
    "com.example.app", 
    "com.example.app.HiddenActivity",
    ComponentConfig.TYPE_ACTIVITY
);
config.setExported(true);
config.setBypassPermissions(true);

IntentFilterConfig filter = new IntentFilterConfig();
filter.addAction("android.intent.action.VIEW");
filter.addCategory("android.intent.category.DEFAULT");
filter.addMimeType("image/*");
config.addIntentFilter(filter);

moduleInstance.addComponentConfig(config);
*/
```

**Important**: The primary way DeepIntegrator functions is via its Xposed hooks altering system behavior based on its own configuration (`settings.json`). The service API described here is a secondary, direct interaction pattern that depends on specific Wobbz framework features and careful dependency management.

## Interaction with SuperPatcher (Conceptual)

In scenarios where DeepIntegrator's component modification via `PackageManagerService` hooks isn't sufficient (e.g., due to hardcoded checks within an app), `SuperPatcher` *might* be used for more direct patching. Interaction with `SuperPatcher` would also likely depend on a similar service API provided by `SuperPatcher` itself.

**Note**: The `SuperPatcherModule.HookCallback()` shown in an older example would need to align with `SuperPatcher`'s actual API, which if also using `libxposed-api`, might involve registering `Hooker` instances or declarative patch definitions rather than direct callbacks.

```java
// Hypothetical: Get the SuperPatcher service
// Object superPatcherService = FeatureManager.getInstance(context).getService("com.wobbz.SuperPatcher.service");
// SuperPatcherModule superPatcherInstance = (SuperPatcherModule) superPatcherService;

// Example: Requesting a hook via a hypothetical updated SuperPatcher API
/*
PatchDefinition patch = new PatchDefinition.Builder()
    .setTargetPackage("com.example.app")
    .setTargetClass("com.example.app.ComponentHelper")
    .setTargetMethod("isComponentAccessible")
    .setTargetParams(new String[]{"android.content.ComponentName", "android.content.Context"})
    .setHookType(PatchDefinition.HookType.REPLACE_RETURN_CONSTANT)
    .setReplacementConstant(true)
    .build();
superPatcherInstance.applyPatch(patch);
*/
```

## Troubleshooting Component Exposure

If a component still cannot be accessed:
1. Verify DeepIntegrator's Xposed hooks are active for `system_server` and the target app
2. Check DeepIntegrator's internal logs (if any) to see if it attempted to modify the component
3. Check for internal permission checks or custom component resolution logic in the target app
4. Check for custom signature verification or classloader protection in the target app

## Security Considerations

DeepIntegrator can significantly affect app security boundaries. Use it carefully and consider:
1. Only expose components that are necessary, as defined in its configuration
2. Avoid bypassing critical security permissions unless absolutely required and understood
3. Test thoroughly

## Development Environment

This module is developed using Java 17 