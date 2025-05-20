# DeepIntegrator Module API Documentation

## Overview

DeepIntegrator enables the exposure of hidden app components and facilitates deeper integration between apps by:

1. Making components (Activities, Services, ContentProviders, BroadcastReceivers) exported
2. Bypassing permission checks for components
3. Adding or modifying intent filters to enhance component discovery

This module utilizes `io.github.libxposed.api.XposedInterface` and `io.github.libxposed.api.Hooker` classes to hook into the Android `PackageManagerService` and modify component resolution, exposure, and intent filter behavior at runtime.

## Key Features

- **Component Exposure**: Make any app component exported and accessible
- **Permission Bypass**: Remove or alter permission requirements for components
- **Intent Filter Injection**: Add custom intent filters to components 
- **Component Access Logging**: Track component access attempts and modifications

## Service API (Implemented via FeatureManager)

DeepIntegrator exposes a direct service API for other modules within the LSPosed Framework through the `FeatureManager`. 

```java
// Get the DeepIntegrator service via FeatureManager
Object service = FeatureManager.getInstance(context).getService("com.wobbz.DeepIntegrator.service");
DeepIntegratorModule moduleInstance = (DeepIntegratorModule) service;

// Access logs for exposed components
List<ComponentAccessLog> logs = moduleInstance.getAccessLogs();
// Clear the access logs
moduleInstance.clearAccessLogs();

// Add a new component configuration dynamically
ComponentConfig config = new ComponentConfig(
    "com.example.app", 
    "com.example.app.HiddenActivity",
    ComponentConfig.TYPE_ACTIVITY
);
config.setExported(true);
config.setBypassPermissions(true);

// Add a custom intent filter
IntentFilterConfig filter = new IntentFilterConfig();
filter.addAction("android.intent.action.VIEW");
filter.addCategory("android.intent.category.DEFAULT");
filter.addDataScheme("https");
config.addIntentFilter(filter);

// Apply the configuration
moduleInstance.addComponentConfig(config);

// Remove a component configuration
moduleInstance.removeComponentConfig("com.example.app", "com.example.app.HiddenActivity", "activity");
```

## Configuration (via settings.json)

DeepIntegrator is configured through a settings.json file with the following structure:

```json
{
  "componentOverrides": [
    {
      "packageName": "com.example.app",
      "componentName": "com.example.app.HiddenActivity",
      "componentType": "activity",
      "makeExported": true,
      "nullifyPermission": true,
      "bypassPermissions": true,
      "intentFilters": [
        {
          "actions": ["android.intent.action.VIEW"],
          "categories": ["android.intent.category.DEFAULT"],
          "schemes": ["https"],
          "hosts": ["example.com"],
          "mimeTypes": ["text/plain"]
        }
      ]
    }
  ],
  "logExposedComponents": true,
  "autoExpose": false,
  "bypassPermissionChecks": true,
  "componentLogs": [
    {
      "packageName": "com.example.app",
      "componentName": "com.example.app.HiddenActivity",
      "componentType": "activity", 
      "callingPackage": "com.example.caller",
      "timestamp": 1620000000000
    }
  ]
}
```

### Configuration Properties

- **componentOverrides**: Array of component configurations to modify
- **logExposedComponents**: When true, logs access to exposed components
- **autoExpose**: When true, automatically exposes all components in targeted apps
- **bypassPermissionChecks**: When true, bypasses permission checks for component access
- **componentLogs**: Array of logs for component access attempts (populated at runtime)

## Component Configuration Model

The `ComponentConfig` class contains the following properties:

```java
public class ComponentConfig {
    public static final String TYPE_ACTIVITY = "activity";
    public static final String TYPE_SERVICE = "service";
    public static final String TYPE_PROVIDER = "provider";
    public static final String TYPE_RECEIVER = "receiver";
    
    private String packageName;
    private String componentName;
    private String componentType;
    private boolean makeExported;
    private boolean nullifyPermission;
    private boolean bypassPermissions;
    private List<IntentFilterConfig> intentFilters;
    
    // Getters and setters
    // fromJson/toJson methods
}
```

## Intent Filter Configuration Model

The `IntentFilterConfig` class contains:

```java
public class IntentFilterConfig {
    private List<String> actions;
    private List<String> categories;
    private List<String> schemes;
    private List<String> hosts;
    private List<String> paths;
    private List<String> mimeTypes;
    
    // Getters and setters
    // fromJson/toJson methods
}
```

## Component Access Log Model

The `ComponentAccessLog` class tracks access to exposed components:

```java
public class ComponentAccessLog {
    private String packageName;
    private String componentName;
    private String componentType;
    private String callingPackage;
    private long timestamp;
    
    // Getters and setters
    // fromJson/toJson methods
}
```

## Integration with SuperPatcher

DeepIntegrator can leverage SuperPatcher for more direct patching when needed. It accesses SuperPatcher's service through FeatureManager:

```java
// Get the SuperPatcher service
SuperPatcherService superPatcherService = mFeatureManager.getService("com.wobbz.SuperPatcher.service");
if (superPatcherService != null) {
    // Use SuperPatcher to apply deeper patches when needed
}
```

## Integration with IntentMaster

IntentMaster and DeepIntegrator work together to provide complete control over app-to-app communication:

1. **DeepIntegrator**: Makes hidden or restricted components accessible
2. **IntentMaster**: Routes and modifies intents sent between components

For optimal integration:
1. Configure DeepIntegrator to expose necessary components
2. Configure IntentMaster rules to interact with those components
3. Use consistent component naming between both modules

## Development Environment

This module is developed using Java 17 and uses modern LSPosed API annotations:
- `@XposedPlugin` for module identification
- `@HotReloadable` for hot reload support

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

## Development Environment

This module is developed using Java 17 