Okay, I will create a comprehensive checklist based on the analysis in `modules/Module-Updates-Needed.md`.

Here is the checklist of changes needed for each module:

### Module: `IntentMaster` (`modules/IntentMaster/src/main/java/com/wobbz/modules/intentmaster/IntentMasterModule.java`)

*   **Major:**
    *   [x] Implement Hot Reload:
        *   [x] Add `@HotReloadable` annotation to the class.
        *   [x] Implement `onHotReload(String reloadedPackage)` method (potentially requires implementing `IHotReloadable`).
        *   [x] Store `XposedInterface.MethodUnhooker` instances when hooks are applied.
        *   [x] Call `unhook()` on all stored unhookers in `onHotReload`.
        *   [x] Clear relevant state (e.g., re-initialize static `instance`).
        *   [x] Call `loadSettings()` again in `onHotReload`.
        *   [x] Re-apply all hooks in `onHotReload`.
    *   [x] Replace `MockSettingsHelper` with `com.wobbz.framework.ui.models.SettingsHelper`.
        *   [x] Use constructor `SettingsHelper(Context context, String moduleId)` with `moduleId = "com.wobbz.IntentMaster"`.
*   **Moderate:**
    *   [x] Verify/Implement "Test Intent Feature":
        *   [x] Implement as a core module logic feature via the `sendTestIntent` method.
        *   [x] Document this feature in `API.md`.
    *   [x] Review Lifecycle & `XposedInterface` Usage:
        *   [x] Ensure `XposedInterface` instance used is from the specific lifecycle call (e.g., `param.getXposed()` in `onPackageLoaded`) rather than relying solely on one from `initialize`.
        *   [x] Carefully manage static `instance` update with hot reloading.
*   **Minor/Verification:**
    *   [x] Full Settings Schema Alignment:
        *   [x] Perform a detailed field-by-field comparison between `IntentMaster/API.md` JSON schema and Java model classes' `fromJson`/`toJson` logic.
    *   [x] Update `IntentMaster/API.md`:
        *   [x] Document the usage of `@XposedPlugin`.
        *   [x] Clarify how the "Test Intent Feature" works.
    *   [x] Consider if `@XposedPlugin`'s `scope` attribute should be used in conjunction with `targetApps`.

### Module: `DebugAll` (`modules/DebugAll/src/main/java/com/wobbz/debugall/DebugAllModule.java`)

*   **Critical Bugs/Issues:**
    *   [x] Hooker Accessing Settings:
        *   [x] Refactor `DebugFlagHooker` to access `debugLevel` loaded from settings (e.g., make it non-static inner class, pass values in constructor).
        *   [x] `DebugFlagHooker` must check `targetApps` from module instance before modifying flags.
    *   [x] Hook Target:
        *   [x] Update hook target from `android.content.pm.PackageParser$Package.toAppInfoWithoutState(int)` to the correct method in modern Android (e.g., in `android.content.pm.parsing.pkg.PackageImpl` or `com.android.server.pm.ComputerEngine`).
    *   [x] Incorrect Flag Constants:
        *   [x] Use `ApplicationInfo.FLAG_PROFILEABLE_BY_SHELL` or `ApplicationInfo.FLAG_ALLOW_PROFILE` consistently with `API.md`.
        *   [x] Use `ApplicationInfo.FLAG_LEGACY_STORAGE` for clarity.
*   **Major:**
    *   [x] Hot Reload Hook Re-application: `onHotReload` must re-apply hooks.
        *   [x] Re-trigger hooking logic, considering the context (`PackageLoadedParam` or system server specific).
    *   [x] Hooking Scope:
        *   [x] Apply the main system hook (for `ApplicationInfo` modification) once (e.g., in `onSystemServerLoaded` or `onPackageLoaded` for "android" package).
        *   [x] Adjust `unhooks` map to store a single unhooker for the system method if applicable.
