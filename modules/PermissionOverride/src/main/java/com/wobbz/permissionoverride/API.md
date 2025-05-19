# PermissionOverride API Documentation

The PermissionOverride module provides powerful capabilities for overriding Android permission checks, bypassing signature verification, and enhancing reflection access. This document describes how to use the PermissionOverride API in your module.

## Accessing the PermissionOverride API

PermissionOverride registers itself as a service with the `FeatureManager`. You can access it as follows:

```java
// Get the FeatureManager instance
FeatureManager featureManager = FeatureManager.getInstance(context);

// Get the PermissionOverride service
PermissionOverrideModule permissionOverride = featureManager.getService(PermissionOverrideModule.SERVICE_KEY);

// Check if the service is available
if (permissionOverride != null) {
    // Use the PermissionOverride API
}
```

## API Methods

### Enhanced Reflection

PermissionOverride offers enhanced reflection capabilities that can bypass normal Android restrictions. These are particularly useful when dealing with system classes or protected/private members.

#### Finding Classes

```java
/**
 * Find a class with enhanced reflection capabilities.
 * 
 * @param className The class name.
 * @param classLoader The class loader.
 * @return The class, or null if not found.
 */
public Class<?> findClass(String className, ClassLoader classLoader);
```

Example usage:

```java
Class<?> activityManagerClass = permissionOverride.findClass(
    "com.android.server.am.ActivityManagerService", 
    lpparam.classLoader
);
```

#### Finding Methods

```java
/**
 * Find a method with enhanced reflection capabilities.
 * 
 * @param clazz The class.
 * @param methodName The method name.
 * @param parameterTypes The parameter types.
 * @return The method, or null if not found.
 */
public Method findMethod(Class<?> clazz, String methodName, Object[] parameterTypes);
```

Example usage:

```java
Method startActivityMethod = permissionOverride.findMethod(
    activityManagerClass,
    "startActivity",
    new Object[]{
        "android.content.Intent",
        "android.content.Context",
        "java.lang.String"
    }
);
```

#### Finding Constructors

```java
/**
 * Find a constructor with enhanced reflection capabilities.
 * 
 * @param clazz The class.
 * @param parameterTypes The parameter types.
 * @return The constructor, or null if not found.
 */
public Constructor<?> findConstructor(Class<?> clazz, Object[] parameterTypes);
```

Example usage:

```java
Constructor<?> intentConstructor = permissionOverride.findConstructor(
    Intent.class,
    new Object[]{
        "java.lang.String",
        "android.net.Uri"
    }
);
```

### Field Access

```java
/**
 * Get the value of a field from an object.
 * 
 * @param obj The object (null for static fields).
 * @param className The class name.
 * @param fieldName The field name.
 * @param classLoader The class loader.
 * @return The field value, or null if not found.
 */
public Object getFieldValue(Object obj, String className, String fieldName, ClassLoader classLoader);

/**
 * Set the value of a field in an object.
 * 
 * @param obj The object (null for static fields).
 * @param className The class name.
 * @param fieldName The field name.
 * @param value The value to set.
 * @param classLoader The class loader.
 * @return true if successful, false otherwise.
 */
public boolean setFieldValue(Object obj, String className, String fieldName, Object value, ClassLoader classLoader);
```

Example usage:

```java
// Get a private field value
Boolean isGranted = (Boolean) permissionOverride.getFieldValue(
    permissionState,
    "com.android.server.pm.permission.PermissionState",
    "mGranted",
    lpparam.classLoader
);

// Set a private field value
permissionOverride.setFieldValue(
    permissionState,
    "com.android.server.pm.permission.PermissionState",
    "mGranted",
    true,
    lpparam.classLoader
);
```

### Object Creation and Method Invocation

```java
/**
 * Create a new instance of a class.
 * 
 * @param className The class name.
 * @param classLoader The class loader.
 * @param constructorParams The constructor parameters.
 * @return The new instance, or null if failed.
 */
public Object createInstance(String className, ClassLoader classLoader, Object... constructorParams);

/**
 * Invoke a method on an object.
 * 
 * @param obj The object (null for static methods).
 * @param className The class name.
 * @param methodName The method name.
 * @param classLoader The class loader.
 * @param params The method parameters.
 * @return The method result, or null if failed.
 */
public Object invokeMethod(Object obj, String className, String methodName, ClassLoader classLoader, Object... params);
```

Example usage:

```java
// Create an instance of a protected class
Object permissionInfo = permissionOverride.createInstance(
    "android.content.pm.PermissionInfo",
    lpparam.classLoader,
    "com.example.permission.CUSTOM_PERMISSION"
);

// Invoke a private method
Boolean hasPermission = (Boolean) permissionOverride.invokeMethod(
    packageManager,
    "android.content.pm.PackageManager",
    "hasSystemFeature",
    lpparam.classLoader,
    "android.hardware.camera"
);
```

### Permission Request History

PermissionOverride can track permission requests for debugging purposes:

```java
/**
 * Get the permission request history.
 * 
 * @return A copy of the permission request history.
 */
public List<PermissionRequest> getPermissionRequestHistory();
```

The `PermissionRequest` class includes:
- `packageName`: The package that requested the permission
- `permission`: The permission string
- `granted`: Whether the permission was granted
- `timestamp`: When the request occurred

## Best Practices

1. **Check for service availability**: Always check if the PermissionOverride service is available before using it.
2. **Handle errors gracefully**: The API methods may return null if they fail. Handle these cases appropriately.
3. **Combine with SuperPatcher**: The PermissionOverride module works well with SuperPatcher for advanced app modifications.
4. **Security considerations**: With great power comes great responsibility. Be careful when bypassing security checks.
5. **Performance**: Cache reflection results when possible to avoid repeated lookups. 