package com.wobbz.networkguard;

import android.app.Application;
import android.app.AndroidAppHelper;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.content.pm.PackageManager;

import com.wobbz.framework.IHotReloadable;
import com.wobbz.framework.IModulePlugin;
import com.wobbz.framework.analytics.AnalyticsManager;
import com.wobbz.framework.annotations.HotReloadable;
import com.wobbz.framework.development.LoggingHelper;
import com.wobbz.framework.security.SecurityManager;
import com.wobbz.framework.security.SecurityManager.FirewallRule;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.github.libxposed.api.XC_MethodHook;
import com.github.libxposed.api.XposedBridge;
import com.github.libxposed.api.XposedHelpers;
import com.github.libxposed.api.callbacks.XC_LoadPackage.LoadPackageParam;
import com.github.libxposed.api.callbacks.IXposedHookZygoteInit.StartupParam;

@HotReloadable
public class NetworkGuardModule implements IModulePlugin, IHotReloadable, SecurityManager.SecurityListener {
    private static final String TAG = "NetworkGuard";
    private final Map<String, XC_MethodHook.Unhook> mUnhooks = new HashMap<>();
    private SecurityManager mSecurityManager;
    private AnalyticsManager mAnalyticsManager;
    private Context mModuleContext;
    private boolean mManagersInitialized = false;
    private LoadPackageParam mStoredLpparam;

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        LoggingHelper.info(TAG, "NetworkGuard module initializing in Zygote");
        
        Application initialApplication = XposedBridge.sInitialApplication;
        if (initialApplication != null) {
            this.mModuleContext = initialApplication.getApplicationContext();
            initializeManagers(this.mModuleContext);
        } else {
            LoggingHelper.warning(TAG, "XposedBridge.sInitialApplication is null in initZygote. Managers will be initialized later in handleLoadPackage.");
        }
        
