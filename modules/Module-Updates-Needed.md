
Okay, you're right. My previous analysis was based solely on the documentation. Let's dive into the actual implementation of each module, compare it with its documentation (`API.md`, `README.md`), and the overall framework guidelines to identify necessary changes, additions, or removals.

I'll start with the `IntentMaster` module, specifically `IntentMasterModule.java`.

Then I will proceed with the other modules: `DebugAll`, `DeepIntegrator`, `NetworkGuard`, `PermissionOverride`, and `SuperPatcher`.

**Module: `IntentMaster`**

File: `modules/IntentMaster/src/main/java/com/wobbz/modules/intentmaster/IntentMasterModule.java`
API Docs: `modules/IntentMaster/API.md`
Framework `API.md` and `README.md` are also relevant for annotations and patterns.

**Analysis of `IntentMasterModule.java`:**

1.  **`@XposedPlugin` Annotation:**
    *   **Implementation:**
        ```java
        @XposedPlugin(
            id = "com.wobbz.IntentMaster",
            name = "Intent Master",
            description = "Intercepts, modifies, redirects, and logs intents between applications",
            version = "1.0.0",
            author = "wobbz"
        )
        public class IntentMasterModule implements IModulePlugin {
        ```
    *   **Observation:** This is correctly used as per the framework's `API.md`. The `id` attribute here is `com.wobbz.IntentMaster`. The framework `API.md` also mentions `scope` and `minFrameworkVersion` as optional attributes for `@XposedPlugin`. The current annotation doesn't use `scope`. The `API.md` for IntentMaster should reflect this annotation usage.

2.  **Hot Reload (`@HotReloadable` and `onHotReload`)**:
    *   **Implementation:** `IntentMasterModule.java` does **not** have the `@HotReloadable` annotation, nor does it implement an `onHotReload` method.
    *   **Needed Change:**
        *   Add `@HotReloadable` annotation to the class.
        *   Implement the `onHotReload(String reloadedPackage)` method (likely requiring `IntentMasterModule` to also implement `IHotReloadable` from the framework, if such an interface exists, or the framework calls it via reflection).
        *   Inside `onHotReload`:
            *   Store `XposedInterface.MethodUnhooker` instances when hooks are applied. Currently, the unhookers are not stored.
            *   Call `unhook()` on all stored unhookers.
            *   Clear any relevant state (e.g., re-initialize the static `instance` if necessary, though this is tricky).
            *   Call `loadSettings()` again.
            *   Re-apply all hooks using the (potentially new) `XposedInterface` instance and `PackageLoadedParam` if available for the reloaded package. The `onPackageLoaded` method contains the main hooking logic, so `onHotReload` would need to be able to re-trigger this for the `reloadedPackage`.

3.  **Settings Handling (`MockSettingsHelper`)**:
    *   **Implementation:** Uses a `private static class MockSettingsHelper`.
    *   **Needed Change:**
        *   Replace `MockSettingsHelper` with the actual `com.wobbz.framework.ui.models.SettingsHelper`.
        *   The constructor `SettingsHelper(Context context, String moduleId)` should be used. The `moduleId` would be `"com.wobbz.IntentMaster"` from the `@XposedPlugin` annotation.
        *   The `SettingsHelper` from the framework is expected to load from a `settings.json` file associated with the module.

4.  **Lifecycle Methods and Initialization (`IModulePlugin`, `initialize`)**:
    *   **Implementation:**
        *   Implements `IModulePlugin`.
        *   `public void initialize(Context context, XposedInterface xposedInterface)`: This custom method initializes `this.xposedInterface` and `this.settings`, then calls `loadSettings()`. This method is likely called by the framework's `PluginManager`.
        *   `public void onModuleLoaded(ModuleLoadedParam param)`: Sets the static `instance = this;`. This `ModuleLoadedParam` is from `io.github.libxposed.api.XposedModuleInterface`.
        *   `public void onPackageLoaded(PackageLoadedParam param)`: Main hooking logic. Gets `XposedInterface` from `this.xposedInterface` which was set in the `initialize` method.
    *   **Observations & Potential Issues:**
        *   The `XposedInterface` is stored from the `initialize` method. Standard `libxposed-api` provides `XposedInterface` in `onPackageLoaded`, `onSystemServerLoaded`, etc. The framework's `PluginManager` seems to be orchestrating this. This should be consistent. If `initialize` is called once, but `onPackageLoaded` is called for multiple packages, the stored `xposedInterface` might be from the context of the first package or a generic one.
        *   **Static Instance (`instance`):** This is a common pattern for hooker classes to access the module instance. However, it can be problematic with hot-reloading if the `instance` variable doesn't get updated to the *new* module instance after a hot reload, leading to hookers calling methods on the *old* (stale) instance. The `onModuleLoaded` method correctly sets the instance.
    *   **Needed Change (Recommendation):**
        *   Ensure the `XposedInterface` instance used is always the one provided by the specific lifecycle call (e.g., `param.getXposed()` within `onPackageLoaded`) rather than relying solely on one stored during an `initialize` call, especially if `initialize` is called less frequently than `onPackageLoaded`. However, the current `onPackageLoaded` doesn't use `param.getXposed()`.
        *   If `IHotReloadable` is implemented, carefully manage the static `instance` update or provide the new instance to hookers in a different way.

5.  **"Test Intent Feature"**:
    *   **Implementation:** No explicit code for a "Test Intent Feature" (e.g., a method to craft and send an intent through `processIntent`) is present in `IntentMasterModule.java`.
    *   **Needed Change:**
        *   If this feature is intended to be part of the module's core logic (callable programmatically or via a debug interface within the module), it needs to be implemented. This could involve a method that takes parameters to construct an `Intent` and then calls `processIntent` to simulate its handling.
        *   If it's purely a UI-driven feature where the UI constructs and sends a real intent, then no change is needed here, but the `API.md` should clarify this.

6.  **`targetApps` Logic**:
    *   **Implementation:** `isTargetPackage(String packageName)` hooks "android" and all packages if `targetApps` is empty.
    *   **Observation:** This seems functional. The `API.md` lists `targetApps` as a global setting.
    *   **Needed Change (Minor Documentation):** The `@XposedPlugin`'s `scope` attribute could potentially be used to declare common target packages if that aligns with the framework's intent for `scope`. The interaction between `scope` and the `targetApps` setting should be clear.

7.  **Hooking Implementation**:
    *   **Implementation:** Uses `xposedInterface.hook(Method, Hooker.class)` with static inner `Hooker` classes.
    *   **Observation:** This aligns with the new `libxposed-api` style. Hookers use `IntentMasterModule.getInstance()` to access the module.
    *   **Needed Change (for Hot Reload):** As mentioned, `MethodUnhooker` instances need to be stored from the `xposedInterface.hook(...)` calls to enable unhooking in `onHotReload`.
        ```java
        // Example change:
        // In IntentMasterModule class:
        // private final List<XposedInterface.MethodUnhooker> unhookers = new ArrayList<>();

        // When hooking:
        // Method startActivityMethod = ...;
        // XposedInterface.MethodUnhooker unhooker = xposedInterface.hook(startActivityMethod, ActivityStartActivityHook.class);
        // if (unhooker != null) unhookers.add(unhooker);

        // In onHotReload:
        // for (XposedInterface.MethodUnhooker unhooker : unhookers) {
        //     unhooker.unhook();
        // }
        // unhookers.clear();
        ```

8.  **Max Logs (`MAX_LOGS`)**:
    *   **Implementation:** `saveLogs()` limits saved logs. `logIntent()` trims in-memory logs when `logs.size() > MAX_LOGS * 1.5`.
    *   **Observation:** This is a reasonable approach to prevent unbounded memory use and overly large settings.

9.  **Model Classes (`IntentRule.java`, `IntentLog.java`, etc.)**:
    *   **Observation:** These classes (`IntentRule`, `IntentLog`, `IntentModification`, `IntentExtra`, `IntentExtraMatch`) seem to generally correspond to the structures described in the `IntentMaster/API.md` settings schema.
    *   **Needed Change (Verification):** A detailed field-by-field comparison between the `API.md` JSON schema and the Java model classes' `fromJson`/`toJson` logic would be needed to ensure all documented fields are handled correctly (e.g., all `Extra Match Types`, `Intent Actions`, `Extra Types for Adding`).
        *   For example, `API.md` `IntentRule Structure` shows `extraMatches` as an array. `IntentRule.java` has `private List<IntentExtraMatch> extraMatches;`. This matches.
        *   `API.md` `modification` object fields like `newAction`, `newData`, `newType`, etc. need to be verified against `IntentModification.java`.
        *   The `API.md` mentions `extrasToAdd` items have a `type` (STRING, INT, etc.). `IntentExtra.java` has an `ExtraType` enum and `addToIntent` logic. This seems to cover it.

