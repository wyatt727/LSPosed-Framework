package com.wobbz.deepintegrator;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;

import com.wobbz.deepintegrator.models.ComponentAccessLog;
import com.wobbz.deepintegrator.models.ComponentConfig;
import com.wobbz.deepintegrator.models.IntentFilterConfig;
import com.wobbz.framework.IHotReloadable;
import com.wobbz.framework.IModulePlugin;
import com.wobbz.framework.development.LoggingHelper;
import com.wobbz.framework.features.FeatureManager;
import com.wobbz.framework.ui.models.SettingsHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.libxposed.api.XC_MethodHook;
import com.github.libxposed.api.XposedBridge;
import com.github.libxposed.api.XposedHelpers;
import com.github.libxposed.api.callbacks.XC_LoadPackage;
import com.github.libxposed.api.callbacks.IXposedHookZygoteInit.StartupParam;

/**
 * DeepIntegratorModule exposes hidden app components and modifies intent filters
 * to enable deeper integration between apps.
 */
public class DeepIntegratorModule implements IModulePlugin, IHotReloadable {
    private static final String TAG = "DeepIntegratorModule";
    private static final String MODULE_ID = "com.wobbz.DeepIntegrator";
    
    // Service key for other modules to find this module
    public static final String SERVICE_KEY = "com.wobbz.DeepIntegrator.service";
    
    // Store active hooks for hot reload support
    private final List<XC_MethodHook.Unhook> mActiveHooks = new ArrayList<>();
    
    // Component configurations from settings
    private final Map<String, List<ComponentConfig>> mComponentConfigs = new HashMap<>();
    
    // Component access logs
    private final List<ComponentAccessLog> mAccessLogs = new ArrayList<>();
    private static final int MAX_LOG_ENTRIES = 100;
    
    private Context mContext;
    private SettingsHelper mSettings;
    private boolean mLogComponentAccess;
    private boolean mAutoExpose;
    private boolean mBypassPermissionChecks;
    private FeatureManager mFeatureManager;
    
    // SuperPatcher module instance
    private Object mSuperPatcherService;
    
    /**
     * Handle a package being loaded.
     *
     * @param context The context for this module.
     * @param lpparam The parameters for the loaded package.
     */
    @Override
    public void handleLoadPackage(Context context, XC_LoadPackage.LoadPackageParam lpparam) {
        mContext = context;
        mSettings = new SettingsHelper(context, MODULE_ID);
        mFeatureManager = FeatureManager.getInstance(context);
        
        // Register this instance as a service for other modules to use
        mFeatureManager.registerService(SERVICE_KEY, this);
        
        // Load settings
        loadSettings();
        
        // Get SuperPatcher service
        mSuperPatcherService = mFeatureManager.getService("com.wobbz.SuperPatcher.service");
        if (mSuperPatcherService == null) {
            LoggingHelper.error(TAG, "Failed to get SuperPatcher service, functionality will be limited");
        }
        
        // Only hook in the package manager package (usually system_server)
        if (lpparam.packageName.equals("android") || 
            lpparam.packageName.equals("com.android.server") ||
            lpparam.processName.equals("android") || 
            lpparam.processName.contains("system_server")) {
            
            hookPackageManagerService(lpparam);
        }
    }
    
    /**
     * Load settings from SettingsHelper.
     */
    private void loadSettings() {
        mLogComponentAccess = mSettings.getBoolean("logExposedComponents", true);
        mAutoExpose = mSettings.getBoolean("autoExpose", false);
        mBypassPermissionChecks = mSettings.getBoolean("bypassPermissionChecks", true);
        
        // Load component configurations
        loadComponentConfigurations();
        
        // Load access logs
        loadAccessLogs();
    }
    
