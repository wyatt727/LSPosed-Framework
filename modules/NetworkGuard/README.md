# NetworkGuard Module

## Overview

NetworkGuard is an Xposed module designed for advanced network filtering, monitoring, and protection within the Wobbz LSPosed Framework. It allows for fine-grained control over network connections made by applications, leveraging the central `SecurityManager` for policy decisions and utilizing the `io.github.libxposed.api.XposedInterface` for interacting with the Xposed framework.

## Features

- **Connection Interception**: Hooks core Android networking APIs (`java.net.Socket`, `java.net.URL`, `java.net.InetAddress`) and common third-party libraries (OkHttp) to monitor and control network traffic.
- **Firewall Integration**: Works with `com.wobbz.framework.security.SecurityManager` to enforce firewall rules. This includes:
    - Blocking connections based on package name, destination host/domain, port, and protocol.
    - Respecting global domain blocklists/allowlists and app-specific rules defined in `SecurityManager`.
- **Connection Blocking**: Prevents unauthorized network connections by throwing `IOException` or returning specific values (e.g., loopback address for DNS lookups).
- **Network Statistics**:
    - Tracks connection counts and HTTP requests per application (`AppNetworkStats`).
    - Monitors total bytes sent and received.
    - Logs active connections.
- **App-Specific Hooks**: Provides capabilities to hook network libraries used by specific applications.
- **Connectivity State Monitoring**: Hooks `ConnectivityManager` to be aware of network state changes.
- **Security Event Listening**: Listens to events from `SecurityManager` such as rule changes or firewall events.

## How It Works

NetworkGuard implements the `io.github.libxposed.api.IXposedModule` interface to integrate with the LSPosed framework.

1.  **Initialization (`onZygote`)**:
    *   Early initialization tasks can be performed here if needed, typically for hooks that need to be active in the Zygote process.
    *   Core network hooks for APIs available globally (e.g., `java.net.Socket`, `java.net.URL`) are often set up.
2.  **System Server Initialization (`onSystemServer`)**:
    *   Hooks related to system server processes can be initialized here.
3.  **Package Loading (`onPackageLoaded`)**:
    *   This method is called for each loaded application.
    *   The module uses the provided `XposedInterface` instance (`param.getXposed()`) to access Xposed functionalities like finding and hooking methods.
    *   Hooks are typically organized into `io.github.libxposed.api.Hooker` classes for better structure and maintainability.
    *   For each relevant application (excluding system processes or the framework itself):
        *   Initializes managers if necessary.
        *   Installs hooks for app-specific network operations, including libraries like OkHttp, using the application's classloader (`param.getAppInfo().getClassLoader()`).
        *   Hooks `ConnectivityManager` for the app if required.
4.  **Connection Control**:
    *   Before a network connection is established (e.g., `Socket` constructor, `Socket.connect`, `URL.openConnection`, OkHttp `newCall`), NetworkGuard consults `SecurityManager.shouldAllowConnection()`.
    *   If `SecurityManager` denies the connection, NetworkGuard blocks it (e.g., by throwing an `IOException`).
    *   DNS lookups (`InetAddress.getAllByName`) can be redirected to loopback if blocked.
5.  **Statistics & Logging**:
    *   Connection attempts, successes, and failures are tracked.
    *   `AppNetworkStats` maintains per-app statistics.

## Configuration

NetworkGuard's behavior is primarily configured through the central `SecurityManager` rules (domain block/allow lists, app-specific firewall rules).

## Dependencies

-   **Xposed API**: `compileOnly project(':libxposed-api:api')`
    -   Uses `io.github.libxposed.api.XposedInterface` for framework interaction.
    -   Uses `io.github.libxposed.api.Hooker` for organizing hook implementations.
-   `com.wobbz.framework.analytics.AnalyticsManager` (if still applicable)
-   `com.wobbz.framework.development.LoggingHelper` (if still applicable)
-   `com.wobbz.framework.security.SecurityManager`

## Code Example: Hooking `Socket.connect`

Below is a simplified example of how NetworkGuard might hook the `Socket.connect(SocketAddress endpoint, int timeout)` method using the new API:

```java
package com.wobbz.networkguard.hooks;

import io.github.libxposed.api.Hooker;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.annotations.BeforeHook;
import java.net.Socket;
import java.net.SocketAddress;
import com.wobbz.framework.security.SecurityManager; // Assuming SecurityManager path

public class SocketConnectHook implements Hooker {

    private final SecurityManager securityManager;

    public SocketConnectHook(SecurityManager securityManager) {
        this.securityManager = securityManager;
    }

    @BeforeHook(target = Socket.class, method = "connect", parameterTypes = {SocketAddress.class, int.class})
    public static void beforeConnect(XposedInterface.BeforeHookParam param) throws Throwable {
        Socket instance = (Socket) param.getThisObject();
        SocketAddress endpoint = (SocketAddress) param.getArgs()[0];
        // int timeout = (int) param.getArgs()[1]; // Timeout parameter

        // Obtain an instance of SecurityManager, e.g., via a singleton or passed in
        // For this example, let's assume it's accessible
        // SecurityManager securityManager = obtainSecurityManager();


        // Example: Get the hook instance from param if it was registered with one
        SocketConnectHook hookInstance = (SocketConnectHook) param.getHooker();


        if (hookInstance.securityManager != null && !hookInstance.securityManager.shouldAllowConnection(instance, endpoint)) {
            param.setThrowable(new java.io.IOException("Connection blocked by NetworkGuard: " + endpoint.toString()));
        }
        // Else, allow connection
    }

    // Helper method to obtain SecurityManager (implementation specific)
    // private static SecurityManager obtainSecurityManager() {
    //    // ... logic to get SecurityManager instance ...
    //    return YourSecurityManagerProvider.get();
    // }
}

// In your main module class implementing IXposedModule:
// public class NetworkGuardModule implements IXposedModule {
//     @Override
//     public void onPackageLoaded(XposedInterface.PackageLoadedParam param) {
//         if (param.isFirstParty()) return; // Example: ignore first party apps

//         SecurityManager sm = ...; // Obtain your SecurityManager
//         param.getXposed().hook(new SocketConnectHook(sm), param.getAppInfo().getClassLoader());
//     }
//     // ... other IXposedModule methods
// }
```

## Permissions Declared

-   `android.permission.INTERNET`
-   `android.permission.ACCESS_NETWORK_STATE`

## Scope

Targets: `android`, `com.android.phone`, `com.google.android.gms` (and potentially other apps via dynamic hooks).

## Development Environment

This module is developed using Java 17. 