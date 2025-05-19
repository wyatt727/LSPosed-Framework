package com.wobbz.debugall;

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
    id = "com.wobbz.DebugAll",
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
    private Context mContext;
    private AnalyticsManager mAnalyticsManager;
    private SettingsHelper mSettingsHelper;
    
    // Debug flag options
    private static final int FLAG_DEBUGGABLE = ApplicationInfo.FLAG_DEBUGGABLE;
    private static final int FLAG_ENABLE_PROFILING = ApplicationInfo.FLAG_ENABLE_PROFILING;
    private static final int FLAG_EXTERNAL_STORAGE_LEGACY = ApplicationInfo.FLAG_EXTERNAL_STORAGE_LEGACY;
    
    private final Map<String, Integer> debugFlags = new HashMap<>();
    
    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        log("Module initialized in Zygote");
        mContext = (Context) XposedHelpers.getObjectField(startupParam, "modulePath");
        
        // Initialize analytics
        if (mContext != null) {
            mAnalyticsManager = AnalyticsManager.getInstance(mContext);
            mSettingsHelper = new SettingsHelper(mContext, "com.wobbz.DebugAll");
            loadSettings();
        }
        
        // Set default debug flags
        debugFlags.put("DEBUGGABLE", FLAG_DEBUGGABLE);
        debugFlags.put("ENABLE_PROFILING", FLAG_ENABLE_PROFILING);
        debugFlags.put("EXTERNAL_STORAGE_LEGACY", FLAG_EXTERNAL_STORAGE_LEGACY);
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
                    "setDebugFlags", "com.wobbz.DebugAll", packageName);
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
        
        // Reload settings
        loadSettings();
        
        log("All hooks cleaned up, ready for new implementation");
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