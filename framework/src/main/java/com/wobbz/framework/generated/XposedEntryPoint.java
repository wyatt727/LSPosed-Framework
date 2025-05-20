package com.wobbz.framework.generated;

import android.content.Context;
import android.util.Log;

import com.wobbz.framework.PluginManager;
import com.wobbz.framework.processor.HotReloadManager;
import com.wobbz.framework.processor.ModuleDiscovery;

import java.util.Map;

import io.github.libxposed.api.XposedContext;
import io.github.libxposed.api.XposedModuleInterface;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.SystemServerLoadedParam;

/**
 * Main entry point for the Wobbz LSPosed Modular Framework.
 * This class is listed in the {@code assets/xposed_init} file and is loaded by LSPosed.
 * It initializes the framework and delegates Xposed lifecycle events to all registered modules.
 *
 * <p>The entry point is responsible for:</p>
 * <ul>
 *   <li>Setting up the framework environment</li>
 *   <li>Discovering and initializing all modules</li>
 *   <li>Delegating Xposed lifecycle callbacks to modules via the {@link PluginManager}</li>
 *   <li>Starting the hot-reload server if needed</li>
 * </ul>
 */
public class XposedEntryPoint implements XposedModuleInterface {
    private static final String TAG = "WobbzFramework";
    
    private Context mContext;
    private XposedInterface mXposedInterface;
    private PluginManager mPluginManager;
    private HotReloadManager mHotReloadManager;
    
    /**
     * Default constructor required by LSPosed.
     */
    public XposedEntryPoint() {
        mPluginManager = PluginManager.getInstance();
    }
    
    /**
     * Called when this module is first loaded. Initializes the framework and discovers modules.
     *
     * @param param Information about the loading context.
     */
    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        try {
            Log.i(TAG, "Wobbz Framework initializing...");
            mXposedInterface = param.getXposed();
            
            // Log the startup parameters
            boolean isSystemServer = param.isSystemServer();
            String processName = param.getProcessName();
            Log.i(TAG, "Module loaded in process: " + processName + (isSystemServer ? " (System Server)" : ""));
            
            // We will set up the context in initFramework method, which will be called from 
            // onSystemServerLoaded or onPackageLoaded depending on which happens first
            
            // Forward to PluginManager
            mPluginManager.setXposedInterface(mXposedInterface);
            mPluginManager.handleModuleLoaded(param);
            
            Log.i(TAG, "Wobbz Framework initialized successfully in " + processName);
        } catch (Throwable t) {
            Log.e(TAG, "Error initializing Wobbz Framework: " + t.getMessage(), t);
        }
    }
    
    /**
     * Called when the system server is loaded.
     * This is where we'll initialize most of our framework components since it's
     * the safest place to get a Context and has broad access privileges.
     *
     * @param param Information about the system server.
     */
    @Override
    public void onSystemServerLoaded(SystemServerLoadedParam param) {
        try {
            Log.i(TAG, "System server loaded, initializing framework core in system_server");
            initFramework(param.getSystemContext(), param.getXposed());
            
            // Forward to PluginManager after framework is fully initialized
            mPluginManager.handleSystemServerLoaded(param);
        } catch (Throwable t) {
            Log.e(TAG, "Error in onSystemServerLoaded: " + t.getMessage(), t);
        }
    }
    
    /**
     * Called when a package is loaded. Forwards the callback to modules that are enabled for this package.
     *
     * @param param Information about the loaded package.
     */
    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        try {
            // Try to get context from the package if we don't have one yet
            if (mContext == null) {
                try {
                    Class<?> activityThreadClass = param.getXposed().loadClass("android.app.ActivityThread");
                    Object activityThread = activityThreadClass.getDeclaredMethod("currentActivityThread").invoke(null);
                    Context context = (Context) activityThreadClass.getDeclaredMethod("getSystemContext").invoke(activityThread);
                    if (context != null) {
                        Log.i(TAG, "Got context from ActivityThread in package: " + param.getPackageName());
                        initFramework(context, param.getXposed());
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "Failed to get context from package: " + param.getPackageName(), t);
                }
            }
            
            // Forward to PluginManager regardless of whether we have initialized the framework yet
            // (as modules might not need a Context to operate)
            mPluginManager.handleLoadPackage(param);
        } catch (Throwable t) {
            Log.e(TAG, "Error in onPackageLoaded for " + param.getPackageName() + ": " + t.getMessage(), t);
        }
    }
    
    /**
     * Called when resources for a package are loaded. Typically not used by our framework directly.
     *
     * @param param Information about the loaded resources.
     */
    @Override
    public void onResourcesLoaded(ResourcesLoadedParam param) {
        // Currently not used in our framework architecture
        // Could be used in the future if modules need to modify resources
    }
    
    /**
     * Initializes the framework with a valid Context.
     * This is the core initialization logic for the framework and should only be called once.
     *
     * @param context The Android context.
     * @param xposedInterface The XposedInterface instance.
     */
    private synchronized void initFramework(Context context, XposedInterface xposedInterface) {
        // Check if we've already initialized
        if (mContext != null) {
            Log.d(TAG, "Framework already initialized, skipping duplicate initialization");
            return;
        }
        
        mContext = context.getApplicationContext();
        if (mContext == null) {
            // Fall back to the original context if getApplicationContext() returns null
            mContext = context;
        }
        
        Log.i(TAG, "Initializing Wobbz Framework with context: " + mContext);
        
        // Initialize the PluginManager with the context
        mPluginManager.initialize(mContext);
        mPluginManager.setXposedInterface(xposedInterface);
        
        // Discover and register all modules
        ModuleDiscovery moduleDiscovery = new ModuleDiscovery(mContext);
        int loadedModules = moduleDiscovery.discoverAndRegisterModules(xposedInterface);
        Log.i(TAG, "Loaded " + loadedModules + " modules");
        
        // Set up hot-reload support if we have any hot-reloadable modules
        Map<String, Integer> hotReloadableModules = moduleDiscovery.findHotReloadableModules();
        if (!hotReloadableModules.isEmpty()) {
            mHotReloadManager = new HotReloadManager(mContext, hotReloadableModules);
            mHotReloadManager.startHotReloadServers();
            Log.i(TAG, "Started hot-reload manager for " + hotReloadableModules.size() + " modules");
        }
        
        Log.i(TAG, "Wobbz Framework core initialized successfully");
    }
} 