package com.wobbz.framework.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wobbz.framework.development.LoggingHelper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Manages advanced security features for the Wobbz LSPosed Modular Framework,
 * including a configurable firewall, network activity monitoring, and privacy protection
 * mechanisms like domain blocking.
 *
 * <p>This class operates as a singleton and provides interfaces for modules to:
 * <ul>
 *     <li>Define and enforce firewall rules for applications.</li>
 *     <li>Block or allow network access to specific domains using regular expressions.</li>
 *     <li>Monitor network data usage per application.</li>
 *     <li>Receive notifications about security events and configuration changes.</li>
 * </ul>
 * </p>
 *
 * <p>Rules and configurations are persisted in a JSON file ({@code security_rules.json})
 * within the application's private file storage. Firewall activity can be logged to
 * {@code firewall.log}.</p>
 *
 * <p><b>Note:</b> Effective network interception and control typically require
 * this manager to be used in conjunction with Xposed hooks placed in appropriate
 * Android framework or application networking methods.</p>
 *
 * <p>Usage:
 * <pre>{@code
 * SecurityManager sm = SecurityManager.getInstance(context);
 * sm.setFirewallEnabled(true);
 * sm.addDomainToBlocklist(".*\\.doubleclick\\.net");
 * sm.addFirewallRule("com.example.app", new SecurityManager.FirewallRule(SecurityManager.RULE_TYPE_BLOCK, "*", 0, 0, SecurityManager.PROTO_ALL));
 * }</pre>
 * </p>
 */
public class SecurityManager {
    private static final String TAG = "WobbzSecurityManager"; // Renamed for clarity
    private static final String RULES_FILE_NAME = "security_rules.json";
    private static final String SECURITY_DIR_NAME = "security";
    private static final String FIREWALL_LOG_FILE_NAME = "firewall.log";
    private static final int MAX_FIREWALL_LOG_ENTRIES = 1000; // Renamed for clarity
    private static final long DATA_USAGE_COLLECTION_INTERVAL_MINUTES = 5;
    private static final long LOG_ROTATION_INTERVAL_HOURS = 1;

    /** Rule type: Allow the connection. */
    public static final int RULE_TYPE_ALLOW = 0;
    /** Rule type: Block the connection. */
    public static final int RULE_TYPE_BLOCK = 1;
    /** Rule type: Log the connection and continue processing rules (effectively allows unless a block rule matches later). */
    public static final int RULE_TYPE_LOG = 2;

    /** Network protocol: TCP. */
    public static final int PROTO_TCP = 0;
    /** Network protocol: UDP. */
    public static final int PROTO_UDP = 1;
    /** Network protocol: ICMP. */
    public static final int PROTO_ICMP = 2;
    /** Network protocol: Matches all protocols. */
    public static final int PROTO_ALL = 3;

    private static volatile SecurityManager sInstance; // Added volatile for thread-safe double-checked locking

    private final Context mContext;
    private final File mSecurityDir; // Store File object for security directory

    // Rule storage
    private final Set<Pattern> mDomainBlocklist = new HashSet<>();
    private final Set<Pattern> mDomainAllowlist = new HashSet<>();
    private final Map<String, List<FirewallRule>> mAppRules = new ConcurrentHashMap<>(); // For thread-safe app rule modifications
    private final Set<String> mNetworkRestrictedApps = new HashSet<>(); // ConcurrentHashSet could be used if modifications are very frequent

    // Monitoring and Logging
    private final Map<String, Long> mDataUsageMap = new ConcurrentHashMap<>();
    private final List<String> mFirewallLog = new ArrayList<>(); // Synchronized access via synchronized(mFirewallLog)
    private final List<SecurityListener> mListeners = new CopyOnWriteArrayList<>(); // Thread-safe for listener management
    private final ScheduledExecutorService mExecutor = Executors.newScheduledThreadPool(2);
    private final Gson mGson = new GsonBuilder().setPrettyPrinting().create();

    // Feature enable/disable flags
    private boolean mFirewallEnabled = false;
    private boolean mNetworkMonitoringEnabled = false;
    private boolean mPrivacyProtectionEnabled = false; // This will now control domain lists

    /**
     * Private constructor for singleton pattern. Initializes security directory,
     * loads rules, and schedules periodic tasks.
     *
     * @param context The application context.
     */
    private SecurityManager(Context context) {
        mContext = context.getApplicationContext();
        mSecurityDir = new File(mContext.getFilesDir(), SECURITY_DIR_NAME);

        if (!mSecurityDir.exists()) {
            if (!mSecurityDir.mkdirs()) {
                LoggingHelper.logError(TAG, "Failed to create security directory: " + mSecurityDir.getAbsolutePath());
            }
        }

        loadRules();

        mExecutor.scheduleAtFixedRate(this::collectDataUsage, DATA_USAGE_COLLECTION_INTERVAL_MINUTES, DATA_USAGE_COLLECTION_INTERVAL_MINUTES, TimeUnit.MINUTES);
        mExecutor.scheduleAtFixedRate(this::rotateLogFile, LOG_ROTATION_INTERVAL_HOURS, LOG_ROTATION_INTERVAL_HOURS, TimeUnit.HOURS);
        LoggingHelper.logInfo(TAG, "SecurityManager initialized. Firewall: " + mFirewallEnabled + ", Monitoring: " + mNetworkMonitoringEnabled + ", Privacy: " + mPrivacyProtectionEnabled);
    }

