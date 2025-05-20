package com.wobbz.framework;

import android.content.Context;

import com.wobbz.framework.development.LoggingHelper;
import com.wobbz.framework.features.FeatureManager;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github.libxposed.api.XposedModuleInterface;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;
import io.github.libxposed.api.XposedInterface;

/**
 * Manages the lifecycle and dispatch of events for all registered {@link IModulePlugin} instances
 * within the Wobbz LSPosed Modular Framework.
 *
 * <p>This class acts as the central coordinator for modules. It handles:
 * <ul>
 *     <li>Registration and tracking of modules.</li>
 *     <li>Initialization of modules with necessary context and Xposed interfaces.</li>
 *     <li>Delegation of standard Xposed lifecycle callbacks (e.g., {@code onModuleLoaded}, {@code onPackageLoaded})
 *         to all relevant and enabled modules.</li>
 *     <li>Orchestration of the hot-reloading process for modules that support it.</li>
 *     <li>Integration with a {@link FeatureManager} to check if modules or specific features are enabled.</li>
 *     <li>Centralized logging capabilities.</li>
 * </ul>
 * </p>
 *
 * <p>The {@code PluginManager} is implemented as a singleton and should be initialized early
 * in the Xposed entry point, typically by providing it with an {@link XposedInterface} instance
 * and an Android {@link Context}.</p>
 */
public class PluginManager implements FeatureManager.FeatureChangeListener {
    private static final String TAG = "PluginManager";
    private static volatile PluginManager sInstance;
    
    private final List<IModulePlugin> mModules = new ArrayList<>();
    private final Map<String, IHotReloadable> mHotReloadableModules = new HashMap<>();
    private final Map<String, IModulePlugin> mModuleById = new HashMap<>();
    
    private Context mContext;
    private FeatureManager mFeatureManager;
    private XposedInterface mXposedInterface;
    
    /**
     * Private constructor to enforce the singleton pattern.
     */
    private PluginManager() {
        // Private constructor for singleton
    }
    
    /**
     * Retrieves the singleton instance of the {@code PluginManager}.
     *
     * @return The singleton {@code PluginManager} instance.
     */
    public static PluginManager getInstance() {
        if (sInstance == null) {
            synchronized (PluginManager.class) {
                if (sInstance == null) {
                    sInstance = new PluginManager();
                }
            }
        }
        return sInstance;
    }
    
    /**
     * Initializes the PluginManager with essential Android and Xposed components.
     * This method should be called early, ideally from the main Xposed entry point.
     * It sets up the application context and initializes the {@link FeatureManager}
     * for toggling module features.
     *
     * @param context The application context. Should be obtained safely.
     */
    public void initialize(Context context) {
        if (this.mContext != null) {
            log("PluginManager already initialized. Skipping re-initialization.");
            return;
        }
        this.mContext = context.getApplicationContext();
        this.mFeatureManager = FeatureManager.getInstance(this.mContext);
        this.mFeatureManager.addListener(this);
        log("PluginManager initialized with context. FeatureManager ready.");
    }
    
    /**
     * Sets the {@link XposedInterface} to be used by the PluginManager and its modules.
     * This is crucial for logging via Xposed and for modules to interact with the Xposed framework.
     * Once set, it also attempts to initialize any modules that were registered before the
     * XposedInterface was available.
     *
     * @param xposedInterface The {@link XposedInterface} instance obtained from LSPosed.
     */
    public void setXposedInterface(XposedInterface xposedInterface) {
        if (this.mXposedInterface != null && this.mXposedInterface == xposedInterface) {
            log("XposedInterface already set to the same instance.");
            return;
        }
        this.mXposedInterface = xposedInterface;
        LoggingHelper.setXposedInterface(xposedInterface); // Configure shared logger
        log("XposedInterface set. Logging configured.");

        // Initialize any already registered modules that might have missed it.
        if (mContext != null) {
            for (IModulePlugin module : mModules) {
                initializeModuleWithFrameworkDependencies(module);
            }
        }
    }
    
