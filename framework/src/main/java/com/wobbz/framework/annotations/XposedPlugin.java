package com.wobbz.framework.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark a class as an LSPosed plugin module.
 * This will be processed at build time to generate required metadata.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface XposedPlugin {
    /**
     * Unique identifier for the module
     */
    String id();
    
    /**
     * Display name of the module
     */
    String name();
    
    /**
     * Module description
     */
    String description() default "";
    
    /**
     * Version string in semver format
     */
    String version() default "1.0.0";
    
    /**
     * Version code (integer)
     */
    int versionCode() default 1;
    
    /**
     * Package name for the module
     */
    String packageName() default "";
    
    /**
     * List of package names to target with this module
     */
    String[] scope() default {};
    
    /**
     * Author of the module
     */
    String author() default "";
    
    /**
     * List of permissions required by this module
     */
    String[] permissions() default {};
} 