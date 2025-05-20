# PermissionOverride Module API

## Overview

The PermissionOverride module modifies Android's permission system for targeted applications based on its declarative configuration defined in a `settings.json` file. It also exposes a service API for other Xposed modules through the Framework's `FeatureManager`.

This module utilizes `io.github.libxposed.api.XposedInterface` and `io.github.libxposed.api.Hooker` classes to hook relevant system methods (e.g., in `PackageManagerService`, `Context`) to enforce configured permission overrides. Hooks are applied in `onSystemServerLoaded` for system-wide effects and `onPackageLoaded` for app-specific contexts.

**Warning**: Modifying application permissions can have significant security and privacy implications. Configure this module with a clear understanding of the permissions and potential consequences.

## Configuration (via `settings.json`)

PermissionOverride rules are defined in a `settings.json` file within the module's configuration directory.

### Rule Object Structure

The module supports two different JSON structures for permission overrides:

#### Modern Format (Recommended)
The primary element is `permissionOverrides`, an array of rule objects with the following properties:

-   **`packageName`** (String, Required):
    The package name of the application to which this rule applies.
    Example: `"com.example.targetapp"`

-   **`permission`** (String, Required):
    The full name of the Android permission to override.
    Example: `"android.permission.READ_CONTACTS"`

-   **`action`** (String, Required):
    The action to take for this permission. Supported values:
    -   `"GRANT"`: Force grant this permission to the application.
    -   `"DENY"`: Force deny this permission to the application, even if requested or previously granted.
    -   `"FAKE_GRANT"`: Make the app believe it has the permission, but logs that it's a fake grant. This returns PERMISSION_GRANTED to the app's checks, but allows tracking of these special cases.
    -   `"FAKE_DENY"`: Make the app believe it is denied the permission. Returns PERMISSION_DENIED to the app's checks, but logs that it's a fake deny.
    Example: `"GRANT"`

-   **`enabled`** (Boolean, Optional):
    Set to `true` to enable the rule, `false` to disable it. If omitted, defaults to `true`.
    Example: `true`

#### Legacy Format (Supported for Backward Compatibility)
An alternative format using nested permission objects:

```json
[
  {
    "packageName": "com.example.app",
    "permissions": {
      "android.permission.CAMERA": "GRANT",
      "android.permission.LOCATION": "DENY"
    }
  }
]
```

### Example `settings.json` (Modern Format)

```json
{
  "permissionOverrides": [
    {
      "packageName": "com.example.problematicapp",
      "permission": "android.permission.ACCESS_FINE_LOCATION",
      "action": "DENY",
      "enabled": true
    },
    {
      "packageName": "com.example.legacyapp",
      "permission": "android.permission.WRITE_EXTERNAL_STORAGE",
      "action": "GRANT"
    },
    {
      "packageName": "com.sample.testfeatures",
      "permission": "android.permission.CAMERA",
      "action": "FAKE_DENY",
      "enabled": true
    }
  ],
  "globalSettings": {
    "verboseLogging": false,
    "bypassSignatureChecks": true,
    "logPermissionRequests": true,
    "defaultPermissionBehavior": "DEFAULT", // Possible values: "GRANT", "DENY", "FAKE_GRANT", "FAKE_DENY", "DEFAULT"
    "targetApps": ["com.example.app1", "com.example.app2"] // If empty, all apps are targeted
  }
}
```

### Global Settings

The `globalSettings` object contains overall settings for the module:
-   `verboseLogging` (Boolean): Enable detailed logging from PermissionOverride.
-   `bypassSignatureChecks` (Boolean): When true, the module will bypass signature verification checks.
-   `logPermissionRequests` (Boolean): When true, logs all permission requests that are processed by the module.
-   `defaultPermissionBehavior` (String): The default action to take for permissions that don't have specific overrides.
-   `targetApps` (Array of Strings): The list of package names to target. If empty, all apps are targeted.

## Service API

The PermissionOverride module exposes a service API through the Framework's `FeatureManager`. Other modules can access this service using:

```java
IPermissionOverrideService service = (IPermissionOverrideService)FeatureManager.getInstance(context)
    .getService("com.wobbz.PermissionOverride.service");
```

### Service Methods

-   **`Integer checkPermissionOverrideStatus(String packageName, String permission)`**:  
    Checks if a permission is overridden for a package, returning the override status.

-   **`boolean P_isAppPermissionForced(String packageName, String permission)`**:  
    Checks if a permission is forced to be granted for a package.

-   **`boolean P_isAppPermissionSuppressed(String packageName, String permission)`**:  
    Checks if a permission is suppressed (forced to be denied) for a package.

-   **Reflection Utilities**:  
    The service also provides several helper methods for reflection operations, which may use enhanced techniques to bypass restrictions:
    -   `Class<?> findClass(String className, ClassLoader classLoader)`
    -   `Method findMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes)`
    -   `Constructor<?> findConstructor(Class<?> clazz, Class<?>... parameterTypes)`
    -   `Object getFieldValue(Object obj, String className, String fieldName, ClassLoader classLoader)`
    -   `boolean setFieldValue(Object obj, String className, String fieldName, Object value, ClassLoader classLoader)`
    -   `Object createInstance(String className, ClassLoader classLoader, Object... constructorParams)`
    -   `Object invokeMethod(Object obj, String className, String methodName, ClassLoader classLoader, Object... params)`

## Internal Hooking Mechanism (Informational)

-   PermissionOverride hooks key methods in the Android framework related to permission checking and granting.
-   System-level hooks (in `onSystemServerLoaded`) target services like `PackageManagerService` and `PermissionManagerService` (e.g., `checkPermission`, `grantRuntimePermission`).
-   Application-level hooks (in `onPackageLoaded`) target methods like `Context.checkSelfPermission()`, `PackageManager.checkPermission()` within the app's process.
-   The module consults the loaded configuration to determine if an override should be applied for the current package and permission check.
-   FAKE_GRANT and FAKE_DENY actions return the same values as regular GRANT/DENY to the application, but are logged distinctly for monitoring purposes.

## Development Environment

This module is developed using Java 17. 