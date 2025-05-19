# NetworkGuard Module

## Overview

NetworkGuard is an Xposed module designed for advanced network filtering, monitoring, and protection within the Wobbz LSPosed Framework. It allows for fine-grained control over network connections made by applications, leveraging the central `SecurityManager` for policy decisions.

## Features

- **Connection Interception**: Hooks core Android networking APIs (`java.net.Socket`, `java.net.URL`, `java.net.InetAddress`) and common third-party libraries (OkHttp, Facebook SDK, Google Cronet) to monitor and control network traffic.
- **Firewall Integration**: Works with `com.wobbz.framework.security.SecurityManager` to enforce firewall rules. This includes:
    - Blocking connections based on package name, destination host/domain, port, and protocol.
    - Respecting global domain blocklists/allowlists and app-specific rules defined in `SecurityManager`.
- **Connection Blocking**: Prevents unauthorized network connections by throwing `IOException` or returning specific values (e.g., loopback address for DNS lookups).
- **Network Statistics**: 
    - Tracks connection counts and HTTP requests per application (`AppNetworkStats`).
    - Monitors total bytes sent and received (though the current implementation for this seems basic).
    - Logs active connections.
- **App-Specific Hooks**: Provides capabilities to hook network libraries used by specific applications (e.g., Facebook, Google apps).
- **Connectivity State Monitoring**: Hooks `ConnectivityManager` to be aware of network state changes.
- **Hot-Reload Support**: Allows the module to be updated and reloaded at runtime without a full reboot.
- **Security Event Listening**: Listens to events from `SecurityManager` such as rule changes or firewall events.

## How It Works

1.  **Initialization (`initZygote`)**: 
    *   Attempts to initialize `SecurityManager` and `AnalyticsManager`.
    *   Installs core network hooks for APIs available globally in the Zygote process (e.g., `java.net.Socket`, `java.net.URL`).
2.  **Package Loading (`handleLoadPackage`)**:
    *   For each loaded application (that is not a system process or the framework itself):
        *   Initializes managers if they weren't ready from `initZygote`.
        *   Installs hooks for app-specific network operations, including libraries like OkHttp, Facebook SDK, Cronet, by using the application's classloader (`lpparam.classLoader`).
        *   Hooks `ConnectivityManager` for the app.
3.  **Connection Control**:
    *   Before a network connection is established (e.g., `Socket` constructor, `Socket.connect`, `URL.openConnection`, OkHttp `newCall`), NetworkGuard consults `SecurityManager.shouldAllowConnection()`.
    *   If `SecurityManager` denies the connection, NetworkGuard blocks it (e.g., by throwing an `IOException`).
    *   DNS lookups (`InetAddress.getAllByName`) can be redirected to loopback if blocked.
4.  **Statistics & Logging**:
    *   Connection attempts, successes, and failures (if logged by `SecurityManager`) are tracked.
    *   `AppNetworkStats` maintains per-app statistics.

## Configuration

NetworkGuard's behavior is primarily configured through the central `SecurityManager` rules (domain block/allow lists, app-specific firewall rules). 

Direct configuration specific to NetworkGuard (e.g., toggles for specific hook sets) would typically be managed via a `settings.json` file and the framework's `SettingsHelper`, though a specific one for NetworkGuard is not detailed in the provided code structure yet.

## Dependencies

- `com.wobbz.framework.IHotReloadable`
- `com.wobbz.framework.IModulePlugin`
- `com.wobbz.framework.analytics.AnalyticsManager`
- `com.wobbz.framework.development.LoggingHelper`
- `com.wobbz.framework.security.SecurityManager`

## Permissions Declared

- `android.permission.INTERNET`
- `android.permission.ACCESS_NETWORK_STATE`

## Scope

Targets: `android`, `com.android.phone`, `com.google.android.gms` (and potentially other apps via dynamic hooks). 