# IntentMaster Module API Documentation

## Overview

IntentMaster is a powerful Xposed module for intercepting, modifying, redirecting, and logging intents between Android applications. It provides a rule-based system to control how intents flow through the system, allowing for deep customization of app-to-app communication. It utilizes `io.github.libxposed.api.XposedInterface` and `io.github.libxposed.api.Hooker` classes for its core hooking mechanisms.

## Module Implementation

IntentMaster implements the modern LSPosed API interfaces and annotations:

```java
@XposedPlugin(
    id = "com.wobbz.IntentMaster",
    name = "Intent Master",
    description = "Intercepts, modifies, redirects, and logs intents between applications",
    version = "1.0.0",
    author = "wobbz"
)
@HotReloadable
public class IntentMasterModule implements IModulePlugin, IHotReloadable {
    // Implementation...
}
```

The module uses:
- `@XposedPlugin` to identify the module within the LSPosed framework
- `@HotReloadable` to support dynamic reloading of the module without restarting the device
- `IModulePlugin` to integrate with the Framework
- `IHotReloadable` to implement the `onHotReload()` method for hot reload support

## Key Features

- **Intent Interception**: Hooks key Android intent-related methods (e.g., `Activity.startActivity`, `Context.sendBroadcast`) using the Xposed framework to capture intents in flight.
- **Intent Modification**: Change any aspect of an intent (action, data, type, component, extras, etc.) based on defined rules.
- **Intent Redirection**: Redirect intents to different components than originally intended.
- **Intent Blocking**: Prevent certain intents from being delivered.
- **Intent Logging**: Keep a detailed log of all intercepted intents and actions taken.
- **Rule-Based System**: Define complex matching rules in JSON format to target specific intents.
- **Test Intent Feature**: Create and send test intents to see how they're handled by the rules.

## Test Intent Feature

The Test Intent Feature allows you to create and send test intents to validate your rules without needing to set up complex test scenarios with other apps. This is implemented through the `sendTestIntent` method:

```java
public String sendTestIntent(JSONObject testIntentConfig) {
    // Implementation that creates and processes a test intent
    // based on the provided configuration
}
```

### Test Intent Configuration

The test intent is configured using a JSON object with the following properties:

```json
{
  "action": "android.intent.action.VIEW",
  "data": "https://example.com",
  "type": "text/plain",
  "component": "com.example.app/.MainActivity",
  "categories": ["android.intent.category.DEFAULT"],
  "extras": [
    {
      "key": "test_key",
      "value": "test_value",
      "type": "STRING"
    }
  ],
  "flags": 0,
  "sourcePackage": "com.example.sourceapp",
  "send": true,
  "startActivity": true
}
```

### Configuration Properties

