package com.wobbz.superpatcher.templates;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Manager for predefined patch templates.
 * Provides easy access to commonly used patch configurations.
 */
public class TemplateManager {
    
    // Template types
    public static final String TEMPLATE_SSL_UNPINNING = "ssl_unpinning";
    public static final String TEMPLATE_DEBUG_FLAGS = "debug_flags";
    public static final String TEMPLATE_LOG_METHOD = "log_method";
    
    private static final Map<String, TemplateProvider> TEMPLATES = new HashMap<>();
    
    static {
        // Register templates
        TEMPLATES.put(TEMPLATE_SSL_UNPINNING, SSLUnpinningTemplate::generateTemplate);
        TEMPLATES.put(TEMPLATE_DEBUG_FLAGS, TemplateManager::getDebugFlagsTemplate);
        TEMPLATES.put(TEMPLATE_LOG_METHOD, TemplateManager::getLogMethodTemplate);
    }
    
    /**
     * Get a template by type.
     * 
     * @param templateType The template type.
     * @return The JSON configuration for the template.
     */
    public static String getTemplate(String templateType) {
        TemplateProvider provider = TEMPLATES.get(templateType);
        if (provider != null) {
            return provider.getTemplate();
        }
        return "[]";
    }
    
    /**
     * Get all available template types.
     * 
     * @return An array of template types.
     */
    public static String[] getAvailableTemplates() {
        return TEMPLATES.keySet().toArray(new String[0]);
    }
    
    /**
     * Get a human-readable name for a template type.
     * 
     * @param templateType The template type.
     * @return A human-readable name.
     */
    public static String getTemplateName(String templateType) {
        switch (templateType) {
            case TEMPLATE_SSL_UNPINNING: return "SSL Certificate Unpinning";
            case TEMPLATE_DEBUG_FLAGS: return "Enable Debug Flags";
            case TEMPLATE_LOG_METHOD: return "Log Method Calls";
            default: return templateType;
        }
    }
    
    /**
     * Get a description for a template type.
     * 
     * @param templateType The template type.
     * @return A description.
     */
    public static String getTemplateDescription(String templateType) {
        switch (templateType) {
            case TEMPLATE_SSL_UNPINNING: 
                return "Bypasses SSL certificate pinning in common frameworks like OkHttp3 and WebView";
            case TEMPLATE_DEBUG_FLAGS: 
                return "Enables various debug flags and developer options in applications";
            case TEMPLATE_LOG_METHOD: 
                return "Logs method calls with parameters and return values for debugging";
            default: return "";
        }
    }
    
    /**
     * Debug flags template for enabling developer options and debug mode.
     */
    private static String getDebugFlagsTemplate() {
        try {
            JSONArray patches = new JSONArray();
            
            // Enable debug flag on Application class
            patches.put(createSimplePatch(
                "android.app.Application",
                "onCreate",
                "after",
                null,
                "Enable debug mode on Application",
                "setFieldValue",
                new Object[]{"debug", true}
            ));
            
            // Enable debug mode in WebView
            patches.put(createSimplePatch(
                "android.webkit.WebView",
                "setWebContentsDebuggingEnabled",
                "replace",
                new String[]{"boolean"},
                "Enable WebView debugging",
                "callOriginal",
                new Object[]{true}
            ));
            
            // Enable StrictMode
            patches.put(createSimplePatch(
                "android.os.StrictMode",
                "enableDefaults",
                "after",
                null,
                "Enable StrictMode",
                null,
                null
            ));
            
            return patches.toString(2);
            
        } catch (JSONException e) {
            e.printStackTrace();
            return "[]";
        }
    }
    
    /**
     * Log method template for debugging method calls.
     */
    private static String getLogMethodTemplate() {
        try {
            JSONArray patches = new JSONArray();
            
            // Example of a generic method logging template
            JSONObject patch = new JSONObject();
            patch.put("className", "");  // To be filled by user
            patch.put("methodName", ""); // To be filled by user
            patch.put("hookType", "both");
            patch.put("logArgs", true);
            patch.put("logReturn", true);
            patch.put("description", "Log method calls with parameters and return values");
            
            patches.put(patch);
            
            return patches.toString(2);
            
        } catch (JSONException e) {
            e.printStackTrace();
            return "[]";
        }
    }
    
    /**
     * Create a simple patch configuration.
     */
    private static JSONObject createSimplePatch(String className, String methodName, String hookType,
                                              String[] parameterTypes, String description,
                                              String actionType, Object[] actionParams) 
                                              throws JSONException {
        JSONObject patch = new JSONObject();
        
        patch.put("className", className);
        patch.put("methodName", methodName);
        patch.put("hookType", hookType);
        patch.put("description", description);
        
        if (parameterTypes != null) {
            JSONArray paramTypesArray = new JSONArray();
            for (String type : parameterTypes) {
                paramTypesArray.put(type);
            }
            patch.put("parameterTypes", paramTypesArray);
        }
        
        if (actionType != null) {
            patch.put("actionType", actionType);
            
            if (actionParams != null) {
                JSONArray paramsArray = new JSONArray();
                for (Object param : actionParams) {
                    paramsArray.put(param);
                }
                patch.put("actionParams", paramsArray);
            }
        }
        
        return patch;
    }
    
    /**
     * Interface for template providers.
     */
    @FunctionalInterface
    private interface TemplateProvider {
        /**
         * Get the template JSON configuration.
         * 
         * @return The template as a JSON string.
         */
        String getTemplate();
    }
} 