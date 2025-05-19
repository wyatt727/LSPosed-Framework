# LSPosed Framework Automation Guide

## 1. Development Workflow Automations

### 1.1 Annotation Processing

```bash
# Automatically run when building
./gradlew processAnnotations

# Watch for changes
./gradlew processAnnotations --continuous
```

Generated files:
- `META-INF/xposed/java_init.list`
- `META-INF/xposed/scope.list`
- `module.prop`

### 1.2 Hot-Reload Development

```bash
# Start development server
./gradlew runDevServer

# Enable hot-reload for specific module
./gradlew :modules:YourModule:enableHotReload

# Watch for changes and auto-reload
./gradlew watchModules
```

### 1.3 Settings UI Generation

```bash
# Generate UI from JSON schemas
./gradlew generateSettingsUI

# Validate settings schemas
./gradlew validateSettings

# Preview generated UI
./gradlew previewSettings
```

## 2. Build System Integration

### 2.1 Dependency Resolution

```bash
# Check for conflicts
./gradlew checkDependencies

# Generate dependency graph
./gradlew generateDependencyGraph

# Validate version constraints
./gradlew validateVersions
```

### 2.2 Resource Overlay Packaging

```bash
# Package overlays
./gradlew packageOverlays

# Install overlays
./gradlew installOverlays

# Validate overlay resources
./gradlew validateOverlays
```

## 3. Continuous Integration

### 3.1 GitHub Actions Workflow

```yaml
name: CI/CD Pipeline

on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Process Annotations
        run: ./gradlew processAnnotations
        
      - name: Validate Dependencies
        run: ./gradlew checkDependencies
        
      - name: Generate Settings UI
        run: ./gradlew generateSettingsUI
        
      - name: Package Overlays
        run: ./gradlew packageOverlays
        
      - name: Run Tests
        run: ./gradlew test
        
      - name: Build Release
        run: ./gradlew assembleRelease
```

### 3.2 Remote Update Deployment

```yaml
name: Deploy Updates

on:
  release:
    types: [published]

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - name: Build Update Package
        run: ./gradlew buildUpdate
        
      - name: Sign Package
        run: ./gradlew signUpdate
        
      - name: Upload to CDN
        run: ./gradlew uploadToCDN
```

## 4. Development Tools

### 4.1 IDE Integration

Install plugins:
- LSPosed Development Support
- Hot Reload Helper
- Settings UI Preview

Enable features in `build.gradle`:
```groovy
lsposed {
  enableDevTools = true
  enableHotReload = true
  enableSettingsPreview = true
}
```

### 4.2 Debug Tools

```bash
# Monitor hot-reload status
./gradlew monitorHotReload

# View logs in real-time
./gradlew tailLogs

# Profile hook performance
./gradlew profileHooks
```

## 5. Quality Checks

### 5.1 Code Validation

```bash
# Run all checks
./gradlew check

# Individual checks
./gradlew validateAnnotations
./gradlew validateDependencies
./gradlew validateSettings
./gradlew validateOverlays
```

### 5.2 Testing

```bash
# Unit tests
./gradlew test

# Integration tests
./gradlew integrationTest

# Hot-reload tests
./gradlew testHotReload
```

## 6. Release Process

### 6.1 Build Release

```bash
# Create release build
./gradlew assembleRelease

# Sign APK
./gradlew signRelease

# Generate changelog
./gradlew generateChangelog
```

### 6.2 Publish Update

```bash
# Build update package
./gradlew buildUpdate

# Sign update
./gradlew signUpdate

# Upload to CDN
./gradlew uploadToCDN

# Notify clients
./gradlew notifyClients
```

## 7. Monitoring & Analytics

### 7.1 Performance Monitoring

```bash
# Monitor hook performance
./gradlew monitorHooks

# Track memory usage
./gradlew trackMemory

# Generate performance report
./gradlew performanceReport
```

### 7.2 Usage Analytics

```bash
# Collect metrics
./gradlew collectMetrics

# Generate analytics dashboard
./gradlew generateDashboard

# Export usage data
./gradlew exportAnalytics
```

## 8. Maintenance Tasks

### 8.1 Cleanup

```bash
# Clean build files
./gradlew clean

# Clean hot-reload cache
./gradlew cleanHotReload

# Clean generated UI
./gradlew cleanGeneratedUI
```

### 8.2 Updates