- **`action`**: The intent action (e.g., "android.intent.action.VIEW")
- **`data`**: The intent data URI (e.g., "https://example.com")
- **`type`**: The MIME type (e.g., "text/plain")
- **`component`**: The target component in flattened form (e.g., "com.example.app/.MainActivity")
- **`categories`**: Array of intent categories to add
- **`extras`**: Array of extra values to add to the intent
- **`flags`**: Integer flags to set on the intent
- **`sourcePackage`**: The package name to use as the source (defaults to the module's package)
- **`send`**: Whether to actually send the intent or just simulate processing
- **`startActivity`**, **`startService`**, **`sendBroadcast`**: How to send the intent (if `send` is true)

### Usage Example

To use the Test Intent Feature:

1. Configure a test intent in JSON format
2. Pass it to the `sendTestIntent` method
3. The method will:
   - Create the intent according to configuration
   - Process it through your defined rules
   - Optionally send the processed intent
   - Return a result message describing what happened

This feature is particularly useful for:
- Debugging complex rule sets
- Testing modifications to intents
- Verifying that intent redirection works as expected
- Testing how apps respond to modified intents

## Integration with Other Modules

- **DeepIntegrator**: IntentMaster can work in conjunction with DeepIntegrator to route intents to components that DeepIntegrator might expose.
- **PermissionOverride**: IntentMaster may rely on PermissionOverride if a redirected or modified intent requires permissions that the original target or context did not have.
- **Hooking Mechanism**: IntentMaster uses the `libxposed-api` (specifically `io.github.libxposed.api.XposedInterface` and `Hooker` classes) for its underlying hook implementations within the Xposed framework.

## Settings Schema

### Global Settings

- `targetApps`: List of packages to intercept intents from
- `logAllIntents`: Whether to log all intercepted intents
- `interceptionEnabled`: Global toggle for the module
- `intentRules`: Array of rules for matching and modifying intents
- `intentLogs`: Array of logged intents (read-only)
- `intentActions`: Common intent actions for reference (read-only)
- `intentCategories`: Common intent categories for reference (read-only)
- `testIntent`: Configuration for the test intent feature

### Intent Rule Structure

Each rule in the `intentRules` array has the following structure:

```json
{
  "id": "unique-id",
  "name": "Rule Name",
  "enabled": true,
  "packageName": "com.example.app",
  "action": "android.intent.action.VIEW",
  "data": "https://example.com/.*",
  "type": "text/plain",
  "component": "com.example.app/.MainActivity",
  "categories": ["android.intent.category.DEFAULT"],
  "extraMatches": [
    {
      "key": "extra_key",
      "value": "extra_value",
      "matchType": "EQUALS"
    }
  ],
  "intentAction": "MODIFY",
  "modification": {
    "newAction": "android.intent.action.SEND",
    "newData": "https://modified.com",
    "newType": "text/html",
    "newCategories": ["android.intent.category.DEFAULT"],
    "newComponent": "com.example.app/.OtherActivity",
    "extrasToAdd": [
      {
        "key": "new_extra",
        "value": "new_value",
        "type": "STRING"
      }
    ],
    "extrasToRemove": ["remove_this_extra"],
    "flags": 0
  }
}
```

### Intent Match Criteria

- `packageName`: Source package name (optional)
- `action`: Intent action (optional)
- `data`: URI data string or regex pattern (optional)
- `type`: MIME type (optional)
- `component`: Component name in flattened form (optional)
- `categories`: List of intent categories to match (optional)
- `extraMatches`: List of extras to match (optional)

### Extra Match Types

- `EQUALS`: Extra value must exactly match
- `CONTAINS`: Extra value must contain the specified string
- `REGEX`: Extra value must match the regex pattern
- `EXISTS`: Extra key must exist (value is ignored)
- `NOT_EXISTS`: Extra key must not exist (value is ignored)

### Intent Actions

- `MODIFY`: Modify the intent according to the modification spec
- `REDIRECT`: Special case of MODIFY focused on changing the component
- `BLOCK`: Prevent the intent from being delivered
- `LOG`: Just log the intent without modifying it

### Extra Types for Adding

- `STRING`: String value
- `INT`: Integer value
- `BOOLEAN`: Boolean value
- `FLOAT`: Float value
- `LONG`: Long value
- `DOUBLE`: Double value
- `BYTE_ARRAY`: Byte array (string will be converted)
- `CHAR_SEQUENCE`: Character sequence (equivalent to STRING)

## Log Structure

Each entry in the `intentLogs` array has the following structure:

```json
{
  "id": "unique-id",
  "timestamp": 123456789,
  "formattedTime": "2023-05-19 13:45:30",
  "sourcePackage": "com.example.app",
  "action": "android.intent.action.VIEW",
  "data": "https://example.com",
  "type": "text/plain",
  "component": "com.example.app/.MainActivity",
  "categories": ["android.intent.category.DEFAULT"],
  "extras": [
    {
      "key": "extra_key",
      "value": "extra_value",
      "type": "String"
    }
  ],
  "flags": 0,
  "appliedRuleId": "rule-id",
  "appliedRuleName": "Rule Name",
  "resultAction": "MODIFY",
  "wasModified": true,
  "wasBlocked": false
}
```

## Examples

### Example 1: Change Chrome PWA File Attachment Type

This rule changes the MIME type of file selection intents from Chrome to allow all file types:

```json
{
  "name": "Allow Chrome PWA File Attachments",
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

### Example 2: Redirect SMS App Intents

This rule redirects SMS viewing intents from the default SMS app to a different messaging app:

```json
{
  "name": "Redirect SMS to Secure Messenger",
  "enabled": true,
  "action": "android.intent.action.VIEW",
  "data": "sms:.*",
  "intentAction": "REDIRECT",
  "modification": {
    "newComponent": "com.secureamessenger/.ConversationActivity"
  }
}
```

### Example 3: Block Tracking Broadcasts

This rule blocks analytics broadcast intents:

```json
{
  "name": "Block Analytics Broadcasts",
  "enabled": true,
  "action": "com.google.analytics.TRACK_EVENT",
  "intentAction": "BLOCK"
}
```

### Example 4: Using the Test Intent Feature

This example demonstrates using the test intent feature to test a rule:

```json
{
  "testIntent": {
    "action": "android.intent.action.VIEW",
    "data": "https://example.com",
    "type": "text/plain",
    "component": "com.example.app/.MainActivity",
    "extras": [
      {
        "key": "test_key",
        "value": "test_value",
        "type": "STRING"
      }
    ],
    "send": true,
    "startActivity": true
  }
}
```

## API Usage from Other Modules

IntentMaster does not currently expose a programmatic API for other Xposed modules to call directly. Its functionality is self-contained and primarily controlled via its `settings.json` configuration file, which defines the intent processing rules.

## Best Practices

1. **Start with logging**: Enable logging and observe intents before creating rules
2. **Be specific**: Make rules as specific as possible to avoid unintended side effects
3. **Test thoroughly**: Use the test intent feature to verify your rules
4. **Limit target apps**: Only target specific apps rather than all apps for performance
5. **Beware of system intents**: Be very careful when modifying or blocking system intents
6. **Consider chained effects**: Remember that one modified intent may trigger other intents

## Development Environment

This module is developed using Java 17 and implements the modern LSPosed API with proper annotations for plugin identification and hot reload support. 