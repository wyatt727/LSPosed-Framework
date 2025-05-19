# 🚀 LSPosed Modular Framework

[![Build Status](https://img.shields.io/github/actions/workflow/status/wobbz/LSPosedFramework/build.yml?branch=main)](https://github.com/wobbz/LSPosedFramework/actions)
[![Release](https://img.shields.io/github/v/release/wobbz/LSPosedFramework)](https://github.com/wobbz/LSPosedFramework/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

> A **modern**, **annotation-driven**, and **Android 15-ready** host framework for all your LSPosed modules—featuring hot-reload development, auto-generated UI, and seamless dependency management.

---

## 📋 Table of Contents

- [✨ Overview](#-overview)  
- [⭐ Features](#-features)  
- [🏗️ Architecture](#️-architecture)  
  - [Project Layout & Build Configuration](#project-layout--build-configuration)  
  - [Module Metadata & Resources](#module-metadata--resources)  
  - [Hook Implementation Patterns](#hook-implementation-patterns)  
- [🔧 Best Practices](#-best-practices)  
  - [Android 15 Specific Adjustments](#android-15-specific-adjustments)
  - [Package Naming Conventions](#package-naming-conventions)
- [🛠️ Development Workflow](#️-development-workflow)  
- [⚙️ Getting Started](#️-getting-started)  
  - [Prerequisites](#prerequisites)  
  - [Build & Installation](#build--installation)  
- [🤝 Contributing](#-contributing)  
- [📄 License](#-license)

---

## ✨ Overview

The **LSPosed Modular Framework** is a modern, feature-rich Android library that:

- Uses **Java annotations** (`@XposedPlugin`, `@HotReloadable`) for module metadata and lifecycle
- Provides **JSON-based configuration** for settings UI and dependencies
- Supports **hot-reload development** without device reboots
- Manages **module dependencies** and version constraints
- Handles **remote updates** via CDN with signature verification
- Includes powerful core modules:
  - **IntentMaster**: Advanced intent manipulation and routing
  - **NetworkGuard**: Comprehensive network traffic control
  - **PermissionOverride**: Fine-grained permission management
  - **DeepIntegrator**: Component exposure and integration
  - **SuperPatcher**: Low-level system modifications
  - **DebugAll**: Application debugging utilities

Designed for **OnePlus 12 (arm64, Android 15, OxygenOS 15.0)** but fully compatible with any Android 14+ device.

---

## ⭐ Features

- 🎯 **Annotation-Driven Development**: 
  - `@XposedPlugin` for module metadata
  - `@HotReloadable` for development workflow
  - Compile-time validation and type safety
- 🔄 **Hot-Reload Architecture**: 
  - Live code updates without reboots
  - State preservation between reloads
  - Automatic hook cleanup and reapplication
- 🎨 **Dynamic Settings UI**: 
  - JSON schema-based UI generation
  - Real-time configuration updates
  - Type-safe settings management
- 📦 **Dependency System**: 
  - Version constraints in `module-info.json`
  - Automatic dependency resolution
  - Conflict detection and reporting
- 🔒 **Security Framework**: 
  - Permission management
  - Network traffic control
  - Component access control
- 📊 **Analytics & Diagnostics**: 
  - Hook performance metrics
  - Memory usage tracking
  - Web-based diagnostics interface

---

## 🏗️ Architecture

### Project Layout & Build Configuration

```
LSPosedFramework/
├── framework/        ← Core library
│   ├── src/main/java/com/wobbz/framework/
│   │   ├── annotations/     ← @XposedPlugin, @HotReloadable
│   │   ├── analytics/      ← Performance tracking
│   │   ├── security/       ← Security management
│   │   ├── development/    ← Development tools
│   │   └── ui/            ← Settings UI generation
│   └── src/main/resources/
│       └── META-INF/xposed/
├── modules/          ← Core modules
│   ├── IntentMaster/
│   │   ├── module-info.json    ← Dependencies & metadata
│   │   ├── settings.json       ← UI configuration
│   │   └── src/main/.../IntentMasterModule.java
│   ├── NetworkGuard/
│   ├── PermissionOverride/
│   ├── DeepIntegrator/
│   ├── SuperPatcher/
│   └── DebugAll/
└── docs/            ← Documentation
```

### Module Metadata & Resources

* **Annotation-Based Configuration**:

```java
@XposedPlugin(
  id = "com.wobbz.debugall",
  name = "Debug-All",
  description = "Force-enable DEBUGGABLE on apps",
  version = "1.0.0",
  scope = {"android", "com.android.systemui"},
  permissions = {"android.permission.READ_LOGS"}
)
@HotReloadable
public class DebugAllModule implements IModulePlugin {
  // Implementation
}
```

* **Dependencies & Metadata** (`module-info.json`):

```json
{
  "id": "com.wobbz.debugall",
  "version": "1.0.0",
  "minApi": 34,
  "maxApi": 35,
  "dependsOn": {
    "com.wobbz.superpatcher": ">=1.2.0"
  },
  "conflicts": [
    "com.legacy.debugger"
  ]
}
```

* **Settings UI** (`settings.json`):

```json
{
  "fields": [
    {
      "key": "debugLevel",
      "type": "choice",
      "label": "Debug Level",
      "options": ["info", "debug", "verbose"]
    },
    {
      "key": "targetApps",
      "type": "app_list",
      "label": "Target Applications"
    }
  ]
}
```

### Hook Implementation Patterns

1. **Hot-Reload Support**
```java
@HotReloadable
public class MyModule implements IModulePlugin {
  @Override
  public void onHotReload() {
    // Cleanup and reinitialize hooks
  }
}
```

2. **Safe Execution & Analytics**
```java
try {
  long trackingId = mAnalyticsManager.trackHookStart(hookId, MODULE_ID, packageName);
  // hook logic
  mAnalyticsManager.trackHookEnd(trackingId, true);
} catch (Throwable t) {
  LoggingHelper.error(TAG, "Hook failed", t);
  mAnalyticsManager.trackHookEnd(trackingId, false);
}
```

3. **Security Integration**
```java
if (mSecurityManager != null && 
    !mSecurityManager.shouldAllowConnection(packageName, host, port, SecurityManager.PROTO_TCP)) {
  throw new SecurityException("Connection blocked by NetworkGuard");
}
```

---

## 🔧 Best Practices

### Development Workflow

1. **Create New Module Structure**

```bash
modules/NewModule/
├── src/main/java/com/wobbz/newmodule/
│   └── NewModule.java           # @XposedPlugin annotated class
├── module-info.json             # Dependencies & metadata
└── settings.json               # UI configuration
```

2. **Add Module Configuration**

```java
@XposedPlugin(
  id = "com.wobbz.newmodule",
  name = "New-Module"
)
@HotReloadable
public class NewModule implements IModulePlugin {
  // Implementation
}
```

3. **Configure Dependencies**

```json
{
  "dependsOn": {
    "com.wobbz.superpatcher": "^2.0.0"
  }
}
```

4. **Define Settings UI**

```json
{
  "fields": [
    {
      "key": "enabled",
      "type": "boolean",
      "label": "Enable Feature"
    }
  ]
}
```

5. **Development**
```bash
# Start hot-reload server
./gradlew runDevServer

# Watch for changes
./gradlew watchModules
```

### Package Naming Conventions

* **Use lowercase everywhere**: 
  * Framework: `com.wobbz.framework.*`
  * Modules: `com.wobbz.debugall`, `com.wobbz.networkguard`
  * Module IDs in annotations: `com.wobbz.debugall`

### Android 15 Specific Adjustments

* **Hook Signatures**: Use `framework.art` for Android 15
* **Hidden-API Enforcement**: Leverage LSPosed's allowlist
* **SELinux Contexts**: Handle overlay permissions
* **Vendor Overlays**: Use `overlayfs` when possible
* **Scope Updates**: Manage ART cache invalidation

---

## 🛠️ Development Workflow

1. **Create New Module**

```bash
./gradlew createModule -PmoduleName=NewFeature
```

2. **Add Annotations**

```java
@XposedPlugin(
  id = "com.wobbz.newfeature",
  name = "New Feature"
)
@HotReloadable
public class NewFeatureModule implements IModulePlugin {
  // Implementation
}
```

3. **Configure Dependencies**

```json
{
  "dependsOn": {
    "com.wobbz.coreutils": "^2.0.0"
  }
}
```

4. **Define Settings UI**

```json
{
  "fields": [
    {
      "key": "enabled",
      "type": "boolean",
      "label": "Enable Feature"
    }
  ]
}
```

5. **Development**
```bash
# Start hot-reload server
./gradlew runDevServer

# Watch for changes
./gradlew watchModules
```

6. **Build & Deploy**
```bash
./gradlew assembleRelease
./gradlew uploadToCDN  # For remote distribution
```

---

## ⚙️ Getting Started

### Prerequisites

* Android Studio 2023.1+
* JDK 17+
* Android SDK (API 35)
* LSPosed Framework 1.0+

### Build & Installation

1. Clone the repository
2. Configure signing keys for remote updates
3. Run `./gradlew assembleRelease`
4. Install via LSPosed Manager

#### Handling `libxposed:api` Dependency (Important)

The project relies on the `libxposed:api` (specifically `io.github.libxposed:api`) for Xposed functionalities. As of `0.9.2` (tag `100`), this dependency may fail to resolve correctly from JitPack due to issues with JitPack's build environment (often using an older JDK like Java 8) conflicting with the Android Gradle Plugin version used by `libxposed:api` which requires a newer JDK (e.g., Java 11+).

To resolve this, you'll need to build the `libxposed:api` AAR (Android Archive) file locally and include it directly in the modules that require it (e.g., the `framework` module and potentially other modules like `PermissionOverride`).

**Steps:**

1.  **Clone the `libxposed/api` repository:**
    ```bash
    git clone https://github.com/libxposed/api.git libxposed-api
    cd libxposed-api
    ```

2.  **Configure Android SDK for the cloned repository:**
    Ensure you have an Android SDK installed and its path is known. Create or update a `local.properties` file in the root of the cloned `libxposed-api` directory:
    ```properties
    sdk.dir=/path/to/your/android/sdk
    ```
    (Replace `/path/to/your/android/sdk` with the actual path, e.g., `~/Library/Android/sdk` on macOS or the path pointed to by `$ANDROID_HOME`).

3.  **Build the AAR:**
    From the root of the `libxposed-api` directory, run the following command:
    ```bash
    ./gradlew :api:assembleRelease
    ```
    If you encounter issues with the `:checks:compileKotlin` task (e.g., due to JVM target compatibility), you can try excluding it:
    ```bash
    ./gradlew :api:assembleRelease -x :checks:compileKotlin
    ```
    This will generate an AAR file located at `api/build/outputs/aar/api-release.aar`.

4.  **Copy the AAR to your project:**
    *   Create a `libs` directory within each module that needs this dependency (e.g., `/LSPosedFramework/framework/libs/` and `/LSPosedFramework/modules/YourModule/libs/`).
    *   Copy the `api-release.aar` into these `libs` directories. You might want to rename it for clarity, for example, to `xposed-api.aar`.

5.  **Update module `build.gradle` files:**
    In the `build.gradle` file of each module that now includes the local AAR (e.g., `framework/build.gradle`), modify the dependency declaration from:
    ```gradle
    // compileOnly "io.github.libxposed:api:${rootProject.ext.xposedApiVersion}"
    ```
    to:
    ```gradle
    compileOnly files('libs/xposed-api.aar') // Or your chosen AAR filename
    ```

After these steps, clean and rebuild your project. This ensures that the `libxposed:api` is correctly provided, bypassing the JitPack resolution issues for this specific version.

**Note:** This workaround is necessary due to the current build state of `libxposed:api` on JitPack. Ideally, future versions or configurations of `libxposed:api` might resolve this, allowing direct fetching from JitPack. Always check the JitPack build logs for the specific version you intend to use.

---

## 🤝 Contributing

1. Fork the repository
2. Create your feature branch
3. Add tests and documentation
4. Submit a pull request

See [CONTRIBUTING.md](CONTRIBUTING.md) for details.

---

## 📄 License

Distributed under the MIT License. See [LICENSE](LICENSE) for details.