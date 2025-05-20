# IntentMaster Module

## Overview

IntentMaster is a powerful Xposed module for intercepting, modifying, redirecting, and logging intents between Android applications. It provides a rule-based system to control how intents flow through the system, allowing for deep customization of app-to-app communication. This module utilizes the `io.github.libxposed.api.XposedInterface` for its core hooking functionalities.

## Features

- Intercept intents from target applications
- Match intents based on complex criteria (action, data URI, type, component, extras)
- Modify any aspect of intents (action, data, type, categories, component, extras, flags)
- Redirect intents to different components
- Block unwanted intents
- Log all intent activity
- Test intents with a built-in testing tool

## How It Works

IntentMaster implements the `io.github.libxposed.api.IXposedModule` interface. Its primary operations occur within the `onPackageLoaded` lifecycle method.

1.  **Hooking Intent Mechanisms**: When a target application is loaded, IntentMaster uses `XposedInterface` (obtained via `param.getXposed()`) to hook key Android framework methods responsible for intent dispatch. These commonly include:
    *   `Activity.startActivity` and its variants
    *   `Context.sendBroadcast`, `Context.sendOrderedBroadcast`
    *   `Context.startService`
    *   And other relevant intent-related methods.
2.  **Hooker Classes**: Hooks are organized into `io.github.libxposed.api.Hooker` classes for clarity and maintainability. Each `Hooker` targets specific methods involved in intent handling.
3.  **Rule Processing**: When an intercepted method is called (e.g., an app tries to start an activity), the registered hook (e.g., `@BeforeHook` or `@AfterHook`) in the relevant `Hooker` class is triggered.
    *   The hook extracts the `Intent` object from the method parameters.
    *   It then consults the configured `intentRules` (see Configuration section).
    *   If a rule matches the intent, IntentMaster performs the specified action: `MODIFY`, `REDIRECT`, or `BLOCK`.
    *   Modifications are applied directly to the `Intent` object within the hook before the original method proceeds (or is skipped).

## Configuration

IntentMaster is configured through its `settings.json` file. The main settings include:

- `targetApps`: List of packages to intercept intents from
- `logAllIntents`: Whether to log all intercepted intents
- `interceptionEnabled`: Global toggle for the module
- `intentRules`: Array of rules for matching and modifying intents (see examples below)

## Example Use Cases

### 1. Chrome PWA File Attachment

By default, Chrome's Progressive Web Apps (PWAs) can only attach image, audio, or video files. With IntentMaster, you can modify the MIME type of Chrome's GET_CONTENT intents to allow all file types:

```json
{
  "name": "Chrome PWA File Attachment Enabler",
  "enabled": true,
  "packageName": "com.android.chrome",
  "action": "android.intent.action.GET_CONTENT",
  "type": "image/.*|video/.*|audio/.*",
  "intentAction": "MODIFY",
  "modification": {
    "newType": "*/*"
  }
}
```

### 2. Redirect Media Sharing to Custom App

Redirect shared media from any app to your preferred media app:

```json
{
  "name": "Redirect Media to SecureShare",
  "enabled": true,
  "action": "android.intent.action.SEND",
  "type": "image/.*|video/.*",
  "intentAction": "REDIRECT",
  "modification": {
    "newComponent": "com.example.secureshare/.MainActivity"
  }
}
```

### 3. Block Analytics or Tracking Intents

Block certain intents that might be used for analytics or tracking:

```json
{
  "name": "Block Analytics Broadcasts",
  "enabled": true,
  "action": "com.google.analytics.TRACK_EVENT",
  "intentAction": "BLOCK"
}
```

## Integration with Other Modules

IntentMaster works well with:

- **DeepIntegrator**: Use IntentMaster to route intents to components exposed by DeepIntegrator
- **PermissionOverride**: Necessary when routed intents require permissions that would otherwise block the target app

## Dependencies

-   **Xposed API**: `compileOnly project(':libxposed-api:api')`
    -   Uses `io.github.libxposed.api.XposedInterface` for framework interaction.
    -   Uses `io.github.libxposed.api.Hooker` for organizing hook implementations.
-   Relies on the Android Framework APIs for Intent handling.

## Logging

IntentMaster keeps a detailed log of all intercepted intents, showing:

- Source package
- Intent action, data, type, component
- Categories and extras
- Which rule was applied (if any)
- Whether the intent was modified or blocked

View these logs in the IntentMaster settings UI.

## Development Environment

This module is developed using Java 17.

## For More Information

See the detailed API documentation in [API.md](./API.md) 