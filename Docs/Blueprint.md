# LSPosed Modular Framework Blueprint (Android 15+)
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
       src/main/java/com/wobbz/framework/
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

## II. Module Metadata & Annotations

1. **Annotation-Driven Discovery**
   Instead of YAML descriptors, use Java annotations:

   ```java
   @XposedPlugin(
     id = "com.wobbz.NewFeature",
     name = "New Feature",
     scope = {"com.example.app", "system_server"},
     version = "1.0.0"
   )
   public class NewFeatureModule implements IModulePlugin { 
     // Implementation
   }
   ```

2. **Dependency Management**
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

3. **Settings Schema**
   Auto-generate LSPosed Manager UI from JSON:

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
         "key": "onlyWhenCharging",
         "type": "boolean",
         "label": "Only When Charging",
         "defaultValue": true
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

4. **Resource Overlays**
   Place overlay resources in standard Android resource structure:

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

## III. Advanced Features

1. **Hot-Reloading Support**
   Enable development-time hot reloading:

   ```java
   @HotReloadable
   @XposedPlugin(...)
   public class DevFeatureModule implements IModulePlugin {
     @Override
     public void onHotReload() {
       // Clean up old hooks
       // Re-initialize with new implementation
     }
   }
   ```

2. **Remote Updates**
   Configure update sources in `update-config.json`:

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

3. **Built-in Analytics & Crash Reporting**
   Enable optional telemetry in `telemetry.json`:

   ```json
   {
     "enabled": false,
     "collectMetrics": {
       "hookPerformance": true,
       "memoryUsage": true,
       "batteryImpact": true
     },
     "crashReporting": {
       "enabled": true,
       "includeSystemLogs": false
     }
   }
   ```

---

## IV. Build System Integration

1. **Annotation Processing**
   Add to `build.gradle`:

   ```groovy
   dependencies {
     annotationProcessor 'com.wobbz.lsposed:plugin-processor:1.0.0'
   }
   ```

2. **Hot-Reload Development Server**
   Run development server:

   ```bash
   ./gradlew runDevServer
   ```

   Enable in your module:

   ```groovy
   lsposed {
     enableHotReload = true
     devServerPort = 8081
   }
   ```

---

## V. Migration Guide

1. **From YAML to Annotations**
   * Replace `descriptor.yaml` with `@XposedPlugin`
   * Move scope lists into annotation
   * Gradle task auto-generates necessary files

2. **Adding Hot-Reload Support**
   * Implement `@HotReloadable`
   * Add cleanup in `onHotReload()`
   * Configure dev server

3. **Settings UI Migration**
   * Convert existing UI to settings schema
   * Test auto-generated UI in LSPosed Manager
   * Verify persistence works

With Android 15 defaults and these best practices baked in, this framework blueprint gives you a single, scalable host for all your LSPosed modules—easy to build, extend, and maintain on your OnePlus 12 running OxygenOS 15.0.
