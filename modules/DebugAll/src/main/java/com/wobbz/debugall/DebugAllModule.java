package com.wobbz.debugall;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;

import com.wobbz.framework.IHotReloadable;
import com.wobbz.framework.IModulePlugin;
import com.wobbz.framework.analytics.AnalyticsManager;
import com.wobbz.framework.annotations.HotReloadable;
import com.wobbz.framework.annotations.XposedPlugin;
import com.wobbz.framework.development.LoggingHelper;
import com.wobbz.framework.ui.models.SettingsHelper;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedInterface.Hooker;
import io.github.libxposed.api.XposedInterface.BeforeHookCallback;
import io.github.libxposed.api.XposedInterface.AfterHookCallback;
import io.github.libxposed.api.XposedInterface.MethodUnhooker;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;

/**
 * Module to force debug flags on apps based on user configuration.
 * Supports targeting specific apps and configuring different debug flags.
 */
@XposedPlugin(
    id = "com.wobbz.debugall",
    name = "Debug-All",
    description = "Configure debug flags for selected applications",
    version = "1.0.0",
    scope = {"android", "com.android.systemui"},
    permissions = {"android.permission.READ_LOGS"}
)
@HotReloadable
public class DebugAllModule implements IModulePlugin, IHotReloadable {
    private static final String TAG = "DebugAll";
    private Map<String, MethodUnhooker<?>> unhooks = new HashMap<>();
    private Set<String> targetApps = new HashSet<>();
    private boolean verboseLogging = false;
    private String debugLevel = "info";
    private Context mModuleContext;
    private AnalyticsManager mAnalyticsManager;
    private SettingsHelper mSettingsHelper;
    private boolean mManagersInitialized = false;
    private XposedInterface mXposedInterface;
    
    // Debug flag options
    private static final int FLAG_DEBUGGABLE = ApplicationInfo.FLAG_DEBUGGABLE;
    private static final int FLAG_ENABLE_PROFILING = 0x00000004; // ApplicationInfo.FLAG_ENABLE_PROFILING
    private static final int FLAG_EXTERNAL_STORAGE_LEGACY = 0x00400000; // ApplicationInfo.FLAG_EXTERNAL_STORAGE_LEGACY
    
    private final Map<String, Integer> debugFlags = new HashMap<>();
    
    @Override
    public void onModuleLoaded(ModuleLoadedParam startupParam) {
        log("Module initialized in Zygote");
        
        // Store the initial context if available
        if (mModuleContext != null) {
            initializeManagersAndSettings(mModuleContext);
        } else {
            log("Application context is null in initZygote. Managers and settings will be initialized later in handleLoadPackage.");
        }
        
        // Set default debug flags
        debugFlags.put("DEBUGGABLE", FLAG_DEBUGGABLE);
        debugFlags.put("ENABLE_PROFILING", FLAG_ENABLE_PROFILING);
        debugFlags.put("EXTERNAL_STORAGE_LEGACY", FLAG_EXTERNAL_STORAGE_LEGACY);
    }
    
    private synchronized void initializeManagersAndSettings(Context context) {
        if (mManagersInitialized) {
            return;
        }
        if (context == null) {
            log("Context is null, cannot initialize managers or settings.");
            return;
        }
        this.mModuleContext = context.getApplicationContext();

        try {
            mAnalyticsManager = AnalyticsManager.getInstance(this.mModuleContext);
            if (mAnalyticsManager != null) {
                logVerbose("AnalyticsManager initialized successfully.");
            } else {
                log("AnalyticsManager.getInstance() returned null.");
            }
        } catch (Exception e) {
            log("Failed to initialize AnalyticsManager: " + e.getMessage());
            LoggingHelper.logError(TAG, e);
        }

        try {
            mSettingsHelper = new SettingsHelper(this.mModuleContext, "com.wobbz.debugall");
            logVerbose("SettingsHelper initialized successfully.");
            loadSettings();
        } catch (Exception e) {
            log("Failed to initialize SettingsHelper: " + e.getMessage());
            LoggingHelper.logError(TAG, e);
        }

        if (mSettingsHelper != null) {
             mManagersInitialized = true;
        } else {
            log("SettingsHelper failed to initialize. Module may not function correctly.");
        }
    }
    
    /**
     * Load settings from the framework's settings manager
     */
    private void loadSettings() {
        if (mSettingsHelper != null) {
            // Load target apps
            String[] apps = mSettingsHelper.getStringArray("targetApps");
            if (apps != null) {
                targetApps.clear();
                for (String app : apps) {
                    targetApps.add(app);
                }
            }
            
            // Load verbose logging setting
            verboseLogging = mSettingsHelper.getBoolean("verboseLogging", false);
            
            // Load debug level
            debugLevel = mSettingsHelper.getString("debugLevel", "info");
            
            logVerbose("Loaded settings: " + targetApps.size() + " target apps, verbose=" + 
                      verboseLogging + ", level=" + debugLevel);
        }
    }
    
