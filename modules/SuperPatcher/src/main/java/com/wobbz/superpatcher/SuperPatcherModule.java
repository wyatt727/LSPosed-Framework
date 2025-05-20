package com.wobbz.superpatcher;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.wobbz.framework.IHotReloadable;
import com.wobbz.framework.IModulePlugin;
import com.wobbz.framework.development.LoggingHelper;
import com.wobbz.framework.features.FeatureManager;
import com.wobbz.framework.ui.models.SettingsHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dalvik.system.DexClassLoader;
import io.github.libxposed.api.XC_MethodHook;
import io.github.libxposed.api.XposedBridge;
import io.github.libxposed.api.XposedHelpers;
import io.github.libxposed.api.callbacks.XC_LoadPackage;
import io.github.libxposed.api.callbacks.XC_LoadPackage.PackageLoadedParam;
import io.github.libxposed.api.callbacks.XC_LoadPackage.ModuleLoadedParam;

/**
 * SuperPatcher module provides core hooking capabilities for modifying methods, accessing fields,
 * and loading custom code into target applications.
 */
public class SuperPatcherModule implements IModulePlugin, IHotReloadable {
    private static final String TAG = "SuperPatcherModule";
    private static final String MODULE_ID = "com.wobbz.SuperPatcher";
    
    // Service key for other modules to find this module
    public static final String SERVICE_KEY = "com.wobbz.SuperPatcher.service";
    
    // Stores active hooks to support unhooking during hot reload
    private final Map<String, List<XC_MethodHook.Unhook>> mActiveHooks = new ConcurrentHashMap<>();
    
    // Store custom class loaders
    private final Map<String, DexClassLoader> mCustomClassLoaders = new ConcurrentHashMap<>();
    
    private Context mContext;
    private SettingsHelper mSettings;
    private boolean mVerboseLogging;
    private FeatureManager mFeatureManager;
    
    /**
     * Handle a package being loaded.
     * This is the main entry point for LSPosed modules.
     *
     * @param context The context for this module.
     * @param lpparam The parameters for the loaded package.
     */
    @Override
    public void onPackageLoaded(PackageLoadedParam lpparam) throws Throwable {
        Context context = lpparam.appInfo != null ?
            XposedBridge.sInitialApplication.createPackageContext(lpparam.packageName, Context.CONTEXT_IGNORE_SECURITY) :
            XposedBridge.sInitialApplication;
        
        mContext = context;
        mSettings = new SettingsHelper(context, MODULE_ID);
        mVerboseLogging = mSettings.getBoolean("verboseLogging", false);
        mFeatureManager = FeatureManager.getInstance(context);
        
        // Register this instance as a service for other modules to use
        mFeatureManager.registerService(SERVICE_KEY, this);
        
        // Check if this package is enabled
        String[] enabledApps = mSettings.getStringArray("enabledApps");
        if (enabledApps == null || !isPackageEnabled(lpparam.packageName, enabledApps)) {
            return;
        }
        
        log("Handling package: " + lpparam.packageName);
        
        // Apply method patches
        applyMethodPatches(lpparam);
        
        // Load custom DEX files if enabled
        if (mSettings.getBoolean("loadCustomDex", false)) {
            loadCustomDexFiles(lpparam);
        }
    }
    