```bash
# Update dependencies
./gradlew updateDependencies

# Update IDE plugins
./gradlew updatePlugins

# Update development tools
./gradlew updateDevTools
```

Here are several high-impact build-time automations you can layer on top of your existing `META-INF/xposed/*` generation to make your framework even more powerful and developer-friendly:

---

### 1. **Descriptor Schema Validation**

**What:** As part of your Gradle build, run a schema check (e.g. via a JSON-Schema or YAML-Schema validator) against every `modules/*/descriptor.yaml`.
**Why it helps:** Immediately catches typos, missing required fields (`id`, `entry_classes`, etc.), or malformed scopes—so you never ship an invalid configuration that only fails at runtime.

---

### 2. **Auto-Generated ProGuard / R8 Keep Rules**

**What:** Scan the merged list of `entry_classes` and generate a `proguard-keep-rules.pro` fragment with lines like:

```proguard
-keep class com.wobbz.debugall.DebugAllModule { *; }
```

**Why it helps:** Ensures R8 never strips or obfuscates your plugin entry points, without asking every module author to hand-craft keep rules.

---

### 3. **Dynamic Version Code & Name Bumping**

**What:** Derive `versionCode` (e.g. via `git rev-list --count HEAD`) and `versionName` (e.g. from your latest Git tag) in your Gradle script, and inject them into the generated `module.prop`.
**Why it helps:** Guarantees your framework APK and all modules inside it always carry an up-to-date version, removes manual bookkeeping, and prevents publishing stale builds.

---

### 4. **README / Docs Table Generation**

**What:** Produce a Markdown snippet at build time—pulled from every module's `id`, `name`, `description`, and `scope`—and inject it into your `README.md` or a separate `docs/MODULES.md`.
**Why it helps:** Your GitHub landing page always reflects the current feature set with zero manual edits, making it immediately clear what each plugin does.

---

### 5. **Android Resource (strings.xml) Generation**

**What:** From each `descriptor.yaml`, auto-generate `<string>` entries for `module_name_<id>` and `module_desc_<id>` in `res/values/strings.xml`.
**Why it helps:** Lets you reference module names/descriptions via `@string/…` in your manifest or UI, and sets you up for easy localization if you decide to add a settings UI later.

---

### 6. **Constants Class for Entry Points & Scopes**

**What:** Emit a generated Java (or Kotlin) class like:

```java
public final class ModuleRegistry {
  public static final String[] ENTRY_CLASSES = {
    "com.wobbz.debugall.DebugAllModule",
    "com.wobbz.adblocker.AdBlockerModule",
    // …
  };
  public static final String[] SCOPES = {
    "com.android.systemui", "com.chrome.browser", // …
  };
}
```

**Why it helps:** Avoids string-literal typos in your code, simplifies reflective lookups, and gives IDE autocomplete when you need to reference these values programmatically.

---

### 7. **Automated APK Signing & Zipalign**

**What:** After `assembleRelease`, automatically run `zipalign` and `apksigner` (using your keystore) as a final Gradle task.
**Why it helps:** Guarantees every build artifact is production-ready and reduces one more manual step before distribution.

---

### 8. **Changelog Generation from Git**

**What:** At build time, pull your recent Git commits or tags and generate a snippet for `CHANGELOG.md` under a new "Unreleased" or current version heading.
**Why it helps:** Keeps your changelog in sync with what actually changed in code, so contributors and users always know what's new.

---

### 9. **Dependency Audit & License Report**

**What:** Use a Gradle plugin (e.g. [Gradle License Report](https://github.com/jaredsburrows/gradle-license-plugin)) to emit a summary of all third-party dependencies and their licenses into `LICENSES.html` or similar.
**Why it helps:** Makes open-source compliance transparent and ensures you're not unknowingly bundling problematic libraries.

---

### 10. **CI-Ready Build Metadata Injection**

**What:** Embed continuous-integration metadata—build timestamp, branch name, commit SHA—into a resource file or `module.prop` entry.
**Why it helps:** When you find an issue in a deployed APK, you immediately know exactly which CI run (and thus which code state) produced it.

---

By stacking these automations on top of your existing descriptor-driven Xposed file generation, you'll turn your LSPosed framework into a fully self-documenting, self-versioning, and CI-friendly powerhouse—letting you focus on writing hooks, not on manual bookkeeping.
