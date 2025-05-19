# NetworkGuard Module API

## Overview

The NetworkGuard module primarily operates by hooking system and application network calls and deferring to the `com.wobbz.framework.security.SecurityManager` for decisions. It does not expose a direct programmatic API for other modules to call extensively, but other modules can interact with it implicitly through the `SecurityManager`.

## Public Methods (Callable by Framework/Reflection)

While not typically called by other modules directly, these methods are part of its public interface for specific purposes (e.g., management UI, framework interactions):

-   `void addFirewallRule(String packageName, String destinationPattern, int portStart, int portEnd, int protocol, int ruleType)`
    *   **Description**: Adds a custom firewall rule directly to the `SecurityManager` if it's initialized. This is likely a helper or intended for a settings UI.
    *   **Parameters**:
        *   `packageName`: Target package for the rule.
        *   `destinationPattern`: Regex for destination host/IP.
        *   `portStart`: Start of port range.
        *   `portEnd`: End of port range.
        *   `protocol`: `SecurityManager.PROTO_TCP`, `PROTO_UDP`, etc.
        *   `ruleType`: `SecurityManager.RULE_TYPE_ALLOW`, `RULE_TYPE_BLOCK`.

-   `void blockAppNetwork(String packageName)`
    *   **Description**: Instructs `SecurityManager` to restrict all network access for the given package.

-   `void unblockAppNetwork(String packageName)`
    *   **Description**: Instructs `SecurityManager` to remove network restrictions for the given package.

-   `AppNetworkStats getAppNetworkStats(String packageName)`
    *   **Description**: Retrieves network statistics for a specific application.
    *   **Returns**: `AppNetworkStats` object containing counts for connections, HTTP requests, data usage (currently placeholder), etc.

-   `Map<String, Set<String>> getAllAppConnections()`
    *   **Description**: Returns a map of package names to a set of their active/observed connections (host:port strings or URLs).

-   `long getTotalBytesReceived()`
    *   **Description**: Returns the total bytes received across all monitored apps (currently placeholder, likely relies on `SecurityManager` or future data collection).

-   `long getTotalBytesSent()`
    *   **Description**: Returns the total bytes sent (currently placeholder).

## `AppNetworkStats` Class

This public static inner class holds network statistics for an application:

```java
public static class AppNetworkStats {
    public final AtomicLong connectionCount = new AtomicLong(0);
    public final AtomicLong httpRequests = new AtomicLong(0);
    public final AtomicLong bytesReceived = new AtomicLong(0); // Placeholder
    public final AtomicLong bytesSent = new AtomicLong(0);     // Placeholder
    public final AtomicLong allowedConnections = new AtomicLong(0);
    public final AtomicLong blockedConnections = new AtomicLong(0);
    public final AtomicLong lastConnectionTime = new AtomicLong(0);
    public final Map<String, Integer> domainFrequency = new ConcurrentHashMap<>();
    public final Map<String, Long> domainDataUsage = new ConcurrentHashMap<>();
    public final AtomicLong isConnected = new AtomicLong(0); // 0 = false, 1 = true
}
```

## Configuration via `SecurityManager`

Modules or a central UI should configure network rules (allow/block lists, app restrictions) via the `SecurityManager` API. NetworkGuard listens to `SecurityManager` events and applies these rules.

## Hot Reloading

Implements `IHotReloadable`. The `onHotReload()` method:
- Cleans up existing Xposed hooks.
- Removes itself as a listener from `SecurityManager`.
- Re-initializes `SecurityManager` and `AnalyticsManager` instances (if context is available).
- Re-adds itself as a listener to the new `SecurityManager` instance.
- Re-installs core network hooks.

## Interactions

-   **Listens to**: `SecurityManager` (for rule changes and firewall events).
-   **Uses**: `AnalyticsManager` (for tracking hook performance), `LoggingHelper`.

## Notes on Context Initialization Issue

(This note is for developers reading the API, assuming the context fix attempt in code was not fully successful via automated edit)

The initialization of `mContext` in `initZygote` (and thus the early initialization of `SecurityManager` and `AnalyticsManager`) is problematic as `StartupParam.modulePath` is a String, not a Context. The module attempts to initialize these managers using `XposedBridge.sInitialApplication` or later in `handleLoadPackage`. If these managers are not available when hooks are made (especially core hooks in `initZygote`), those hooks might not be able to enforce security policies until the managers are fully initialized. This is a known issue requiring careful handling or a framework-level solution for context provisioning in `initZygote`. 