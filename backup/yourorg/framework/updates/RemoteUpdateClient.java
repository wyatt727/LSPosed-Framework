package com.wobbz.framework.updates;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Client for checking and installing updates from a CDN.
 * Supports signature verification and delta updates.
 */
public class RemoteUpdateClient {
    private static final String TAG = "RemoteUpdateClient";
    private static final String PREF_NAME = "remote_update_prefs";
    private static final String PREF_LAST_CHECK = "last_update_check";
    private static final String UPDATE_DIR = "updates";
    
    private final Context mContext;
    private final UpdateConfig mConfig;
    private final Gson mGson;
    private final List<UpdateListener> mListeners = new ArrayList<>();
    
    /**
     * Create a new RemoteUpdateClient with the given context and config.
     */
    public RemoteUpdateClient(Context context, UpdateConfig config) {
        mContext = context.getApplicationContext();
        mConfig = config;
        mGson = new Gson();
        
        // Create updates directory if it doesn't exist
        File updateDir = new File(mContext.getFilesDir(), UPDATE_DIR);
        if (!updateDir.exists()) {
            updateDir.mkdirs();
        }
    }
    
    /**
     * Add a listener for update events.
     */
    public void addListener(UpdateListener listener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }
    
    /**
     * Remove a listener for update events.
     */
    public void removeListener(UpdateListener listener) {
        mListeners.remove(listener);
    }
    
    /**
     * Check for updates.
     * 
     * @param force Force check even if the check interval hasn't elapsed.
     */
    public void checkForUpdates(boolean force) {
        // Check if we should check for updates based on the interval
        SharedPreferences prefs = mContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        long lastCheck = prefs.getLong(PREF_LAST_CHECK, 0);
        long now = System.currentTimeMillis();
        
        if (!force && (now - lastCheck < mConfig.updateCheckInterval * 1000)) {
            // Not time to check yet
            return;
        }
        
        // Only check if we have connectivity and meet requirements
        if (shouldCheckUpdates()) {
            new CheckUpdateTask().execute();
        }
    }
    
    /**
     * Download and install an update.
     * 
     * @param updateInfo The update info to download and install.
     */
    public void downloadAndInstallUpdate(UpdateInfo updateInfo) {
        if (shouldDownloadUpdates()) {
            new DownloadUpdateTask().execute(updateInfo);
        }
    }
    
    /**
     * Determine if we should check for updates based on network conditions.
     */
    private boolean shouldCheckUpdates() {
        ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null && activeNetwork.isConnected();
        
        if (!isConnected) {
            return false;
        }
        
        // Check if we require WiFi
        if (mConfig.autoUpdate.requireWifi) {
            return activeNetwork.getType() == ConnectivityManager.TYPE_WIFI;
        }
        
        return true;
    }
    
    /**
     * Determine if we should download updates based on network and device conditions.
     */
    private boolean shouldDownloadUpdates() {
        // First check if auto-update is enabled
        if (!mConfig.autoUpdate.enabled) {
            return false;
        }
        
        // Check network conditions
        if (!shouldCheckUpdates()) {
            return false;
        }
        
        // Check if device is idle
        if (mConfig.autoUpdate.onlyWhenIdle) {
            // In a real implementation, we would check if the device is idle
            // using the PowerManager or JobScheduler
            return true; // Simplified for this example
        }
        
        return true;
    }
    
    /**
     * Verify the signature of an update file.
     * 
     * @param updateFile The update file to verify.
     * @param signature The signature as a Base64 string.
     * @param sourceName The name of the update source.
     * @return true if the signature is valid, false otherwise.
     */
    private boolean verifySignature(File updateFile, String signature, String sourceName) {
        try {
            // Find the update source with the given name
            UpdateSource source = null;
            for (UpdateSource s : mConfig.updateSources) {
                if (s.name.equals(sourceName)) {
                    source = s;
                    break;
                }
            }
            
            if (source == null) {
                Log.e(TAG, "Unknown update source: " + sourceName);
                return false;
            }
            
            // Decode the public key
            byte[] keyBytes = Base64.getDecoder().decode(source.publicKey);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            PublicKey publicKey = java.security.KeyFactory.getInstance("Ed25519").generatePublic(spec);
            
            // Read the update file
            byte[] fileBytes = java.nio.file.Files.readAllBytes(updateFile.toPath());
            
            // Decode the signature
            byte[] signatureBytes = Base64.getDecoder().decode(signature);
            
            // Verify the signature
            Signature sig = Signature.getInstance("Ed25519");
            sig.initVerify(publicKey);
            sig.update(fileBytes);
            
            return sig.verify(signatureBytes);
        } catch (Exception e) {
            Log.e(TAG, "Error verifying signature", e);
            return false;
        }
    }
    