        hookCoreNetworkApis(null);
    }

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
            LoggingHelper.error(TAG, "One or more managers failed to initialize. mManagersInitialized set to false.");
        }
    }
    
    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        this.mStoredLpparam = lpparam;
        if (!mManagersInitialized) {
            LoggingHelper.info(TAG, "Attempting to initialize managers in handleLoadPackage for: " + lpparam.packageName);
            Context contextToUse = null;
            Application app = AndroidAppHelper.currentApplication();
            if (app != null) {
                contextToUse = app.getApplicationContext();
            } else if (lpparam.appInfo != null) {
                 try {
                    Context baseContext = XposedBridge.sInitialApplication != null ? XposedBridge.sInitialApplication.getApplicationContext() : null;
                    if (baseContext != null) {
                        contextToUse = baseContext.createPackageContext(lpparam.packageName, Context.CONTEXT_IGNORE_SECURITY);
                    } else {
                        LoggingHelper.warning(TAG, "No base context to create package context for " + lpparam.packageName);
                    }
                } catch (PackageManager.NameNotFoundException | NullPointerException e) {
                     LoggingHelper.warning(TAG, "Failed to create package context for " + lpparam.packageName + ". Falling back. Error: " + e.getMessage());
                }
            }
            
            if (contextToUse == null && XposedBridge.sInitialApplication != null) {
                 LoggingHelper.info(TAG, "Using XposedBridge.sInitialApplication as fallback context for manager initialization.");
                 contextToUse = XposedBridge.sInitialApplication.getApplicationContext();
            }

            if (contextToUse != null) {
                initializeManagers(contextToUse);
            } else {
                LoggingHelper.error(TAG, "Still no context available in handleLoadPackage for " + lpparam.packageName + " to initialize managers.");
            }
        }
        
        hookCoreNetworkApis(lpparam);
        hookAppNetworkOperations(lpparam);
        hookAppSpecificNetworkLibraries(lpparam);
    }

    private void hookCoreNetworkApis(LoadPackageParam lpparam) {
        try {
            XC_MethodHook.Unhook socketConstructorHook = XposedHelpers.findAndHookMethod(
                Socket.class,
                "<init>",
                InetAddress.class,
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(Param param) throws Throwable {
                        InetAddress address = (InetAddress) param.args[0];
                        int port = (int) param.args[1];
                        String currentPackageName = (lpparam != null) ? lpparam.packageName : "unknown_zygote_context";
                        
                        if (mSecurityManager != null) {
                            if (!mSecurityManager.shouldAllowConnection(
                                    currentPackageName, address.getHostAddress(), port, SecurityManager.PROTO_TCP)) {
                                param.setThrowable(new IOException("Connection blocked by NetworkGuard (Socket to " + address.getHostAddress() + ":" + port + ")"));
                            }
                        }
                    }
                }
            );
            mUnhooks.put("Socket.<init>", socketConstructorHook);
            
            XC_MethodHook.Unhook urlOpenHook = XposedHelpers.findAndHookMethod(
                URL.class,
                "openConnection",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(Param param) throws Throwable {
                        URL urlInstance = (URL) param.thisObject;
                        String currentPackageName = (lpparam != null) ? lpparam.packageName : "unknown_zygote_context";

                        if (mSecurityManager != null) {
                            if (!mSecurityManager.shouldAllowConnection(
                                    currentPackageName, urlInstance.getHost(), urlInstance.getPort() == -1 ? urlInstance.getDefaultPort() : urlInstance.getPort(), SecurityManager.PROTO_TCP)) {
                                param.setThrowable(new IOException("Connection blocked by NetworkGuard (URL.openConnection to " + urlInstance.getHost() + ":" + (urlInstance.getPort() == -1 ? urlInstance.getDefaultPort() : urlInstance.getPort()) + ")"));
                            }
                        }
                    }
                }
            );
            mUnhooks.put("URL.openConnection", urlOpenHook);
            
            LoggingHelper.info(TAG, "Core network hooks (generic) installed successfully");
            
        } catch (Throwable t) {
            LoggingHelper.error(TAG, "Failed to hook core network APIs", t);
        }
    }

    private void hookAppNetworkOperations(LoadPackageParam lpparam) {
        if (!mManagersInitialized) {
            LoggingHelper.info(TAG, "Attempting to initialize managers in hookAppNetworkOperations for: " + lpparam.packageName);
            Context contextToUse = null;
            Application app = AndroidAppHelper.currentApplication();
            if (app != null) {
                contextToUse = app.getApplicationContext();
            } else if (lpparam.appInfo != null) {
                 try {
                    Context baseContext = XposedBridge.sInitialApplication != null ? XposedBridge.sInitialApplication.getApplicationContext() : null;
                    if (baseContext != null) {
                        contextToUse = baseContext.createPackageContext(lpparam.packageName, Context.CONTEXT_IGNORE_SECURITY);
                    } else {
                        LoggingHelper.warning(TAG, "No base context to create package context for " + lpparam.packageName + " in hookAppNetworkOperations.");
                    }
                } catch (PackageManager.NameNotFoundException | NullPointerException e) {
                     LoggingHelper.warning(TAG, "Failed to create package context for " + lpparam.packageName + " in hookAppNetworkOperations. Falling back. Error: " + e.getMessage());
                }
            }
            if (contextToUse == null && XposedBridge.sInitialApplication != null) {
                LoggingHelper.info(TAG, "Using XposedBridge.sInitialApplication as fallback context for manager initialization in hookAppNetworkOperations.");
                contextToUse = XposedBridge.sInitialApplication.getApplicationContext();
            }
            if (contextToUse != null) {
                initializeManagers(contextToUse);
            } else {
                LoggingHelper.error(TAG, "Still no context for managers in hookAppNetworkOperations for " + lpparam.packageName + ". Some hooks might not work.");
            }
        }

        try {
            final String packageName = lpparam.packageName;
            // ... existing code ...
        } catch (Throwable t) {
            LoggingHelper.error(TAG, "Failed to hook app-specific network operations", t);
        }
    }

    private void hookAppSpecificNetworkLibraries(LoadPackageParam lpparam) {
        String packageName = lpparam.packageName;
        ClassLoader appClassLoader = lpparam.classLoader;

        if (mSecurityManager != null) {
            try {
                LoggingHelper.debug(TAG, "Attempting to hook OkHttp for: " + packageName);
                Class<?> okhttpClientClass = XposedHelpers.findClass(
                    "okhttp3.OkHttpClient", 
                    appClassLoader); 
                
                XC_MethodHook.Unhook okhttpHook = XposedHelpers.findAndHookMethod(
                    okhttpClientClass,
                    "newCall",
                    XposedHelpers.findClass("okhttp3.Request", appClassLoader),
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(Param param) throws Throwable {
                            Object request = param.args[0];
                            Object httpUrl = XposedHelpers.callMethod(request, "url");
                            
                            String host = (String) XposedHelpers.callMethod(httpUrl, "host");
                            int port = (int) XposedHelpers.callMethod(httpUrl, "port");
                            
                            if (mSecurityManager.shouldAllowConnection(
                                    lpparam.packageName, host, port, SecurityManager.PROTO_TCP)) {
                                // Allowed
                            } else {
                                param.setThrowable(new IOException("Connection blocked by NetworkGuard (OkHttp to " + host + ":" + port + ")"));
                            }
                        }
                    }
                );
                mUnhooks.put(lpparam.packageName + ".OkHttpClient.newCall", okhttpHook);
                LoggingHelper.info(TAG, "OkHttp hooks installed successfully for " + lpparam.packageName);
            } catch (XposedHelpers.ClassNotFoundError e) {
                LoggingHelper.debug(TAG, "OkHttp not found in " + lpparam.packageName + ", skipping OkHttp hooks.");
            } catch (Throwable t) {
                LoggingHelper.warning(TAG, "Error hooking OkHttp for " + lpparam.packageName + ": " + t.getMessage());
            }
        } else {
            LoggingHelper.warning(TAG, "SecurityManager not initialized, skipping OkHttp hooks for " + packageName);
        }

        // Hook commonly used network libraries in different apps
        // ... existing code ...
    }

    public void onHotReload() {
        LoggingHelper.info(TAG, "Hot-reloading NetworkGuard module");
        
        mUnhooks.values().forEach(XC_MethodHook.Unhook::unhook);
        mUnhooks.clear();
        
        if (mSecurityManager != null) {
            mSecurityManager.removeListener(this);
        }
        if (mAnalyticsManager != null) {
            // If AnalyticsManager has listeners or state to clear on hot reload, do it here.
        }
        mManagersInitialized = false;

        if (this.mModuleContext != null) {
            LoggingHelper.info(TAG, "Re-initializing managers with stored mModuleContext on hot-reload.");
            initializeManagers(this.mModuleContext);
        } else if (XposedBridge.sInitialApplication != null) {
             LoggingHelper.warning(TAG, "mModuleContext was null during hot-reload, attempting with sInitialApplication for manager re-initialization.");
             initializeManagers(XposedBridge.sInitialApplication.getApplicationContext());
        } else {
            LoggingHelper.error(TAG, "Cannot re-initialize managers during hot-reload: No valid context stored or available.");
        }
        
        if (mStoredLpparam != null) {
            hookCoreNetworkApis(mStoredLpparam);
            hookAppNetworkOperations(mStoredLpparam);
            hookAppSpecificNetworkLibraries(mStoredLpparam);
        } else {
            LoggingHelper.error(TAG, "Cannot reinstall hooks during hot-reload: mStoredLpparam is null. Hooks will be re-applied on next handleLoadPackage.");
        }
    }
}