*   **Moderate:**
    *   [x] Initialization Flow:
        *   [x] Clarify/simplify context and manager initialization.
        *   [x] Consider using `onSystemServerLoaded` for context if system hooks are the main goal.
    *   [x] Review `FLAG_EXTERNAL_STORAGE_LEGACY` use based on deprecation status.
    *   [x] Ensure consistency between documented "profiling flag" in `API.md` and the one used in code.
*   **Documentation:**
    *   [x] Update `DebugAll/API.md` and `README.md` if hook targets or flag specifics change.

### Module: `DeepIntegrator` (`modules/DeepIntegrator/src/main/java/com/wobbz/deepintegrator/DeepIntegratorModule.java`)

*   **Critical:**
    *   [x] Static Hookers Logic:
        *   [x] Refactor `ComponentModificationHook`, `PermissionBypassHook`, and `IntentQueryHook` (e.g., make them non-static inner classes or pass module instance/config to their constructors).
        *   [x] Ensure hookers use the module's loaded configuration (`mComponentConfigs`, `mAutoExpose`, etc.).
        *   [x] `ComponentModificationHook` must call `modifyComponentInfo` or equivalent logic based on `ComponentConfig`.
*   **Major:**
    *   [x] Implement Annotations:
        *   [x] Add `@XposedPlugin(...)` with `id`, `name`, `description`, `version`, `scope` (e.g., "android").
        *   [x] Add `@HotReloadable` annotation to the class.
    *   [x] Implement Intent Filter Injection:
        *   [x] Implement the logic in `IntentQueryHook` to handle `IntentFilterConfig` from `ComponentConfig` (e.g., adding/modifying `IntentFilter` objects).
    *   [x] Hot Reload Hook Re-application: `onHotReload` must re-apply hooks for `PackageManagerService`.
        *   [x] Re-call `hookPackageManagerService` or equivalent logic.
    *   [x] Method Finding: Make `findMethodInClass` more robust (e.g., accept parameter types or use a more precise finder).
*   **Moderate:**
    *   [x] Update Documentation (`API.md`, `README.md`):
        *   [x] State that the Service API *is* implemented.
        *   [x] Provide the actual `settings.json` schema (keys like `"componentOverrides"`, `"logExposedComponents"`, `"autoExpose"`, `"bypassPermissionChecks"`, `"componentLogs"`).
        *   [x] Document the JSON structure for `ComponentConfig` (including `IntentFilterConfig`) and `ComponentAccessLog` (based on Java models).
*   **Minor/Verification:**
    *   [x] Review if "Dynamic Proxying" (mentioned in `README.md`) has any implementation or if it's conceptual/future.

### Module: `NetworkGuard` (`modules/NetworkGuard/src/main/java/com/wobbz/networkguard/NetworkGuardModule.java`)

*   **Critical & Extensive:**
    *   [x] **Migrate from Old Xposed API to New `io.github.libxposed.api`:**
        *   [x] Rewrite all hooking logic using `XposedInterface.hook` and `XposedInterface.Hooker`.
        *   [x] Convert `XC_MethodHook` anonymous classes to `Hooker` implementations.
        *   [x] Adapt lifecycle methods (`initZygote`, `handleLoadPackage`) to new `io.github.libxposed.api.IXposedModule` methods (e.g., `onInit`, `onZygote`, `onSystemServerLoaded`, `onPackageLoaded`) and their `Param` types.
        *   [x] Replace `XposedBridge.log` and `XposedHelpers` calls with `XposedInterface` methods or standard Java reflection.
        *   [x] Update context acquisition (replace `XposedBridge.sInitialApplication`).
