package com.wobbz.framework.development;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Development server for hot-reload functionality.
 * Listens for connections from development tools and handles code updates.
 */
public class DevServer {
    private static final String TAG = "DevServer";
    
    private final Context mContext;
    private final int mPort;
    private final List<DevServerListener> mListeners = new ArrayList<>();
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    
    private ServerSocket mServerSocket;
    private boolean mRunning;
    
    /**
     * Create a new development server.
     * 
     * @param context The application context.
     * @param port The port to listen on.
     */
    public DevServer(Context context, int port) {
        mContext = context.getApplicationContext();
        mPort = port;
    }
    
    /**
     * Start the development server.
     */
    public void start() {
        if (mRunning) {
            return;
        }
        
        mRunning = true;
        mExecutor.execute(this::runServer);
    }
    
    /**
     * Stop the development server.
     */
    public void stop() {
        mRunning = false;
        
        try {
            if (mServerSocket != null && !mServerSocket.isClosed()) {
                mServerSocket.close();
                mServerSocket = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error stopping server", e);
        }
    }
    
    /**
     * Add a listener for development server events.
     */
    public void addListener(DevServerListener listener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }
    
    /**
     * Remove a listener for development server events.
     */
    public void removeListener(DevServerListener listener) {
        mListeners.remove(listener);
    }
    
    /**
     * Run the server in a background thread.
     */
    private void runServer() {
        try {
            mServerSocket = new ServerSocket(mPort);
            Log.i(TAG, "Development server started on port " + mPort);
            
            while (mRunning) {
                Socket clientSocket = mServerSocket.accept();
                handleClientConnection(clientSocket);
            }
        } catch (IOException e) {
            if (mRunning) {
                Log.e(TAG, "Error running development server", e);
            }
        } finally {
            mRunning = false;
        }
    }
    
    /**
     * Handle a client connection.
     */
    private void handleClientConnection(Socket clientSocket) {
        new ClientHandler(clientSocket).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
    
    /**
     * Notify listeners of a hot reload request.
     */
    private void notifyHotReload(String moduleName) {
        mMainHandler.post(() -> {
            for (DevServerListener listener : mListeners) {
                listener.onHotReloadRequest(moduleName);
            }
        });
    }
    
    /**
     * Client handler for processing commands from connected clients.
     */
    private class ClientHandler extends AsyncTask<Void, Void, Void> {
        private final Socket mClientSocket;
        
        ClientHandler(Socket clientSocket) {
            mClientSocket = clientSocket;
        }
        
        @Override
        protected Void doInBackground(Void... voids) {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(mClientSocket.getInputStream()));
                PrintWriter writer = new PrintWriter(mClientSocket.getOutputStream(), true);
                
                String line;
                while ((line = reader.readLine()) != null) {
                    handleCommand(line, writer);
                }
            } catch (IOException e) {
                Log.e(TAG, "Error handling client connection", e);
            } finally {
                try {
                    mClientSocket.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing client socket", e);
                }
            }
            
            return null;
        }
        
        /**
         * Handle a command received from the client.
         */
        private void handleCommand(String command, PrintWriter writer) {
            if (command.startsWith("RELOAD ")) {
                // Extract module name from command
                String moduleName = command.substring("RELOAD ".length()).trim();
                
                if (!moduleName.isEmpty()) {
                    // Notify listeners of the hot reload request
                    notifyHotReload(moduleName);
                    
                    // Respond to the client
                    writer.println("OK");
                } else {
                    writer.println("ERROR: Missing module name");
                }
            } else if (command.equals("PING")) {
                // Simple ping command to check if the server is alive
                writer.println("PONG");
            } else {
                writer.println("ERROR: Unknown command");
            }
        }
    }
    
    /**
     * Listener for development server events.
     */
    public interface DevServerListener {
        /**
         * Called when a hot reload is requested.
         * 
         * @param moduleName The name of the module to reload.
         */
        void onHotReloadRequest(String moduleName);
    }
} 