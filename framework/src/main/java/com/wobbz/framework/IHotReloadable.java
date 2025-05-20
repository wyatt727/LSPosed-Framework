package com.wobbz.framework;

/**
 * Interface to be implemented by Xposed modules that support hot-reloading.
 * 
 * <p>For a module to support hot-reloading, it must:</p>
 * <ol>
 *   <li>Be annotated with {@code @HotReloadable}</li>
 *   <li>Implement this interface</li>
 *   <li>Properly handle the {@link #onHotReload(String)} callback</li>
 * </ol>
 * 
 * <p>During hot-reload, the module instance remains the same, but the code is updated.
 * The module must clean up its state, unhook all methods, and then re-initialize itself.</p>
 */
public interface IHotReloadable {
    
    /**
     * Called when the module's code has been updated and needs to be hot-reloaded.
     * 
     * <p>Implementation requirements:</p>
     * <ol>
     *   <li>Unhook all existing Xposed hooks</li>
     *   <li>Clean up any state that might be stale</li>
     *   <li>Re-initialize and re-apply all necessary hooks</li>
     *   <li>Reload any external configuration files if necessary</li>
     * </ol>
     * 
     * @param reloadedPackage The package name that triggered the reload, or null if it's a global reload.
     *                        This allows the module to only reload hooks for the specific package
     *                        if desired.
     * @throws Throwable If any error occurs during hot-reload
     */
    void onHotReload(String reloadedPackage) throws Throwable;
} 