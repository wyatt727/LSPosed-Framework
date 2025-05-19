# DeepIntegrator Module API Documentation

## Overview

DeepIntegrator enables the exposure of hidden app components and facilitates deeper integration between apps by:

1. Making components (Activities, Services, ContentProviders, BroadcastReceivers) exported
2. Bypassing permission checks for components
3. Adding or modifying intent filters to enhance component discovery

This document provides details on how to integrate with DeepIntegrator from other modules, particularly IntentMaster.

## Key Features

- **Component Exposure**: Make any app component exported and accessible
- **Permission Bypass**: Remove permission requirements for components
- **Intent Filter Injection**: Add custom intent filters to components
- **Component Access Logging**: Track all component access through DeepIntegrator

## Integration with IntentMaster

IntentMaster and DeepIntegrator are designed to work together to provide complete control over app-to-app communication:

1. **DeepIntegrator**: Makes hidden components accessible
2. **IntentMaster**: Routes and modifies intents sent between components

### Workflow Example

1. Use DeepIntegrator to expose a hidden activity in App A that has useful functionality
2. Use IntentMaster to:
   - Redirect intents from App B to the newly exposed activity in App A
   - Modify intent extras as needed before delivery
   - Log intent traffic for debugging

### Configuration Recommendations

For optimal integration between DeepIntegrator and IntentMaster:

1. First configure DeepIntegrator to expose the necessary components
2. Then configure IntentMaster rules to interact with those components
3. Use consistent component naming between both modules

## Service API

DeepIntegrator exposes a service API that can be accessed by other modules:

```java
// Get the DeepIntegrator service
Object service = FeatureManager.getInstance(context).getService("com.wobbz.DeepIntegrator.service");
DeepIntegratorModule module = (DeepIntegratorModule) service;

// Access logs
List<ComponentAccessLog> logs = module.getAccessLogs();

// Add component configuration
ComponentConfig config = new ComponentConfig(
    "com.example.app", 
    "com.example.app.HiddenActivity",
    ComponentConfig.TYPE_ACTIVITY
);
config.setExported(true);
config.setBypassPermissions(true);

// Add intent filter to component
IntentFilterConfig filter = new IntentFilterConfig();
filter.addAction("android.intent.action.VIEW");
filter.addCategory("android.intent.category.DEFAULT");
filter.addMimeType("image/*");
config.addIntentFilter(filter);

module.addComponentConfig(config);
```

## Fallback to SuperPatcher

In some cases, hardcoded checks in an app might prevent access even when a component is marked as exported. In these cases:

1. Use SuperPatcher to hook internal permission check methods
2. Use SuperPatcher to hook internal component resolution methods
3. Use SuperPatcher to modify fields that control component visibility

Example:

```java
// Get the SuperPatcher service
Object superPatcherService = FeatureManager.getInstance(context).getService("com.wobbz.SuperPatcher.service");

// Hook internal isComponentAccessible method
superPatcherService.requestHook(
    "com.example.app",
    "com.example.app.ComponentHelper",
    "isComponentAccessible",
    new String[]{"android.content.ComponentName", "android.content.Context"},
    "replace",
    new SuperPatcherModule.HookCallback() {
        @Override
        public void onHook(Member method, Object thisObject, Object[] args, Object returnValue, boolean isBefore) {
            // Force return true to allow access
            return true;
        }
    }
);
```

## Troubleshooting Component Exposure

If a component still cannot be accessed after using DeepIntegrator:

1. Check the component access logs to verify the component was modified
2. Check for internal permission checks in the app using SuperPatcher
3. Check for custom signature verification
4. Check if the app uses custom classloaders or protection mechanisms

## Security Considerations

DeepIntegrator can significantly affect app security boundaries. Use it carefully and consider:

1. Only expose components that are necessary
2. Avoid bypassing critical security permissions
3. Monitor the component access logs
4. Test thoroughly before deploying in production environments 