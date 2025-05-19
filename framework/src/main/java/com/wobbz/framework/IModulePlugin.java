package com.wobbz.framework;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * Interface that all modules must implement to provide LSPosed hooks.
 */
public interface IModulePlugin extends IXposedHookZygoteInit, IXposedHookLoadPackage {
    /**
     * Initialize the module during Zygote startup.
     * This method is called once when the Zygote process starts.
     * 
     * @param startupParam Information about the module being loaded
     * @throws Throwable if any error occurs
     */
    @Override
    void initZygote(StartupParam startupParam) throws Throwable;
    
    /**
     * Handle the loading of a package.
     * This method is called whenever a new app process is created.
     * 
     * @param lpparam Information about the package being loaded
     * @throws Throwable if any error occurs
     */
    @Override
    void handleLoadPackage(LoadPackageParam lpparam) throws Throwable;
} 