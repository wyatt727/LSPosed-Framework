package com.wobbz.framework.generated;

import com.wobbz.framework.PluginManager;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * Main entry point for LSPosed.
 * This class is referenced in META-INF/xposed/java_init.list
 */
public class XposedEntryPoint implements IXposedHookZygoteInit, IXposedHookLoadPackage {
    private final PluginManager mPluginManager = PluginManager.getInstance();
    
    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        // During build, discovered modules will be registered here
        // Example: mPluginManager.registerModule(new SomeModule());
        
        // Forward to all registered modules
        mPluginManager.handleZygoteInit(startupParam);
    }
    
    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        // Forward to all registered modules
        mPluginManager.handleLoadPackage(lpparam);
    }
} 