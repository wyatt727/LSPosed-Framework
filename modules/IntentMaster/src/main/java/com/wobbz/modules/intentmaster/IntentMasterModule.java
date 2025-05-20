package com.wobbz.modules.intentmaster;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;

import com.wobbz.framework.IHotReloadable;
import com.wobbz.framework.IModulePlugin;
import com.wobbz.framework.annotations.HotReloadable;
import com.wobbz.framework.development.LoggingHelper;
import com.wobbz.framework.ui.models.SettingsField;
import com.wobbz.framework.ui.models.SettingsHelper;
import com.wobbz.framework.annotations.XposedPlugin;
import com.wobbz.modules.intentmaster.model.IntentLog;
import com.wobbz.modules.intentmaster.model.IntentRule;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedInterface.BeforeHookCallback;
import io.github.libxposed.api.XposedInterface.AfterHookCallback;
import io.github.libxposed.api.XposedInterface.Hooker;
import io.github.libxposed.api.XposedInterface.MethodUnhooker;
import io.github.libxposed.api.XposedModuleInterface;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;

/**
 * IntentMasterModule - Intercepts, modifies, redirects, and logs intents between applications.
 * 
 * This module hooks into key Android methods responsible for intent handling and applies
 * rules defined by the user for matching and modifying intents.
 */
@XposedPlugin(
    id = "com.wobbz.IntentMaster",
    name = "Intent Master",
    description = "Intercepts, modifies, redirects, and logs intents between applications",
    version = "1.0.0",
    author = "wobbz"
)
@HotReloadable
public class IntentMasterModule implements IModulePlugin, IHotReloadable {
    private static final String TAG = "IntentMasterModule";
    private static final int MAX_LOGS = 100;
    
    private SettingsHelper settings;
    private final List<IntentRule> rules = new ArrayList<>();
    private final List<IntentLog> logs = new CopyOnWriteArrayList<>();
    private boolean interceptionEnabled = true;
    private boolean logAllIntents = true;
    private List<String> targetApps = new ArrayList<>();
    private XposedInterface xposedInterface;
    private Context moduleContext;
    
    // Keep track of unhookers for hot reload
    private final List<MethodUnhooker<?>> unhookers = new ArrayList<>();
    
    public IntentMasterModule() {
        // Will be initialized in initialize()
    }
    
    /**
     * Initialize the module with context.
     */
    public void initialize(Context context, XposedInterface xposedInterface) {
        this.xposedInterface = xposedInterface;
        this.moduleContext = context;
        
        // Initialize settings
        this.settings = new SettingsHelper(context, "com.wobbz.IntentMaster");
        
        // Load settings
        loadSettings();
    }
    
    /**
     * Handles changes to module settings.
     */
    private void onSettingsChanged(String key, Object newValue) {
        LoggingHelper.debug(TAG, "Settings changed: " + key);
        loadSettings();
    }
    
    /**
     * Loads all module settings.
     */
    private void loadSettings() {
        interceptionEnabled = settings.getBoolean("interceptionEnabled", true);
        logAllIntents = settings.getBoolean("logAllIntents", true);
        
        // Load target apps
        String[] targetAppsArray = settings.getStringArray("targetApps");
        if (targetAppsArray != null) {
            targetApps = new ArrayList<>(Arrays.asList(targetAppsArray));
        } else {
            targetApps = new ArrayList<>();
        }
        
        // Load intent rules
        String rulesJson = settings.getString("intentRules", "[]");
        try {
            JSONArray rulesArray = new JSONArray(rulesJson);
            rules.clear();
            for (int i = 0; i < rulesArray.length(); i++) {
                rules.add(IntentRule.fromJson(rulesArray.getJSONObject(i)));
            }
        } catch (JSONException e) {
            LoggingHelper.error(TAG, "Failed to parse intent rules: " + e.getMessage());
        }
        
        // Save logs to settings
        saveLogs();
    }
    