**Summary of Changes/Additions for `IntentMasterModule.java`:**

*   **Major:**
    1.  **Implement Hot Reload:** Add `@HotReloadable`, implement `onHotReload` including storing and using `MethodUnhooker` instances, and reloading settings/state.
    2.  **Replace `MockSettingsHelper`:** Use the framework's `com.wobbz.framework.ui.models.SettingsHelper`.
*   **Moderate:**
    1.  **Verify/Implement "Test Intent Feature":** If this is a core module logic feature.
    2.  **Review Lifecycle & `XposedInterface` Usage:** Ensure `XposedInterface` is used from the correct context, especially if `initialize` is called differently from standard Xposed lifecycle methods. Clarify the static `instance` pattern with hot reloading.
*   **Minor/Verification:**
    1.  **Full Settings Schema Alignment:** Detailed check of all fields in `API.md` against Java model classes and their JSON serialization/deserialization.
    2.  **Update `IntentMaster/API.md`:** Document the usage of `@XposedPlugin`. Clarify how the "Test Intent Feature" works.
    3.  Consider if `@XposedPlugin`'s `scope` attribute should be used.

I will now proceed to analyze `DebugAllModule.java`.


Read file: modules/DebugAll/src/main/java/com/wobbz/debugall/DebugAllModule.java
**Module: `DebugAll`**

File: `modules/DebugAll/src/main/java/com/wobbz/debugall/DebugAllModule.java`
API Docs: `modules/DebugAll/API.md`, `modules/DebugAll/README.md`

**Analysis of `DebugAllModule.java`:**

1.  **`@XposedPlugin` and `@HotReloadable` Annotations:**
    *   **Implementation:**
        ```java
        @XposedPlugin(
            id = "com.wobbz.debugall",
            name = "Debug-All",
            description = "Configure debug flags for selected applications",
            version = "1.0.0",
            scope = {"android", "com.android.systemui"}, // "android" is typical for system_server hooks
            permissions = {"android.permission.READ_LOGS"}
        )
        @HotReloadable
        public class DebugAllModule implements IModulePlugin, IHotReloadable {
        ```
    *   **Observation:** Correctly uses `@XposedPlugin` with relevant fields. `scope` includes "android", which is appropriate for modifying `ApplicationInfo` in the system server. `@HotReloadable` is present.
    *   **Interface `IHotReloadable`:** The class implements `IHotReloadable` (from `com.wobbz.framework.IHotReloadable`).

2.  **Settings Handling (`SettingsHelper`)**:
    *   **Implementation:**
        ```java
        mSettingsHelper = new SettingsHelper(this.mModuleContext, "com.wobbz.debugall");
        // ...
        String[] apps = mSettingsHelper.getStringArray("targetApps");
        verboseLogging = mSettingsHelper.getBoolean("verboseLogging", false);
        debugLevel = mSettingsHelper.getString("debugLevel", "info");
        ```
    *   **Observation:** Correctly uses `com.wobbz.framework.ui.models.SettingsHelper` with the module ID. Loads `targetApps`, `verboseLogging`, and `debugLevel` which matches the configuration in `API.md`.