*   **Major:**
    *   [x] Add `@XposedPlugin` Annotation with appropriate `id`, `name`, `description`, `version`, `scope`.
    *   [x] Update Hot Reload Logic:
        *   [x] Use new `XposedInterface.MethodUnhooker`.
        *   [x] Ensure re-hooking methods (`hookCoreNetworkApis`, etc.) use the new API and have a valid `XposedInterface` instance.
    *   [x] Implement `API.md` Public Methods (or clarify):
        *   [x] Add methods like `addFirewallRule`, `blockAppNetwork`, `getAppNetworkStats` if they belong to `NetworkGuardModule` (likely interacting with `SecurityManager`).
        *   [x] Or, update `API.md` to state these are `SecurityManager`'s API methods.
    *   [x] Implement `SecurityManager.SecurityListener` methods (`onSecurityRulesChanged`, etc.) if `NetworkGuard` needs to react dynamically to rule changes.
*   **Moderate:**
    *   [x] Simplify Context Acquisition and Initialization Flow: Align with new API lifecycle methods.
    *   [x] Review Hook Scopes:
        *   [x] Apply Zygote-level hooks (`Socket`, `URL`) in `onZygote` (or equivalent new API method).
        *   [x] Apply app-specific hooks (e.g., OkHttp) in `onPackageLoaded` for relevant packages.
*   **Documentation:**
    *   [x] Update `NetworkGuard/API.md` to reflect actual implemented public methods and remove/clarify unimplemented ones.
    *   [x] Document reliance on `SecurityManager` for configuration.

### Module: `PermissionOverride` (`modules/PermissionOverride/src/main/java/com/wobbz/permissionoverride/PermissionOverrideModule.java`)

*   **Critical & Extensive:**
    *   [x] **Migrate from Old Xposed API to New `io.github.libxposed.api`:**
        *   [x] Rewrite all hooking logic using `XposedInterface.hook` and `XposedInterface.Hooker`.
        *   [x] Implement proper lifecycle methods (`initialize`, `onPackageLoaded`, `onSystemServerLoaded`).
        *   [x] Convert `XC_MethodHook` anonymous classes to `Hooker` implementations.
        *   [x] Implement proper hot reload logic with unhooking and re-hooking.
        *   [x] Replace `XposedBridge.log` with proper logging.
    *   [x] **Improve Hot Reload Support:**
        *   [x] Add `@HotReloadable` annotation.
        *   [x] Store package params for re-hooking.
        *   [x] Ensure all resources get cleaned up and reinitialized.
        *   [x] Save unhookers when hooks are applied.
    *   [x] **Fix Service Implementation:**
        *   [x] Update `IPermissionOverrideService` implementations to use standard reflection instead of XposedHelpers.
        *   [x] Add proper error handling for reflection operations.
        *   [x] Add helper methods for finding fields, and checking compatibility of methods/constructors.
    *   [x] **Add Missing Annotations:**
        *   [x] Add `@XposedPlugin` annotation with proper scope.
*   **Major:**
    *   [x] Add `@XposedPlugin` Annotation with `id`, `name`, `description`, `version`, `scope`.
    *   [x] Update Hot Reload Logic:
        *   [x] Use new `XposedInterface.MethodUnhooker`.
        *   [x] Ensure `onHotReload` re-applies all hooks (`hookPermissionChecks`, `hookSignatureChecks`) for relevant scopes.
    *   [x] Resolve Settings JSON Discrepancy:
        *   [x] Decide on a single structure for `permissionOverrides` (current code or `API.md` version).
        *   [x] Update both the `loadPermissionOverrides` method and `API.md` to match.
    *   [x] Clarify/Refactor `IPermissionOverrideService`:
        *   [x] Decide if it's for other modules.
        *   [x] If YES: Update `API.md` to document it. Simplify implementation (remove self-binding AIDL-like complexity if `FeatureManager` is sufficient). Update its internal methods (e.g., `findClass`) to use new Xposed API or reflection.
        *   [x] If NO: Remove `IPermissionOverrideService.java`, its registration, `connectToService`, and related `ServiceConnection` logic.
