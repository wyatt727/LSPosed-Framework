package com.wobbz.framework.processor;

import android.content.Context;
import android.util.Log;

import com.wobbz.framework.IHotReloadable;
import com.wobbz.framework.IModulePlugin;
import com.wobbz.framework.PluginManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;

/**
 * Manages the hot-reloading functionality for modules annotated with {@link com.wobbz.framework.annotations.HotReloadable}.
 * This class runs a background server to listen for hot-reload commands from development tools,
 * and handles the process of reloading modules and re-applying their hooks.
 *
 * <p>When a module is hot-reloaded:</p>
 * <ol>
 *   <li>All existing hooks are removed (via {@link IHotReloadable#onHotReload()})</li>
 *   <li>The module's code is updated</li>
 *   <li>The module's {@link IHotReloadable#onHotReload()} method is called</li>
 *   <li>The framework re-triggers {@link IModulePlugin#onPackageLoaded(PackageLoadedParam)}
 *       for all active packages that the module is enabled for</li>
 * </ol>
 */
public class HotReloadManager {
    private static final String TAG = "HotReloadManager";
    
    private final Context mContext;
    private final PluginManager mPluginManager;
    private final Map<Integer, ServerThread> mServerThreads = new HashMap<>();
    private final Map<String, Integer> mModulePorts;
    private final ExecutorService mExecutor = Executors.newCachedThreadPool();
    
    private boolean mIsRunning = false;
    
    /**
     * Creates a new HotReloadManager.
     *
     * @param context The context to use.
     * @param modulePorts A map of module class names to their hot-reload port numbers.
     */
    public HotReloadManager(Context context, Map<String, Integer> modulePorts) {
        this.mContext = context;
        this.mPluginManager = PluginManager.getInstance();
        this.mModulePorts = modulePorts;
    }
    
    /**
     * Starts the hot-reload servers for all registered hot-reloadable modules.
     */
    public void startHotReloadServers() {
        if (mIsRunning) {
            Log.w(TAG, "Hot-reload servers already running, ignoring start request");
            return;
        }
        
        if (mModulePorts.isEmpty()) {
            Log.i(TAG, "No hot-reloadable modules found, not starting any servers");
            return;
        }
        
        // Create a set of unique ports from all hot-reloadable modules
        for (Map.Entry<String, Integer> entry : mModulePorts.entrySet()) {
            String moduleClass = entry.getKey();
            int port = entry.getValue();
            
            // Check if we already have a server for this port
            if (!mServerThreads.containsKey(port)) {
                ServerThread serverThread = new ServerThread(port);
                mServerThreads.put(port, serverThread);
                mExecutor.execute(serverThread);
                Log.i(TAG, "Started hot-reload server for port " + port + " serving module " + moduleClass);
            } else {
                // If we have a server for this port already, it can serve multiple modules
                Log.i(TAG, "Module " + moduleClass + " will use existing hot-reload server on port " + port);
            }
        }
        
        mIsRunning = true;
        Log.i(TAG, "Started " + mServerThreads.size() + " hot-reload servers for " + mModulePorts.size() + " modules");
    }
    
    /**
     * Stops all running hot-reload servers.
     */
    public void stopHotReloadServers() {
        if (!mIsRunning) {
            return;
        }
        
        for (ServerThread serverThread : mServerThreads.values()) {
            serverThread.stopServer();
        }
        
        mServerThreads.clear();
        mIsRunning = false;
        Log.i(TAG, "Stopped all hot-reload servers");
    }
    
    /**
     * Processes a hot-reload request for a specific module.
     *
     * @param moduleClassName The fully qualified class name of the module to reload.
     * @param packageName Optional package name to specifically reload hooks for, or null to reload all packages.
     */
    private void processHotReload(String moduleClassName, String packageName) {
        Log.i(TAG, "Processing hot-reload for module: " + moduleClassName + 
              (packageName != null ? " (package: " + packageName + ")" : ""));
        
        // Forward the request to the PluginManager
        mPluginManager.handleHotReload(moduleClassName);
        
        // Log completion
        Log.i(TAG, "Hot-reload completed for module: " + moduleClassName);
    }
    
    /**
     * Server thread that listens for hot-reload commands on a specific port.
     */
    private class ServerThread implements Runnable {
        private final int mPort;
        private ServerSocket mServerSocket;
        private volatile boolean mRunning;
        
        public ServerThread(int port) {
            this.mPort = port;
            this.mRunning = true;
        }
        
        public void stopServer() {
            mRunning = false;
            if (mServerSocket != null) {
                try {
                    mServerSocket.close();
                } catch (Exception e) {
                    Log.e(TAG, "Error closing server socket on port " + mPort, e);
                }
            }
        }
        
        @Override
        public void run() {
            try {
                mServerSocket = new ServerSocket(mPort);
                Log.i(TAG, "Hot-reload server started on port " + mPort);
                
                while (mRunning) {
                    try {
                        // Accept client connections
                        Socket socket = mServerSocket.accept();
                        
                        // Process the connection in a separate thread to keep accepting new connections
                        mExecutor.execute(new ClientHandler(socket));
                    } catch (Exception e) {
                        if (mRunning) {
                            Log.e(TAG, "Error accepting client connection on port " + mPort, e);
                        }
                    }
                }
            } catch (Exception e) {
                if (mRunning) {
                    Log.e(TAG, "Error starting hot-reload server on port " + mPort, e);
                }
            } finally {
                if (mServerSocket != null && !mServerSocket.isClosed()) {
                    try {
                        mServerSocket.close();
                    } catch (Exception e) {
                        Log.e(TAG, "Error closing server socket on port " + mPort, e);
                    }
                }
            }
        }
    }
    
    /**
     * Handles client connections to the hot-reload server.
     */
    private class ClientHandler implements Runnable {
        private final Socket mSocket;
        
        public ClientHandler(Socket socket) {
            this.mSocket = socket;
        }
        
        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(mSocket.getInputStream(), StandardCharsets.UTF_8))) {
                
                // Read command line
                String command = reader.readLine();
                if (command != null) {
                    command = command.trim();
                    Log.d(TAG, "Received command: " + command);
                    
                    // Parse the command
                    if (command.startsWith("reload ")) {
                        String moduleClassName = command.substring("reload ".length()).trim();
                        processHotReload(moduleClassName, null);
                    } else if (command.startsWith("reload-package ")) {
                        String[] parts = command.substring("reload-package ".length()).trim().split("\\s+", 2);
                        if (parts.length == 2) {
                            String moduleClassName = parts[0].trim();
                            String packageName = parts[1].trim();
                            processHotReload(moduleClassName, packageName);
                        } else {
                            Log.w(TAG, "Invalid reload-package command format: " + command);
                        }
                    } else {
                        Log.w(TAG, "Unknown command: " + command);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error handling client connection", e);
            } finally {
                try {
                    mSocket.close();
                } catch (Exception e) {
                    Log.e(TAG, "Error closing client socket", e);
                }
            }
        }
    }
} 