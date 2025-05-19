package com.wobbz.framework.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark a module as supporting hot-reload for development.
 * Classes with this annotation must implement IHotReloadable interface.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface HotReloadable {
    /**
     * Port for development server, defaults to 8081
     */
    int port() default 8081;
} 