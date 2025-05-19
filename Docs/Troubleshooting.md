# LSPosed Modular Framework — Troubleshooting Guide

**Device Context:** OnePlus 12 (CPH2583) running OxygenOS 15.0 (Android 14), rooted via Magisk with Zygisk & LSPosed installed, plus Kali Nethunter chroot environment.

---

## 1. Environment & Build Configuration

**Issue: JDK/Gradle Incompatibility**

* **Symptom:** Build errors like `Unsupported major.minor version` or `Could not find com.android.tools.build:gradle`.
* **Solution:**

  * Ensure JDK 8 is on PATH (`java -version` should report 1.8.x).
  * Align Gradle plugin and Android Gradle Plugin versions in `build.gradle`.
  * In CI or local, install Android SDK build-tools matching `targetSdkVersion` (34).

**Issue: ANDROID\_HOME or SDK paths misconfigured**

* **Symptom:** `SDK not found`, `Failed to find target with hash string 'android-34'`.
* **Solution:**

  * Export `ANDROID_SDK_ROOT` pointing to the correct SDK directory.
  * In Android Studio, verify SDK path under **Settings → Appearance & Behavior → System Settings → Android SDK**.

**Considerations:**

* OnePlus 12's development host may have multiple SDKs; use explicit `sdk.dir` in `local.properties`.

---

## 2. Module Descriptor & Resource Generation

**Issue: Missing or malformed `META-INF/xposed/java_init.list`**

* **Symptom:** LSPosed logs `no entry class found`, framework doesn't load plugins.
* **Solution:**

  * Confirm Gradle task scanned all `modules/*/descriptor.yaml`.
  * Validate YAML syntax; run a YAML linter.
  * Inspect the final APK: `unzip -l app-release.apk | grep java_init.list`.

**Issue: Incorrect `module.prop` metadata**

* **Symptom:** LSPosed Manager shows wrong module name/version or refuses to install.
* **Solution:**

  * Check merged `module.prop` for duplicated keys.
  * Ensure each `descriptor.yaml` defines a unique `id`.

**Considerations:**

* OxygenOS's packaging may strip unknown `META-INF` entries; test on-device via `adb shell pm path`.

---

## 3. Plugin Discovery & Hook Implementation

**Issue: Hook methods not invoked**

* **Symptom:** No `XposedBridge.log` output, no behavior change.
* **Solution:**

  * Wrap hook bodies in `try/catch` and log exceptions.
  * Verify correct method signatures against OxygenOS 15.0 AOSP source.
  * Confirm plugin scope includes your test package or `android` (for system_server hooks).

**Issue: Signature mismatches on Android 14**

* **Symptom:** `NoSuchMethodError` in logs or silent failure.
* **Solution:**

  * Pull `frameworks/base` for Android 14; search for `generateApplicationInfo` and `getApplicationInfo`.
  * Adjust hook parameters in `IModulePlugin` implementations accordingly.

**Considerations:**

* OnePlus may backport security patches that rename or relocate framework classes; inspect `/system/framework/*.jar` for class names.

---

## 4. LSPosed Activation & Scope Issues

**Issue: Module disabled or wrong scope**

* **Symptom:** LSPosed Manager shows module OFF or no effect when toggled.
* **Solution:**

  * In LSPosed Manager, ensure your module is **enabled** and scope set to **All processes** or explicit package list.
  * Reboot device after any toggle change.

**Issue: Conflicts with existing modules**

* **Symptom:** Hooks fire inconsistently or crash.
* **Solution:**

  * Disable all other modules; re-enable one by one to isolate conflicts.
  * Use unique logging tags per module to trace invocations.

**Considerations:**

* The OnePlus 12's performance optimizations (Trinity Engine) can restart background services; ensure LSPosed Manager remains active.

---

## 5. SELinux & Zygisk Nuances

**Issue: SELinux denials blocking hooks**

* **Symptom:** Logs show `avc: denied` entries in `dmesg` or `logcat`.
* **Solution:**

  * Temporarily set SELinux to permissive: `adb shell su -c 'setenforce 0'`.
  * If hooks start working, add custom SELinux policy or context adjustments in `post-fs-data.sh` (for Magisk modules) or by patching `file_contexts` (advanced).

**Issue: Zygisk injection not occurring in `system_server`**

* **Symptom:** Zygisk modules don't run for system-level hooks.
* **Solution:**

  * In Magisk Manager's **Zygisk** settings, disable "Enforce DenyList".
  * Ensure Magisk version supports Zygisk on Android 14 (use `minMagiskVersion` in `module.prop`).

**Considerations:**

* OxygenOS's SELinux policy may differ; monitor `/sys/fs/selinux/enforce` and adjust policies accordingly.

---

## 6. Kali Nethunter Chroot Environment

**Issue: Filesystem path inconsistencies**

