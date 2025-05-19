package com.wobbz.modules.intentmaster;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;

import com.wobbz.framework.FeatureManager;
import com.wobbz.framework.LoggingHelper;
import com.wobbz.framework.SettingsHelper;
import com.wobbz.framework.annotations.ModuleEntry;
import com.wobbz.framework.models.SettingsField;
import com.wobbz.modules.intentmaster.model.IntentLog;
import com.wobbz.modules.intentmaster.model.IntentRule;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * IntentMasterModule - Intercepts, modifies, redirects, and logs intents between applications.
 * 
 * This module hooks into key Android methods responsible for intent handling and applies
 * rules defined by the user for matching and modifying intents.
 */
@ModuleEntry
public class IntentMasterModule {
    private static final String TAG = "IntentMasterModule";
    private static final int MAX_LOGS = 100;
    
    private final SettingsHelper settings;
    private final List<IntentRule> rules = new ArrayList<>();
    private final List<IntentLog> logs = new CopyOnWriteArrayList<>();
    private boolean interceptionEnabled = true;
    private boolean logAllIntents = true;
    private List<String> targetApps = new ArrayList<>();
    
    public IntentMasterModule() {
        settings = SettingsHelper.getInstance(TAG);
        
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
     * Entry point for the module.
     * Sets up hooks for intent-related methods.
     */
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        String packageName = lpparam.packageName;
        
        // Only hook target packages
        if (!isTargetPackage(packageName)) {
            return;
        }
        
        LoggingHelper.debug(TAG, "Hooking " + packageName);
        
        // Hook Activity.startActivity methods
        hookStartActivity(lpparam);
        
        // Hook Context.startService methods
        hookStartService(lpparam);
        
        // Hook Context.sendBroadcast methods
        hookSendBroadcast(lpparam);
        
        // Hook Context.bindService methods
        hookBindService(lpparam);
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
    
    /**
     * Hooks Activity.startActivity methods.
     */
    private void hookStartActivity(XC_LoadPackage.LoadPackageParam lpparam) {
        // Hook Activity.startActivity(Intent)
        XposedHelpers.findAndHookMethod(Activity.class, "startActivity", 
                Intent.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (!interceptionEnabled) return;
                        
                        Activity activity = (Activity) param.thisObject;
                        Intent intent = (Intent) param.args[0];
                        
                        Intent modifiedIntent = processIntent(intent, activity.getPackageName());
                        if (modifiedIntent == null) {
                            // Block the intent
                            param.setResult(null);
                        } else if (modifiedIntent != intent) {
                            // Replace with modified intent
                            param.args[0] = modifiedIntent;
                        }
                    }
                });
        
