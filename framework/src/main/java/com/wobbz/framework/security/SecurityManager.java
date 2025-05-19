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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Security manager for advanced protection features.
 * Includes firewall, network monitoring, and privacy protection.
 */
public class SecurityManager {
    private static final String TAG = "SecurityManager";
    private static final String RULES_FILE = "security_rules.json";
    private static final String RULES_DIR = "security";
    private static final String FIREWALL_LOG = "firewall.log";
    private static final int LOG_MAX_ENTRIES = 1000;
    
    // Types of firewall rules
    public static final int RULE_TYPE_ALLOW = 0;
    public static final int RULE_TYPE_BLOCK = 1;
    public static final int RULE_TYPE_LOG = 2;
    
    // Network protocols
    public static final int PROTO_TCP = 0;
    public static final int PROTO_UDP = 1;
    public static final int PROTO_ICMP = 2;
    public static final int PROTO_ALL = 3;
    
    private static SecurityManager sInstance;
    
    private final Context mContext;
    private final Set<Pattern> mDomainBlocklist = new HashSet<>();
    private final Set<Pattern> mDomainAllowlist = new HashSet<>();
    private final Map<String, List<FirewallRule>> mAppRules = new HashMap<>();
    private final Set<String> mNetworkRestrictedApps = new HashSet<>();
    private final Map<String, Long> mDataUsageMap = new ConcurrentHashMap<>();
    private final List<String> mFirewallLog = new ArrayList<>();
    private final List<SecurityListener> mListeners = new ArrayList<>();
    private final ScheduledExecutorService mExecutor = Executors.newScheduledThreadPool(2);
    
    private boolean mFirewallEnabled = false;
    private boolean mNetworkMonitoringEnabled = false;
    private boolean mPrivacyProtectionEnabled = false;
    
    /**
     * Private constructor for singleton pattern.
     */
    private SecurityManager(Context context) {
        mContext = context.getApplicationContext();
        
        // Create security directory
        File securityDir = new File(mContext.getFilesDir(), RULES_DIR);
        if (!securityDir.exists()) {
            securityDir.mkdirs();
        }
        
        // Load saved rules
        loadRules();
        
        // Start periodic data usage collection
        mExecutor.scheduleAtFixedRate(this::collectDataUsage, 5, 5, TimeUnit.MINUTES);
        
        // Start log rotation
        mExecutor.scheduleAtFixedRate(this::rotateLogFile, 1, 1, TimeUnit.HOURS);
    }
    
