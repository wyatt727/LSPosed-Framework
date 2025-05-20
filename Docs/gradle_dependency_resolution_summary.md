# Gradle Dependency Resolution Guide

## Current Approach: Local Source Inclusion

This project includes the libxposed API directly as source code in the `libxposed-api` directory, making it available to all modules through local project dependencies.

### How It Works

1. The `libxposed-api` directory contains the complete source code of the API
2. The project's `settings.gradle` includes this directory as a subproject
3. Modules reference the API using a project dependency: `compileOnly project(':libxposed-api:api')`

### Benefits of This Approach

- **Consistent API Version**: All modules use exactly the same API implementation
- **Direct Access to Source**: Better IDE support, easier debugging
- **No Network Dependencies**: Builds work even without internet access
- **Simplified Development**: No need to manage individual AAR files

## Implementing This Approach

### In settings.gradle
```gradle
// Include the libxposed-api project
include ':libxposed-api:api'
```

### In module build.gradle
```gradle
dependencies {
    // Reference the local API project
    compileOnly project(':libxposed-api:api')
}
```

## Ineffective/Problematic Approaches

The following approaches should be **avoided**:

### 1. JitPack Resolution (DEPRECATED)

```gradle
// DO NOT USE THIS APPROACH
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    compileOnly 'com.github.libxposed:api:x.x.x'
}
```

Problems:
- JitPack builds with incompatible JDK versions
- Potential version inconsistencies between modules
- Network dependency for builds

### 2. Individual AAR Downloads

```gradle
// DO NOT USE THIS APPROACH
dependencies {
    compileOnly files('libs/xposed-api.aar')
}
```

Problems:
- Multiple copies of the same library
- Version inconsistencies between modules
- Manual update process

### 3. Maven Central / api.lsposed.org

LSPosed API is not published to standard Maven repositories, making these approaches unreliable.

## Key Gradle Concepts Reinforced

### 1. Repository Order
Gradle searches repositories in the order they are declared. Always place more reliable repositories first.

### 2. Dependency Scopes
- `implementation`: Dependency is available at compile and runtime, and to module consumers
- `compileOnly`: Dependency is only available at compile time (appropriate for Xposed API)
- `runtimeOnly`: Dependency is only available at runtime

### 3. Transitive Dependencies
Dependencies can have their own dependencies, which Gradle resolves automatically, unless:
- The dependency is `compileOnly` (doesn't include transitive dependencies)
- The dependency has `optional` dependencies that need explicit inclusion

### 4. Dependency Resolution Management
In settings.gradle, you can use `dependencyResolutionManagement` to configure repositories and versions across the entire project:

```gradle
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // Other repositories
    }
}
```

### 5. Version Catalogs
Consider using Gradle's version catalogs for consistent dependency versions:

```gradle
// In settings.gradle
dependencyResolutionManagement {
    versionCatalogs {
        libs {
            library('androidx-core', 'androidx.core:core-ktx:1.9.0')
            // Other libraries
        }
    }
}

// In build.gradle
dependencies {
    implementation libs.androidx.core
}
```

## Conclusion

The current approach of including the libxposed API as a local source project provides the most reliable and developer-friendly solution. Modules should reference this local project rather than attempting to use JitPack or individual AAR files. 