package com.wobbz.framework;

import android.content.Context;

import com.wobbz.framework.development.LoggingHelper;
import com.wobbz.framework.features.FeatureManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.libxposed.api.XposedModuleInterface;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;
import io.github.libxposed.api.XposedInterface;

/**
 * Core manager class for LSPosed plugins
 * Handles delegation to individual modules
 */
public class PluginManager implements FeatureManager.FeatureChangeListener {
    private static final String TAG = "PluginManager";
    private static PluginManager sInstance;
    
    private final List<IModulePlugin> mModules = new ArrayList<>();
    private final Map<String, IHotReloadable> mHotReloadableModules = new HashMap<>();
    private final Map<String, IModulePlugin> mModuleById = new HashMap<>();
    
    private Context mContext;
    private FeatureManager mFeatureManager;
    private XposedInterface mXposedInterface;
    
    private PluginManager() {
        // Private constructor for singleton
    }
    
    public static synchronized PluginManager getInstance() {
        if (sInstance == null) {
            sInstance = new PluginManager();
        }
        return sInstance;
    }
    
    /**
     * Initialize the plugin manager with a context.
     * This must be called before using feature toggles.
     */
    public void initialize(Context context) {
        mContext = context.getApplicationContext();
        mFeatureManager = FeatureManager.getInstance(mContext);
        mFeatureManager.addListener(this);
    }
    
    /**
     * Set the XposedInterface for logging
     * 
     * @param xposedInterface The XposedInterface to use for logging
     */
    public void setXposedInterface(XposedInterface xposedInterface) {
        mXposedInterface = xposedInterface;
        LoggingHelper.setXposedInterface(xposedInterface);
        
        // Initialize any already registered modules with the XposedInterface
        if (mContext != null) {
            for (IModulePlugin module : mModules) {
                initializeModuleWithContext(module);
            }
        }
    }
    
    /**
     * Register a module with the plugin manager
     */
    public void registerModule(IModulePlugin module) {
        String moduleClassName = module.getClass().getName();
        log("Registering module: " + moduleClassName);
        mModules.add(module);
        
        // Extract module ID from class name
        String moduleId = getModuleId(moduleClassName);
        mModuleById.put(moduleId, module);
        
        if (module instanceof IHotReloadable) {
            mHotReloadableModules.put(moduleClassName, (IHotReloadable) module);
        }
        
        // Initialize the module with context and XposedInterface if available
        initializeModuleWithContext(module);
    }
    
    /**
     * Initialize a module with context and XposedInterface if available
     */
    private void initializeModuleWithContext(IModulePlugin module) {
        if (mContext == null || mXposedInterface == null) {
            return;
        }
        
        try {
            // Try to initialize the module with context and XposedInterface
            try {
                module.getClass().getMethod("initialize", Context.class, XposedInterface.class)
                    .invoke(module, mContext, mXposedInterface);
                log("Initialized module " + module.getClass().getName() + " with context and XposedInterface");
            } catch (NoSuchMethodException e) {
                // Try the generic Object version as fallback
                try {
                    module.getClass().getMethod("initialize", Object.class, Object.class)
                        .invoke(module, mContext, mXposedInterface);
                    log("Initialized module " + module.getClass().getName() + " with context and XposedInterface (generic method)");
                } catch (NoSuchMethodException ex) {
                    // Method doesn't exist, that's fine
                    log("Module " + module.getClass().getName() + " doesn't have initialize method");
                }
            }
        } catch (Exception e) {
            log("Error initializing module " + module.getClass().getName() + ": " + e.getMessage());
        }
    }
    
    /**
     * Handle Zygote initialization
     */
    public void handleZygoteInit(ModuleLoadedParam startupParam) {
        // Log information about the current process environment
        String processName = "unknown";
        boolean isSystemServer = false;
        try {
            processName = startupParam.getProcessName();
            isSystemServer = startupParam.isSystemServer();
        } catch (Throwable t) {
            log("Error accessing startupParam details: " + t.getMessage());
        }

        log("Framework initializing in process: " + processName + 
            (isSystemServer ? " (System Server)" : " (App Process)"));
        log("Number of registered modules: " + mModules.size());

        // Call onModuleLoaded for each module
        for (IModulePlugin module : mModules) {
            try {
                module.onModuleLoaded(startupParam);
                log("Module " + module.getClass().getName() + " initialized via onModuleLoaded");
                
                // Initialize with context and XposedInterface if needed
                initializeModuleWithContext(module);
            } catch (Throwable t) {
                log("Error in module " + module.getClass().getName() + " onModuleLoaded: " + t.getMessage());
            }
        }
        
        log("Initial module registration and framework setup complete for process: " + processName);
    }
    
