package com.wobbz.framework.analytics;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.wobbz.framework.development.LoggingHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manager for tracking performance metrics and usage analytics.
 */
public class AnalyticsManager {
    private static final String TAG = "AnalyticsManager";
    private static final String PREFS_NAME = "analytics_config";
    private static final String KEY_ANALYTICS_ENABLED = "analytics_enabled";
    private static final String KEY_CRASH_REPORTING_ENABLED = "crash_reporting_enabled";
    private static final String METRICS_DIR = "metrics";
    private static final String CRASHES_DIR = "crashes";
    
    private static AnalyticsManager sInstance;
    
    private Context mContext;
    private boolean mAnalyticsEnabled = false;
    private boolean mCrashReportingEnabled = false;
    private final ConcurrentHashMap<String, HookMetric> mHookMetrics = new ConcurrentHashMap<>();
    private final List<PerformanceListener> mListeners = new ArrayList<>();
    private final ScheduledExecutorService mExecutor = Executors.newSingleThreadScheduledExecutor();
    private final AtomicLong mStartTime = new AtomicLong(0);
    private final Map<String, Long> mMemoryBaseline = new HashMap<>();
    
    // Device info
    private final String mDeviceInfo;
    
    /**
     * Private constructor for singleton pattern.
     */
    private AnalyticsManager(Context context) {
        mContext = context.getApplicationContext();
        loadConfig();
        
        // Create metrics directory
        File metricsDir = new File(mContext.getFilesDir(), METRICS_DIR);
        if (!metricsDir.exists()) {
            metricsDir.mkdirs();
        }
        
        // Create crashes directory
        File crashesDir = new File(mContext.getFilesDir(), CRASHES_DIR);
        if (!crashesDir.exists()) {
            crashesDir.mkdirs();
        }
        
        // Build device info string
        mDeviceInfo = Build.MANUFACTURER + "," + 
                      Build.MODEL + "," + 
                      Build.VERSION.SDK_INT + "," + 
                      Build.VERSION.RELEASE;
        
        // Start periodic metrics collection
        if (mAnalyticsEnabled) {
            mExecutor.scheduleAtFixedRate(this::collectMetrics, 30, 30, TimeUnit.MINUTES);
        }
        
        // Initialize crash handler
        if (mCrashReportingEnabled) {
            Thread.setDefaultUncaughtExceptionHandler(new CrashHandler());
        }
        
        mStartTime.set(SystemClock.elapsedRealtime());
    }
    