    /**
     * Initialize the module.
     *
     * @param startupParam The parameters for the module initialization.
     */
    @Override
    public void onModuleLoaded(ModuleLoadedParam startupParam) throws Throwable {
        // Initialize settings when module is loaded
        if (XposedBridge.sInitialApplication != null) {
            mContext = XposedBridge.sInitialApplication.getApplicationContext();
            mSettings = new SettingsHelper(mContext, MODULE_ID);
            mVerboseLogging = mSettings.getBoolean("verboseLogging", false);
            mFeatureManager = FeatureManager.getInstance(mContext);
            
            log("SuperPatcher initialized");
        }
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
     * @param lpparam The parameters for the loaded package.
     */
    private void applyMethodPatches(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            String patchesJson = mSettings.getString("patchDefinitions", "[]");
            JSONArray patches = new JSONArray(patchesJson);
            
            for (int i = 0; i < patches.length(); i++) {
                JSONObject patch = patches.getJSONObject(i);
                
                // Check if this patch applies to the current package
                String targetPackage = patch.optString("package", "");
                if (!targetPackage.isEmpty() && !lpparam.packageName.equals(targetPackage)) {
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
                        parameterTypes[j] = getClassFromName(paramTypesJson.getString(j), lpparam.classLoader);
                    }
                }
                
                // Apply the hook
                applyHook(lpparam, className, methodName, parameterTypes, hookType, patch);
            }
        } catch (JSONException e) {
            LoggingHelper.error(TAG, "Error parsing patch definitions", e);
        }
    }
    
    /**
     * Apply a hook to a method.
     *
     * @param lpparam The parameters for the loaded package.
     * @param className The class name to hook.
     * @param methodName The method name to hook.
     * @param parameterTypes The parameter types for the method.
     * @param hookType The type of hook to apply (before, after, replace).
     * @param config Additional configuration for the hook.
     */
    private void applyHook(XC_LoadPackage.LoadPackageParam lpparam, String className, 
                           String methodName, Object[] parameterTypes, String hookType, 
                           JSONObject config) {
        try {
            Class<?> targetClass = findClassWithPermissionCheck(className, lpparam.classLoader);
            if (targetClass == null) {
                LoggingHelper.error(TAG, "Failed to find class: " + className);
                return;
            }
            
            // Create hook based on type
            XC_MethodHook hook;
            
            if (hookType.equals("replace")) {
                hook = new MethodReplacementHook(config, lpparam.classLoader);
            } else {
                hook = new MethodHook(hookType, config, lpparam.classLoader);
            }
            
            // Apply hook based on method type
            List<XC_MethodHook.Unhook> unhooks = new ArrayList<>();
            
            if (methodName.equals("$init")) {
                // Constructor hook
                if (parameterTypes == null) {
                    unhooks.add(XposedBridge.hookAllConstructors(targetClass, hook));
                } else {
                    Constructor<?> constructor = findConstructorWithPermissionCheck(targetClass, parameterTypes);
                    if (constructor != null) {
                        unhooks.add(XposedBridge.hookMethod(constructor, hook));
                    }
                }
            } else {
                // Method hook
                if (parameterTypes == null) {
                    unhooks.add(XposedBridge.hookAllMethods(targetClass, methodName, hook));
                } else {
                    Method method = findMethodWithPermissionCheck(targetClass, methodName, parameterTypes);
                    if (method != null) {
                        unhooks.add(XposedBridge.hookMethod(method, hook));
                    }
                }
            }
            
            if (!unhooks.isEmpty()) {
                // Store the unhooks for hot reload
                String hookKey = getHookKey(lpparam.packageName, className, methodName, parameterTypes);
                mActiveHooks.put(hookKey, unhooks);
                
                log("Applied " + hookType + " hook to " + className + "." + methodName);
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
     * @param lpparam The parameters for the loaded package.
     */
    private void loadCustomDexFiles(XC_LoadPackage.LoadPackageParam lpparam) {
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
                        lpparam.classLoader
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
            
            Field field = XposedHelpers.findField(clazz, fieldName);
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
            XposedHelpers.findAndHookField(className, classLoader, fieldName, value);
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
            return XposedHelpers.newInstance(XposedHelpers.findClass(className, classLoader), constructorParams);
        } catch (Throwable t) {
            LoggingHelper.error(TAG, "Error creating instance", t);
            
            // Try using PermissionOverride if available
            Object permissionOverride = getPermissionOverrideService();
            if (permissionOverride != null) {
                try {
                    Method createInstanceMethod = permissionOverride.getClass().getMethod("createInstance", 
                            String.class, ClassLoader.class, Object[].class);
                    return createInstanceMethod.invoke(permissionOverride, className, classLoader, constructorParams);
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
            return XposedHelpers.callMethod(obj, methodName, params);
        } catch (Throwable t) {
            LoggingHelper.error(TAG, "Error invoking method", t);
            
            // Try using PermissionOverride if available
            Object permissionOverride = getPermissionOverrideService();
            if (permissionOverride != null) {
                try {
                    Method invokeMethodMethod = permissionOverride.getClass().getMethod("invokeMethod", 
                            Object.class, String.class, String.class, ClassLoader.class, Object[].class);
                    return invokeMethodMethod.invoke(permissionOverride, obj, className, methodName, classLoader, params);
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
     * Method hook implementation.
     * Handles before and after hooks based on configuration.
     */
    private class MethodHook extends XC_MethodHook {
        private final String mType;
        private final JSONObject mConfig;
        private final ClassLoader mClassLoader;
        
        MethodHook(String type, JSONObject config, ClassLoader classLoader) {
            mType = type;
            mConfig = config;
            mClassLoader = classLoader;
        }
        
        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            if (mType.equals("before") || mType.equals("both")) {
                handleHookedMethod(param, true);
            }
        }
        
        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            if (mType.equals("after") || mType.equals("both")) {
                handleHookedMethod(param, false);
            }
        }
        
        private void handleHookedMethod(MethodHookParam param, boolean isBefore) {
            try {
                // Extract hook information for callbacks
                String hookId = null;
                if (param.method != null) {
                    String className = param.method.getDeclaringClass().getName();
                    String methodName = param.method.getName();
                    String packageName = param.thisObject != null ? 
                            param.thisObject.getClass().getClassLoader().toString() : "unknown";
                    hookId = packageName + "#" + className + "#" + methodName;
                    
                    // Invoke callback if registered
                    HookCallback callback = mCallbacks.get(hookId);
                    if (callback != null) {
                        callback.onHook(param.method, param.thisObject, param.args, param.getResult(), isBefore);
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
                    for (int i = 0; i < param.args.length; i++) {
                        argsLog.append(param.args[i]);
                        if (i < param.args.length - 1) {
                            argsLog.append(", ");
                        }
                    }
                    argsLog.append("]");
                    log(argsLog.toString());
                }
                
                // Modify arguments if requested
                if (modifyArgs && mConfig.has("argValues")) {
                    JSONArray argValues = mConfig.getJSONArray("argValues");
                    for (int i = 0; i < Math.min(argValues.length(), param.args.length); i++) {
                        if (!argValues.isNull(i)) {
                            param.args[i] = convertJsonValue(argValues.get(i));
                        }
                    }
                }
                
                // Log return value if requested
                if (logReturn && mVerboseLogging) {
                    log("Return value: " + param.getResult());
                }
                
                // Modify return value if requested
                if (modifyReturn && mConfig.has("returnValue")) {
                    param.setResult(convertJsonValue(mConfig.get("returnValue")));
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
     * Method replacement hook implementation.
     * Completely replaces the original method implementation.
     */
    private class MethodReplacementHook extends XC_MethodReplacement {
        private final JSONObject mConfig;
        private final ClassLoader mClassLoader;
        
        MethodReplacementHook(JSONObject config, ClassLoader classLoader) {
            mConfig = config;
            mClassLoader = classLoader;
        }
        
        @Override
        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
            try {
                // Extract hook information for callbacks
                String hookId = null;
                if (param.method != null) {
                    String className = param.method.getDeclaringClass().getName();
                    String methodName = param.method.getName();
                    String packageName = param.thisObject != null ? 
                            param.thisObject.getClass().getClassLoader().toString() : "unknown";
                    hookId = packageName + "#" + className + "#" + methodName;
                    
                    // Invoke callback if registered
                    HookCallback callback = mCallbacks.get(hookId);
                    if (callback != null) {
                        callback.onHook(param.method, param.thisObject, param.args, null, true);
                    }
                }
                
                // Get return value from config
                if (mConfig.has("returnValue")) {
                    return convertJsonValue(mConfig.get("returnValue"));
                }
                
                // Execute custom code if specified
                if (mConfig.has("customCode")) {
                    // This is a placeholder for custom code execution
                    // In a real implementation, this might execute JavaScript or another language
                    log("Custom code execution not implemented yet");
                }
                
                // Default to null
                return null;
                
            } catch (Throwable t) {
                LoggingHelper.error(TAG, "Error in method replacement", t);
                return null;
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
     * Handle hot reload requests.
     * This removes active hooks so they can be reinstalled with new settings.
     */
    @Override
    public void onHotReload() {
        log("Hot reloading SuperPatcher");
        
        // Unhook all active hooks
        for (List<XC_MethodHook.Unhook> unhooks : mActiveHooks.values()) {
            for (XC_MethodHook.Unhook unhook : unhooks) {
                unhook.unhook();
            }
        }
        
        // Clear the active hooks
        mActiveHooks.clear();
        
        // Clear custom class loaders
        mCustomClassLoaders.clear();
        
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