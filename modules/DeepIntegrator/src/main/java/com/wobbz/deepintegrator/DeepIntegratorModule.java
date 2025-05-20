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
import com.wobbz.framework.annotations.HotReloadable;
import com.wobbz.framework.annotations.XposedPlugin;
import com.wobbz.framework.development.LoggingHelper;
import com.wobbz.framework.features.FeatureManager;
import com.wobbz.framework.ui.models.SettingsHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedInterface.Hooker;
import io.github.libxposed.api.XposedInterface.BeforeHookCallback;
import io.github.libxposed.api.XposedInterface.AfterHookCallback;
import io.github.libxposed.api.XposedInterface.MethodUnhooker;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.SystemServerLoadedParam;

/**
 * DeepIntegratorModule exposes hidden app components and modifies intent filters
 * to enable deeper integration between apps.
 */
@XposedPlugin(
    id = "com.wobbz.DeepIntegrator",
    name = "Deep Integrator",
    description = "Exposes hidden app components and modifies intent filters for deeper app integration",
    version = "1.0.0",
    scope = {"android"} // For system server hooks
)
@HotReloadable
public class DeepIntegratorModule implements IModulePlugin, IHotReloadable {
    private static final String TAG = "DeepIntegratorModule";
    private static final String MODULE_ID = "com.wobbz.DeepIntegrator";
    
    // Service key for other modules to find this module
    public static final String SERVICE_KEY = "com.wobbz.DeepIntegrator.service";
    
    // Store active hooks for hot reload support
    private final List<MethodUnhooker<?>> mActiveHooks = new ArrayList<>();
    
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
    private XposedInterface mXposedInterface;
    
    // SuperPatcher module instance
    private Object mSuperPatcherService;
    
    // Store the last lpparam for re-hooking during hot reload
    private PackageLoadedParam mLastLpparam;
    
    /**
     * Called when the module is loaded in Zygote
     */
    @Override
    public void onModuleLoaded(ModuleLoadedParam startupParam) {
        log("Module loaded in " + startupParam.getProcessName());
    }
    
    /**
     * Initialize the module with context and XposedInterface
     */
    public void initialize(Context context, XposedInterface xposedInterface) {
        this.mContext = context;
        this.mXposedInterface = xposedInterface;
        
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
        
        log("Module initialized with context and XposedInterface");
    }
    
    /**
     * Handle a package being loaded.
     */
    @Override
    public void onPackageLoaded(PackageLoadedParam lpparam) {
        // Only hook in the package manager package (usually system_server)
        if (lpparam.getPackageName().equals("android")) {
            mLastLpparam = lpparam; // Store for hot reload
            hookPackageManagerService(lpparam);
        }
    }
    
    /**
     * Handle system server being loaded.
     */
    public void onSystemServerLoaded(SystemServerLoadedParam param) {
        log("System server loaded");
        // Can also initialize hooks here with param.getXposed()
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
        
        // Add to beginning of list
        mAccessLogs.add(0, log);
        
        // Trim list if too large
        while (mAccessLogs.size() > MAX_LOG_ENTRIES) {
            mAccessLogs.remove(mAccessLogs.size() - 1);
        }
        
        // Save logs
        saveAccessLogs();
    }
    
    /**
     * Check if a package is targeted for component modification.
     */
    private boolean isTargetedPackage(String packageName) {
        if (packageName == null) {
            return false;
        }
        
        // If auto-expose is enabled, target all packages
        if (mAutoExpose) {
            return true;
        }
        
        // Check if there are specific configurations for this package
        return mComponentConfigs.containsKey(packageName);
    }
    
