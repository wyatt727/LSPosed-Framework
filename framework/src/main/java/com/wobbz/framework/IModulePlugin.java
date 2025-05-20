package com.wobbz.framework;

import io.github.libxposed.api.XposedModuleInterface;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;

/**
 * Defines the core contract for all feature modules within the Wobbz LSPosed Modular Framework.
 * Modules implementing this interface can be discovered and managed by the framework,
 * and will receive standard Xposed lifecycle callbacks.
 *
 * <p>This interface extends {@link XposedModuleInterface}, inheriting its standard callback methods
 * such as {@link #onPackageLoaded(PackageLoadedParam)}, {@link #onSystemServerLoaded(SystemServerLoadedParam)},
 * and {@link #onResourcesLoaded(ResourcesLoadedParam)}. Modules should override these methods
 * to implement their specific hooking logic.</p>
 *
 * <p>The framework may provide additional lifecycle methods or dependency injection capabilities
 * for modules implementing this interface beyond the standard Xposed callbacks.</p>
 */
public interface IModulePlugin extends XposedModuleInterface {

    /**
     * Called by the framework when the module is first loaded into a process, typically Zygote or system_server,
     * or when it's loaded into a target application process.
     * This method serves as an initial entry point for the module, similar to
     * {@link XposedModuleInterface#onModuleLoaded(XposedModuleInterface.ModuleLoadedParam)},
     * but is managed by the Wobbz Framework.
     *
     * <p>Implementations can use this callback for one-time initializations, setting up
     * resources, or preparing for hooks. It's generally preferred to perform actual hooking
     * within {@link #onPackageLoaded(PackageLoadedParam)} for application-specific hooks or
     * {@link #onSystemServerLoaded(SystemServerLoadedParam)} for system-wide hooks.</p>
     *
     * @param startupParam Provides information about the context in which the module is loaded,
     *                     such as the process name and class loader. This is the same parameter
     *                     passed to {@link XposedModuleInterface#onModuleLoaded(XposedModuleInterface.ModuleLoadedParam)}.
     */
    @Override
    default void onModuleLoaded(ModuleLoadedParam startupParam) {
        // Default implementation does nothing. Modules should override if specific
        // on-load actions are required beyond what XposedModuleInterface provides directly.
    }

    /**
     * Retrieves a unique identifier for this module.
     * The framework uses this ID for various purposes, such as:
     * <ul>
     *     <li>Managing module-specific configurations.</li>
     *     <li>Tracking module state and lifecycle.</li>
     *     <li>Resolving inter-module dependencies.</li>
     * </ul>
     *
     * <p>By default, this method returns the fully qualified class name of the implementing module.
     * It is recommended that modules relying on persistent configuration or inter-module
     * communication provide a stable and unique ID, potentially by overriding this method
     * or by using the {@code id} field in a `module-info.json` file if supported by the framework.</p>
     *
     * @return A string representing the unique identifier of this module. Defaults to the class name.
     */
    default String getModuleId() {
        return this.getClass().getName();
    }
} 