    /**
     * Registers a new {@link IModulePlugin} with the manager.
     * Registered modules will receive lifecycle callbacks and can be managed by the framework.
     * If the module implements {@link IHotReloadable}, it will also be registered for hot-reloading.
     *
     * @param module The module instance to register. Must not be null.
     */
    public void registerModule(IModulePlugin module) {
        if (module == null) {
            logError("Attempted to register a null module.");
            return;
        }
        String moduleClassName = module.getClass().getName();
        if (mModules.contains(module)) {
            log("Module " + moduleClassName + " is already registered. Skipping.");
            return;
        }

        log("Registering module: " + moduleClassName);
        mModules.add(module);

        String moduleId = module.getModuleId(); // Use the module's own reported ID
        if (mModuleById.containsKey(moduleId)) {
            logWarn("Module ID '" + moduleId + "' from " + moduleClassName +
                    " conflicts with existing module: " + mModuleById.get(moduleId).getClass().getName() +
                    ". Check module IDs for uniqueness.");
        }
        mModuleById.put(moduleId, module);

        if (module instanceof IHotReloadable) {
            mHotReloadableModules.put(moduleClassName, (IHotReloadable) module);
            log("Module " + moduleClassName + " registered for hot-reloading.");
        }

        initializeModuleWithFrameworkDependencies(module);
    }
    
    /**
     * Initializes a given module if the necessary framework dependencies (Context and XposedInterface)
     * are available. This method attempts to call an optional {@code initialize(Context, XposedInterface)}
     * method on the module for framework-specific setup.
     *
     * @param module The module to initialize.
     */
    private void initializeModuleWithFrameworkDependencies(IModulePlugin module) {
        if (mContext == null) {
            log("Context not yet available, deferring full initialization of " + module.getClass().getName());
            return;
        }
        if (mXposedInterface == null) {
            log("XposedInterface not yet available, deferring full initialization of " + module.getClass().getName());
            return;
        }

        // Check if already initialized by looking for a hypothetical flag or state if necessary
        // For now, we rely on modules to be idempotent or manage their own init state.

        try {
            Method initMethod = null;
            try {
                initMethod = module.getClass().getMethod("initialize", Context.class, XposedInterface.class);
            } catch (NoSuchMethodException e) {
                // Fallback: Try with generic Object, Object (less type-safe, for broader compatibility if needed)
                try {
                    initMethod = module.getClass().getMethod("initialize", Object.class, Object.class);
                } catch (NoSuchMethodException ex) {
                    // Method doesn't exist, which is acceptable as it's optional.
                    logVerbose("Module " + module.getClass().getName() + " does not have a framework initialize(Context, XposedInterface) method.");
                    return;
                }
            }

            initMethod.invoke(module, mContext, mXposedInterface);
            log("Successfully called framework initialize method on module " + module.getClass().getName());

        } catch (Exception e) {
            logError("Error during framework initialization of module " + module.getClass().getName() + ": " + e.getMessage(), e);
        }
    }
    
    /**
     * Handles the {@code onModuleLoaded} (historically Zygote/SystemServer init) phase of the Xposed lifecycle.
     * It logs process information and delegates the {@link IModulePlugin#onModuleLoaded(ModuleLoadedParam)}
     * callback to all registered modules.
     *
     * @param startupParam Parameters provided by LSPosed regarding the loaded module context.
     */
    public void handleModuleLoaded(ModuleLoadedParam startupParam) {
        String processName = Optional.ofNullable(startupParam).map(ModuleLoadedParam::getProcessName).orElse("unknown");
        boolean isSystemServer = Optional.ofNullable(startupParam).map(ModuleLoadedParam::isSystemServer).orElse(false);

        log("Framework handleModuleLoaded in process: " + processName +
            (isSystemServer ? " (System Server)" : " (App Process)") + ". " +
            mModules.size() + " modules registered.");

        for (IModulePlugin module : mModules) {
            try {
                // Ensure XposedInterface is set for the module before this call if possible
                // This is typically handled by setXposedInterface or module registration logic
                module.onModuleLoaded(startupParam);
                log("Module " + module.getClass().getName() + " notified: onModuleLoaded for process " + processName);
                initializeModuleWithFrameworkDependencies(module); // Ensure framework init is also attempted
            } catch (Throwable t) {
                logError("Error in module " + module.getClass().getName() + " during onModuleLoaded for " + processName + ": " + t.getMessage(), t);
            }
        }
        log("Finished handleModuleLoaded for process: " + processName);
    }
    
