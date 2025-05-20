package com.wobbz.networkguard;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import com.wobbz.framework.IHotReloadable;
import com.wobbz.framework.IModulePlugin;
import com.wobbz.framework.analytics.AnalyticsManager;
import com.wobbz.framework.annotations.HotReloadable;
import com.wobbz.framework.annotations.XposedPlugin;
import com.wobbz.framework.development.LoggingHelper;
import com.wobbz.framework.security.SecurityManager;
import com.wobbz.framework.security.SecurityManager.FirewallRule;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedInterface.Hooker;
import io.github.libxposed.api.XposedInterface.BeforeHookCallback;
import io.github.libxposed.api.XposedInterface.AfterHookCallback;
import io.github.libxposed.api.XposedInterface.MethodUnhooker;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.ZygoteLoadedParam;

/**
 * NetworkGuard module to monitor and control network traffic
 * from applications based on security rules.
 */
@XposedPlugin(
    id = "com.wobbz.networkguard",
    name = "Network Guard",
    description = "Monitors and blocks network traffic based on security rules",
    version = "1.0.0",
    scope = {"android", "*"} // Needs system server and all apps for complete network control
)
@HotReloadable
public class NetworkGuardModule implements IModulePlugin, IHotReloadable, SecurityManager.SecurityListener {
    private static final String TAG = "NetworkGuard";
    
    // Store method unhookers for hot reload support
    private final List<MethodUnhooker<?>> mUnhookers = new ArrayList<>();
    
    // Managers for security rules and analytics
    private SecurityManager mSecurityManager;
    private AnalyticsManager mAnalyticsManager;
    
    // Context and initialization state
    private Context mModuleContext;
    private boolean mManagersInitialized = false;
    
    // Store params for hot reload
    private ZygoteLoadedParam mZygoteParam;
    private PackageLoadedParam mLastParam;
    
    // Store XposedInterface for hooking
    private XposedInterface mXposedInterface;
    
    /**
     * Called when the module is initialized in Zygote.
     * This is where we implement hooks that need to be applied very early.
     */
    @Override
    public void onZygote(ZygoteLoadedParam param) {
        LoggingHelper.info(TAG, "NetworkGuard module initializing in Zygote");
        
        mZygoteParam = param;
        mXposedInterface = param.getXposed();
        
        // Store hooks in Zygote for core network classes that all apps use
        hookCoreNetworkApis(null);
    }
    
    /**
     * Called when the module context is available.
     */
    @Override
    public void initialize(Context context, XposedInterface xposedInterface) {
        this.mModuleContext = context;
        this.mXposedInterface = xposedInterface;
        
        initializeManagers(context);
    }
    
    /**
     * Initialize security and analytics managers.
     */
    private synchronized void initializeManagers(Context context) {
        if (mManagersInitialized) {
            return;
        }
        
        if (context == null) {
            LoggingHelper.error(TAG, "Context is null, cannot initialize managers.");
            return;
        }
        
        this.mModuleContext = context.getApplicationContext();

        try {
            mSecurityManager = SecurityManager.getInstance(this.mModuleContext);
            if (mSecurityManager != null) {
                mSecurityManager.addListener(this);
                LoggingHelper.info(TAG, "SecurityManager initialized successfully.");
            } else {
                LoggingHelper.error(TAG, "SecurityManager.getInstance() returned null.");
            }
        } catch (Exception e) {
            LoggingHelper.error(TAG, "Failed to initialize SecurityManager", e);
        }

        try {
            mAnalyticsManager = AnalyticsManager.getInstance(this.mModuleContext);
            if (mAnalyticsManager != null) {
                LoggingHelper.info(TAG, "AnalyticsManager initialized successfully.");
            } else {
                LoggingHelper.error(TAG, "AnalyticsManager.getInstance() returned null.");
            }
        } catch (Exception e) {
            LoggingHelper.error(TAG, "Failed to initialize AnalyticsManager", e);
        }
        
        if (mSecurityManager != null && mAnalyticsManager != null) {
            mManagersInitialized = true;
        } else {
            LoggingHelper.error(TAG, "One or more managers failed to initialize.");
        }
    }
    