3.  **Lifecycle Methods and Initialization**:
    *   **Implementation:**
        *   `onModuleLoaded(ModuleLoadedParam startupParam)`: Initializes `debugFlags` map. Calls `initializeManagersAndSettings` if `mModuleContext` is already available (it might not be if `initialize` hasn't been called by framework yet).
        *   `initialize(Context context, XposedInterface xposedInterface)`: Custom framework method. Stores `context` and `xposedInterface`. Calls `initializeManagersAndSettings`.
        *   `initializeManagersAndSettings(Context context)`: Initializes `mAnalyticsManager` and `mSettingsHelper`. Loads settings.
        *   `onPackageLoaded(PackageLoadedParam lpparam)`: Main hooking logic.
    *   **Observation:**
        *   The module relies on `mXposedInterface` being set by the framework's `initialize` call.
        *   `onPackageLoaded` is where hooks are applied.
        *   `AnalyticsManager` is used.
    *   **Potential Issue with `onModuleLoaded` and `mModuleContext`:** `onModuleLoaded` (standard Xposed init in Zygote/SystemServer) might be called before the framework's custom `initialize(Context, XposedInterface)` method. `mModuleContext` might be null in `onModuleLoaded`. The code attempts `initializeManagersAndSettings` only if `mModuleContext` is not null. `initializeManagersAndSettings` is also called in `initialize()`, and `onPackageLoaded` tries it again if `!mManagersInitialized && mModuleContext != null`. This seems a bit convoluted for ensuring initialization. A clearer initialization path would be better. Usually, context is reliably available later (e.g., in `onSystemServerLoaded` or via `AndroidAppHelper` if running in an app process).
        *   **For system server hooks (which this module primarily needs), `onSystemServerLoaded(SystemServerLoadedParam param)` is the more typical entry point from `libxposed-api`, where `param.getXposed()` would provide the `XposedInterface`.** The current `onPackageLoaded` approach for what seems to be a system-server level hook (modifying `ApplicationInfo` globally) is unusual. The hook target `android.content.pm.PackageParser$Package` is a system class.

4.  **Hook Target and Logic (`onPackageLoaded`, `DebugFlagHooker`)**:
    *   **Implementation:**
        *   Hooks `android.content.pm.PackageParser$Package.toAppInfoWithoutState(int)`. This is an older target. Modern Android versions use `android.content.pm.parsing.pkg.PackageImpl` or similar.
        *   `DebugFlagHooker.after()`: Modifies `appInfo.flags`.
            *   It retrieves `debugLevel` by accessing a local variable: `String debugLevel = "info"; // Default value, should be set from module`. **This is a critical bug.** The `debugLevel` from the module instance (loaded from settings) is not passed to or accessed by the static `DebugFlagHooker`. The hooker will *always* use `"info"`.
    *   **Needed Change (Critical):**
        1.  **Hook Target:** Update to hook the correct method in modern Android for `ApplicationInfo` generation (e.g., in `android.content.pm.parsing.pkg.PackageImpl` or `com.android.server.pm.ComputerEngine`). This often requires checking Android versions. The `API.md` mentions `PackageImpl` but the code uses `PackageParser$Package`.
        2.  **Pass `debugLevel` to Hooker:** The `DebugFlagHooker` needs access to the `debugLevel` loaded from settings. This can be done by:
            *   Making `DebugFlagHooker` non-static and instantiating it with the `debugLevel` (and `targetApps`, `verboseLogging` if needed for its logic).
            *   Or, passing these values via `param.setHookerExtra()` / `param.getHookerExtra()` if the `libxposed-api` supports it for `Hooker` classes (less common than with `XC_MethodHook`'s `additionalHookInfo`).
            *   The simplest is often to make the `Hooker` an inner class (non-static) or pass values to its constructor. The current `mXposedInterface.hook(toAppInfoMethod, DebugFlagHooker.class)` expects a static hooker or one with a no-arg constructor. This needs to change if the hooker needs instance state. A common pattern is `mXposedInterface.hook(toAppInfoMethod, new DebugFlagHooker(this.debugLevel, this.targetApps))`.
        3.  **Accessing `targetApps` in Hooker:** The `DebugFlagHooker` does not currently check `targetApps`. It applies flags to *any* app whose `ApplicationInfo` is being generated in a package context where the hook is active. This check should happen *before* modifying flags, likely using `appInfo.packageName`.
    *   **Flag Values:** The constants `FLAG_ENABLE_PROFILING` and `FLAG_EXTERNAL_STORAGE_LEGACY` are hardcoded.
        *   `FLAG_ENABLE_PROFILING = 0x00000004;` // This is `ApplicationInfo.FLAG_PROFILEABLE_BY_SHELL` (value 4) not `ApplicationInfo.FLAG_ALLOW_PROFILE`. `FLAG_ALLOW_PROFILE` has value 2.
        *   `FLAG_EXTERNAL_STORAGE_LEGACY = 0x00400000;` // This is `ApplicationInfo.FLAG_LEGACY_STORAGE`.
        *   **Needed Change:** Use the correct `ApplicationInfo.FLAG_*` constants for clarity and maintainability, e.g., `ApplicationInfo.FLAG_PROFILEABLE_BY_SHELL` or `ApplicationInfo.FLAG_ALLOW_PROFILE` based on intended effect, and `ApplicationInfo.FLAG_LEGACY_STORAGE`. The `API.md` mentions `FLAG_ALLOW_PROFILE` and `FLAG_EXTERNAL_STORAGE_LEGACY`.
        *   **Action Needed from `API.md`/`README.md` for `DebugAll`:**
            *   `FLAG_EXTERNAL_STORAGE_LEGACY`: The code currently *does* attempt to set this. As per previous analysis, this needs review for deprecation.
            *   Profiling flag: `API.md` said "FLAG_ALLOW_PROFILE (or equivalent modern profiling flag)". Code uses `0x00000004` which is `FLAG_PROFILEABLE_BY_SHELL`. This needs to be consistent.

5.  **Hot Reload (`onHotReload`)**:
    *   **Implementation:**
        ```java
        public void onHotReload() {
            log("Hot reloading module");
            loadSettings(); // Reloads settings
            for (MethodUnhooker<?> unhooker : unhooks.values()) {
                unhooker.unhook(); // Unhooks
            }
            unhooks.clear(); // Clears unhookers
            // Missing: Re-applying hooks.
            log("Module hot-reloaded successfully");
        }
        ```
    *   **Needed Change:** The `onHotReload` method unhooks and reloads settings but **does not re-apply the hooks**. The hooks are only applied in `onPackageLoaded`. After a hot reload, the module would become inactive until the next app loads or system restart.
        *   It needs to re-trigger the hooking logic. This is complex because `onPackageLoaded` takes `PackageLoadedParam`. `onHotReload` would need to iterate over previously hooked packages (if known) or re-hook for "android" if that's the primary target.
        *   If the main hook is in `system_server` (process "android"), `onHotReload` should probably re-attempt that hook specifically.

6.  **Targeting Logic - `onPackageLoaded` vs. System Hook:**
    *   **Observation:** The module hooks `PackageParser$Package.toAppInfoWithoutState`. This method is called within `system_server` when it's parsing packages. So, the hooking should ideally happen once when `system_server` ("android" package) loads.
    *   **Needed Change (Recommendation):**
        *   Move the primary hooking logic for `PackageParser$Package` (or its modern equivalent) to `onSystemServerLoaded(SystemServerLoadedParam param)` if the framework calls this. Use `param.getXposed()` for the `XposedInterface`.
        *   This makes the hook application more targeted to when `system_server` is ready. `onPackageLoaded` could still be used for `mManagersInitialized` logic if context is needed from a specific app early on, but the main system hook should be in `onSystemServerLoaded`.
        *   The current `onPackageLoaded` will try to apply this system-level hook *every time any targeted app loads*, which is redundant and potentially problematic if `mXposedInterface.hook` doesn't handle multiple hook attempts gracefully (though `libxposed-api` likely does). It also stores unhookers keyed by `packageName`, implying multiple hooks of the same system method, which is not what's intended. There should be one hook on the system method, and that hook's logic then filters by `appInfo.packageName`.

**Summary of Changes/Additions for `DebugAllModule.java`:**

*   **Critical Bugs/Issues:**
    1.  **Hooker Accessing Settings:** `DebugFlagHooker` uses a hardcoded `debugLevel` and doesn't check `targetApps`. It needs to get these from the `DebugAllModule` instance (e.g., by being a non-static inner class or receiving them in constructor).
    2.  **Hook Target:** `PackageParser$Package` is outdated. Needs update for modern Android versions.
    3.  **Incorrect Flag Constants:** Use proper `ApplicationInfo.FLAG_*` constants.
*   **Major:**
    1.  **Hot Reload Hook Re-application:** `onHotReload` must re-apply hooks.
    2.  **Hooking Scope:** The main system hook should be applied once (e.g., in `onSystemServerLoaded` or `onPackageLoaded` for "android" package) rather than for every app in `targetApps`. The `unhooks` map should probably store one unhooker for the system method.
*   **Moderate:**
    1.  **Initialization Flow:** Clarify/simplify the context and manager initialization, possibly using `onSystemServerLoaded` for context if system hooks are the main goal.
    2.  Review `FLAG_EXTERNAL_STORAGE_LEGACY` use as per previous doc analysis.
    3.  Ensure consistency between documented "profiling flag" and the one used.
*   **Documentation:**
    1.  Update `API.md`/`README.md` if hook targets or flag specifics change.

I will now proceed to analyze `DeepIntegratorModule.java`.


Read file: modules/DeepIntegrator/src/main/java/com/wobbz/deepintegrator/DeepIntegratorModule.java
**Module: `DeepIntegrator`**

File: `modules/DeepIntegrator/src/main/java/com/wobbz/deepintegrator/DeepIntegratorModule.java`
API Docs: `modules/DeepIntegrator/API.md`, `modules/DeepIntegrator/README.md`
Models: `ComponentConfig.java`, `IntentFilterConfig.java`, `ComponentAccessLog.java`

**Analysis of `DeepIntegratorModule.java`:**

1.  **`@XposedPlugin` and `@HotReloadable` Annotations:**
    *   **Implementation:** Missing `@XposedPlugin` and `@HotReloadable` annotations on the `DeepIntegratorModule` class.
    *   **Needed Change:**
        *   Add `@XposedPlugin(...)` with appropriate `id`, `name`, `description`, `version`, `scope` (likely "android" for `PackageManagerService` hooks).
        *   Add `@HotReloadable`.
        *   Ensure the class implements `IModulePlugin` and `IHotReloadable` from the framework (it currently does).

2.  **Service API (`SERVICE_KEY`, `FeatureManager`)**:
    *   **Implementation:**
        ```java
        public static final String SERVICE_KEY = "com.wobbz.DeepIntegrator.service";
        // ...
        mFeatureManager = FeatureManager.getInstance(context);
        mFeatureManager.registerService(SERVICE_KEY, this);
        // ...
        public List<ComponentAccessLog> getAccessLogs() { ... }
        public void clearAccessLogs() { ... }
        public void addComponentConfig(ComponentConfig config) { ... }
        public void removeComponentConfig(String packageName, String componentName, String componentType) { ... }
        ```
    *   **Observation:** This directly implements the "Service API" discussed conceptually in `API.md`. It registers itself with `FeatureManager`. The public methods match those described (e.g., `getAccessLogs`, `addComponentConfig`).
    *   **Needed Change:**
        *   The `API.md` should be updated to state that this Service API *is* implemented, not hypothetical.
        *   The `ComponentConfig` and `IntentFilterConfig` structures used by `addComponentConfig` should be precisely documented in `API.md` (currently they are in the context of a conceptual example).
        *   The `SuperPatcherService` interaction (`mSuperPatcherService = mFeatureManager.getService("com.wobbz.SuperPatcher.service");`) is present. The `API.md` correctly describes this as conceptual depending on `SuperPatcher`.

3.  **Settings Handling (`SettingsHelper`)**:
    *   **Implementation:**
        ```java
        mSettings = new SettingsHelper(context, MODULE_ID); // MODULE_ID = "com.wobbz.DeepIntegrator"
        // ...
        mLogComponentAccess = mSettings.getBoolean("logExposedComponents", true);
        mAutoExpose = mSettings.getBoolean("autoExpose", false);
        mBypassPermissionChecks = mSettings.getBoolean("bypassPermissionChecks", true);
        String configJson = mSettings.getString("componentOverrides", "[]"); // Loads ComponentConfig
        String logsJson = mSettings.getString("componentLogs", "[]"); // Loads ComponentAccessLog
        ```
    *   **Observation:** Uses `SettingsHelper` correctly. Loads settings like `logExposedComponents`, `autoExpose`, `bypassPermissionChecks`. Also loads `componentOverrides` (for `ComponentConfig` list) and `componentLogs`.
    *   **Needed Change:**
        *   The `settings.json` structure in `API.md` (and `README.md`) is hypothetical. It needs to be updated to reflect the actual keys used: `"logExposedComponents"`, `"autoExpose"`, `"bypassPermissionChecks"`, `"componentOverrides"`, `"componentLogs"`.
        *   The structure for `"componentOverrides"` (an array of `ComponentConfig` JSON objects) and `"componentLogs"` needs to be documented. The Java model classes (`ComponentConfig.java`, `ComponentAccessLog.java`) define this structure via their `fromJson`/`toJson` methods.

4.  **Lifecycle and Initialization**:
    *   **Implementation:**
        *   `onModuleLoaded(ModuleLoadedParam startupParam)`: Logs.
        *   `initialize(Context context, XposedInterface xposedInterface)`: Main initialization (stores context, XposedInterface, initializes SettingsHelper, FeatureManager, registers service, loads settings, gets SuperPatcher service).
        *   `onPackageLoaded(PackageLoadedParam lpparam)`: Hooks `PackageManagerService` (and `ComponentResolver` for Android 13+) if `lpparam.getPackageName().equals("android")`.
    *   **Observation:** Correctly targets "android" package for system server hooks. Uses `XposedInterface` from the `initialize` method.

5.  **Hooking Logic (`hookPackageManagerService`, `hookMethod`, `ComponentModificationHook`, etc.)**:
    *   **Implementation:**
        *   Hooks methods like `generateActivityInfo`, `generateServiceInfo`, `generateProviderInfo`, `queryIntentActivitiesInternal`, etc., in `com.android.server.pm.PackageManagerService`.
        *   Adds hooks for `com.android.server.pm.ComponentResolver` on Android 13+.
        *   Hooks `com.android.server.pm.permission.PermissionManagerService.checkPermission` if `mBypassPermissionChecks` is true.
        *   `hookMethod` helper finds methods by name (first one found) and applies hook.
        *   **Static Hookers (`ComponentModificationHook`, `IntentQueryHook`, `PermissionBypassHook`):** These are static inner classes.
            *   `ComponentModificationHook.after()`: Modifies `ComponentInfo` (sets `exported = true`, nullifies permissions). **It does not use the instance's `ComponentConfig` settings or the `modifyComponentInfo` method from the module instance.** It applies a generic "expose and nullify permission" to any component it sees.
            *   `IntentQueryHook.after()`: Is a placeholder.
            *   `PermissionBypassHook.before()`: Has example logic to grant specific permissions. Does not use configurations.
    *   **Needed Change (Critical):**
        1.  **Static Hookers Don't Use Module State/Config:** The static hooker classes (`ComponentModificationHook`, `PermissionBypassHook`, `IntentQueryHook`) cannot access the `DeepIntegratorModule` instance's fields (like `mComponentConfigs`, `mAutoExpose`, `mBypassPermissionChecks`) or methods (like `modifyComponentInfo`, `isTargetedPackage`).
            *   The current `ComponentModificationHook` makes *all* components exported and nullifies their permissions, which is likely not the intended configurable behavior based on `ComponentConfig`.
            *   These hookers need to be non-static inner classes or be passed the module instance/relevant config when hooked, e.g., `mXposedInterface.hook(method, new ComponentModificationHook(this, componentType))`. Then they can call instance methods of `DeepIntegratorModule`.
        2.  **`modifyComponentInfo` Method Not Used by Hook:** The `private void modifyComponentInfo(ComponentInfo info, String componentType)` method contains detailed logic to check `ComponentConfig` and apply specific changes. This method is currently **not called** from the `ComponentModificationHook`. The hooker implements its own simpler (and likely incorrect for general use) logic.
        3.  **`IntentQueryHook` Incomplete:** The `API.md` mentions "Intent Filter Injection". The `IntentQueryHook` is the place for this but is empty. If this feature is intended, it needs implementation (e.g., adding/modifying `IntentFilter` objects in the results of `queryIntentActivitiesInternal`, etc., based on `ComponentConfig.intentFilters`).
        4.  **`findMethodInClass` Simplistic:** `findMethodInClass` just takes the first method by name. This can be risky if there are overloads. It should ideally take parameter types. The `hookMethod` calls it without specifying parameters.
        5.  **`getCallingPackageName()`**: Uses `Binder.getCallingUid()` and `PackageManager.getPackagesForUid()`. This is standard.

6.  **Hot Reload (`onHotReload`)**:
    *   **Implementation:**
        ```java
        public void onHotReload() {
            log("Hot reloading module");
            for (MethodUnhooker<?> unhooker : mActiveHooks) { // Unhooks
                unhooker.unhook();
            }
            mActiveHooks.clear();
            loadSettings(); // Reloads settings
            // Missing: Re-applying hooks.
            log("Module hot-reloaded successfully");
        }
        ```
    *   **Needed Change:** Similar to other modules, `onHotReload` unhooks and reloads settings but **does not re-apply the hooks**. It needs to re-call `hookPackageManagerService` (or equivalent logic) using the stored `mXposedInterface` and a relevant `PackageLoadedParam` (perhaps a "dummy" one for the "android" package, or the framework needs to provide a way to re-hook for system_server).

7.  **Model Classes (`ComponentConfig`, `IntentFilterConfig`, `ComponentAccessLog`)**:
    *   **Observation:** These seem to be implemented with `fromJson`/`toJson`.
    *   **Needed Change (Verification):**
        *   `ComponentConfig.java`: Contains `isExported()`, `isOverrideExported()`, `isNullifyPermission()`, `isBypassPermissions()`. The `modifyComponentInfo` method uses these. The `ComponentModificationHook` needs to correctly use this logic.
        *   `IntentFilterConfig.java`: Is present. If "Intent Filter Injection" is implemented, `ComponentConfig.getIntentFilters()` would be used to create and add `android.content.IntentFilter` objects.

**Summary of Changes/Additions for `DeepIntegratorModule.java`:**

*   **Critical:**
    1.  **Static Hookers Logic:** `ComponentModificationHook`, `PermissionBypassHook` must be refactored (e.g., become non-static or get module instance) to use the module's loaded configuration (`mComponentConfigs`, `mAutoExpose`, etc.) and call methods like `modifyComponentInfo`. The current `ComponentModificationHook` is too aggressive.
*   **Major:**
    1.  **Implement Annotations:** Add `@XposedPlugin` and `@HotReloadable`.
    2.  **Implement Intent Filter Injection:** The `IntentQueryHook` needs to be implemented to handle `IntentFilterConfig` from `ComponentConfig` if this feature is to be supported.
    3.  **Hot Reload Hook Re-application:** `onHotReload` must re-apply hooks for `PackageManagerService`.
    4.  **Method Finding:** `findMethodInClass` should be made more robust (e.g., accept parameter types).
*   **Moderate:**
    1.  **Update Documentation (`API.md`, `README.md`):**
        *   Reflect that the Service API *is* implemented.
        *   Provide the actual `settings.json` schema based on keys like `"componentOverrides"`, `"logExposedComponents"`.
        *   Document the JSON structure for `ComponentConfig` (including `IntentFilterConfig`) and `ComponentAccessLog`.
*   **Minor/Verification:**
    1.  Review if "Dynamic Proxying" (mentioned in `README.md`) has any implementation or if it's purely conceptual/future.

I will now proceed to analyze `NetworkGuardModule.java`.


Read file: modules/NetworkGuard/src/main/java/com/wobbz/networkguard/NetworkGuardModule.java
**Module: `NetworkGuard`**

File: `modules/NetworkGuard/src/main/java/com/wobbz/networkguard/NetworkGuardModule.java`
API Docs: `modules/NetworkGuard/API.md`, `modules/NetworkGuard/README.md`
Framework Components: `SecurityManager.java`, `AnalyticsManager.java`

**Analysis of `NetworkGuardModule.java`:**

1.  **Outdated Xposed API Usage**:
    *   **Implementation:** The module extensively uses the **old Xposed API**:
        *   `com.github.libxposed.api.XC_MethodHook` (should be `io.github.libxposed.api.XposedInterface.Hooker`)
        *   `com.github.libxposed.api.XposedBridge` (logging, `sInitialApplication`)
        *   `com.github.libxposed.api.XposedHelpers` (finding and hooking methods, finding classes)
        *   `com.github.libxposed.api.callbacks.XC_LoadPackage.LoadPackageParam`
        *   `com.github.libxposed.api.callbacks.IXposedHookZygoteInit.StartupParam`
    *   **Needed Change (Critical & Extensive):**
        *   **Complete Rewrite of Hooking Logic:** All hooking must be migrated to the `io.github.libxposed.api.XposedInterface` and `io.github.libxposed.api.XposedInterface.Hooker` pattern.
            *   Replace `XposedHelpers.findAndHookMethod(...)` with `xposedInterface.hook(method, HookerClass.class)`.
            *   `XC_MethodHook` anonymous classes need to be converted into separate static inner classes or top-level classes implementing `Hooker`.
            *   `param.args`, `param.thisObject`, `param.setResult()`, `param.setThrowable()` in `XC_MethodHook` have equivalents in `BeforeHookCallback` and `AfterHookCallback` parameters of the `Hooker` interface methods.
        *   **Lifecycle Methods:**
            *   It uses `initZygote(StartupParam startupParam)` and `handleLoadPackage(LoadPackageParam lpparam)`. These are from the old `IXposedHookZygoteInit` and `IXposedHookLoadPackage` interfaces, not the new `io.github.libxposed.api.IXposedModule` (which uses `onInit`, `onZygote`, `onSystemServerLoaded`, `onPackageLoaded` with different `Param` types). The class `NetworkGuardModule` implements `IModulePlugin` which extends `XposedModuleInterface`. It needs to be checked if `IModulePlugin` or `XposedModuleInterface` bridge these old method signatures to the new API's expectations, or if `NetworkGuardModule` should directly implement `io.github.libxposed.api.IXposedModule` or use the framework's lifecycle methods.
            *   The framework's `API.md` states modules should implement `io.github.libxposed.api.IXposedModule`.
        *   **Logging:** Replace `LoggingHelper.info/error/etc.` if `LoggingHelper` itself uses `XposedBridge.log`. Logging should ideally go through an `XposedInterface` instance if available, or a framework-provided logger that abstracts this. `LoggingHelper` appears to be a framework utility; its implementation needs to be checked.
        *   **Accessing Context:** `XposedBridge.sInitialApplication` is an old API pattern. Context should be obtained via lifecycle params or `AndroidAppHelper.currentApplication()` more reliably.
        *   **Finding Classes/Methods:** `XposedHelpers.findClass` should be replaced with `classLoader.loadClass()` or `xposedInterface.loadClass()`. `XposedHelpers.callMethod` needs to be replaced with standard reflection or appropriate `XposedInterface` helpers if any exist for this.

2.  **`@XposedPlugin` Annotation:**
    *   **Implementation:** Missing.
    *   **Needed Change:** Add `@XposedPlugin(...)` with `id`, `name`, `description`, `version`, `scope` (likely very broad, or "android" plus common app packages if it hooks app-specific libraries).

3.  **Hot Reload (`@HotReloadable`, `onHotReload`)**:
    *   **Implementation:**
        *   `@HotReloadable` annotation is present.
        *   `public void onHotReload()`:
            *   Correctly iterates `mUnhooks.values().forEach(XC_MethodHook.Unhook::unhook);` (this unhooking call itself will need to change with the new API).
            *   Removes listener from `SecurityManager`.
            *   Re-initializes managers.
            *   Re-applies hooks using `hookCoreNetworkApis(mStoredLpparam)`, etc.
    *   **Observation:** The general structure of unhooking, reloading, and re-hooking is present.
    *   **Needed Change:**
        *   The unhooking mechanism `XC_MethodHook.Unhook` will change to `XposedInterface.MethodUnhooker`.
        *   The re-hooking methods (`hookCoreNetworkApis`, etc.) will need to use the new API.
        *   The `mStoredLpparam` usage for re-hooking in `onHotReload` is a good attempt. Ensure that the `XposedInterface` instance needed for re-hooking is available and valid.

4.  **Manager Initialization (`initializeManagers`)**:
    *   **Implementation:** Initializes `SecurityManager` and `AnalyticsManager`. Relies on `mModuleContext`.
    *   **Observation:** The logic to obtain `mModuleContext` in `initZygote` and `handleLoadPackage` is complex due to uncertainty about when context is available.
    *   **Needed Change:** Simplify context acquisition. If most hooks are Zygote/System-level, `onZygote` or `onSystemServerLoaded` (from the new API) might be better places to get a stable context or `XposedInterface` for system-wide actions. `SecurityManager.getInstance(this.mModuleContext)` suggests it might need a context for file operations, etc.

5.  **Dependency on Framework Components (`SecurityManager`, `AnalyticsManager`, `LoggingHelper`)**:
    *   **Implementation:** Correctly uses these components.
    *   **Observation:** The effectiveness of `NetworkGuard` highly depends on the implementation and configuration of `SecurityManager`.
    *   **`SecurityManager.SecurityListener`**: `NetworkGuardModule` implements this, but no listener methods (`onSecurityRulesChanged`, etc.) are implemented in the provided code snippet.
    *   **Needed Change:** Implement the `SecurityManager.SecurityListener` methods if `NetworkGuard` needs to react to rule changes dynamically (e.g., by re-evaluating active connections or re-applying certain logic, though full re-hooking might not always be necessary if hooks just consult `SecurityManager`).

6.  **Hooking Strategy (`hookCoreNetworkApis`, `hookAppNetworkOperations`, `hookAppSpecificNetworkLibraries`)**:
    *   **`hookCoreNetworkApis`:** Hooks `Socket.<init>` and `URL.openConnection`. These are applied in `initZygote` (with `lpparam = null`) and again in `handleLoadPackage`. Applying in Zygote should cover most processes.
    *   **`hookAppNetworkOperations`:** Currently an empty placeholder.
    *   **`hookAppSpecificNetworkLibraries`:** Hooks OkHttp's `newCall`.
    *   **Needed Change (Post API Migration):**
        *   Determine the correct scope for each hook. Zygote-level hooks (`Socket`, `URL`) should be applied once in `onZygote`. App-specific hooks (like OkHttp) should be in `onPackageLoaded` for relevant packages.
        *   The `XC_MethodHook` logic: `String currentPackageName = (lpparam != null) ? lpparam.packageName : "unknown_zygote_context";`. In the new API, `HookParam` might provide means to get current app context or the `XposedInterface` instance might be app-specific. For Zygote hooks, determining the exact package originating a call can be tricky and often relies on stack walking or other context clues if `lpparam` isn't available.

7.  **`NetworkGuardModule.API.md` Discrepancies**:
    *   The `API.md` for `NetworkGuard` describes public methods like `addFirewallRule`, `blockAppNetwork`, `getAppNetworkStats`. These methods are **not present** in the `NetworkGuardModule.java` code.
    *   It states: "NetworkGuard module ... defers to the `com.wobbz.framework.security.SecurityManager` for decisions. It does not expose a direct programmatic API for other Xposed modules to call its core hooking functionalities extensively." This aligns with the code's reliance on `SecurityManager`.
    *   **Needed Change:**
        *   If the public methods in `API.md` are intended, they need to be implemented. They would likely interact with `mSecurityManager`.
        *   If they are not intended for `NetworkGuardModule` itself but for `SecurityManager`, the `API.md` should clarify that these are `SecurityManager`'s API methods.
        *   The `AppNetworkStats` class is also in `API.md` but not in the module code.

**Summary of Changes/Additions for `NetworkGuardModule.java`:**

*   **Critical & Extensive:**
    1.  **Migrate from Old Xposed API to New `io.github.libxposed.api`:** This is the most significant task, involving rewriting all hook implementations, lifecycle method usage, and utility calls (`XposedBridge`, `XposedHelpers`).
*   **Major:**
    1.  **Add `@XposedPlugin` Annotation.**
    2.  **Update Hot Reload Logic:** Use new API unhookers and ensure re-hooking uses the new API.
    3.  **Implement `API.md` Public Methods:** Add methods like `addFirewallRule`, `getAppNetworkStats`, etc., if they are meant to be part of this module's direct API (likely delegating to `SecurityManager`). Or, clarify in `API.md` that these are `SecurityManager` APIs.
    4.  **Implement `SecurityManager.SecurityListener` methods:** If needed for dynamic rule updates.
*   **Moderate:**
    1.  **Simplify Context Acquisition and Initialization Flow:** Align with new API lifecycle methods for better context stability.
    2.  **Review Hook Scopes:** Ensure Zygote hooks are applied in `onZygote` and app-specific ones in `onPackageLoaded`.
*   **Documentation:**
    1.  Update `NetworkGuard/API.md` to reflect actual implemented public methods and remove/clarify unimplemented ones.
    2.  Document the reliance on `SecurityManager` for configuration.

This module requires substantial refactoring due to the outdated API usage.

I will now proceed to analyze `PermissionOverrideModule.java`.

Read file: modules/PermissionOverride/src/main/java/com/wobbz/permissionoverride/PermissionOverrideModule.java
**Module: `PermissionOverride`**

File: `modules/PermissionOverride/src/main/java/com/wobbz/permissionoverride/PermissionOverrideModule.java`
API Docs: `modules/PermissionOverride/API.md`, `modules/PermissionOverride/README.md`
Service Interface: `IPermissionOverrideService.java` (defined in the same module)

**Analysis of `PermissionOverrideModule.java`:**

1.  **Outdated Xposed API Usage**:
    *   **Implementation:** Similar to `NetworkGuard`, this module heavily uses the **old Xposed API**:
        *   `com.github.libxposed.api.IXposedHookLoadPackage` (interface implemented by the module)
        *   `com.github.libxposed.api.XC_MethodHook`
        *   `com.github.libxposed.api.XposedHelpers`
        *   `com.github.libxposed.api.callbacks.XC_LoadPackage.LoadPackageParam`
        *   `XposedInterface.Utils.log` (part of `libxposed-api` but often used with older patterns, direct use of `XposedInterface` instance's `log` is preferred with the new API).
    *   **Needed Change (Critical & Extensive):**
        *   **Complete Rewrite of Hooking Logic:** Migrate all hooking to `io.github.libxposed.api.XposedInterface` and `Hooker` classes.
        *   **Lifecycle Methods:** The module implements `IXposedHookLoadPackage` and its `handleLoadPackage(Context context, XC_LoadPackage.LoadPackageParam lpparam)` method. This is from the *old* API structure. It needs to be adapted to the new `io.github.libxposed.api.IXposedModule` lifecycle methods (e.g., `onPackageLoaded` which receives `XposedInterface` and `PackageLoadedParam` from the new API). The framework's `API.md` mandates implementing `io.github.libxposed.api.IXposedModule`. The `IModulePlugin` it also implements might be a bridge, but direct new API usage is cleaner.
        *   **Context and `XposedInterface` Acquisition:** `handleLoadPackage` receives a `Context`. The new `onPackageLoaded` provides `XposedInterface`. The module needs to adapt to use the new `XposedInterface` for its operations.
        *   **Logging:** Standardize logging as per previous comments.

2.  **`@XposedPlugin` Annotation:**
    *   **Implementation:** Missing.
    *   **Needed Change:** Add `@XposedPlugin(...)` with `id`, `name`, `description`, `version`, `scope` (likely "android" and potentially all packages if it hooks `Context.checkPermission`).

3.  **Hot Reload (`@HotReloadable`, `onHotReload`)**:
    *   **Implementation:**
        *   `@HotReloadable` annotation is present (though the class doesn't directly implement `IHotReloadable` from the framework in its signature, it does implement `IHotReloadable` from `com.wobbz.framework.IHotReloadable`).
        *   `public void onHotReload()`: Unhooks old API `XC_MethodHook.Unhook` instances, clears cache, reloads settings.
    *   **Needed Change:**
        *   Update unhooking to use `XposedInterface.MethodUnhooker`.
        *   **Re-applying Hooks:** `onHotReload` is missing the logic to re-apply hooks. It needs to re-trigger the hooking logic (e.g., `hookPermissionChecks`, `hookSignatureChecks`) for all relevant packages or system_server.

4.  **Service Implementation (`IPermissionOverrideService`)**:
    *   **Implementation:** The module implements `IPermissionOverrideService` (an AIDL-like interface defined in the same module) and registers itself with `FeatureManager` using `SERVICE_KEY`.
    *   The `IPermissionOverrideService.java` defines methods like `checkPermissionOverrideStatus`, `P_isAppPermissionForced`, `findClass`, `findMethod`, etc. These are implemented at the end of `PermissionOverrideModule.java`.
    *   **RemoteException Handling:** The module attempts to connect to an `IPermissionOverrideService` (itself?) via `bindService` in the `connectToService` method, which is called when `lpparam.packageName.equals("android")`. This seems overly complex, as if it's trying to be both a client and server of its own interface across a Binder-like mechanism. If the service methods are meant to be called by *other* modules *within the same process via `FeatureManager`*, then the direct implementation of `IPermissionOverrideService` by `PermissionOverrideModule` and registration with `FeatureManager` is sufficient. The self-binding via AIDL seems unnecessary and error-prone (`RemoteException`).
    *   **Observation:** The `API.md` for `PermissionOverride` states: "The PermissionOverride module does not expose a direct programmatic API for other Xposed modules...". This directly contradicts the presence and registration of `IPermissionOverrideService`.
    *   **Needed Change:**
        *   **Clarify API Strategy:** Decide if `IPermissionOverrideService` is intended for other modules.
            *   If YES: Update `API.md` to document this service API. Simplify the implementation – direct calls via `FeatureManager` don't need the `ServiceConnection`, `IBinder`, `RemoteException` handling if all modules run in the same framework process space. The current service methods use `XposedHelpers` which also needs updating.
            *   If NO: Remove `IPermissionOverrideService`, its registration, `connectToService`, and the `ServiceConnection` logic.
        *   The service methods like `findClass`, `findMethod` using `XposedHelpers` will also need to be updated if this service is kept.

5.  **Settings Handling (`SettingsHelper`)**:
    *   **Implementation:** Uses `SettingsHelper` to load `bypassSignatureChecks`, `logPermissionRequests`, `defaultPermissionBehavior`, and `permissionOverrides`.
    *   **Observation:** The JSON structure for `permissionOverrides` in `loadPermissionOverrides()`:
        ```json
        // Expected by code:
        // [
        //   {
        //     "packageName": "com.example.app",
        //     "permissions": {
        //       "android.permission.CAMERA": "DENY", // or "GRANT" or "DEFAULT"
        //       "android.permission.LOCATION": "GRANT"
        //     }
        //   }
        // ]
        ```
        This is different from the structure in `PermissionOverride/API.md`:
        ```json
        // In API.md:
        // {
        //   "permissionOverrides": [
        //     {
        //       "packageName": "com.example.problematicapp",
        //       "permission": "android.permission.ACCESS_FINE_LOCATION", // Single permission
        //       "action": "DENY",                                    // Single action
        //       "enabled": true
        //     }
        //   ]
        // }
        ```
    *   **Needed Change:**
        *   **Unify Settings Structure:** The code's implementation of `loadPermissionOverrides` (multiple permissions per package object) and the `API.md`'s example (single permission per rule object) are different. Decide on one structure and update both code and `API.md`. The `API.md` version is more granular and common.
        *   The "FAKE_GRANT" / "FAKE_DENY" actions from `API.md` are not handled in `loadPermissionOverrides` or `checkPermissionOverride`.

6.  **Hooking Logic (`hookPermissionChecks`, `hookSignatureChecks`)**:
    *   **Implementation:**
        *   `hookPermissionChecks`: Hooks `Context.checkPermission`, `ApplicationPackageManager.checkPermission`, and conditionally (old Android) `PackageManagerService.checkUidPermission`.
        *   `hookSignatureChecks`: Hooks `PackageManagerService.compareSignatures`, `PackageManagerServiceUtils.compareSignatures`, `ApplicationPackageManager.getPackageInfo` (after hook), and `PackageManagerService.checkSignatures` (Android P+).
        *   Uses `isTargetedPackage` which relies on `SettingsHelper.getStringArray("targetApps")`.
    *   **Observation:** The range of hooks is comprehensive. All use old Xposed API.
    *   **Needed Change (Post API Migration):**
        *   The hooks in `PackageManagerService` (like `compareSignatures`, `checkSignatures`, `checkPermission` from `handleLoadPackage` when "android") should be applied in `onSystemServerLoaded` for the "android" package.
        *   Hooks on `Context.checkPermission` and `ApplicationPackageManager.checkPermission` should be applied in `onPackageLoaded` for targeted applications.
        *   The `checkPermissionOverride` logic seems to correctly consult `mAppPermissionOverrides` and `mDefaultBehavior`.

7.  **"Conceptual" Features from `API.md` (`FAKE_GRANT`, `FAKE_DENY`)**:
    *   **Implementation:** Not implemented in `loadPermissionOverrides` or `checkPermissionOverride`.
    *   **Needed Change:** If these are to be supported, the logic needs to be added. This would be complex as "faking" a grant/deny often means more than just returning a different result from `checkPermission`; it might involve altering app behavior or other system interactions.

**Summary of Changes/Additions for `PermissionOverrideModule.java`:**

*   **Critical & Extensive:**
    1.  **Migrate from Old Xposed API to New `io.github.libxposed.api`:** Complete rewrite of hooking logic, lifecycle usage.
*   **Major:**
    1.  **Add `@XposedPlugin` Annotation.**
    2.  **Update Hot Reload Logic:** Use new API unhookers and re-apply hooks.
    3.  **Resolve Settings JSON Discrepancy:** Align the structure in code (`loadPermissionOverrides`) with `API.md`.
    4.  **Clarify/Refactor `IPermissionOverrideService`:** Decide its purpose. If kept, simplify (remove AIDL-like self-binding) and update its methods to use new Xposed API. Update `API.md` accordingly.
*   **Moderate:**
    1.  **Implement "FAKE_GRANT" / "FAKE_DENY" actions** if they are to be supported features, and document them.
    2.  **Review Hook Scopes:** Apply system hooks in `onSystemServerLoaded` and app-specific hooks in `onPackageLoaded`.
*   **Documentation:**
    1.  Update `API.md` for settings JSON, service API (if kept), and implemented features.

I will now proceed to analyze `SuperPatcherModule.java`.

Read file: modules/SuperPatcher/src/main/java/com/wobbz/superpatcher/SuperPatcherModule.java
**Module: `SuperPatcher`**

File: `modules/SuperPatcher/src/main/java/com/wobbz/superpatcher/SuperPatcherModule.java`
API Docs: `modules/SuperPatcher/API.md`, `modules/SuperPatcher/README.md`
Supporting Classes: `TemplateManager.java`, `SSLUnpinningTemplate.java`

**Analysis of `SuperPatcherModule.java`:**

1.  **Outdated Xposed API Usage**:
    *   **Implementation:** This module is also built entirely on the **old Xposed API**:
        *   `io.github.libxposed.api.XC_MethodHook`, `XC_MethodReplacement` (these seem to be incorrect imports, likely meant `com.github.libxposed.api...` or `de.robv.android.xposed...` if it's very old, or they are stubs. The `io.github.libxposed.api` namespace is for the *new* API which uses `Hooker`).
        *   `XposedBridge.hookAllConstructors`, `XposedBridge.hookMethod`, `XposedBridge.sInitialApplication`.
        *   `XposedHelpers.findClass`, `XposedHelpers.findMethodExact`, `XposedHelpers.findConstructorExact`, `XposedHelpers.findField`, `XposedHelpers.callMethod`, `XposedHelpers.newInstance`.
        *   `callbacks.XC_LoadPackage.PackageLoadedParam`, `callbacks.XC_LoadPackage.ModuleLoadedParam`.
    *   **Needed Change (Critical & Extensive):**
        *   **Complete Rewrite of Hooking Logic:** Migrate all hooking to the new `io.github.libxposed.api.XposedInterface` and `Hooker` pattern.
            *   Replace `XposedBridge.hookMethod` with `xposedInterface.hook(method, HookerClass.class)`.
            *   `XC_MethodHook` and `XC_MethodReplacement` inner classes (`MethodHook`, `MethodReplacementHook`) need to be adapted to implement `Hooker` and use its `before`, `after`, `replace` methods.
        *   **Lifecycle Methods:** Adapt `onPackageLoaded` and `onModuleLoaded` (which use old `Param` types) to the new API lifecycle methods (e.g., `onInit`, `onPackageLoaded` with new `Param` types from `io.github.libxposed.api.IXposedModule`).
        *   **Utility Replacements:** Find alternatives for `XposedHelpers` methods using `XposedInterface` or standard reflection.
        *   **Context Acquisition:** Update `XposedBridge.sInitialApplication` usage.

2.  **`@XposedPlugin` Annotation:**
    *   **Implementation:** Missing.
    *   **Needed Change:** Add `@XposedPlugin(...)`. Scope will be broad, or per enabled app in settings.

3.  **Hot Reload (`@HotReloadable`, `onHotReload`)**:
    *   **Implementation:**
        *   Implements `IHotReloadable` from the framework. `@HotReloadable` annotation is missing on the class.
        *   `public void onHotReload()`: Unhooks old API `XC_MethodHook.Unhook` instances, clears `mActiveHooks` and `mCustomClassLoaders`.
    *   **Needed Change:**
        *   Add `@HotReloadable` annotation.
        *   Update unhooking to use `XposedInterface.MethodUnhooker`.
        *   **Re-applying Hooks and DEX Loading:** `onHotReload` does not re-apply patches or reload DEX files. It needs to re-trigger `applyMethodPatches` and `loadCustomDexFiles` for affected packages.

4.  **Service API (`SERVICE_KEY`, `FeatureManager`)**:
    *   **Implementation:** Registers itself with `FeatureManager` using `SERVICE_KEY`.
    *   Exposes public methods like `getDexClassLoader`, `getFieldValue`, `setFieldValue`, `createInstance`, `invokeMethod`, and `requestHook`.
    *   `requestHook`: Allows other modules to dynamically request hooks by adding to `SuperPatcher`'s own JSON patch definitions in settings.
    *   **Observation:** This aligns with the "Service API" concept in its `API.md`.
    *   **Needed Change (Post API Migration):**
        *   The public methods (`getFieldValue`, etc.) currently use `XposedHelpers`. These need to be updated to use the new API or standard reflection after the core of `SuperPatcher` is migrated.
        *   The `requestHook`'s mechanism of modifying its own settings to apply hooks is an interesting approach for dynamic requests.

5.  **Settings Handling (`SettingsHelper`)**:
    *   **Implementation:** Uses `SettingsHelper` to load `verboseLogging`, `enabledApps`, `patchDefinitions` (JSON array of patches), `loadCustomDex`, `customDexPaths`.
    *   **Observation:** The `patchDefinitions` JSON structure (fields like `package`, `className`, `methodName`, `hookType`, `parameterTypes`, `argValues`, `returnValue`) is central to its operation.
    *   **Needed Change:**
        *   `SuperPatcher/API.md` describes a `PatchDefinition` *Java class* structure for a hypothetical dynamic API. The actual implementation uses JSON configuration directly. The `API.md` needs to document the JSON structure of `"patchDefinitions"` as used by the module.
        *   The `README.md` example JSON is good but should be explicitly linked from/to the `API.md` settings section.

6.  **Interaction with `PermissionOverride` Service**:
    *   **Implementation:** `findClassWithPermissionCheck`, `findConstructorWithPermissionCheck`, `findMethodWithPermissionCheck` attempt to use `PermissionOverride` service via `FeatureManager` if standard reflection fails.
    *   **Observation:** This is a good example of inter-module communication. Its success depends on `PermissionOverrideModule` correctly implementing its service.

7.  **Patching Logic (`applyMethodPatches`, `applyHook`, `MethodHook`, `MethodReplacementHook`)**:
    *   **Implementation:**
        *   `applyMethodPatches` parses JSON and calls `applyHook`.
        *   `applyHook` uses `XposedBridge.hookMethod/hookAllConstructors`.
        *   Inner classes `MethodHook` (for before/after) and `MethodReplacementHook` (for replacing) extend old `XC_MethodHook` and `XC_MethodReplacement`.
        *   `handleHookedMethod` in `MethodHook` has logic to modify args/return value and log, based on JSON config.
        *   `convertJsonValue` handles type conversion for arguments/return values.
    *   **Needed Change (Post API Migration):**
        *   The entire hooking chain needs to be rebuilt with `XposedInterface` and `Hooker`.
        *   The logic in `MethodHook` and `MethodReplacementHook` will need to be adapted to the `before`, `after`, `replace` methods of the new `Hooker` interface.
        *   Storing `XC_MethodHook.Unhook` in `mActiveHooks` needs to change to `XposedInterface.MethodUnhooker`.

8.  **Custom DEX Loading (`loadCustomDexFiles`, `getDexClassLoader`)**:
    *   **Implementation:** Loads DEX files specified in settings using `DexClassLoader`.
    *   **Observation:** This is a powerful feature. `getDexClassLoader` allows other modules (via service API) to access classes from these loaded DEX files.

9.  **`TemplateManager.java`, `SSLUnpinningTemplate.java`**:
    *   **Observation:** These are not directly used by `SuperPatcherModule.java` in the provided snippet. They seem to be helper/utility classes for generating common patch JSON configurations.
    *   **Needed Change:** If these are meant to be used by `SuperPatcher` (e.g., to offer pre-defined patches), the module needs to integrate them. Or, they might be for a UI or a developer tool that generates JSON for `SuperPatcher`. Their purpose should be clarified. If they generate JSON for the *old* hook structure, they'll also need updates.

**Summary of Changes/Additions for `SuperPatcherModule.java`:**

*   **Critical & Extensive:**
    1.  **Migrate from Old Xposed API to New `io.github.libxposed.api`:** Complete rewrite of all hooking and Xposed utility usage.
*   **Major:**
    1.  **Add `@XposedPlugin` and `@HotReloadable` Annotations.**
    2.  **Update Hot Reload Logic:** Use new API unhookers and re-apply patches/reload DEX files.
    3.  **Update Service API methods:** Internal implementations of `getFieldValue`, `invokeMethod`, etc., must use the new API or reflection.
*   **Moderate:**
    1.  **Document JSON Patch Structure in `API.md`:** The `patchDefinitions` JSON structure is key and needs to be formally documented, not just the conceptual Java `PatchDefinition`.
*   **Minor/Clarification:**
    1.  Clarify the role and integration of `TemplateManager` and `SSLUnpinningTemplate`. If they generate patch JSON, ensure they align with any changes post-API migration.

This concludes the analysis of the individual module implementations. Each module requires significant effort to migrate to the new Xposed API. Hot reload needs to be consistently implemented to re-apply hooks. Settings JSON documentation needs to be aligned with actual code usage. Service APIs (if any) should be clearly defined and documented.

I will list all files that need changes and the general summary of changes for each.

**Summary of Required Changes by File:**

**1. `modules/IntentMaster/src/main/java/com/wobbz/modules/intentmaster/IntentMasterModule.java`**
    *   **Implement Hot Reload:** Add `@HotReloadable`, implement `onHotReload` (store/use `MethodUnhooker`, reload settings, re-apply hooks).
    *   **Replace `MockSettingsHelper`:** Use framework's `SettingsHelper`.
    *   **Verify/Implement "Test Intent Feature"** if it's a core logic feature.
    *   **Review Lifecycle & `XposedInterface` Usage:** Ensure proper context for `XposedInterface`.
    *   **Documentation:** Update `IntentMaster/API.md` for `@XposedPlugin` usage and settings schema.

**2. `modules/DebugAll/src/main/java/com/wobbz/debugall/DebugAllModule.java`**
    *   **Critical Fixes:**
        *   `DebugFlagHooker` needs access to module's `debugLevel` and `targetApps`.
        *   Update outdated `PackageParser$Package` hook target.
        *   Use correct `ApplicationInfo.FLAG_*` constants.
    *   **Major Fixes:**
        *   `onHotReload` must re-apply hooks.
        *   Hook system method for `ApplicationInfo` once (e.g., in `onSystemServerLoaded`).
    *   **Moderate Fixes:**
        *   Simplify initialization flow.
        *   Review/align profiling and legacy storage flags with documentation/intent.
    *   **Documentation:** Update `DebugAll/API.md` if hook targets/flags change.

**3. `modules/DeepIntegrator/src/main/java/com/wobbz/deepintegrator/DeepIntegratorModule.java`**
    *   **Critical Fixes:**
        *   Static hookers (`ComponentModificationHook`, `PermissionBypassHook`) must use module's configuration and methods (e.g., become non-static or get module instance).
    *   **Major Fixes:**
        *   Add `@XposedPlugin` and `@HotReloadable` annotations.
        *   Implement "Intent Filter Injection" in `IntentQueryHook` if feature is desired.
        *   `onHotReload` must re-apply `PackageManagerService` hooks.
        *   Make `findMethodInClass` more robust (use parameter types).
    *   **Moderate Fixes:**
        *   Update `DeepIntegrator/API.md` and `README.md`:
            *   Document that Service API *is* implemented.
            *   Provide actual `settings.json` schema (keys like `componentOverrides`).
            *   Document JSON structure for `ComponentConfig` and `ComponentAccessLog`.

**4. `modules/NetworkGuard/src/main/java/com/wobbz/networkguard/NetworkGuardModule.java`**
    *   **Critical & Extensive:** Migrate entirely from old Xposed API to new `io.github.libxposed.api`.
    *   **Major Fixes:**
        *   Add `@XposedPlugin` annotation.
        *   Update hot reload logic for new API.
        *   Implement public methods from `NetworkGuard/API.md` (e.g., `addFirewallRule`) or clarify they belong to `SecurityManager`.
        *   Implement `SecurityManager.SecurityListener` methods if needed.
    *   **Moderate Fixes:**
        *   Simplify context acquisition and initialization.
        *   Review hook scopes (Zygote vs. app-specific).
    *   **Documentation:** Update `NetworkGuard/API.md` to reflect actual implementation.

**5. `modules/PermissionOverride/src/main/java/com/wobbz/permissionoverride/PermissionOverrideModule.java`**
    *   **Critical & Extensive:** Migrate entirely from old Xposed API to new `io.github.libxposed.api`.
    *   **Major Fixes:**
        *   Add `@XposedPlugin` annotation.
        *   Update hot reload logic for new API and ensure re-application of hooks.
        *   Resolve settings JSON discrepancy between code and `API.md`.
        *   Clarify/refactor `IPermissionOverrideService` (if kept, simplify, update internals to new API).
    *   **Moderate Fixes:**
        *   Implement "FAKE_GRANT"/"FAKE_DENY" if feature is desired.
        *   Review hook scopes.
    *   **Documentation:** Update `PermissionOverride/API.md` (settings JSON, Service API, features).

**6. `modules/SuperPatcher/src/main/java/com/wobbz/superpatcher/SuperPatcherModule.java`**
    *   **Critical & Extensive:** Migrate entirely from old Xposed API to new `io.github.libxposed.api`.
    *   **Major Fixes:**
        *   Add `@XposedPlugin` and `@HotReloadable` annotations.
        *   Update hot reload logic for new API (re-apply patches, reload DEX).
        *   Update internal implementations of Service API methods (e.g., `getFieldValue`) to use new API/reflection.
    *   **Moderate Fixes:**
        *   Document the actual JSON structure for `patchDefinitions` in `SuperPatcher/API.md`.
    *   **Clarification:** Role of `TemplateManager.java` and `SSLUnpinningTemplate.java`.

**Cross-Cutting Concerns / Framework Level:**

*   **`IModulePlugin` and `XposedModuleInterface`:** Their role as a bridge or if modules should directly implement `io.github.libxposed.api.IXposedModule` needs to be consistent. The framework's `API.md` suggests `IXposedModule`.
*   **`LoggingHelper`:** Ensure it uses the new `XposedInterface.log()` or an equivalent that doesn't rely on `XposedBridge`.
*   **Context and `XposedInterface` Provision:** Ensure a clear and reliable way for modules to get `Context` and `XposedInterface` for various lifecycle stages (Zygote, system server, app load).
*   **Hot Reload Robustness:** The framework part that triggers `onHotReload` must correctly handle module re-initialization, and modules must correctly re-apply all state and hooks. The static `instance` pattern in modules like `IntentMaster` needs careful handling during hot reload.
*   **`API.md` and `README.md` for each module:** Systematically update these to reflect the *actual* implementation after changes, especially regarding settings JSON, public APIs, and Xposed API usage.

This detailed analysis should provide a clear path forward for refactoring and improving the modules.
