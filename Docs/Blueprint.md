# LSPosed Modular Framework Blueprint

This document serves as the technical blueprint for the LSPosed Modular Framework, detailing the architecture, configuration, and implementation guidelines.

## I. Project Layout & Build Configuration

### Root Project Structure
```
LSPosed-Modules/
├── build.gradle              # Root build file
├── settings.gradle           # Project settings
├── gradle.properties         # Gradle properties
├── libxposed-api/            # Local libxposed API source
├── framework/                # Core framework library
└── modules/                  # Feature modules directory
    ├── SuperPatcher/         # Core functionality module
    ├── DeepIntegrator/       # System integration module
    ├── PermissionOverride/   # Permission enhancement module
    ├── ResourceMaster/       # Resource manipulation module
    └── IntentMaster/         # Intent modification module
```

### Root `build.gradle` (`ext` block)
```groovy
ext {
    minSdk = 30 // Android 11
    targetSdk = 34 // Android 14
    compileSdk = 34
    javaVersion = JavaVersion.VERSION_17
    
    // Dependencies
    annotationProcessor = "1.0.0"
    kotlinVersion = "1.9.0"
    // ... other common dependencies
}
```

### Framework Module (`framework/build.gradle`)
```groovy
apply plugin: 'com.android.library'

android {
    compileSdkVersion rootProject.ext.compileSdk
    namespace "com.wobbz.framework"
    
    defaultConfig {
        minSdkVersion rootProject.ext.minSdk
        targetSdkVersion rootProject.ext.targetSdk
        versionCode 1
        versionName "1.0.0"
    }
    
    compileOptions {
        sourceCompatibility rootProject.ext.javaVersion
        targetCompatibility rootProject.ext.javaVersion
    }
}

dependencies {
    // Use local libxposed-api source
    compileOnly project(':libxposed-api:api')
    
    // Annotation processor for module discovery
    annotationProcessor "com.wobbz.framework:processor:${rootProject.ext.annotationProcessor}"
    
    // ... other dependencies
}
```

### Feature Module Template (`modules/FeatureModule/build.gradle`)
```groovy
apply plugin: 'com.android.library'

android {
    compileSdkVersion rootProject.ext.compileSdk
    
    defaultConfig {
        minSdkVersion rootProject.ext.minSdk
        targetSdkVersion rootProject.ext.targetSdk
        
        // ... other configuration
    }
    
    compileOptions {
        sourceCompatibility rootProject.ext.javaVersion
        targetCompatibility rootProject.ext.javaVersion
    }
}

dependencies {
    // Core framework dependency
    implementation project(':framework')
    
    // Use local libxposed-api source
    compileOnly project(':libxposed-api:api')
    
    // Annotation processor
    annotationProcessor "com.wobbz.framework:processor:${rootProject.ext.annotationProcessor}"
    
    // ... other dependencies
}
```

### Framework Directory Structure (`framework/src/main/`)
```
java/com/wobbz/framework/
├── PluginManager.java                     # Manages plugins, core logic
├── IModulePlugin.java                     # Interface for feature modules
├── IHotReloadable.java                    # Interface for hot-reloading
├── annotation/                            # Annotation classes (e.g., @XposedPlugin)
├── processor/                             # Annotation processors
├── security/                              # Security framework components
├── ui/                                    # Settings UI generation
├── updates/                               # Remote update system
├── overlays/                              # Resource overlay handling
├── development/                           # Development tools (e.g., HotReloadServer)
├── generated/                             # Auto-generated code
├── features/                              # Framework-specific features
├── diagnostics/                           # Diagnostic utilities
├── permissions/                           # Permission management
├── analytics/                             # Analytics integration
└── util/                                  # General utility classes (conceptual, may be sub-packaged)
    // Specific files like Logger.java, ConfigManager.java, HookManager.java
    // might be within these packages or refactored.

resources/META-INF/xposed/
├── module.prop.tpl                        # Template for module.prop (should use com.wobbz.lsposedframework as ID)
├── xposed_init.tpl                        # Template for xposed_init (pointing to com.wobbz.framework.generated.XposedEntryPoint)
├── features.json                          # Framework features
└── log-config.json                        # Logging configuration
```

## II. Module Metadata & Annotations

### Module Annotation
```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface XposedPlugin {
    String name();                  // Module name
    String version();               // Module version (semver)
    String description() default ""; // Module description
    String[] scope() default {};    // Package scope
    String author() default "";     // Module author
    int minAPI() default 1;         // Minimum framework API version
    boolean hasSettings() default false; // Has settings UI
    boolean hasOverlays() default false; // Has resource overlays
}
```

### Module Information JSON
```json
// modules/FeatureModule/module-info.json
{
  "id": "feature-module",       // Unique module ID
  "version": "1.0.0",           // Module version
  "minimumFrameworkVersion": "1.0.0", // Required framework version
  "dependencies": [             // Other module dependencies
    {
      "id": "core-module",
      "version": ">=1.0.0"
    }
  ],
  "loadAfter": [                // Load order preferences
    "security-module"
  ],
  "conflicts": [                // Incompatible modules
    "legacy-module"
  ]
}
```

