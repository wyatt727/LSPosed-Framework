package com.wobbz.debugall;

import android.app.Application;
import android.app.AndroidAppHelper;
import android.content.Context;
import android.content.pm.ApplicationInfo;

import com.wobbz.framework.IHotReloadable;
import com.wobbz.framework.IModulePlugin;
import com.wobbz.framework.analytics.AnalyticsManager;
import com.wobbz.framework.annotations.HotReloadable;
import com.wobbz.framework.annotations.XposedPlugin;
import com.wobbz.framework.development.LoggingHelper;
import com.wobbz.framework.ui.models.SettingsHelper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

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
    private Map<String, XC_MethodHook.Unhook> unhooks = new HashMap<>();
    private Set<String> targetApps = new HashSet<>();
    private boolean verboseLogging = false;
    private String debugLevel = "info";
    private Context mModuleContext;
    private AnalyticsManager mAnalyticsManager;
    private SettingsHelper mSettingsHelper;
    private boolean mManagersInitialized = false;
    
    // Debug flag options
    private static final int FLAG_DEBUGGABLE = ApplicationInfo.FLAG_DEBUGGABLE;
    private static final int FLAG_ENABLE_PROFILING = ApplicationInfo.FLAG_ENABLE_PROFILING;
    private static final int FLAG_EXTERNAL_STORAGE_LEGACY = ApplicationInfo.FLAG_EXTERNAL_STORAGE_LEGACY;
    
    private final Map<String, Integer> debugFlags = new HashMap<>();
    
    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        log("Module initialized in Zygote");
        
        Application initialApplication = XposedBridge.sInitialApplication;
        if (initialApplication != null) {
            this.mModuleContext = initialApplication.getApplicationContext();
            initializeManagersAndSettings(this.mModuleContext);
        } else {
            log("XposedBridge.sInitialApplication is null in initZygote. Managers and settings will be initialized later in handleLoadPackage.");
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
            XposedBridge.log(e);
        }

        try {
            mSettingsHelper = new SettingsHelper(this.mModuleContext, "com.wobbz.debugall");
            logVerbose("SettingsHelper initialized successfully.");
            loadSettings();
        } catch (Exception e) {
            log("Failed to initialize SettingsHelper: " + e.getMessage());
            XposedBridge.log(e);
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
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        if (!mManagersInitialized) {
            logVerbose("Attempting to initialize managers/settings in handleLoadPackage for: " + lpparam.packageName);
            Context contextToUse = null;
            if (lpparam.appInfo != null) {
                try {
                    contextToUse = AndroidAppHelper.currentApplication().createPackageContext(lpparam.packageName, Context.CONTEXT_IGNORE_SECURITY);
                } catch (Exception e) {
                     log("Failed to create package context for " + lpparam.packageName + ". Falling back. Error: " + e.getMessage());
                }
            }
            if (contextToUse == null && XposedBridge.sInitialApplication != null) {
                 logVerbose("Using XposedBridge.sInitialApplication as fallback context for manager/settings initialization.");
                 contextToUse = XposedBridge.sInitialApplication.getApplicationContext();
            }

            if (contextToUse != null) {
                initializeManagersAndSettings(contextToUse);
            } else {
                log("Still no context available in handleLoadPackage for " + lpparam.packageName + " to initialize managers/settings. Module may not function as expected.");
            }
        }

        String packageName = lpparam.packageName;
        
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
            
            // Hook the method that generates ApplicationInfo
            XC_MethodHook.Unhook unhook = XposedHelpers.findAndHookMethod(
                android.content.pm.PackageParser.Package.class.getName(),
                lpparam.classLoader,
                "toAppInfoWithoutState",
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        ApplicationInfo appInfo = (ApplicationInfo) param.getResult();
                        if (appInfo != null) {
                            // Apply debug flags based on debug level
                            if ("info".equals(debugLevel)) {
                                appInfo.flags |= FLAG_DEBUGGABLE;
                            } else if ("debug".equals(debugLevel)) {
                                appInfo.flags |= FLAG_DEBUGGABLE | FLAG_ENABLE_PROFILING;
                            } else if ("verbose".equals(debugLevel)) {
                                appInfo.flags |= FLAG_DEBUGGABLE | FLAG_ENABLE_PROFILING | 
                                                FLAG_EXTERNAL_STORAGE_LEGACY;
                            }
                            
                            log("Set debug flags for: " + appInfo.packageName);
                        }
                    }
                }
            );
            
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
            XposedBridge.log(t);
        }
    }
    
    @Override
    public void onHotReload() {
        log("Hot-reloading module");
        
        // Clean up existing hooks
        for (XC_MethodHook.Unhook unhook : unhooks.values()) {
            if (unhook != null) {
                unhook.unhook();
            }
        }
        unhooks.clear();
        
        // Reload settings - ensure managers are re-initialized if necessary first
        mManagersInitialized = false;
        if (this.mModuleContext != null) {
            logVerbose("Re-initializing managers and settings with stored mModuleContext on hot-reload.");
            initializeManagersAndSettings(this.mModuleContext);
        } else if (XposedBridge.sInitialApplication != null) {
            logVerbose("mModuleContext was null during hot-reload, attempting with sInitialApplication for manager/settings re-initialization.");
            initializeManagersAndSettings(XposedBridge.sInitialApplication.getApplicationContext());
        } else {
            log("Cannot re-initialize managers/settings during hot-reload: No valid context stored or available. Settings may be stale.");
        }
        
        log("All hooks cleaned up, settings reloaded (if possible), ready for new implementation");
    }
    
    private void log(String message) {
        if (LoggingHelper.class != null) {
            LoggingHelper.info(TAG, message);
        } else {
            XposedBridge.log("[" + TAG + "] " + message);
        }
    }
    
    private void logVerbose(String message) {
        if (verboseLogging) {
            if (LoggingHelper.class != null) {
                LoggingHelper.debug(TAG, message);
            } else {
                XposedBridge.log("[" + TAG + " VERBOSE] " + message);
            }
        }
    }
} 