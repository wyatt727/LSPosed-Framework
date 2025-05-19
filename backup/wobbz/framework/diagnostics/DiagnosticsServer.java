package com.wobbz.framework.diagnostics;

import android.content.Context;
import android.os.Build;
import android.os.SystemClock;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.wobbz.framework.analytics.AnalyticsManager;
import com.wobbz.framework.development.LoggingHelper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Local HTTP server for diagnostics and hook tracking.
 * Provides real-time data about the framework and modules.
 */
public class DiagnosticsServer implements AnalyticsManager.PerformanceListener {
    private static final String TAG = "DiagnosticsServer";
    private static final int MAX_HISTORY_SIZE = 100;
    private static final int DEFAULT_PORT = 8082;
    
    private static DiagnosticsServer sInstance;
    
    private final Context mContext;
    private final int mPort;
    private final AtomicBoolean mRunning = new AtomicBoolean(false);
    private final ScheduledExecutorService mExecutor = Executors.newSingleThreadScheduledExecutor();
    private final List<HookEvent> mHookHistory = Collections.synchronizedList(new ArrayList<>());
    private final List<MemoryEvent> mMemoryHistory = Collections.synchronizedList(new ArrayList<>());
    
    private ServerSocket mServerSocket;
    private AnalyticsManager mAnalyticsManager;
    private final long mStartTime = SystemClock.elapsedRealtime();
    
    /**
     * Private constructor for singleton pattern.
     */
    private DiagnosticsServer(Context context, int port) {
        mContext = context.getApplicationContext();
        mPort = port;
        
        // Connect to analytics manager
        mAnalyticsManager = AnalyticsManager.getInstance(context);
        mAnalyticsManager.addListener(this);
    }
    
    /**
     * Get the singleton instance.
     */
    public static synchronized DiagnosticsServer getInstance(Context context) {
        return getInstance(context, DEFAULT_PORT);
    }
    
    /**
     * Get the singleton instance with a specific port.
     */
    public static synchronized DiagnosticsServer getInstance(Context context, int port) {
        if (sInstance == null) {
            sInstance = new DiagnosticsServer(context, port);
        }
        return sInstance;
    }
    
    /**
     * Start the diagnostics server.
     */
    public void start() {
        if (mRunning.getAndSet(true)) {
            LoggingHelper.info(TAG, "Server already running");
            return;
        }
        
        mExecutor.execute(this::runServer);
    }
    
    /**
     * Stop the diagnostics server.
     */
    public void stop() {
        if (!mRunning.getAndSet(false)) {
            return;
        }
        
        try {
            if (mServerSocket != null && !mServerSocket.isClosed()) {
                mServerSocket.close();
                mServerSocket = null;
            }
        } catch (IOException e) {
            LoggingHelper.error(TAG, "Error stopping server", e);
        }
    }
    
    /**
     * Run the HTTP server.
     */
    private void runServer() {
        try {
            mServerSocket = new ServerSocket(mPort);
            LoggingHelper.info(TAG, "Diagnostics server started on port " + mPort);
            
            while (mRunning.get()) {
                Socket clientSocket = mServerSocket.accept();
                handleRequest(clientSocket);
            }
        } catch (IOException e) {
            if (mRunning.get()) {
                LoggingHelper.error(TAG, "Error running server", e);
            }
        } finally {
            mRunning.set(false);
        }
    }
    
    /**
     * Handle an HTTP request.
     */
    private void handleRequest(Socket socket) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String line = reader.readLine();
            if (line == null) {
                socket.close();
                return;
            }
            
            // Parse request line
            String[] parts = line.split(" ");
            if (parts.length != 3) {
                sendError(socket, 400, "Bad Request");
                return;
            }
            
            String method = parts[0];
            String path = parts[1];
            