    /**
     * Gets the singleton instance of the {@code SecurityManager}.
     * Uses double-checked locking for thread-safe lazy initialization.
     *
     * @param context The application context, used for initialization if the instance doesn't exist yet.
     * @return The singleton {@code SecurityManager} instance.
     */
    public static SecurityManager getInstance(Context context) {
        if (sInstance == null) {
            synchronized (SecurityManager.class) {
                if (sInstance == null) {
                    sInstance = new SecurityManager(context);
                }
            }
        }
        return sInstance;
    }

    /**
     * Shuts down the SecurityManager, stopping scheduled tasks.
     * This is useful for cleanup in specific lifecycle scenarios or testing.
     */
    public void shutdown() {
        LoggingHelper.logInfo(TAG, "Shutting down SecurityManager...");
        mExecutor.shutdown();
        try {
            if (!mExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                mExecutor.shutdownNow();
                if (!mExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    LoggingHelper.logError(TAG, "Executor did not terminate.");
                }
            }
        } catch (InterruptedException ie) {
            mExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LoggingHelper.logInfo(TAG, "SecurityManager has been shut down.");
    }

    /**
     * Adds a {@link SecurityListener} to receive notifications about security events
     * and configuration changes.
     *
     * @param listener The listener to add. Ignored if null or already registered.
     */
    public void addListener(SecurityListener listener) {
        if (listener != null) {
            mListeners.addIfAbsent(listener);
        }
    }

    /**
     * Removes a previously registered {@link SecurityListener}.
     *
     * @param listener The listener to remove.
     */
    public void removeListener(SecurityListener listener) {
        mListeners.remove(listener);
    }

    /**
     * Enables or disables the firewall.
     *
     * @param enabled {@code true} to enable the firewall, {@code false} to disable.
     */
    public void setFirewallEnabled(boolean enabled) {
        if (mFirewallEnabled != enabled) {
            mFirewallEnabled = enabled;
            LoggingHelper.logInfo(TAG, "Firewall enabled state changed to: " + enabled);
            saveRules();
            notifyConfigChanged();
        }
    }

    /**
     * Checks if the firewall is currently enabled.
     *
     * @return {@code true} if the firewall is enabled, {@code false} otherwise.
     */
    public boolean isFirewallEnabled() {
        return mFirewallEnabled;
    }

    /**
     * Enables or disables network monitoring. This includes data usage tracking
     * and firewall event logging.
     *
     * @param enabled {@code true} to enable network monitoring, {@code false} to disable.
     */
    public void setNetworkMonitoringEnabled(boolean enabled) {
        if (mNetworkMonitoringEnabled != enabled) {
            mNetworkMonitoringEnabled = enabled;
            LoggingHelper.logInfo(TAG, "Network monitoring enabled state changed to: " + enabled);
            saveRules();
            notifyConfigChanged();
        }
    }

    /**
     * Checks if network monitoring is currently enabled.
     *
     * @return {@code true} if network monitoring is enabled, {@code false} otherwise.
     */
    public boolean isNetworkMonitoringEnabled() {
        return mNetworkMonitoringEnabled;
    }

    /**
     * Enables or disables privacy protection features, primarily controlling the
     * activation of domain blocklists and allowlists.
     *
     * @param enabled {@code true} to enable privacy protection, {@code false} to disable.
     */
    public void setPrivacyProtectionEnabled(boolean enabled) {
        if (mPrivacyProtectionEnabled != enabled) {
            mPrivacyProtectionEnabled = enabled;
            LoggingHelper.logInfo(TAG, "Privacy protection enabled state changed to: " + enabled);
            saveRules();
            notifyConfigChanged();
        }
    }

    /**
     * Checks if privacy protection features (e.g., domain blocking) are currently enabled.
     *
     * @return {@code true} if privacy protection is enabled, {@code false} otherwise.
     */
    public boolean isPrivacyProtectionEnabled() {
        return mPrivacyProtectionEnabled;
    }

    /**
     * Adds a domain pattern to the blocklist. Requires {@link #isPrivacyProtectionEnabled()}
     * to be effective.
     *
     * @param pattern A regular expression for the domains to block (e.g., ".*badsite\\.com").
     *                Invalid patterns will be logged and ignored.
     */
    public void addDomainToBlocklist(String pattern) {
        if (pattern == null || pattern.trim().isEmpty()) {
            LoggingHelper.logWarning(TAG, "Attempted to add null or empty pattern to blocklist.");
            return;
        }
        try {
            Pattern compiled = Pattern.compile(pattern);
            synchronized (mDomainBlocklist) {
                if (mDomainBlocklist.add(compiled)) {
                    LoggingHelper.logInfo(TAG, "Added domain pattern to blocklist: " + pattern);
                    saveRules();
                    notifyRulesChanged();
                } else {
                    LoggingHelper.logDebug(TAG, "Domain pattern already in blocklist: " + pattern);
                }
            }
        } catch (PatternSyntaxException e) {
            LoggingHelper.logError(TAG, "Invalid domain pattern for blocklist: " + pattern, e);
        }
    }

    /**
     * Removes a domain pattern from the blocklist.
     *
     * @param pattern The exact regular expression pattern that was previously added.
     */
    public void removeDomainFromBlocklist(String pattern) {
        if (pattern == null) return;
        synchronized (mDomainBlocklist) {
            if (mDomainBlocklist.removeIf(p -> p.pattern().equals(pattern))) {
                LoggingHelper.logInfo(TAG, "Removed domain pattern from blocklist: " + pattern);
                saveRules();
                notifyRulesChanged();
            }
        }
    }

    /**
     * Adds a domain pattern to the allowlist. An allowlist entry overrides any blocklist entry
     * for the same domain. Requires {@link #isPrivacyProtectionEnabled()} to be effective.
     *
     * @param pattern A regular expression for the domains to allow (e.g., ".*goodsite\\.com").
     *                Invalid patterns will be logged and ignored.
     */
    public void addDomainToAllowlist(String pattern) {
        if (pattern == null || pattern.trim().isEmpty()) {
            LoggingHelper.logWarning(TAG, "Attempted to add null or empty pattern to allowlist.");
            return;
        }
        try {
            Pattern compiled = Pattern.compile(pattern);
            synchronized (mDomainAllowlist) {
                if (mDomainAllowlist.add(compiled)) {
                    LoggingHelper.logInfo(TAG, "Added domain pattern to allowlist: " + pattern);
                    saveRules();
                    notifyRulesChanged();
                } else {
                    LoggingHelper.logDebug(TAG, "Domain pattern already in allowlist: " + pattern);
                }
            }
        } catch (PatternSyntaxException e) {
            LoggingHelper.logError(TAG, "Invalid domain pattern for allowlist: " + pattern, e);
        }
    }

    /**
     * Removes a domain pattern from the allowlist.
     *
     * @param pattern The exact regular expression pattern that was previously added.
     */
    public void removeDomainFromAllowlist(String pattern) {
        if (pattern == null) return;
        synchronized (mDomainAllowlist) {
            if (mDomainAllowlist.removeIf(p -> p.pattern().equals(pattern))) {
                LoggingHelper.logInfo(TAG, "Removed domain pattern from allowlist: " + pattern);
                saveRules();
                notifyRulesChanged();
            }
        }
    }

    /**
     * Adds a firewall rule for a specific application.
     *
     * @param packageName The package name of the application.
     * @param rule        The {@link FirewallRule} to add. Must not be null.
     */
    public void addFirewallRule(String packageName, FirewallRule rule) {
        if (packageName == null || packageName.trim().isEmpty() || rule == null) {
            LoggingHelper.logWarning(TAG, "Cannot add firewall rule with null/empty package name or null rule.");
            return;
        }
        mAppRules.computeIfAbsent(packageName, k -> new CopyOnWriteArrayList<>()).add(rule); // Use CopyOnWriteArrayList for rules list
        LoggingHelper.logInfo(TAG, "Added firewall rule for " + packageName + ": " + rule.toString());
        saveRules();
        notifyRulesChanged();
    }

    /**
     * Removes a firewall rule for an application at a specific index.
     *
     * @param packageName The package name of the application.
     * @param ruleIndex   The index of the rule to remove from the list of rules for this app.
     */
    public void removeFirewallRule(String packageName, int ruleIndex) {
        if (packageName == null || packageName.trim().isEmpty()) {
            LoggingHelper.logWarning(TAG, "Cannot remove firewall rule with null/empty package name.");
            return;
        }
        List<FirewallRule> rules = mAppRules.get(packageName);
        if (rules != null && ruleIndex >= 0 && ruleIndex < rules.size()) {
            FirewallRule removedRule = rules.remove(ruleIndex);
            LoggingHelper.logInfo(TAG, "Removed firewall rule for " + packageName + ": " + removedRule.toString());
            if (rules.isEmpty()) {
                mAppRules.remove(packageName); // Clean up empty rule list
            }
            saveRules();
            notifyRulesChanged();
        } else {
            LoggingHelper.logWarning(TAG, "Failed to remove firewall rule for " + packageName + " at index " + ruleIndex + ". Rules list size: " + (rules != null ? rules.size() : "N/A"));
        }
    }

    /**
     * Retrieves all firewall rules configured for a specific application.
     *
     * @param packageName The package name of the application.
     * @return A new {@code List} containing the firewall rules, or an empty list if no rules are defined for the app.
     */
    public List<FirewallRule> getFirewallRulesForApp(String packageName) {
        if (packageName == null) return new ArrayList<>();
        List<FirewallRule> rules = mAppRules.get(packageName);
        return rules != null ? new ArrayList<>(rules) : new ArrayList<>(); // Return a copy
    }

    /**
     * Sets whether an application's network access is globally restricted.
     * A restricted app will have all its network connections blocked by the firewall,
     * regardless of other rules, if the firewall is enabled.
     *
     * @param packageName The package name of the application.
     * @param restricted  {@code true} to restrict the app's network access, {@code false} to allow.
     */
    public void setAppNetworkRestricted(String packageName, boolean restricted) {
        if (packageName == null || packageName.trim().isEmpty()) {
            LoggingHelper.logWarning(TAG, "Cannot set network restriction with null/empty package name.");
            return;
        }
        boolean changed;
        synchronized (mNetworkRestrictedApps) { // Synchronize access to mNetworkRestrictedApps
            if (restricted) {
                changed = mNetworkRestrictedApps.add(packageName);
            } else {
                changed = mNetworkRestrictedApps.remove(packageName);
            }
        }
        if (changed) {
            LoggingHelper.logInfo(TAG, "Set network restriction for " + packageName + " to: " + restricted);
            saveRules();
            notifyRulesChanged();
        }
    }

    /**
     * Checks if an application's network access is globally restricted.
     *
     * @param packageName The package name of the application.
     * @return {@code true} if the app's network access is restricted, {@code false} otherwise.
     */
    public boolean isAppNetworkRestricted(String packageName) {
        if (packageName == null) return false;
        synchronized (mNetworkRestrictedApps) {
            return mNetworkRestrictedApps.contains(packageName);
        }
    }

    /**
     * Checks if a given domain is blocked according to the current privacy protection settings
     * (domain blocklist/allowlist). This check is only active if {@link #isPrivacyProtectionEnabled()} is true.
     *
     * @param domain The domain name to check (e.g., "www.example.com").
     * @return {@code true} if the domain matches a blocklist pattern and not an allowlist pattern,
     *         {@code false} otherwise or if privacy protection is disabled.
     */
    public boolean isDomainBlocked(String domain) {
        if (domain == null || !mPrivacyProtectionEnabled) { // Check privacy protection flag
            return false;
        }

        // Allowlist overrides blocklist
        synchronized (mDomainAllowlist) {
            for (Pattern pattern : mDomainAllowlist) {
                if (pattern.matcher(domain).matches()) {
                    return false; // Allowed by allowlist
                }
            }
        }

        synchronized (mDomainBlocklist) {
            for (Pattern pattern : mDomainBlocklist) {
                if (pattern.matcher(domain).matches()) {
                    return true; // Blocked by blocklist
                }
            }
        }
        return false; // Not in allowlist, not in blocklist
    }

    /**
     * Determines whether a network connection should be allowed based on the current firewall state and rules.
     *
     * @param packageName The package name of the application initiating the connection.
     * @param destination The destination hostname or IP address.
     * @param port        The destination port number.
     * @param protocol    The network protocol (see {@code PROTO_*} constants).
     * @return {@code true} if the connection should be allowed, {@code false} if it should be blocked.
     */
    public boolean shouldAllowConnection(String packageName, String destination, int port, int protocol) {
        if (!mFirewallEnabled) {
            return true; // Firewall disabled, all connections allowed by default through SecurityManager
        }

        if (packageName == null || destination == null) { // Basic null checks
            logFirewallEvent(packageName, destination, port, protocol, true, "Invalid parameters, default allow");
            return true;
        }

        // 1. Check if app is globally restricted
        if (isAppNetworkRestricted(packageName)) {
            logFirewallEvent(packageName, destination, port, protocol, false, "App globally restricted");
            return false;
        }

        // 2. Check if domain is blocked (if privacy protection is enabled)
        if (mPrivacyProtectionEnabled && isDomainBlocked(destination)) { // isDomainBlocked already checks mPrivacyProtectionEnabled
            logFirewallEvent(packageName, destination, port, protocol, false, "Domain in blocklist");
            return false;
        }

        // 3. Check specific rules for this app
        List<FirewallRule> rules = mAppRules.get(packageName);
        if (rules != null && !rules.isEmpty()) { // Iterate only if rules exist
            for (FirewallRule rule : rules) {
                if (rule.matches(destination, port, protocol)) {
                    switch (rule.type) {
                        case RULE_TYPE_BLOCK:
                            logFirewallEvent(packageName, destination, port, protocol, false, "Matched block rule: " + rule.toShortString());
                            return false; // Explicit block
                        case RULE_TYPE_ALLOW:
                            logFirewallEvent(packageName, destination, port, protocol, true, "Matched allow rule: " + rule.toShortString());
                            return true; // Explicit allow
                        case RULE_TYPE_LOG:
                            logFirewallEvent(packageName, destination, port, protocol, true, "Matched log rule (connection allowed): " + rule.toShortString());
                            // Logged, but doesn't terminate rule processing. This connection is allowed unless a later block rule hits.
                            // If this is the *last* matching rule, or no other rules match, it's allowed.
                            break; // Continue to check other rules, but this implies allowance if no subsequent block.
                    }
                }
            }
        }

        // 4. Default action: Allow if no explicit block rule matched.
        // If a LOG rule matched and no subsequent BLOCK rule matched, the connection is allowed here.
        logFirewallEvent(packageName, destination, port, protocol, true, "Default allow (no specific block rule matched)");
        return true;
    }

    /**
     * Logs a firewall event if network monitoring is enabled.
     * The log entry includes a timestamp, package name, destination, port, protocol,
     * action (allow/block), and reason.
     *
     * @param packageName The package name of the application.
     * @param destination The destination hostname or IP address.
     * @param port        The destination port.
     * @param protocol    The network protocol.
     * @param allowed     {@code true} if the connection was allowed, {@code false} if blocked.
     * @param reason      A brief description of why the action was taken.
     */
    private void logFirewallEvent(String packageName, String destination, int port,
                                 int protocol, boolean allowed, String reason) {
        if (!mNetworkMonitoringEnabled) {
            return;
        }

        long timestamp = System.currentTimeMillis();
        String logEntry = String.format("%d,%s,%s,%d,%s,%s,%s",
                timestamp,
                packageName == null ? "N/A" : packageName,
                destination == null ? "N/A" : destination,
                port,
                protocolToString(protocol),
                allowed ? "ALLOW" : "BLOCK",
                reason == null ? "N/A" : reason
        );

        synchronized (mFirewallLog) {
            mFirewallLog.add(0, logEntry); // Add to the beginning for recent events first
            while (mFirewallLog.size() > MAX_FIREWALL_LOG_ENTRIES) {
                mFirewallLog.remove(mFirewallLog.size() - 1); // Trim oldest entries
            }
        }

        // Notify listeners about the firewall event
        for (SecurityListener listener : mListeners) {
            try {
                listener.onFirewallEvent(packageName, destination, port, protocol, allowed, reason);
            } catch (Exception e) {
                LoggingHelper.logError(TAG, "Error notifying listener of firewall event", e);
            }
        }
    }

    /**
     * Retrieves the firewall log entries.
     *
     * @param limit The maximum number of log entries to return. If non-positive or greater
     *              than the current log size, all entries are returned.
     * @return A new {@code List} containing the firewall log entries, with the most recent entries first.
     */
    public List<String> getFirewallLog(int limit) {
        synchronized (mFirewallLog) {
            if (limit <= 0 || limit >= mFirewallLog.size()) {
                return new ArrayList<>(mFirewallLog); // Return a copy
            }
            return new ArrayList<>(mFirewallLog.subList(0, limit)); // Return a copy of the sublist
        }
    }

    /**
     * Clears all entries from the in-memory firewall log.
     * This does not clear the persisted log file until the next {@link #rotateLogFile()} call.
     */
    public void clearFirewallLog() {
        synchronized (mFirewallLog) {
            mFirewallLog.clear();
        }
        LoggingHelper.logInfo(TAG, "In-memory firewall log cleared.");
        // Consider if immediately clearing the file is also desired or if rotation handles it sufficiently.
    }

    /**
     * Gets the recorded network data usage for a specific application.
     * Data usage is collected periodically if network monitoring is enabled.
     *
     * @param packageName The package name of the application.
     * @return The data usage in bytes, or 0L if the package is not found or monitoring is disabled.
     */
    public long getDataUsage(String packageName) {
        if (packageName == null) return 0L;
        return mDataUsageMap.getOrDefault(packageName, 0L);
    }

    /**
     * Periodically called task to collect network data usage for all installed applications.
     * Uses {@link android.net.TrafficStats} for data collection.
     * This method updates the internal data usage map and notifies listeners.
     *
     * <p>For more accurate and granular data (especially per-network type or UID),
     * a future enhancement could consider using {@link android.net.NetworkStatsManager},
     * which requires {@code PACKAGE_USAGE_STATS} permission and may need elevated privileges
     * or specific Android versions.</p>
     */
    private void collectDataUsage() {
        if (!mNetworkMonitoringEnabled) {
            return;
        }
        LoggingHelper.logDebug(TAG, "Collecting data usage...");
        PackageManager pm = mContext.getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA); // Get more info if needed

        boolean updated = false;
        for (ApplicationInfo app : apps) {
            int uid = app.uid;
            long currentRxBytes = android.net.TrafficStats.getUidRxBytes(uid);
            long currentTxBytes = android.net.TrafficStats.getUidTxBytes(uid);
            long totalBytes = currentRxBytes + currentTxBytes;

            if (currentRxBytes == android.net.TrafficStats.UNSUPPORTED || 
                currentTxBytes == android.net.TrafficStats.UNSUPPORTED) {
                // LoggingHelper.logDebug(TAG, "TrafficStats unsupported for UID: " + uid + " (Package: " + app.packageName + ")");
                continue; // Skip if TrafficStats not supported for this UID
            }
            
            // We store cumulative usage. If a more sophisticated delta is needed, store previous values.
            // For simplicity here, we are overwriting with the current total.
            // A more robust implementation might track previous values to calculate usage over the interval.
            Long previousTotal = mDataUsageMap.get(app.packageName);
            if (previousTotal == null || totalBytes != previousTotal) {
                 mDataUsageMap.put(app.packageName, totalBytes);
                 updated = true;
            }
        }

        if (updated) {
            LoggingHelper.logDebug(TAG, "Data usage updated for one or more apps.");
            // Notify listeners that data usage has been updated
            for (SecurityListener listener : mListeners) {
                try {
                    listener.onDataUsageUpdated();
                } catch (Exception e) {
                    LoggingHelper.logError(TAG, "Error notifying listener of data usage update", e);
                }
            }
        }
    }

