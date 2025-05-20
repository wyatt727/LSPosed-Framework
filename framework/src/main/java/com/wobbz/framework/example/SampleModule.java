package com.wobbz.framework.example;

import com.wobbz.framework.IHotReloadable;
import com.wobbz.framework.annotations.HotReloadable;
import com.wobbz.framework.annotations.XposedPlugin;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedInterface.MethodUnhooker;
import io.github.libxposed.api.XposedContext;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import io.github.libxposed.api.hooks.HookMode;
import io.github.libxposed.api.hooks.Hooker;
import io.github.libxposed.api.hooks.HookerWithContext;
import io.github.libxposed.api.hooks.InlineHooker;

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sample implementation of a hot-reloadable Xposed module.
 * This class demonstrates the proper way to implement a module that supports hot-reloading.
 */
@XposedPlugin(
    name = "Sample Module",
    version = "1.0.0",
    description = "Demonstrates proper implementation of a hot-reloadable module",
    author = "WobbzDev",
    scope = {"android", "com.android.settings"}
)
@HotReloadable
public class SampleModule implements XposedModule, IHotReloadable {
    
    private static final String TAG = "SampleModule";
    
    // Store the Xposed context for later use
    private XposedContext xposedContext;
    
    // Keep track of our active hooks for each package
    private final Map<String, List<MethodUnhooker<?>>> activeHooks = new HashMap<>();
    
    @Override
    public void onInit(XposedContext context) {
        this.xposedContext = context;
        context.getLogger().i(TAG, "Module initialized");
    }
    
    @Override
    public void onSystemServerLoaded(XposedModuleInterface moduleInterface) {
        try {
            hookSystemServer(moduleInterface);
        } catch (Throwable t) {
            xposedContext.getLogger().e(TAG, "Error hooking System Server", t);
        }
    }
    
    @Override
    public void onPackageLoaded(XposedModuleInterface moduleInterface, String packageName) {
        try {
            if ("com.android.settings".equals(packageName)) {
                hookSettings(moduleInterface, packageName);
            }
        } catch (Throwable t) {
            xposedContext.getLogger().e(TAG, "Error hooking package: " + packageName, t);
        }
    }
    
    @Override
    public void onHotReload(String reloadedPackage) throws Throwable {
        xposedContext.getLogger().i(TAG, "Hot-reload triggered for: " + 
                (reloadedPackage != null ? reloadedPackage : "all packages"));
        
        if (reloadedPackage != null) {
            // Only unhook the specific package
            unhookPackage(reloadedPackage);
        } else {
            // Unhook all packages
            for (String pkg : activeHooks.keySet()) {
                unhookPackage(pkg);
            }
        }
        
        xposedContext.getLogger().i(TAG, "Hot-reload completed successfully");
    }
    
    /**
     * Unhook all methods for a specific package.
     */
    private void unhookPackage(String packageName) {
        List<MethodUnhooker<?>> hooks = activeHooks.get(packageName);
        if (hooks != null) {
            xposedContext.getLogger().i(TAG, "Unhooking " + hooks.size() + " methods for package: " + packageName);
            for (MethodUnhooker<?> unhooker : hooks) {
                try {
                    unhooker.unhook();
                } catch (Throwable t) {
                    xposedContext.getLogger().e(TAG, "Error unhooking method", t);
                }
            }
            hooks.clear();
        }
    }
    
    /**
     * Hook into the System Server.
     */
    private void hookSystemServer(XposedModuleInterface moduleInterface) throws Throwable {
        xposedContext.getLogger().i(TAG, "Hooking System Server");
        
        // Initialize the hooks list for this package if needed
        if (!activeHooks.containsKey("android")) {
            activeHooks.put("android", new ArrayList<>());
        }
        
        // Example: Hook ActivityManagerService.startActivity
        ClassLoader classLoader = moduleInterface.getClassLoader();
        Class<?> activityManagerService = moduleInterface.findClass("com.android.server.am.ActivityManagerService", classLoader);
        
        for (Method method : activityManagerService.getDeclaredMethods()) {
            if ("startActivity".equals(method.getName())) {
                xposedContext.getLogger().i(TAG, "Found ActivityManagerService.startActivity method: " + method);
                
                MethodUnhooker<?> unhooker = moduleInterface.hook(method, new StartActivityHooker());
                activeHooks.get("android").add(unhooker);
            }
        }
    }
    
    /**
     * Hook into the Settings app.
     */
    private void hookSettings(XposedModuleInterface moduleInterface, String packageName) throws Throwable {
        xposedContext.getLogger().i(TAG, "Hooking Settings app");
        
        // Initialize the hooks list for this package if needed
        if (!activeHooks.containsKey(packageName)) {
            activeHooks.put(packageName, new ArrayList<>());
        }
        
        // Example: Hook a Settings activity
        ClassLoader classLoader = moduleInterface.getClassLoader();
        Class<?> settingsActivity = moduleInterface.findClass("com.android.settings.Settings", classLoader);
        
        Method onCreate = settingsActivity.getDeclaredMethod("onCreate", android.os.Bundle.class);
        xposedContext.getLogger().i(TAG, "Hooking Settings.onCreate");
        
        MethodUnhooker<?> unhooker = moduleInterface.hook(onCreate, new OnCreateHooker());
        activeHooks.get(packageName).add(unhooker);
    }
    
    /**
     * Hook implementation for ActivityManagerService.startActivity.
     */
    private class StartActivityHooker implements Hooker {
        
        @Override
        public void beforeHook(XposedInterface.HookParam param) throws Throwable {
            // Log and potentially modify or block intent launching
            xposedContext.getLogger().i(TAG, "ActivityManagerService.startActivity called");
        }
        
        @Override
        public void afterHook(XposedInterface.HookParam param) throws Throwable {
            // Log or modify the result
            xposedContext.getLogger().i(TAG, "ActivityManagerService.startActivity returned: " + param.getResult());
        }
    }
    
    /**
     * Hook implementation for Settings.onCreate.
     */
    private class OnCreateHooker implements Hooker {
        
        @Override
        public void beforeHook(XposedInterface.HookParam param) throws Throwable {
            xposedContext.getLogger().i(TAG, "Settings.onCreate called");
        }
        
        @Override
        public void afterHook(XposedInterface.HookParam param) throws Throwable {
            xposedContext.getLogger().i(TAG, "Settings.onCreate completed");
        }
    }
} 