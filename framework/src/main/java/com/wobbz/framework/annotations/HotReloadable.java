package com.wobbz.framework.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a module as capable of supporting hot-reloading of its code.
 * 
 * <p>When this annotation is present on a module class, the framework will attempt to call
 * the {@code onHotReload(String reloadedPackage)} method on the module instance after new
 * code has been pushed to the device without a full reboot.</p>
 * 
 * <p>Modules implementing hot-reload capability must:</p>
 * <ol>
 *   <li>Unhook all existing Xposed hooks</li>
 *   <li>Clean any state that might be stale from the previous code version</li>
 *   <li>Re-initialize and re-apply all necessary Xposed hooks using the new code</li>
 *   <li>Reload any external configuration files if necessary</li>
 * </ol>
 * 
 * <p>Example usage:</p>
 * <pre>
 * &#64;XposedPlugin(
 *     name = "My Awesome Module",
 *     version = "1.0.1",
 *     description = "Demonstrates framework features."
 * )
 * &#64;HotReloadable
 * public class MyAwesomeModule implements IXposedModule {
 *     // Module implementation...
 * 
 *     public void onHotReload(String reloadedPackage) throws Throwable {
 *         // Unhook existing hooks
 *         // Reset state
 *         // Re-apply hooks
 *         // Reload configuration
 *     }
 * }
 * </pre>
 * 
 * @see XposedPlugin
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface HotReloadable {
    // Marker annotation - no attributes required
} 