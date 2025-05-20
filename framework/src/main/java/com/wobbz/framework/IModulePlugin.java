package com.wobbz.framework;

import io.github.libxposed.api.XposedModuleInterface;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;

/**
 * Interface that all modules must implement to provide LSPosed hooks.
 */
public interface IModulePlugin extends XposedModuleInterface {
    /**
     * Called when the module is loaded in Zygote
     * This is equivalent to the onModuleLoaded method in XposedModuleInterface
     * 
     * @param startupParam Information about the process in which the module is loaded
     */
    default void onModuleLoaded(ModuleLoadedParam startupParam) {
        // Default empty implementation
    }

    /**
     * Get the unique module ID.
     * This is used for configuration and tracking.
     * 
     * @return The module ID
     */
    default String getModuleId() {
        return this.getClass().getName();
    }
} 