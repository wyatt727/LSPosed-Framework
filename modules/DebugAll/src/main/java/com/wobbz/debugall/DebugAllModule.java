package com.wobbz.debugall;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;

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
import io.github.libxposed.api.XposedModuleInterface.SystemServerLoadedParam;

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
    
    // Debug flag constants with proper names
    private static final int FLAG_DEBUGGABLE = ApplicationInfo.FLAG_DEBUGGABLE;
    private static final int FLAG_PROFILEABLE_BY_SHELL = ApplicationInfo.FLAG_PROFILEABLE_BY_SHELL; // Proper constant name for the flag
    private static final int FLAG_LEGACY_STORAGE = ApplicationInfo.FLAG_LEGACY_STORAGE; // Proper constant name for the flag
    
    private final Map<String, Integer> debugFlags = new HashMap<>();
    
    private static DebugAllModule instance;
    
    public static DebugAllModule getInstance() {
        return instance;
    }
    
    @Override
    public void onModuleLoaded(ModuleLoadedParam startupParam) {
        log("Module initialized in Zygote");
        instance = this;
        
        // Store the initial context if available
        if (mModuleContext != null) {
            initializeManagersAndSettings(mModuleContext);
        } else {
            log("Application context is null in initZygote. Managers and settings will be initialized later in handleLoadPackage.");
        }
        
        // Set default debug flags
        debugFlags.put("DEBUGGABLE", FLAG_DEBUGGABLE);
        debugFlags.put("PROFILEABLE", FLAG_PROFILEABLE_BY_SHELL);
        debugFlags.put("LEGACY_STORAGE", FLAG_LEGACY_STORAGE);
    }
    
    /**
     * Called when the system server is loaded.
     * This is where we should hook system-level ApplicationInfo generation.
     */
    public void onSystemServerLoaded(SystemServerLoadedParam param) {
        log("System server loaded - hooking ApplicationInfo generation");
        
        // Get XposedInterface from the parameter for system server context
        XposedInterface xposedInterface = param.getXposed();
        
        try {
            // Find the appropriate class based on Android version for modern devices
            Class<?> packageImplClass = null;
            Method targetMethod = null;
            
            if (Build.VERSION.SDK_INT >= 30) { // Android 11+
                try {
                    // Try to find modern Android 11+ class (PackageImpl)
                    packageImplClass = param.getClassLoader().loadClass("android.content.pm.parsing.pkg.PackageImpl");
                    // Find method that generates ApplicationInfo
                    for (Method m : packageImplClass.getDeclaredMethods()) {
                        // Methods like toAppInfoWithoutState or similar
                        if (m.getName().contains("AppInfo") && m.getReturnType() == ApplicationInfo.class) {
                            targetMethod = m;
                            log("Found modern ApplicationInfo generation method: " + m.getName());
                            break;
                        }
                    }
                } catch (ClassNotFoundException e) {
                    log("Could not find PackageImpl class: " + e.getMessage());
                }
            }
            
            // Fallback to Android 10 version or check ComputerEngine
            if (targetMethod == null && Build.VERSION.SDK_INT >= 29) { // Android 10
                try {
                    // Try ComputerEngine for newer Android versions
                    Class<?> computerEngineClass = param.getClassLoader().loadClass("com.android.server.pm.ComputerEngine");
                    for (Method m : computerEngineClass.getDeclaredMethods()) {
                        if (m.getName().contains("generateApplicationInfo") || 
                            m.getName().contains("getApplicationInfo")) {
                            targetMethod = m;
                            packageImplClass = computerEngineClass;
                            log("Found ComputerEngine ApplicationInfo generation method: " + m.getName());
                            break;
                        }
                    }
                } catch (ClassNotFoundException e) {
                    log("Could not find ComputerEngine class: " + e.getMessage());
                }
            }
            
            // Last resort: try legacy PackageParser method
            if (targetMethod == null) {
                try {
                    packageImplClass = param.getClassLoader().loadClass("android.content.pm.PackageParser$Package");
                    for (Method m : packageImplClass.getDeclaredMethods()) {
                        if (m.getName().equals("toAppInfoWithoutState") && 
                            m.getParameterTypes().length == 1 && 
                            m.getParameterTypes()[0] == int.class) {
                            targetMethod = m;
                            log("Found legacy ApplicationInfo generation method: toAppInfoWithoutState");
                            break;
                        }
                    }
                } catch (ClassNotFoundException e) {
                    log("Could not find legacy Package class: " + e.getMessage());
                }
            }
            
            if (targetMethod == null) {
                log("ERROR: Could not find any ApplicationInfo generation method");
                return;
            }
            
            // Hook the method with our non-static hooker that has access to module instance
            MethodUnhooker<?> unhook = xposedInterface.hook(targetMethod, new DebugFlagHooker(this));
            if (unhook != null) {
                unhooks.put("android", unhook);
                log("Successfully hooked ApplicationInfo generation in system server");
            }
        } catch (Throwable t) {
            log("Error hooking ApplicationInfo in system server: " + t.getMessage());
            LoggingHelper.logError(TAG, t);
        }
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

        // Store XposedInterface for later use
        mXposedInterface = lpparam.getXposed();
        
        // Since we now hook system server directly, per-package hooking is less necessary
        // But we can still handle initialization here if needed
        
        // We'll only hook the system package (android) if we're in onPackageLoaded instead of onSystemServerLoaded
        if (lpparam.getPackageName().equals("android") && unhooks.get("android") == null) {
            log("Detected system server package in onPackageLoaded - initializing hook");
            onSystemServerLoaded(null); // Call our system hook from here if needed
        }
    }
    
    /**
     * Hooker implementation for modifying ApplicationInfo flags
     * Now non-static to access module instance properties
     */
    public class DebugFlagHooker implements Hooker {
        
        private final DebugAllModule module;
        
        public DebugFlagHooker(DebugAllModule module) {
            this.module = module;
        }
        
        /**
         * Called before the method executes
         */
        public void before(BeforeHookCallback callback) {
            // No action needed before method execution
        }
        
        /**
         * Called after the method executes, adds debug flags to the ApplicationInfo
         */
        public void after(AfterHookCallback callback) {
            // Modify the ApplicationInfo after the method returns
            Object result = callback.getResult();
            if (result instanceof ApplicationInfo) {
                ApplicationInfo appInfo = (ApplicationInfo) result;
                
                // Only apply to targeted apps
                if (!module.targetApps.isEmpty() && !module.targetApps.contains(appInfo.packageName)) {
                    return;
                }
                
                // Apply debug flags based on debug level from module
                String level = module.debugLevel;
                
                if ("info".equals(level)) {
                    appInfo.flags |= ApplicationInfo.FLAG_DEBUGGABLE;
                    module.logVerbose("Setting FLAG_DEBUGGABLE for " + appInfo.packageName);
                } else if ("debug".equals(level)) {
                    appInfo.flags |= ApplicationInfo.FLAG_DEBUGGABLE | ApplicationInfo.FLAG_PROFILEABLE_BY_SHELL;
                    module.logVerbose("Setting FLAG_DEBUGGABLE and FLAG_PROFILEABLE_BY_SHELL for " + appInfo.packageName);
                } else if ("verbose".equals(level)) {
                    appInfo.flags |= ApplicationInfo.FLAG_DEBUGGABLE | 
                                    ApplicationInfo.FLAG_PROFILEABLE_BY_SHELL | 
                                    ApplicationInfo.FLAG_LEGACY_STORAGE;
                    module.logVerbose("Setting all debug flags for " + appInfo.packageName);
                }
                
                callback.setResult(appInfo);
                LoggingHelper.logInfo("DebugAll", "Set debug flags for: " + appInfo.packageName);
            }
        }
    }
    
    @Override
    public void initialize(Context context, XposedInterface xposedInterface) {
        this.mModuleContext = context;
        this.mXposedInterface = xposedInterface;
        
        initializeManagersAndSettings(context);
        
        instance = this;
    }
    
    @Override
    public void onHotReload() {
        log("Hot reloading module");
        
        // Unhook all methods
        for (MethodUnhooker<?> unhooker : unhooks.values()) {
            unhooker.unhook();
        }
        unhooks.clear();
        
        // Re-initialize instance
        instance = this;
        
        // Reload settings
        loadSettings();
        
        // Re-apply system server hook
        if (mXposedInterface != null) {
            onSystemServerLoaded(null);
        }
        
        log("Module hot-reloaded successfully");
    }
    
    private void log(String message) {
        LoggingHelper.info(TAG, message);
    }
    
    private void logVerbose(String message) {
        if (verboseLogging) {
            LoggingHelper.debug(TAG, message);
        }
    }
} 