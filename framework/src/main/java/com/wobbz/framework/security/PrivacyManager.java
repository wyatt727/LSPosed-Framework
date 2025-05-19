package com.wobbz.framework.security;

import android.content.Context;
import android.location.Location;
import android.os.Build;
import android.telephony.TelephonyManager;

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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Advanced privacy protection manager.
 * Provides features like location spoofing, identity masking, and sensor control.
 */
public class PrivacyManager {
    private static final String TAG = "PrivacyManager";
    private static final String PRIVACY_CONFIG_FILE = "privacy_config.json";
    private static final String PRIVACY_DIR = "privacy";
    
    // Privacy protection types
    public static final int PRIVACY_LOCATION = 0;
    public static final int PRIVACY_IDENTITY = 1;
    public static final int PRIVACY_CONTACTS = 2;
    public static final int PRIVACY_CAMERA = 3;
    public static final int PRIVACY_MICROPHONE = 4;
    public static final int PRIVACY_STORAGE = 5;
    public static final int PRIVACY_PHONE = 6;
    public static final int PRIVACY_SENSORS = 7;
    
    // Location spoofing modes
    public static final int LOCATION_MODE_DISABLED = 0;
    public static final int LOCATION_MODE_FIXED = 1;
    public static final int LOCATION_MODE_RANDOM = 2;
    public static final int LOCATION_MODE_OFFSET = 3;
    public static final int LOCATION_MODE_CUSTOM = 4;
    
    // Identity privacy modes
    public static final int IDENTITY_MODE_DISABLED = 0;
    public static final int IDENTITY_MODE_RANDOM = 1;
    public static final int IDENTITY_MODE_FIXED = 2;
    public static final int IDENTITY_MODE_EMPTY = 3;
    
    private static PrivacyManager sInstance;
    
    private final Context mContext;
    private boolean mPrivacyEnabled = false;
    private final Map<String, AppPrivacySettings> mAppSettings = new ConcurrentHashMap<>();
    private final Map<String, FakeDeviceInfo> mFakeDevices = new HashMap<>();
    private final List<PrivacyListener> mListeners = new ArrayList<>();
    
    // Default location for fixed mode
    private double mDefaultLatitude = 37.422; // Google headquarters
    private double mDefaultLongitude = -122.084;
    private float mDefaultAccuracy = 50.0f;
    
    // Default identity for fixed mode
    private String mDefaultIMEI = "000000000000000";
    private String mDefaultAndroidId = "0000000000000000";
    private String mDefaultGSFId = "0000000000000000";
    
    /**
     * Private constructor for singleton pattern.
     */
    private PrivacyManager(Context context) {
        mContext = context.getApplicationContext();
        
        // Create privacy directory
        File privacyDir = new File(mContext.getFilesDir(), PRIVACY_DIR);
        if (!privacyDir.exists()) {
            privacyDir.mkdirs();
        }
        
        // Load privacy settings
        loadConfig();
        
        // Create a few fake device profiles
        initializeFakeDevices();
    }
    