### Settings Schema
```json
// modules/FeatureModule/settings.json
{
  "$schema": "https://json-schema.org/draft-07/schema#",
  "type": "object",
  "properties": {
    "enableFeature": {
      "type": "boolean",
      "title": "Enable Feature",
      "description": "Turn this feature on or off",
      "default": true
    },
    "sensitivity": {
      "type": "integer",
      "title": "Sensitivity",
      "description": "Adjust how sensitive the feature is",
      "minimum": 1,
      "maximum": 10,
      "default": 5
    },
    "mode": {
      "type": "string",
      "title": "Operation Mode",
      "description": "Select how the feature operates",
      "enum": ["simple", "advanced", "expert"],
      "default": "simple"
    }
  }
}
```

## III. Core Features

### Module Discovery
```java
public class ModuleLoader {
    private final List<Class<?>> pluginClasses = new ArrayList<>();
    
    public void discoverModules() {
        try {
            // Read annotated classes from META-INF/xposed/java_init.list
            AssetManager assets = context.getAssets();
            InputStream is = assets.open("META-INF/xposed/java_init.list");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    Class<?> clazz = Class.forName(line.trim());
                    if (clazz.isAnnotationPresent(XposedPlugin.class)) {
                        pluginClasses.add(clazz);
                    }
                }
            }
        } catch (Exception e) {
            Logger.error("Failed to discover modules", e);
        }
    }
}
```

### Settings UI Generation
```java
public class SettingsGenerator {
    public View generateSettingsUI(Context context, String moduleId) {
        try {
            // Load schema from module's settings.json
            String schema = loadSettingsSchema(moduleId);
            JSONObject jsonSchema = new JSONObject(schema);
            
            // Generate UI elements based on schema
            return buildUIFromSchema(context, jsonSchema);
        } catch (Exception e) {
            Logger.error("Failed to generate settings UI", e);
            return new TextView(context, "Error loading settings");
        }
    }
}
```

### Hot-Reloading Support
```java
@HotReloadable
public class FeatureModule implements IXposedModule {
    private final List<XposedInterface.MethodUnhooker> unhookers = new ArrayList<>();
    
    @Override
    public void onPackageLoaded(XposedInterface xposedInterface, String packageName) {
        // Store unhookers for hot-reload
        Method targetMethod = findTargetMethod();
        unhookers.add(xposedInterface.hook(targetMethod, MyHooker.class));
    }
    
    @Override
    public void onHotReload() {
        // Unhook all methods
        for (XposedInterface.MethodUnhooker unhooker : unhookers) {
            unhooker.unhook();
        }
        unhookers.clear();
        
        // Reinitialize
        initialize();
    }
}
```

### Security Framework
```java
public class ModuleSecurity {
    public boolean validateModule(String moduleId) {
        // Check module signature
        // Verify permissions
        // Validate against security policy
        return true;
    }
    
    public void enforcePermissions(String moduleId, String permission) {
        if (!hasPermission(moduleId, permission)) {
            throw new SecurityException("Module does not have permission: " + permission);
        }
    }
}
```

### Diagnostics Server
```java
public class DiagnosticsServer {
    public void start() {
        // Start diagnostic HTTP server on localhost
        // Expose metrics, logs, and performance data
    }
    
    public void recordHookInvocation(String moduleId, String methodName) {
        // Track hook performance and frequency
    }
}
```

### Logging System
```java
public class Logger {
    public static void info(String moduleId, String message) {
        log(LogLevel.INFO, moduleId, message);
    }
    
    public static void error(String moduleId, String message, Throwable throwable) {
        log(LogLevel.ERROR, moduleId, message);
        if (throwable != null) {
            // Log stack trace
        }
    }
}
```

## IV. Development Workflow

### Module Creation
1. Create a new directory in `modules/`
2. Implement a class with `@XposedPlugin` annotation
3. Create `settings.json` if needed
4. Create `module-info.json` for dependencies

### Hot-Reload Development
```bash
# Start development server
./gradlew runDevServer

# Watch for changes and trigger reload
./gradlew watchModules
```

### Building & Testing
```bash
# Process annotations
./gradlew processAnnotations

# Generate settings UI
./gradlew generateSettingsUI

# Package overlays
./gradlew packageOverlays

# Build all modules
./gradlew build

# Run tests
./gradlew test
```

## V. Integration Guidelines

### Hooking Best Practices
1. Use minimal hooks - prefer single entry points
2. Implement proper exception handling
3. Store unhook references for cleanup
4. Log relevant information
5. Consider security implications

### Configuration Management
1. Use `SettingsHelper` to load/save settings
2. Follow JSON schema for UI generation
3. Handle schema changes with versioning
4. Provide sensible defaults

### Resource Overlay Guidelines
1. Structure overlays by target package
2. Use explicit resource IDs
3. Test on multiple Android versions
4. Handle missing resources gracefully

### Security Considerations
1. Implement least privilege principle
2. Validate all inputs, especially user settings
3. Use the security framework for validation
4. Document security implications
