* [ ] **Initialize Repository**

  * [ ] Create Git repository (e.g. `git init`)
  * [ ] Add a `.gitignore` tailored for Android/Gradle projects

* [ ] **Define Root Project Configuration**

  * [ ] Create `settings.gradle` to include `:framework` and every folder under `modules/` dynamically
  * [ ] Create root `build.gradle`

    * [ ] Declare common version properties: Xposed API version, `minSdk`, `targetSdk`, Java compatibility
    * [ ] Configure repositories (Maven Central, Google)

* [ ] **Create Framework Module**

  * [ ] Under `framework/`, create an Android library module
  * [ ] Add `framework/build.gradle`

    * [ ] Apply `com.android.library` plugin
    * [ ] Apply `compileSdkVersion` and `defaultConfig` using root ext properties
    * [ ] Enable Java 1.8 compatibility
    * [ ] Add `compileOnly "io.github.libxposed:api:<version>"` dependency
    * [ ] Include any shared helper libraries if needed
  * [ ] Create `framework/src/main/java/.../IModulePlugin.java`

    * [ ] Define `initZygote(StartupParam)` and `handleLoadPackage(LoadPackageParam)` methods
  * [ ] Create `framework/src/main/java/.../PluginManager.java`

    * [ ] Implement logic to discover and instantiate all plugin classes from descriptors
    * [ ] Provide `initZygote` and `handleLoadPackage` dispatch methods
  * [ ] Create resource templates in `framework/src/main/resources/META-INF/xposed/`

    * [ ] `java_init.list.tpl` (placeholder for entry classes)
    * [ ] `scope.list.tpl` (placeholder for per-module scopes)
    * [ ] `module.prop.tpl` (placeholder for combined metadata)
  * [ ] Add ProGuard/R8 rules under `framework/proguard-rules.pro` to keep plugin classes intact

* [ ] **Set Up Module Descriptor Processing**

  * [ ] Add a Gradle task in `framework/build.gradle` to scan all `modules/*/descriptor.yaml` files
  * [ ] Parse each YAML to extract:

    * [ ] `id`, `name`, `description` (for module.prop)
    * [ ] `entry_classes` (for java\_init.list)
    * [ ] `scope` entries (for scope.list)
  * [ ] Generate merged files in `build/generated/META-INF/xposed/` from the templates

* [ ] **Generate Framework Entry Class**

  * [ ] Create a Gradle task to compile a generated Java class (e.g. `FrameworkEntry.java`) under `build/generated/src/`

    * [ ] Implements `IXposedHookZygoteInit` and `IXposedHookLoadPackage`
    * [ ] Delegates calls to `PluginManager`

* [ ] **Configure Packaging**

  * [ ] Ensure `android.defaultConfig` in framework includes no `applicationId` (library mode)
  * [ ] Confirm that the merged `META-INF/xposed` folder is included in the final APK
  * [ ] Add `assembleRelease` configuration to produce an installable APK

* [ ] **Create Feature Modules Skeleton**

  * [ ] For each new feature, under `modules/FeatureName/` directory:

    * [ ] Create `build.gradle`

      * [ ] Apply `com.android.library`
      * [ ] Declare `implementation project(":framework")`
      * [ ] Configure any additional dependencies
    * [ ] Create `descriptor.yaml` with fields:

      * `id`
      * `name`
      * `description`
      * `entry_classes` (list of plugin entry points)
      * `scope` (list of package filters)
    * [ ] Under `src/main/java/...`, create `FeatureNameModule.java`

      * [ ] Implement `IModulePlugin`
      * [ ] Provide no-op stubs for `initZygote` and `handleLoadPackage`

* [ ] **Link All Modules in Settings**

  * [ ] Verify that `settings.gradle` picks up each `modules/*` folder as a project
  * [ ] Confirm each module appears under Gradle’s project tree

* [ ] **Integrate Common Utilities**

  * [ ] In `framework/src/main/java/.../util/`, implement:

    * [ ] Logging helper (wrapping `XposedBridge.log`)
    * [ ] Reflection helper methods for safe hook installation
    * [ ] Error handling wrappers to catch and log exceptions in hooks

* [ ] **Finalize CI Build Configuration**

  * [ ] Create a CI pipeline (e.g. GitHub Actions):

    * [ ] Checkout code
    * [ ] Install required JDK and Android SDK components
    * [ ] Run `./gradlew clean assembleRelease`
    * [ ] Archive the resulting APK artifact

* [ ] **Documentation & Developer Guide**

  * [ ] Write a `README.md` at repository root covering:

    * Project overview and purpose
    * Directory structure explanation
    * Steps to add a new feature module
    * How to build and install the combined APK
  * [ ] Include code comments in `IModulePlugin`, `PluginManager`, and build scripts explaining dynamic generation

* [ ] **Versioning & Release Prep**

  * [ ] Define semantic versioning strategy in `module.prop.tpl`
  * [ ] Add a `CHANGELOG.md` template for documenting updates