    /**
     * Get the singleton instance.
     */
    public static synchronized SecurityManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new SecurityManager(context);
        }
        return sInstance;
    }
    
    /**
     * Add a security listener to receive security events.
     */
    public void addListener(SecurityListener listener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }
    
    /**
     * Remove a security listener.
     */
    public void removeListener(SecurityListener listener) {
        mListeners.remove(listener);
    }
    
    /**
     * Set the firewall enabled state.
     */
    public void setFirewallEnabled(boolean enabled) {
        mFirewallEnabled = enabled;
        saveRules();
        notifyConfigChanged();
    }
    
    /**
     * Check if the firewall is enabled.
     */
    public boolean isFirewallEnabled() {
        return mFirewallEnabled;
    }
    
    /**
     * Set the network monitoring enabled state.
     */
    public void setNetworkMonitoringEnabled(boolean enabled) {
        mNetworkMonitoringEnabled = enabled;
        saveRules();
        notifyConfigChanged();
    }
    
    /**
     * Check if network monitoring is enabled.
     */
    public boolean isNetworkMonitoringEnabled() {
        return mNetworkMonitoringEnabled;
    }
    
    /**
     * Set the privacy protection enabled state.
     */
    public void setPrivacyProtectionEnabled(boolean enabled) {
        mPrivacyProtectionEnabled = enabled;
        saveRules();
        notifyConfigChanged();
    }
    
    /**
     * Check if privacy protection is enabled.
     */
    public boolean isPrivacyProtectionEnabled() {
        return mPrivacyProtectionEnabled;
    }
    
    /**
     * Add a domain pattern to the blocklist.
     * 
     * @param pattern Regex pattern for domains to block.
     */
    public void addDomainToBlocklist(String pattern) {
        try {
            Pattern compiled = Pattern.compile(pattern);
            mDomainBlocklist.add(compiled);
            saveRules();
            notifyRulesChanged();
            
            LoggingHelper.info(TAG, "Added domain pattern to blocklist: " + pattern);
        } catch (Exception e) {
            LoggingHelper.error(TAG, "Invalid domain pattern: " + pattern, e);
        }
    }
    
    /**
     * Remove a domain pattern from the blocklist.
     * 
     * @param pattern The pattern to remove.
     */
    public void removeDomainFromBlocklist(String pattern) {
        mDomainBlocklist.removeIf(p -> p.pattern().equals(pattern));
        saveRules();
        notifyRulesChanged();
        
        LoggingHelper.info(TAG, "Removed domain pattern from blocklist: " + pattern);
    }
    
    /**
     * Add a domain pattern to the allowlist.
     * 
     * @param pattern Regex pattern for domains to allow.
     */
    public void addDomainToAllowlist(String pattern) {
        try {
            Pattern compiled = Pattern.compile(pattern);
            mDomainAllowlist.add(compiled);
            saveRules();
            notifyRulesChanged();
            
            LoggingHelper.info(TAG, "Added domain pattern to allowlist: " + pattern);
        } catch (Exception e) {
            LoggingHelper.error(TAG, "Invalid domain pattern: " + pattern, e);
        }
    }
    
    /**
     * Remove a domain pattern from the allowlist.
     * 
     * @param pattern The pattern to remove.
     */
    public void removeDomainFromAllowlist(String pattern) {
        mDomainAllowlist.removeIf(p -> p.pattern().equals(pattern));
        saveRules();
        notifyRulesChanged();
        
        LoggingHelper.info(TAG, "Removed domain pattern from allowlist: " + pattern);
    }
    
    /**
     * Add a firewall rule for an app.
     * 
     * @param packageName The app package name.
     * @param rule The firewall rule to add.
     */
    public void addFirewallRule(String packageName, FirewallRule rule) {
        List<FirewallRule> rules = mAppRules.computeIfAbsent(packageName, k -> new ArrayList<>());
        rules.add(rule);
        saveRules();
        notifyRulesChanged();
        
        LoggingHelper.info(TAG, "Added firewall rule for " + packageName + ": " + rule.toString());
    }
    
    /**
     * Remove a firewall rule for an app.
     * 
     * @param packageName The app package name.
     * @param index The index of the rule to remove.
     */
    public void removeFirewallRule(String packageName, int index) {
        List<FirewallRule> rules = mAppRules.get(packageName);
        if (rules != null && index >= 0 && index < rules.size()) {
            FirewallRule rule = rules.remove(index);
            saveRules();
            notifyRulesChanged();
            
            LoggingHelper.info(TAG, "Removed firewall rule for " + packageName + ": " + rule.toString());
        }
    }
    
    /**
     * Get all firewall rules for an app.
     * 
     * @param packageName The app package name.
     * @return List of firewall rules, or empty list if none.
     */
    public List<FirewallRule> getFirewallRulesForApp(String packageName) {
        List<FirewallRule> rules = mAppRules.get(packageName);
        return rules != null ? new ArrayList<>(rules) : new ArrayList<>();
    }
    
    /**
     * Set network restriction for an app.
     * 
     * @param packageName The app package name.
     * @param restricted Whether to restrict network access.
     */
    public void setAppNetworkRestricted(String packageName, boolean restricted) {
        if (restricted) {
            mNetworkRestrictedApps.add(packageName);
        } else {
            mNetworkRestrictedApps.remove(packageName);
        }
        saveRules();
        notifyRulesChanged();
        
        LoggingHelper.info(TAG, "Set network restriction for " + packageName + ": " + restricted);
    }
    
    /**
     * Check if an app has network restrictions.
     * 
     * @param packageName The app package name.
     * @return true if the app is restricted, false otherwise.
     */
    public boolean isAppNetworkRestricted(String packageName) {
        return mNetworkRestrictedApps.contains(packageName);
    }
    
    /**
     * Check if a domain is blocked.
     * 
     * @param domain The domain to check.
     * @return true if the domain is blocked, false otherwise.
     */
    public boolean isDomainBlocked(String domain) {
        if (!mFirewallEnabled) {
            return false;
        }
        
        // Check allowlist first (allowlist overrides blocklist)
        for (Pattern pattern : mDomainAllowlist) {
            if (pattern.matcher(domain).matches()) {
                return false;
            }
        }
        
        // Then check blocklist
        for (Pattern pattern : mDomainBlocklist) {
            if (pattern.matcher(domain).matches()) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Check if a network connection should be allowed.
     * 
     * @param packageName The app package name.
     * @param destination The destination host.
     * @param port The destination port.
     * @param protocol The protocol (TCP, UDP, etc).
     * @return true if the connection should be allowed, false otherwise.
     */
    public boolean shouldAllowConnection(String packageName, String destination, int port, int protocol) {
        if (!mFirewallEnabled) {
            return true;
        }
        
        // Check if app is globally restricted
        if (mNetworkRestrictedApps.contains(packageName)) {
            logFirewallEvent(packageName, destination, port, protocol, false, "App globally restricted");
            return false;
        }
        
        // Check if domain is blocked
        if (isDomainBlocked(destination)) {
            logFirewallEvent(packageName, destination, port, protocol, false, "Domain in blocklist");
            return false;
        }
        
        // Check specific rules for this app
        List<FirewallRule> rules = mAppRules.get(packageName);
        if (rules != null) {
            for (FirewallRule rule : rules) {
                if (rule.matches(destination, port, protocol)) {
                    if (rule.type == RULE_TYPE_BLOCK) {
                        logFirewallEvent(packageName, destination, port, protocol, false, "Matched block rule");
                        return false;
                    } else if (rule.type == RULE_TYPE_ALLOW) {
                        logFirewallEvent(packageName, destination, port, protocol, true, "Matched allow rule");
                        return true;
                    } else if (rule.type == RULE_TYPE_LOG) {
                        logFirewallEvent(packageName, destination, port, protocol, true, "Matched log rule");
                        // Continue checking other rules
                    }
                }
            }
        }
        
        // Default to allow if no rules matched
        logFirewallEvent(packageName, destination, port, protocol, true, "Default allow");
        return true;
    }
    
    /**
     * Log a firewall event.
     */
    private void logFirewallEvent(String packageName, String destination, int port, 
                                 int protocol, boolean allowed, String reason) {
        if (!mNetworkMonitoringEnabled) {
            return;
        }
        
        // Create log entry
        String entry = System.currentTimeMillis() + "," + 
                      packageName + "," + 
                      destination + "," + 
                      port + "," + 
                      protocolToString(protocol) + "," + 
                      (allowed ? "ALLOW" : "BLOCK") + "," + 
                      reason;
        
        synchronized (mFirewallLog) {
            mFirewallLog.add(0, entry); // Add at beginning
            
            // Trim log if it gets too large
            while (mFirewallLog.size() > LOG_MAX_ENTRIES) {
                mFirewallLog.remove(mFirewallLog.size() - 1);
            }
        }
        
        // Notify listeners
        for (SecurityListener listener : mListeners) {
            listener.onFirewallEvent(packageName, destination, port, protocol, allowed, reason);
        }
    }
    
    /**
     * Get the firewall log entries.
     * 
     * @param limit Maximum number of entries to return.
     * @return List of log entries.
     */
    public List<String> getFirewallLog(int limit) {
        synchronized (mFirewallLog) {
            if (limit <= 0 || limit >= mFirewallLog.size()) {
                return new ArrayList<>(mFirewallLog);
            }
            return new ArrayList<>(mFirewallLog.subList(0, limit));
        }
    }
    
    /**
     * Clear the firewall log.
     */
    public void clearFirewallLog() {
        synchronized (mFirewallLog) {
            mFirewallLog.clear();
        }
        
        LoggingHelper.info(TAG, "Cleared firewall log");
    }
    
    /**
     * Get data usage for an app.
     * 
     * @param packageName The app package name.
     * @return Data usage in bytes, or 0 if unknown.
     */
    public long getDataUsage(String packageName) {
        return mDataUsageMap.getOrDefault(packageName, 0L);
    }
    
    /**
     * Collect data usage information.
     */
    private void collectDataUsage() {
        if (!mNetworkMonitoringEnabled) {
            return;
        }
        
        // This is a placeholder - in a real implementation, you'd use TrafficStats or
        // NetworkStatsManager to collect actual data usage
        LoggingHelper.debug(TAG, "Collecting data usage (placeholder)");
        
        // Demo implementation - just increment each app's usage by a random amount
        PackageManager pm = mContext.getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        
        for (ApplicationInfo app : apps) {
            long currentUsage = mDataUsageMap.getOrDefault(app.packageName, 0L);
            // Add 0-1MB of data usage
            long newUsage = currentUsage + (long) (Math.random() * 1024 * 1024);
            mDataUsageMap.put(app.packageName, newUsage);
        }
        
        // Notify listeners
        for (SecurityListener listener : mListeners) {
            listener.onDataUsageUpdated();
        }
    }
    
    /**
     * Rotate the firewall log file.
     */
    private void rotateLogFile() {
        try {
            File logFile = new File(new File(mContext.getFilesDir(), RULES_DIR), FIREWALL_LOG);
            File backupFile = new File(new File(mContext.getFilesDir(), RULES_DIR), FIREWALL_LOG + ".1");
            
            if (logFile.exists()) {
                if (backupFile.exists()) {
                    backupFile.delete();
                }
                
                logFile.renameTo(backupFile);
            }
            
            // Write current log to file
            synchronized (mFirewallLog) {
                try (FileOutputStream fos = new FileOutputStream(logFile)) {
                    for (String entry : mFirewallLog) {
                        fos.write((entry + "\n").getBytes());
                    }
                }
            }
            
            LoggingHelper.debug(TAG, "Rotated firewall log file");
            
        } catch (IOException e) {
            LoggingHelper.error(TAG, "Error rotating firewall log", e);
        }
    }
    
    /**
     * Load security rules from storage.
     */
    private void loadRules() {
        try {
            File file = new File(new File(mContext.getFilesDir(), RULES_DIR), RULES_FILE);
            if (!file.exists()) {
                // Initialize with default rules
                initializeDefaultRules();
                return;
            }
            
            StringBuilder builder = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }
            }
            
            Gson gson = new Gson();
            JsonObject root = JsonParser.parseString(builder.toString()).getAsJsonObject();
            
            // Load settings
            if (root.has("firewallEnabled")) {
                mFirewallEnabled = root.get("firewallEnabled").getAsBoolean();
            }
            
            if (root.has("networkMonitoringEnabled")) {
                mNetworkMonitoringEnabled = root.get("networkMonitoringEnabled").getAsBoolean();
            }
            
            if (root.has("privacyProtectionEnabled")) {
                mPrivacyProtectionEnabled = root.get("privacyProtectionEnabled").getAsBoolean();
            }
            
            // Load domain blocklist
            mDomainBlocklist.clear();
            if (root.has("domainBlocklist")) {
                JsonArray blocklist = root.getAsJsonArray("domainBlocklist");
                for (JsonElement element : blocklist) {
                    try {
                        Pattern pattern = Pattern.compile(element.getAsString());
                        mDomainBlocklist.add(pattern);
                    } catch (Exception e) {
                        LoggingHelper.error(TAG, "Invalid domain pattern: " + element.getAsString(), e);
                    }
                }
            }
            
            // Load domain allowlist
            mDomainAllowlist.clear();
            if (root.has("domainAllowlist")) {
                JsonArray allowlist = root.getAsJsonArray("domainAllowlist");
                for (JsonElement element : allowlist) {
                    try {
                        Pattern pattern = Pattern.compile(element.getAsString());
                        mDomainAllowlist.add(pattern);
                    } catch (Exception e) {
                        LoggingHelper.error(TAG, "Invalid domain pattern: " + element.getAsString(), e);
                    }
                }
            }
            
            // Load app rules
            mAppRules.clear();
            if (root.has("appRules")) {
                JsonObject appRules = root.getAsJsonObject("appRules");
                for (Map.Entry<String, JsonElement> entry : appRules.entrySet()) {
                    String packageName = entry.getKey();
                    JsonArray rules = entry.getValue().getAsJsonArray();
                    
                    List<FirewallRule> ruleList = new ArrayList<>();
                    for (JsonElement element : rules) {
                        JsonObject ruleObj = element.getAsJsonObject();
                        FirewallRule rule = new FirewallRule();
                        
                        rule.type = ruleObj.get("type").getAsInt();
                        rule.destinationPattern = ruleObj.get("destinationPattern").getAsString();
                        rule.portStart = ruleObj.get("portStart").getAsInt();
                        rule.portEnd = ruleObj.get("portEnd").getAsInt();
                        rule.protocol = ruleObj.get("protocol").getAsInt();
                        
                        ruleList.add(rule);
                    }
                    
                    mAppRules.put(packageName, ruleList);
                }
            }
            
            // Load network restricted apps
            mNetworkRestrictedApps.clear();
            if (root.has("networkRestrictedApps")) {
                JsonArray restrictedApps = root.getAsJsonArray("networkRestrictedApps");
                for (JsonElement element : restrictedApps) {
                    mNetworkRestrictedApps.add(element.getAsString());
                }
            }
            
            LoggingHelper.info(TAG, "Loaded security rules: " + 
                              mDomainBlocklist.size() + " blocked domains, " + 
                              mDomainAllowlist.size() + " allowed domains, " + 
                              mAppRules.size() + " app rules, " + 
                              mNetworkRestrictedApps.size() + " restricted apps");
            
        } catch (IOException e) {
            LoggingHelper.error(TAG, "Error loading security rules", e);
            
            // Initialize with default rules
            initializeDefaultRules();
        }
    }
    
    /**
     * Initialize default security rules.
     */
    private void initializeDefaultRules() {
        mFirewallEnabled = true;
        mNetworkMonitoringEnabled = true;
        mPrivacyProtectionEnabled = true;
        
        // Add some common ad domains to blocklist
        addDomainToBlocklist("ads\\..*");
        addDomainToBlocklist(".*\\.ads\\..*");
        addDomainToBlocklist("analytics\\..*");
        addDomainToBlocklist("tracker\\..*");
        
        // Add common safe domains to allowlist
        addDomainToAllowlist(".*\\.google\\.com");
        addDomainToAllowlist(".*\\.android\\.com");
        addDomainToAllowlist(".*\\.githubusercontent\\.com");
        
        saveRules();
        
        LoggingHelper.info(TAG, "Initialized default security rules");
    }
    
    /**
     * Save security rules to storage.
     */
    private void saveRules() {
        try {
            JsonObject root = new JsonObject();
            
            // Save settings
            root.addProperty("firewallEnabled", mFirewallEnabled);
            root.addProperty("networkMonitoringEnabled", mNetworkMonitoringEnabled);
            root.addProperty("privacyProtectionEnabled", mPrivacyProtectionEnabled);
            
            // Save domain blocklist
            JsonArray blocklist = new JsonArray();
            for (Pattern pattern : mDomainBlocklist) {
                blocklist.add(pattern.pattern());
            }
            root.add("domainBlocklist", blocklist);
            
            // Save domain allowlist
            JsonArray allowlist = new JsonArray();
            for (Pattern pattern : mDomainAllowlist) {
                allowlist.add(pattern.pattern());
            }
            root.add("domainAllowlist", allowlist);
            
            // Save app rules
            JsonObject appRules = new JsonObject();
            for (Map.Entry<String, List<FirewallRule>> entry : mAppRules.entrySet()) {
                String packageName = entry.getKey();
                List<FirewallRule> rules = entry.getValue();
                
                JsonArray ruleArray = new JsonArray();
                for (FirewallRule rule : rules) {
                    JsonObject ruleObj = new JsonObject();
                    ruleObj.addProperty("type", rule.type);
                    ruleObj.addProperty("destinationPattern", rule.destinationPattern);
                    ruleObj.addProperty("portStart", rule.portStart);
                    ruleObj.addProperty("portEnd", rule.portEnd);
                    ruleObj.addProperty("protocol", rule.protocol);
                    
                    ruleArray.add(ruleObj);
                }
                
                appRules.add(packageName, ruleArray);
            }
            root.add("appRules", appRules);
            
            // Save network restricted apps
            JsonArray restrictedApps = new JsonArray();
            for (String packageName : mNetworkRestrictedApps) {
                restrictedApps.add(packageName);
            }
            root.add("networkRestrictedApps", restrictedApps);
            
            // Write to file
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String json = gson.toJson(root);
            
            File file = new File(new File(mContext.getFilesDir(), RULES_DIR), RULES_FILE);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(json.getBytes());
            }
            
            LoggingHelper.debug(TAG, "Saved security rules");
            
        } catch (IOException e) {
            LoggingHelper.error(TAG, "Error saving security rules", e);
        }
    }
    
    private String protocolToString(int protocol) {
        switch (protocol) {
            case PROTO_TCP: return "TCP";
            case PROTO_UDP: return "UDP";
            case PROTO_ICMP: return "ICMP";
            case PROTO_ALL: return "ALL";
            default: return "UNKNOWN";
        }
    }
    
    private void notifyRulesChanged() {
        for (SecurityListener listener : mListeners) {
            listener.onSecurityRulesChanged();
        }
    }
    
    private void notifyConfigChanged() {
        for (SecurityListener listener : mListeners) {
            listener.onSecurityConfigChanged(mFirewallEnabled, mNetworkMonitoringEnabled, mPrivacyProtectionEnabled);
        }
    }
    
    /**
     * Represents a firewall rule.
     */
    public static class FirewallRule {
        public int type = RULE_TYPE_BLOCK;
        public String destinationPattern = "*";
        public int portStart = 0;
        public int portEnd = 65535;
        public int protocol = PROTO_ALL;
        
        /**
         * Check if this rule matches the given connection.
         */
        public boolean matches(String destination, int port, int protocol) {
            // Check protocol
            if (this.protocol != PROTO_ALL && this.protocol != protocol) {
                return false;
            }
            
            // Check port
            if (port < portStart || port > portEnd) {
                return false;
            }
            
            // Check destination
            if (!destinationPattern.equals("*")) {
                try {
                    Pattern pattern = Pattern.compile(destinationPattern);
                    return pattern.matcher(destination).matches();
                } catch (Exception e) {
                    return false;
                }
            }
            
            return true;
        }
        
        @Override
        public String toString() {
            String typeStr = type == RULE_TYPE_ALLOW ? "ALLOW" : 
                            (type == RULE_TYPE_BLOCK ? "BLOCK" : "LOG");
            
            return typeStr + " " + destinationPattern + ":" + 
                   portStart + "-" + portEnd + " " + 
                   (protocol == PROTO_ALL ? "ALL" : 
                   (protocol == PROTO_TCP ? "TCP" : 
                   (protocol == PROTO_UDP ? "UDP" : "ICMP")));
        }
    }
    
    /**
     * Listener for security events.
     */
    public interface SecurityListener {
        /**
         * Called when security rules are changed.
         */
        void onSecurityRulesChanged();
        
        /**
         * Called when security configuration is changed.
         */
        void onSecurityConfigChanged(boolean firewallEnabled, 
                                    boolean networkMonitoringEnabled, 
                                    boolean privacyProtectionEnabled);
        
        /**
         * Called when a firewall event occurs.
         */
        void onFirewallEvent(String packageName, String destination, 
                            int port, int protocol, boolean allowed, String reason);
        
        /**
         * Called when data usage information is updated.
         */
        void onDataUsageUpdated();
    }
} 