    /**
     * Get the singleton instance.
     */
    public static synchronized AnalyticsManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new AnalyticsManager(context);
        }
        return sInstance;
    }
    
    /**
     * Load configuration from SharedPreferences.
     */
    private void loadConfig() {
        SharedPreferences prefs = mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        mAnalyticsEnabled = prefs.getBoolean(KEY_ANALYTICS_ENABLED, false);
        mCrashReportingEnabled = prefs.getBoolean(KEY_CRASH_REPORTING_ENABLED, false);
    }
    
    /**
     * Save configuration to SharedPreferences.
     */
    private void saveConfig() {
        SharedPreferences prefs = mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_ANALYTICS_ENABLED, mAnalyticsEnabled);
        editor.putBoolean(KEY_CRASH_REPORTING_ENABLED, mCrashReportingEnabled);
        editor.apply();
    }
    
    /**
     * Enable or disable analytics collection.
     */
    public void setAnalyticsEnabled(boolean enabled) {
        mAnalyticsEnabled = enabled;
        saveConfig();
        
        if (enabled) {
            mExecutor.scheduleAtFixedRate(this::collectMetrics, 30, 30, TimeUnit.MINUTES);
        }
    }
    
    /**
     * Enable or disable crash reporting.
     */
    public void setCrashReportingEnabled(boolean enabled) {
        mCrashReportingEnabled = enabled;
        saveConfig();
        
        if (enabled && Thread.getDefaultUncaughtExceptionHandler() != null && 
                !(Thread.getDefaultUncaughtExceptionHandler() instanceof CrashHandler)) {
            Thread.setDefaultUncaughtExceptionHandler(new CrashHandler());
        }
    }
    
    /**
     * Check if analytics collection is enabled.
     */
    public boolean isAnalyticsEnabled() {
        return mAnalyticsEnabled;
    }
    
    /**
     * Check if crash reporting is enabled.
     */
    public boolean isCrashReportingEnabled() {
        return mCrashReportingEnabled;
    }
    
    /**
     * Track the start of a hook operation.
     * 
     * @param hookId Unique identifier for the hook.
     * @param moduleId The module ID.
     * @param targetPackage The target package.
     * @return A tracking ID to pass to {@link #trackHookEnd}.
     */
    public long trackHookStart(String hookId, String moduleId, String targetPackage) {
        if (!mAnalyticsEnabled) {
            return 0;
        }
        
        String key = moduleId + ":" + hookId;
        HookMetric metric = mHookMetrics.get(key);
        if (metric == null) {
            metric = new HookMetric(hookId, moduleId, targetPackage);
            mHookMetrics.put(key, metric);
        }
        
        long trackingId = SystemClock.elapsedRealtimeNanos();
        metric.beginExecution(trackingId);
        return trackingId;
    }
    
    /**
     * Track the end of a hook operation.
     * 
     * @param trackingId The tracking ID returned by {@link #trackHookStart}.
     * @param success Whether the hook execution was successful.
     */
    public void trackHookEnd(long trackingId, boolean success) {
        if (!mAnalyticsEnabled || trackingId == 0) {
            return;
        }
        
        for (HookMetric metric : mHookMetrics.values()) {
            if (metric.endExecution(trackingId, success)) {
                // Notify listeners
                for (PerformanceListener listener : mListeners) {
                    listener.onHookPerformance(metric.getHookId(), metric.getModuleId(), 
                            metric.getTargetPackage(), metric.getLastExecutionTime(), success);
                }
                break;
            }
        }
    }
    
    /**
     * Track memory usage for a module.
     * 
     * @param moduleId The module ID.
     * @param memoryUsage Memory usage in bytes.
     */
    public void trackMemoryUsage(String moduleId, long memoryUsage) {
        if (!mAnalyticsEnabled) {
            return;
        }
        
        // Calculate delta from baseline if available
        Long baseline = mMemoryBaseline.get(moduleId);
        long delta = baseline != null ? memoryUsage - baseline : 0;
        
        // If no baseline, set this as baseline
        if (baseline == null) {
            mMemoryBaseline.put(moduleId, memoryUsage);
            delta = 0;
        }
        
        // Notify listeners
        for (PerformanceListener listener : mListeners) {
            listener.onMemoryUsage(moduleId, memoryUsage, delta);
        }
    }
    
    /**
     * Add a listener for performance events.
     */
    public void addListener(PerformanceListener listener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }
    
    /**
     * Remove a listener for performance events.
     */
    public void removeListener(PerformanceListener listener) {
        mListeners.remove(listener);
    }
    
    /**
     * Collect metrics and write to disk.
     */
    private void collectMetrics() {
        if (!mAnalyticsEnabled) {
            return;
        }
        
        try {
            JsonObject metrics = new JsonObject();
            
            // Add device info
            metrics.addProperty("device", mDeviceInfo);
            
            // Add uptime
            long uptime = SystemClock.elapsedRealtime() - mStartTime.get();
            metrics.addProperty("uptime", uptime);
            
            // Add hook metrics
            JsonObject hooks = new JsonObject();
            for (HookMetric metric : mHookMetrics.values()) {
                JsonObject hookData = new JsonObject();
                hookData.addProperty("module", metric.getModuleId());
                hookData.addProperty("package", metric.getTargetPackage());
                hookData.addProperty("avgTime", metric.getAverageExecutionTime());
                hookData.addProperty("maxTime", metric.getMaxExecutionTime());
                hookData.addProperty("count", metric.getExecutionCount());
                hookData.addProperty("successRate", metric.getSuccessRate());
                
                hooks.add(metric.getHookId(), hookData);
            }
            metrics.add("hooks", hooks);
            
            // Add memory metrics
            JsonObject memory = new JsonObject();
            for (Map.Entry<String, Long> entry : mMemoryBaseline.entrySet()) {
                memory.addProperty(entry.getKey(), entry.getValue());
            }
            metrics.add("memory", memory);
            
            // Write to file
            String timestamp = String.valueOf(System.currentTimeMillis());
            File metricsFile = new File(new File(mContext.getFilesDir(), METRICS_DIR), 
                    "metrics_" + timestamp + ".json");
            
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String json = gson.toJson(metrics);
            
            try (FileOutputStream fos = new FileOutputStream(metricsFile)) {
                fos.write(json.getBytes());
            }
            
            LoggingHelper.info(TAG, "Metrics collected and written to " + metricsFile.getAbsolutePath());
            
        } catch (IOException e) {
            LoggingHelper.error(TAG, "Error collecting metrics", e);
        }
    }
    
    /**
     * Crash handler for reporting uncaught exceptions.
     */
    private class CrashHandler implements Thread.UncaughtExceptionHandler {
        private final Thread.UncaughtExceptionHandler mDefaultHandler;
        
        CrashHandler() {
            mDefaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        }
        
        @Override
        public void uncaughtException(Thread thread, Throwable ex) {
            try {
                // Write crash report
                String timestamp = String.valueOf(System.currentTimeMillis());
                File crashFile = new File(new File(mContext.getFilesDir(), CRASHES_DIR), 
                        "crash_" + timestamp + ".txt");
                
                try (FileOutputStream fos = new FileOutputStream(crashFile)) {
                    // Write thread info
                    String threadInfo = "Thread: " + thread.getName() + " (id: " + thread.getId() + ")\n";
                    fos.write(threadInfo.getBytes());
                    
                    // Write device info
                    String deviceInfo = "Device: " + mDeviceInfo + "\n";
                    fos.write(deviceInfo.getBytes());
                    
                    // Write uptime
                    long uptime = SystemClock.elapsedRealtime() - mStartTime.get();
                    String uptimeInfo = "Uptime: " + uptime + "ms\n";
                    fos.write(uptimeInfo.getBytes());
                    
                    // Write exception
                    fos.write("Exception:\n".getBytes());
                    for (StackTraceElement element : ex.getStackTrace()) {
                        fos.write(("  " + element.toString() + "\n").getBytes());
                    }
                }
                
                LoggingHelper.error(TAG, "Crash report written to " + crashFile.getAbsolutePath());
                
            } catch (IOException e) {
                LoggingHelper.error(TAG, "Error writing crash report", e);
            }
            
            // Pass to default handler
            if (mDefaultHandler != null) {
                mDefaultHandler.uncaughtException(thread, ex);
            }
        }
    }
    
    /**
     * Listener for performance events.
     */
    public interface PerformanceListener {
        /**
         * Called when a hook's performance is measured.
         */
        void onHookPerformance(String hookId, String moduleId, String targetPackage, 
                               long executionTime, boolean success);
        
        /**
         * Called when memory usage is tracked.
         */
        void onMemoryUsage(String moduleId, long memoryUsage, long memoryDelta);
    }
} 