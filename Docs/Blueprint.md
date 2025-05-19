Below is a general LSPosed Modular Framework blueprint—updated for Android 15 (API 35)
---

## I. Project Layout & Build Configuration

1. **Root Repository Setup**

   * **`settings.gradle`**

     ```groovy
     rootProject.name = "LSPosedFramework"
     include ':framework'
     fileTree('modules').eachDir { dir ->
       include ":modules:${dir.name}"
       project(":modules:${dir.name}").projectDir = dir
     }
     ```
   * **`build.gradle`** (root)

     ```groovy
     buildscript {
       repositories { google(); mavenCentral() }
       dependencies {
         classpath 'com.android.tools.build:gradle:8.1.0'
       }
     }
     allprojects {
       repositories { google(); mavenCentral() }
     }
     ext {
       xposedApiVersion = '0.4.2'   // LSPosed build for Android 15
       minSdk           = 21
       targetSdk        = 35
       compileSdk        = 35
       javaVersion      = JavaVersion.VERSION_1_8
     }
     ```

2. **Framework Module (`/framework`)**

   * **`framework/build.gradle`**

     ```groovy
     apply plugin: 'com.android.library'
     android {
       compileSdkVersion rootProject.ext.compileSdk
       defaultConfig {
         minSdkVersion rootProject.ext.minSdk
         targetSdkVersion rootProject.ext.targetSdk
         compileOptions {
           sourceCompatibility rootProject.ext.javaVersion
           targetCompatibility rootProject.ext.javaVersion
         }
       }
     }
     dependencies {
       compileOnly "io.github.libxposed:api:${rootProject.ext.xposedApiVersion}"
     }
     ```
   * **Directory structure**

     ```
     framework/
       build.gradle
       proguard-rules.pro
       src/main/java/com/yourorg/framework/
         IModulePlugin.java
         PluginManager.java
       src/main/resources/META-INF/xposed/
         java_init.list.tpl
         scope.list.tpl
         module.prop.tpl
     ```

3. **Module Descriptor Processing**

   * A Gradle task in `framework/build.gradle` scans each `modules/*/descriptor.yaml`, parsing:

     * `id`, `name`, `description` → merged into `module.prop`
     * `entry_classes` → populates `java_init.list`
     * `scope` → populates `scope.list`
   * Generated files placed under `build/generated/META-INF/xposed/`

4. **Generated Entry Class**

   * Compiled under `build/generated/src/`
   * Implements `IXposedHookZygoteInit` and `IXposedHookLoadPackage`
   * Delegates both methods to `PluginManager`

5. **Packaging**

   * `./gradlew assembleRelease` produces one APK containing:

     * All compiled code (framework + features)
     * Merged `META-INF/xposed/java_init.list`, `scope.list`, `module.prop`
   * Install via `adb install -r app-release.apk`

---

## II. Module Metadata & Resource Declarations

1. **`java_init.list`** (generated)
   One entry-class FQN per line, from all feature modules’ descriptors.

2. **`scope.list`** (generated)
   One package or process filter per line, limiting where each feature runs.

3. **`module.prop`** (generated)

   ```properties
   id=your.framework.id
   name=Your LSPosed Modular Framework
   version=1.0.0
   author=Your Name
   description=Host framework for multiple LSPosed feature modules
   ```

4. **APK Manifest Entries**
   Use:

   ```xml
   <application
     android:label="@string/app_name"
     android:description="@string/app_desc"
     … >
     <!-- No legacy xposedmodule/meta-data tags needed -->
   </application>
   ```

---

## III. Hook Implementation Patterns

1. **Minimal Impact Hooks**

   * All reflective lookups (classes/methods/fields) resolved once in `initZygote()`.
   * In `handleLoadPackage()`, immediately return if `param.packageName` isn’t in that feature’s scope.

2. **Safe Execution Wrappers**

   ```java
   try {
     // hook logic
   } catch (Throwable t) {
     XposedBridge.log("[FeatureX] " + Log.getStackTraceString(t));
   }
   ```

3. **Conditional Hooking**

   * Leverage `scope.list` to prevent loading unnecessary code in unrelated processes.
   * Features that run only in system\_server or a specific app will never trigger elsewhere.

4. **Shared Utilities**

   * **LoggingHelper.log(tag, msg)** wraps `XposedBridge.log` with consistent prefix and timestamp.
   * **ReflectionHelper.findClassSafe()** and `.findMethodSafe()` to centralize signature drift handling.

