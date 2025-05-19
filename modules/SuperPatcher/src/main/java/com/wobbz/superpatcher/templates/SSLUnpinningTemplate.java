package com.wobbz.superpatcher.templates;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Template for SSL certificate pinning bypass.
 * Provides pre-defined hooks for common SSL pinning implementations.
 */
public class SSLUnpinningTemplate {

    /**
     * Generate a JSON configuration for SSL pinning bypass.
     * 
     * @return A JSON string containing hook configurations for SSL pinning bypass.
     */
    public static String generateTemplate() {
        try {
            JSONArray patches = new JSONArray();
            
            // OkHttp3 CertificatePinner
            patches.put(createPatch(
                "okhttp3.CertificatePinner",
                "check",
                "replace",
                new String[]{"java.lang.String", "java.util.List"},
                null,
                true
            ));
            
            // OkHttp3 CertificatePinner - Alternative method
            patches.put(createPatch(
                "okhttp3.CertificatePinner",
                "check$okhttp",
                "replace",
                new String[]{"java.lang.String", "java.util.List"},
                null,
                true
            ));
            
            // Android TrustManagerImpl checkServerTrusted
            patches.put(createPatch(
                "com.android.org.conscrypt.TrustManagerImpl",
                "checkServerTrusted",
                "replace",
                new String[]{"java.security.cert.X509Certificate[]", "java.lang.String", "java.lang.String"},
                createReturnValue("null", null),
                true
            ));
            
            // Android WebViewClient onReceivedSslError
            patches.put(createPatch(
                "android.webkit.WebViewClient",
                "onReceivedSslError",
                "replace",
                new String[]{"android.webkit.WebView", "android.webkit.SslErrorHandler", "android.net.http.SslError"},
                createMethodCall("handler", "proceed", new Object[0]),
                false
            ));
            
            // Custom X509TrustManager - checkServerTrusted
            patches.put(createPatch(
                "*TrustManager*",
                "checkServerTrusted",
                "replace",
                null,
                null,
                true
            ));
            
            // SSLContext.init with null TrustManager
            patches.put(createPatch(
                "javax.net.ssl.SSLContext",
                "init",
                "before",
                new String[]{"javax.net.ssl.KeyManager[]", "javax.net.ssl.TrustManager[]", "java.security.SecureRandom"},
                createNullTrustManager(),
                false
            ));
            
            return patches.toString(2);
            
        } catch (JSONException e) {
            e.printStackTrace();
            return "[]";
        }
    }
    
    /**
     * Create a basic patch configuration.
     */
    private static JSONObject createPatch(String className, String methodName, String hookType,
                                          String[] parameterTypes, JSONObject extraConfig, boolean wildcard) 
                                          throws JSONException {
        JSONObject patch = new JSONObject();
        
        // If wildcard is true, we'll match partial class names
        patch.put("className", className);
        patch.put("methodName", methodName);
        patch.put("hookType", hookType);
        patch.put("useWildcard", wildcard);
        
        if (parameterTypes != null) {
            JSONArray paramTypesArray = new JSONArray();
            for (String type : parameterTypes) {
                paramTypesArray.put(type);
            }
            patch.put("parameterTypes", paramTypesArray);
        }
        
        // Add any extra configuration
        if (extraConfig != null) {
            for (String key : extraConfig.keySet()) {
                patch.put(key, extraConfig.get(key));
            }
        }
        
        return patch;
    }
    
    /**
     * Create a return value configuration.
     */
    private static JSONObject createReturnValue(String type, Object value) throws JSONException {
        JSONObject returnValue = new JSONObject();
        returnValue.put("type", type);
        if (value != null) {
            returnValue.put("value", value);
        }
        return returnValue;
    }
    
    /**
     * Create a method call configuration.
     */
    private static JSONObject createMethodCall(String objectName, String methodName, Object[] args) 
                                              throws JSONException {
        JSONObject methodCall = new JSONObject();
        methodCall.put("type", "methodCall");
        methodCall.put("object", objectName);
        methodCall.put("method", methodName);
        
        JSONArray argsArray = new JSONArray();
        for (Object arg : args) {
            argsArray.put(arg);
        }
        methodCall.put("args", argsArray);
        
        return methodCall;
    }
    
    /**
     * Create a configuration to replace the TrustManager with a null TrustManager.
     */
    private static JSONObject createNullTrustManager() throws JSONException {
        JSONObject config = new JSONObject();
        config.put("modifyArgs", true);
        
        JSONArray argValues = new JSONArray();
        argValues.put(JSONObject.NULL); // KeyManager[] - keep original
        
        // Create null TrustManager array
        JSONObject trustManagerArg = new JSONObject();
        trustManagerArg.put("type", "customTrustManager");
        argValues.put(trustManagerArg);
        
        argValues.put(JSONObject.NULL); // SecureRandom - keep original
        
        config.put("argValues", argValues);
        return config;
    }
} 