    /**
     * Handle package loading
     */
    public void handleLoadPackage(PackageLoadedParam lpparam) {
        // Get package name safely
        String packageName = getPackageName(lpparam);
        log("Package loaded: " + packageName);
        
        for (IModulePlugin module : mModules) {
            String moduleId = getModuleId(module.getClass().getName());
            // Check if the module is enabled for this package
            if (isModuleEnabledForPackage(moduleId, packageName)) {
                try {
                    module.onPackageLoaded(lpparam);
                } catch (Throwable t) {
                    log("Error handling package load in module " + 
                        module.getClass().getName() + ": " + t.getMessage());
                    log(t.toString());
                }
            } else {
                log("Module " + moduleId + " is disabled for package " + packageName + ", skipping");
            }
        }
    }
    
    /**
     * Extract package name from PackageLoadedParam
     */
    private String getPackageName(PackageLoadedParam lpparam) {
        try {
            return lpparam.getPackageName();
        } catch (Exception e) {
            return "unknown";
        }
    }
    
    /**
     * Handle hot reload for a module
     */
    public void handleHotReload(String moduleClassName) {
        log("Hot-reloading module: " + moduleClassName);
        
        IHotReloadable module = mHotReloadableModules.get(moduleClassName);
        if (module != null) {
            try {
                module.onHotReload();
                log("Module " + moduleClassName + " hot-reloaded successfully");
            } catch (Throwable t) {
                log("Error hot-reloading module " + moduleClassName + ": " + t.getMessage());
                log(t.toString());
            }
        } else {
            log("Module " + moduleClassName + " not found or doesn't support hot-reload");
        }
    }
    
    /**
     * Check if a module is enabled.
     * 
     * @param moduleId The module ID.
     * @return true if the module is enabled, false otherwise.
     */
    public boolean isModuleEnabled(String moduleId) {
        return mFeatureManager != null && mFeatureManager.isFeatureEnabled(moduleId);
    }
    
    /**
     * Check if a module is enabled for a specific package.
     * 
     * @param moduleId The module ID.
     * @param packageName The package name.
     * @return true if the module is enabled for the package, false otherwise.
     */
    public boolean isModuleEnabledForPackage(String moduleId, String packageName) {
        return mFeatureManager != null && mFeatureManager.isFeatureEnabledForPackage(moduleId, packageName);
    }
    
    /**
     * Get the module ID from a class name.
     * For example, "com.wobbz.debugall.DebugAllModule" -> "com.wobbz.DebugAll"
     */
    private String getModuleId(String className) {
        // This is a simple implementation; you may want to make this more robust
        String[] parts = className.split("\\.");
        if (parts.length >= 3) {
            // Extract the module name from the class name
            String moduleName = parts[parts.length - 2];
            // Convert first character to uppercase if needed
            if (moduleName.length() > 1) {
                moduleName = Character.toUpperCase(moduleName.charAt(0)) + moduleName.substring(1);
            }
            
            // Build the module ID
            return parts[0] + "." + parts[1] + "." + moduleName;
        }
        
        // Fallback to the class name
        return className;
    }
    
    /**
     * Handle feature change events from FeatureManager.
     */
    @Override
    public void onFeatureChanged(String featureId, boolean enabled) {
        log("Feature changed: " + featureId + " -> " + enabled);
        
        // If a module is disabled, we might want to clean up its hooks
        if (!enabled) {
            IModulePlugin module = mModuleById.get(featureId);
            if (module instanceof IHotReloadable) {
                try {
                    ((IHotReloadable) module).onHotReload();
                    log("Cleaned up module " + featureId + " after disabling");
                } catch (Throwable t) {
                    log("Error cleaning up module " + featureId + ": " + t.getMessage());
                    log(t.toString());
                }
            }
        }
    }
    
    /**
     * Log a message to the Xposed log
     * 
     * @param message The message to log
     */
    public void log(String message) {
        if (mXposedInterface != null) {
            mXposedInterface.log("[" + TAG + "] " + message);
        } else {
            // Fall back to LoggingHelper if XposedInterface not available
            LoggingHelper.info(TAG, message);
        }
    }
} 