    /**
     * Periodically called task to rotate the firewall log file.
     * The current in-memory log is written to {@code firewall.log}, and the previous
     * {@code firewall.log} is backed up to {@code firewall.log.1}.
     */
    private void rotateLogFile() {
        if (!mNetworkMonitoringEnabled || mFirewallLog.isEmpty()) {
            // No need to rotate if monitoring is off or log is empty
            return;
        }

        File logFile = new File(mSecurityDir, FIREWALL_LOG_FILE_NAME);
        File backupFile = new File(mSecurityDir, FIREWALL_LOG_FILE_NAME + ".1");

        LoggingHelper.logDebug(TAG, "Attempting to rotate firewall log file: " + logFile.getAbsolutePath());

        try {
            if (logFile.exists()) {
                if (backupFile.exists()) {
                    if (!backupFile.delete()) {
                        LoggingHelper.logWarning(TAG, "Failed to delete old backup log file: " + backupFile.getAbsolutePath());
                    }
                }
                if (!logFile.renameTo(backupFile)) {
                    LoggingHelper.logWarning(TAG, "Failed to rename current log to backup log file: " + logFile.getAbsolutePath());
                }
            }

            // Write current in-memory log to the new log file
            List<String> logSnapshot;
            synchronized (mFirewallLog) {
                logSnapshot = new ArrayList<>(mFirewallLog); // Take a snapshot to write
            }

            try (FileOutputStream fos = new FileOutputStream(logFile);
                 PrintWriter writer = new PrintWriter(fos)) {
                for (String entry : logSnapshot) {
                    writer.println(entry);
                }
                writer.flush();
                LoggingHelper.logInfo(TAG, "Firewall log successfully written to: " + logFile.getAbsolutePath() + " (" + logSnapshot.size() + " entries)");
            } catch (IOException e) {
                LoggingHelper.logError(TAG, "IOException writing firewall log to file: " + logFile.getAbsolutePath(), e);
            }
        } catch (Exception e) {
            LoggingHelper.logError(TAG, "Unexpected error during firewall log rotation for: " + logFile.getAbsolutePath(), e);
        }
    }

