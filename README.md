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
- [🛠️ Development Workflow](#️-development-workflow)  
- [⚙️ Getting Started](#️-getting-started)  
  - [Prerequisites](#prerequisites)  
  - [Build & Installation](#build--installation)  
- [🤝 Contributing](#-contributing)  
- [📄 License](#-license)

---

## ✨ Overview

The **LSPosed Modular Framework** is a modern, feature-rich Android library that:

- Uses **Java annotations** (`@XposedPlugin`) for module metadata and discovery
- Supports **hot-reload development** without device reboots
- Provides **auto-generated settings UI** from JSON schemas
- Manages **module dependencies** and version constraints
- Handles **remote updates** via CDN with signature verification
- Packages **resource overlays** automatically
- Supports **Android 15 (API 35)** with the latest libxposed/shim versions

Designed for **OnePlus 12 (arm64, Android 15, OxygenOS 15.0)** but fully compatible with any Android 14+ device.

---

## ⭐ Features

- 🎯 **Annotation-Driven**: Replace YAML with `@XposedPlugin` for compile-time validation
- 🔄 **Hot-Reload**: Develop and test changes without rebooting
- 🎨 **Auto UI**: Generate LSPosed Manager settings from JSON schema
- 📦 **Smart Dependencies**: Declare and validate module relationships
- 🚀 **Remote Updates**: Secure, CDN-based module distribution
- 🎭 **Resource Overlays**: Automatic RRO packaging and management
- ⚡ **Lean Runtime**: Optimized hook resolution and caching
- 🔒 **Safe Execution**: Comprehensive error handling and recovery

---

## 🏗️ Architecture

### Project Layout & Build Configuration

```
LSPosedFramework/
├── settings.gradle
├── build.gradle      ← root ext { xposedApiVersion, minSdk, targetSdk, compileSdk }
├── framework/        ← core library
│   ├── build.gradle  ← annotation processor setup
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/java/com/wobbz/framework/
│       │   ├── annotations/     ← @XposedPlugin, @HotReloadable
│       │   ├── ui/             ← Settings UI generation
│       │   ├── updates/        ← Remote update client
│       │   └── hot-reload/     ← Development server
│       └── main/resources/
└── modules/          ← feature sub-modules
    ├── DebugAll/
    │   ├── build.gradle
    │   ├── module-info.json    ← dependencies & conflicts
    │   ├── settings.json       ← UI configuration
    │   └── src/main/java/com/wobbz/debugall/
    │       └── DebugAllModule.java  ← @XposedPlugin annotation
    └── AdBlocker/
        ├── build.gradle
        ├── module-info.json
        └── src/...
```

### Module Metadata & Resources

* **Annotation-Based Configuration**:

```java
@XposedPlugin(
  id = "com.wobbz.DebugAll",
  name = "Debug-All",
  description = "Force-enable DEBUGGABLE on all apps",
  scope = {"com.android.systemui", "com.chrome.browser"}
)
@HotReloadable
public class DebugAllModule implements IModulePlugin {
  // Implementation
}
```

* **Dependencies & Conflicts** (`module-info.json`):

```json
{
  "dependsOn": {
    "com.wobbz.CoreUtils": ">=1.2.0"
  },
  "conflictsWith": [
    "com.otherorg.LegacyHooks"
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

2. **Safe Execution & Logging**
```java
try {
  // hook logic
} catch (Throwable t) {
  LoggingHelper.error("MyModule", "Hook failed", t);
}
```

3. **Resource Overlays**
```
res/overlay/com.android.systemui/
  layout/
    status_bar.xml
  values/
    colors.xml
```

---

## 🔧 Best Practices

* **Development Workflow**
  * Enable hot-reload in `build.gradle`
  * Use `./gradlew runDevServer` for live updates
  * Monitor changes via LoggingHelper

* **Dependency Management**
  * Declare version constraints in `module-info.json`
  * Use semantic versioning
  * Handle conflicts explicitly

* **Settings UI**
  * Define UI schema in `settings.json`
  * Use typed fields for validation
  * Support i18n via resource strings

* **Remote Updates**
  * Sign updates with Ed25519
  * Support delta downloads
  * Handle background updates

* **Resource Overlays**
  * Follow Android RRO conventions
  * Test on multiple Android versions
  * Handle overlay conflicts

### Android 15 Specific Adjustments

* **Hook Signatures** moved from `framework.jar` to `framework.art`—search both
* **Hidden-API Enforcement**—use LSPosed's built-in allowlist
* **SELinux Contexts**—handle overlay permissions
* **Vendor Overlays**—use `overlayfs` when possible
* **Scope Updates**—manage ART cache invalidation

---

## 🛠️ Development Workflow

1. **Create New Module**

```bash
./gradlew createModule -PmoduleName=NewFeature
```

2. **Add Annotations**

```java
@XposedPlugin(
  id = "com.wobbz.NewFeature",
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
    "com.wobbz.CoreUtils": "^2.0.0"
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
* JDK 8+
* Android SDK (API 35)
* LSPosed Framework 1.0+

### Build & Installation

1. Clone the repository
2. Configure signing keys for remote updates
3. Run `./gradlew assembleRelease`
4. Install via LSPosed Manager

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
