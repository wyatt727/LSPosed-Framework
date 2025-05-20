# libxposed API Usage Guide

## Introduction

This guide explains how to use the libxposed API in your LSPosed modules with the project's integrated approach.

## Project Structure

The LSPosed-Modules project now includes the libxposed API directly as a source dependency through the `libxposed-api` directory in the project root. This approach ensures:

- Consistent API version across all modules
- Simplified dependency management
- Direct access to source code for better IDE integration
- Easier debugging and development

## Integration Method

### In Your Module

When developing a module within this project, the libxposed API is automatically available through project dependencies.

1. In your module's `build.gradle`, reference the API project:

```gradle
dependencies {
    // Reference the local API project
    compileOnly project(':libxposed-api:api')
    
    // Other dependencies...
}
```

2. Import the necessary classes in your code:

```java
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedContext;
import io.github.libxposed.api.hooks.Hooker;
import io.github.libxposed.api.hooks.HookParam;
// Other imports as needed...
```

## API Usage Pattern

The modern libxposed API follows this general pattern:

1. Receive the `XposedInterface` instance in your module's entry point
2. Create `Hooker` implementation classes for your hooks
3. Use `xposedInterface.hook()` to register your hooks

Example:

```java
public class MyModule implements IXposedModule {
    @Override
    public void onInit(XposedContext context, XposedInterface xposedInterface) {
        // Initialize your module
        
        // Find methods to hook
        Method targetMethod = findMethodToHook();
        
        // Register hook
        xposedInterface.hook(targetMethod, MyHooker.class);
    }
}

// Hooker implementation
public class MyHooker implements Hooker {
    @Override
    public void beforeHook(HookParam param) {
        // Pre-execution logic
    }
    
    @Override
    public void afterHook(HookParam param) {
        // Post-execution logic
    }
}
```

## Logging

Use the libxposed API's logging mechanism:

```java
// In a method with access to xposedInterface
xposedInterface.log("Your log message");
```

## Troubleshooting

1. **Import Issues**: Ensure you're importing from `io.github.libxposed.api` packages, not from older Xposed API packages.

2. **Missing Classes**: If your IDE can't find libxposed API classes, make sure your module's `build.gradle` includes the project dependency and that you've synced your Gradle project.

3. **Hook Not Working**: Verify that:
   - You're using the correct method to hook
   - Your Hooker implementation is properly registered
   - The target app or framework component is within your module's scope

## Additional Resources

- See the example modules in the project for complete implementations
- Refer to the source code in the `libxposed-api` directory for API details
- Check the project wiki for more advanced usage patterns 