    /**
     * Loads security rules and configurations from {@code security_rules.json}.
     * If the file doesn't exist or is invalid, default rules are initialized.
     */
    @SuppressWarnings("unchecked") // For GSON type casting
    private void loadRules() {
        File rulesFile = new File(mSecurityDir, RULES_FILE_NAME);
        if (!rulesFile.exists() || !rulesFile.canRead()) {
            LoggingHelper.logWarning(TAG, "Rules file not found or not readable: " + rulesFile.getAbsolutePath() + ". Initializing default rules.");
            initializeDefaultRules();
            saveRules(); // Save the default rules
            return;
        }

        try (FileReader fileReader = new FileReader(rulesFile);
             BufferedReader reader = new BufferedReader(fileReader)) {

            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root == null) {
                LoggingHelper.logError(TAG, "Failed to parse rules file, root object is null. File: " + rulesFile.getAbsolutePath());
                initializeDefaultRules();
                saveRules();
                return;
            }

            // Load enabled states
            mFirewallEnabled = root.has("firewallEnabled") ? root.get("firewallEnabled").getAsBoolean() : false;
            mNetworkMonitoringEnabled = root.has("networkMonitoringEnabled") ? root.get("networkMonitoringEnabled").getAsBoolean() : false;
            mPrivacyProtectionEnabled = root.has("privacyProtectionEnabled") ? root.get("privacyProtectionEnabled").getAsBoolean() : false;

            // Load domain blocklist
            if (root.has("domainBlocklist")) {
                JsonArray blocklistArray = root.getAsJsonArray("domainBlocklist");
                synchronized (mDomainBlocklist) {
                    mDomainBlocklist.clear();
                    for (JsonElement element : blocklistArray) {
                        try {
                            mDomainBlocklist.add(Pattern.compile(element.getAsString()));
                        } catch (PatternSyntaxException e) {
                            LoggingHelper.logError(TAG, "Invalid regex pattern in saved blocklist: " + element.getAsString(), e);
                        }
                    }
                }
            }

            // Load domain allowlist
            if (root.has("domainAllowlist")) {
                JsonArray allowlistArray = root.getAsJsonArray("domainAllowlist");
                synchronized (mDomainAllowlist) {
                    mDomainAllowlist.clear();
                    for (JsonElement element : allowlistArray) {
                        try {
                            mDomainAllowlist.add(Pattern.compile(element.getAsString()));
                        } catch (PatternSyntaxException e) {
                            LoggingHelper.logError(TAG, "Invalid regex pattern in saved allowlist: " + element.getAsString(), e);
                        }
                    }
                }
            }

            // Load app-specific rules
            if (root.has("appRules")) {
                JsonObject appRulesObject = root.getAsJsonObject("appRules");
                mAppRules.clear();
                for (Map.Entry<String, JsonElement> entry : appRulesObject.entrySet()) {
                    String packageName = entry.getKey();
                    JsonArray rulesArray = entry.getValue().getAsJsonArray();
                    List<FirewallRule> packageRules = new CopyOnWriteArrayList<>(); // Use CopyOnWriteArrayList
                    for (JsonElement ruleElement : rulesArray) {
                        FirewallRule rule = mGson.fromJson(ruleElement, FirewallRule.class);
                        if (rule != null) { // Basic validation
                           packageRules.add(rule);
                        }
                    }
                    if (!packageRules.isEmpty()) {
                        mAppRules.put(packageName, packageRules);
                    }
                }
            }

            // Load network restricted apps
            if (root.has("networkRestrictedApps")) {
                JsonArray restrictedAppsArray = root.getAsJsonArray("networkRestrictedApps");
                synchronized (mNetworkRestrictedApps) {
                    mNetworkRestrictedApps.clear();
                    for (JsonElement element : restrictedAppsArray) {
                        mNetworkRestrictedApps.add(element.getAsString());
                    }
                }
            }
            LoggingHelper.logInfo(TAG, "Security rules loaded successfully from: " + rulesFile.getAbsolutePath());

        } catch (IOException e) {
            LoggingHelper.logError(TAG, "IOException loading rules from " + rulesFile.getAbsolutePath() + ". Initializing defaults.", e);
            initializeDefaultRules();
            saveRules(); // Save defaults if loading failed
        } catch (com.google.gson.JsonSyntaxException | IllegalStateException e) {
            LoggingHelper.logError(TAG, "JSON syntax or structure error in rules file " + rulesFile.getAbsolutePath() + ". Initializing defaults.", e);
            initializeDefaultRules();
            saveRules();
        } catch (Exception e) { // Catch-all for unexpected errors during loading
            LoggingHelper.logError(TAG, "Unexpected error loading rules from " + rulesFile.getAbsolutePath() + ". Initializing defaults.", e);
            initializeDefaultRules();
            saveRules();
        }
    }

    /**
     * Initializes default security rules and configurations.
     * This method is called if the rules file does not exist or is invalid.
     * Currently, it sets all features to disabled and clears rule lists.
     */
    private void initializeDefaultRules() {
        LoggingHelper.logInfo(TAG, "Initializing default security rules and configurations.");
        mFirewallEnabled = false;
        mNetworkMonitoringEnabled = false;
        mPrivacyProtectionEnabled = false;

        synchronized (mDomainBlocklist) {
            mDomainBlocklist.clear();
        }
        synchronized (mDomainAllowlist) {
            mDomainAllowlist.clear();
        }
        mAppRules.clear();
        synchronized (mNetworkRestrictedApps) {
            mNetworkRestrictedApps.clear();
        }
        // Example: Add a default block rule for a known ad domain if privacy protection were enabled by default
        // if (mPrivacyProtectionEnabled) {
        //    addDomainToBlocklist(".*doubleclick\\.net");
        // }
        // Ensure security directory exists (should be done in constructor, but double-check)
        if (!mSecurityDir.exists()) {
            if (!mSecurityDir.mkdirs()) {
                LoggingHelper.logError(TAG, "Failed to create security directory during default rule initialization: " + mSecurityDir.getAbsolutePath());
            }
        }
    }

    /**
     * Saves the current security rules and configurations to {@code security_rules.json}.
     * This method is synchronized to prevent concurrent modification issues during saving.
     */
    private synchronized void saveRules() {
        File rulesFile = new File(mSecurityDir, RULES_FILE_NAME);
        JsonObject root = new JsonObject();

        // Save enabled states
        root.addProperty("firewallEnabled", mFirewallEnabled);
        root.addProperty("networkMonitoringEnabled", mNetworkMonitoringEnabled);
        root.addProperty("privacyProtectionEnabled", mPrivacyProtectionEnabled);

        // Save domain blocklist
        JsonArray blocklistArray = new JsonArray();
        synchronized (mDomainBlocklist) {
            for (Pattern pattern : mDomainBlocklist) {
                blocklistArray.add(pattern.pattern());
            }
        }
        root.add("domainBlocklist", blocklistArray);

        // Save domain allowlist
        JsonArray allowlistArray = new JsonArray();
        synchronized (mDomainAllowlist) {
            for (Pattern pattern : mDomainAllowlist) {
                allowlistArray.add(pattern.pattern());
            }
        }
        root.add("domainAllowlist", allowlistArray);

        // Save app-specific rules
        JsonObject appRulesObject = new JsonObject();
        for (Map.Entry<String, List<FirewallRule>> entry : mAppRules.entrySet()) {
            JsonArray rulesArray = new JsonArray();
            for (FirewallRule rule : entry.getValue()) {
                rulesArray.add(mGson.toJsonTree(rule));
            }
            appRulesObject.add(entry.getKey(), rulesArray);
        }
        root.add("appRules", appRulesObject);

        // Save network restricted apps
        JsonArray restrictedAppsArray = new JsonArray();
        synchronized (mNetworkRestrictedApps) {
            for (String app : mNetworkRestrictedApps) {
                restrictedAppsArray.add(app);
            }
        }
        root.add("networkRestrictedApps", restrictedAppsArray);

        try (FileOutputStream fos = new FileOutputStream(rulesFile);
             PrintWriter writer = new PrintWriter(fos)) {
            String jsonString = mGson.toJson(root);
            writer.print(jsonString);
            LoggingHelper.logDebug(TAG, "Security rules saved successfully to: " + rulesFile.getAbsolutePath());
        } catch (IOException e) {
            LoggingHelper.logError(TAG, "IOException saving rules to " + rulesFile.getAbsolutePath(), e);
        } catch (Exception e) {
            LoggingHelper.logError(TAG, "Unexpected error saving rules to " + rulesFile.getAbsolutePath(), e);
        }
    }

    /**
     * Converts a protocol constant (e.g., {@link #PROTO_TCP}) to its string representation.
     *
     * @param protocol The protocol constant.
     * @return A string like "TCP", "UDP", "ICMP", "ALL", or "UNKNOWN".
     */
    private String protocolToString(int protocol) {
        switch (protocol) {
            case PROTO_TCP:
                return "TCP";
            case PROTO_UDP:
                return "UDP";
            case PROTO_ICMP:
                return "ICMP";
            case PROTO_ALL:
                return "ALL";
            default:
                return "UNKNOWN(" + protocol + ")";
        }
    }

    /**
     * Notifies all registered listeners that security rules have changed.
     */
    private void notifyRulesChanged() {
        LoggingHelper.logDebug(TAG, "Notifying " + mListeners.size() + " listeners of rule change.");
        for (SecurityListener listener : mListeners) {
            try {
                listener.onSecurityRulesChanged();
            } catch (Exception e) {
                LoggingHelper.logError(TAG, "Error notifying listener of rule change", e);
            }
        }
    }

    /**
     * Notifies all registered listeners that the security configuration (enabled states) has changed.
     */
    private void notifyConfigChanged() {
        LoggingHelper.logDebug(TAG, "Notifying " + mListeners.size() + " listeners of config change.");
        for (SecurityListener listener : mListeners) {
            try {
                listener.onSecurityConfigChanged(mFirewallEnabled, mNetworkMonitoringEnabled, mPrivacyProtectionEnabled);
            } catch (Exception e) {
                LoggingHelper.logError(TAG, "Error notifying listener of config change", e);
            }
        }
    }

    /**
     * Represents a single firewall rule that can be applied to an application.
     * A rule defines conditions (destination pattern, port range, protocol) and an action (allow, block, log).
     */
    public static class FirewallRule {
        /** The type of rule (e.g., {@link #RULE_TYPE_BLOCK}). Default is BLOCK. */
        public int type = RULE_TYPE_BLOCK;
        /** A regex pattern to match against the destination hostname or IP address. "*" matches any. */
        public String destinationPattern = "*"; // Default to match all destinations
        /** The start of the port range for this rule (inclusive). 0 for any port if portEnd is also 0 or max. */
        public int portStart = 0;
        /** The end of the port range for this rule (inclusive). 65535 for any port. */
        public int portEnd = 65535;
        /** The network protocol for this rule (e.g., {@link #PROTO_TCP}). Default is ALL. */
        public int protocol = PROTO_ALL;

        private transient Pattern compiledDestinationPattern; // Cache compiled pattern

        /**
         * Default constructor for GSON.
         */
        public FirewallRule() {}

        /**
         * Constructs a new {@code FirewallRule}.
         *
         * @param type               The rule type ({@link #RULE_TYPE_ALLOW}, {@link #RULE_TYPE_BLOCK}, {@link #RULE_TYPE_LOG}).
         * @param destinationPattern A regex for the destination. Use "*" for any.
         * @param portStart          The start of the port range (0 for any if portEnd is max).
         * @param portEnd            The end of the port range (65535 for any).
         * @param protocol           The protocol ({@link #PROTO_TCP}, {@link #PROTO_UDP}, etc., or {@link #PROTO_ALL}).
         */
        public FirewallRule(int type, String destinationPattern, int portStart, int portEnd, int protocol) {
            this.type = type;
            this.destinationPattern = (destinationPattern == null || destinationPattern.isEmpty()) ? "*" : destinationPattern;
            this.portStart = Math.max(0, portStart);
            this.portEnd = Math.min(65535, Math.max(this.portStart, portEnd)); // Ensure portEnd >= portStart
            this.protocol = protocol;
            compilePattern();
        }

        private void compilePattern() {
            if (this.destinationPattern == null || "*".equals(this.destinationPattern)) {
                this.compiledDestinationPattern = null; // Indicates match all
            } else {
                try {
                    this.compiledDestinationPattern = Pattern.compile(this.destinationPattern);
                } catch (PatternSyntaxException e) {
                    LoggingHelper.logError(TAG, "Invalid regex in FirewallRule: " + this.destinationPattern, e);
                    this.compiledDestinationPattern = null; // Effectively makes it match all if pattern is invalid
                    this.destinationPattern = "*"; // Reset to wildcard to avoid repeated errors
                }
            }
        }

        /**
         * Checks if a given network connection matches this firewall rule.
         *
         * @param destination The destination hostname or IP address of the connection.
         * @param port        The destination port of the connection.
         * @param protocol    The protocol of the connection.
         * @return {@code true} if the connection matches this rule, {@code false} otherwise.
         */
        public boolean matches(String destination, int port, int protocol) {
            if (compiledDestinationPattern == null && !"*".equals(this.destinationPattern)) { // Re-compile if null and not wildcard (e.g. after GSON deserialization)
                compilePattern();
            }

            // Protocol match
            if (this.protocol != PROTO_ALL && this.protocol != protocol) {
                return false;
            }

            // Port match
            if (!(port >= this.portStart && port <= this.portEnd)) {
                 // If rule port range is 0-65535 (any port), this check should effectively pass for any valid port.
                 // However, if specific ports are set, then it must be within range.
                 // This logic seems correct. If portStart=0, portEnd=65535, any port will match.
                if (!(this.portStart == 0 && this.portEnd == 65535)) { // Only fail if not matching a specific port range
                     return false;
                }
            }

            // Destination match
            if (compiledDestinationPattern != null) { // If null, it's a wildcard "*"
                if (destination == null || !compiledDestinationPattern.matcher(destination).matches()) {
                    return false;
                }
            }
            // If compiledDestinationPattern is null, it means "*" which matches any destination.

            return true; // All conditions met
        }
        
        /**
         * Provides a short string representation of the rule, useful for logging.
         * @return A compact string describing the rule.
         */
        public String toShortString() {
            String typeStr;
            switch (type) {
                case RULE_TYPE_ALLOW: typeStr = "ALLOW"; break;
                case RULE_TYPE_BLOCK: typeStr = "BLOCK"; break;
                case RULE_TYPE_LOG: typeStr = "LOG"; break;
                default: typeStr = "UNK_TYPE"; break;
            }
            String protoStr;
            switch (protocol) {
                 case PROTO_TCP: protoStr = "TCP"; break;
                 case PROTO_UDP: protoStr = "UDP"; break;
                 case PROTO_ICMP: protoStr = "ICMP"; break;
                 case PROTO_ALL: protoStr = "ALL"; break;
                 default: protoStr = "UNK_PROTO"; break;
            }
            String portRange = (portStart == 0 && portEnd == 65535) ? "ANY_PORT" : portStart + "-" + portEnd;
            return String.format("%s %s %s:%s", typeStr, protoStr, destinationPattern, portRange);
        }


        @Override
        public String toString() {
            return "FirewallRule{" +
                    "type=" + type +
                    ", destinationPattern='" + destinationPattern + '\'' +
                    ", portStart=" + portStart +
                    ", portEnd=" + portEnd +
                    ", protocol=" + protocol +
                    '}';
        }
    }

    /**
     * Interface for components that need to be notified of security-related events
     * from the {@link SecurityManager}.
     */
    public interface SecurityListener {
        /**
         * Called when security rules (e.g., firewall rules, domain lists) have changed.
         * Listeners should refresh any cached rules or update their behavior accordingly.
         */
        void onSecurityRulesChanged();

        /**
         * Called when the global security configuration has changed (e.g., firewall enabled/disabled).
         *
         * @param firewallEnabled           Current state of the firewall.
         * @param networkMonitoringEnabled  Current state of network monitoring.
         * @param privacyProtectionEnabled  Current state of privacy protection features.
         */
        void onSecurityConfigChanged(boolean firewallEnabled,
                                    boolean networkMonitoringEnabled,
                                    boolean privacyProtectionEnabled);

        /**
         * Called when a firewall event occurs (e.g., a connection is allowed or blocked).
         * This is only triggered if network monitoring is enabled.
         *
         * @param packageName The package name of the application initiating the connection.
         * @param destination The destination hostname or IP address.
         * @param port        The destination port.
         * @param protocol    The network protocol (see {@code PROTO_*} constants).
         * @param allowed     {@code true} if the connection was allowed, {@code false} if blocked.
         * @param reason      A brief string explaining why the action was taken (e.g., "Matched block rule", "Domain in blocklist").
         */
        void onFirewallEvent(String packageName, String destination,
                            int port, int protocol, boolean allowed, String reason);

        /**
         * Called when network data usage information for applications has been updated.
         * This is only triggered if network monitoring is enabled.
         */
        void onDataUsageUpdated();
    }
} 