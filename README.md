# 🚀 LSPosed Modular Framework

[![Build Status](https://img.shields.io/github/actions/workflow/status/yourorg/LSPosedFramework/build.yml?branch=main)](https://github.com/yourorg/LSPosedFramework/actions)
[![Release](https://img.shields.io/github/v/release/yourorg/LSPosedFramework)](https://github.com/yourorg/LSPosedFramework/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

> A **scalable**, **descriptor-driven**, and **Android 15-ready** host framework for all your LSPosed modules—bundle dozens of in-memory hooks into one cohesive APK.

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
- [🛠️ Extension Workflow](#️-extension-workflow)  
- [⚙️ Getting Started](#️-getting-started)  
  - [Prerequisites](#prerequisites)  
  - [Build & Installation](#build--installation)  
- [🤝 Contributing](#-contributing)  
- [📄 License](#-license)

---

## ✨ Overview

The **LSPosed Modular Framework** is a single, unified Android library that:

- Hosts **multiple LSPosed feature modules** in one APK  
- Uses **declarative descriptors** (`descriptor.yaml`) to drive metadata and scope  
- Automatically **generates** the required `META-INF/xposed/*` files at build time  
- Supports **Android 15 (API 35)** with the latest libxposed/shim versions  
- Provides **shared utilities**, **logging**, and **reflection helpers**  

Designed for **OnePlus 12 (arm64, Android 15, OxygenOS 15.0)** but fully compatible with any Android 14+ device.

---

## ⭐ Features

- 🎯 **Descriptor-Driven**: Define module ID, name, entry classes, and scope in YAML.  
- 🧩 **Plugin Architecture**: Drop in new features without boilerplate—each lives under `modules/FeatureName`.  
- ⚡ **Lean Hooks**: Reflective targets resolved once; minimal runtime overhead.  
- 🔒 **Safe Execution**: All hook logic wrapped in try/catch to prevent app crashes.  
- 🔄 **Dynamic Scope**: Limit hooks to specific packages or processes via `scope.list`.  
- 📜 **Centralized Config**: Single `module.prop` and versioning for the entire suite.  

---

## 🏗️ Architecture

### Project Layout & Build Configuration

```
LSPosedFramework/
├── settings.gradle
├── build.gradle      ← root ext { xposedApiVersion, minSdk, targetSdk, compileSdk, javaVersion }
├── framework/        ← core library
│   ├── build.gradle
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/java/com/yourorg/framework/
│       │   ├── IModulePlugin.java
│       │   └── PluginManager.java
│       └── main/resources/META-INF/xposed/
│           ├── java_init.list.tpl
│           ├── scope.list.tpl
│           └── module.prop.tpl
└── modules/          ← feature sub-modules
    ├── DebugAll/
    │   ├── build.gradle
    │   ├── descriptor.yaml
    │   └── src/main/java/com/yourorg/debugall/DebugAllModule.java
    └── AdBlocker/
        ├── build.gradle
        ├── descriptor.yaml
        └── src/…
```

- **settings.gradle** dynamically includes `:framework` and each `modules/*`.  
- **Root build.gradle** defines common versions:  

```groovy
ext {
  xposedApiVersion = '0.4.2'
  minSdk           = 21
  targetSdk        = 35
  compileSdk       = 35
  javaVersion      = JavaVersion.VERSION_1_8
}
```

### Module Metadata & Resources

* **descriptor.yaml** (per feature):

```yaml
id: com.yourorg.DebugAll
name: Debug-All
description: Force-enable DEBUGGABLE on all apps
entry_classes:
  - com.yourorg.debugall.DebugAllModule
scope:
  - com.android.systemui
  - com.chrome.browser
```

* At build time, the framework:

  1. Parses all `descriptor.yaml`
  2. Merges into:
     * `java_init.list`
     * `scope.list`
     * `module.prop`

* **Manifest** uses:

```xml
<application
  android:label="@string/app_name"
  android:description="@string/app_desc">
  <!-- No legacy xposedmeta tags needed -->
</application>
```

### Hook Implementation Patterns

1. **Minimal Impact**
   * Resolve reflective lookups in `initZygote()`.
   * Early return in `handleLoadPackage()` if outside feature scope.

2. **Safe Execution**

```java
try {
  // hook logic
} catch (Throwable t) {
  XposedBridge.log("[DebugAll] " + Log.getStackTraceString(t));
}
```

3. **Shared Utilities**
   * **LoggingHelper** for consistent, prefixed logs.
   * **ReflectionHelper** for safe `findClass`/`findMethod`.

---

## 🔧 Best Practices

* **Pin Versions**
  * libxposed API → `0.4.2` for Android 15
  * Android Gradle Plugin ≥ 8.1.0

* **Descriptor Validation**
  * Gradle task fails the build if `descriptor.yaml` lacks `id` or `entry_classes`.

* **Structured Logging**
  * Prefix: `[ModuleID|FeatureName]`
  * Runtime verbosity toggle via `config.properties` in `META-INF/xposed/`

* **Performance Awareness**
  * Offload heavy work to background threads.
  * Keep hooks lean to avoid polluting ART AOT profiles.

* **Thread Safety**
  * Immutable plugin lists/config maps.
  * Synchronize only when mutating shared state.

* **ProGuard & Resources**

```proguard
-keep class com.yourorg.** { *; }
```

* Avoid bundling large assets; load on demand.

### Android 15 Specific Adjustments

* **Hook Signatures** moved from `framework.jar` to `framework.art`—search both.
* **Hidden-API Enforcement**—use LSPosed's built-in allowlist, not custom hacks.
* **SELinux Contexts**—after overlays/bind-mounts, run:

```bash
chcon -R u:object_r:system_lib_file:s0 $MODDIR/system/lib64/…
```

* **Vendor Overlays**—prefer `overlayfs` over bind-mounts on `/vendor`.
* **Scope Updates**—force-stop apps to clear ART caches after scope changes.

---

## 🛠️ Extension Workflow

1. **Create Module Skeleton**

```bash
mkdir -p modules/NewFeature && cd modules/NewFeature
touch build.gradle descriptor.yaml
```

2. **Define Descriptor**

```yaml
id: com.yourorg.NewFeature
name: New Feature
description: …
entry_classes:
  - com.yourorg.newfeature.NewFeatureModule
scope:
  - com.target.app
```

3. **Implement Plugin**

```java
public class NewFeatureModule implements IModulePlugin {
  @Override
  public void initZygote(StartParam sp) {
    // cache reflection targets
  }
  @Override
  public void handleLoadPackage(LoadPackageParam lp) {
    if (!scope.contains(lp.packageName)) return;
    try {
      // hook logic
    } catch (Throwable t) {
      LoggingHelper.e("NewFeature", t);
    }
  }
}
```

4. **Build & Install**

```bash
./gradlew clean assembleRelease
adb install -r framework/build/outputs/apk/release/app-release.apk
```

5. **Enable & Reboot**
   * In LSPosed Manager → Modules → enable → select "All processes" or per-feature → reboot.

---

## 🤝 Contributing

1. Fork the repo
2. Create your feature under `modules/`
3. Follow the descriptor & plugin conventions
4. Submit a PR—ensure your descriptor and code pass validation

---

## 📄 License

Distributed under the **MIT License**. See [LICENSE](LICENSE) for details.
