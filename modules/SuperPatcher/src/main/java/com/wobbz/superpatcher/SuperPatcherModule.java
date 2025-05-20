package com.wobbz.superpatcher;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

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

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dalvik.system.DexClassLoader;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedInterface.Hooker;
import io.github.libxposed.api.XposedInterface.BeforeHookCallback;
import io.github.libxposed.api.XposedInterface.AfterHookCallback;
import io.github.libxposed.api.XposedInterface.MethodUnhooker;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;

/**
 * SuperPatcher module provides core hooking capabilities for modifying methods, accessing fields,
 * and loading custom code into target applications.
 */
@XposedPlugin(
    id = "com.wobbz.superpatcher",
    name = "Super Patcher",
    description = "Advanced dynamic patching and hooking framework",
    version = "1.0.0",
    scope = {"*"} // Needs to hook any app
)
@HotReloadable
public class SuperPatcherModule implements IModulePlugin, IHotReloadable {
    private static final String TAG = "SuperPatcherModule";
    private static final String MODULE_ID = "com.wobbz.SuperPatcher";
    
    // Service key for other modules to find this module
    public static final String SERVICE_KEY = "com.wobbz.SuperPatcher.service";
    
    // Stores active hooks to support unhooking during hot reload
    private final Map<String, List<MethodUnhooker<?>>> mActiveHooks = new ConcurrentHashMap<>();
    
    // Store custom class loaders
    private final Map<String, DexClassLoader> mCustomClassLoaders = new ConcurrentHashMap<>();
    
    private Context mContext;
    private SettingsHelper mSettings;
    private boolean mVerboseLogging;
    private FeatureManager mFeatureManager;
    private XposedInterface mXposedInterface;
    
    // Store params for hot reload
    private PackageLoadedParam mLastParam;
    