    /**
     * Load component configurations from settings.
     */
    private void loadComponentConfigurations() {
        mComponentConfigs.clear();
        
        try {
            String configJson = mSettings.getString("componentOverrides", "[]");
            JSONArray configs = new JSONArray(configJson);
            
            for (int i = 0; i < configs.length(); i++) {
                JSONObject config = configs.getJSONObject(i);
                ComponentConfig componentConfig = ComponentConfig.fromJson(config);
                
                String packageName = componentConfig.getPackageName();
                List<ComponentConfig> packageConfigs = mComponentConfigs.get(packageName);
                if (packageConfigs == null) {
                    packageConfigs = new ArrayList<>();
                    mComponentConfigs.put(packageName, packageConfigs);
                }
                
                packageConfigs.add(componentConfig);
            }
            
            log("Loaded component configurations for " + mComponentConfigs.size() + " packages");
            
        } catch (JSONException e) {
            LoggingHelper.error(TAG, "Error loading component configurations", e);
        }
    }
    
    /**
     * Load access logs from settings.
     */
    private void loadAccessLogs() {
        mAccessLogs.clear();
        
        try {
            String logsJson = mSettings.getString("componentLogs", "[]");
            JSONArray logs = new JSONArray(logsJson);
            
            for (int i = 0; i < logs.length(); i++) {
                JSONObject log = logs.getJSONObject(i);
                ComponentAccessLog accessLog = ComponentAccessLog.fromJson(log);
                mAccessLogs.add(accessLog);
            }
            
            log("Loaded " + mAccessLogs.size() + " component access logs");
            
        } catch (JSONException e) {
            LoggingHelper.error(TAG, "Error loading access logs", e);
        }
    }
    
    /**
     * Save access logs to settings.
     */
    private void saveAccessLogs() {
        try {
            JSONArray logs = new JSONArray();
            for (ComponentAccessLog accessLog : mAccessLogs) {
                logs.put(accessLog.toJson());
            }
            
            mSettings.putString("componentLogs", logs.toString());
            
        } catch (JSONException e) {
            LoggingHelper.error(TAG, "Error saving access logs", e);
        }
    }
    
    /**
     * Add an access log entry.
     */
    private void addAccessLog(ComponentAccessLog log) {
        if (!mLogComponentAccess) {
            return;
        }
        
        mAccessLogs.add(0, log); // Add at the beginning for most recent first
        
        // Trim the log if it gets too large
        if (mAccessLogs.size() > MAX_LOG_ENTRIES) {
            mAccessLogs.subList(MAX_LOG_ENTRIES, mAccessLogs.size()).clear();
        }
        
        // Save logs to settings
        saveAccessLogs();
    }
    