    /**
     * Saves intent logs to settings.
     */
    private void saveLogs() {
        try {
            JSONArray logsArray = new JSONArray();
            // Limit the number of logs to prevent settings from growing too large
            List<IntentLog> logsToSave = logs.size() <= MAX_LOGS ? 
                    logs : logs.subList(logs.size() - MAX_LOGS, logs.size());
            
            for (IntentLog log : logsToSave) {
                logsArray.put(log.toJson());
            }
            
            settings.putString("intentLogs", logsArray.toString());
        } catch (JSONException e) {
            LoggingHelper.error(TAG, "Failed to save logs: " + e.getMessage());
        }
    }
    
    /**
     * Checks if a package is targeted by this module.
     */
    private boolean isTargetPackage(String packageName) {
        // Always hook android system for system-wide intent monitoring
        if ("android".equals(packageName)) {
            return true;
        }
        
        // If targetApps is empty, hook all packages (use with caution)
        if (targetApps.isEmpty()) {
            return true;
        }
        
        return targetApps.contains(packageName);
    }
    
    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        String packageName = param.getPackageName();
        
        // Only hook target packages
        if (!isTargetPackage(packageName)) {
            return;
        }
        
        LoggingHelper.debug(TAG, "Hooking " + packageName);
        
        try {
            // Hook Activity.startActivity methods
            hookStartActivity(param.getClassLoader());
            
            // Hook Context.startService methods
            hookStartService(param.getClassLoader());
            
            // Hook Context.sendBroadcast methods
            hookSendBroadcast(param.getClassLoader());
            
            // Hook Context.bindService methods
            hookBindService(param.getClassLoader());
        } catch (Exception e) {
            LoggingHelper.error(TAG, "Error setting up hooks: " + e.getMessage());
        }
    }
    
    /**
     * Hooks Activity.startActivity methods.
     */
    private void hookStartActivity(ClassLoader classLoader) throws Exception {
        // Get the Activity class
        Class<?> activityClass = classLoader.loadClass("android.app.Activity");
        
        // Hook Activity.startActivity(Intent)
        Method startActivityMethod = activityClass.getDeclaredMethod("startActivity", Intent.class);
        MethodUnhooker<?> unhooker1 = xposedInterface.hook(startActivityMethod, ActivityStartActivityHook.class);
        if (unhooker1 != null) {
            unhookers.add(unhooker1);
        }
        
        // Hook Activity.startActivity(Intent, Bundle)
        Method startActivityWithBundleMethod = activityClass.getDeclaredMethod("startActivity", Intent.class, Bundle.class);
        MethodUnhooker<?> unhooker2 = xposedInterface.hook(startActivityWithBundleMethod, ActivityStartActivityWithBundleHook.class);
        if (unhooker2 != null) {
            unhookers.add(unhooker2);
        }
        
        // Get the Context class
        Class<?> contextClass = classLoader.loadClass("android.content.Context");
        
        // Hook Context.startActivity(Intent)
        Method contextStartActivityMethod = contextClass.getDeclaredMethod("startActivity", Intent.class);
        MethodUnhooker<?> unhooker3 = xposedInterface.hook(contextStartActivityMethod, ContextStartActivityHook.class);
        if (unhooker3 != null) {
            unhookers.add(unhooker3);
        }
        
        // Hook Context.startActivity(Intent, Bundle)
        Method contextStartActivityWithBundleMethod = contextClass.getDeclaredMethod("startActivity", Intent.class, Bundle.class);
        MethodUnhooker<?> unhooker4 = xposedInterface.hook(contextStartActivityWithBundleMethod, ContextStartActivityWithBundleHook.class);
        if (unhooker4 != null) {
            unhookers.add(unhooker4);
        }
    }
    
    /**
     * Hook classes for Activity.startActivity
     */
    public static class ActivityStartActivityHook implements Hooker {
        public static void before(BeforeHookCallback callback) {
            // Get the activity instance
            Activity activity = (Activity) callback.getThisObject();
            // Get the intent argument
            Intent intent = (Intent) callback.getArgs()[0];
            
            // Process the intent
            IntentMasterModule module = getInstance();
            if (module == null) return;
            
            Intent modifiedIntent = module.processIntent(intent, activity.getPackageName());
            if (modifiedIntent == null) {
                // Block the intent
                callback.returnAndSkip(null);
            } else if (modifiedIntent != intent) {
                // Replace with modified intent
                callback.getArgs()[0] = modifiedIntent;
            }
        }
        
        public static void after(AfterHookCallback callback) {
            // No implementation needed for after hook
        }
    }
    
    public static class ActivityStartActivityWithBundleHook implements Hooker {
        public static void before(BeforeHookCallback callback) {
            // Get the activity instance
            Activity activity = (Activity) callback.getThisObject();
            // Get the intent argument
            Intent intent = (Intent) callback.getArgs()[0];
            
            // Process the intent
            IntentMasterModule module = getInstance();
            if (module == null) return;
            
            Intent modifiedIntent = module.processIntent(intent, activity.getPackageName());
            if (modifiedIntent == null) {
                // Block the intent
                callback.returnAndSkip(null);
            } else if (modifiedIntent != intent) {
                // Replace with modified intent
                callback.getArgs()[0] = modifiedIntent;
            }
        }
        
        public static void after(AfterHookCallback callback) {
            // No implementation needed for after hook
        }
    }
    
    public static class ContextStartActivityHook implements Hooker {
        public static void before(BeforeHookCallback callback) {
            // Get the context instance
            Context context = (Context) callback.getThisObject();
            // Get the intent argument
            Intent intent = (Intent) callback.getArgs()[0];
            
            // Process the intent
            IntentMasterModule module = getInstance();
            if (module == null) return;
            
            Intent modifiedIntent = module.processIntent(intent, context.getPackageName());
            if (modifiedIntent == null) {
                // Block the intent
                callback.returnAndSkip(null);
            } else if (modifiedIntent != intent) {
                // Replace with modified intent
                callback.getArgs()[0] = modifiedIntent;
            }
        }
        
        public static void after(AfterHookCallback callback) {
            // No implementation needed for after hook
        }
    }
    
    public static class ContextStartActivityWithBundleHook implements Hooker {
        public static void before(BeforeHookCallback callback) {
            // Get the context instance
            Context context = (Context) callback.getThisObject();
            // Get the intent argument
            Intent intent = (Intent) callback.getArgs()[0];
            
            // Process the intent
            IntentMasterModule module = getInstance();
            if (module == null) return;
            
            Intent modifiedIntent = module.processIntent(intent, context.getPackageName());
            if (modifiedIntent == null) {
                // Block the intent
                callback.returnAndSkip(null);
            } else if (modifiedIntent != intent) {
                // Replace with modified intent
                callback.getArgs()[0] = modifiedIntent;
            }
        }
        
        public static void after(AfterHookCallback callback) {
            // No implementation needed for after hook
        }
    }
    
    /**
     * Hooks Context.startService methods.
     */
    private void hookStartService(ClassLoader classLoader) throws Exception {
        Class<?> contextClass = classLoader.loadClass("android.content.Context");
        
        // Hook Context.startService(Intent)
        Method startServiceMethod = contextClass.getDeclaredMethod("startService", Intent.class);
        MethodUnhooker<?> unhooker1 = xposedInterface.hook(startServiceMethod, StartServiceHook.class);
        if (unhooker1 != null) {
            unhookers.add(unhooker1);
        }
        
        // Hook Context.startForegroundService(Intent) - added in Android O
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Method startForegroundServiceMethod = contextClass.getDeclaredMethod("startForegroundService", Intent.class);
            MethodUnhooker<?> unhooker2 = xposedInterface.hook(startForegroundServiceMethod, StartForegroundServiceHook.class);
            if (unhooker2 != null) {
                unhookers.add(unhooker2);
            }
        }
    }
    
    public static class StartServiceHook implements Hooker {
        public static void before(BeforeHookCallback callback) {
            // Get the context instance
            Context context = (Context) callback.getThisObject();
            // Get the intent argument
            Intent intent = (Intent) callback.getArgs()[0];
            
            // Process the intent
            IntentMasterModule module = getInstance();
            if (module == null) return;
            
            Intent modifiedIntent = module.processIntent(intent, context.getPackageName());
            if (modifiedIntent == null) {
                // Block the intent
                callback.returnAndSkip(null);
            } else if (modifiedIntent != intent) {
                // Replace with modified intent
                callback.getArgs()[0] = modifiedIntent;
            }
        }
        
        public static void after(AfterHookCallback callback) {
            // No implementation needed for after hook
        }
    }
    
    public static class StartForegroundServiceHook implements Hooker {
        public static void before(BeforeHookCallback callback) {
            // Get the context instance
            Context context = (Context) callback.getThisObject();
            // Get the intent argument
            Intent intent = (Intent) callback.getArgs()[0];
            
            // Process the intent
            IntentMasterModule module = getInstance();
            if (module == null) return;
            
            Intent modifiedIntent = module.processIntent(intent, context.getPackageName());
            if (modifiedIntent == null) {
                // Block the intent
                callback.returnAndSkip(null);
            } else if (modifiedIntent != intent) {
                // Replace with modified intent
                callback.getArgs()[0] = modifiedIntent;
            }
        }
        
        public static void after(AfterHookCallback callback) {
            // No implementation needed for after hook
        }
    }
    
    /**
     * Hooks Context.sendBroadcast methods.
     */
    private void hookSendBroadcast(ClassLoader classLoader) throws Exception {
        Class<?> contextClass = classLoader.loadClass("android.content.Context");
        
        // Hook Context.sendBroadcast(Intent)
        Method sendBroadcastMethod = contextClass.getDeclaredMethod("sendBroadcast", Intent.class);
        MethodUnhooker<?> unhooker1 = xposedInterface.hook(sendBroadcastMethod, SendBroadcastHook.class);
        if (unhooker1 != null) {
            unhookers.add(unhooker1);
        }
        
        // Hook Context.sendBroadcast(Intent, String)
        Method sendBroadcastWithPermMethod = contextClass.getDeclaredMethod("sendBroadcast", Intent.class, String.class);
        MethodUnhooker<?> unhooker2 = xposedInterface.hook(sendBroadcastWithPermMethod, SendBroadcastWithPermHook.class);
        if (unhooker2 != null) {
            unhookers.add(unhooker2);
        }
        
        // Hook Context.sendOrderedBroadcast
        Method sendOrderedBroadcastMethod = contextClass.getDeclaredMethod("sendOrderedBroadcast", 
                Intent.class, String.class);
        MethodUnhooker<?> unhooker3 = xposedInterface.hook(sendOrderedBroadcastMethod, SendOrderedBroadcastHook.class);
        if (unhooker3 != null) {
            unhookers.add(unhooker3);
        }
    }
    
    public static class SendBroadcastHook implements Hooker {
        public static void before(BeforeHookCallback callback) {
            // Get the context instance
            Context context = (Context) callback.getThisObject();
            // Get the intent argument
            Intent intent = (Intent) callback.getArgs()[0];
            
            // Process the intent
            IntentMasterModule module = getInstance();
            if (module == null) return;
            
            Intent modifiedIntent = module.processIntent(intent, context.getPackageName());
            if (modifiedIntent == null) {
                // Block the intent
                callback.returnAndSkip(null);
            } else if (modifiedIntent != intent) {
                // Replace with modified intent
                callback.getArgs()[0] = modifiedIntent;
            }
        }
        
        public static void after(AfterHookCallback callback) {
            // No implementation needed for after hook
        }
    }
    
    public static class SendBroadcastWithPermHook implements Hooker {
        public static void before(BeforeHookCallback callback) {
            // Get the context instance
            Context context = (Context) callback.getThisObject();
            // Get the intent argument
            Intent intent = (Intent) callback.getArgs()[0];
            
            // Process the intent
            IntentMasterModule module = getInstance();
            if (module == null) return;
            
            Intent modifiedIntent = module.processIntent(intent, context.getPackageName());
            if (modifiedIntent == null) {
                // Block the intent
                callback.returnAndSkip(null);
            } else if (modifiedIntent != intent) {
                // Replace with modified intent
                callback.getArgs()[0] = modifiedIntent;
            }
        }
        
        public static void after(AfterHookCallback callback) {
            // No implementation needed for after hook
        }
    }
    
    public static class SendOrderedBroadcastHook implements Hooker {
        public static void before(BeforeHookCallback callback) {
            // Get the context instance
            Context context = (Context) callback.getThisObject();
            // Get the intent argument
            Intent intent = (Intent) callback.getArgs()[0];
            
            // Process the intent
            IntentMasterModule module = getInstance();
            if (module == null) return;
            
            Intent modifiedIntent = module.processIntent(intent, context.getPackageName());
            if (modifiedIntent == null) {
                // Block the intent
                callback.returnAndSkip(null);
            } else if (modifiedIntent != intent) {
                // Replace with modified intent
                callback.getArgs()[0] = modifiedIntent;
            }
        }
        
        public static void after(AfterHookCallback callback) {
            // No implementation needed for after hook
        }
    }
    
    /**
     * Hooks Context.bindService methods.
     */
    private void hookBindService(ClassLoader classLoader) throws Exception {
        Class<?> contextClass = classLoader.loadClass("android.content.Context");
        
        // Hook Context.bindService(Intent, ServiceConnection, int)
        Method bindServiceMethod = contextClass.getDeclaredMethod("bindService", 
                Intent.class, ServiceConnection.class, int.class);
        MethodUnhooker<?> unhooker = xposedInterface.hook(bindServiceMethod, BindServiceHook.class);
        if (unhooker != null) {
            unhookers.add(unhooker);
        }
    }
    
    public static class BindServiceHook implements Hooker {
        public static void before(BeforeHookCallback callback) {
            // Get the context instance
            Context context = (Context) callback.getThisObject();
            // Get the intent argument
            Intent intent = (Intent) callback.getArgs()[0];
            
            // Process the intent
            IntentMasterModule module = getInstance();
            if (module == null) return;
            
            Intent modifiedIntent = module.processIntent(intent, context.getPackageName());
            if (modifiedIntent == null) {
                // Block the intent
                callback.returnAndSkip(false); // Return false to indicate binding failed
            } else if (modifiedIntent != intent) {
                // Replace with modified intent
                callback.getArgs()[0] = modifiedIntent;
            }
        }
        
        public static void after(AfterHookCallback callback) {
            // No implementation needed for after hook
        }
    }
    
    // Static instance reference for hooks to access
    private static IntentMasterModule instance;
    
    // Method to get the singleton instance
    public static IntentMasterModule getInstance() {
        return instance;
    }
    
    // Store instance on initialization
    @Override
    public void onModuleLoaded(ModuleLoadedParam startupParam) {
        instance = this;
    }
    
    /**
     * Process an intent according to the defined rules.
     * 
     * @param intent The intent to process
     * @param sourcePackage The package name of the app sending the intent
     * @return Modified intent, or null to block the intent
     */
    private Intent processIntent(Intent intent, String sourcePackage) {
        if (intent == null) return intent;
        
        // Always log if enabled
        if (logAllIntents) {
            logIntent(intent, sourcePackage, "ALLOWED", null);
        }
        
        // If interception is disabled, return the original intent
        if (!interceptionEnabled) {
            return intent;
        }
        
        String intentAction = intent.getAction() != null ? intent.getAction() : "";
        String intentType = intent.getType() != null ? intent.getType() : "";
        ComponentName component = intent.getComponent();
        String targetPackage = component != null ? component.getPackageName() : "";
        String targetClass = component != null ? component.getClassName() : "";
        
        // Check each rule for a match
        for (IntentRule rule : rules) {
            boolean actionMatch = rule.getActionPattern().isEmpty() || 
                    intentAction.matches(rule.getActionPattern());
            boolean typeMatch = rule.getTypePattern().isEmpty() || 
                    intentType.matches(rule.getTypePattern());
            boolean sourceMatch = rule.getSourcePackagePattern().isEmpty() || 
                    sourcePackage.matches(rule.getSourcePackagePattern());
            boolean targetPackageMatch = rule.getTargetPackagePattern().isEmpty() || 
                    targetPackage.matches(rule.getTargetPackagePattern());
            boolean targetClassMatch = rule.getTargetClassPattern().isEmpty() || 
                    targetClass.matches(rule.getTargetClassPattern());
            
            // All patterns must match for the rule to apply
            if (actionMatch && typeMatch && sourceMatch && targetPackageMatch && targetClassMatch) {
                LoggingHelper.debug(TAG, "Rule matched: " + rule.getName() + " for intent: " + intentAction);
                
                // Apply the rule
                switch (rule.getAction()) {
                    case "BLOCK":
                        logIntent(intent, sourcePackage, "BLOCKED", rule.getName());
                        return null;
                    case "MODIFY":
                        Intent modifiedIntent = new Intent(intent);
                        
                        // Modify action if specified
                        if (!rule.getNewAction().isEmpty()) {
                            modifiedIntent.setAction(rule.getNewAction());
                        }
                        
                        // Modify type if specified
                        if (!rule.getNewType().isEmpty()) {
                            modifiedIntent.setType(rule.getNewType());
                        }
                        
                        // Modify component if specified
                        if (!rule.getNewTargetPackage().isEmpty() || !rule.getNewTargetClass().isEmpty()) {
                            String newPackage = !rule.getNewTargetPackage().isEmpty() ? 
                                    rule.getNewTargetPackage() : targetPackage;
                            String newClass = !rule.getNewTargetClass().isEmpty() ? 
                                    rule.getNewTargetClass() : targetClass;
                            
                            if (!newPackage.isEmpty() && !newClass.isEmpty()) {
                                modifiedIntent.setComponent(new ComponentName(newPackage, newClass));
                            }
                        }
                        
                        logIntent(modifiedIntent, sourcePackage, "MODIFIED", rule.getName());
                        return modifiedIntent;
                    case "LOG":
                        logIntent(intent, sourcePackage, "LOGGED", rule.getName());
                        break;
                    default:
                        // No action or ALLOW
                        break;
                }
                
                // If the rule matched but we didn't return, continue to the next rule
            }
        }
        
        // Default: pass the intent through unchanged
        return intent;
    }
    
    /**
     * Logs an intent to the module's log storage.
     */
    private void logIntent(Intent intent, String sourcePackage, String status, String ruleMatched) {
        try {
            IntentLog logEntry = new IntentLog(intent, sourcePackage, status, ruleMatched);
            logs.add(logEntry);
            
            // Trim logs if we've exceeded the maximum
            if (logs.size() > MAX_LOGS * 1.5) {
                logs.subList(0, logs.size() - MAX_LOGS).clear();
            }
            
            // Save logs to settings
            saveLogs();
            
            LoggingHelper.debug(TAG, "Logged intent: " + logEntry.toString());
        } catch (Exception e) {
            LoggingHelper.error(TAG, "Failed to log intent: " + e.getMessage());
        }
    }
    
    /**
     * Implementation of onHotReload for hot reloading support.
     * This method is called when the module code is updated in development.
     */
    @Override
    public void onHotReload() {
        LoggingHelper.debug(TAG, "Hot reloading module");
        
        // Unhook all methods
        for (MethodUnhooker<?> unhooker : unhookers) {
            unhooker.unhook();
        }
        unhookers.clear();
        
        // Clear static instance and reinitialize
        instance = this;
        
        // Clear and reload settings
        loadSettings();
        
        // The hooks will be reapplied when onPackageLoaded is called again by the framework
        LoggingHelper.debug(TAG, "Module hot-reloaded successfully");
    }
    
    /**
     * Create and send a test intent based on specified configuration.
     * This allows testing the module's rules without needing another app.
     * 
     * @param testIntentConfig JSON configuration for the test intent
     * @return Result of sending the intent or an error message
     */
    public String sendTestIntent(JSONObject testIntentConfig) {
        try {
            // Create a new intent from the test configuration
            Intent testIntent = new Intent();
            
            // Set basic properties
            if (testIntentConfig.has("action")) {
                testIntent.setAction(testIntentConfig.getString("action"));
            }
            
            if (testIntentConfig.has("data")) {
                testIntent.setData(android.net.Uri.parse(testIntentConfig.getString("data")));
            }
            
            if (testIntentConfig.has("type")) {
                if (testIntentConfig.has("data")) {
                    testIntent.setDataAndType(
                        android.net.Uri.parse(testIntentConfig.getString("data")),
                        testIntentConfig.getString("type")
                    );
                } else {
                    testIntent.setType(testIntentConfig.getString("type"));
                }
            }
            
            // Set component if specified
            if (testIntentConfig.has("component")) {
                String component = testIntentConfig.getString("component");
                String[] parts = component.split("/", 2);
                if (parts.length == 2) {
                    String packageName = parts[0];
                    String className = parts[1];
                    // Handle shorthand class name (with dot prefix)
                    if (className.startsWith(".")) {
                        className = packageName + className;
                    }
                    testIntent.setComponent(new ComponentName(packageName, className));
                }
            }
            
            // Add categories
            if (testIntentConfig.has("categories")) {
                JSONArray categories = testIntentConfig.getJSONArray("categories");
                for (int i = 0; i < categories.length(); i++) {
                    testIntent.addCategory(categories.getString(i));
                }
            }
            
            // Add extras
            if (testIntentConfig.has("extras")) {
                JSONArray extras = testIntentConfig.getJSONArray("extras");
                for (int i = 0; i < extras.length(); i++) {
                    JSONObject extra = extras.getJSONObject(i);
                    String key = extra.getString("key");
                    String type = extra.getString("type");
                    
                    switch (type) {
                        case "STRING":
                            testIntent.putExtra(key, extra.getString("value"));
                            break;
                        case "INT":
                            testIntent.putExtra(key, extra.getInt("value"));
                            break;
                        case "BOOLEAN":
                            testIntent.putExtra(key, extra.getBoolean("value"));
                            break;
                        case "FLOAT":
                            testIntent.putExtra(key, (float) extra.getDouble("value"));
                            break;
                        case "LONG":
                            testIntent.putExtra(key, extra.getLong("value"));
                            break;
                        case "DOUBLE":
                            testIntent.putExtra(key, extra.getDouble("value"));
                            break;
                        // Add other types as needed
                    }
                }
            }
            
            // Set flags if specified
            if (testIntentConfig.has("flags")) {
                testIntent.setFlags(testIntentConfig.getInt("flags"));
            }
            
            // Process the intent through our rules
            String sourcePackage = moduleContext.getPackageName();
            if (testIntentConfig.has("sourcePackage")) {
                sourcePackage = testIntentConfig.getString("sourcePackage");
            }
            
            // Log the original intent
            logIntent(testIntent, sourcePackage, "TEST_ORIGINAL", null);
            
            // Process through our rules
            Intent processedIntent = processIntent(testIntent, sourcePackage);
            
            // Check the result
            if (processedIntent == null) {
                return "Intent was blocked by rules";
            } else if (processedIntent != testIntent) {
                logIntent(processedIntent, sourcePackage, "TEST_MODIFIED", null);
                return "Intent was modified by rules";
            }
            
            // If we're supposed to actually send the intent
            if (testIntentConfig.has("send") && testIntentConfig.getBoolean("send")) {
                try {
                    if (moduleContext != null) {
                        if (testIntentConfig.has("startActivity") && testIntentConfig.getBoolean("startActivity")) {
                            processedIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            moduleContext.startActivity(processedIntent);
                            return "Test intent was sent via startActivity";
                        } else if (testIntentConfig.has("startService") && testIntentConfig.getBoolean("startService")) {
                            ComponentName result = moduleContext.startService(processedIntent);
                            return "Test intent was sent via startService: " + (result != null ? "success" : "failed");
                        } else if (testIntentConfig.has("sendBroadcast") && testIntentConfig.getBoolean("sendBroadcast")) {
                            moduleContext.sendBroadcast(processedIntent);
                            return "Test intent was sent via sendBroadcast";
                        }
                    } else {
                        return "Cannot send intent: Module context is null";
                    }
                } catch (Exception e) {
                    return "Error sending intent: " + e.getMessage();
                }
            }
            
            return "Test intent processed successfully (no actual sending)";
            
        } catch (Exception e) {
            LoggingHelper.error(TAG, "Error in sendTestIntent: " + e.getMessage());
            return "Error: " + e.getMessage();
        }
    }
} 