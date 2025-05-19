# LSPosed Module Framework Architecture

A modern, annotation-driven framework for building and managing LSPosed modules with hot-reload support, remote updates, and auto-generated settings UI.

---

## 1. High-Level Overview

1. **Host Framework**
   * A single Android "library" project that bundles:
     * Annotation processors for plugin discovery
     * Hot-reload development server
     * Settings UI generation
     * Remote update system
     * Shared utilities (logging, reflection helpers, config loaders)

2. **Feature Modules**
   * Each feature is a self-contained module under `modules/`
   * Uses annotations instead of YAML for metadata
   * Supports hot-reloading during development
   * Can declare dependencies and conflicts

3. **Dynamic Discovery**
   * Annotation processor generates all required metadata
   * Hot-reload capability for rapid development
   * Remote update support via CDN

4. **Centralized Configuration**
   * Unified settings UI generation
   * Dependency resolution at build time
   * Resource overlay packaging

---

## 2. Directory Structure

```
/LSPosedFramework
├── build.gradle                // root Gradle config
├── settings.gradle             // module discovery
│
├── framework/                  // Host Framework
│   ├── build.gradle           // annotation processor setup
│   ├── src/
│   │   ├── main/java/…        // Core framework code
│   │   ├── processor/         // Annotation processors
│   │   └── hot-reload/        // Development server
│
└── modules/                    // Feature modules
    ├── DebugAll/
    │   ├── build.gradle       // hot-reload config
    │   ├── src/main/java/…    // Annotated module code
    │   ├── src/main/res/      // Optional resource overlays
    │   ├── module-info.json   // Dependencies & conflicts
    │   └── settings.json      // UI configuration
    │
    └── NetworkLogger/
        ├── build.gradle
        ├── src/main/java/…
        ├── module-info.json
        └── settings.json
```

---

## 3. Annotation-Driven Development

Replace YAML descriptors with Java annotations:

```java
@XposedPlugin(
  id = "com.wobbz.DebugAll",
  name = "Debug-All",
  version = "1.0.0",
  scope = {
    "android",
    "com.android.systemui"
  }
)
@HotReloadable
public class DebugAllModule implements IModulePlugin {
  @Override
  public void initZygote(StartupParam param) {
    // Initialize
  }

  @Override
  public void handleLoadPackage(LoadPackageParam param) {
    // Hook logic
  }

  @Override
  public void onHotReload() {
    // Cleanup and reinitialize
  }
}
```

---

## 4. Dependency Management

Define module relationships in `module-info.json`:

```json
{
  "dependsOn": {
    "com.wobbz.CoreUtils": ">=1.2.0",
    "com.wobbz.CommonLib": "^2.0.0"
  },
  "conflictsWith": [
    "com.otherorg.LegacyHooks"
  ],
  "provides": {
    "debuggingCapability": "1.0.0"
  }
}
```

The framework validates these at build time:
- Checks version constraints
- Builds dependency graph
- Detects conflicts
- Auto-includes required modules

---

## 5. Settings UI Generation

Define UI in `settings.json`:

```json
{
  "enabledByDefault": true,
  "fields": [
    {
      "key": "verboseLogging",
      "type": "boolean",
      "label": "Verbose Logging",
      "defaultValue": false
    },
    {
      "key": "targetApps",
      "type": "package_list",
      "label": "Target Applications",
      "description": "Select apps to apply hooks to"
    }
  ]
}
```

Framework automatically:
- Generates LSPosed Manager UI
- Handles persistence
- Provides typed access in code

---

## 6. Hot-Reload Development

Enable in `build.gradle`:

```groovy
lsposed {
  enableHotReload = true
  devServerPort = 8081
}
```

Usage:
1. Run `./gradlew runDevServer`
2. Make changes to your module
3. Changes apply instantly without reboot
4. View logs in real-time

---

## 7. Remote Updates

Configure in `update-config.json`:

```json
{
  "updateSources": [
    {
      "name": "Official CDN",
      "url": "https://cdn.wobbz.com/lsposed/modules",
      "publicKey": "BASE64_ED25519_PUBLIC_KEY"
    }
  ],
  "updateCheckInterval": 86400,
  "autoUpdate": {
    "enabled": true,
    "onlyWhenIdle": true,
    "requireWifi": true
  }
}
```

Features:
- Signed module updates
- Automatic background updates
- Bandwidth-aware downloading
- Version rollback support

---

## 8. Resource Overlays

Place overlays in standard structure:

```
modules/YourFeature/
  src/main/
    res/
      overlay/
        com.android.systemui/
          layout/
            status_bar.xml
          values/
            colors.xml
```

Framework automatically:
- Packages as RRO overlay
- Handles installation
- Manages versions

---

## 9. Adding a New Module

1. Create module directory
2. Add `@XposedPlugin` annotation
3. Implement `IModulePlugin`
4. Add `module-info.json` for dependencies
5. Create `settings.json` if needed
6. Add resource overlays if needed
7. Enable hot-reload for development

---

## 10. Benefits

* **Annotation-Driven**: No manual descriptor files
* **Hot-Reload**: Rapid development cycle
* **Auto-UI**: Generated settings screens
* **Dependency Aware**: Version constraints and conflicts
* **Remote Updates**: CDN-based module distribution
* **Resource Support**: Built-in overlay packaging
* **Type Safe**: Compile-time validation

---

This modern framework provides a complete solution for building, testing, and distributing LSPosed modules with minimal boilerplate and maximum developer productivity.
