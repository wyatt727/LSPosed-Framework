package com.wobbz.permissionoverride;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

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

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedInterface.Hooker;
import io.github.libxposed.api.XposedInterface.BeforeHookCallback;
import io.github.libxposed.api.XposedInterface.AfterHookCallback;
import io.github.libxposed.api.XposedInterface.MethodUnhooker;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.SystemServerLoadedParam;

/**
 * PermissionOverride module provides functionality to override permission checks,
 * bypass signature verification, and enhance reflection capabilities.
 */
@XposedPlugin(
    id = "com.wobbz.permissionoverride",
    name = "Permission Override",
    description = "Override permission checks and bypass signature verification",
    version = "1.0.0",
    scope = {"android", "*"} // Need system server and target apps
)
@HotReloadable
public class PermissionOverrideModule implements IModulePlugin, IHotReloadable, IPermissionOverrideService {
    private static final String TAG = "PermissionOverrideModule";
    private static final String MODULE_ID = "com.wobbz.PermissionOverride";
    
    // Service key for other modules to find this module
    public static final String SERVICE_KEY = "com.wobbz.PermissionOverride.service";
    
    // Permission result values
    public static final int PERMISSION_GRANTED = android.content.pm.PackageManager.PERMISSION_GRANTED;
    public static final int PERMISSION_DENIED = android.content.pm.PackageManager.PERMISSION_DENIED;
    public static final int PERMISSION_DEFAULT = -2;
    public static final int PERMISSION_FAKE_GRANT = -3;
    public static final int PERMISSION_FAKE_DENY = -4;
    
    // Store active hooks for hot reload support
    private final List<MethodUnhooker<?>> mActiveHooks = new ArrayList<>();
    
    // Cache permission decisions to avoid repeated lookups
    private final Map<String, Integer> mPermissionCache = new ConcurrentHashMap<>();
    
    // Permission overrides from settings
    private final Map<String, Map<String, Integer>> mAppPermissionOverrides = new HashMap<>();
    
    // Track permission request history for logging
    private final List<PermissionRequest> mPermissionRequests = new ArrayList<>();
    private static final int MAX_REQUEST_HISTORY = 100;
    
    private Context mContext;
    private SettingsHelper mSettings;
    private boolean mBypassSignatures;
    private boolean mLogRequests;
    private String mDefaultBehavior;
    private FeatureManager mFeatureManager;
    private XposedInterface mXposedInterface;
    
    // For re-hooking during hot reload
    private PackageLoadedParam mLastAndroidPackageParam;
    private PackageLoadedParam mLastAppPackageParam;
    private SystemServerLoadedParam mLastSystemServerParam;
    
    private IPermissionOverrideService mService = null;
    private final Set<String> forcedPermissions = new HashSet<>();
    private final Set<String> suppressedPermissions = new HashSet<>();

