# IntentMaster Module

## Overview

IntentMaster is a powerful module for intercepting, modifying, redirecting, and logging intents between Android applications. It provides a rule-based system to control how intents flow through the system, allowing for deep customization of app-to-app communication.

## Features

- Intercept intents from target applications
- Match intents based on complex criteria (action, data URI, type, component, extras)
- Modify any aspect of intents (action, data, type, categories, component, extras, flags)
- Redirect intents to different components
- Block unwanted intents
- Log all intent activity
- Test intents with a built-in testing tool

## Configuration

IntentMaster is configured through its settings.json file. The main settings include:

- `targetApps`: List of packages to intercept intents from
- `logAllIntents`: Whether to log all intercepted intents
- `interceptionEnabled`: Global toggle for the module
- `intentRules`: Array of rules for matching and modifying intents

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

## Logging

IntentMaster keeps a detailed log of all intercepted intents, showing:

- Source package
- Intent action, data, type, component
- Categories and extras
- Which rule was applied (if any)
- Whether the intent was modified or blocked

View these logs in the IntentMaster settings UI.

## For More Information

See the detailed API documentation in [API.md](./API.md) 