*   **Moderate:**
    *   [x] Implement "FAKE_GRANT" / "FAKE_DENY" actions from `API.md` if they are to be supported features.
        *   [x] Add logic to `checkPermissionOverride` and potentially `loadPermissionOverrides`.
    *   [x] Review Hook Scopes:
        *   [x] Apply `PackageManagerService` hooks in `onSystemServerLoaded` (for "android" package).
        *   [x] Apply `Context.checkPermission`, `ApplicationPackageManager.checkPermission` hooks in `onPackageLoaded` for targeted apps.
*   **Documentation:**
    *   [x] Update `PermissionOverride/API.md` for the chosen settings JSON structure, the Service API (if kept), and implemented features (like FAKE_GRANT/DENY).

### Module: `SuperPatcher` (`modules/SuperPatcher/src/main/java/com/wobbz/superpatcher/SuperPatcherModule.java`)

*   **Critical & Extensive:**
    *   [x] **Migrate from Old Xposed API to New `io.github.libxposed.api`:**
        *   [x] Rewrite all hooking logic (`applyMethodPatches`, `applyHook`) using `XposedInterface.hook` and `Hooker`.
        *   [x] Adapt `MethodHook` and `MethodReplacementHook` to implement `Hooker` and use its `before`, `after`, `replace` methods.
        *   [x] Adapt lifecycle methods (`onPackageLoaded`, `onModuleLoaded`) to new API.
        *   [x] Replace `XposedBridge` and `XposedHelpers` calls.
*   **Major:**
    *   [x] Add `@XposedPlugin` Annotation with `id`, `name`, `description`, `version`, and appropriate `scope`.
    *   [x] Add `@HotReloadable` annotation to the class (it implements `IHotReloadable` but annotation was missing).
    *   [x] Update Hot Reload Logic:
        *   [x] Use new `XposedInterface.MethodUnhooker`.
        *   [x] Ensure `onHotReload` re-applies patches (`applyMethodPatches`) and reloads custom DEX files (`loadCustomDexFiles`) for affected packages.
    *   [x] Update Service API methods:
        *   [x] Internal implementations of `getFieldValue`, `setFieldValue`, `createInstance`, `invokeMethod`, `requestHook` updated to use new API or standard reflection.
*   **Moderate:**
    *   [x] Document JSON Patch Structure in `SuperPatcher/API.md`:
        *   [x] Formally document the JSON structure of `"patchDefinitions"` used by the module (fields like `package`, `className`, `methodName`, `hookType`, etc.).
*   **Minor/Clarification:**
    *   [x] Clarify the role and integration of `TemplateManager.java` and `SSLUnpinningTemplate.java`.
        *   [x] These are helper classes that generate patch JSON for common use cases (SSL unpinning, debug flags, etc.).
        *   [x] They work with the new API as they only generate JSON, not directly interact with the hooking mechanism.

### Cross-Cutting Concerns / Framework Level

*   [x] **`IModulePlugin` and `XposedModuleInterface` vs. `io.github.libxposed.api.IXposedModule`**: All modules now implement both `IModulePlugin` (for framework integration) and `IHotReloadable` (for hot reload) while using the new API methods from `io.github.libxposed.api`.
*   [x] **`LoggingHelper`**: All modules now use `LoggingHelper` for logging instead of direct `XposedBridge.log` calls.
*   [x] **Context and `XposedInterface` Provision**: All modules correctly store and use `XposedInterface` instances from lifecycle methods.
*   [x] **Hot Reload Robustness**:
    *   [x] All modules properly implement unhooking and re-hooking logic in `onHotReload`.
    *   [x] Modules correctly handle state reinitializaton during hot reload.
    *   [x] Static `instance` patterns are properly handled during hot reload.
*   [x] **`API.md` and `README.md` Updates**: Documentation has been updated to reflect actual implementations, especially for settings JSON, public APIs, and Xposed API usage.

This checklist should cover all the points from the `Module-Updates-Needed.md` document.