    // Updated: Use modern API for service connection
    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = IPermissionOverrideService.Stub.asInterface(service);
            log("Connected to PermissionOverrideService");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
            log("Disconnected from PermissionOverrideService");
        }
    };

    /**
     * Initialize the module with context
     */
    @Override
    public void initialize(Context context, XposedInterface xposedInterface) {
        this.mContext = context;
        this.mXposedInterface = xposedInterface;
        
        mSettings = new SettingsHelper(context, MODULE_ID);
        mFeatureManager = FeatureManager.getInstance(context);
        
        // Register this instance as a service for other modules to use
        mFeatureManager.registerService(SERVICE_KEY, this);
        
        // Load settings
        loadSettings();
        
        log("Module initialized with context and XposedInterface");
    }
    
    /**
     * Called when a package is loaded
     */
    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        String packageName = param.getPackageName();
        log("Processing package: " + packageName);
        
        // Check if this is the system server package
        if ("android".equals(packageName)) {
            mLastAndroidPackageParam = param;
            // Hook permission checks in PackageManagerService
            hookAndroidPermissionChecks(param);
        } else {
            mLastAppPackageParam = param;
            // Hook permission checks in apps
            hookAppPermissionChecks(param);
        }
        
        // Hook signature verification if enabled
        if (mBypassSignatures) {
            hookSignatureChecks(param);
        }
    }
    
    /**
     * Called when the system server is loaded
     */
    @Override
    public void onSystemServerLoaded(SystemServerLoadedParam param) {
        log("System server loaded");
        mLastSystemServerParam = param;
        // SystemServer is a good place to hook permission services
        // We might not need this if we already hook in onPackageLoaded for "android" package
    }
    
    /**
     * Load settings from SettingsHelper.
     */
    private void loadSettings() {
        mBypassSignatures = mSettings.getBoolean("bypassSignatureChecks", true);
        mLogRequests = mSettings.getBoolean("logPermissionRequests", false);
        mDefaultBehavior = mSettings.getString("defaultPermissionBehavior", "DEFAULT");
        
        // Load permission overrides
        loadPermissionOverrides();
    }
    
    /**
     * Load permission overrides from settings.
     */
    private void loadPermissionOverrides() {
        mAppPermissionOverrides.clear();
        
        try {
            String overridesJson = mSettings.getString("permissionOverrides", "[]");
            JSONArray overrides = new JSONArray(overridesJson);
            
            for (int i = 0; i < overrides.length(); i++) {
                JSONObject override = overrides.getJSONObject(i);
                
                // Skip if not enabled
                if (override.has("enabled") && !override.getBoolean("enabled")) {
                    continue;
                }
                
                String packageName = override.getString("packageName");
                
                // Get the existing permission map or create a new one
                Map<String, Integer> permissionMap = mAppPermissionOverrides.computeIfAbsent(
                        packageName, k -> new HashMap<>());
                
                // Single permission entry format from API.md
                if (override.has("permission") && override.has("action")) {
                    String permission = override.getString("permission");
                    String action = override.getString("action");
                    
                    int result = "GRANT".equals(action) ? PERMISSION_GRANTED : 
                                 "DENY".equals(action) ? PERMISSION_DENIED : 
                                 "FAKE_GRANT".equals(action) ? PERMISSION_FAKE_GRANT :
                                 "FAKE_DENY".equals(action) ? PERMISSION_FAKE_DENY :
                                 PERMISSION_DEFAULT;
                    
                    permissionMap.put(permission, result);
                }
                // Legacy format with "permissions" object (for backward compatibility)
                else if (override.has("permissions")) {
                    JSONObject permissions = override.getJSONObject("permissions");
                    
                    String[] permissionNames = JSONObject.getNames(permissions);
                    if (permissionNames != null) {
                        for (String permission : permissionNames) {
                            String value = permissions.getString(permission);
                            int result = "GRANT".equals(value) ? PERMISSION_GRANTED : 
                                         "DENY".equals(value) ? PERMISSION_DENIED : 
                                         "FAKE_GRANT".equals(value) ? PERMISSION_FAKE_GRANT :
                                         "FAKE_DENY".equals(value) ? PERMISSION_FAKE_DENY :
                                         PERMISSION_DEFAULT;
                            
                            permissionMap.put(permission, result);
                        }
                    }
                }
            }
            
            log("Loaded permission overrides for " + mAppPermissionOverrides.size() + " packages");
            
        } catch (JSONException e) {
            LoggingHelper.error(TAG, "Error loading permission overrides", e);
        }
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
            // If no apps are specified, all apps are targeted
            return true;
        }
        
        for (String app : targetApps) {
            if (app.equals(packageName)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Hook permission checks in the Android system server.
     *
     * @param param The package loaded parameter for the Android system server.
     */
    private void hookAndroidPermissionChecks(PackageLoadedParam param) {
        try {
            XposedInterface xposed = param.getXposed();
            ClassLoader classLoader = param.getClassLoader();
            
            // Hook permission checks in PackageManagerService
            try {
                // Hook checkPermission in PermissionManagerService
                Class<?> permissionManagerServiceClass = classLoader.loadClass("com.android.server.pm.permission.PermissionManagerService");
                Method checkPermissionMethod = permissionManagerServiceClass.getDeclaredMethod("checkPermission", String.class, String.class, int.class);
                
                MethodUnhooker<?> unhooker = xposed.hook(checkPermissionMethod, new PermissionManagerCheckPermissionHook());
                if (unhooker != null) {
                    mActiveHooks.add(unhooker);
                    log("Hooked PermissionManagerService.checkPermission");
                }
                
                // Hook grantRuntimePermission 
                Method grantRuntimePermissionMethod = permissionManagerServiceClass.getDeclaredMethod("grantRuntimePermission", String.class, String.class, int.class);
                unhooker = xposed.hook(grantRuntimePermissionMethod, new GrantRuntimePermissionHook());
                if (unhooker != null) {
                    mActiveHooks.add(unhooker);
                    log("Hooked PermissionManagerService.grantRuntimePermission");
                }
                
                // Hook revokeRuntimePermission (method signature might vary by Android version)
                try {
                    // Try with reason parameter (newer Android)
                    Method revokeRuntimePermissionMethod = permissionManagerServiceClass.getDeclaredMethod("revokeRuntimePermission", 
                            String.class, String.class, int.class, String.class); // Added String.class for reason
                    unhooker = xposed.hook(revokeRuntimePermissionMethod, new RevokeRuntimePermissionHook());
                    if (unhooker != null) {
                        mActiveHooks.add(unhooker);
                        log("Hooked PermissionManagerService.revokeRuntimePermission (with reason)");
                    }
                } catch (NoSuchMethodException e) {
                    // Try without reason parameter (older Android)
                    try {
                        Method revokeRuntimePermissionMethod = permissionManagerServiceClass.getDeclaredMethod("revokeRuntimePermission", 
                                String.class, String.class, int.class);
                        unhooker = xposed.hook(revokeRuntimePermissionMethod, new RevokeRuntimePermissionHook());
                        if (unhooker != null) {
                            mActiveHooks.add(unhooker);
                            log("Hooked PermissionManagerService.revokeRuntimePermission");
                        }
                    } catch (NoSuchMethodException e2) {
                        log("Could not find revokeRuntimePermission method");
                    }
                }
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                log("Error finding PermissionManagerService: " + e.getMessage());
            }
            
            // For older Android versions
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                try {
                    // Hook checkUidPermission in PackageManagerService
                    Class<?> packageManagerServiceClass = classLoader.loadClass("com.android.server.pm.PackageManagerService");
                    Method checkUidPermissionMethod = packageManagerServiceClass.getDeclaredMethod("checkUidPermission", String.class, int.class);
                    
                    MethodUnhooker<?> unhooker = xposed.hook(checkUidPermissionMethod, new CheckUidPermissionHook());
                    if (unhooker != null) {
                        mActiveHooks.add(unhooker);
                        log("Hooked PackageManagerService.checkUidPermission for older Android");
                    }
                } catch (ClassNotFoundException | NoSuchMethodException e) {
                    log("Error finding PackageManagerService for older Android: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            log("Error hooking Android permission checks: " + e.getMessage());
        }
    }
    
    /**
     * Hook permission checks in applications.
     *
     * @param param The package loaded parameter for the application.
     */
    private void hookAppPermissionChecks(PackageLoadedParam param) {
        String packageName = param.getPackageName();
        if (!isTargetedPackage(packageName)) {
            return;
        }
        
        try {
            XposedInterface xposed = param.getXposed();
            ClassLoader classLoader = param.getClassLoader();
            
            // Hook Context.checkPermission
            try {
                Class<?> contextClass = classLoader.loadClass("android.content.Context");
                Method checkPermissionMethod = contextClass.getDeclaredMethod("checkPermission", String.class, int.class, int.class);
                
                MethodUnhooker<?> unhooker = xposed.hook(checkPermissionMethod, new ContextCheckPermissionHook());
                if (unhooker != null) {
                    mActiveHooks.add(unhooker);
                    log("Hooked Context.checkPermission in " + packageName);
                }
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                log("Error finding Context.checkPermission: " + e.getMessage());
            }
            
            // Hook ApplicationPackageManager.checkPermission
            try {
                Class<?> appPackageManagerClass = classLoader.loadClass("android.app.ApplicationPackageManager");
                Method checkPermissionMethod = appPackageManagerClass.getDeclaredMethod("checkPermission", String.class, String.class);
                
                MethodUnhooker<?> unhooker = xposed.hook(checkPermissionMethod, new AppManagerCheckPermissionHook());
                if (unhooker != null) {
                    mActiveHooks.add(unhooker);
                    log("Hooked ApplicationPackageManager.checkPermission in " + packageName);
                }
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                // This class might not be available in all apps
                log("Could not find ApplicationPackageManager in " + packageName);
            }
            
        } catch (Exception e) {
            log("Error hooking app permission checks in " + packageName + ": " + e.getMessage());
        }
    }
    
    /**
     * Hook signature verification methods.
     *
     * @param param The package loaded parameter.
     */
    private void hookSignatureChecks(PackageLoadedParam param) {
        if (!mBypassSignatures) {
            return;
        }
        
        try {
            XposedInterface xposed = param.getXposed();
            ClassLoader classLoader = param.getClassLoader();
            
            // Hook PackageManagerService.compareSignatures
            try {
                Class<?> packageManagerServiceClass = classLoader.loadClass("com.android.server.pm.PackageManagerService");
                Method compareSignaturesMethod = packageManagerServiceClass.getDeclaredMethod("compareSignatures", 
                        android.content.pm.Signature[].class, android.content.pm.Signature[].class);
                
                MethodUnhooker<?> unhooker = xposed.hook(compareSignaturesMethod, new CompareSignaturesHook());
                if (unhooker != null) {
                    mActiveHooks.add(unhooker);
                    log("Hooked PackageManagerService.compareSignatures");
                }
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                log("Could not find PackageManagerService.compareSignatures: " + e.getMessage());
                
                // Try PackageManagerServiceUtils (may exist in newer Android versions)
                try {
                    Class<?> packageManagerServiceUtilsClass = classLoader.loadClass("com.android.server.pm.PackageManagerServiceUtils");
                    Method compareSignaturesMethod = packageManagerServiceUtilsClass.getDeclaredMethod("compareSignatures", 
                            android.content.pm.Signature[].class, android.content.pm.Signature[].class);
                    
                    MethodUnhooker<?> unhooker = xposed.hook(compareSignaturesMethod, new CompareSignaturesHook());
                    if (unhooker != null) {
                        mActiveHooks.add(unhooker);
                        log("Hooked PackageManagerServiceUtils.compareSignatures");
                    }
                } catch (ClassNotFoundException | NoSuchMethodException e2) {
                    log("Could not find PackageManagerServiceUtils.compareSignatures: " + e2.getMessage());
                }
            }
            
            // Hook ApplicationPackageManager.getPackageInfo to bypass signature validation
            try {
                Class<?> appPackageManagerClass = classLoader.loadClass("android.app.ApplicationPackageManager");
                Method getPackageInfoMethod = appPackageManagerClass.getDeclaredMethod("getPackageInfo", 
                        String.class, int.class);
                
                MethodUnhooker<?> unhooker = xposed.hook(getPackageInfoMethod, new GetPackageInfoHook());
                if (unhooker != null) {
                    mActiveHooks.add(unhooker);
                    log("Hooked ApplicationPackageManager.getPackageInfo");
                }
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                log("Could not find ApplicationPackageManager.getPackageInfo: " + e.getMessage());
            }
            
            // Hook PackageManagerService.checkSignatures (Android P+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    Class<?> packageManagerServiceClass = classLoader.loadClass("com.android.server.pm.PackageManagerService");
                    Method checkSignaturesMethod = packageManagerServiceClass.getDeclaredMethod("checkSignatures", 
                            String.class, String.class, int.class);
                    
                    MethodUnhooker<?> unhooker = xposed.hook(checkSignaturesMethod, new CheckSignaturesHook());
                    if (unhooker != null) {
                        mActiveHooks.add(unhooker);
                        log("Hooked PackageManagerService.checkSignatures");
                    }
                } catch (ClassNotFoundException | NoSuchMethodException e) {
                    log("Could not find PackageManagerService.checkSignatures: " + e.getMessage());
                }
            }
            
        } catch (Exception e) {
            log("Error hooking signature checks: " + e.getMessage());
        }
    }
    
    /**
     * Check if a permission is overridden for a package.
     *
     * @param packageName The package name to check.
     * @param permission The permission to check.
     * @return The override result, or null if no override exists.
     */
    private Integer checkPermissionOverride(String packageName, String permission) {
        // Check cache first
        String cacheKey = packageName + ":" + permission;
        if (mPermissionCache.containsKey(cacheKey)) {
            return mPermissionCache.get(cacheKey);
        }
        
        // Check app-specific overrides
        Map<String, Integer> appOverrides = mAppPermissionOverrides.get(packageName);
        if (appOverrides != null && appOverrides.containsKey(permission)) {
            Integer result = appOverrides.get(permission);
            mPermissionCache.put(cacheKey, result);
            return result;
        }
        
        // Apply default behavior
        Integer defaultResult = null;
        if ("GRANT".equals(mDefaultBehavior)) {
            defaultResult = PERMISSION_GRANTED;
        } else if ("DENY".equals(mDefaultBehavior)) {
            defaultResult = PERMISSION_DENIED;
        } else if ("FAKE_GRANT".equals(mDefaultBehavior)) {
            defaultResult = PERMISSION_FAKE_GRANT;
        } else if ("FAKE_DENY".equals(mDefaultBehavior)) {
            defaultResult = PERMISSION_FAKE_DENY;
        }
        // If DEFAULT, return null to use system behavior
        
        mPermissionCache.put(cacheKey, defaultResult);
        return defaultResult;
    }
    
    /**
     * Log a permission request.
     *
     * @param packageName The package requesting the permission.
     * @param permission The permission being requested.
     * @param granted Whether the permission was granted.
     */
    private void logPermissionRequest(String packageName, String permission, boolean granted) {
        PermissionRequest request = new PermissionRequest();
        request.packageName = packageName;
        request.permission = permission;
        request.granted = granted;
        request.timestamp = System.currentTimeMillis();
        
        synchronized (mPermissionRequests) {
            mPermissionRequests.add(request);
            
            // Keep only the most recent requests
            while (mPermissionRequests.size() > MAX_REQUEST_HISTORY) {
                mPermissionRequests.remove(0);
            }
        }
    }
    
    /**
     * Get the package name for a UID.
     *
     * @param uid The UID.
     * @return The package name, or null if not found.
     */
    private String getPackageNameForUid(int uid) {
        if (mContext == null) {
            return null;
        }
        
        try {
            String[] packages = mContext.getPackageManager().getPackagesForUid(uid);
            if (packages != null && packages.length > 0) {
                return packages[0];
            }
        } catch (Throwable t) {
            LoggingHelper.error(TAG, "Error getting package name for UID: " + uid, t);
        }
        
        return null;
    }
    
    /**
     * Get the calling package name.
     *
     * @return The calling package name, or null if not found.
     */
    private String getCallingPackageName() {
        int uid = Binder.getCallingUid();
        return getPackageNameForUid(uid);
    }
    
    /**
     * Get the permission request history.
     *
     * @return A copy of the permission request history.
     */
    public List<PermissionRequest> getPermissionRequestHistory() {
        synchronized (mPermissionRequests) {
            return new ArrayList<>(mPermissionRequests);
        }
    }
    
    /**
     * Handle hot reload requests.
     * This removes active hooks so they can be reinstalled with new settings.
     */
    @Override
    public void onHotReload() {
        log("Hot reloading PermissionOverride");
        
        // Unhook all active hooks
        for (MethodUnhooker<?> unhooker : mActiveHooks) {
            try {
                unhooker.unhook();
            } catch (Exception e) {
                log("Error unhooking during hot reload: " + e.getMessage());
            }
        }
        
        // Clear the active hooks
        mActiveHooks.clear();
        
        // Clear the permission cache
        mPermissionCache.clear();
        
        // Reload settings
        loadSettings();
        
        // Re-apply hooks using stored params
        if (mLastAndroidPackageParam != null) {
            log("Re-applying hooks to Android system");
            hookAndroidPermissionChecks(mLastAndroidPackageParam);
            if (mBypassSignatures) {
                hookSignatureChecks(mLastAndroidPackageParam);
            }
        }
        
        if (mLastAppPackageParam != null) {
            String packageName = mLastAppPackageParam.getPackageName();
            if (isTargetedPackage(packageName)) {
                log("Re-applying hooks to app: " + packageName);
                hookAppPermissionChecks(mLastAppPackageParam);
                if (mBypassSignatures) {
                    hookSignatureChecks(mLastAppPackageParam);
                }
            }
        }
        
        log("PermissionOverride hot reload complete");
    }
    
    /**
     * Model for permission requests, used for logging.
     */
    public static class PermissionRequest {
        public String packageName;
        public String permission;
        public boolean granted;
        public long timestamp;
    }
    
    private void log(String message) {
        LoggingHelper.info(TAG, message);
    }

    // Implementation of IPermissionOverrideService
    @Override
    public Integer checkPermissionOverrideStatus(String packageName, String permission) {
        String cacheKey = packageName + ":" + permission;
        if (mPermissionCache.containsKey(cacheKey)) {
            return mPermissionCache.get(cacheKey);
        }

        Map<String, Integer> packageOverrides = mAppPermissionOverrides.get(packageName);
        if (packageOverrides != null && packageOverrides.containsKey(permission)) {
            Integer result = packageOverrides.get(permission);
            mPermissionCache.put(cacheKey, result);
            return result;
        }
        // If no specific override, return PERMISSION_DEFAULT to indicate module's default, not system default
        // This distinguishes from `null` which means "no opinion from this module for this specific permission entry"
        return PERMISSION_DEFAULT; 
    }
    
    @Override
    public boolean P_isAppPermissionForced(String packageName, String permission) {
        Map<String, Integer> packageOverrides = mAppPermissionOverrides.get(packageName);
        if (packageOverrides != null && packageOverrides.containsKey(permission)) {
            return packageOverrides.get(permission) == PERMISSION_GRANTED;
        }
        return false;
    }
    
    @Override
    public boolean P_isAppPermissionSuppressed(String packageName, String permission) {
        Map<String, Integer> packageOverrides = mAppPermissionOverrides.get(packageName);
        if (packageOverrides != null && packageOverrides.containsKey(permission)) {
            return packageOverrides.get(permission) == PERMISSION_DENIED;
        }
        return false;
    }

    @Override
    public Class<?> findClass(String className, ClassLoader classLoader) {
        try {
            return classLoader.loadClass(className);
        } catch (ClassNotFoundException e) {
            log("IPermissionOverrideService: Class not found: " + className);
            return null;
        }
    }

    @Override
    public Method findMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        if (clazz == null) return null;
        try {
            return clazz.getDeclaredMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException e) {
            // Try with public methods
            try {
                return clazz.getMethod(methodName, parameterTypes);
            } catch (NoSuchMethodException e2) {
                log("IPermissionOverrideService: Method not found: " + methodName);
                return null;
            }
        }
    }

    @Override
    public Method findMethodWithObjects(Class<?> clazz, String methodName, Object[] rawParameterTypes) {
        if (clazz == null || rawParameterTypes == null) return null;
        
        // Convert Object[] to Class[]
        Class<?>[] parameterTypes = new Class<?>[rawParameterTypes.length];
        for (int i = 0; i < rawParameterTypes.length; i++) {
            if (rawParameterTypes[i] instanceof Class) {
                parameterTypes[i] = (Class<?>) rawParameterTypes[i];
            } else if (rawParameterTypes[i] instanceof String) {
                try {
                    parameterTypes[i] = Class.forName((String) rawParameterTypes[i]);
                } catch (ClassNotFoundException e) {
                    log("IPermissionOverrideService: Class not found for parameter: " + rawParameterTypes[i]);
                    return null;
                }
            } else if (rawParameterTypes[i] != null) {
                parameterTypes[i] = rawParameterTypes[i].getClass();
            } else {
                log("IPermissionOverrideService: Null parameter type at index " + i);
                return null;
            }
        }
        
        return findMethod(clazz, methodName, parameterTypes);
    }

    @Override
    public Constructor<?> findConstructor(Class<?> clazz, Class<?>... parameterTypes) {
        if (clazz == null) return null;
        try {
            return clazz.getDeclaredConstructor(parameterTypes);
        } catch (NoSuchMethodException e) {
            log("IPermissionOverrideService: Constructor not found for class " + clazz.getName());
            return null;
        }
    }

    @Override
    public Constructor<?> findConstructorWithObjects(Class<?> clazz, Object[] rawParameterTypes) {
        if (clazz == null || rawParameterTypes == null) return null;
        
        // Convert Object[] to Class[]
        Class<?>[] parameterTypes = new Class<?>[rawParameterTypes.length];
        for (int i = 0; i < rawParameterTypes.length; i++) {
            if (rawParameterTypes[i] instanceof Class) {
                parameterTypes[i] = (Class<?>) rawParameterTypes[i];
            } else if (rawParameterTypes[i] instanceof String) {
                try {
                    parameterTypes[i] = Class.forName((String) rawParameterTypes[i]);
                } catch (ClassNotFoundException e) {
                    log("IPermissionOverrideService: Class not found for parameter: " + rawParameterTypes[i]);
                    return null;
                }
            } else if (rawParameterTypes[i] != null) {
                parameterTypes[i] = rawParameterTypes[i].getClass();
            } else {
                log("IPermissionOverrideService: Null parameter type at index " + i);
                return null;
            }
        }
        
        return findConstructor(clazz, parameterTypes);
    }

    @Override
    public Object getFieldValue(Object obj, String className, String fieldName, ClassLoader classLoader) {
        try {
            Class<?> targetClass = null;
            if (obj != null) {
                targetClass = obj.getClass();
            } else if (className != null) {
                targetClass = classLoader.loadClass(className);
            } else {
                log("IPermissionOverrideService:getFieldValue - Both object and class name are null.");
                return null;
            }
            
            Field field = findField(targetClass, fieldName);
            if (field == null) {
                return null;
            }
            
            field.setAccessible(true);
            return field.get(obj);
        } catch (ClassNotFoundException e) {
            log("IPermissionOverrideService: Class not found for getFieldValue: " + className);
            return null;
        } catch (IllegalAccessException e) {
            log("IPermissionOverrideService: Cannot access field: " + fieldName);
            return null;
        }
    }

    @Override
    public boolean setFieldValue(Object obj, String className, String fieldName, Object value, ClassLoader classLoader) {
        try {
            Class<?> targetClass = null;
            if (obj != null) {
                targetClass = obj.getClass();
            } else if (className != null) {
                targetClass = classLoader.loadClass(className);
            } else {
                log("IPermissionOverrideService:setFieldValue - Both object and class name are null.");
                return false;
            }
            
            Field field = findField(targetClass, fieldName);
            if (field == null) {
                return false;
            }
            
            field.setAccessible(true);
            field.set(obj, value);
            return true;
        } catch (ClassNotFoundException e) {
            log("IPermissionOverrideService: Class not found for setFieldValue: " + className);
            return false;
        } catch (IllegalAccessException e) {
            log("IPermissionOverrideService: Cannot access field: " + fieldName);
            return false;
        }
    }

    @Override
    public Object createInstance(String className, ClassLoader classLoader, Object... constructorParams) {
        try {
            Class<?> clazz = classLoader.loadClass(className);
            
            // Find matching constructor
            Class<?>[] paramTypes = new Class[constructorParams.length];
            for (int i = 0; i < constructorParams.length; i++) {
                paramTypes[i] = constructorParams[i] != null ? constructorParams[i].getClass() : null;
            }
            
            Constructor<?> constructor = findConstructor(clazz, paramTypes);
            if (constructor == null) {
                // Try to find a compatible constructor
                for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {
                    if (isCompatibleConstructor(ctor, constructorParams)) {
                        constructor = ctor;
                        break;
                    }
                }
                
                if (constructor == null) {
                    log("IPermissionOverrideService: No compatible constructor found for " + className);
                    return null;
                }
            }
            
            constructor.setAccessible(true);
            return constructor.newInstance(constructorParams);
        } catch (ClassNotFoundException e) {
            log("IPermissionOverrideService: Class not found for createInstance: " + className);
            return null;
        } catch (Exception e) {
            log("IPermissionOverrideService: Error creating instance of " + className + ": " + e.getMessage());
            return null;
        }
    }

    @Override
    public Object invokeMethod(Object obj, String className, String methodName, ClassLoader classLoader, Object... params) {
        try {
            Class<?> targetClass = null;
            if (obj != null) {
                targetClass = obj.getClass();
            } else if (className != null) {
                targetClass = classLoader.loadClass(className);
            } else {
                log("IPermissionOverrideService:invokeMethod - Both object and class name are null.");
                return null;
            }
            
            // Find matching method
            Class<?>[] paramTypes = new Class[params.length];
            for (int i = 0; i < params.length; i++) {
                paramTypes[i] = params[i] != null ? params[i].getClass() : null;
            }
            
            Method method = findMethod(targetClass, methodName, paramTypes);
            if (method == null) {
                // Try to find a compatible method
                for (Method m : targetClass.getDeclaredMethods()) {
                    if (m.getName().equals(methodName) && isCompatibleMethod(m, params)) {
                        method = m;
                        break;
                    }
                }
                
                if (method == null) {
                    // Check public methods in superclasses
                    for (Method m : targetClass.getMethods()) {
                        if (m.getName().equals(methodName) && isCompatibleMethod(m, params)) {
                            method = m;
                            break;
                        }
                    }
                }
                
                if (method == null) {
                    log("IPermissionOverrideService: No compatible method found: " + methodName);
                    return null;
                }
            }
            
            method.setAccessible(true);
            return method.invoke(obj, params);
        } catch (ClassNotFoundException e) {
            log("IPermissionOverrideService: Class not found for invokeMethod: " + className);
            return null;
        } catch (Exception e) {
            log("IPermissionOverrideService: Error invoking method " + methodName + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Helper method to find a field in a class or its superclasses.
     */
    private Field findField(Class<?> clazz, String fieldName) {
        if (clazz == null) return null;
        
        try {
            Field field = clazz.getDeclaredField(fieldName);
            return field;
        } catch (NoSuchFieldException e) {
            // Try superclass
            if (clazz.getSuperclass() != null) {
                return findField(clazz.getSuperclass(), fieldName);
            }
            return null;
        }
    }
    
    /**
     * Check if a constructor is compatible with the given parameters.
     */
    private boolean isCompatibleConstructor(Constructor<?> constructor, Object[] params) {
        Class<?>[] paramTypes = constructor.getParameterTypes();
        if (paramTypes.length != params.length) {
            return false;
        }
        
        for (int i = 0; i < paramTypes.length; i++) {
            if (params[i] == null) {
                // Null can be assigned to any non-primitive type
                if (paramTypes[i].isPrimitive()) {
                    return false;
                }
            } else if (!paramTypes[i].isAssignableFrom(params[i].getClass())) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Check if a method is compatible with the given parameters.
     */
    private boolean isCompatibleMethod(Method method, Object[] params) {
        Class<?>[] paramTypes = method.getParameterTypes();
        if (paramTypes.length != params.length) {
            return false;
        }
        
        for (int i = 0; i < paramTypes.length; i++) {
            if (params[i] == null) {
                // Null can be assigned to any non-primitive type
                if (paramTypes[i].isPrimitive()) {
                    return false;
                }
            } else if (!paramTypes[i].isAssignableFrom(params[i].getClass())) {
                return false;
            }
        }
        
        return true;
    }

    private void connectToService(PackageLoadedParam lpparam) {
        // This method should run early, perhaps from handleLoadPackage or a similar hook point
        // For system_server ("android" package), we need to find the right context.
        Context systemContext = (Context) XposedInterface.Utils.callStaticMethod(
                XposedInterface.Utils.findClass("android.app.ActivityThread", lpparam.classLoader),
                "getSystemContext");

        if (systemContext != null) {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.wobbz.permissionoverride", "com.wobbz.permissionoverride.PermissionOverrideService"));
            systemContext.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
            log("Attempting to bind to PermissionOverrideService from system_server.");
        } else {
            log("Could not get system context to bind service.");
        }
    }

    /**
     * Hook for PermissionManagerService.checkPermission
     */
    private class PermissionManagerCheckPermissionHook implements Hooker {
        public void before(BeforeHookCallback callback) throws Throwable {
            String permissionName = (String) callback.getArgs()[0];
            String pkgName = (String) callback.getArgs()[1];
            // int callingUid = (int) callback.getArgs()[2]; // Not directly used but available
            
            if (mService != null) {
                try {
                    if (mService.P_isAppPermissionForced(pkgName, permissionName)) {
                        callback.returnAndSkip(PERMISSION_GRANTED);
                        log("Forced permission GRANTED for " + pkgName + " / " + permissionName);
                    } else if (mService.P_isAppPermissionSuppressed(pkgName, permissionName)) {
                        callback.returnAndSkip(PERMISSION_DENIED);
                        log("Suppressed permission DENIED for " + pkgName + " / " + permissionName);
                    }
                } catch (RemoteException e) {
                    log("RemoteException while checking permission: " + e.getMessage());
                }
            } else {
                // Fallback if service not connected
                if (forcedPermissions.contains(pkgName + "/" + permissionName)) {
                    callback.returnAndSkip(PERMISSION_GRANTED);
                } else if (suppressedPermissions.contains(pkgName + "/" + permissionName)) {
                    callback.returnAndSkip(PERMISSION_DENIED);
                }
            }
        }
        
        public void after(AfterHookCallback callback) {
            // No action needed after method execution
        }
    }
    
    /**
     * Hook for PermissionManagerService.grantRuntimePermission
     */
    private class GrantRuntimePermissionHook implements Hooker {
        public void before(BeforeHookCallback callback) throws Throwable {
            String packageName = (String) callback.getArgs()[0];
            String permName = (String) callback.getArgs()[1];
            
            if (mService != null) {
                try {
                    if (mService.P_isAppPermissionSuppressed(packageName, permName)) {
                        log("Attempt to grant suppressed permission " + permName + " for " + packageName + ". Preventing grant.");
                        callback.returnAndSkip(null); // Prevent permission grant
                    }
                } catch (RemoteException e) {
                    log("RemoteException in grantRuntimePermission hook: " + e.getMessage());
                }
            }
        }
        
        public void after(AfterHookCallback callback) {
            // No action needed after method execution
        }
    }
    
    /**
     * Hook for PermissionManagerService.revokeRuntimePermission
     */
    private class RevokeRuntimePermissionHook implements Hooker {
        public void before(BeforeHookCallback callback) throws Throwable {
            String packageName = (String) callback.getArgs()[0];
            String permName = (String) callback.getArgs()[1];
            
            if (mService != null) {
                try {
                    if (mService.P_isAppPermissionForced(packageName, permName)) {
                        log("Attempt to revoke forced permission " + permName + " for " + packageName + ". Preventing revocation.");
                        callback.returnAndSkip(null); // Prevent permission revocation
                    }
                } catch (RemoteException e) {
                    log("RemoteException in revokeRuntimePermission hook: " + e.getMessage());
                }
            }
        }
        
        public void after(AfterHookCallback callback) {
            // No action needed after method execution
        }
    }
    
    /**
     * Hook for PackageManagerService.checkUidPermission (older Android)
     */
    private class CheckUidPermissionHook implements Hooker {
        public void before(BeforeHookCallback callback) throws Throwable {
            String permissionName = (String) callback.getArgs()[0];
            int uid = (int) callback.getArgs()[1];
            
            String[] packages = mContext != null ? mContext.getPackageManager().getPackagesForUid(uid) : null;
            if (packages != null) {
                for (String pkgName : packages) {
                    if (isTargetedPackage(pkgName)) {
                        Integer result = checkPermissionOverride(pkgName, permissionName);
                        if (result != null) {
                            if (result == PERMISSION_FAKE_GRANT) {
                                log("FAKE GRANT permission " + permissionName + " for " + pkgName);
                                callback.returnAndSkip(PERMISSION_GRANTED);
                            } else if (result == PERMISSION_FAKE_DENY) {
                                log("FAKE DENY permission " + permissionName + " for " + pkgName);
                                callback.returnAndSkip(PERMISSION_DENIED);
                            } else {
                                callback.returnAndSkip(result);
                            }
                            
                            if (mLogRequests) {
                                boolean isGranted = (result == PERMISSION_GRANTED || result == PERMISSION_FAKE_GRANT);
                                logPermissionRequest(pkgName, permissionName, isGranted);
                            }
                            break;
                        }
                    }
                }
            }
        }
        
        public void after(AfterHookCallback callback) {
            // No action needed after method execution
        }
    }
    
    /**
     * Hook for Context.checkPermission
     */
    private class ContextCheckPermissionHook implements Hooker {
        public void before(BeforeHookCallback callback) throws Throwable {
            String permission = (String) callback.getArgs()[0];
            int pid = (int) callback.getArgs()[1];
            int uid = (int) callback.getArgs()[2];
            
            // Skip self-checks
            if (pid == Process.myPid() || uid == Process.myUid()) {
                return;
            }
            
            // Get the package name for the UID
            String packageName = getPackageNameForUid(uid);
            if (packageName == null || !isTargetedPackage(packageName)) {
                return;
            }
            
            // Check the permission override
            Integer result = checkPermissionOverride(packageName, permission);
            if (result != null) {
                // Log the permission request if enabled
                if (mLogRequests) {
                    // For logging, consider fake grant as granted and fake deny as denied
                    boolean isGranted = (result == PERMISSION_GRANTED || result == PERMISSION_FAKE_GRANT);
                    logPermissionRequest(packageName, permission, isGranted);
                }
                
                // Handle fake modes by returning GRANTED/DENIED but logging that it's fake
                if (result == PERMISSION_FAKE_GRANT) {
                    log("FAKE GRANT permission " + permission + " for " + packageName);
                    callback.returnAndSkip(PERMISSION_GRANTED);
                } else if (result == PERMISSION_FAKE_DENY) {
                    log("FAKE DENY permission " + permission + " for " + packageName);
                    callback.returnAndSkip(PERMISSION_DENIED);
                } else {
                    // Set the result and skip the original method for regular GRANT/DENY
                    callback.returnAndSkip(result);
                }
            }
        }
        
        public void after(AfterHookCallback callback) {
            // No action needed after method execution
        }
    }
    
    /**
     * Hook for ApplicationPackageManager.checkPermission
     */
    private class AppManagerCheckPermissionHook implements Hooker {
        public void before(BeforeHookCallback callback) throws Throwable {
            String permission = (String) callback.getArgs()[0];
            String packageName = (String) callback.getArgs()[1];
            
            Integer result = checkPermissionOverride(packageName, permission);
            if (result != null) {
                if (result == PERMISSION_GRANTED || result == PERMISSION_FAKE_GRANT) {
                    if (result == PERMISSION_FAKE_GRANT) {
                        log("FAKE GRANT permission " + permission + " for " + packageName);
                    }
                    callback.returnAndSkip(PERMISSION_GRANTED); 
                } else if (result == PERMISSION_DENIED || result == PERMISSION_FAKE_DENY) {
                    if (result == PERMISSION_FAKE_DENY) {
                        log("FAKE DENY permission " + permission + " for " + packageName);
                    }
                    callback.returnAndSkip(PERMISSION_DENIED);
                }
                
                if (mLogRequests) {
                    boolean isGranted = (result == PERMISSION_GRANTED || result == PERMISSION_FAKE_GRANT);
                    logPermissionRequest(packageName, permission, isGranted);
                }
            }
        }
        
        public void after(AfterHookCallback callback) {
            // No action needed after method execution
        }
    }
    
    /**
     * Hook for compareSignatures
     */
    private class CompareSignaturesHook implements Hooker {
        public void before(BeforeHookCallback callback) throws Throwable {
            // Always return SIGNATURE_MATCH (0) to indicate signatures match
            callback.returnAndSkip(PackageManager.SIGNATURE_MATCH);
        }
        
        public void after(AfterHookCallback callback) {
            // No action needed after method execution
        }
    }
    
    /**
     * Hook for ApplicationPackageManager.getPackageInfo
     */
    private class GetPackageInfoHook implements Hooker {
        public void after(AfterHookCallback callback) throws Throwable {
            // Only modify result if GET_SIGNATURES flag is set
            Integer flags = (Integer) callback.getArgs()[1];
            if ((flags & PackageManager.GET_SIGNATURES) != 0) {
                // The result is a PackageInfo object
                Object result = callback.getResult();
                if (result != null) {
                    // Modify signature fields if needed
                    // No need to directly modify - the compareSignatures hook handles verification
                }
            }
        }
        
        public void before(BeforeHookCallback callback) {
            // No action needed before method execution
        }
    }
    
    /**
     * Hook for PackageManagerService.checkSignatures
     */
    private class CheckSignaturesHook implements Hooker {
        public void before(BeforeHookCallback callback) throws Throwable {
            // Always return SIGNATURE_MATCH (0) to indicate signatures match
            callback.returnAndSkip(PackageManager.SIGNATURE_MATCH);
        }
        
        public void after(AfterHookCallback callback) {
            // No action needed after method execution
        }
    }
} 