    /**
     * Handle a package being loaded.
     * This is the main entry point for LSPosed modules.
     *
     * @param param The parameters for the loaded package.
     */
    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        try {
            // Store param for hot reload
            mLastParam = param;
            
            // Get context for the loaded package
            Context context = param.getAppContext();
            if (context == null) {
                // Try to get application context via another method if available
                context = param.getSystemContext();
            }
            
            mContext = context;
            mXposedInterface = param.getXposed();
            
            // Initialize settings if not already done
            if (mSettings == null && context != null) {
                mSettings = new SettingsHelper(context, MODULE_ID);
                mVerboseLogging = mSettings.getBoolean("verboseLogging", false);
                mFeatureManager = FeatureManager.getInstance(context);
                
                // Register this instance as a service for other modules to use
                mFeatureManager.registerService(SERVICE_KEY, this);
            }
            
            // Check if this package is enabled
            String[] enabledApps = mSettings.getStringArray("enabledApps");
            if (enabledApps == null || !isPackageEnabled(param.getPackageName(), enabledApps)) {
                return;
            }
            
            log("Handling package: " + param.getPackageName());
            
            // Apply method patches
            applyMethodPatches(param);
            
            // Load custom DEX files if enabled
            if (mSettings.getBoolean("loadCustomDex", false)) {
                loadCustomDexFiles(param);
            }
        } catch (Exception e) {
            LoggingHelper.error(TAG, "Error in onPackageLoaded for " + param.getPackageName(), e);
        }
    }
    
    /**
     * Initialize the module.
     *
     * @param param The parameters for the module initialization.
     */
    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        try {
            // Store XposedInterface
            mXposedInterface = param.getXposed();
            
            log("SuperPatcher module loaded");
        } catch (Exception e) {
            LoggingHelper.error(TAG, "Error in onModuleLoaded", e);
        }
    }
    
    /**
     * Initialize with context.
     */
    @Override
    public void initialize(Context context, XposedInterface xposedInterface) {
        this.mContext = context;
        this.mXposedInterface = xposedInterface;
        
        mSettings = new SettingsHelper(context, MODULE_ID);
        mVerboseLogging = mSettings.getBoolean("verboseLogging", false);
        mFeatureManager = FeatureManager.getInstance(context);
        
        // Register this instance as a service for other modules to use
        mFeatureManager.registerService(SERVICE_KEY, this);
        
        log("SuperPatcher initialized with context");
    }
    
    /**
     * Check if a package is enabled for patching.
     *
     * @param packageName The package name to check.
     * @param enabledApps Array of enabled packages.
     * @return true if the package is enabled, false otherwise.
     */
    private boolean isPackageEnabled(String packageName, String[] enabledApps) {
        for (String app : enabledApps) {
            if (app.equals(packageName)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Apply method patches defined in settings to the loaded package.
     *
     * @param param The parameters for the loaded package.
     */
    private void applyMethodPatches(PackageLoadedParam param) {
        try {
            String patchesJson = mSettings.getString("patchDefinitions", "[]");
            JSONArray patches = new JSONArray(patchesJson);
            
            for (int i = 0; i < patches.length(); i++) {
                JSONObject patch = patches.getJSONObject(i);
                
                // Check if this patch applies to the current package
                String targetPackage = patch.optString("package", "");
                if (!targetPackage.isEmpty() && !param.getPackageName().equals(targetPackage)) {
                    continue;
                }
                
                // Required fields
                String className = patch.getString("className");
                String methodName = patch.getString("methodName");
                String hookType = patch.optString("hookType", "after");
                
                // Handle parameter types
                JSONArray paramTypesJson = patch.optJSONArray("parameterTypes");
                Object[] parameterTypes = null;
                
                if (paramTypesJson != null) {
                    parameterTypes = new Object[paramTypesJson.length()];
                    for (int j = 0; j < paramTypesJson.length(); j++) {
                        parameterTypes[j] = getClassFromName(paramTypesJson.getString(j), param.getClassLoader());
                    }
                }
                
                // Apply the hook
                applyHook(param, className, methodName, parameterTypes, hookType, patch);
            }
        } catch (JSONException e) {
            LoggingHelper.error(TAG, "Error parsing patch definitions", e);
        }
    }
    
    /**
     * Apply a hook to a method.
     *
     * @param param The parameters for the loaded package.
     * @param className The class name to hook.
     * @param methodName The method name to hook.
     * @param parameterTypes The parameter types for the method.
     * @param hookType The type of hook to apply (before, after, replace).
     * @param config Additional configuration for the hook.
     */
    private void applyHook(PackageLoadedParam param, String className, 
                           String methodName, Object[] parameterTypes, String hookType, 
                           JSONObject config) {
        try {
            Class<?> targetClass = findClassWithPermissionCheck(className, param.getClassLoader());
            if (targetClass == null) {
                LoggingHelper.error(TAG, "Failed to find class: " + className);
                return;
            }
            
            // Get XposedInterface
            XposedInterface xposed = param.getXposed();
            if (xposed == null) {
                LoggingHelper.error(TAG, "XposedInterface is null, cannot apply hook");
                return;
            }
            
            Method targetMethod = null;
            List<MethodUnhooker<?>> unhookers = new ArrayList<>();
            
            // Try to find the exact method
            if (parameterTypes != null) {
                targetMethod = findMethodWithPermissionCheck(targetClass, methodName, parameterTypes);
                if (targetMethod != null) {
                    // Hook the method based on the hook type
                    MethodUnhooker<?> unhooker = null;
                    if (hookType.equals("replace")) {
                        unhooker = xposed.hook(targetMethod, new ReplaceHooker(config, param.getClassLoader()));
                    } else if (hookType.equals("before") || hookType.equals("after") || hookType.equals("both")) {
                        unhooker = xposed.hook(targetMethod, new BeforeAfterHooker(hookType, config, param.getClassLoader()));
                    }
                    
                    if (unhooker != null) {
                        unhookers.add(unhooker);
                        log("Hooked method: " + className + "." + methodName);
                    }
                }
            } 
            // If parameterTypes is null or method not found, try to hook all methods with the name
            else {
                Method[] methods = targetClass.getDeclaredMethods();
                for (Method method : methods) {
                    if (method.getName().equals(methodName)) {
                        MethodUnhooker<?> unhooker = null;
                        if (hookType.equals("replace")) {
                            unhooker = xposed.hook(method, new ReplaceHooker(config, param.getClassLoader()));
                        } else if (hookType.equals("before") || hookType.equals("after") || hookType.equals("both")) {
                            unhooker = xposed.hook(method, new BeforeAfterHooker(hookType, config, param.getClassLoader()));
                        }
                        
                        if (unhooker != null) {
                            unhookers.add(unhooker);
                            log("Hooked method: " + className + "." + methodName + " (variant)");
                        }
                    }
                }
            }
            
            if (!unhookers.isEmpty()) {
                // Store the unhookers for hot reload
                String hookKey = getHookKey(param.getPackageName(), className, methodName, parameterTypes);
                mActiveHooks.put(hookKey, unhookers);
                
                log("Successfully hooked " + className + "." + methodName);
            } else {
                LoggingHelper.error(TAG, "Failed to hook " + className + "." + methodName);
            }
            
        } catch (Throwable t) {
            LoggingHelper.error(TAG, "Error applying hook to " + className + "." + methodName, t);
        }
    }
    
    /**
     * Find a class with permission check - will attempt to use PermissionOverride if available.
     *
     * @param className The class name.
     * @param classLoader The class loader.
     * @return The class, or null if not found.
     */
    private Class<?> findClassWithPermissionCheck(String className, ClassLoader classLoader) {
        try {
            return XposedHelpers.findClass(className, classLoader);
        } catch (Throwable t) {
            // If standard reflection fails, try using PermissionOverride
            Object permissionOverride = getPermissionOverrideService();
            if (permissionOverride != null) {
                log("Standard reflection failed, trying PermissionOverride for class: " + className);
                try {
                    Method findClassMethod = permissionOverride.getClass().getMethod("findClass", 
                            String.class, ClassLoader.class);
                    Object result = findClassMethod.invoke(permissionOverride, className, classLoader);
                    if (result instanceof Class) {
                        return (Class<?>) result;
                    }
                } catch (Throwable e) {
                    LoggingHelper.error(TAG, "Error using PermissionOverride for class access", e);
                }
            }
            return null;
        }
    }
    
    /**
     * Find a constructor with permission check - will attempt to use PermissionOverride if available.
     *
     * @param clazz The class.
     * @param parameterTypes The parameter types.
     * @return The constructor, or null if not found.
     */
    private Constructor<?> findConstructorWithPermissionCheck(Class<?> clazz, Object[] parameterTypes) {
        try {
            return XposedHelpers.findConstructorExact(clazz, parameterTypes);
        } catch (Throwable t) {
            // If standard reflection fails, try using PermissionOverride
            Object permissionOverride = getPermissionOverrideService();
            if (permissionOverride != null) {
                log("Standard reflection failed, trying PermissionOverride for constructor access");
                try {
                    Method findConstructorMethod = permissionOverride.getClass().getMethod("findConstructor", 
                            Class.class, Object[].class);
                    Object result = findConstructorMethod.invoke(permissionOverride, clazz, parameterTypes);
                    if (result instanceof Constructor) {
                        return (Constructor<?>) result;
                    }
                } catch (Throwable e) {
                    LoggingHelper.error(TAG, "Error using PermissionOverride for constructor access", e);
                }
            }
            return null;
        }
    }
    
    /**
     * Find a method with permission check - will attempt to use PermissionOverride if available.
     *
     * @param clazz The class.
     * @param methodName The method name.
     * @param parameterTypes The parameter types.
     * @return The method, or null if not found.
     */
    private Method findMethodWithPermissionCheck(Class<?> clazz, String methodName, Object[] parameterTypes) {
        try {
            return XposedHelpers.findMethodExact(clazz, methodName, parameterTypes);
        } catch (Throwable t) {
            // If standard reflection fails, try using PermissionOverride
            Object permissionOverride = getPermissionOverrideService();
            if (permissionOverride != null) {
                log("Standard reflection failed, trying PermissionOverride for method access: " + methodName);
                try {
                    Method findMethodMethod = permissionOverride.getClass().getMethod("findMethod", 
                            Class.class, String.class, Object[].class);
                    Object result = findMethodMethod.invoke(permissionOverride, clazz, methodName, parameterTypes);
                    if (result instanceof Method) {
                        return (Method) result;
                    }
                } catch (Throwable e) {
                    LoggingHelper.error(TAG, "Error using PermissionOverride for method access", e);
                }
            }
            return null;
        }
    }
    
    /**
     * Get the PermissionOverride service from FeatureManager.
     *
     * @return The PermissionOverride service, or null if not available.
     */
    private Object getPermissionOverrideService() {
        if (mFeatureManager == null) {
            return null;
        }
        
        // Check for PermissionOverride service
        return mFeatureManager.getService("com.wobbz.PermissionOverride.service");
    }
    
    /**
     * Get a class from its name, handling primitive types.
     *
     * @param className The class name.
     * @param classLoader The class loader to use.
     * @return The Class object.
     */
    private Class<?> getClassFromName(String className, ClassLoader classLoader) throws ClassNotFoundException {
        switch (className) {
            case "boolean": return boolean.class;
            case "byte": return byte.class;
            case "char": return char.class;
            case "double": return double.class;
            case "float": return float.class;
            case "int": return int.class;
            case "long": return long.class;
            case "short": return short.class;
            case "void": return void.class;
            default: return classLoader.loadClass(className);
        }
    }
    
    /**
     * Generate a unique key for a hook.
     *
     * @param packageName The package name.
     * @param className The class name.
     * @param methodName The method name.
     * @param parameterTypes The parameter types.
     * @return A unique key for the hook.
     */
    private String getHookKey(String packageName, String className, String methodName, Object[] parameterTypes) {
        StringBuilder key = new StringBuilder();
        key.append(packageName).append("#")
           .append(className).append("#")
           .append(methodName);
        
        if (parameterTypes != null) {
            key.append("#");
            for (Object type : parameterTypes) {
                key.append(type.toString()).append(",");
            }
        }
        
        return key.toString();
    }
    
    /**
     * Load custom DEX files into the target application.
     *
     * @param param The parameters for the loaded package.
     */
    private void loadCustomDexFiles(PackageLoadedParam param) {
        String[] dexPaths = mSettings.getStringArray("customDexPaths");
        if (dexPaths == null || dexPaths.length == 0) {
            return;
        }
        
        File optimizedDir = mContext.getDir("dex", Context.MODE_PRIVATE);
        
        for (String dexPath : dexPaths) {
            try {
                File dexFile = new File(dexPath);
                if (!dexFile.exists()) {
                    LoggingHelper.error(TAG, "DEX file does not exist: " + dexPath);
                    continue;
                }
                
                DexClassLoader loader = new DexClassLoader(
                        dexFile.getAbsolutePath(),
                        optimizedDir.getAbsolutePath(),
                        null,
                        param.getClassLoader()
                );
                
                mCustomClassLoaders.put(dexPath, loader);
                log("Loaded custom DEX: " + dexPath);
                
            } catch (Throwable t) {
                LoggingHelper.error(TAG, "Error loading DEX file: " + dexPath, t);
            }
        }
    }
    
    /**
     * Get a ClassLoader for a specific DEX path.
     *
     * @param dexPath The path to the DEX file.
     * @return The ClassLoader, or null if not found.
     */
    public ClassLoader getDexClassLoader(String dexPath) {
        return mCustomClassLoaders.get(dexPath);
    }
    
    /**
     * Read the value of a field from an object.
     *
     * @param obj The object to read from (null for static fields).
     * @param className The class name.
     * @param fieldName The field name.
     * @param classLoader The class loader to use.
     * @return The field value.
     */
    public Object getFieldValue(Object obj, String className, String fieldName, ClassLoader classLoader) {
        try {
            Class<?> clazz = findClassWithPermissionCheck(className, classLoader);
            if (clazz == null) {
                return null;
            }
            
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(obj);
        } catch (Throwable t) {
            LoggingHelper.error(TAG, "Error getting field value", t);
            
            // Try using PermissionOverride if available
            Object permissionOverride = getPermissionOverrideService();
            if (permissionOverride != null) {
                try {
                    Method getFieldValueMethod = permissionOverride.getClass().getMethod("getFieldValue", 
                            Object.class, String.class, String.class, ClassLoader.class);
                    return getFieldValueMethod.invoke(permissionOverride, obj, className, fieldName, classLoader);
                } catch (Throwable e) {
                    LoggingHelper.error(TAG, "Error using PermissionOverride for field access", e);
                }
            }
            
            return null;
        }
    }
    
    /**
     * Set the value of a field in an object.
     *
     * @param obj The object to modify (null for static fields).
     * @param className The class name.
     * @param fieldName The field name.
     * @param value The value to set.
     * @param classLoader The class loader to use.
     * @return true if successful, false otherwise.
     */
    public boolean setFieldValue(Object obj, String className, String fieldName, Object value, ClassLoader classLoader) {
        try {
            Class<?> clazz = findClassWithPermissionCheck(className, classLoader);
            if (clazz == null) {
                return false;
            }
            
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(obj, value);
            return true;
        } catch (Throwable t) {
            LoggingHelper.error(TAG, "Error setting field value", t);
            
            // Try using PermissionOverride if available
            Object permissionOverride = getPermissionOverrideService();
            if (permissionOverride != null) {
                try {
                    Method setFieldValueMethod = permissionOverride.getClass().getMethod("setFieldValue", 
                            Object.class, String.class, String.class, Object.class, ClassLoader.class);
                    Object result = setFieldValueMethod.invoke(permissionOverride, obj, className, fieldName, value, classLoader);
                    return Boolean.TRUE.equals(result);
                } catch (Throwable e) {
                    LoggingHelper.error(TAG, "Error using PermissionOverride for field access", e);
                }
            }
            
            return false;
        }
    }
    
    /**
     * Create a new instance of a class.
     *
     * @param className The class name.
     * @param classLoader The class loader to use.
     * @param constructorParams The constructor parameters.
     * @return The new instance.
     */
    public Object createInstance(String className, ClassLoader classLoader, Object... constructorParams) {
        try {
            Class<?> clazz = findClassWithPermissionCheck(className, classLoader);
            if (clazz == null) {
                return null;
            }
            
            if (constructorParams == null || constructorParams.length == 0) {
                // Use default constructor
                Constructor<?> constructor = clazz.getDeclaredConstructor();
                constructor.setAccessible(true);
                return constructor.newInstance();
            } else {
                // Find matching constructor
                Class<?>[] paramTypes = new Class[constructorParams.length];
                for (int i = 0; i < constructorParams.length; i++) {
                    paramTypes[i] = constructorParams[i] != null ? constructorParams[i].getClass() : null;
                }
                
                Constructor<?> constructor = findConstructorWithPermissionCheck(clazz, paramTypes);
                if (constructor != null) {
                    constructor.setAccessible(true);
                    return constructor.newInstance(constructorParams);
                }
            }
            return null;
        } catch (Throwable t) {
            LoggingHelper.error(TAG, "Error creating instance", t);
            
            // Try using PermissionOverride if available
            Object permissionOverride = getPermissionOverrideService();
            if (permissionOverride != null) {
                try {
                    Method createInstanceMethod = permissionOverride.getClass().getMethod("createInstance", 
                            String.class, ClassLoader.class, Object[].class);
                    return createInstanceMethod.invoke(permissionOverride, className, classLoader, new Object[] { constructorParams });
                } catch (Throwable e) {
                    LoggingHelper.error(TAG, "Error using PermissionOverride for instance creation", e);
                }
            }
            
            return null;
        }
    }
    
    /**
     * Invoke a method on an object.
     *
     * @param obj The object to invoke on (null for static methods).
     * @param className The class name.
     * @param methodName The method name.
     * @param classLoader The class loader to use.
     * @param params The method parameters.
     * @return The method result.
     */
    public Object invokeMethod(Object obj, String className, String methodName, ClassLoader classLoader, Object... params) {
        try {
            Class<?> clazz = null;
            if (obj != null) {
                clazz = obj.getClass();
            } else if (className != null) {
                clazz = findClassWithPermissionCheck(className, classLoader);
            }
            
            if (clazz == null) {
                return null;
            }
            
            // Find matching method
            Class<?>[] paramTypes = new Class[params.length];
            for (int i = 0; i < params.length; i++) {
                paramTypes[i] = params[i] != null ? params[i].getClass() : null;
            }
            
            Method method = findMethodWithPermissionCheck(clazz, methodName, paramTypes);
            if (method != null) {
                method.setAccessible(true);
                return method.invoke(obj, params);
            }
            return null;
        } catch (Throwable t) {
            LoggingHelper.error(TAG, "Error invoking method", t);
            
            // Try using PermissionOverride if available
            Object permissionOverride = getPermissionOverrideService();
            if (permissionOverride != null) {
                try {
                    Method invokeMethodMethod = permissionOverride.getClass().getMethod("invokeMethod", 
                            Object.class, String.class, String.class, ClassLoader.class, Object[].class);
                    return invokeMethodMethod.invoke(permissionOverride, obj, className, methodName, classLoader, new Object[] { params });
                } catch (Throwable e) {
                    LoggingHelper.error(TAG, "Error using PermissionOverride for method invocation", e);
                }
            }
            
            return null;
        }
    }
    
    /**
     * API for other modules to request a hook.
     * 
     * @param packageName The package name to apply the hook to.
     * @param className The class to hook.
     * @param methodName The method to hook.
     * @param parameterTypes The parameter types or null for all methods with the name.
     * @param hookType The type of hook (before, after, replace, both).
     * @param callback The callback to invoke when the hook is triggered.
     * @return A unique ID for the hook, or null if failed.
     */
    public String requestHook(String packageName, String className, String methodName, 
                              String[] parameterTypes, String hookType, 
                              final HookCallback callback) {
        try {
            // Create JSON config
            JSONObject config = new JSONObject();
            config.put("className", className);
            config.put("methodName", methodName);
            config.put("hookType", hookType);
            config.put("package", packageName);
            
            if (parameterTypes != null) {
                JSONArray paramTypesArray = new JSONArray();
                for (String type : parameterTypes) {
                    paramTypesArray.put(type);
                }
                config.put("parameterTypes", paramTypesArray);
            }
            
            // Save the callback for later use
            String hookId = packageName + "#" + className + "#" + methodName;
            mCallbacks.put(hookId, callback);
            
            // Add this to patch definitions
            String patchesJson = mSettings.getString("patchDefinitions", "[]");
            JSONArray patches = new JSONArray(patchesJson);
            patches.put(config);
            mSettings.setString("patchDefinitions", patches.toString());
            
            return hookId;
        } catch (JSONException e) {
            LoggingHelper.error(TAG, "Error creating hook request", e);
            return null;
        }
    }
    
    /**
     * Callback for requested hooks.
     */
    public interface HookCallback {
        /**
         * Called when the hook is triggered.
         * 
         * @param method The method being hooked.
         * @param thisObject The object the method is being called on (null for static methods).
         * @param args The arguments passed to the method.
         * @param returnValue The return value from the method (null for before hooks).
         * @param isBefore True if called before the method, false if after.
         */
        void onHook(Member method, Object thisObject, Object[] args, Object returnValue, boolean isBefore);
    }
    
    // Store callbacks for requested hooks
    private final Map<String, HookCallback> mCallbacks = new ConcurrentHashMap<>();
    
    /**
     * Before/After hook implementation using the new Xposed API.
     */
    private class BeforeAfterHooker implements Hooker {
        private final String mType;
        private final JSONObject mConfig;
        private final ClassLoader mClassLoader;
        
        BeforeAfterHooker(String type, JSONObject config, ClassLoader classLoader) {
            mType = type;
            mConfig = config;
            mClassLoader = classLoader;
        }
        
        @Override
        public void before(BeforeHookCallback callback) throws Throwable {
            if (mType.equals("before") || mType.equals("both")) {
                handleHookedMethod(callback, true);
            }
        }
        
        @Override
        public void after(AfterHookCallback callback) throws Throwable {
            if (mType.equals("after") || mType.equals("both")) {
                handleHookedMethod(callback, false);
            }
        }
        
        private void handleHookedMethod(Object callback, boolean isBefore) {
            try {
                // Extract hook information for callbacks
                String hookId = null;
                Object method = null;
                Object thisObject = null;
                Object[] args = null;
                Object result = null;
                
                if (isBefore) {
                    BeforeHookCallback beforeCallback = (BeforeHookCallback) callback;
                    method = beforeCallback.getMember();
                    thisObject = beforeCallback.getThisObject();
                    args = beforeCallback.getArgs();
                } else {
                    AfterHookCallback afterCallback = (AfterHookCallback) callback;
                    method = afterCallback.getMember();
                    thisObject = afterCallback.getThisObject();
                    args = afterCallback.getArgs();
                    result = afterCallback.getResult();
                }
                
                if (method != null) {
                    String className = ((Member)method).getDeclaringClass().getName();
                    String methodName = ((Member)method).getName();
                    String packageName = thisObject != null ? 
                            thisObject.getClass().getClassLoader().toString() : "unknown";
                    hookId = packageName + "#" + className + "#" + methodName;
                    
                    // Invoke callback if registered
                    HookCallback hookCallback = mCallbacks.get(hookId);
                    if (hookCallback != null) {
                        hookCallback.onHook((Member)method, thisObject, args, result, isBefore);
                    }
                }
                
                // Handle modifications based on config
                boolean modifyArgs = mConfig.optBoolean("modifyArgs", false);
                boolean modifyReturn = mConfig.optBoolean("modifyReturn", false) && !isBefore;
                boolean logArgs = mConfig.optBoolean("logArgs", false);
                boolean logReturn = mConfig.optBoolean("logReturn", false) && !isBefore;
                
                // Log arguments if requested
                if (logArgs && mVerboseLogging) {
                    StringBuilder argsLog = new StringBuilder("Arguments: [");
                    for (int i = 0; i < args.length; i++) {
                        argsLog.append(args[i]);
                        if (i < args.length - 1) {
                            argsLog.append(", ");
                        }
                    }
                    argsLog.append("]");
                    log(argsLog.toString());
                }
                
                // Modify arguments if requested
                if (modifyArgs && mConfig.has("argValues")) {
                    JSONArray argValues = mConfig.getJSONArray("argValues");
                    for (int i = 0; i < Math.min(argValues.length(), args.length); i++) {
                        if (!argValues.isNull(i)) {
                            args[i] = convertJsonValue(argValues.get(i));
                        }
                    }
                }
                
                // Log return value if requested
                if (logReturn && mVerboseLogging) {
                    log("Return value: " + result);
                }
                
                // Modify return value if requested
                if (modifyReturn && mConfig.has("returnValue")) {
                    ((AfterHookCallback)callback).setResult(convertJsonValue(mConfig.get("returnValue")));
                }
                
            } catch (Throwable t) {
                LoggingHelper.error(TAG, "Error in method hook", t);
            }
        }
        
        private Object convertJsonValue(Object value) throws JSONException {
            if (value instanceof JSONObject) {
                JSONObject obj = (JSONObject) value;
                if (obj.has("type") && obj.has("value")) {
                    String type = obj.getString("type");
                    switch (type) {
                        case "boolean":
                            return obj.getBoolean("value");
                        case "int":
                            return obj.getInt("value");
                        case "long":
                            return obj.getLong("value");
                        case "double":
                            return obj.getDouble("value");
                        case "string":
                            return obj.getString("value");
                        case "null":
                            return null;
                    }
                }
            }
            
            return value;
        }
    }
    
    /**
     * Method replacement hook implementation using the new Xposed API.
     */
    private class ReplaceHooker implements Hooker {
        private final JSONObject mConfig;
        private final ClassLoader mClassLoader;
        
        ReplaceHooker(JSONObject config, ClassLoader classLoader) {
            mConfig = config;
            mClassLoader = classLoader;
        }
        
        @Override
        public void before(BeforeHookCallback callback) throws Throwable {
            try {
                // Extract hook information for callbacks
                String hookId = null;
                if (callback.getMember() != null) {
                    String className = callback.getMember().getDeclaringClass().getName();
                    String methodName = callback.getMember().getName();
                    String packageName = callback.getThisObject() != null ? 
                            callback.getThisObject().getClass().getClassLoader().toString() : "unknown";
                    hookId = packageName + "#" + className + "#" + methodName;
                    
                    // Invoke callback if registered
                    HookCallback hookCallback = mCallbacks.get(hookId);
                    if (hookCallback != null) {
                        hookCallback.onHook(callback.getMember(), callback.getThisObject(), callback.getArgs(), null, true);
                    }
                }
                
                // Get return value from config
                if (mConfig.has("returnValue")) {
                    callback.returnAndSkip(convertJsonValue(mConfig.get("returnValue")));
                } else {
                    // Default to null
                    callback.returnAndSkip(null);
                }
                
            } catch (Throwable t) {
                LoggingHelper.error(TAG, "Error in method replacement", t);
                callback.returnAndSkip(null);
            }
        }
        
        @Override
        public void after(AfterHookCallback callback) {
            // Method replacement doesn't need after logic
        }
        
        private Object convertJsonValue(Object value) throws JSONException {
            if (value instanceof JSONObject) {
                JSONObject obj = (JSONObject) value;
                if (obj.has("type") && obj.has("value")) {
                    String type = obj.getString("type");
                    switch (type) {
                        case "boolean":
                            return obj.getBoolean("value");
                        case "int":
                            return obj.getInt("value");
                        case "long":
                            return obj.getLong("value");
                        case "double":
                            return obj.getDouble("value");
                        case "string":
                            return obj.getString("value");
                        case "null":
                            return null;
                    }
                }
            }
            
            return value;
        }
    }
    
    /**
     * Handle hot reload requests.
     * This removes active hooks so they can be reinstalled with new settings.
     */
    @Override
    public void onHotReload() {
        log("Hot reloading SuperPatcher");
        
        // Unhook all active hooks
        for (List<MethodUnhooker<?>> unhookers : mActiveHooks.values()) {
            for (MethodUnhooker<?> unhooker : unhookers) {
                try {
                    unhooker.unhook();
                } catch (Exception e) {
                    LoggingHelper.error(TAG, "Error unhooking method during hot reload", e);
                }
            }
        }
        
        // Clear the active hooks
        mActiveHooks.clear();
        
        // Clear custom class loaders
        mCustomClassLoaders.clear();
        
        // Reload settings
        if (mContext != null) {
            mSettings = new SettingsHelper(mContext, MODULE_ID);
            mVerboseLogging = mSettings.getBoolean("verboseLogging", false);
        }
        
        // Re-apply hooks if we have stored the last package param
        if (mLastParam != null) {
            log("Re-applying hooks for " + mLastParam.getPackageName());
            // Apply method patches
            applyMethodPatches(mLastParam);
            
            // Load custom DEX files if enabled
            if (mSettings != null && mSettings.getBoolean("loadCustomDex", false)) {
                loadCustomDexFiles(mLastParam);
            }
        }
        
        log("SuperPatcher hot reload complete");
    }
    
    private void log(String message) {
        if (mVerboseLogging) {
            LoggingHelper.debug(TAG, message);
        } else {
            LoggingHelper.info(TAG, message);
        }
    }
}