    /**
     * Get the singleton instance.
     */
    public static synchronized PrivacyManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new PrivacyManager(context);
        }
        return sInstance;
    }
    
    /**
     * Add a privacy listener to receive privacy events.
     */
    public void addListener(PrivacyListener listener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }
    
    /**
     * Remove a privacy listener.
     */
    public void removeListener(PrivacyListener listener) {
        mListeners.remove(listener);
    }
    
    /**
     * Set the privacy enabled state.
     */
    public void setPrivacyEnabled(boolean enabled) {
        mPrivacyEnabled = enabled;
        saveConfig();
        notifyConfigChanged();
    }
    
    /**
     * Check if privacy protection is enabled.
     */
    public boolean isPrivacyEnabled() {
        return mPrivacyEnabled;
    }
    
    /**
     * Set privacy settings for an app.
     */
    public void setAppPrivacySettings(String packageName, AppPrivacySettings settings) {
        mAppSettings.put(packageName, settings);
        saveConfig();
        notifyPrivacySettingsChanged(packageName);
    }
    
    /**
     * Get privacy settings for an app.
     */
    public AppPrivacySettings getAppPrivacySettings(String packageName) {
        return mAppSettings.getOrDefault(packageName, new AppPrivacySettings());
    }
    
    /**
     * Process a location read request from an app.
     * 
     * @param packageName The app package name.
     * @param originalLocation The original location.
     * @return Modified location based on privacy settings, or null to block.
     */
    public Location processLocationRequest(String packageName, Location originalLocation) {
        if (!mPrivacyEnabled) {
            return originalLocation;
        }
        
        AppPrivacySettings settings = getAppPrivacySettings(packageName);
        if (!settings.isFeatureEnabled(PRIVACY_LOCATION)) {
            return originalLocation;
        }
        
        // Get location mode for this app
        int locationMode = settings.getFeatureMode(PRIVACY_LOCATION);
        
        switch (locationMode) {
            case LOCATION_MODE_DISABLED:
                // Block location access
                logPrivacyEvent(packageName, "Blocked location access");
                return null;
                
            case LOCATION_MODE_FIXED:
                // Return fixed location
                Location fixedLocation = new Location("privacy");
                fixedLocation.setLatitude(mDefaultLatitude);
                fixedLocation.setLongitude(mDefaultLongitude);
                fixedLocation.setAccuracy(mDefaultAccuracy);
                fixedLocation.setTime(System.currentTimeMillis());
                if (Build.VERSION.SDK_INT >= 17) {
                    fixedLocation.setElapsedRealtimeNanos(System.nanoTime());
                }
                
                logPrivacyEvent(packageName, "Fixed location: " + mDefaultLatitude + "," + mDefaultLongitude);
                return fixedLocation;
                
            case LOCATION_MODE_RANDOM:
                // Return random location (within reasonable bounds)
                Location randomLocation = new Location("privacy");
                randomLocation.setLatitude(-90.0 + Math.random() * 180.0);
                randomLocation.setLongitude(-180.0 + Math.random() * 360.0);
                randomLocation.setAccuracy(10.0f + (float) (Math.random() * 100.0));
                randomLocation.setTime(System.currentTimeMillis());
                if (Build.VERSION.SDK_INT >= 17) {
                    randomLocation.setElapsedRealtimeNanos(System.nanoTime());
                }
                
                logPrivacyEvent(packageName, "Random location: " + 
                               randomLocation.getLatitude() + "," + randomLocation.getLongitude());
                return randomLocation;
                
            case LOCATION_MODE_OFFSET:
                // Return offset from real location
                if (originalLocation == null) {
                    return null;
                }
                
                Location offsetLocation = new Location(originalLocation);
                // Add random offset of up to 5km
                double metersLat = (Math.random() * 10000.0) - 5000.0;
                double metersLng = (Math.random() * 10000.0) - 5000.0;
                
                // Convert meters to degrees (approximate)
                double latOffset = metersLat / 111111.0;
                double lngOffset = metersLng / (111111.0 * Math.cos(Math.toRadians(offsetLocation.getLatitude())));
                
                offsetLocation.setLatitude(offsetLocation.getLatitude() + latOffset);
                offsetLocation.setLongitude(offsetLocation.getLongitude() + lngOffset);
                offsetLocation.setAccuracy(offsetLocation.getAccuracy() * 2); // Double accuracy (less precise)
                
                logPrivacyEvent(packageName, "Offset location: " + 
                               offsetLocation.getLatitude() + "," + offsetLocation.getLongitude());
                return offsetLocation;
                
            case LOCATION_MODE_CUSTOM:
                // Return custom location from settings
                Location customLocation = new Location("privacy");
                customLocation.setLatitude(settings.getCustomLocationLatitude());
                customLocation.setLongitude(settings.getCustomLocationLongitude());
                customLocation.setAccuracy(settings.getCustomLocationAccuracy());
                customLocation.setTime(System.currentTimeMillis());
                if (Build.VERSION.SDK_INT >= 17) {
                    customLocation.setElapsedRealtimeNanos(System.nanoTime());
                }
                
                logPrivacyEvent(packageName, "Custom location: " + 
                               customLocation.getLatitude() + "," + customLocation.getLongitude());
                return customLocation;
                
            default:
                return originalLocation;
        }
    }
    
    /**
     * Process a device ID request from an app.
     * 
     * @param packageName The app package name.
     * @param type The type of ID (IMEI, Android ID, etc).
     * @param originalValue The original ID value.
     * @return Modified ID based on privacy settings, or null to block.
     */
    public String processDeviceIdRequest(String packageName, String type, String originalValue) {
        if (!mPrivacyEnabled) {
            return originalValue;
        }
        
        AppPrivacySettings settings = getAppPrivacySettings(packageName);
        if (!settings.isFeatureEnabled(PRIVACY_IDENTITY)) {
            return originalValue;
        }
        
        // Get identity mode for this app
        int identityMode = settings.getFeatureMode(PRIVACY_IDENTITY);
        
        switch (identityMode) {
            case IDENTITY_MODE_DISABLED:
                // Block identity access
                logPrivacyEvent(packageName, "Blocked " + type + " access");
                return null;
                
            case IDENTITY_MODE_RANDOM:
                // Return random ID
                String randomId;
                if ("IMEI".equals(type)) {
                    // Generate 15-digit IMEI
                    randomId = generateRandomImei();
                } else if ("ANDROID_ID".equals(type)) {
                    // Generate 16-char Android ID
                    randomId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
                } else {
                    // Generate generic ID
                    randomId = UUID.randomUUID().toString().replace("-", "");
                }
                
                logPrivacyEvent(packageName, "Random " + type + ": " + randomId);
                return randomId;
                
            case IDENTITY_MODE_FIXED:
                // Return fixed ID
                String fixedId;
                if ("IMEI".equals(type)) {
                    fixedId = mDefaultIMEI;
                } else if ("ANDROID_ID".equals(type)) {
                    fixedId = mDefaultAndroidId;
                } else if ("GSF_ID".equals(type)) {
                    fixedId = mDefaultGSFId;
                } else {
                    fixedId = "0000000000000000";
                }
                
                logPrivacyEvent(packageName, "Fixed " + type + ": " + fixedId);
                return fixedId;
                
            case IDENTITY_MODE_EMPTY:
                // Return empty string or zeros
                String emptyId = "";
                if ("IMEI".equals(type)) {
                    emptyId = "000000000000000";
                } else if ("ANDROID_ID".equals(type) || "GSF_ID".equals(type)) {
                    emptyId = "0000000000000000";
                }
                
                logPrivacyEvent(packageName, "Empty " + type);
                return emptyId;
                
            default:
                return originalValue;
        }
    }
    
    /**
     * Get a fake device profile for an app.
     * 
     * @param packageName The app package name.
     * @return Fake device info, or null to use real device info.
     */
    public FakeDeviceInfo getFakeDeviceInfo(String packageName) {
        if (!mPrivacyEnabled) {
            return null;
        }
        
        AppPrivacySettings settings = getAppPrivacySettings(packageName);
        if (!settings.isFeatureEnabled(PRIVACY_IDENTITY)) {
            return null;
        }
        
        String deviceProfile = settings.getCustomDeviceProfile();
        if (deviceProfile != null && !deviceProfile.isEmpty()) {
            FakeDeviceInfo info = mFakeDevices.get(deviceProfile);
            if (info != null) {
                logPrivacyEvent(packageName, "Using fake device profile: " + deviceProfile);
                return info;
            }
        }
        
        // Default to random fake device
        String[] profileNames = mFakeDevices.keySet().toArray(new String[0]);
        if (profileNames.length > 0) {
            String randomProfile = profileNames[new Random().nextInt(profileNames.length)];
            logPrivacyEvent(packageName, "Using random device profile: " + randomProfile);
            return mFakeDevices.get(randomProfile);
        }
        
        return null;
    }
    
    /**
     * Check if privacy settings should restrict a permission for an app.
     * 
     * @param packageName The app package name.
     * @param permission The permission name.
     * @return true if the permission should be blocked, false otherwise.
     */
    public boolean shouldBlockPermission(String packageName, String permission) {
        if (!mPrivacyEnabled) {
            return false;
        }
        
        AppPrivacySettings settings = getAppPrivacySettings(packageName);
        
        if (permission.contains("CAMERA") && settings.isFeatureEnabled(PRIVACY_CAMERA)) {
            logPrivacyEvent(packageName, "Blocked camera permission: " + permission);
            return true;
        }
        
        if (permission.contains("MICROPHONE") || permission.contains("RECORD_AUDIO") && 
                settings.isFeatureEnabled(PRIVACY_MICROPHONE)) {
            logPrivacyEvent(packageName, "Blocked microphone permission: " + permission);
            return true;
        }
        
        if ((permission.contains("READ_CONTACTS") || permission.contains("WRITE_CONTACTS")) && 
                settings.isFeatureEnabled(PRIVACY_CONTACTS)) {
            logPrivacyEvent(packageName, "Blocked contacts permission: " + permission);
            return true;
        }
        
        if ((permission.contains("READ_EXTERNAL_STORAGE") || permission.contains("WRITE_EXTERNAL_STORAGE")) && 
                settings.isFeatureEnabled(PRIVACY_STORAGE)) {
            logPrivacyEvent(packageName, "Blocked storage permission: " + permission);
            return true;
        }
        
        if ((permission.contains("READ_PHONE_STATE") || permission.contains("CALL_PHONE")) && 
                settings.isFeatureEnabled(PRIVACY_PHONE)) {
            logPrivacyEvent(packageName, "Blocked phone permission: " + permission);
            return true;
        }
        
        if ((permission.contains("BODY_SENSORS") || permission.contains("ACTIVITY_RECOGNITION")) && 
                settings.isFeatureEnabled(PRIVACY_SENSORS)) {
            logPrivacyEvent(packageName, "Blocked sensor permission: " + permission);
            return true;
        }
        
        return false;
    }
    
    /**
     * Generate a random IMEI that conforms to the IMEI check digit algorithm.
     */
    private String generateRandomImei() {
        Random random = new Random();
        StringBuilder imei = new StringBuilder();
        
        // Generate first 14 digits
        for (int i = 0; i < 14; i++) {
            imei.append(random.nextInt(10));
        }
        
        // Calculate check digit (Luhn algorithm)
        int sum = 0;
        for (int i = 0; i < 14; i++) {
            int digit = Character.getNumericValue(imei.charAt(i));
            if (i % 2 == 1) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
        }
        int checkDigit = (10 - (sum % 10)) % 10;
        
        // Append check digit
        imei.append(checkDigit);
        
        return imei.toString();
    }
    
    /**
     * Log a privacy event.
     */
    private void logPrivacyEvent(String packageName, String message) {
        LoggingHelper.debug(TAG, "Privacy event for " + packageName + ": " + message);
        
        // Notify listeners
        for (PrivacyListener listener : mListeners) {
            listener.onPrivacyEvent(packageName, message);
        }
    }
    
    /**
     * Load privacy configuration from storage.
     */
    private void loadConfig() {
        try {
            File file = new File(new File(mContext.getFilesDir(), PRIVACY_DIR), PRIVACY_CONFIG_FILE);
            if (!file.exists()) {
                // Initialize with default settings
                initializeDefaultSettings();
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
            
            // Load global settings
            if (root.has("privacyEnabled")) {
                mPrivacyEnabled = root.get("privacyEnabled").getAsBoolean();
            }
            
            if (root.has("defaultLatitude")) {
                mDefaultLatitude = root.get("defaultLatitude").getAsDouble();
            }
            
            if (root.has("defaultLongitude")) {
                mDefaultLongitude = root.get("defaultLongitude").getAsDouble();
            }
            
            if (root.has("defaultAccuracy")) {
                mDefaultAccuracy = root.get("defaultAccuracy").getAsFloat();
            }
            
            if (root.has("defaultIMEI")) {
                mDefaultIMEI = root.get("defaultIMEI").getAsString();
            }
            
            if (root.has("defaultAndroidId")) {
                mDefaultAndroidId = root.get("defaultAndroidId").getAsString();
            }
            
            if (root.has("defaultGSFId")) {
                mDefaultGSFId = root.get("defaultGSFId").getAsString();
            }
            
            // Load app settings
            mAppSettings.clear();
            if (root.has("appSettings")) {
                JsonObject appSettingsObj = root.getAsJsonObject("appSettings");
                for (Map.Entry<String, JsonElement> entry : appSettingsObj.entrySet()) {
                    String packageName = entry.getKey();
                    JsonObject settingsObj = entry.getValue().getAsJsonObject();
                    
                    AppPrivacySettings settings = new AppPrivacySettings();
                    
                    if (settingsObj.has("enabledFeatures")) {
                        JsonArray enabledFeatures = settingsObj.getAsJsonArray("enabledFeatures");
                        for (JsonElement element : enabledFeatures) {
                            settings.enableFeature(element.getAsInt());
                        }
                    }
                    
                    if (settingsObj.has("featureModes")) {
                        JsonObject modeObj = settingsObj.getAsJsonObject("featureModes");
                        for (Map.Entry<String, JsonElement> modeEntry : modeObj.entrySet()) {
                            int feature = Integer.parseInt(modeEntry.getKey());
                            int mode = modeEntry.getValue().getAsInt();
                            settings.setFeatureMode(feature, mode);
                        }
                    }
                    
                    if (settingsObj.has("customLocationLatitude")) {
                        settings.setCustomLocationLatitude(settingsObj.get("customLocationLatitude").getAsDouble());
                    }
                    
                    if (settingsObj.has("customLocationLongitude")) {
                        settings.setCustomLocationLongitude(settingsObj.get("customLocationLongitude").getAsDouble());
                    }
                    
                    if (settingsObj.has("customLocationAccuracy")) {
                        settings.setCustomLocationAccuracy(settingsObj.get("customLocationAccuracy").getAsFloat());
                    }
                    
                    if (settingsObj.has("customDeviceProfile")) {
                        settings.setCustomDeviceProfile(settingsObj.get("customDeviceProfile").getAsString());
                    }
                    
                    mAppSettings.put(packageName, settings);
                }
            }
            
            // Load fake device profiles
            mFakeDevices.clear();
            if (root.has("fakeDevices")) {
                JsonObject devicesObj = root.getAsJsonObject("fakeDevices");
                for (Map.Entry<String, JsonElement> entry : devicesObj.entrySet()) {
                    String profileName = entry.getKey();
                    JsonObject deviceObj = entry.getValue().getAsJsonObject();
                    
                    FakeDeviceInfo device = new FakeDeviceInfo();
                    
                    if (deviceObj.has("manufacturer")) {
                        device.manufacturer = deviceObj.get("manufacturer").getAsString();
                    }
                    
                    if (deviceObj.has("model")) {
                        device.model = deviceObj.get("model").getAsString();
                    }
                    
                    if (deviceObj.has("product")) {
                        device.product = deviceObj.get("product").getAsString();
                    }
                    
                    if (deviceObj.has("device")) {
                        device.device = deviceObj.get("device").getAsString();
                    }
                    
                    if (deviceObj.has("brand")) {
                        device.brand = deviceObj.get("brand").getAsString();
                    }
                    
                    if (deviceObj.has("board")) {
                        device.board = deviceObj.get("board").getAsString();
                    }
                    
                    if (deviceObj.has("fingerprint")) {
                        device.fingerprint = deviceObj.get("fingerprint").getAsString();
                    }
                    
                    if (deviceObj.has("apiLevel")) {
                        device.apiLevel = deviceObj.get("apiLevel").getAsInt();
                    }
                    
                    mFakeDevices.put(profileName, device);
                }
            }
            
            LoggingHelper.info(TAG, "Loaded privacy config: " + 
                              (mPrivacyEnabled ? "enabled" : "disabled") + ", " + 
                              mAppSettings.size() + " app settings, " +
                              mFakeDevices.size() + " fake device profiles");
            
        } catch (IOException e) {
            LoggingHelper.error(TAG, "Error loading privacy config", e);
            
            // Initialize with default settings
            initializeDefaultSettings();
        }
    }
    
    /**
     * Initialize default privacy settings.
     */
    private void initializeDefaultSettings() {
        mPrivacyEnabled = true;
        
        // Create default settings for common apps
        AppPrivacySettings facebookSettings = new AppPrivacySettings();
        facebookSettings.enableFeature(PRIVACY_LOCATION);
        facebookSettings.enableFeature(PRIVACY_IDENTITY);
        facebookSettings.setFeatureMode(PRIVACY_LOCATION, LOCATION_MODE_FIXED);
        facebookSettings.setFeatureMode(PRIVACY_IDENTITY, IDENTITY_MODE_RANDOM);
        mAppSettings.put("com.facebook.katana", facebookSettings);
        
        AppPrivacySettings adSettings = new AppPrivacySettings();
        adSettings.enableFeature(PRIVACY_LOCATION);
        adSettings.enableFeature(PRIVACY_IDENTITY);
        adSettings.setFeatureMode(PRIVACY_LOCATION, LOCATION_MODE_DISABLED);
        adSettings.setFeatureMode(PRIVACY_IDENTITY, IDENTITY_MODE_RANDOM);
        mAppSettings.put("com.google.android.gms.ads", adSettings);
        
        saveConfig();
        
        LoggingHelper.info(TAG, "Initialized default privacy settings");
    }
    
    /**
     * Initialize fake device profiles.
     */
    private void initializeFakeDevices() {
        // Pixel 6
        FakeDeviceInfo pixel6 = new FakeDeviceInfo();
        pixel6.manufacturer = "Google";
        pixel6.model = "Pixel 6";
        pixel6.product = "oriole";
        pixel6.device = "oriole";
        pixel6.brand = "google";
        pixel6.board = "oriole";
        pixel6.fingerprint = "google/oriole/oriole:12/SP2A.220505.002/8353555:user/release-keys";
        pixel6.apiLevel = 31;
        mFakeDevices.put("Pixel 6", pixel6);
        
        // Samsung Galaxy S21
        FakeDeviceInfo s21 = new FakeDeviceInfo();
        s21.manufacturer = "Samsung";
        s21.model = "SM-G991U";
        s21.product = "z3q";
        s21.device = "z3q";
        s21.brand = "samsung";
        s21.board = "lahaina";
        s21.fingerprint = "samsung/z3qub/z3q:12/SP1A.210812.016/G991USQS5CVFB:user/release-keys";
        s21.apiLevel = 31;
        mFakeDevices.put("Galaxy S21", s21);
        
        // OnePlus 9 Pro
        FakeDeviceInfo oneplus9Pro = new FakeDeviceInfo();
        oneplus9Pro.manufacturer = "OnePlus";
        oneplus9Pro.model = "OnePlus 9 Pro";
        oneplus9Pro.product = "OnePlus9Pro";
        oneplus9Pro.device = "OnePlus9Pro";
        oneplus9Pro.brand = "OnePlus";
        oneplus9Pro.board = "lahaina";
        oneplus9Pro.fingerprint = "OnePlus/OnePlus9Pro/OnePlus9Pro:12/SKQ1.210216.001/R.202201312243:user/release-keys";
        oneplus9Pro.apiLevel = 31;
        mFakeDevices.put("OnePlus 9 Pro", oneplus9Pro);
    }
    
    /**
     * Save privacy configuration to storage.
     */
    private void saveConfig() {
        try {
            JsonObject root = new JsonObject();
            
            // Save global settings
            root.addProperty("privacyEnabled", mPrivacyEnabled);
            root.addProperty("defaultLatitude", mDefaultLatitude);
            root.addProperty("defaultLongitude", mDefaultLongitude);
            root.addProperty("defaultAccuracy", mDefaultAccuracy);
            root.addProperty("defaultIMEI", mDefaultIMEI);
            root.addProperty("defaultAndroidId", mDefaultAndroidId);
            root.addProperty("defaultGSFId", mDefaultGSFId);
            
            // Save app settings
            JsonObject appSettingsObj = new JsonObject();
            for (Map.Entry<String, AppPrivacySettings> entry : mAppSettings.entrySet()) {
                String packageName = entry.getKey();
                AppPrivacySettings settings = entry.getValue();
                
                JsonObject settingsObj = new JsonObject();
                
                // Save enabled features
                JsonArray enabledFeatures = new JsonArray();
                for (int feature : settings.getEnabledFeatures()) {
                    enabledFeatures.add(feature);
                }
                settingsObj.add("enabledFeatures", enabledFeatures);
                
                // Save feature modes
                JsonObject modeObj = new JsonObject();
                for (Map.Entry<Integer, Integer> modeEntry : settings.getFeatureModes().entrySet()) {
                    modeObj.addProperty(modeEntry.getKey().toString(), modeEntry.getValue());
                }
                settingsObj.add("featureModes", modeObj);
                
                // Save custom location
                settingsObj.addProperty("customLocationLatitude", settings.getCustomLocationLatitude());
                settingsObj.addProperty("customLocationLongitude", settings.getCustomLocationLongitude());
                settingsObj.addProperty("customLocationAccuracy", settings.getCustomLocationAccuracy());
                
                // Save custom device profile
                if (settings.getCustomDeviceProfile() != null) {
                    settingsObj.addProperty("customDeviceProfile", settings.getCustomDeviceProfile());
                }
                
                appSettingsObj.add(packageName, settingsObj);
            }
            root.add("appSettings", appSettingsObj);
            
            // Save fake devices
            JsonObject devicesObj = new JsonObject();
            for (Map.Entry<String, FakeDeviceInfo> entry : mFakeDevices.entrySet()) {
                String profileName = entry.getKey();
                FakeDeviceInfo device = entry.getValue();
                
                JsonObject deviceObj = new JsonObject();
                deviceObj.addProperty("manufacturer", device.manufacturer);
                deviceObj.addProperty("model", device.model);
                deviceObj.addProperty("product", device.product);
                deviceObj.addProperty("device", device.device);
                deviceObj.addProperty("brand", device.brand);
                deviceObj.addProperty("board", device.board);
                deviceObj.addProperty("fingerprint", device.fingerprint);
                deviceObj.addProperty("apiLevel", device.apiLevel);
                
                devicesObj.add(profileName, deviceObj);
            }
            root.add("fakeDevices", devicesObj);
            
            // Write to file
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String json = gson.toJson(root);
            
            File file = new File(new File(mContext.getFilesDir(), PRIVACY_DIR), PRIVACY_CONFIG_FILE);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(json.getBytes());
            }
            
            LoggingHelper.debug(TAG, "Saved privacy config");
            
        } catch (IOException e) {
            LoggingHelper.error(TAG, "Error saving privacy config", e);
        }
    }
    
    private void notifyConfigChanged() {
        for (PrivacyListener listener : mListeners) {
            listener.onPrivacyConfigChanged(mPrivacyEnabled);
        }
    }
    
    private void notifyPrivacySettingsChanged(String packageName) {
        for (PrivacyListener listener : mListeners) {
            listener.onPrivacySettingsChanged(packageName);
        }
    }
    
    /**
     * Privacy settings for an app.
     */
    public static class AppPrivacySettings {
        private final Set<Integer> mEnabledFeatures = new HashSet<>();
        private final Map<Integer, Integer> mFeatureModes = new HashMap<>();
        private double mCustomLocationLatitude = 37.422;
        private double mCustomLocationLongitude = -122.084;
        private float mCustomLocationAccuracy = 50.0f;
        private String mCustomDeviceProfile = null;
        
        /**
         * Enable a privacy feature.
         */
        public void enableFeature(int feature) {
            mEnabledFeatures.add(feature);
        }
        
        /**
         * Disable a privacy feature.
         */
        public void disableFeature(int feature) {
            mEnabledFeatures.remove(feature);
        }
        
        /**
         * Check if a feature is enabled.
         */
        public boolean isFeatureEnabled(int feature) {
            return mEnabledFeatures.contains(feature);
        }
        
        /**
         * Set the mode for a feature.
         */
        public void setFeatureMode(int feature, int mode) {
            mFeatureModes.put(feature, mode);
        }
        
        /**
         * Get the mode for a feature.
         */
        public int getFeatureMode(int feature) {
            return mFeatureModes.getOrDefault(feature, 0);
        }
        
        /**
         * Get all enabled features.
         */
        public Set<Integer> getEnabledFeatures() {
            return new HashSet<>(mEnabledFeatures);
        }
        
        /**
         * Get all feature modes.
         */
        public Map<Integer, Integer> getFeatureModes() {
            return new HashMap<>(mFeatureModes);
        }
        
        /**
         * Set custom location for location spoofing.
         */
        public void setCustomLocation(double latitude, double longitude, float accuracy) {
            mCustomLocationLatitude = latitude;
            mCustomLocationLongitude = longitude;
            mCustomLocationAccuracy = accuracy;
        }
        
        /**
         * Get custom location latitude.
         */
        public double getCustomLocationLatitude() {
            return mCustomLocationLatitude;
        }
        
        /**
         * Set custom location latitude.
         */
        public void setCustomLocationLatitude(double latitude) {
            mCustomLocationLatitude = latitude;
        }
        
        /**
         * Get custom location longitude.
         */
        public double getCustomLocationLongitude() {
            return mCustomLocationLongitude;
        }
        
        /**
         * Set custom location longitude.
         */
        public void setCustomLocationLongitude(double longitude) {
            mCustomLocationLongitude = longitude;
        }
        
        /**
         * Get custom location accuracy.
         */
        public float getCustomLocationAccuracy() {
            return mCustomLocationAccuracy;
        }
        
        /**
         * Set custom location accuracy.
         */
        public void setCustomLocationAccuracy(float accuracy) {
            mCustomLocationAccuracy = accuracy;
        }
        
        /**
         * Get custom device profile name.
         */
        public String getCustomDeviceProfile() {
            return mCustomDeviceProfile;
        }
        
        /**
         * Set custom device profile name.
         */
        public void setCustomDeviceProfile(String profileName) {
            mCustomDeviceProfile = profileName;
        }
    }
    
    /**
     * Fake device information for device spoofing.
     */
    public static class FakeDeviceInfo {
        public String manufacturer;
        public String model;
        public String product;
        public String device;
        public String brand;
        public String board;
        public String fingerprint;
        public int apiLevel;
    }
    
    /**
     * Listener for privacy events.
     */
    public interface PrivacyListener {
        /**
         * Called when privacy configuration is changed.
         */
        void onPrivacyConfigChanged(boolean privacyEnabled);
        
        /**
         * Called when app privacy settings are changed.
         */
        void onPrivacySettingsChanged(String packageName);
        
        /**
         * Called when a privacy event occurs.
         */
        void onPrivacyEvent(String packageName, String message);
    }
} 