    /**
     * Handles the {@code onSystemServerLoaded} phase of the Xposed lifecycle.
     * Delegates the callback to all registered modules.
     *
     * @param param Parameters provided by LSPosed regarding the loaded system server.
     */
    public void handleSystemServerLoaded(XposedModuleInterface.SystemServerLoadedParam param) {
        log("System server loaded. Notifying " + mModules.size() + " modules.");
        
        for (IModulePlugin module : mModules) {
            try {
                module.onSystemServerLoaded(param);
                log("Module " + module.getClass().getName() + " notified: onSystemServerLoaded");
            } catch (Throwable t) {
                logError("Error in module " + module.getClass().getName() + " during onSystemServerLoaded: " + t.getMessage(), t);
            }
        }
    }
    
    /**
     * Handles the {@code onPackageLoaded} phase of the Xposed lifecycle.
     * It delegates the {@link IModulePlugin#onPackageLoaded(PackageLoadedParam)} callback
     * to all registered modules, but only if the module is enabled for the specific package
     * according to the {@link FeatureManager}.
     *
     * @param lpparam Parameters provided by LSPosed regarding the loaded package.
     */
    public void handleLoadPackage(PackageLoadedParam lpparam) {
        if (lpparam == null) {
            logError("handleLoadPackage received null lpparam. Skipping.");
            return;
        }
        String packageName = Optional.ofNullable(lpparam.getPackageName()).orElse("unknown");
        if ("unknown".equals(packageName)) {
            logWarn("handleLoadPackage: Package name is unknown. Modules may not be filtered correctly.");
        }

        log("Package loaded: " + packageName + ". Checking " + mModules.size() + " modules.");

        for (IModulePlugin module : mModules) {
            String moduleId = module.getModuleId();
            if (isModuleEnabledForPackage(moduleId, packageName)) {
                try {
                    logVerbose("Notifying module " + moduleId + " for package " + packageName);
                    module.onPackageLoaded(lpparam);
                } catch (Throwable t) {
                    logError("Error in module " + moduleId + " (" + module.getClass().getName() +
                             ") during onPackageLoaded for " + packageName + ": " + t.getMessage(), t);
                }
            } else {
                logVerbose("Module " + moduleId + " is disabled for package " + packageName + ", skipping onPackageLoaded.");
            }
        }
    }
    
    /**
     * Initiates the hot-reloading process for a specified module.
     * The module must be registered and implement {@link IHotReloadable}.
     *
     * @param moduleClassName The fully qualified class name of the module to reload.
     */
    public void handleHotReload(String moduleClassName) {
        if (moduleClassName == null || moduleClassName.isEmpty()) {
            logError("handleHotReload called with null or empty moduleClassName.");
            return;
        }
        log("Attempting hot-reload for module: " + moduleClassName);

        IHotReloadable module = mHotReloadableModules.get(moduleClassName);
        if (module != null) {
            try {
                module.onHotReload();
                log("Module " + moduleClassName + " hot-reloaded successfully.");
            } catch (Throwable t) {
                logError("Error hot-reloading module " + moduleClassName + ": " + t.getMessage(), t);
            }
        } else {
            logWarn("Module " + moduleClassName + " not found among hot-reloadable modules or does not support hot-reload.");
        }
    }
    
