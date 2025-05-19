package com.wobbz.framework;

import android.content.Context;

import com.wobbz.framework.development.LoggingHelper;
import com.wobbz.framework.features.FeatureManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

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
    }
    
    /**
     * Handle Zygote initialization
     */
    public void handleZygoteInit(IXposedHookZygoteInit.StartupParam startupParam) {
        log("Initializing " + mModules.size() + " modules in Zygote");
        
        for (IModulePlugin module : mModules) {
            // Check if the module is enabled
            String moduleId = getModuleId(module.getClass().getName());
            if (isModuleEnabled(moduleId)) {
                try {
                    module.initZygote(startupParam);
                } catch (Throwable t) {
                    log("Error initializing module " + module.getClass().getName() + ": " + t.getMessage());
                    XposedBridge.log(t);
                }
            } else {
                log("Module " + moduleId + " is disabled, skipping initialization");
            }
        }
    }
    
    /**
     * Handle package loading
     */
    public void handleLoadPackage(LoadPackageParam lpparam) {
        log("Package loaded: " + lpparam.packageName);
        
        for (IModulePlugin module : mModules) {
            String moduleId = getModuleId(module.getClass().getName());
            // Check if the module is enabled for this package
            if (isModuleEnabledForPackage(moduleId, lpparam.packageName)) {
                try {
                    module.handleLoadPackage(lpparam);
                } catch (Throwable t) {
                    log("Error handling package load in module " + 
                        module.getClass().getName() + ": " + t.getMessage());
                    XposedBridge.log(t);
                }
            } else {
                log("Module " + moduleId + " is disabled for package " + lpparam.packageName + ", skipping");
            }
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
                XposedBridge.log(t);
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
                    XposedBridge.log(t);
                }
            }
        }
    }
    
    private void log(String message) {
        if (LoggingHelper.class != null) {
            LoggingHelper.info(TAG, message);
        } else {
            XposedBridge.log("[" + TAG + "] " + message);
        }
    }
} 