---

## IV. Expanded Best Practices (with Android 15 considerations)

1. **Version Pinning & Compatibility**

   * **Xposed API** pinned to `0.4.2` for Android 15 support.
   * Guard Android-version–specific code:

     ```java
     if (Build.VERSION.SDK_INT >= 35) { … }
     ```

2. **Declarative Descriptor-Driven Design**

   * All module and feature metadata in YAML (`descriptor.yaml`), not hard-coded.
   * Add a Gradle validation step to fail the build if any descriptor is missing required keys (`id`, `entry_classes`).

3. **Structured Logging & Observability**

   * Prefix logs with `[ModuleID|FeatureName]`.
   * Allow runtime log-level toggling via a small `config.properties` in `META-INF/xposed/`.

4. **Performance Awareness**

   * **Avoid heavy work** in hot paths; defer expensive tasks to on-demand threads.
   * Android 15’s ART profile changes demand lean hooks to keep AOT profiles clean.

5. **Thread Safety & Immutability**

   * Store plugin lists and config maps in immutable collections.
   * Synchronize only on rare, necessary data mutations.

6. **Resource Management & ProGuard**

   * **ProGuard rules** to keep hook classes:

     ```proguard
     -keep class com.yourorg.** { *; }
     ```
   * Avoid bundling large assets; load them externally or on first use.

7. **Android 15–Specific Adjustments**

   * **Hook signatures moved**: reflectively search both `framework.jar` and `framework.art` for classes like `PackageParser`.
   * **Hidden-API enforcement**: rely on LSPosed’s built-in allowlist rather than manual bypass.
   * **SELinux contexts**: when overlaying or bind-mounting files, restore contexts:

     ```bash
     chcon -R u:object_r:system_lib_file:s0 $MODDIR/system/lib64/...
     ```
   * **Vendor overlays**: use `overlayfs` instead of bind-mount on `/vendor` to avoid Android 15 mount restrictions.
   * **Force-stop requirement**: after updating `scope.list`, a force-stop (not just reboot) may be needed to clear ART caches.

8. **Documentation & Onboarding**

   * **README.md** in the root explains:

     * Overall structure
     * How descriptors drive generation
     * Steps to add new features
   * **In-code Javadoc** for each hook, noting target method signatures and Android-15 nuances.

9. **Semantic Versioning & Releases**

   * MAJOR version bump for breaking descriptor or plugin interface changes.
   * Distribute APKs named `lspf-framework-vX.Y.Z.apk` for clarity.

10. **Security & Community Practices**

    * **Peer reviews** on every new feature/descriptor change.
    * **Least privilege**: no extra Android permissions.
    * **Dependency audits**: keep Gradle/Maven libraries up to date.

---

## V. Extension Workflow Recap

1. **Create New Feature Module**

   ```bash
   mkdir -p modules/NewFeature && cd modules/NewFeature
   touch build.gradle descriptor.yaml
   ```

2. **Write `descriptor.yaml`**

   ```yaml
   id: com.yourorg.NewFeature
   name: New Feature Name
   description: What this feature does
   entry_classes:
     - com.yourorg.newfeature.NewFeatureModule
   scope:
     - com.some.target.app
   ```

3. **Implement `NewFeatureModule.java`**

   ```java
   public class NewFeatureModule implements IModulePlugin {
     @Override
     public void initZygote(StartParam sp) { /* cache reflection targets */ }
     @Override
     public void handleLoadPackage(LoadPackageParam lp) {
       if (!scope.contains(lp.packageName)) return;
       try { /* hook logic */ }
       catch(Throwable t){ LoggingHelper.e("NewFeature", t); }
     }
   }
   ```

4. **Sync & Build**

   ```bash
   ./gradlew assembleDebug
   ```

   * Verify merged `META-INF/xposed/*` under `build/outputs/apk/release/`

5. **Install & Enable**

   ```bash
   adb install -r app-release.apk
   ```

   * In LSPosed Manager, enable the module, select “All processes” or per-feature scopes, then reboot.

---

With Android 15 defaults and these best practices baked in, this framework blueprint gives you a single, scalable host for all your LSPosed modules—easy to build, extend, and maintain on your OnePlus 12 running OxygenOS 15.0.
