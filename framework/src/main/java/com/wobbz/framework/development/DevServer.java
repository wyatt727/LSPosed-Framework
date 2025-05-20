package com.wobbz.framework.development;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Implements a lightweight development server to facilitate hot-reloading of modules.
 * This server listens on a specified network port for commands from development tools
 * (e.g., a Gradle plugin or an IDE plugin).
 *
 * <p>When a "RELOAD moduleName" command is received, it notifies registered {@link DevServerListener}s,
 * typically prompting the {@link com.wobbz.framework.PluginManager} to perform a hot-reload
 * for the specified module.</p>
 *
 * <p>The server runs in a background thread and handles each client connection in a separate task.
 * It is designed to be started during development and stopped when no longer needed.</p>
 *
 * <p>Supported commands:</p>
 * <ul>
 *     <li>{@code RELOAD <moduleClassName>} - Requests a hot-reload for the module identified by its fully qualified class name.</li>
 *     <li>{@code PING} - A simple command to check if the server is responsive; the server replies with "PONG".</li>
 * </ul>
 */
public class DevServer {
    private static final String TAG = "WobbzDevServer"; // Specific tag for this server

    private final Context mContext; // Application context, currently unused but kept for potential future use.
    private final int mPort;
    private final List<DevServerListener> mListeners = new CopyOnWriteArrayList<>(); // Thread-safe for listeners
    private final ExecutorService mServerExecutor = Executors.newSingleThreadExecutor(); // For the server accept loop
    private final ExecutorService mClientHandlerExecutor = Executors.newCachedThreadPool(); // For handling multiple clients
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private ServerSocket mServerSocket;
    private volatile boolean mIsRunning = false; // Ensure visibility across threads

    /**
     * Constructs a new {@code DevServer}.
     *
     * @param context The application context. While not directly used by the server socket logic,
     *                it's good practice to have it for potential future needs like accessing resources or system services.
     * @param port    The network port on which the server will listen for connections.
     */
    public DevServer(Context context, int port) {
        this.mContext = context.getApplicationContext(); // Use application context to avoid activity leaks
        this.mPort = port;
        LoggingHelper.logInfo(TAG, "DevServer initialized for port: " + port);
    }

    /**
     * Starts the development server if it is not already running.
     * The server will begin listening for incoming connections on the configured port.
     * This method is thread-safe.
     */
    public void start() {
        if (mIsRunning) {
            LoggingHelper.logInfo(TAG, "Server is already running on port " + mPort);
            return;
        }

        LoggingHelper.logInfo(TAG, "Starting DevServer on port " + mPort + "...");
        mIsRunning = true;
        try {
            mServerExecutor.execute(this::serverLoop);
        } catch (RejectedExecutionException e) {
            LoggingHelper.logError(TAG, "Failed to start server loop executor. Is it shutting down?", e);
            mIsRunning = false; // Ensure state is correct if execute fails
        }
    }

    /**
     * Stops the development server if it is running.
     * Closes the server socket and attempts to interrupt client handling threads.
     * This method is thread-safe.
     */
    public void stop() {
        if (!mIsRunning) {
            LoggingHelper.logInfo(TAG, "Server is not running or already stopped.");
            return;
        }

        LoggingHelper.logInfo(TAG, "Stopping DevServer on port " + mPort + "...");
        mIsRunning = false; // Signal loops to stop

        try {
            if (mServerSocket != null && !mServerSocket.isClosed()) {
                mServerSocket.close(); // This will interrupt the accept() call in serverLoop
            }
        } catch (IOException e) {
            LoggingHelper.logError(TAG, "Error closing server socket", e);
        }
        mServerSocket = null;

        // Shutdown executors gracefully
        shutdownExecutor(mServerExecutor, "ServerLoopExecutor");
        shutdownExecutor(mClientHandlerExecutor, "ClientHandlerExecutor");

        LoggingHelper.logInfo(TAG, "DevServer stopped.");
    }

