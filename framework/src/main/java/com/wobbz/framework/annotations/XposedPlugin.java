package com.wobbz.framework.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines essential metadata for an Xposed module. This annotation replaces the traditional
 * `xposed_init` file and `module.prop` for basic information, enabling compile-time
 * validation and easier management.
 * 
 * <p>Must be applied to a class that implements {@code io.github.libxposed.api.IXposedModule}.</p>
 * 
 * <p>Example usage:</p>
 * <pre>
 * &#64;XposedPlugin(
 *     name = "My Awesome Module",
 *     version = "1.0.1",
 *     description = "Demonstrates framework features.",
 *     author = "WobbzDev",
 *     scope = {"com.android.settings"}
 * )
 * &#64;HotReloadable
 * public class MyAwesomeModule implements IXposedModule {
 *     // ... module implementation ...
 * }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface XposedPlugin {
    
    /**
     * The human-readable name of the module.
     * 
     * @return Module name
     */
    String name();
    
    /**
     * The version string of the module (e.g., "1.0.0").
     * 
     * @return Module version
     */
    String version();
    
    /**
     * A brief description of what the module does.
     * 
     * @return Module description (default: empty string)
     */
    String description() default "";
    
    /**
     * The author(s) of the module.
     * 
     * @return Module author(s) (default: empty string)
     */
    String author() default "";
    
    /**
     * An array of package names that this module primarily targets.
     * This helps in organizing and potentially filtering modules in a management UI.
     * 
     * @return Target package names (default: empty array)
     */
    String[] scope() default {};
    
    /**
     * The minimum version of the Wobbz LSPosed Framework this module is compatible with.
     * 
     * @return Minimum framework version (default: 1)
     */
    int minFrameworkVersion() default 1;
} 