            // Skip headers
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                // Skip header
            }
            
            // Handle request
            if ("GET".equals(method)) {
                if ("/".equals(path) || "/index.html".equals(path)) {
                    sendHtml(socket);
                } else if ("/api/stats".equals(path)) {
                    sendStats(socket);
                } else if ("/api/hooks".equals(path)) {
                    sendHookHistory(socket);
                } else if ("/api/memory".equals(path)) {
                    sendMemoryHistory(socket);
                } else {
                    sendError(socket, 404, "Not Found");
                }
            } else {
                sendError(socket, 405, "Method Not Allowed");
            }
            
        } catch (IOException e) {
            LoggingHelper.error(TAG, "Error handling request", e);
            try {
                sendError(socket, 500, "Internal Server Error");
            } catch (IOException ex) {
                // Ignore
            }
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }
    
    /**
     * Send an HTML response.
     */
    private void sendHtml(Socket socket) throws IOException {
        String html = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "  <title>LSPosed Diagnostics</title>\n" +
                "  <meta charset=\"utf-8\">\n" +
                "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n" +
                "  <style>\n" +
                "    body { font-family: Arial, sans-serif; margin: 0; padding: 20px; }\n" +
                "    h1 { color: #333; }\n" +
                "    .card { background: #f5f5f5; border-radius: 5px; padding: 15px; margin-bottom: 20px; }\n" +
                "    .hook-item { background: #fff; border: 1px solid #ddd; padding: 10px; margin-bottom: 5px; }\n" +
                "    .success { color: green; }\n" +
                "    .failure { color: red; }\n" +
                "  </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "  <h1>LSPosed Diagnostics</h1>\n" +
                "  \n" +
                "  <div class=\"card\">\n" +
                "    <h2>System Info</h2>\n" +
                "    <div id=\"system-info\">Loading...</div>\n" +
                "  </div>\n" +
                "  \n" +
                "  <div class=\"card\">\n" +
                "    <h2>Hook History</h2>\n" +
                "    <div id=\"hook-history\">Loading...</div>\n" +
                "  </div>\n" +
                "  \n" +
                "  <div class=\"card\">\n" +
                "    <h2>Memory Usage</h2>\n" +
                "    <div id=\"memory-usage\">Loading...</div>\n" +
                "  </div>\n" +
                "  \n" +
                "  <script>\n" +
                "    function refreshStats() {\n" +
                "      fetch('/api/stats')\n" +
                "        .then(response => response.json())\n" +
                "        .then(data => {\n" +
                "          const infoDiv = document.getElementById('system-info');\n" +
                "          infoDiv.innerHTML = `\n" +
                "            <p>Device: ${data.device}</p>\n" +
                "            <p>Uptime: ${Math.floor(data.uptime / 60000)} minutes</p>\n" +
                "            <p>Analytics Enabled: ${data.analyticsEnabled}</p>\n" +
                "          `;\n" +
                "        });\n" +
                "        \n" +
                "      fetch('/api/hooks')\n" +
                "        .then(response => response.json())\n" +
                "        .then(data => {\n" +
                "          const historyDiv = document.getElementById('hook-history');\n" +
                "          if (data.hooks.length === 0) {\n" +
                "            historyDiv.innerHTML = '<p>No hook events recorded</p>';\n" +
                "            return;\n" +
                "          }\n" +
                "          \n" +
                "          let html = '';\n" +
                "          data.hooks.forEach(hook => {\n" +
                "            const statusClass = hook.success ? 'success' : 'failure';\n" +
                "            html += `\n" +
                "              <div class=\"hook-item\">\n" +
                "                <p><b>${hook.hookId}</b> (${hook.moduleId})</p>\n" +
                "                <p>Target: ${hook.targetPackage}</p>\n" +
                "                <p>Execution time: ${hook.executionTime}ms</p>\n" +
                "                <p>Status: <span class=\"${statusClass}\">${hook.success ? 'Success' : 'Failure'}</span></p>\n" +
                "                <p>Timestamp: ${new Date(hook.timestamp).toLocaleString()}</p>\n" +
                "              </div>\n" +
                "            `;\n" +
                "          });\n" +
                "          historyDiv.innerHTML = html;\n" +
                "        });\n" +
                "        \n" +
                "      fetch('/api/memory')\n" +
                "        .then(response => response.json())\n" +
                "        .then(data => {\n" +
                "          const memoryDiv = document.getElementById('memory-usage');\n" +
                "          if (data.memory.length === 0) {\n" +
                "            memoryDiv.innerHTML = '<p>No memory events recorded</p>';\n" +
                "            return;\n" +
                "          }\n" +
                "          \n" +
                "          let html = '';\n" +
                "          data.memory.forEach(mem => {\n" +
                "            const deltaClass = mem.delta > 0 ? 'failure' : 'success';\n" +
                "            html += `\n" +
                "              <div class=\"hook-item\">\n" +
                "                <p><b>${mem.moduleId}</b></p>\n" +
                "                <p>Memory usage: ${Math.floor(mem.usage / 1024)} KB</p>\n" +
                "                <p>Delta: <span class=\"${deltaClass}\">${Math.floor(mem.delta / 1024)} KB</span></p>\n" +
                "                <p>Timestamp: ${new Date(mem.timestamp).toLocaleString()}</p>\n" +
                "              </div>\n" +
                "            `;\n" +
                "          });\n" +
                "          memoryDiv.innerHTML = html;\n" +
                "        });\n" +
                "    }\n" +
                "    \n" +
                "    // Refresh data every 5 seconds\n" +
                "    refreshStats();\n" +
                "    setInterval(refreshStats, 5000);\n" +
                "  </script>\n" +
                "</body>\n" +
                "</html>";
        
        sendResponse(socket, 200, "text/html", html);
    }
    
    /**
     * Send system stats as JSON.
     */
    private void sendStats(Socket socket) throws IOException {
        JsonObject data = new JsonObject();
        
        // Device info
        data.addProperty("device", 
                Build.MANUFACTURER + " " + Build.MODEL + " (Android " + Build.VERSION.RELEASE + ")");
        
        // Uptime
        data.addProperty("uptime", SystemClock.elapsedRealtime() - mStartTime);
        
        // Analytics status
        data.addProperty("analyticsEnabled", mAnalyticsManager.isAnalyticsEnabled());
        data.addProperty("crashReportingEnabled", mAnalyticsManager.isCrashReportingEnabled());
        
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(data);
        
        sendResponse(socket, 200, "application/json", json);
    }
    
    /**
     * Send hook history as JSON.
     */
    private void sendHookHistory(Socket socket) throws IOException {
        JsonObject data = new JsonObject();
        JsonArray hooks = new JsonArray();
        
        synchronized (mHookHistory) {
            for (HookEvent event : mHookHistory) {
                JsonObject hookObj = new JsonObject();
                hookObj.addProperty("hookId", event.hookId);
                hookObj.addProperty("moduleId", event.moduleId);
                hookObj.addProperty("targetPackage", event.targetPackage);
                hookObj.addProperty("executionTime", event.executionTime / 1000000); // ns to ms
                hookObj.addProperty("success", event.success);
                hookObj.addProperty("timestamp", event.timestamp);
                
                hooks.add(hookObj);
            }
        }
        
        data.add("hooks", hooks);
        
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(data);
        
        sendResponse(socket, 200, "application/json", json);
    }
    
    /**
     * Send memory history as JSON.
     */
    private void sendMemoryHistory(Socket socket) throws IOException {
        JsonObject data = new JsonObject();
        JsonArray memory = new JsonArray();
        
        synchronized (mMemoryHistory) {
            for (MemoryEvent event : mMemoryHistory) {
                JsonObject memObj = new JsonObject();
                memObj.addProperty("moduleId", event.moduleId);
                memObj.addProperty("usage", event.usage);
                memObj.addProperty("delta", event.delta);
                memObj.addProperty("timestamp", event.timestamp);
                
                memory.add(memObj);
            }
        }
        
        data.add("memory", memory);
        
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(data);
        
        sendResponse(socket, 200, "application/json", json);
    }
    
    /**
     * Send an HTTP response.
     */
    private void sendResponse(Socket socket, int statusCode, String contentType, String body) throws IOException {
        String statusText = getStatusText(statusCode);
        
        OutputStream output = socket.getOutputStream();
        output.write(("HTTP/1.1 " + statusCode + " " + statusText + "\r\n").getBytes());
        output.write(("Content-Type: " + contentType + "\r\n").getBytes());
        output.write(("Content-Length: " + body.length() + "\r\n").getBytes());
        output.write("\r\n".getBytes());
        output.write(body.getBytes());
        output.flush();
    }
    
    /**
     * Send an error response.
     */
    private void sendError(Socket socket, int statusCode, String statusText) throws IOException {
        String body = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "  <title>" + statusCode + " " + statusText + "</title>\n" +
                "</head>\n" +
                "<body>\n" +
                "  <h1>" + statusCode + " " + statusText + "</h1>\n" +
                "</body>\n" +
                "</html>";
        
        sendResponse(socket, statusCode, "text/html", body);
    }
    
    /**
     * Get a status text for an HTTP status code.
     */
    private String getStatusText(int statusCode) {
        switch (statusCode) {
            case 200: return "OK";
            case 400: return "Bad Request";
            case 404: return "Not Found";
            case 405: return "Method Not Allowed";
            case 500: return "Internal Server Error";
            default: return "Unknown";
        }
    }
    
    /**
     * Add a hook event to the history.
     */
    public void addHookEvent(String hookId, String moduleId, String targetPackage, 
                               long executionTime, boolean success) {
        HookEvent event = new HookEvent();
        event.hookId = hookId;
        event.moduleId = moduleId;
        event.targetPackage = targetPackage;
        event.executionTime = executionTime;
        event.success = success;
        event.timestamp = System.currentTimeMillis();
        
        synchronized (mHookHistory) {
            mHookHistory.add(0, event); // Add to beginning
            while (mHookHistory.size() > MAX_HISTORY_SIZE) {
                mHookHistory.remove(mHookHistory.size() - 1); // Remove oldest
            }
        }
    }
    
    /**
     * Add a memory event to the history.
     */
    public void addMemoryEvent(String moduleId, long usage, long delta) {
        MemoryEvent event = new MemoryEvent();
        event.moduleId = moduleId;
        event.usage = usage;
        event.delta = delta;
        event.timestamp = System.currentTimeMillis();
        
        synchronized (mMemoryHistory) {
            mMemoryHistory.add(0, event); // Add to beginning
            while (mMemoryHistory.size() > MAX_HISTORY_SIZE) {
                mMemoryHistory.remove(mMemoryHistory.size() - 1); // Remove oldest
            }
        }
    }
    
    @Override
    public void onHookPerformance(String hookId, String moduleId, String targetPackage, 
                                  long executionTime, boolean success) {
        addHookEvent(hookId, moduleId, targetPackage, executionTime, success);
    }
    
    @Override
    public void onMemoryUsage(String moduleId, long memoryUsage, long memoryDelta) {
        addMemoryEvent(moduleId, memoryUsage, memoryDelta);
    }
    
    /**
     * Hook event data.
     */
    private static class HookEvent {
        String hookId;
        String moduleId;
        String targetPackage;
        long executionTime;
        boolean success;
        long timestamp;
    }
    
    /**
     * Memory event data.
     */
    private static class MemoryEvent {
        String moduleId;
        long usage;
        long delta;
        long timestamp;
    }
} 