        // Hook Activity.startActivity(Intent, Bundle)
        XposedHelpers.findAndHookMethod(Activity.class, "startActivity", 
                Intent.class, Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (!interceptionEnabled) return;
                        
                        Activity activity = (Activity) param.thisObject;
                        Intent intent = (Intent) param.args[0];
                        
                        Intent modifiedIntent = processIntent(intent, activity.getPackageName());
                        if (modifiedIntent == null) {
                            // Block the intent
                            param.setResult(null);
                        } else if (modifiedIntent != intent) {
                            // Replace with modified intent
                            param.args[0] = modifiedIntent;
                        }
                    }
                });
        
        // Hook Context.startActivity methods too
        try {
            XposedHelpers.findAndHookMethod(Context.class, "startActivity", 
                    Intent.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (!interceptionEnabled) return;
                            
                            Context context = (Context) param.thisObject;
                            Intent intent = (Intent) param.args[0];
                            
                            Intent modifiedIntent = processIntent(intent, context.getPackageName());
                            if (modifiedIntent == null) {
                                // Block the intent
                                param.setResult(null);
                            } else if (modifiedIntent != intent) {
                                // Replace with modified intent
                                param.args[0] = modifiedIntent;
                            }
                        }
                    });
            
            // Newer versions of Android have a version with options bundle
            XposedHelpers.findAndHookMethod(Context.class, "startActivity", 
                    Intent.class, Bundle.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (!interceptionEnabled) return;
                            
                            Context context = (Context) param.thisObject;
                            Intent intent = (Intent) param.args[0];
                            
                            Intent modifiedIntent = processIntent(intent, context.getPackageName());
                            if (modifiedIntent == null) {
                                // Block the intent
                                param.setResult(null);
                            } else if (modifiedIntent != intent) {
                                // Replace with modified intent
                                param.args[0] = modifiedIntent;
                            }
                        }
                    });
        } catch (Error | Exception e) {
            LoggingHelper.error(TAG, "Failed to hook Context.startActivity: " + e.getMessage());
        }
    }
    
    /**
     * Hooks Context.startService and startForegroundService methods.
     */
    private void hookStartService(XC_LoadPackage.LoadPackageParam lpparam) {
        // Hook Context.startService(Intent)
        try {
            XposedHelpers.findAndHookMethod(Context.class, "startService", 
                    Intent.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (!interceptionEnabled) return;
                            
                            Context context = (Context) param.thisObject;
                            Intent intent = (Intent) param.args[0];
                            
                            Intent modifiedIntent = processIntent(intent, context.getPackageName());
                            if (modifiedIntent == null) {
                                // Block the intent
                                param.setResult(null);
                            } else if (modifiedIntent != intent) {
                                // Replace with modified intent
                                param.args[0] = modifiedIntent;
                            }
                        }
                    });
        } catch (Error | Exception e) {
            LoggingHelper.error(TAG, "Failed to hook Context.startService: " + e.getMessage());
        }
        
        // Hook Context.startForegroundService(Intent) for Android O+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                XposedHelpers.findAndHookMethod(Context.class, "startForegroundService", 
                        Intent.class, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                if (!interceptionEnabled) return;
                                
                                Context context = (Context) param.thisObject;
                                Intent intent = (Intent) param.args[0];
                                
                                Intent modifiedIntent = processIntent(intent, context.getPackageName());
                                if (modifiedIntent == null) {
                                    // Block the intent
                                    param.setResult(null);
                                } else if (modifiedIntent != intent) {
                                    // Replace with modified intent
                                    param.args[0] = modifiedIntent;
                                }
                            }
                        });
            } catch (Error | Exception e) {
                LoggingHelper.error(TAG, "Failed to hook Context.startForegroundService: " + e.getMessage());
            }
        }
    }
    
    /**
     * Hooks Context.sendBroadcast methods.
     */
    private void hookSendBroadcast(XC_LoadPackage.LoadPackageParam lpparam) {
        // Hook Context.sendBroadcast(Intent)
        try {
            XposedHelpers.findAndHookMethod(Context.class, "sendBroadcast", 
                    Intent.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (!interceptionEnabled) return;
                            
                            Context context = (Context) param.thisObject;
                            Intent intent = (Intent) param.args[0];
                            
                            Intent modifiedIntent = processIntent(intent, context.getPackageName());
                            if (modifiedIntent == null) {
                                // Block the intent
                                param.setResult(null);
                            } else if (modifiedIntent != intent) {
                                // Replace with modified intent
                                param.args[0] = modifiedIntent;
                            }
                        }
                    });
        } catch (Error | Exception e) {
            LoggingHelper.error(TAG, "Failed to hook Context.sendBroadcast: " + e.getMessage());
        }
        
        // Hook Context.sendBroadcast(Intent, String)
        try {
            XposedHelpers.findAndHookMethod(Context.class, "sendBroadcast", 
                    Intent.class, String.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (!interceptionEnabled) return;
                            
                            Context context = (Context) param.thisObject;
                            Intent intent = (Intent) param.args[0];
                            
                            Intent modifiedIntent = processIntent(intent, context.getPackageName());
                            if (modifiedIntent == null) {
                                // Block the intent
                                param.setResult(null);
                            } else if (modifiedIntent != intent) {
                                // Replace with modified intent
                                param.args[0] = modifiedIntent;
                            }
                        }
                    });
        } catch (Error | Exception e) {
            LoggingHelper.error(TAG, "Failed to hook Context.sendBroadcast with permission: " + e.getMessage());
        }
        
        // Hook Context.sendOrderedBroadcast methods
        try {
            XposedHelpers.findAndHookMethod(Context.class, "sendOrderedBroadcast", 
                    Intent.class, String.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (!interceptionEnabled) return;
                            
                            Context context = (Context) param.thisObject;
                            Intent intent = (Intent) param.args[0];
                            
                            Intent modifiedIntent = processIntent(intent, context.getPackageName());
                            if (modifiedIntent == null) {
                                // Block the intent
                                param.setResult(null);
                            } else if (modifiedIntent != intent) {
                                // Replace with modified intent
                                param.args[0] = modifiedIntent;
                            }
                        }
                    });
        } catch (Error | Exception e) {
            LoggingHelper.error(TAG, "Failed to hook Context.sendOrderedBroadcast: " + e.getMessage());
        }
    }
    
    /**
     * Hooks Context.bindService methods.
     */
    private void hookBindService(XC_LoadPackage.LoadPackageParam lpparam) {
        // Hook Context.bindService
        try {
            // Different Android versions have different method signatures
            Method[] methods = Context.class.getDeclaredMethods();
            for (Method method : methods) {
                if ("bindService".equals(method.getName())) {
                    Class<?>[] paramTypes = method.getParameterTypes();
                    if (paramTypes.length >= 3 && 
                            Intent.class.isAssignableFrom(paramTypes[0]) &&
                            ServiceConnection.class.isAssignableFrom(paramTypes[1])) {
                        
                        XposedBridge.hookMethod(method, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                if (!interceptionEnabled) return;
                                
                                Context context = (Context) param.thisObject;
                                Intent intent = (Intent) param.args[0];
                                
                                Intent modifiedIntent = processIntent(intent, context.getPackageName());
                                if (modifiedIntent == null) {
                                    // Block the intent - set result to false for bindService
                                    param.setResult(false);
                                } else if (modifiedIntent != intent) {
                                    // Replace with modified intent
                                    param.args[0] = modifiedIntent;
                                }
                            }
                        });
                    }
                }
            }
        } catch (Error | Exception e) {
            LoggingHelper.error(TAG, "Failed to hook Context.bindService: " + e.getMessage());
        }
    }
    
    /**
     * Processes an intent according to the defined rules.
     * 
     * @param intent The original intent
     * @param sourcePackage The package that created the intent
     * @return The modified intent, or null if the intent should be blocked
     */
    private Intent processIntent(Intent intent, String sourcePackage) {
        if (intent == null) {
            LoggingHelper.warning(TAG, "processIntent called with null intent from " + sourcePackage);
            return null;
        }
        
        String action = intent.getAction() != null ? intent.getAction() : "(no action)";
        String dataString = intent.getDataString() != null ? intent.getDataString() : "(no data)";
        String component = intent.getComponent() != null ? intent.getComponent().flattenToString() : "(no component)";

        String logMessage = String.format("Processing intent: Action=%s, Data=%s, Component=%s, Source=%s", 
                                          action, dataString, component, sourcePackage);
        LoggingHelper.info(TAG, logMessage);

        Intent originalIntent = new Intent(intent); // Clone for logging original state
        
        // Log the intent if logging is enabled
        if (logAllIntents) {
            IntentLog log = IntentLog.fromIntent(intent, sourcePackage);
            logs.add(log);
            
            // Trim logs if they exceed the maximum
            if (logs.size() > MAX_LOGS) {
                logs.subList(0, logs.size() - MAX_LOGS).clear();
            }
            
            saveLogs();
        }
        
        // Skip processing if interception is disabled
        if (!interceptionEnabled) {
            return intent;
        }
        
        // Find the first matching rule
        for (IntentRule rule : rules) {
            if (rule.matches(intent, sourcePackage)) {
                LoggingHelper.debug(TAG, "Found matching rule: " + rule.getName());
                
                // Update the log with the applied rule info
                if (logAllIntents && !logs.isEmpty()) {
                    IntentLog latestLog = logs.get(logs.size() - 1);
                    latestLog.setAppliedRule(rule);
                    saveLogs();
                }
                
                // Apply the rule's action
                return rule.applyModification(intent);
            }
        }
        
        // No matching rule found, return the original intent
        return intent;
    }
    
    private void logIntent(Intent intent, String sourcePackage, String status, String ruleMatched) {
        if (!logAllIntents && !"Blocked".equals(status) && !"Modified".equals(status) && !"Redirected".equals(status) && !"Logged (Rule)".equals(status)) {
            return; // Don't log if logAllIntents is false and it wasn't a rule-triggered event
        }
        if (intent == null) return;

        LoggingHelper.debug(TAG, String.format("Logging intent: Action=%s, Status=%s, Rule=%s", 
                                         intent.getAction(), status, ruleMatched != null ? ruleMatched : "N/A"));

        IntentLog logEntry = new IntentLog(intent, sourcePackage, status, ruleMatched);
        logs.add(logEntry);
        
        // Trim logs if they exceed the maximum
        if (logs.size() > MAX_LOGS) {
            logs.subList(0, logs.size() - MAX_LOGS).clear();
        }
        
        saveLogs();
    }
} 