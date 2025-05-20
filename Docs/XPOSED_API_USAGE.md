# LSPosed API Usage Guide

## Overview
The LSPosed API is the core library that enables modules to hook into Android framework methods. This guide explains how to reference and use the API in your modules.

## API Location and Integration
The Xposed API is included in the project as a direct source dependency via the `libxposed-api` directory. This approach ensures consistent API versions across all modules and simplifies dependency management.

### Project Configuration
The project is configured to use the local `libxposed-api` source:

1. In the project's `settings.gradle`, the libxposed-api module is included
2. Module build files reference this local project dependency

### Module Build Configuration
When creating a new module or updating an existing one, configure your `build.gradle` like this:

```gradle
dependencies {
    // Reference the local libxposed-api project
    compileOnly project(':libxposed-api:api')
    
    // Other dependencies...
}
```

## API Usage in Modules
When implementing a module:

1. Import the necessary classes from `io.github.libxposed.api`
2. Create hooking implementations using the new API style
3. Use the proper lifecycle methods for your module

Example implementation:

```java
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.hooks.HookParam;
import io.github.libxposed.api.hooks.Hooker;

// Implement hooking using the new API
public class YourHooker implements Hooker {
    @Override
    public void beforeHook(HookParam param) {
        // Pre-hook logic
    }

    @Override
    public void afterHook(HookParam param) {
        // Post-hook logic
    }
}

// In your module class:
public void hookMethod(XposedInterface xposedInterface) {
    Method method = findTargetMethod();
    xposedInterface.hook(method, YourHooker.class);
}
```

## Troubleshooting
- Check that your module is using `io.github.libxposed.api` imports, not the legacy Xposed API
- Ensure proper initialization of the XposedInterface in your module
- For logging, use `xposedInterface.log()` instead of the old `XposedBridge.log`

## Further Resources
- See example modules in the project for complete implementations
- Refer to libxposed-api JavaDocs for detailed API documentation 