    /**
     * Hook package manager methods to handle component info and intent resolution.
     */
    private void hookPackageManagerService(PackageLoadedParam lpparam) {
        if (mXposedInterface == null) {
            log("XposedInterface is null, cannot hook methods");
            return;
        }
        
        try {
            log("Hooking PackageManagerService");
            
            // Hook activity info getter
            hookMethod(lpparam, "com.android.server.pm.PackageManagerService", 
                      "generateActivityInfo", 
                      new ComponentModificationHook("activity"));
            
            // Hook service info getter
            hookMethod(lpparam, "com.android.server.pm.PackageManagerService", 
                      "generateServiceInfo", 
                      new ComponentModificationHook("service"));
            
            // Hook provider info getter
            hookMethod(lpparam, "com.android.server.pm.PackageManagerService", 
                      "generateProviderInfo", 
                      new ComponentModificationHook("provider"));
            
            // Hook intent resolution
            hookMethod(lpparam, "com.android.server.pm.PackageManagerService", 
                      "queryIntentActivitiesInternal", 
                      new IntentQueryHook("activity"));
            
            hookMethod(lpparam, "com.android.server.pm.PackageManagerService", 
                      "queryIntentServicesInternal", 
                      new IntentQueryHook("service"));
            
            hookMethod(lpparam, "com.android.server.pm.PackageManagerService", 
                      "queryIntentProvidersInternal", 
                      new IntentQueryHook("provider"));
            
            // For Android 13+, also hook ComponentResolver class
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                hookMethod(lpparam, "com.android.server.pm.ComponentResolver", 
                          "queryActivities", 
                          new IntentQueryHook("activity"));
                
                hookMethod(lpparam, "com.android.server.pm.ComponentResolver", 
                          "queryServices", 
                          new IntentQueryHook("service"));
                
                hookMethod(lpparam, "com.android.server.pm.ComponentResolver", 
                          "queryProviders", 
                          new IntentQueryHook("provider"));
            }
            
            // Hook permission checking if bypass is enabled
            if (mBypassPermissionChecks) {
                hookMethod(lpparam, "com.android.server.pm.permission.PermissionManagerService", 
                          "checkPermission", 
                          new PermissionBypassHook());
            }
            
            log("Successfully hooked PackageManagerService methods");
        } catch (Throwable t) {
            LoggingHelper.error(TAG, "Error hooking PackageManagerService", t);
        }
    }
    
    /**
     * Helper to hook methods with proper error handling
     */
    private void hookMethod(PackageLoadedParam lpparam, String className, 
                           String methodName, Hooker hook) {
        try {
            // Get class
            Class<?> clazz = lpparam.getClassLoader().loadClass(className);
            
            // Find method
            Method method = findMethodInClass(clazz, methodName);
            if (method == null) {
                log("Could not find method " + methodName + " in class " + className);
                return;
            }
            
            // Hook method
            MethodUnhooker<?> unhooker = mXposedInterface.hook(method, hook);
            if (unhooker != null) {
                mActiveHooks.add(unhooker);
                log("Hooked " + className + "." + methodName + "() successfully");
            } else {
                log("Failed to hook " + className + "." + methodName + "()");
            }
        } catch (Throwable t) {
            LoggingHelper.error(TAG, "Error hooking " + className + "." + methodName, t);
        }
    }
    
    /**
     * Find a method in a class by name and parameter types.
     * 
     * @param clazz The class to search in
     * @param methodName The name of the method to find
     * @param parameterTypes Optional parameter types to match
     * @return The found method, or null if not found
     */
    private Method findMethodInClass(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        if (clazz == null || methodName == null) {
            return null;
        }
        
        // If parameter types are provided, use exact matching
        if (parameterTypes != null && parameterTypes.length > 0) {
            try {
                return clazz.getDeclaredMethod(methodName, parameterTypes);
            } catch (NoSuchMethodException e) {
                // Fall back to searching all methods
            }
        }
        
        // Search all methods
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                // If we didn't specify parameter types, return the first match
                if (parameterTypes == null || parameterTypes.length == 0) {
                    method.setAccessible(true);
                    return method;
                }
                
                // Otherwise, check parameter types
                Class<?>[] methodParams = method.getParameterTypes();
                if (methodParams.length == parameterTypes.length) {
                    boolean match = true;
                    for (int i = 0; i < methodParams.length; i++) {
                        if (!methodParams[i].isAssignableFrom(parameterTypes[i])) {
                            match = false;
                            break;
                        }
                    }
                    
                    if (match) {
                        method.setAccessible(true);
                        return method;
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Get a component configuration.
     */
    private ComponentConfig getComponentConfig(String packageName, String componentName, String componentType) {
        List<ComponentConfig> packageConfigs = mComponentConfigs.get(packageName);
        if (packageConfigs == null) {
            return null;
        }
        
        for (ComponentConfig config : packageConfigs) {
            if (config.getComponentName().equals(componentName) &&
                config.getComponentType().equals(componentType)) {
                return config;
            }
        }
        
        return null;
    }
    
    /**
     * Modify component info based on configurations.
     */
    private void modifyComponentInfo(ComponentInfo info, String componentType) {
        if (info == null || !isTargetedPackage(info.packageName)) {
            return;
        }
        
        String componentName = info.name;
        ComponentConfig config = getComponentConfig(info.packageName, componentName, componentType);
        
        // Use default overrides for auto-expose if no specific config
        boolean shouldExpose = (config != null) ? config.isOverrideExported() : mAutoExpose;
        boolean overrideExported = (config != null) ? config.isOverrideExported() : mAutoExpose;
        boolean exported = (config != null) ? config.isExported() : true;
        boolean nullifyPermission = (config != null) ? config.isNullifyPermission() : mBypassPermissionChecks;
        
        if (shouldExpose) {
            String callingPackage = getCallingPackageName();
            
            // Log component access
            if (mLogComponentAccess && callingPackage != null) {
                ComponentAccessLog log = new ComponentAccessLog(
                    info.packageName,
                    componentName,
                    componentType,
                    callingPackage,
                    System.currentTimeMillis()
                );
                addAccessLog(log);
            }
            
            // Override exported flag if needed
            if (overrideExported) {
                info.exported = exported;
            }
            
            // Remove permission requirement if needed
            if (nullifyPermission) {
                // Handle different ComponentInfo subclasses appropriately
                if (info instanceof ActivityInfo) {
                    ((ActivityInfo)info).permission = null;
                } else if (info instanceof ServiceInfo) {
                    ((ServiceInfo)info).permission = null;
                } else if (info instanceof ProviderInfo) {
                    ProviderInfo providerInfo = (ProviderInfo) info;
                    providerInfo.readPermission = null;
                    providerInfo.writePermission = null;
                }
            }
        }
    }
    
    /**
     * Get the package name of the calling app.
     */
    private String getCallingPackageName() {
        int uid = Binder.getCallingUid();
        if (mContext == null) {
            return null;
        }
        
        String[] packages = mContext.getPackageManager().getPackagesForUid(uid);
        return (packages != null && packages.length > 0) ? packages[0] : null;
    }
    
    /**
     * Hooker to modify component info.
     * Now a non-static inner class to access module instance.
     */
    public class ComponentModificationHook implements Hooker {
        private final String componentType;
        
        public ComponentModificationHook(String componentType) {
            this.componentType = componentType;
        }
        
        public void before(BeforeHookCallback callback) {
            // No action needed before method execution
        }
        
        public void after(AfterHookCallback callback) {
            Object result = callback.getResult();
            if (result instanceof ComponentInfo) {
                ComponentInfo info = (ComponentInfo) result;
                
                // Use the module instance's modifyComponentInfo method
                modifyComponentInfo(info, componentType);
                
                callback.setResult(info);
            }
        }
    }
    
    /**
     * Hooker to modify intent query results.
     * Now implements intent filter injection and is a non-static inner class.
     */
    public class IntentQueryHook implements Hooker {
        private final String componentType;
        
        public IntentQueryHook(String componentType) {
            this.componentType = componentType;
        }
        
        public void before(BeforeHookCallback callback) {
            // No action needed before method execution
        }
        
        public void after(AfterHookCallback callback) {
            // Get the result list (which might be a List<ResolveInfo>)
            Object result = callback.getResult();
            if (!(result instanceof List<?>)) {
                return;
            }
            
            // Get the intent being queried (first parameter is often the Intent)
            Object[] args = callback.getArgs();
            Intent intent = null;
            for (Object arg : args) {
                if (arg instanceof Intent) {
                    intent = (Intent) arg;
                    break;
                }
            }
            
            if (intent == null) {
                return;
            }
            
            // Intent filter injection would be implemented here
            // This would add or modify ResolveInfo objects in the result list
            // based on IntentFilterConfig in ComponentConfig
            
            // Example pseudocode:
            // for (ComponentConfig config : getAllComponentConfigs())
            //    if (config.getComponentType().equals(componentType))
            //        for (IntentFilterConfig filter : config.getIntentFilters())
            //            if (filter matches intent)
            //                Create and add ResolveInfo to result
            
            // This is just a placeholder for the implementation
            log("IntentQueryHook called for " + componentType + 
                " with intent action: " + intent.getAction());
        }
    }
    
    /**
     * Hooker to bypass permission checks.
     * Now a non-static inner class to access module instance.
     */
    public class PermissionBypassHook implements Hooker {
        public void before(BeforeHookCallback callback) {
            // Get parameters
            Object[] args = callback.getArgs();
            if (args.length >= 2 && args[0] instanceof String && args[1] instanceof String) {
                String permission = (String) args[0];
                String packageName = (String) args[1];
                
                // Check if this package has permission bypass configured
                if (isTargetedPackage(packageName) && mBypassPermissionChecks) {
                    // Bypass certain permissions
                    if (permission != null && (
                        permission.contains("INTERACT_ACROSS_USERS") ||
                        permission.contains("MANAGE_ACTIVITY") ||
                        permission.contains("INJECT_EVENTS") ||
                        permission.contains("WRITE_SECURE_SETTINGS"))) {
                        
                        callback.returnAndSkip(PackageManager.PERMISSION_GRANTED);
                    }
                }
            }
        }
        
        public void after(AfterHookCallback callback) {
            // No action needed after method execution
        }
    }
    
    /**
     * Get the list of component access logs.
     */
    public List<ComponentAccessLog> getAccessLogs() {
        return new ArrayList<>(mAccessLogs);
    }
    
    /**
     * Clear component access logs.
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
        
        // Remove existing config for this component if it exists
        for (int i = 0; i < packageConfigs.size(); i++) {
            ComponentConfig existingConfig = packageConfigs.get(i);
            if (existingConfig.getComponentName().equals(config.getComponentName()) &&
                existingConfig.getComponentType().equals(config.getComponentType())) {
                packageConfigs.remove(i);
                break;
            }
        }
        
        // Add new config
        packageConfigs.add(config);
        
        // Save configurations
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
                break;
            }
        }
        
        // Remove package entry if it has no more configs
        if (packageConfigs.isEmpty()) {
            mComponentConfigs.remove(packageName);
        }
        
        // Save configurations
        saveComponentConfigurations();
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
     * Handle hot reload.
     */
    @Override
    public void onHotReload() {
        log("Hot reloading module");
        
        // Unhook all existing hooks
        for (MethodUnhooker<?> unhooker : mActiveHooks) {
            try {
                unhooker.unhook();
            } catch (Throwable t) {
                log("Error unhooking: " + t.getMessage());
            }
        }
        mActiveHooks.clear();
        
        // Reload settings
        loadSettings();
        
        // Re-apply hooks if we have a stored lpparam
        if (mLastLpparam != null) {
            log("Re-applying hooks to PackageManagerService");
            hookPackageManagerService(mLastLpparam);
        } else {
            log("No stored lpparam, hooks will be applied when onPackageLoaded is called");
        }
        
        log("Module hot-reloaded successfully");
    }
    
    /**
     * Log helper.
     */
    private void log(String message) {
        LoggingHelper.info(TAG, message);
    }
} 