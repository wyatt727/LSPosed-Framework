# SuperPatcher API Documentation

SuperPatcher exposes a comprehensive API that other modules can use to modify method behavior, access fields, and load custom code. This document describes how to use the SuperPatcher API in your module.

## Accessing the SuperPatcher API

SuperPatcher registers itself as a service with the `FeatureManager`. You can access it as follows:

```java
// Get the FeatureManager instance
FeatureManager featureManager = FeatureManager.getInstance(context);

// Get the SuperPatcher service
SuperPatcherModule superPatcher = featureManager.getService(SuperPatcherModule.SERVICE_KEY);

// Check if the service is available
if (superPatcher != null) {
    // Use the SuperPatcher API
}
```

## API Methods

### Method Hooking

```java
/**
 * Request a hook for a specific method.
 * 
 * @param packageName The package name to apply the hook to.
 * @param className The class to hook.
 * @param methodName The method to hook.
 * @param parameterTypes The parameter types or null for all methods with the name.
 * @param hookType The type of hook (before, after, replace, both).
 * @param callback The callback to invoke when the hook is triggered.
 * @return A unique ID for the hook, or null if failed.
 */
public String requestHook(String packageName, String className, String methodName, 
                          String[] parameterTypes, String hookType, 
                          SuperPatcherModule.HookCallback callback);
```

Example usage:

```java
superPatcher.requestHook(
    "com.example.app",
    "com.example.app.MainActivity",
    "onCreate",
    new String[]{"android.os.Bundle"},
    "after",
    (method, thisObject, args, returnValue, isBefore) -> {
        // This is called after onCreate is executed
        Log.d("MyModule", "MainActivity.onCreate was called!");
    }
);
```

### Field Access

```java
/**
 * Read the value of a field from an object.
 * 
 * @param obj The object to read from (null for static fields).
 * @param className The class name.
 * @param fieldName The field name.
 * @param classLoader The class loader to use.
 * @return The field value.
 */
public Object getFieldValue(Object obj, String className, String fieldName, ClassLoader classLoader);

/**
 * Set the value of a field in an object.
 * 
 * @param obj The object to modify (null for static fields).
 * @param className The class name.
 * @param fieldName The field name.
 * @param value The value to set.
 * @param classLoader The class loader to use.
 * @return true if successful, false otherwise.
 */
public boolean setFieldValue(Object obj, String className, String fieldName, Object value, ClassLoader classLoader);
```

Example usage:

```java
// Read a static field
Boolean debugEnabled = (Boolean) superPatcher.getFieldValue(
    null,
    "com.example.app.Config",
    "DEBUG",
    lpparam.classLoader
);

// Set a field
superPatcher.setFieldValue(
    webView,
    "android.webkit.WebView",
    "mPrivateBrowsingEnabled",
    false,
    lpparam.classLoader
);
```

### Object Creation and Method Invocation

```java
/**
 * Create a new instance of a class.
 * 
 * @param className The class name.
 * @param classLoader The class loader to use.
 * @param constructorParams The constructor parameters.
 * @return The new instance.
 */
public Object createInstance(String className, ClassLoader classLoader, Object... constructorParams);

/**
 * Invoke a method on an object.
 * 
 * @param obj The object to invoke on (null for static methods).
 * @param className The class name.
 * @param methodName The method name.
 * @param classLoader The class loader to use.
 * @param params The method parameters.
 * @return The method result.
 */
public Object invokeMethod(Object obj, String className, String methodName, ClassLoader classLoader, Object... params);
```

Example usage:

```java
// Create an instance
Uri uri = (Uri) superPatcher.createInstance(
    "android.net.Uri",
    lpparam.classLoader,
    "https://example.com"
);

// Invoke a method
String host = (String) superPatcher.invokeMethod(
    uri,
    "android.net.Uri",
    "getHost",
    lpparam.classLoader
);
```

### DEX Loading

```java
/**
 * Get a ClassLoader for a specific DEX path.
 * 
 * @param dexPath The path to the DEX file.
 * @return The ClassLoader, or null if not found.
 */
public ClassLoader getDexClassLoader(String dexPath);
```

## PermissionOverride Integration

All API methods automatically attempt to use the PermissionOverride module if standard reflection fails due to visibility/permission issues. You don't need to handle this explicitly.

## Best Practices

1. **Check for service availability**: Always check if the SuperPatcher service is available before using it.
2. **Handle errors gracefully**: The API methods may return null if they fail. Handle these cases appropriately.
3. **Use for advanced cases**: For simple method hooking, you can use the LSPosed API directly. Use SuperPatcher for more complex cases or when you need its additional capabilities.
4. **Hot reload support**: Be aware that hooks may be removed during hot reload. If you need persistent hooks, consider registering them on each module load. 