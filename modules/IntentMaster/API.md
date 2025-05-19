# IntentMaster Module API Documentation

## Overview

IntentMaster is a powerful module for intercepting, modifying, redirecting, and logging intents between Android applications. It provides a rule-based system to control how intents flow through the system, allowing for deep customization of app-to-app communication.

## Key Features

- **Intent Interception**: Hook into key Android intent-related methods to capture intents in flight
- **Intent Modification**: Change any aspect of an intent (action, data, type, component, extras, etc.)
- **Intent Redirection**: Redirect intents to different components than originally intended
- **Intent Blocking**: Prevent certain intents from being delivered
- **Intent Logging**: Keep a detailed log of all intercepted intents
- **Rule-Based System**: Define complex matching rules to target specific intents
- **Test Intent Feature**: Create and send test intents to see how they're handled

## Integration with Other Modules

- **DeepIntegrator**: Works with DeepIntegrator to route intents to components that have been exposed
- **PermissionOverride**: Leverages PermissionOverride when necessary to bypass permission checks
- **SuperPatcher**: Relies on SuperPatcher for its underlying hook mechanisms

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

## API Usage from Other Modules

IntentMaster does not currently expose a programmatic API to other modules, but this may be added in the future.

## Best Practices

1. **Start with logging**: Enable logging and observe intents before creating rules
2. **Be specific**: Make rules as specific as possible to avoid unintended side effects
3. **Test thoroughly**: Use the test intent feature to verify your rules
4. **Limit target apps**: Only target specific apps rather than all apps for performance
5. **Beware of system intents**: Be very careful when modifying or blocking system intents
6. **Consider chained effects**: Remember that one modified intent may trigger other intents 