    /**
     * Called when an app is loaded.
     */
    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        LoggingHelper.debug(TAG, "Processing package: " + param.getPackageName());
        
        // Store the last param for hot reload
        mLastParam = param;
        
        // If managers not initialized yet, try with context from this package
        if (!mManagersInitialized && mModuleContext == null) {
            try {
                // Try to get the app's context
                Context appContext = param.getAppContext();
                if (appContext != null) {
                    LoggingHelper.info(TAG, "Initializing managers with context from " + param.getPackageName());
                    initializeManagers(appContext);
                }
            } catch (Exception e) {
                LoggingHelper.error(TAG, "Failed to get context from " + param.getPackageName(), e);
            }
        }
        
        // Hook package-specific network operations
        hookAppNetworkOperations(param);
        hookAppSpecificNetworkLibraries(param);
    }
    
    /**
     * Hook core network APIs that are used by all apps.
     * This should be done in Zygote for complete coverage.
     */
    private void hookCoreNetworkApis(PackageLoadedParam param) {
        try {
            // Socket constructor hooks
            Class<?> socketClass = Socket.class;
            XposedInterface xposed = (param != null) ? param.getXposed() : mXposedInterface;
            
            if (xposed == null) {
                LoggingHelper.error(TAG, "XposedInterface is null, cannot hook core network APIs");
                return;
            }
            
            // Socket(InetAddress host, int port)
            try {
                Method socketConstructor = socketClass.getDeclaredMethod("getConstructor", InetAddress.class, int.class);
                MethodUnhooker<?> unhooker = xposed.hook(socketConstructor, new SocketConstructorHook());
                if (unhooker != null) {
                    mUnhookers.add(unhooker);
                }
            } catch (NoSuchMethodException e) {
                LoggingHelper.error(TAG, "Could not find Socket constructor with InetAddress,int", e);
            }
            
            // URL.openConnection()
            try {
                Method openConnectionMethod = URL.class.getDeclaredMethod("openConnection");
                MethodUnhooker<?> unhooker = xposed.hook(openConnectionMethod, new URLOpenConnectionHook());
                if (unhooker != null) {
                    mUnhookers.add(unhooker);
                }
            } catch (NoSuchMethodException e) {
                LoggingHelper.error(TAG, "Could not find URL.openConnection method", e);
            }
            
            LoggingHelper.info(TAG, "Core network hooks installed successfully");
            
        } catch (Throwable t) {
            LoggingHelper.error(TAG, "Failed to hook core network APIs", t);
        }
    }
    
    /**
     * Hooker for Socket constructor
     */
    private class SocketConstructorHook implements Hooker {
        public void before(BeforeHookCallback callback) throws Throwable {
            try {
                InetAddress address = (InetAddress) callback.getArgs()[0];
                int port = (int) callback.getArgs()[1];
                String currentPackageName = "unknown";
                
                // Try to determine the current package name
                if (callback.getThisObject() != null) {
                    ClassLoader loader = callback.getThisObject().getClass().getClassLoader();
                    if (loader != null) {
                        currentPackageName = loader.toString();
                        // Extract package name from classloader string if possible
                        if (currentPackageName.contains("PathClassLoader")) {
                            int start = currentPackageName.indexOf("[") + 1;
                            int end = currentPackageName.indexOf("]");
                            if (start > 0 && end > start) {
                                currentPackageName = currentPackageName.substring(start, end);
                            }
                        }
                    }
                }
                
                // Check if connection should be allowed
                if (mSecurityManager != null) {
                    if (!mSecurityManager.shouldAllowConnection(
                            currentPackageName, address.getHostAddress(), port, SecurityManager.PROTO_TCP)) {
                        callback.returnAndSkip(new IOException("Connection blocked by NetworkGuard (Socket to " 
                                + address.getHostAddress() + ":" + port + ")"));
                    }
                }
            } catch (Exception e) {
                LoggingHelper.error(TAG, "Error in SocketConstructorHook", e);
            }
        }
        
        public void after(AfterHookCallback callback) {
            // No action needed after method execution
        }
    }
    
    /**
     * Hooker for URL.openConnection
     */
    private class URLOpenConnectionHook implements Hooker {
        public void before(BeforeHookCallback callback) throws Throwable {
            try {
                URL urlInstance = (URL) callback.getThisObject();
                String currentPackageName = "unknown";
                
                // Try to determine the current package name 
                ClassLoader loader = callback.getThisObject().getClass().getClassLoader();
                if (loader != null) {
                    currentPackageName = loader.toString();
                    // Extract package name from classloader string if possible
                    if (currentPackageName.contains("PathClassLoader")) {
                        int start = currentPackageName.indexOf("[") + 1;
                        int end = currentPackageName.indexOf("]");
                        if (start > 0 && end > start) {
                            currentPackageName = currentPackageName.substring(start, end);
                        }
                    }
                }
                
                // Check if connection should be allowed
                if (mSecurityManager != null) {
                    int port = urlInstance.getPort() == -1 ? urlInstance.getDefaultPort() : urlInstance.getPort();
                    if (!mSecurityManager.shouldAllowConnection(
                            currentPackageName, urlInstance.getHost(), port, SecurityManager.PROTO_TCP)) {
                        callback.returnAndSkip(new IOException("Connection blocked by NetworkGuard (URL.openConnection to " 
                                + urlInstance.getHost() + ":" + port + ")"));
                    }
                }
            } catch (Exception e) {
                LoggingHelper.error(TAG, "Error in URLOpenConnectionHook", e);
            }
        }
        
        public void after(AfterHookCallback callback) {
            // No action needed after method execution
        }
    }
    
    /**
     * Hook app-specific network operations.
     */
    private void hookAppNetworkOperations(PackageLoadedParam param) {
        // App-specific network operation hooks would go here
        // For example, hooking ConnectivityManager methods
        
        // Just a stub for future implementation
        LoggingHelper.debug(TAG, "hookAppNetworkOperations for " + param.getPackageName());
    }
    
    /**
     * Hook app-specific network libraries like OkHttp
     */
    private void hookAppSpecificNetworkLibraries(PackageLoadedParam param) {
        String packageName = param.getPackageName();
        ClassLoader appClassLoader = param.getClassLoader();
        XposedInterface xposed = param.getXposed();

        if (mSecurityManager != null && appClassLoader != null && xposed != null) {
            try {
                // Try to hook OkHttp library if present in the app
                LoggingHelper.debug(TAG, "Attempting to hook OkHttp for: " + packageName);
                
                try {
                    // Find OkHttp classes
                    Class<?> okhttpClientClass = appClassLoader.loadClass("okhttp3.OkHttpClient");
                    Class<?> requestClass = appClassLoader.loadClass("okhttp3.Request");
                    
                    // Hook the newCall method
                    Method newCallMethod = okhttpClientClass.getDeclaredMethod("newCall", requestClass);
                    MethodUnhooker<?> unhooker = xposed.hook(newCallMethod, new OkHttpNewCallHook(packageName));
                    if (unhooker != null) {
                        mUnhookers.add(unhooker);
                        LoggingHelper.info(TAG, "OkHttp hook installed successfully for " + packageName);
                    }
                } catch (ClassNotFoundException e) {
                    LoggingHelper.debug(TAG, "OkHttp not found in " + packageName + ", skipping OkHttp hooks.");
                } catch (NoSuchMethodException e) {
                    LoggingHelper.error(TAG, "OkHttp method not found in " + packageName, e);
                }
            } catch (Throwable t) {
                LoggingHelper.warning(TAG, "Error hooking network libraries for " + packageName + ": " + t.getMessage());
            }
        } else {
            if (mSecurityManager == null) {
                LoggingHelper.warning(TAG, "SecurityManager not initialized, skipping app-specific hooks for " + packageName);
            }
        }
    }
    
    /**
     * Hooker for OkHttp's newCall method
     */
    private class OkHttpNewCallHook implements Hooker {
        private final String packageName;
        
        public OkHttpNewCallHook(String packageName) {
            this.packageName = packageName;
        }
        
        public void before(BeforeHookCallback callback) throws Throwable {
            try {
                // Get the request object
                Object request = callback.getArgs()[0];
                
                // Get the URL from the request
                Object httpUrl = request.getClass().getMethod("url").invoke(request);
                
                // Extract host and port
                String host = (String) httpUrl.getClass().getMethod("host").invoke(httpUrl);
                int port = (int) httpUrl.getClass().getMethod("port").invoke(httpUrl);
                
                // Check if connection should be allowed
                if (mSecurityManager != null) {
                    if (!mSecurityManager.shouldAllowConnection(
                            packageName, host, port, SecurityManager.PROTO_TCP)) {
                        callback.returnAndSkip(new IOException("Connection blocked by NetworkGuard (OkHttp to " 
                                + host + ":" + port + ")"));
                    }
                }
            } catch (Exception e) {
                LoggingHelper.error(TAG, "Error in OkHttpNewCallHook", e);
            }
        }
        
        public void after(AfterHookCallback callback) {
            // No action needed after method execution
        }
    }
    
    /**
     * Called when the module is hot reloaded
     */
    @Override
    public void onHotReload() {
        LoggingHelper.info(TAG, "Hot-reloading NetworkGuard module");
        
        // Unhook all methods
        for (MethodUnhooker<?> unhooker : mUnhookers) {
            try {
                unhooker.unhook();
            } catch (Exception e) {
                LoggingHelper.error(TAG, "Error unhooking method during hot reload", e);
            }
        }
        mUnhookers.clear();
        
        // Remove security listener
        if (mSecurityManager != null) {
            mSecurityManager.removeListener(this);
        }
        
        // Reset initialization flag
        mManagersInitialized = false;
        
        // Re-initialize managers if context is available
        if (this.mModuleContext != null) {
            LoggingHelper.info(TAG, "Re-initializing managers with stored context");
            initializeManagers(this.mModuleContext);
        }
        
        // Re-apply hooks
        if (mZygoteParam != null) {
            LoggingHelper.info(TAG, "Re-applying zygote hooks");
            mXposedInterface = mZygoteParam.getXposed();
            hookCoreNetworkApis(null);
        }
        
        if (mLastParam != null) {
            LoggingHelper.info(TAG, "Re-applying app hooks for " + mLastParam.getPackageName());
            hookAppNetworkOperations(mLastParam);
            hookAppSpecificNetworkLibraries(mLastParam);
        }
        
        LoggingHelper.info(TAG, "NetworkGuard module hot-reloaded successfully");
    }
    
    /**
     * Add a firewall rule (API for other modules)
     */
    public void addFirewallRule(FirewallRule rule) {
        if (mSecurityManager != null) {
            mSecurityManager.addFirewallRule(rule);
        } else {
            LoggingHelper.error(TAG, "Cannot add firewall rule: SecurityManager not initialized");
        }
    }
    
    /**
     * Block all network access for an app (API for other modules)
     */
    public void blockAppNetwork(String packageName, boolean block) {
        if (mSecurityManager != null) {
            if (block) {
                FirewallRule rule = new FirewallRule(packageName, "*", -1, SecurityManager.PROTO_ALL, false);
                mSecurityManager.addFirewallRule(rule);
            } else {
                mSecurityManager.removeFirewallRules(packageName);
            }
        } else {
            LoggingHelper.error(TAG, "Cannot block app network: SecurityManager not initialized");
        }
    }
    
    /**
     * Get network statistics for an app (API for other modules)
     */
    public Map<String, Long> getAppNetworkStats(String packageName) {
        if (mSecurityManager != null) {
            return mSecurityManager.getNetworkStats(packageName);
        } else {
            LoggingHelper.error(TAG, "Cannot get network stats: SecurityManager not initialized");
            return new HashMap<>();
        }
    }
    
    /**
     * SecurityManager.SecurityListener implementation
     */
    @Override
    public void onSecurityRulesChanged() {
        LoggingHelper.info(TAG, "Security rules changed");
        // Any rule-dependent state can be updated here
    }
}