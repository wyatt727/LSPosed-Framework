# NetworkGuard Module API

## Overview

The NetworkGuard module primarily operates by hooking system and application network calls and deferring to the `com.wobbz.framework.security.SecurityManager` for decisions. It does not expose a direct programmatic API for other Xposed modules to call its core hooking functionalities extensively. Interactions from other modules are typically implicit through the `SecurityManager`.

The methods and classes described below are part of its public interface mainly for internal use, interaction with a potential settings/management UI, or specific framework services, rather than for direct invocation by other Xposed modules.

## Public Methods (Potentially for UI/Framework Interaction)

These methods might be called via reflection or direct class interaction if an instance of the main module class (`NetworkGuardModule`) is obtained.

-   `void addFirewallRule(String packageName, String destinationPattern, int portStart, int portEnd, int protocol, int ruleType)`
    *   **Description**: Adds a custom firewall rule directly to the `SecurityManager` if it's initialized.
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
    *   **Returns**: `AppNetworkStats` object.

-   `Map<String, Set<String>> getAllAppConnections()`
    *   **Description**: Returns a map of package names to a set of their active/observed connections.

-   `long getTotalBytesReceived()`
    *   **Description**: Returns the total bytes received across all monitored apps (implementation dependent).

-   `long getTotalBytesSent()`
    *   **Description**: Returns the total bytes sent (implementation dependent).

## `AppNetworkStats` Class

This public static inner class holds network statistics for an application:

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

## Configuration via `SecurityManager`

Modules or a central UI should configure network rules (allow/block lists, app restrictions) via the `SecurityManager` API. NetworkGuard listens to `SecurityManager` events and applies these rules based on its hooks, which are implemented using `io.github.libxposed.api.XposedInterface` and `io.github.libxposed.api.Hooker` classes.

## Settings and Module Reloading

The `libxposed-api` does not define a standardized hot-reloading mechanism comparable to older Xposed versions. If NetworkGuard's settings (e.g., rules fetched from `SecurityManager` or other configurations) need to be reloaded dynamically at runtime without a full process restart, this would typically involve:
-   The module implementing a listener for specific intents (e.g., broadcasts from a settings UI).
-   Upon receiving such an intent, the module would re-fetch its configuration and update its internal state.
-   Re-applying Xposed hooks is generally not done lightly and might not be necessary if the hook logic itself is data-driven (i.e., refers to the reloaded configuration).

## Interactions

-   **Listens to**: `SecurityManager` (for rule changes and firewall events).
-   **Uses**: `AnalyticsManager` (if applicable, for tracking hook performance), `LoggingHelper`.
-   **Core Hooking**: Implemented using `io.github.libxposed.api.XposedInterface` and `io.github.libxposed.api.Hooker` within its `IXposedModule` lifecycle methods (`onZygote`, `onSystemServer`, `onPackageLoaded`). 