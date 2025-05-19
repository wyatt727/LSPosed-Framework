package com.wobbz.framework;

/**
 * Interface that must be implemented by modules marked with @HotReloadable
 * to support live code updates during development.
 */
public interface IHotReloadable {
    /**
     * Called when code changes have been detected and the module
     * should clean up old hooks and reinitialize with new implementation.
     */
    void onHotReload();
} 