    /**
     * Checks if a module, identified by its ID, is currently enabled according to the {@link FeatureManager}.
     *
     * @param moduleId The unique ID of the module.
     * @return {@code true} if the module is enabled, {@code false} otherwise or if FeatureManager is unavailable.
     */
    public boolean isModuleEnabled(String moduleId) {
        if (mFeatureManager == null) {
            logWarn("FeatureManager not available. Cannot check if module '" + moduleId + "' is enabled. Assuming disabled.");
            return false;
        }
        return mFeatureManager.isFeatureEnabled(moduleId);
    }
    
    /**
     * Checks if a module is enabled for a specific target package.
     * This allows for fine-grained control over where a module's hooks are active.
     *
     * @param moduleId The unique ID of the module.
     * @param packageName The name of the target package.
     * @return {@code true} if the module is enabled for the given package, {@code false} otherwise.
     */
    public boolean isModuleEnabledForPackage(String moduleId, String packageName) {
        if (mFeatureManager == null) {
            logWarn("FeatureManager not available. Cannot check if module '" + moduleId + "' is enabled for package '" + packageName + "'. Assuming disabled.");
            return false;
        }
        // Consider a default behavior: if a module is generally enabled but has no specific package rules,
        // it might be considered enabled for all its declared scopes or all packages.
        // For now, relies purely on FeatureManager's specific check.
        return mFeatureManager.isFeatureEnabledForPackage(moduleId, packageName);
    }
    
    /**
     * Callback from {@link FeatureManager} when a feature's enabled state changes.
     * This can be used to dynamically react to feature toggles, e.g., by re-evaluating hooks.
     *
     * @param featureId The ID of the feature (often a module ID) that changed.
     * @param enabled The new enabled state of the feature.
     */
    @Override
    public void onFeatureChanged(String featureId, boolean enabled) {
        log("Feature '" + featureId + "' changed state to: " + (enabled ? "ENABLED" : "DISABLED"));
        // Potentially trigger a re-evaluation or re-hooking for the affected module.
        // This might involve finding the module by featureId and calling a specific method
        // or re-triggering onPackageLoaded for relevant packages if it was disabled/enabled.
        IModulePlugin module = mModuleById.get(featureId);
        if (module != null) {
            log("Module associated with feature: " + module.getClass().getName());
            // If a module was just enabled, it might need its onPackageLoaded calls triggered
            // for already loaded packages. This is complex and might require caching lpparams
            // or specific re-initialization logic in modules.
            // If it was disabled, existing hooks should ideally be removed. This is also complex
            // and often best handled by a hot-reload or a targeted unhooking mechanism.
            log("Further actions on module state change (e.g., re-hooking) are not yet implemented here.");
        } else {
            logWarn("No module directly associated with feature ID: " + featureId + " found for state change reaction.");
        }
    }
    
    // --- Logging --- //
    
    /**
     * Logs a verbose message using {@link LoggingHelper}.
     * Visible if verbose logging is enabled.
     * @param message The message to log.
     */
    private void logVerbose(String message) {
        LoggingHelper.logVerbose(TAG, message);
    }
    
    /**
     * Logs an informational message using {@link LoggingHelper}.
     * @param message The message to log.
     */
    private void log(String message) {
        LoggingHelper.logInfo(TAG, message);
    }
    
    /**
     * Logs a warning message using {@link LoggingHelper}.
     * @param message The message to log.
     */
    private void logWarn(String message) {
        LoggingHelper.logWarning(TAG, message);
    }
    
    /**
     * Logs an error message using {@link LoggingHelper}.
     * @param message The error message.
     * @param throwable An optional throwable to log with stack trace.
     */
    private void logError(String message, Throwable throwable) {
        LoggingHelper.logError(TAG, message, throwable);
    }
    
    /**
     * Logs an error message using {@link LoggingHelper}.
     * @param message The error message.
     */
    private void logError(String message) {
        LoggingHelper.logError(TAG, message, null);
    }
} 