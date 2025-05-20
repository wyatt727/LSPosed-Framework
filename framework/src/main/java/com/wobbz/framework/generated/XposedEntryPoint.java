package com.wobbz.framework.generated;

import com.wobbz.framework.PluginManager;
import com.wobbz.framework.development.LoggingHelper;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;

/**
 * Main entry point for LSPosed.
 * This class is referenced in META-INF/xposed/java_init.list
 */
public class XposedEntryPoint extends XposedModule {
    private final PluginManager mPluginManager = PluginManager.getInstance();
    
    /**
     * Constructor required by XposedModule
     * 
     * @param base The XposedInterface implementation
     * @param param Information about the process in which the module is loaded
     */
    public XposedEntryPoint(XposedInterface base, ModuleLoadedParam param) {
        super(base, param);
        
        // Set the XposedInterface for logging
        mPluginManager.setXposedInterface(base);
        LoggingHelper.setXposedInterface(base);
        
        // Handle module initialization in the constructor
        try {
            // During build, discovered modules will be registered here
            // Example: mPluginManager.registerModule(com.wobbz.examplemodule.ExamplePlugin.class);
            
            // Call the plugin manager to handle module initialization
            mPluginManager.handleZygoteInit(param);
        } catch (Throwable t) {
            // Log any errors that occur during initialization
            log("Error initializing LSPosed framework: " + t.getMessage());
            t.printStackTrace();
        }
    }
    
    @Override
    public void onPackageLoaded(PackageLoadedParam lpparam) {
        try {
            // Call the plugin manager to handle the loaded package
            mPluginManager.handleLoadPackage(lpparam);
        } catch (Throwable t) {
            // Log any errors that occur during package loading
            log("Error in LSPosed framework handling package " + lpparam.getPackageName() + ": " + t.getMessage());
            t.printStackTrace();
        }
    }
} 