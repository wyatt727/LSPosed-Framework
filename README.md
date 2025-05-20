# 🚀 LSPosed Modular Framework

[![Build Status](https://img.shields.io/github/actions/workflow/status/wobbz/LSPosedFramework/build.yml?branch=main)](https://github.com/wobbz/LSPosedFramework/actions)
[![Release](https://img.shields.io/github/v/release/wobbz/LSPosedFramework)](https://github.com/wobbz/LSPosedFramework/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

> A **modern**, **annotation-driven**, and **Android 14+ ready** host framework for all your LSPosed modules—featuring a vastly improved developer experience with **hot-reloading**, **auto-generated UI**, **simplified Xposed API management via local source**, and **seamless dependency handling**.

---

## 📋 Table of Contents

- [✨ Overview: The Game Changer](#-overview-the-game-changer)
- [⭐ Core Features](#️-core-features)
- [🏗️ Architecture](#️-architecture)
  - [Project Layout & Build Configuration](#project-layout--build-configuration)
  - [Module Metadata & Resources](#module-metadata--resources)
  - [Hook Implementation Patterns](#hook-implementation-patterns)
- [🛠️ Development Workflow](#️-development-workflow)
- [⚙️ Getting Started](#️-getting-started)
  - [Prerequisites](#prerequisites)
  - [Build & Installation](#build--installation)
- [🤝 Contributing](#-contributing)

---

## ✨ Overview: The Game Changer

The **LSPosed Modular Framework** revolutionizes how you build LSPosed modules. While LSPosed provides the fundamental hooking capabilities, this framework offers a comprehensive ecosystem around it, addressing common developer pain points and introducing modern development paradigms.

**Why is it a game changer compared to using LSPosed alone?**

1.  **Simplified Xposed API Integration**:
    *   **No More AAR/JitPack Headaches**: Forget manually downloading `xposed-api.aar` files or wrestling with JitPack build issues. The framework integrates `libxposed-api` directly as a **local source dependency** (`libxposed-api` directory).
    *   **Guaranteed Consistency**: All modules use the exact same API version, built from source within your project.
    *   **Offline Builds & Easy Debugging**: Access to the API source code improves IDE integration and allows for offline builds.

2.  **Modernized Development Workflow**:
    *   **Annotation-Driven Development**: Define module properties, scopes, and capabilities directly in your Java/Kotlin code using annotations like `@XposedPlugin`. This reduces boilerplate (e.g., `module.prop`, `assets/xposed_init`) and enables compile-time checks.
    *   **Hot-Reloading**: Dramatically speed up your development. Make code changes and see them reflected almost instantly on your device **without a reboot**.
    *   **JSON-Powered Configuration**: Easily define module settings UIs (`settings.json`) that are automatically rendered in the LSPosed Manager and manage inter-module dependencies (`module-info.json`).

3.  **Robust & Maintainable Modules**:
    *   **Modular Architecture**: Encourages breaking down complex features into smaller, reusable, and independent modules.
    *   **Standardized API Usage**: Promotes the use of the modern `io.github.libxposed.api` (e.g., `XposedInterface`, `Hooker` classes) for cleaner, more maintainable hook implementations.
    *   **Centralized Utilities**: Provides common services for logging, configuration management, and potentially more, reducing redundant code in your modules.

This framework is designed for **Android 14+** (specifically tested on OnePlus 12 with OxygenOS 15.0, but compatible with broader Android versions) and aims to make LSPosed module development as efficient and enjoyable as modern Android application development.

---

## ⭐ Core Features

- 🎯 **Annotation-Driven Development**:
  - `@XposedPlugin` for module metadata (name, version, scope, author, etc.).
  - `@HotReloadable` to enable seamless code updates during development.
  - Eliminates manual descriptor files and brings compile-time validation.
- 🔄 **Hot-Reload Architecture**:
  - Instantly push Java/Kotlin code changes to a running app or the system.
  - `onHotReload()` lifecycle method in modules for state cleanup and re-hooking (a feature provided by this Wobbz LSPosed Framework).
  - Drastically reduces development and testing time.
- 🧩 **Integrated `libxposed-api` Source**:
  - The `libxposed-api` is included as a local project module.
  - Modules depend on it via `compileOnly project(':libxposed-api:api')`.
  - Ensures API consistency and simplifies builds.
- 🎨 **Dynamic Settings UI**:
  - Define module settings using a simple `settings.json` schema.
  - The framework (or an associated utility) can generate the necessary UI for LSPosed Manager.
  - Type-safe access to settings within your module code.
- 📦 **Dependency System**:
  - Manage inter-module dependencies using `module-info.json`.
  - Specify version constraints and load order.
  - Facilitates building complex systems from smaller, focused modules.
- 📞 **Direct Inter-Module Communication (Via Framework Services)**:
  - The framework may provide a service discovery mechanism (e.g., a `FeatureManager`) allowing modules to expose and consume direct APIs from one another, enabling tighter integration beyond typical Xposed hook interactions. (See `DeepIntegrator` and `SuperPatcher` API docs for conceptual examples).
- 🛡️ **Security & Control Framework**: 
  - Provides a structure for implementing fine-grained permission checks and policy enforcement. Modules like `NetworkGuard` (network activity control) and `PermissionOverride` (app permission management) demonstrate concrete security and control capabilities.
- 📊 **Analytics & Diagnostics Support**: 
  - Infrastructure to aid development and monitoring. Modules like `DebugAll` (dynamically setting application debug flags) showcase diagnostic utilities.

---

## 🚀 Example Modules Showcase

This framework hosts a variety of powerful modules, demonstrating its versatility. Here are the examples currently available in the `modules/` directory:

*   **`NetworkGuard`**: Provides advanced network filtering, monitoring, and protection by hooking core networking APIs and integrating with a central `SecurityManager`.
*   **`IntentMaster`**: Offers robust interception, modification, redirection, and logging of Android Intents based on a flexible JSON rule system.
*   **`DeepIntegrator`**: Enables advanced integration with and exposure of internal or normally inaccessible Android application components, working potência_concatenada_recursiva_em_caudally with `IntentMaster`.
*   **`DebugAll`**: Allows dynamic toggling of debugging flags (e.g., `FLAG_DEBUGGABLE`) for selected applications based on configuration.
*   **`SuperPatcher`**: An advanced module for applying low-level, generic, and flexible patches, potentially including bytecode manipulation, driven by its own configuration or a (hypothetical) service API.
*   **`PermissionOverride`**: Enables fine-grained control over Android application permissions, allowing configured overrides of the standard Android permission model.

These modules leverage the framework's features, including the local `libxposed-api`, annotation-driven development, and JSON-based configurations.

---

## 🏗️ Architecture

### Project Layout & Build Configuration

Reflects the structure outlined in `Docs/Blueprint.md`:
```
LSPosed-Modules/
├── build.gradle              # Root build file (ext properties for SDK versions, Java version)
├── settings.gradle           # Includes :framework, :libxposed-api:api, and all :modules/*
├── gradle.properties         # Gradle tuning
├── libxposed-api/            # LOCAL SOURCE for libxposed API
│   └── api/                  # The actual API library project
├── framework/                # Core framework application/library (hosts modules)
└── modules/                  # Your feature modules
    ├── YourModule1/
    │   ├── build.gradle      # Depends on project(':libxposed-api:api'), project(':framework')
    │   ├── src/main/java/    # Module code using @XposedPlugin
    │   ├── module-info.json  # Inter-module dependencies
    │   └── settings.json     # UI schema for this module
    └── YourModule2/
        └── ...
```

**Key `build.gradle` Snippets:**

*   **Root `build.gradle`**:
    ```groovy
    ext {
        minSdk = 30 // Example: Android 11
        targetSdk = 34 // Example: Android 14
        compileSdk = 34
        javaVersion = JavaVersion.VERSION_17 // CRITICAL: Use Java 17
    }
    ```
*   **Module `build.gradle` (`modules/YourModule/build.gradle`)**:
    ```groovy
    dependencies {
        compileOnly project(':libxposed-api:api') // Use the local API source
        implementation project(':framework')      // If your module depends on shared framework code
        // ... other dependencies
    }
    ```

### Module Metadata & Resources

*   **Module Annotation (`@XposedPlugin`)**:
    (Defined in `Docs/Blueprint.md` or framework source)
    ```java
    @XposedPlugin(
      name = "My Awesome Module",
      version = "1.0.1",
      description = "Does awesome things with the new API!",
      scope = {"com.android.systemui", "com.android.settings"},
      author = "Your Name"
    )
    @HotReloadable // Enable hot-reloading for this module
    public class MyAwesomeModule implements IXposedModule { // Use IXposedModule or your framework's interface
        // ... implementation using XposedInterface
    }
    ```

*   **Dependencies & Metadata (`module-info.json`)**:
    (As per `Docs/Blueprint.md`)
    ```json
    {
      "id": "my-awesome-module",
      "version": "1.0.1",
      "dependencies": [
        { "id": "another-module-id", "version": ">=1.2.0" }
      ]
    }
    ```

*   **Settings UI (`settings.json`)**:
    (As per `Docs/Blueprint.md`)
    ```json
    {
      "$schema": "https://json-schema.org/draft-07/schema#",
      "type": "object",
      "properties": {
        "enableAwesomeFeature": {
          "type": "boolean",
          "title": "Enable Awesome Feature",
          "default": true
        },
        "awesomeLevel": {
          "type": "integer",
          "title": "Level of Awesomeness",
          "minimum": 1,
          "maximum": 10,
          "default": 5
        }
      }
    }
    ```

### Hook Implementation Patterns

Utilize the modern `io.github.libxposed.api`. The Wobbz LSPosed Framework provides `com.wobbz.framework.IModulePlugin` (which extends the base `io.github.libxposed.api.XposedModuleInterface`) and `com.wobbz.framework.IHotReloadable` for enhanced module development. It's recommended to use these framework-specific interfaces.

1.  **Basic Hook with `XposedInterface` and `Hooker`** (using `IModulePlugin`):
    ```java
    // In your module class that implements com.wobbz.framework.IModulePlugin
    // Example: within onPackageLoaded(PackageLoadedParam param) or a method called from there

    public void hookSomething(PackageLoadedParam param) { // PackageLoadedParam from io.github.libxposed.api.XposedModuleInterface
        XposedInterface xposedInterface = param.getXposed(); // Obtain XposedInterface from param
        String targetPackageName = param.getPackageName();

        if ("com.example.app".equals(targetPackageName)) {
            try {
                // Find the class and method you want to hook
                // Prefer standard Java reflection or XposedInterface helpers if available
                Class<?> targetClass = xposedInterface.loadClass("com.example.app.TargetClass");
                // Example using standard Java reflection:
                Method targetMethod = targetClass.getDeclaredMethod("targetMethodName", String.class, int.class);
                targetMethod.setAccessible(true); // Important if method is not public
                
                // Hook the method
                // Store the unhooker if you need to unhook later (e.g., for hot-reload)
                MethodUnhooker<?> unhooker = xposedInterface.hook(targetMethod, MyTargetMethodHooker.class);
                xposedInterface.log("Successfully hooked targetMethodName in " + targetPackageName);

            } catch (Throwable t) {
                xposedInterface.log("Failed to hook targetMethodName in " + targetPackageName);
                xposedInterface.log(t);
            }
        }
    }

    // Separate Hooker implementation class
    public static class MyTargetMethodHooker implements Hooker {
        @Override
        public void beforeHook(HookParam param) throws Throwable {
            XposedInterface xposed = param.getXposed(); // Get XposedInterface from HookParam
            xposed.log("targetMethodName: beforeHook called!");
            // param.args contains method arguments
            // param.setResult(...) to skip original method and set a result
            // param.setThrowable(...) to throw an exception
        }

        @Override
        public void afterHook(HookParam param) throws Throwable {
            XposedInterface xposed = param.getXposed();
            xposed.log("targetMethodName: afterHook called!");
            // param.getResult() to get/modify the original method's result
        }
    }
    ```

2.  **Hot-Reload Support**:
    ```java
    @HotReloadable // Wobbz Framework Annotation
    public class MyModule implements IModulePlugin, IHotReloadable { // Wobbz Framework Interfaces
        private List<XposedInterface.MethodUnhooker<?>> mActiveHooks = new ArrayList<>();
        private XposedInterface mXposedInterface; // Store if needed across different lifecycle/hook calls
        private Context mModuleContext; // Store if needed

        @Override
        public void initialize(Context context, XposedInterface xposedInterface) {
            this.mModuleContext = context;
            this.mXposedInterface = xposedInterface;
            // Initial loading of settings or one-time setup
        }

        @Override
        public void onPackageLoaded(PackageLoadedParam param) {
            // It's often better to use the XposedInterface from the param for specific package hooks
            // applyHooks(param.getXposed(), param.getPackageName(), param.getClassLoader());
            // Or, if you've stored a general one from initialize:
            applyHooks(this.mXposedInterface, param.getPackageName(), param.getClassLoader());
        }

        private void applyHooks(XposedInterface xposed, String packageName, ClassLoader classLoader) {
            // Note: Hot reload might clear mActiveHooks. If hooks are package-specific,
            // ensure this method is called appropriately for each relevant package after hot reload.
            
            if ("com.example.app".equals(packageName)) {
                try {
                    Class<?> targetClass = xposed.loadClass("com.example.app.TargetClass");
                    Method someMethod = targetClass.getDeclaredMethod("someMethod");
                    someMethod.setAccessible(true);
                    MethodUnhooker<?> unhooker = xposed.hook(someMethod, SomeMethodHooker.class);
                    if (unhooker != null) mActiveHooks.add(unhooker);
                } catch (Throwable t) {
                    xposed.log(t);
                }
            }
        }
        
        private void clearAllHooks() {
            if (mXposedInterface != null) {
                 mXposedInterface.log("Clearing " + mActiveHooks.size() + " hooks for hot-reload.");
            }
            for (XposedInterface.MethodUnhooker<?> unhooker : mActiveHooks) {
                unhooker.unhook();
            }
            mActiveHooks.clear();
        }

        @Override
        public void onHotReload() { // Signature from com.wobbz.framework.IHotReloadable
            if (mXposedInterface != null) {
                mXposedInterface.log("Hot-reloading MyModule...");
            }
            clearAllHooks();
            // Reload settings if necessary
            // loadSettings(); 
            
            // The Wobbz LSPosed Framework will typically re-trigger onPackageLoaded 
            // for active packages, which should re-apply the hooks.
            // If specific re-initialization is needed here (e.g. for system_server hooks 
            // not tied to onPackageLoaded), add it.
            if (mXposedInterface != null) {
                mXposedInterface.log("Hot-reload completed for MyModule. Hooks will be re-applied on package load.");
            }
        }
        
        // Hooker implementation (e.g., SomeMethodHooker)
        public static class SomeMethodHooker implements Hooker { /* ... */ }

        // Other IModulePlugin methods (onSystemServerLoaded etc.)
    }
    ```

### Inter-Module Services & Communication

Beyond typical Xposed interactions (where modules affect system/app behavior that other modules might then observe), this Wobbz LSPosed Framework can facilitate more direct inter-module communication.

- **Service Discovery (e.g., `FeatureManager`)**: If a central `FeatureManager` or a similar service locator pattern is implemented within the framework, modules can register themselves as service providers and other modules can look up and use these services.
- **Direct API Calls**: This allows one module to call public methods of another module instance directly, enabling a richer, more coupled interaction model when needed. This pattern is hinted at in the API designs of modules like `DeepIntegrator` and `SuperPatcher`.
- **Dependency Management**: The `module-info.json` file helps manage explicit dependencies, which is crucial if direct API calls are to be made reliably.

This direct service-oriented approach is powerful but should be used judiciously, as it creates tighter coupling than traditional Xposed hook-based interactions.

---

## 🛠️ Development Workflow

The workflow is significantly streamlined:

1.  **Create New Module**:
    *   Set up a new module directory under `modules/`.
    *   Create your main module class (e.g., `MyModule.java`) annotated with `@XposedPlugin`.
    *   If needed, add `module-info.json` for dependencies and `settings.json` for UI.
    *   Ensure your module's `build.gradle` has the correct dependencies:
        ```gradle
        dependencies {
            compileOnly project(':libxposed-api:api')
            // implementation project(':framework') // If you have a shared framework module
            // ... other dependencies
        }
        ```

2.  **Implement Hooks**:
    *   Use `XposedInterface` (often obtained from `IModulePlugin` lifecycle methods like `onPackageLoaded`) and `Hooker` classes for your hooking logic.
    *   Implement the `onHotReload()` method in your `@HotReloadable` modules to correctly unhook and re-apply hooks.

3.  **Develop with Hot-Reload**:
    *   Run the development server task (e.g., `./gradlew runDevServer` - specific task name may vary based on your framework setup).
    *   Make code changes in your IDE.
    *   The framework should automatically detect changes, recompile the module, and push updates to the device, triggering `onHotReload()`.
    *   Observe changes and log output in real-time.

4.  **Build & Test**:
    *   Use standard Gradle tasks like `./gradlew build`, `./gradlew assembleRelease`.
    *   The framework might provide custom Gradle tasks for processing annotations, generating UIs, or packaging overlays, as detailed in `Docs/Automations.md` and `Docs/Blueprint.md`. (e.g. `./gradlew processAnnotations`)

---

## ⚙️ Getting Started

### Prerequisites

*   Android Studio or Cursor (latest stable version recommended).
*   **JDK 17**: Ensure your project and IDE are configured to use JDK 17.
*   LSPosed framework installed on your Android 14+ device/emulator.
*   Basic understanding of Android development and Xposed/LSPosed concepts.

### Build & Installation

1.  **Clone the Repository**:
    ```bash
    git clone <your-repository-url> LSPosed-Modular-Framework
    cd LSPosed-Modular-Framework
    ```
    If `libxposed-api` is a submodule:
    ```bash
    git submodule update --init --recursive
    ```

2.  **Open in Android Studio**:
    Import the project. Android Studio should recognize the Gradle structure, including the `libxposed-api` and other modules.

3.  **Configure Build Variants**:
    Select the appropriate build variant (e.g., `debug` or `release`) for the main framework application (if applicable) and your modules.

4.  **Build the Project**:
    ```bash
    ./gradlew assembleDebug # or assembleRelease
    ```
    This will build the framework and all included modules. The `libxposed-api` will be built as part of this process.

5.  **Install**:
    *   Install the main framework APK (if your framework is structured as a host app).
    *   Install your individual module APKs.
    *   Activate them in the LSPosed Manager and select their target scopes.
    *   Reboot if prompted by LSPosed Manager (initial activation usually requires this). Subsequent updates via hot-reload should not.

**Important Note on `libxposed:api` Dependency**:
The previous complex steps for handling `libxposed:api` by manually building and copying AARs are **NO LONGER NECESSARY** with this framework's approach of including `libxposed-api` as a local source dependency. The build system handles it automatically.

---

## 🤝 Contributing

1.  Fork the repository.
2.  Create your feature branch (`git checkout -b feature/AmazingFeature`).
3.  Commit your changes (`git commit -m 'Add some AmazingFeature'`).
4.  Push to the branch (`git push origin feature/AmazingFeature`).
5.  Open a Pull Request.

Contributions are welcome! Please check the repository for any contribution guidelines or open an issue to discuss potential changes.

---

## 📄 License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for more details.