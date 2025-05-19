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
         classpath 'com.android.tools.build:gradle:8.2.0'
       }
     }
     allprojects {
       repositories { google(); mavenCentral() }
     }
     ext {
       xposedApiVersion = '0.4.2'   // LSPosed build for Android 15
       minSdk           = 34
       targetSdk        = 35
       compileSdk       = 35
       javaVersion      = JavaVersion.VERSION_17
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
       implementation "com.google.code.gson:gson:2.10.1"
       implementation "org.json:json:20231013"
     }
     ```
   * **Directory structure**

     ```
     framework/
       build.gradle
       proguard-rules.pro
       src/main/java/com/wobbz/framework/
         annotations/           ← @XposedPlugin, @HotReloadable
         analytics/            ← Performance tracking
         development/          ← Development tools
         security/             ← Security management
         ui/                   ← Settings UI generation
         IModulePlugin.java
         PluginManager.java
       src/main/resources/META-INF/xposed/
         features.json         ← Feature configuration
         log-config.json      ← Logging configuration
         module.prop.tpl      ← Module properties template
     ```

3. **Core Modules**

   ```
   modules/
     IntentMaster/           ← Intent manipulation
       module-info.json      ← Dependencies & metadata
       settings.json         ← UI configuration
       src/main/.../IntentMasterModule.java
     NetworkGuard/           ← Network traffic control
     PermissionOverride/     ← Permission management
     DeepIntegrator/        ← Component exposure
     SuperPatcher/          ← System modifications
     DebugAll/              ← Debugging utilities
   ```

---

## II. Module Metadata & Annotations

1. **Annotation-Driven Development**
   Use Java annotations for module metadata:

   ```java
   @XposedPlugin(
     id = "com.wobbz.debugall",
     name = "Debug-All",
     description = "Configure debug flags for selected applications",
     version = "1.0.0",
     scope = {"android", "com.android.systemui"},
     permissions = {"android.permission.READ_LOGS"}
   )
   @HotReloadable
   public class DebugAllModule implements IModulePlugin {
     // Implementation
   }
   ```

2. **Dependency Management**
   Define module relationships in `module-info.json`:

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

3. **Settings Schema**
   Auto-generate LSPosed Manager UI from JSON:

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

4. **Analytics Integration**
   Track performance metrics:

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

## III. Core Features

1. **Hot-Reloading Support**
   Enable development-time hot reloading:

   ```java
   @HotReloadable
   public class MyModule implements IModulePlugin {
     @Override
     public void onHotReload() {
       // Clean up existing hooks
       mUnhooks.values().forEach(XC_MethodHook.Unhook::unhook);
       mUnhooks.clear();
       
       // Reinitialize managers
       initializeManagers(mModuleContext);
       
       // Reinstall hooks
       hookCoreNetworkApis();
       hookAppNetworkOperations(lpparam);
     }
   }
   ```

2. **Security Framework**
   Implement security checks:

   ```java
   if (mSecurityManager != null && 
       !mSecurityManager.shouldAllowConnection(packageName, host, port, SecurityManager.PROTO_TCP)) {
     throw new SecurityException("Connection blocked by NetworkGuard");
   }
   ```

3. **Diagnostics Server**
   Enable web-based diagnostics:

   ```java
   DiagnosticsServer.getInstance(context)
     .addHookEvent(hookId, moduleId, targetPackage, executionTime, success);
   ```

4. **Logging System**
   Configure logging in `log-config.json`:

   ```json
   {
     "defaultLevel": "INFO",
     "tagLevels": {
       "NetworkGuard": "DEBUG",
       "IntentMaster": "VERBOSE"
     }
   }
   ```

---

## IV. Development Workflow

1. **Module Creation**
   Create new module structure:

   ```
   modules/NewModule/
   ├── src/main/java/com/wobbz/newmodule/
   │   └── NewModule.java           # @XposedPlugin annotated class
   ├── module-info.json             # Dependencies & metadata
   └── settings.json               # UI configuration
   ```

2. **Hot-Reload Development**
   Enable live code updates:

   ```bash
   # Start hot-reload server
   ./gradlew runDevServer

   # Watch for changes
   ./gradlew watchModules
   ```

3. **Testing & Debugging**
   Use built-in diagnostics:

   ```bash
   # View diagnostics dashboard
   adb forward tcp:8082 tcp:8082
   # Open http://localhost:8082 in browser
   ```

4. **Release Build**
   Create release package:

   ```bash
   ./gradlew assembleRelease
   # Outputs signed APK with all modules
   ```

---

## V. Module Integration

1. **Intent Manipulation**
   Use IntentMaster with DeepIntegrator:

   ```java
   // Expose target component
   ComponentConfig config = new ComponentConfig(packageName, componentName, TYPE_ACTIVITY);
   config.setExported(true);
   mDeepIntegrator.addComponentConfig(config);

   // Route intents to exposed component
   IntentRule rule = new IntentRule();
   rule.setAction("android.intent.action.VIEW");
   rule.setIntentAction(Action.REDIRECT);
   rule.getModification().setNewComponent(componentName);
   mIntentMaster.addRule(rule);
   ```

2. **Permission Management**
   Use PermissionOverride with SuperPatcher:

   ```java
   // Check permission override status
   Integer status = mPermissionOverride.checkPermissionOverrideStatus(packageName, permission);
   if (status == PERMISSION_GRANTED) {
     // Proceed with operation
   }

   // Use SuperPatcher for deeper modifications if needed
   if (status == PERMISSION_DENIED) {
     mSuperPatcher.requestHook(packageName, className, methodName, 
       parameterTypes, "replace", callback);
   }
   ```

3. **Network Security**
   Integrate NetworkGuard with security framework:

   ```java
   // Define firewall rules
   FirewallRule rule = new FirewallRule();
   rule.type = SecurityManager.RULE_TYPE_BLOCK;
   rule.destinationPattern = "api.example.com";
   rule.protocol = SecurityManager.PROTO_TCP;
   mSecurityManager.addFirewallRule(packageName, rule);

   // Monitor network activity
   mAnalyticsManager.trackDataUsage(packageName, bytesTransferred);
   ```

With Android 15 defaults and these best practices baked in, this framework blueprint gives you a single, scalable host for all your LSPosed modules—easy to build, extend, and maintain on your OnePlus 12 running OxygenOS 15.0.