    /**
     * Check if a package is targeted by this module.
     *
     * @param packageName The package name to check.
     * @return true if the package is targeted, false otherwise.
     */
    private boolean isTargetedPackage(String packageName) {
        String[] targetApps = mSettings.getStringArray("targetApps");
        if (targetApps == null || targetApps.length == 0) {
            return false;
        }
        
        for (String app : targetApps) {
            if (app.equals(packageName)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Hook into PackageManagerService methods to expose components.
     */
    private void hookPackageManagerService(XC_LoadPackage.LoadPackageParam lpparam) {
        // Hook getActivityInfo to expose activities
        hookMethod(lpparam, "android.app.IPackageManager$Stub$Proxy", "getActivityInfo", 
                  new Class<?>[] {ComponentName.class, int.class, String.class}, 
                  new ComponentModificationHook(ComponentConfig.TYPE_ACTIVITY));
        
        // Hook getServiceInfo to expose services
        hookMethod(lpparam, "android.app.IPackageManager$Stub$Proxy", "getServiceInfo", 
                  new Class<?>[] {ComponentName.class, int.class, String.class}, 
                  new ComponentModificationHook(ComponentConfig.TYPE_SERVICE));
        
        // Hook getProviderInfo to expose content providers
        hookMethod(lpparam, "android.app.IPackageManager$Stub$Proxy", "getProviderInfo", 
                  new Class<?>[] {ComponentName.class, int.class, String.class}, 
                  new ComponentModificationHook(ComponentConfig.TYPE_PROVIDER));
        
        // Hook getReceiverInfo to expose broadcast receivers
        hookMethod(lpparam, "android.app.IPackageManager$Stub$Proxy", "getReceiverInfo", 
                  new Class<?>[] {ComponentName.class, int.class, String.class}, 
                  new ComponentModificationHook(ComponentConfig.TYPE_RECEIVER));
        
        // Hook queryIntentActivities to modify intent resolution
        hookMethod(lpparam, "android.app.IPackageManager$Stub$Proxy", "queryIntentActivities", 
                  new Class<?>[] {Intent.class, String.class, int.class, String.class}, 
                  new IntentQueryHook(ComponentConfig.TYPE_ACTIVITY));
        
        // Hook queryIntentServices to modify intent resolution
        hookMethod(lpparam, "android.app.IPackageManager$Stub$Proxy", "queryIntentServices", 
                  new Class<?>[] {Intent.class, String.class, int.class, String.class}, 
                  new IntentQueryHook(ComponentConfig.TYPE_SERVICE));
        
        // Hook queryIntentProviders to modify intent resolution
        hookMethod(lpparam, "android.app.IPackageManager$Stub$Proxy", "queryIntentProviders", 
                  new Class<?>[] {Intent.class, String.class, int.class, String.class}, 
                  new IntentQueryHook(ComponentConfig.TYPE_PROVIDER));
        
        // Hook queryIntentReceivers to modify intent resolution
        hookMethod(lpparam, "android.app.IPackageManager$Stub$Proxy", "queryIntentReceivers", 
                  new Class<?>[] {Intent.class, String.class, int.class, String.class}, 
                  new IntentQueryHook(ComponentConfig.TYPE_RECEIVER));
        
        // Hook checkComponentPermission to bypass permission checks for components
        if (mBypassPermissionChecks) {
            hookMethod(lpparam, "com.android.server.pm.PackageManagerService", "checkComponentPermission", 
                      new Class<?>[] {String.class, int.class, int.class, int.class}, 
                      new PermissionBypassHook());
        }
    }
    
    /**
     * Hook a method using XposedHelpers.
     */
    private void hookMethod(XC_LoadPackage.LoadPackageParam lpparam, String className, 
                           String methodName, Class<?>[] parameterTypes, XC_MethodHook hook) {
        try {
            Class<?> clazz = XposedHelpers.findClass(className, lpparam.classLoader);
            
            XC_MethodHook.Unhook unhook;
            if (parameterTypes == null) {
                unhook = XposedBridge.hookAllMethods(clazz, methodName, hook);
            } else {
                Method method = XposedHelpers.findMethodExact(clazz, methodName, parameterTypes);
                unhook = XposedBridge.hookMethod(method, hook);
            }
            
            mActiveHooks.add(unhook);
            log("Hooked " + className + "." + methodName);
        } catch (Throwable t) {
            LoggingHelper.error(TAG, "Failed to hook " + className + "." + methodName, t);
        }
    }
    
    /**
     * Get the component configuration for a specific component.
     */
    private ComponentConfig getComponentConfig(String packageName, String componentName, String componentType) {
        List<ComponentConfig> packageConfigs = mComponentConfigs.get(packageName);
        if (packageConfigs == null) {
            return null;
        }
        
        for (ComponentConfig config : packageConfigs) {
            if (config.getComponentType().equals(componentType) && 
                config.getComponentName().equals(componentName)) {
                return config;
            }
        }
        
        return null;
    }
    
    /**
     * Modify a ComponentInfo to make it exported and accessible.
     */
    private void modifyComponentInfo(ComponentInfo info, String componentType) {
        if (!isTargetedPackage(info.packageName)) {
            return;
        }
        
        String componentName = info.name;
        ComponentConfig config = getComponentConfig(info.packageName, componentName, componentType);
        
        boolean shouldExport = mAutoExpose;
        boolean shouldBypassPermissions = mBypassPermissionChecks;
        
        if (config != null) {
            shouldExport = config.isExported();
            shouldBypassPermissions = config.isBypassPermissions();
        }
        
        if (shouldExport) {
            // Make the component exported
            info.exported = true;
            
            // Log the component modification
            ComponentAccessLog log = new ComponentAccessLog(
                componentType, 
                info.packageName + "/" + info.name,
                getCallingPackageName(),
                "EXPOSE"
            );
            log.setWasExported(true);
            addAccessLog(log);
        }
        
        if (shouldBypassPermissions) {
            // Remove permission requirements
            info.permission = null;
            
            // Log the permission bypass
            ComponentAccessLog log = new ComponentAccessLog(
                componentType, 
                info.packageName + "/" + info.name,
                getCallingPackageName(),
                "BYPASS_PERMISSION"
            );
            log.setWasPermissionBypassed(true);
            addAccessLog(log);
        }
    }
    
    /**
     * Get the calling package name.
     */
    private String getCallingPackageName() {
        int uid = Binder.getCallingUid();
        PackageManager pm = mContext.getPackageManager();
        String[] packages = pm.getPackagesForUid(uid);
        if (packages != null && packages.length > 0) {
            return packages[0];
        }
        return "unknown";
    }
    
    /**
     * Hook for modifying component info to expose components.
     */
    private class ComponentModificationHook extends XC_MethodHook {
        private final String mComponentType;
        
        public ComponentModificationHook(String componentType) {
            mComponentType = componentType;
        }
        
        @Override
        protected void afterHookedMethod(Param param) throws Throwable {
            // Check if the result is null (component not found)
            if (param.getResult() == null) {
                return;
            }
            
            ComponentInfo info = (ComponentInfo) param.getResult();
            modifyComponentInfo(info, mComponentType);
        }
    }
    
    /**
     * Hook for modifying intent query results to add custom intent filters.
     */
    private class IntentQueryHook extends XC_MethodHook {
        private final String mComponentType;
        
        public IntentQueryHook(String componentType) {
            mComponentType = componentType;
        }
        
        @Override
        protected void afterHookedMethod(Param param) throws Throwable {
            // Check if the result is null
            if (param.getResult() == null) {
                return;
            }
            
            // Get parameters
            Intent intent = (Intent) param.args[0];
            String callingPackage = (String) param.args[1];
            
            // Get the result list
            List<ResolveInfo> resolveInfoList = (List<ResolveInfo>) param.getResult();
            
            // Process each target app to add custom intent filters
            for (String packageName : mComponentConfigs.keySet()) {
                if (isTargetedPackage(packageName)) {
                    List<ComponentConfig> configs = mComponentConfigs.get(packageName);
                    if (configs == null) continue;
                    
                    for (ComponentConfig config : configs) {
                        if (!config.getComponentType().equals(mComponentType)) continue;
                        if (!config.isExported()) continue;
                        
                        // Check if this component has intent filters that match
                        for (IntentFilterConfig filterConfig : config.getIntentFilters()) {
                            if (matchesIntentFilter(intent, filterConfig)) {
                                // Create a new ResolveInfo and add it to the results
                                ResolveInfo newInfo = createResolveInfo(config);
                                if (newInfo != null) {
                                    resolveInfoList.add(newInfo);
                                    
                                    // Log the intent filter match
                                    ComponentAccessLog log = new ComponentAccessLog(
                                        mComponentType, 
                                        config.getPackageName() + "/" + config.getComponentName(),
                                        callingPackage != null ? callingPackage : getCallingPackageName(),
                                        "INTENT_FILTER_MATCH"
                                    );
                                    addAccessLog(log);
                                }
                            }
                        }
                    }
                }
            }
        }
        
        /**
         * Check if an intent matches a custom intent filter configuration.
         */
        private boolean matchesIntentFilter(Intent intent, IntentFilterConfig filter) {
            // Check actions
            if (!filter.getActions().isEmpty()) {
                String action = intent.getAction();
                if (action == null || !filter.getActions().contains(action)) {
                    return false;
                }
            }
            
            // Check categories
            if (!filter.getCategories().isEmpty()) {
                if (intent.getCategories() == null) return false;
                
                for (String category : filter.getCategories()) {
                    if (!intent.hasCategory(category)) {
                        return false;
                    }
                }
            }
            
            // Check data (scheme, host, path)
            if (!filter.getSchemes().isEmpty()) {
                String scheme = intent.getScheme();
                if (scheme == null || !filter.getSchemes().contains(scheme)) {
                    return false;
                }
                
                // Only check host/path if scheme matches
                if (!filter.getHosts().isEmpty()) {
                    String host = intent.getData() != null ? intent.getData().getHost() : null;
                    if (host == null || !filter.getHosts().contains(host)) {
                        return false;
                    }
                    
                    if (!filter.getPaths().isEmpty()) {
                        String path = intent.getData() != null ? intent.getData().getPath() : null;
                        boolean pathMatches = false;
                        
                        if (path != null) {
                            for (String filterPath : filter.getPaths()) {
                                if (filterPath.endsWith("*")) {
                                    // Path prefix match
                                    String prefix = filterPath.substring(0, filterPath.length() - 1);
                                    if (path.startsWith(prefix)) {
                                        pathMatches = true;
                                        break;
                                    }
                                } else if (path.equals(filterPath)) {
                                    // Exact path match
                                    pathMatches = true;
                                    break;
                                }
                            }
                        }
                        
                        if (!pathMatches) {
                            return false;
                        }
                    }
                }
            }
            
            // Check MIME types
            if (!filter.getMimeTypes().isEmpty()) {
                String type = intent.getType();
                if (type == null) {
                    // Try to get type from data
                    if (intent.getData() != null) {
                        type = mContext.getContentResolver().getType(intent.getData());
                    }
                }
                
                if (type == null) {
                    return false;
                }
                
                boolean mimeMatches = false;
                for (String mimeType : filter.getMimeTypes()) {
                    if (mimeType.endsWith("/*")) {
                        // Type prefix match (e.g., "image/*")
                        String prefix = mimeType.substring(0, mimeType.indexOf('/'));
                        String actualPrefix = type.substring(0, type.indexOf('/'));
                        if (prefix.equals(actualPrefix)) {
                            mimeMatches = true;
                            break;
                        }
                    } else if (type.equals(mimeType)) {
                        // Exact type match
                        mimeMatches = true;
                        break;
                    }
                }
                
                if (!mimeMatches) {
                    return false;
                }
            }
            
            return true;
        }
        
        /**
         * Create a ResolveInfo for a component.
         */
        private ResolveInfo createResolveInfo(ComponentConfig config) {
            try {
                ResolveInfo info = new ResolveInfo();
                ComponentName componentName = new ComponentName(
                    config.getPackageName(), config.getComponentName());
                
                // Set different fields based on component type
                if (mComponentType.equals(ComponentConfig.TYPE_ACTIVITY)) {
                    ActivityInfo activityInfo = new ActivityInfo();
                    activityInfo.packageName = config.getPackageName();
                    activityInfo.name = config.getComponentName();
                    activityInfo.exported = true;
                    if (!config.isBypassPermissions()) {
                        activityInfo.permission = null;
                    }
                    info.activityInfo = activityInfo;
                } else if (mComponentType.equals(ComponentConfig.TYPE_SERVICE)) {
                    ServiceInfo serviceInfo = new ServiceInfo();
                    serviceInfo.packageName = config.getPackageName();
                    serviceInfo.name = config.getComponentName();
                    serviceInfo.exported = true;
                    if (!config.isBypassPermissions()) {
                        serviceInfo.permission = null;
                    }
                    info.serviceInfo = serviceInfo;
                } else if (mComponentType.equals(ComponentConfig.TYPE_PROVIDER)) {
                    ProviderInfo providerInfo = new ProviderInfo();
                    providerInfo.packageName = config.getPackageName();
                    providerInfo.name = config.getComponentName();
                    providerInfo.exported = true;
                    if (!config.isBypassPermissions()) {
                        providerInfo.readPermission = null;
                        providerInfo.writePermission = null;
                    }
                    info.providerInfo = providerInfo;
                } else if (mComponentType.equals(ComponentConfig.TYPE_RECEIVER)) {
                    ActivityInfo receiverInfo = new ActivityInfo();
                    receiverInfo.packageName = config.getPackageName();
                    receiverInfo.name = config.getComponentName();
                    receiverInfo.exported = true;
                    if (!config.isBypassPermissions()) {
                        receiverInfo.permission = null;
                    }
                    info.activityInfo = receiverInfo;
                }
                
                // Set the priority from the first intent filter (if any)
                if (!config.getIntentFilters().isEmpty()) {
                    IntentFilterConfig firstFilter = config.getIntentFilters().get(0);
                    info.priority = firstFilter.getPriority();
                }
                
                return info;
            } catch (Exception e) {
                LoggingHelper.error(TAG, "Error creating ResolveInfo for " + 
                                  config.getPackageName() + "/" + config.getComponentName(), e);
                return null;
            }
        }
    }
    
    /**
     * Hook for bypassing permission checks for components.
     */
    private class PermissionBypassHook extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(Param param) throws Throwable {
            // Get parameters
            String permission = (String) param.args[0];
            int uid = (int) param.args[1];
            
            // Get the package name for the UID
            PackageManager pm = mContext.getPackageManager();
            String[] packages = pm.getPackagesForUid(uid);
            if (packages == null || packages.length == 0) {
                return;
            }
            
            String callingPackage = packages[0];
            
            // Check if we should bypass permission checks for this package
            if (isTargetedPackage(callingPackage) && mBypassPermissionChecks) {
                // Return PERMISSION_GRANTED
                param.setResult(PackageManager.PERMISSION_GRANTED);
                
                // Log the permission bypass
                ComponentAccessLog log = new ComponentAccessLog(
                    "permission", 
                    permission,
                    callingPackage,
                    "BYPASS_PERMISSION_CHECK"
                );
                log.setWasPermissionBypassed(true);
                addAccessLog(log);
            }
        }
    }
    
    /**
     * Get the component access logs.
     */
    public List<ComponentAccessLog> getAccessLogs() {
        return new ArrayList<>(mAccessLogs);
    }
    
    /**
     * Clear the component access logs.
     */
    public void clearAccessLogs() {
        mAccessLogs.clear();
        saveAccessLogs();
    }
    
    /**
     * Add a component configuration.
     */
    public void addComponentConfig(ComponentConfig config) {
        String packageName = config.getPackageName();
        List<ComponentConfig> packageConfigs = mComponentConfigs.get(packageName);
        if (packageConfigs == null) {
            packageConfigs = new ArrayList<>();
            mComponentConfigs.put(packageName, packageConfigs);
        }
        
        // Check if this component already has a config and replace it
        for (int i = 0; i < packageConfigs.size(); i++) {
            ComponentConfig existing = packageConfigs.get(i);
            if (existing.getComponentName().equals(config.getComponentName()) &&
                existing.getComponentType().equals(config.getComponentType())) {
                packageConfigs.set(i, config);
                saveComponentConfigurations();
                return;
            }
        }
        
        // Add new config
        packageConfigs.add(config);
        saveComponentConfigurations();
    }
    
    /**
     * Remove a component configuration.
     */
    public void removeComponentConfig(String packageName, String componentName, String componentType) {
        List<ComponentConfig> packageConfigs = mComponentConfigs.get(packageName);
        if (packageConfigs == null) {
            return;
        }
        
        for (int i = 0; i < packageConfigs.size(); i++) {
            ComponentConfig config = packageConfigs.get(i);
            if (config.getComponentName().equals(componentName) &&
                config.getComponentType().equals(componentType)) {
                packageConfigs.remove(i);
                if (packageConfigs.isEmpty()) {
                    mComponentConfigs.remove(packageName);
                }
                saveComponentConfigurations();
                return;
            }
        }
    }
    
    /**
     * Save component configurations to settings.
     */
    private void saveComponentConfigurations() {
        try {
            JSONArray configs = new JSONArray();
            for (List<ComponentConfig> packageConfigs : mComponentConfigs.values()) {
                for (ComponentConfig config : packageConfigs) {
                    configs.put(config.toJson());
                }
            }
            
            mSettings.putString("componentOverrides", configs.toString());
            
        } catch (JSONException e) {
            LoggingHelper.error(TAG, "Error saving component configurations", e);
        }
    }
    
    /**
     * Clean up when the module is unloaded or reloaded.
     */
    @Override
    public void onHotReload() {
        // Unhook all methods
        for (XC_MethodHook.Unhook unhook : mActiveHooks) {
            unhook.unhook();
        }
        mActiveHooks.clear();
        
        // Save any unsaved state
        saveAccessLogs();
        saveComponentConfigurations();
        
        log("Module unhooked for hot reload");
    }
    
    /**
     * Log a message if verbose logging is enabled.
     */
    private void log(String message) {
        LoggingHelper.debug(TAG, message);
    }
} 