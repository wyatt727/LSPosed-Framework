package com.wobbz.framework.updates;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import com.wobbz.framework.development.LoggingHelper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Class for checking for updates using GitHub API.
 */
public class UpdateChecker {
    private static final String TAG = "UpdateChecker";
    private static final String UPDATE_FILE = "/data/data/com.wobbz.lsposedframework/files/update_info.json";
    private static final String GITHUB_API_URL = "https://api.github.com/repos/wobbz/LSPosedFramework/releases/latest";
    private static final long UPDATE_CHECK_INTERVAL = TimeUnit.HOURS.toMillis(24);
    
    private final Context mContext;
    private final Handler mHandler;
    private final List<UpdateListener> mListeners = new ArrayList<>();
    
    private UpdateInfo mCachedUpdateInfo;
    
    /**
     * Create a new update checker.
     * 
     * @param context The application context.
     */
    public UpdateChecker(Context context) {
        mContext = context.getApplicationContext();
        mHandler = new Handler(Looper.getMainLooper());
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
        // First check if we have a cached result
        if (!force && mCachedUpdateInfo != null) {
            notifyListeners(mCachedUpdateInfo);
            return;
        }
        
        // Then check if we have a saved result
        File updateFile = new File(UPDATE_FILE);
        if (!force && updateFile.exists()) {
            try {
                UpdateInfo updateInfo = readUpdateInfoFromFile(updateFile);
                
                // Check if the info is still valid (not too old)
                long now = System.currentTimeMillis() / 1000;
                if (now - updateInfo.checkTime < UPDATE_CHECK_INTERVAL / 1000) {
                    mCachedUpdateInfo = updateInfo;
                    notifyListeners(updateInfo);
                    return;
                }
            } catch (IOException | JsonSyntaxException e) {
                LoggingHelper.error(TAG, "Error reading update info", e);
            }
        }
        
        // If we reach here, we need to check for updates
        // First try using the script
        if (runUpdateScript(force)) {
            // Script was executed, it will create the update file
            // We'll read it in a moment
            LoggingHelper.info(TAG, "Update script executed successfully");
        } else {
            // Script failed, fall back to direct API call
            LoggingHelper.info(TAG, "Update script failed, falling back to direct API call");
            new CheckUpdateTask().execute(force);
            return;
        }
        
        // Read the update file created by the script
        if (updateFile.exists()) {
            try {
                UpdateInfo updateInfo = readUpdateInfoFromFile(updateFile);
                mCachedUpdateInfo = updateInfo;
                notifyListeners(updateInfo);
            } catch (IOException | JsonSyntaxException e) {
                LoggingHelper.error(TAG, "Error reading update info after script execution", e);
                new CheckUpdateTask().execute(force);
            }
        } else {
            LoggingHelper.error(TAG, "Update file not found after script execution");
            new CheckUpdateTask().execute(force);
        }
    }
    
    /**
     * Get the currently cached update info.
     * 
     * @return The cached update info, or null if none.
     */
    public UpdateInfo getCachedUpdateInfo() {
        return mCachedUpdateInfo;
    }
    
    /**
     * Run the update script.
     * 
     * @param force Force check.
     * @return true if the script was executed successfully, false otherwise.
     */
    private boolean runUpdateScript(boolean force) {
        try {
            // Get the script from resources
            InputStream inputStream = mContext.getAssets().open("META-INF/xposed/check-updates.sh");
            File scriptFile = new File(mContext.getCacheDir(), "check-updates.sh");
            
            // Copy the script to a temporary file
            try (java.io.FileOutputStream outputStream = new java.io.FileOutputStream(scriptFile)) {
                byte[] buffer = new byte[1024];
                int length;
                while ((length = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, length);
                }
            }
            
            // Make the script executable
            if (!scriptFile.setExecutable(true)) {
                LoggingHelper.error(TAG, "Failed to make script executable");
                return false;
            }
            
            // Build the command
            String[] command = {
                "su",
                "-c",
                scriptFile.getAbsolutePath() + (force ? " --force" : "")
            };
            
            // Execute the script
            Process process = Runtime.getRuntime().exec(command);
            
            // Wait for the script to complete
            int exitCode = process.waitFor();
            
            // Check the result
            if (exitCode != 0) {
                LoggingHelper.error(TAG, "Script exited with code " + exitCode);
                return false;
            }
            
            return true;
        } catch (IOException | InterruptedException e) {
            LoggingHelper.error(TAG, "Error running update script", e);
            return false;
        }
    }
    
    /**
     * Read update info from a file.
     * 
     * @param file The file to read.
     * @return The update info.
     */
    private UpdateInfo readUpdateInfoFromFile(File file) throws IOException, JsonSyntaxException {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            StringBuilder json = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                json.append(line);
            }
            
