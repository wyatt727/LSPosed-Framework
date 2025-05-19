package com.wobbz.permissionoverride;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Service interface for PermissionOverrideModule, allowing other modules
 * to leverage its reflection and class/member finding capabilities, potentially
 * bypassing standard Android restrictions if configured.
 */
public interface IPermissionOverrideService {

    /**
     * Finds a class using the provided class loader. 
     * May employ enhanced techniques if standard reflection is restricted.
     *
     * @param className The fully qualified name of the class.
     * @param classLoader The class loader to use.
     * @return The Class object, or null if not found or not accessible.
     */
    Class<?> findClass(String className, ClassLoader classLoader);

    /**
     * Finds a method in the given class with the specified parameter types.
     * May employ enhanced techniques.
     *
     * @param clazz The class to search within.
     * @param methodName The name of the method.
     * @param parameterTypes An array of Class objects representing the parameter types.
     *                       Use XposedHelpers.getObjectClass(value) for object parameters if unsure.
     *                       Pass an empty array or null if the method has no parameters.
     * @return The Method object, or null if not found or not accessible.
     */
    Method findMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes);
    // Overload for Object[] to keep compatibility with SuperPatcher's current use which might pass XposedHelpers.getParameterTypes
    Method findMethodWithObjects(Class<?> clazz, String methodName, Object[] rawParameterTypes);

    /**
     * Finds a constructor in the given class with the specified parameter types.
     * May employ enhanced techniques.
     *
     * @param clazz The class to search within.
     * @param parameterTypes An array of Class objects representing the parameter types.
     * @return The Constructor object, or null if not found or not accessible.
     */
    Constructor<?> findConstructor(Class<?> clazz, Class<?>... parameterTypes);
    // Overload for Object[]
    Constructor<?> findConstructorWithObjects(Class<?> clazz, Object[] rawParameterTypes);

    /**
     * Gets the value of a field from an object.
     *
     * @param obj The object instance (or null for static fields).
     * @param className The name of the class where the field is defined (can be a superclass).
     * @param fieldName The name of the field.
     * @param classLoader The classloader to find the class if needed.
     * @return The value of the field, or null if not found/accessible or an error occurs.
     */
    Object getFieldValue(Object obj, String className, String fieldName, ClassLoader classLoader);

    /**
     * Sets the value of a field on an object.
     *
     * @param obj The object instance (or null for static fields).
     * @param className The name of the class where the field is defined.
     * @param fieldName The name of the field.
     * @param value The new value for the field.
     * @param classLoader The classloader to find the class if needed.
     * @return true if the field was set successfully, false otherwise.
     */
    boolean setFieldValue(Object obj, String className, String fieldName, Object value, ClassLoader classLoader);

    /**
     * Creates an instance of a class using its constructor.
     *
     * @param className The fully qualified name of the class to instantiate.
     * @param classLoader The class loader to use.
     * @param constructorParams The parameters to pass to the constructor.
     * @return The new object instance, or null if instantiation fails.
     */
    Object createInstance(String className, ClassLoader classLoader, Object... constructorParams);

    /**
     * Invokes a method on an object.
     *
     * @param obj The object instance (or null for static methods).
     * @param className The name of the class where the method is defined.
     * @param methodName The name of the method to invoke.
     * @param classLoader The classloader to find the class if needed.
     * @param params The parameters to pass to the method.
     * @return The result of the method invocation, or null if it fails or method is void.
     */
    Object invokeMethod(Object obj, String className, String methodName, ClassLoader classLoader, Object... params);
    
    /**
     * Checks the overridden permission status for a given package and permission.
     * Does not fall back to checking the actual system permission if no override exists.
     *
     * @param packageName The package name being checked.
     * @param permission The permission string (e.g., "android.permission.CAMERA").
     * @return PackageManager.PERMISSION_GRANTED, PackageManager.PERMISSION_DENIED, 
     *         or PERMISSION_DEFAULT if no specific override is set for this exact permission 
     *         (does not mean system default, means this module's default behavior setting for non-overridden permissions).
     *         Returns null if the package or permission is invalid or an error occurs.
     */
    Integer checkPermissionOverrideStatus(String packageName, String permission);
    
    /**
     * Checks if a permission is forced to be granted for a specific application.
     *
     * @param packageName The package name of the application.
     * @param permission The permission to check.
     * @return true if the permission is forced to be granted, false otherwise.
     */
    boolean P_isAppPermissionForced(String packageName, String permission);
    
    /**
     * Checks if a permission is suppressed (forced to be denied) for a specific application.
     *
     * @param packageName The package name of the application.
     * @param permission The permission to check.
     * @return true if the permission is suppressed, false otherwise.
     */
    boolean P_isAppPermissionSuppressed(String packageName, String permission);
} 