    /**
     * Calculate the SHA-256 hash of a file.
     * 
     * @param file The file to hash.
     * @return The hash as a hex string.
     */
    private String calculateHash(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] fileBytes = java.nio.file.Files.readAllBytes(file.toPath());
            byte[] hashBytes = digest.digest(fileBytes);
            
            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.toString();
        } catch (NoSuchAlgorithmException | IOException e) {
            Log.e(TAG, "Error calculating hash", e);
            return null;
        }
    }
    
    /**
     * AsyncTask to check for updates.
     */
    private class CheckUpdateTask extends AsyncTask<Void, Void, UpdateInfo> {
        @Override
        protected UpdateInfo doInBackground(Void... voids) {
            for (UpdateSource source : mConfig.updateSources) {
                try {
                    URL url = new URL(source.url + "/updates.json");
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    
                    int responseCode = connection.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        InputStream in = new BufferedInputStream(connection.getInputStream());
                        java.util.Scanner scanner = new java.util.Scanner(in).useDelimiter("\\A");
                        String response = scanner.hasNext() ? scanner.next() : "";
                        
                        // Parse the JSON response
                        UpdateInfo updateInfo = mGson.fromJson(response, UpdateInfo.class);
                        updateInfo.source = source.name;
                        
                        return updateInfo;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error checking for updates from " + source.name, e);
                }
            }
            
            return null;
        }
        
        @Override
        protected void onPostExecute(UpdateInfo updateInfo) {
            if (updateInfo != null) {
                // Save last check time
                SharedPreferences prefs = mContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                prefs.edit().putLong(PREF_LAST_CHECK, System.currentTimeMillis()).apply();
                
                // Notify listeners
                for (UpdateListener listener : mListeners) {
                    listener.onUpdateAvailable(updateInfo);
                }
                
                // Auto-install if enabled
                if (mConfig.autoUpdate.enabled) {
                    downloadAndInstallUpdate(updateInfo);
                }
            } else {
                // Notify listeners
                for (UpdateListener listener : mListeners) {
                    listener.onNoUpdateAvailable();
                }
            }
        }
    }
    
    /**
     * AsyncTask to download and install an update.
     */
    private class DownloadUpdateTask extends AsyncTask<UpdateInfo, Integer, File> {
        private UpdateInfo mUpdateInfo;
        
        @Override
        protected File doInBackground(UpdateInfo... updateInfos) {
            mUpdateInfo = updateInfos[0];
            
            try {
                // Find the update source
                UpdateSource source = null;
                for (UpdateSource s : mConfig.updateSources) {
                    if (s.name.equals(mUpdateInfo.source)) {
                        source = s;
                        break;
                    }
                }
                
                if (source == null) {
                    Log.e(TAG, "Unknown update source: " + mUpdateInfo.source);
                    return null;
                }
                
                // Download the update
                URL url = new URL(source.url + "/" + mUpdateInfo.fileName);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                
                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    int fileLength = connection.getContentLength();
                    
                    // Create the output file
                    File updateDir = new File(mContext.getFilesDir(), UPDATE_DIR);
                    File outputFile = new File(updateDir, mUpdateInfo.fileName);
                    
                    // Download the file
                    try (InputStream in = new BufferedInputStream(connection.getInputStream());
                         FileOutputStream out = new FileOutputStream(outputFile)) {
                        
                        byte[] buffer = new byte[1024];
                        int total = 0;
                        int count;
                        
                        while ((count = in.read(buffer)) != -1) {
                            total += count;
                            out.write(buffer, 0, count);
                            
                            if (fileLength > 0) {
                                publishProgress((int) (total * 100 / fileLength));
                            }
                        }
                    }
                    
                    // Verify the file hash
                    String hash = calculateHash(outputFile);
                    if (hash == null || !hash.equals(mUpdateInfo.hash)) {
                        Log.e(TAG, "Hash mismatch for update: " + mUpdateInfo.fileName);
                        outputFile.delete();
                        return null;
                    }
                    
                    // Verify the signature
                    if (!verifySignature(outputFile, mUpdateInfo.signature, mUpdateInfo.source)) {
                        Log.e(TAG, "Invalid signature for update: " + mUpdateInfo.fileName);
                        outputFile.delete();
                        return null;
                    }
                    
                    return outputFile;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error downloading update", e);
            }
            
            return null;
        }
        
        @Override
        protected void onProgressUpdate(Integer... values) {
            // Notify listeners of download progress
            for (UpdateListener listener : mListeners) {
                listener.onUpdateDownloadProgress(mUpdateInfo, values[0]);
            }
        }
        
        @Override
        protected void onPostExecute(File updateFile) {
            if (updateFile != null) {
                // Notify listeners
                for (UpdateListener listener : mListeners) {
                    listener.onUpdateDownloaded(mUpdateInfo, updateFile);
                }
                
                // Install the update
                // In a real implementation, this would involve:
                // 1. Extracting the update (if it's a ZIP)
                // 2. Copying files to the appropriate locations
                // 3. Possibly restarting the app or showing a notification
            } else {
                // Notify listeners
                for (UpdateListener listener : mListeners) {
                    listener.onUpdateError(mUpdateInfo, "Failed to download or verify update");
                }
            }
        }
    }
    
    /**
     * Update config class.
     */
    public static class UpdateConfig {
        @SerializedName("updateSources")
        public List<UpdateSource> updateSources;
        
        @SerializedName("updateCheckInterval")
        public long updateCheckInterval;
        
        @SerializedName("autoUpdate")
        public AutoUpdate autoUpdate;
        
        public UpdateConfig() {
            updateSources = new ArrayList<>();
            updateCheckInterval = 86400; // 1 day in seconds
            autoUpdate = new AutoUpdate();
        }
    }
    
    /**
     * Update source class.
     */
    public static class UpdateSource {
        @SerializedName("name")
        public String name;
        
        @SerializedName("url")
        public String url;
        
        @SerializedName("publicKey")
        public String publicKey;
    }
    
    /**
     * Auto update config class.
     */
    public static class AutoUpdate {
        @SerializedName("enabled")
        public boolean enabled;
        
        @SerializedName("onlyWhenIdle")
        public boolean onlyWhenIdle;
        
        @SerializedName("requireWifi")
        public boolean requireWifi;
        
        public AutoUpdate() {
            enabled = false;
            onlyWhenIdle = true;
            requireWifi = true;
        }
    }
    
    /**
     * Update info class.
     */
    public static class UpdateInfo {
        @SerializedName("version")
        public String version;
        
        @SerializedName("versionCode")
        public int versionCode;
        
        @SerializedName("fileName")
        public String fileName;
        
        @SerializedName("hash")
        public String hash;
        
        @SerializedName("signature")
        public String signature;
        
        @SerializedName("changelog")
        public String changelog;
        
        @SerializedName("size")
        public long size;
        
        @SerializedName("mandatory")
        public boolean mandatory;
        
        public String source; // Not from JSON, set when update is found
    }
    
    /**
     * Listener for update events.
     */
    public interface UpdateListener {
        void onUpdateAvailable(UpdateInfo updateInfo);
        void onNoUpdateAvailable();
        void onUpdateDownloadProgress(UpdateInfo updateInfo, int progress);
        void onUpdateDownloaded(UpdateInfo updateInfo, File updateFile);
        void onUpdateError(UpdateInfo updateInfo, String error);
    }
} 