            Gson gson = new Gson();
            return gson.fromJson(json.toString(), UpdateInfo.class);
        }
    }
    
    /**
     * Notify listeners of an update.
     * 
     * @param updateInfo The update info.
     */
    private void notifyListeners(final UpdateInfo updateInfo) {
        mHandler.post(() -> {
            for (UpdateListener listener : mListeners) {
                if (updateInfo.isNewer) {
                    listener.onUpdateAvailable(updateInfo);
                } else {
                    listener.onNoUpdateAvailable();
                }
            }
        });
    }
    
    /**
     * AsyncTask to check for updates directly using the GitHub API.
     */
    private class CheckUpdateTask extends AsyncTask<Boolean, Void, UpdateInfo> {
        @Override
        protected UpdateInfo doInBackground(Boolean... params) {
            boolean force = params.length > 0 && params[0];
            
            try {
                // Get the version info from the module.prop file
                String currentVersion = "1.0.0"; // Default fallback
                int currentVersionCode = 1; // Default fallback
                
                try {
                    // Read it from resources
                    InputStream inputStream = mContext.getAssets().open("META-INF/xposed/module.prop");
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("version=")) {
                            currentVersion = line.substring("version=".length());
                        } else if (line.startsWith("versionCode=")) {
                            currentVersionCode = Integer.parseInt(line.substring("versionCode=".length()));
                        }
                    }
                } catch (IOException e) {
                    LoggingHelper.error(TAG, "Error reading module.prop", e);
                }
                
                // Connect to GitHub API
                URL url = new URL(GITHUB_API_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                
                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // Read the response
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        
                        // Parse the JSON response
                        Gson gson = new Gson();
                        GitHubRelease release = gson.fromJson(response.toString(), GitHubRelease.class);
                        
                        // Create the update info
                        UpdateInfo updateInfo = new UpdateInfo();
                        updateInfo.latestVersion = release.tagName.startsWith("v") ?
                            release.tagName.substring(1) : release.tagName;
                        updateInfo.latestVersionCode = release.id;
                        updateInfo.currentVersion = currentVersion;
                        updateInfo.currentVersionCode = currentVersionCode;
                        updateInfo.downloadUrl = getDownloadUrl(release);
                        updateInfo.changelog = release.body;
                        updateInfo.isNewer = release.id > currentVersionCode;
                        updateInfo.checkTime = System.currentTimeMillis() / 1000;
                        
                        // Write the update info to a file for next time
                        writeUpdateInfoToFile(updateInfo);
                        
                        return updateInfo;
                    }
                } else {
                    LoggingHelper.error(TAG, "GitHub API returned " + responseCode);
                }
            } catch (Exception e) {
                LoggingHelper.error(TAG, "Error checking for updates", e);
            }
            
            return null;
        }
        
        @Override
        protected void onPostExecute(UpdateInfo updateInfo) {
            if (updateInfo != null) {
                mCachedUpdateInfo = updateInfo;
                notifyListeners(updateInfo);
            } else {
                // Notify listeners that no update is available
                for (UpdateListener listener : mListeners) {
                    listener.onNoUpdateAvailable();
                }
            }
        }
        
        /**
         * Write update info to a file.
         * 
         * @param updateInfo The update info.
         */
        private void writeUpdateInfoToFile(UpdateInfo updateInfo) {
            try {
                File updateFile = new File(UPDATE_FILE);
                
                // Create parent directories if they don't exist
                File parent = updateFile.getParentFile();
                if (!parent.exists()) {
                    parent.mkdirs();
                }
                
                // Write the file
                try (java.io.FileWriter writer = new java.io.FileWriter(updateFile)) {
                    Gson gson = new Gson();
                    writer.write(gson.toJson(updateInfo));
                }
            } catch (IOException e) {
                LoggingHelper.error(TAG, "Error writing update info", e);
            }
        }
        
        /**
         * Get the download URL from a GitHub release.
         * 
         * @param release The GitHub release.
         * @return The download URL, or null if not found.
         */
        private String getDownloadUrl(GitHubRelease release) {
            if (release.assets != null) {
                for (GitHubAsset asset : release.assets) {
                    if (asset.name != null && asset.name.endsWith(".apk")) {
                        return asset.browserDownloadUrl;
                    }
                }
            }
            return null;
        }
    }
    
    /**
     * GitHub API release object.
     */
    private static class GitHubRelease {
        @SerializedName("id")
        public int id;
        
        @SerializedName("tag_name")
        public String tagName;
        
        @SerializedName("name")
        public String name;
        
        @SerializedName("body")
        public String body;
        
        @SerializedName("assets")
        public List<GitHubAsset> assets;
    }
    
    /**
     * GitHub API asset object.
     */
    private static class GitHubAsset {
        @SerializedName("name")
        public String name;
        
        @SerializedName("browser_download_url")
        public String browserDownloadUrl;
    }
    
    /**
     * Update info class.
     */
    public static class UpdateInfo {
        @SerializedName("latestVersion")
        public String latestVersion;
        
        @SerializedName("latestVersionCode")
        public int latestVersionCode;
        
        @SerializedName("currentVersion")
        public String currentVersion;
        
        @SerializedName("currentVersionCode")
        public int currentVersionCode;
        
        @SerializedName("downloadUrl")
        public String downloadUrl;
        
        @SerializedName("changelog")
        public String changelog;
        
        @SerializedName("isNewer")
        public boolean isNewer;
        
        @SerializedName("checkTime")
        public long checkTime;
    }
    
    /**
     * Listener for update events.
     */
    public interface UpdateListener {
        /**
         * Called when an update is available.
         * 
         * @param updateInfo The update info.
         */
        void onUpdateAvailable(UpdateInfo updateInfo);
        
        /**
         * Called when no update is available.
         */
        void onNoUpdateAvailable();
    }
} 