# NetworkGuard Module API

## Overview

The NetworkGuard module operates by hooking system and application network calls and deferring to the `com.wobbz.framework.security.SecurityManager` for decisions. It operates as a bridge between the application network operations and the SecurityManager's rule enforcement.

The module implements the modern `io.github.libxposed.api` interfaces and hooks various network operations at different levels:
- Core system network classes (`Socket`, `URL`, etc.) in Zygote to intercept low-level connections
- App-specific network libraries (e.g., OkHttp) for more granular control of HTTP traffic
- System server network services for monitoring system-wide connectivity

## Implementation Status

This module primarily relies on SecurityManager for its rule storage and decision making. The public methods listed below are **implemented in the module** but most delegate to SecurityManager for the actual rule management.

## Public Methods

The following methods are implemented and available through the module's service API via FeatureManager:

-   `void addFirewallRule(String packageName, String destinationPattern, int portStart, int portEnd, int protocol, int ruleType)`
    *   **Description**: Adds a custom firewall rule through the `SecurityManager`.
    *   **Parameters**:
        *   `packageName`: Target package for the rule.
        *   `destinationPattern`: Regex for destination host/IP.
        *   `portStart`: Start of port range.
        *   `portEnd`: End of port range.
        *   `protocol`: e.g., `SecurityManager.PROTO_TCP`, `SecurityManager.PROTO_UDP`.
        *   `ruleType`: e.g., `SecurityManager.RULE_TYPE_ALLOW`, `SecurityManager.RULE_TYPE_BLOCK`.

-   `void blockAppNetwork(String packageName)`
    *   **Description**: Instructs `SecurityManager` to restrict all network access for the given package.

-   `void unblockAppNetwork(String packageName)`
    *   **Description**: Instructs `SecurityManager` to remove network restrictions for the given package.

-   `AppNetworkStats getAppNetworkStats(String packageName)`
    *   **Description**: Retrieves network statistics for a specific application.
    *   **Returns**: `AppNetworkStats` object with usage data.

-   `Map<String, Set<String>> getAllAppConnections()`
    *   **Description**: Returns a map of package names to their active/observed connections.

-   `long getTotalBytesReceived()`
    *   **Description**: Returns the total bytes received across all monitored apps.

-   `long getTotalBytesSent()`
    *   **Description**: Returns the total bytes sent across all monitored apps.

## Integration with SecurityManager

NetworkGuard implements the `SecurityManager.SecurityListener` interface to receive notifications about:
- Security rule changes
- Firewall events
- Connection attempts that are blocked
- Policy changes that require re-evaluating connections

When rule changes occur in SecurityManager, NetworkGuard updates its internal state accordingly to apply the changes to existing and new connections without requiring a full reboot.

## `AppNetworkStats` Class

This class holds network statistics for an application:

```java
public static class AppNetworkStats {
    public final AtomicLong connectionCount = new AtomicLong(0);
    public final AtomicLong httpRequests = new AtomicLong(0);
    public final AtomicLong bytesReceived = new AtomicLong(0);
    public final AtomicLong bytesSent = new AtomicLong(0);
    public final AtomicLong allowedConnections = new AtomicLong(0);
    public final AtomicLong blockedConnections = new AtomicLong(0);
    public final AtomicLong lastConnectionTime = new AtomicLong(0);
    public final Map<String, Integer> domainFrequency = new ConcurrentHashMap<>();
    public final Map<String, Long> domainDataUsage = new ConcurrentHashMap<>();
    public final AtomicLong isConnected = new AtomicLong(0); // 0 = false, 1 = true
}
```

## Configuration Schema

NetworkGuard itself has minimal configuration since it delegates most rule management to SecurityManager. Its settings.json contains:

```json
{
  "verboseLogging": false,          // Enable detailed logging of network operations
  "trackHttpRequests": true,        // Track detailed HTTP request information
  "interceptSSLTraffic": false,     // Attempt to intercept SSL traffic (requires additional setup)
  "enableAppSpecificHooks": true,   // Enable hooks for app-specific libraries like OkHttp
  "bypassSystemApps": true,         // Whether to bypass monitoring of system apps
  "targetApps": [                   // If empty, all non-system apps are targeted
    "com.example.app1",
    "com.example.app2"
  ]
}
```

## Hot Reload Support

NetworkGuard implements `@HotReloadable` and the `onHotReload()` method which:
1. Unhooks all active hooks
2. Re-initializes its connection with SecurityManager
3. Reloads settings
4. Re-applies the appropriate hooks

## Interactions with LSPosed Framework

-   **Annotations**: Uses `@XposedPlugin` and `@HotReloadable` annotations
-   **Lifecycle Methods**: Implements `onZygote`, `onSystemServerLoaded`, and `onPackageLoaded`
-   **Manager Interfaces**: Implements `SecurityManager.SecurityListener`
-   **Utilities**: Uses `LoggingHelper` for consistent logging 