    @Override
    public void onPackageLoaded(PackageLoadedParam lpparam) {
        // Try to get context if needed
        if (!mManagersInitialized && mModuleContext != null) {
            initializeManagersAndSettings(mModuleContext);
        }

        String packageName = lpparam.getPackageName();
        
        // Skip if not in target apps (when target apps are configured)
        if (!targetApps.isEmpty() && !targetApps.contains(packageName)) {
            logVerbose("Skipping non-targeted package: " + packageName);
            return;
        }
        
        log("Handling package: " + packageName);
        
        try {
            // Track performance
            long trackingId = 0;
            if (mAnalyticsManager != null) {
                trackingId = mAnalyticsManager.trackHookStart(
                    "setDebugFlags", "com.wobbz.debugall", packageName);
            }
            
            // Get ApplicationInfo class to hook
            final Class<?> packageClass;
            try {
                packageClass = lpparam.getClassLoader().loadClass("android.content.pm.PackageParser$Package");
            } catch (ClassNotFoundException e) {
                log("Could not find Package class: " + e.getMessage());
                return;
            }
            
            // Find the method to hook
            Method toAppInfoMethod = null;
            for (Method m : packageClass.getDeclaredMethods()) {
                if (m.getName().equals("toAppInfoWithoutState") && 
                    m.getParameterTypes().length == 1 && 
                    m.getParameterTypes()[0] == int.class) {
                    toAppInfoMethod = m;
                    break;
                }
            }
            
            if (toAppInfoMethod == null) {
                log("Could not find toAppInfoWithoutState method");
                return;
            }
            
            // Hook the method using the libxposed API
            MethodUnhooker<Method> unhook = mXposedInterface.hook(toAppInfoMethod, DebugFlagHooker.class);
            
            unhooks.put(packageName, unhook);
            log("Successfully hooked ApplicationInfo generation for " + packageName);
            
            // Track performance end
            if (mAnalyticsManager != null) {
                mAnalyticsManager.trackHookEnd(trackingId, true);
            }
            
        } catch (Throwable t) {
            log("Error hooking ApplicationInfo: " + t.getMessage());
            if (mAnalyticsManager != null) {
                // Track failure
                mAnalyticsManager.trackHookEnd(0, false);
            }
            LoggingHelper.logError(TAG, t);
        }
    }
    
    /**
     * Hooker implementation for modifying ApplicationInfo flags
     */
    public static class DebugFlagHooker implements Hooker {
        
        /**
         * Called before the method executes
         */
        public static void before(BeforeHookCallback callback) {
            // No action needed before method execution
        }
        
        /**
         * Called after the method executes, adds debug flags to the ApplicationInfo
         */
        public static void after(AfterHookCallback callback) {
            // Modify the ApplicationInfo after the method returns
            Object result = callback.getResult();
            if (result instanceof ApplicationInfo) {
                ApplicationInfo appInfo = (ApplicationInfo) result;
                // Apply debug flags based on debug level
                String debugLevel = "info"; // Default value, should be set from module
                
                if ("info".equals(debugLevel)) {
                    appInfo.flags |= ApplicationInfo.FLAG_DEBUGGABLE;
                } else if ("debug".equals(debugLevel)) {
                    appInfo.flags |= ApplicationInfo.FLAG_DEBUGGABLE | 0x00000004; // FLAG_ENABLE_PROFILING
                } else if ("verbose".equals(debugLevel)) {
                    appInfo.flags |= ApplicationInfo.FLAG_DEBUGGABLE | 0x00000004 | 0x00400000; // FLAG_DEBUGGABLE | FLAG_ENABLE_PROFILING | FLAG_EXTERNAL_STORAGE_LEGACY
                }
                
                callback.setResult(appInfo);
                LoggingHelper.logInfo("DebugAll", "Set debug flags for: " + appInfo.packageName);
            }
        }
    }
    
    @Override
    public void onHotReload() {
        log("Hot reloading module");
        
        // Reload settings
        loadSettings();
        
        // Unhook all existing hooks and clear the map
        for (MethodUnhooker<?> unhooker : unhooks.values()) {
            try {
                unhooker.unhook();
            } catch (Throwable t) {
                log("Error unhooking: " + t.getMessage());
            }
        }
        unhooks.clear();
        
        log("Module hot-reloaded successfully");
    }
    
    /**
     * Initialize module with context and XposedInterface
     */
    public void initialize(Context context, XposedInterface xposedInterface) {
        this.mModuleContext = context;
        this.mXposedInterface = xposedInterface;
        
        // Initialize managers
        initializeManagersAndSettings(context);
        
        log("Module initialized with context and XposedInterface");
    }
    
    private void log(String message) {
        LoggingHelper.logInfo(TAG, message);
    }
    
    private void logVerbose(String message) {
        if (verboseLogging) {
            LoggingHelper.logDebug(TAG, message);
        }
    }
} 