    private void shutdownExecutor(ExecutorService executor, String executorName) {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown(); // Disable new tasks from being submitted
            try {
                // Wait a while for existing tasks to terminate
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    LoggingHelper.logWarning(TAG, executorName + " did not terminate in time, forcing shutdown.");
                    executor.shutdownNow(); // Cancel currently executing tasks
                    // Wait a while for tasks to respond to being cancelled
                    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                        LoggingHelper.logError(TAG, executorName + " did not terminate after forced shutdown.");
                    }
                }
            } catch (InterruptedException ie) {
                LoggingHelper.logWarning(TAG, executorName + " termination wait interrupted, forcing shutdown.");
                executor.shutdownNow(); // (Re-)Cancel if current thread also interrupted
                Thread.currentThread().interrupt(); // Preserve interrupt status
            }
        }
    }

    /**
     * Adds a listener to receive notifications about server events, such as hot-reload requests.
     * Listeners are stored in a thread-safe list.
     *
     * @param listener The {@link DevServerListener} to add. If null, the call is ignored.
     */
    public void addListener(DevServerListener listener) {
        if (listener != null && !mListeners.contains(listener)) {
            mListeners.add(listener);
            LoggingHelper.logVerbose(TAG, "Added listener: " + listener.getClass().getName());
        }
    }

    /**
     * Removes a previously added listener.
     *
     * @param listener The {@link DevServerListener} to remove. If null or not registered, the call is ignored.
     */
    public void removeListener(DevServerListener listener) {
        if (listener != null && mListeners.remove(listener)){
            LoggingHelper.logVerbose(TAG, "Removed listener: " + listener.getClass().getName());
        }
    }

    /**
     * The main server loop that accepts client connections.
     * This runs on a dedicated thread via {@code mServerExecutor}.
     */
    private void serverLoop() {
        try {
            mServerSocket = new ServerSocket(mPort);
            LoggingHelper.logInfo(TAG, "DevServer listening on port: " + mServerSocket.getLocalPort());

            while (mIsRunning && !Thread.currentThread().isInterrupted()) {
                Socket clientSocket = mServerSocket.accept(); // Blocking call, interrupted by mServerSocket.close()
                LoggingHelper.logInfo(TAG, "Client connected: " + clientSocket.getInetAddress());
                try {
                    mClientHandlerExecutor.execute(new ClientTask(clientSocket));
                } catch (RejectedExecutionException e) {
                    LoggingHelper.logError(TAG, "Failed to handle client connection, executor service likely shutting down.", e);
                    try {
                        clientSocket.close(); // Ensure client socket is closed if we can't handle it
                    } catch (IOException ioe) {
                        LoggingHelper.logError(TAG, "Error closing client socket after rejection.", ioe);
                    }
                }
            }
        } catch (IOException e) {
            if (mIsRunning) { // Only log error if we weren't expecting to stop
                LoggingHelper.logError(TAG, "DevServer loop error (server will stop)", e);
            }
        } finally {
            mIsRunning = false; // Ensure state is consistent
            LoggingHelper.logInfo(TAG, "DevServer loop has finished.");
            // Note: mServerSocket is closed in stop() or if an exception occurs here naturally
        }
    }

    /**
     * Notifies all registered listeners about a hot-reload request.
     * This method posts the notification to the main application thread.
     *
     * @param moduleName The fully qualified class name of the module to be reloaded.
     */
    private void notifyHotReloadRequest(String moduleName) {
        mMainHandler.post(() -> {
            LoggingHelper.logInfo(TAG, "Notifying " + mListeners.size() + " listeners of hot-reload request for: " + moduleName);
            for (DevServerListener listener : mListeners) {
                try {
                    listener.onHotReloadRequest(moduleName);
                } catch (Exception e) {
                    LoggingHelper.logError(TAG, "Error notifying listener " + listener.getClass().getName() + " for hot reload", e);
                }
            }
        });
    }

    /**
     * An {@link AsyncTask} subclass is not ideal here as it's tied to an implicit Looper which might not exist
     * in all Xposed environments. A direct Runnable submitted to an ExecutorService is better.
     * This {@code ClientTask} handles communication with a single connected client.
     */
    private class ClientTask implements Runnable {
        private final Socket mClientSocket;

        ClientTask(Socket clientSocket) {
            this.mClientSocket = clientSocket;
        }

        @Override
        public void run() {
            try (Socket socket = mClientSocket; // Use try-with-resources for automatic closing
                 BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                 PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

                LoggingHelper.logVerbose(TAG, "Handling client: " + socket.getInetAddress());
                String line;
                while (mIsRunning && (line = reader.readLine()) != null) { // Check mIsRunning to stop if server stops
                    LoggingHelper.logDebug(TAG, "Received command: " + line);
                    processClientCommand(line, writer);
                }
            } catch (IOException e) {
                if (mIsRunning) { // Avoid spamming logs if server is stopping
                    LoggingHelper.logWarning(TAG, "Error during client communication with " + mClientSocket.getInetAddress() + ": " + e.getMessage());
                }
            } finally {
                LoggingHelper.logInfo(TAG, "Client disconnected: " + mClientSocket.getInetAddress());
            }
        }

        /**
         * Processes a command received from the client and sends a response.
         *
         * @param command The command string received from the client.
         * @param writer  The {@link PrintWriter} to send responses back to the client.
         */
        private void processClientCommand(String command, PrintWriter writer) {
            if (command == null) return;
            String trimmedCommand = command.trim();

            if (trimmedCommand.startsWith("RELOAD ")) {
                String moduleName = trimmedCommand.substring("RELOAD ".length()).trim();
                if (!moduleName.isEmpty()) {
                    LoggingHelper.logInfo(TAG, "Processing RELOAD command for module: " + moduleName);
                    notifyHotReloadRequest(moduleName);
                    writer.println("OK: Reload requested for " + moduleName);
                } else {
                    LoggingHelper.logWarning(TAG, "RELOAD command received with empty module name.");
                    writer.println("ERROR: Module name cannot be empty for RELOAD command.");
                }
            } else if (trimmedCommand.equals("PING")) {
                LoggingHelper.logVerbose(TAG, "Processing PING command.");
                writer.println("PONG");
            } else {
                LoggingHelper.logWarning(TAG, "Received unknown command: " + trimmedCommand);
                writer.println("ERROR: Unknown command: " + trimmedCommand);
            }
        }
    }

    /**
     * Interface for components that need to listen to events from the {@link DevServer},
     * particularly hot-reload requests.
     */
    public interface DevServerListener {
        /**
         * Called when a hot-reload request is received from a development tool.
         * Implementations should handle the reloading of the specified module.
         * This method is called on the main application thread.
         *
         * @param moduleClassName The fully qualified class name of the module to be reloaded.
         */
        void onHotReloadRequest(String moduleClassName);
    }
} 