* **Symptom:** Gradle tasks or scripts referencing `/data/adb/modules` fail under chroot.
* **Solution:**

  * Detect chroot mount points in scripts (e.g. check for `/data/nhsystem/`).
  * Use absolute paths or environment variables to point to module directories.

**Issue: ADB shell commands default to chroot namespace**

* **Symptom:** `adb shell pm install` invokes chroot's `pm`, not system's.
* **Solution:**

  * Prefix commands with `adb shell su -mm -c 'pm install …'` to ensure host Android shell context.

**Considerations:**

* OnePlus 12's Nethunter may mount additional namespaces; develop scripts to detect and bypass chroot when performing installation or logging.

---

## 7. Performance & Resource Management

**Issue: Elevated startup latency**

* **Symptom:** Apps take noticeably longer to launch with framework installed.
* **Solution:**

  * Measure baseline vs. hook performance: `adb shell am start -W`.
  * Minimize work in `initZygote` and `handleLoadPackage`; defer heavy initialization to first-use via a flag.

**Issue: Memory leaks in long-running modules**

* **Symptom:** System memory consumption grows over time; OOM kills.
* **Solution:**

  * Avoid static references to large objects in plugins.
  * Use weak references or clear caches periodically.

**Considerations:**

* With 16 GB + 12 GB RAM Boost, you have headroom but don't waste resources—OnePlus thermal management may kill over-consuming processes.

---

## 8. Packaging & Deployment

**Issue: Missing resources in final APK**

* **Symptom:** No `META-INF/xposed` folder or incomplete `.list` files.
* **Solution:**

  * Validate Gradle resource merging: enable `--debug` on `assembleRelease`.
  * Inspect `build/intermediates/merged_assets/release/out/META-INF/xposed`.

**Issue: Conflicting Gradle configurations**

* **Symptom:** Duplicate classes errors or resource overrides.
* **Solution:**

  * Ensure each feature module only depends on `:framework`, not on each other.
  * Use `api` vs. `implementation` appropriately to control classpath exposure.

**Considerations:**

* OxygenOS may optimize APK installs; use `adb install -r -g app-release.apk` to grant all permissions and overwrite existing installs.

---

## 9. CI/CD Build Failures

**Issue: Build server missing SDK components**

* **Symptom:** CI logs show missing SDK Build-Tools 34.0.0.
* **Solution:**

  * Update CI pipeline to install necessary SDK components via CLI (`sdkmanager "build-tools;34.0.0"`).

**Issue: Inconsistent Gradle wrapper versions**

* **Symptom:** Local builds succeed; CI builds fail.
* **Solution:**

  * Commit `gradlew`, `gradle/wrapper/gradle-wrapper.properties`, and correct wrapper JAR to repo.

**Considerations:**

* Leverage caching of Gradle and SDK on CI to speed up repeat builds.

---

## 10. Logging & Diagnostics

**Best Practices:**

* Prefix all log messages with module ID: `XposedBridge.log("[Framework] initZygote invoked");`
* Capture exceptions in all `IModulePlugin` methods and log stack traces.
* Tail logcat with filters: `adb shell logcat | grep -E "\[Framework\]|\[FeatureName\]"`.

---

### General Keep-in-Mind Points

* **Iterative Development:** Validate each change on-device before expanding scope.
* **Version Tracking:** Note exact Magisk, LSPosed, and OxygenOS build numbers when troubleshooting.
* **Isolation:** Use a barebones module (only logging) to confirm foundational functionality before adding hooks.
* **Documentation:** Record every signature change, SELinux tweak, or path workaround in the framework's Wiki.

---

## 1. Annotation Processing Issues

### 1.1 Missing Metadata Files
**Problem:** `META-INF/xposed/*` files not generated
**Solution:**
1. Check `@XposedPlugin` annotation is present
2. Run `./gradlew clean processAnnotations`
3. Verify annotation processor in `build.gradle`:
   ```groovy
   dependencies {
     annotationProcessor 'com.wobbz.lsposed:plugin-processor:1.0.0'
   }
   ```

### 1.2 Invalid Annotations
**Problem:** Build fails with annotation validation errors
**Solution:**
1. Ensure all required fields are present in `@XposedPlugin`
2. Check version format follows semver
3. Verify scope entries are valid package names

## 2. Hot-Reload Problems

### 2.1 Changes Not Applying
**Problem:** Code changes not reflecting in app
**Solution:**
1. Verify development server is running:
   ```bash
   ./gradlew runDevServer --info
   ```
2. Check module has `@HotReloadable`
3. Ensure `onHotReload()` is implemented
4. Look for errors in logcat:
   ```bash
   adb logcat | grep "HotReload"
   ```

### 2.2 Server Connection Issues
**Problem:** Hot-reload server unreachable
**Solution:**
1. Check port is available:
   ```bash
   lsof -i :8081
   ```
