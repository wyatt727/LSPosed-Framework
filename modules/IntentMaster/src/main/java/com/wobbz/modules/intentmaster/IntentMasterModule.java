package com.wobbz.modules.intentmaster;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;

import com.wobbz.framework.IModulePlugin;
import com.wobbz.framework.development.LoggingHelper;
import com.wobbz.framework.ui.models.SettingsField;
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
public class IntentMasterModule implements IModulePlugin {
    private static final String TAG = "IntentMasterModule";
    private static final int MAX_LOGS = 100;
    
    // Mock of SettingsHelper for testing
    private static class MockSettingsHelper {
        private final Map<String, Object> settings = new HashMap<>();
        private final List<BiConsumer<String, Object>> listeners = new ArrayList<>();
        
        public boolean getBoolean(String key, boolean defaultValue) {
            return settings.containsKey(key) ? (Boolean)settings.get(key) : defaultValue;
        }
        
        public String getString(String key, String defaultValue) {
            return settings.containsKey(key) ? (String)settings.get(key) : defaultValue;
        }
        
        public void putString(String key, String value) {
            settings.put(key, value);
            notifyListeners(key, value);
        }
        
        public void registerChangeListener(BiConsumer<String, Object> listener) {
            listeners.add(listener);
        }
        
        private void notifyListeners(String key, Object value) {
            for (BiConsumer<String, Object> listener : listeners) {
                listener.accept(key, value);
            }
        }
        
        public static MockSettingsHelper getInstance(String tag) {
            return new MockSettingsHelper();
        }
    }
    
    private MockSettingsHelper settings;
    private final List<IntentRule> rules = new ArrayList<>();
    private final List<IntentLog> logs = new CopyOnWriteArrayList<>();
    private boolean interceptionEnabled = true;
    private boolean logAllIntents = true;
    private List<String> targetApps = new ArrayList<>();
    private XposedInterface xposedInterface;
    
    public IntentMasterModule() {
        // Will be initialized in initialize()
    }
    
    /**
     * Initialize the module with context.
     */
    public void initialize(Context context, XposedInterface xposedInterface) {
        this.xposedInterface = xposedInterface;
        this.settings = MockSettingsHelper.getInstance(TAG);
        
        // Register settings change listener
        settings.registerChangeListener(this::onSettingsChanged);
        
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
        String targetAppsJson = settings.getString("targetApps", "[]");
        try {
            JSONArray appsArray = new JSONArray(targetAppsJson);
            targetApps = new ArrayList<>(appsArray.length());
            for (int i = 0; i < appsArray.length(); i++) {
                targetApps.add(appsArray.getString(i));
            }
        } catch (JSONException e) {
            LoggingHelper.error(TAG, "Failed to parse target apps: " + e.getMessage());
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
        xposedInterface.hook(startActivityMethod, ActivityStartActivityHook.class);
        
        // Hook Activity.startActivity(Intent, Bundle)
        Method startActivityWithBundleMethod = activityClass.getDeclaredMethod("startActivity", Intent.class, Bundle.class);
        xposedInterface.hook(startActivityWithBundleMethod, ActivityStartActivityWithBundleHook.class);
        
        // Get the Context class
        Class<?> contextClass = classLoader.loadClass("android.content.Context");
        
        // Hook Context.startActivity(Intent)
        Method contextStartActivityMethod = contextClass.getDeclaredMethod("startActivity", Intent.class);
        xposedInterface.hook(contextStartActivityMethod, ContextStartActivityHook.class);
        
        // Hook Context.startActivity(Intent, Bundle)
        Method contextStartActivityWithBundleMethod = contextClass.getDeclaredMethod("startActivity", Intent.class, Bundle.class);
        xposedInterface.hook(contextStartActivityWithBundleMethod, ContextStartActivityWithBundleHook.class);
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
        // Get the Context class
        Class<?> contextClass = classLoader.loadClass("android.content.Context");
        
        // Hook Context.startService(Intent)
        Method startServiceMethod = contextClass.getDeclaredMethod("startService", Intent.class);
        xposedInterface.hook(startServiceMethod, StartServiceHook.class);
        
        // Hook Context.startForegroundService(Intent) for Android O+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Method startForegroundServiceMethod = contextClass.getDeclaredMethod("startForegroundService", Intent.class);
            xposedInterface.hook(startForegroundServiceMethod, StartForegroundServiceHook.class);
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
        // Get the Context class
        Class<?> contextClass = classLoader.loadClass("android.content.Context");
        
        // Hook Context.sendBroadcast(Intent)
        Method sendBroadcastMethod = contextClass.getDeclaredMethod("sendBroadcast", Intent.class);
        xposedInterface.hook(sendBroadcastMethod, SendBroadcastHook.class);
        
        // Hook Context.sendBroadcast(Intent, String)
        Method sendBroadcastWithPermMethod = contextClass.getDeclaredMethod("sendBroadcast", Intent.class, String.class);
        xposedInterface.hook(sendBroadcastWithPermMethod, SendBroadcastWithPermHook.class);
        
        // Hook Context.sendOrderedBroadcast
        Method sendOrderedBroadcastMethod = contextClass.getDeclaredMethod("sendOrderedBroadcast", 
                Intent.class, String.class);
        xposedInterface.hook(sendOrderedBroadcastMethod, SendOrderedBroadcastHook.class);
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
        // Get the Context class
        Class<?> contextClass = classLoader.loadClass("android.content.Context");
        
        // Hook Context.bindService methods (for different Android versions)
        for (Method method : contextClass.getDeclaredMethods()) {
            if (method.getName().equals("bindService") && method.getParameterTypes().length >= 2 
                    && method.getParameterTypes()[0] == Intent.class
                    && method.getParameterTypes()[1] == ServiceConnection.class) {
                xposedInterface.hook(method, BindServiceHook.class);
            }
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
} 