2. Verify ADB connection:
   ```bash
   adb devices
   ```
3. Ensure correct port in `build.gradle`:
   ```groovy
   lsposed {
     devServerPort = 8081
   }
   ```

## 3. Settings UI Generation

### 3.1 UI Not Appearing
**Problem:** Settings screen not showing in LSPosed Manager
**Solution:**
1. Validate settings.json schema:
   ```bash
   ./gradlew validateSettings
   ```
2. Check file location:
   ```
   modules/YourModule/settings.json
   ```
3. Rebuild and reinstall module

### 3.2 Settings Not Persisting
**Problem:** Settings reset after reboot
**Solution:**
1. Verify JSON types match usage
2. Check storage permissions
3. Clear LSPosed Manager data and retry

## 4. Dependency Resolution

### 4.1 Version Conflicts
**Problem:** Build fails with dependency conflicts
**Solution:**
1. Check version constraints:
   ```bash
   ./gradlew checkDependencies --info
   ```
2. Update module-info.json:
   ```json
   {
     "dependsOn": {
       "com.example.core": ">=1.0.0"
     }
   }
   ```
3. Resolve conflicts manually if needed

### 4.2 Missing Dependencies
**Problem:** Required modules not found
**Solution:**
1. Verify dependency exists in repository
2. Check version availability
3. Update dependency declaration

## 5. Resource Overlay Issues

### 5.1 Overlay Not Applying
**Problem:** Custom resources not showing
**Solution:**
1. Check overlay structure:
   ```
   res/overlay/target.package/
   ```
2. Verify target package name
3. Rebuild and reinstall overlay:
   ```bash
   ./gradlew installOverlays
   ```

### 5.2 Resource Conflicts
**Problem:** Resource override conflicts
**Solution:**
1. Check resource IDs
2. Verify overlay priority
3. Clear resource cache

## 6. Remote Updates

### 6.1 Update Check Failures
**Problem:** Updates not detecting
**Solution:**
1. Verify CDN connectivity
2. Check update-config.json:
   ```json
   {
     "updateSources": [
       {
         "url": "https://cdn.example.com",
         "publicKey": "..."
       }
     ]
   }
   ```
3. Validate version numbers

### 6.2 Update Installation Failures
**Problem:** Updates fail to install
**Solution:**
1. Check signature verification
2. Verify storage space
3. Clear update cache

## 7. Development Tools

### 7.1 IDE Integration Issues
**Problem:** IDE plugins not working
**Solution:**
1. Update plugins
2. Check compatibility
3. Enable features in build.gradle:
   ```groovy
   lsposed {
     enableDevTools = true
   }
   ```

### 7.2 Debug Tools Problems
**Problem:** Monitoring tools not working
**Solution:**
1. Check permissions
2. Verify tool dependencies
3. Update development tools

## 8. Common Error Messages

### 8.1 Annotation Processing
```
Error: Invalid @XposedPlugin annotation
```
- Check annotation parameters
- Verify processor version
- Clean and rebuild

### 8.2 Hot-Reload
```
Error: Hot-reload connection failed
```
- Check server status
- Verify port availability
- Check network connection

### 8.3 Settings UI
```
Error: Invalid settings schema
```
- Validate JSON format
- Check field types
- Verify required fields

### 8.4 Dependencies
```
Error: Dependency resolution failed
```
- Check version constraints
- Verify repository access
- Update dependencies

## 9. Performance Issues

### 9.1 Slow Hot-Reload
**Problem:** Hot-reload taking too long
**Solution:**
1. Monitor performance:
   ```bash
   ./gradlew monitorHotReload --info
   ```
2. Reduce module size
3. Optimize reload logic

### 9.2 High Memory Usage
**Problem:** Excessive memory consumption
**Solution:**
1. Track memory:
   ```bash
   ./gradlew trackMemory
   ```
2. Profile hooks
3. Optimize resource usage

## 10. Recovery Steps

### 10.1 Clean State
1. Stop development server
2. Clean build files:
   ```bash
   ./gradlew clean
   ./gradlew cleanHotReload
   ./gradlew cleanGeneratedUI
   ```
3. Clear LSPosed Manager data
4. Reinstall module

### 10.2 Debug Mode
1. Enable verbose logging:
   ```groovy
   lsposed {
     debug = true
     verboseLogging = true
   }
   ```
2. Monitor logcat
3. Check generated files

## 11. Getting Help

1. Check framework logs:
   ```bash
   ./gradlew tailLogs
   ```

2. Generate debug report:
   ```bash
   ./gradlew debugReport
   ```

3. Contact support with:
   - Debug report
   - Module version
   - LSPosed version
   - Android version
   - Error messages

This guide should help you quickly diagnose and remediate issues when building or extending your LSPosed Modular Framework on the OnePlus 12 environment. Continually update this document as you encounter new device-specific quirks.
