# Gradle Dependency Resolution: Lessons Learned for Xposed API

This document summarizes the key findings and best practices identified while troubleshooting Gradle build failures related to the Xposed API dependency in the LSPosed Modular Framework project. Its purpose is to prevent similar issues and streamline future development.

## 1. The Core Problem

The primary build failure stemmed from Gradle's inability to resolve the Xposed API dependency, initially specified as `io.github.libxposed:api:0.4.2` and later corrected to `com.github.libxposed:api:0.4.2`. The error messages typically indicated "Could not find..." or "Could not resolve..." for this artifact, searching through configured repositories like `api.lsposed.org`, `api.xposed.info`, `mavenCentral()`, `google()`, and `mavenLocal()`.

## 2. Successful Solution: Using JitPack

The reliable method to include the Modern Xposed API (e.g., `0.4.2`) is by using JitPack.

### 2.1. Add JitPack Repository

JitPack dynamically builds and serves artifacts directly from GitHub repositories. It needs to be declared in your Gradle configuration.

**For older Gradle versions (root `build.gradle`):**

```groovy
// In your root build.gradle

buildscript {
    repositories {
        google()
        mavenCentral()
        // Add JitPack for buildscript dependencies if any (less common for API itself)
        maven { url "https://jitpack.io" }
    }
    // ... other buildscript configurations ...
}

allprojects {
    repositories {
        google()
        mavenCentral()
        // Add JitPack for project dependencies
        maven { url "https://jitpack.io" }
        mavenLocal() // Still useful for locally built artifacts
        // Other repositories like api.xposed.info can be kept if they host other Xposed-related tools
        // maven { url 'https://api.xposed.info/maven' }
    }
}
```

**For modern Gradle versions (Gradle 7+ in `settings.gradle` or `settings.gradle.kts`):**

This is the preferred approach for new projects.

```kotlin
// In settings.gradle.kts
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS) // Or PREFER_SETTINGS
  repositories {
    google()
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
    mavenLocal()
    // maven { url = uri("https://api.xposed.info/maven") }
  }
}
```

Or Groovy DSL for `settings.gradle`:
```groovy
// In settings.gradle
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS) // Or PREFER_SETTINGS
  repositories {
    google()
    mavenCentral()
    maven { url "https://jitpack.io" }
    mavenLocal()
    // maven { url "https://api.xposed.info/maven" }
  }
}
```
If using `dependencyResolutionManagement` in `settings.gradle`, ensure `allprojects { repositories { ... } }` and `buildscript { repositories { ... } }` blocks in the root `build.gradle` are removed or harmonized to avoid conflicts, with `settings.gradle` being the single source of truth for repository declarations.

### 2.2. Declare the Dependency Correctly

In the module that requires the Xposed API (e.g., `framework/build.gradle` or `modules/your_module/build.gradle`), use the following coordinates:

```groovy
dependencies {
    // ... other dependencies ...

    // Modern Xposed API (e.g., v0.4.2)
    // Use compileOnly as the API is provided by the LSPosed environment at runtime
    compileOnly "com.github.libxposed:api:0.4.2"

    // ... other dependencies ...
}
```
**Important:** The JitPack coordinate convention is `com.github.<GitHubUser>:<RepoName>:<TagOrCommitSHA>`.
For `libxposed/api`, this translates to `com.github.libxposed:api:<version_tag>`.

### 2.3. Verify Version
Ensure that the version tag (e.g., `0.4.2`) actually exists as a release or tag in the `libxposed/api` GitHub repository.

## 3. Ineffective/Problematic Approaches to Avoid

Several methods were attempted that proved unsuccessful or unreliable:

*   **Relying on `api.lsposed.org/maven`**: This endpoint was consistently unavailable due to DNS resolution failures (`nodename nor servname provided, or not known`). This URL should be considered unreliable.
*   **Relying on `api.xposed.info/maven` for `libxposed:api`**: While `api.xposed.info` hosts many Xposed-related artifacts (like the original `de.robv.android.xposed:api`), it does not appear to reliably host the specific `com.github.libxposed:api` artifacts or the desired versions needed for modern LSPosed development.
*   **Using `io.github.libxposed:api:0.4.2`**: This coordinate, while seemingly logical, is not how JitPack maps the `libxposed/api` repository. The correct prefix for JitPack-served GitHub projects is `com.github.<User>:<Repo>`.
*   **Manually Downloading AARs/JARs with `flatDir`**:
    *   This approach requires manually finding, downloading, and placing the correct AAR/JAR file into the project (e.g., a `libs` folder).
    *   It's prone to errors, such as downloading incomplete or incorrect files (e.g., an HTML page instead of the AAR, or a 0-byte file).
    *   It makes version management and updates cumbersome.
    *   While `flatDir { dirs 'libs' }` and `compileOnly files('libs/api-0.4.2.aar')` or `compileOnly name: 'api-0.4.2', ext: 'aar'` can work if the file is correct, it's less robust than using a proper Maven repository like JitPack.
*   **Using `maven { url 'https://raw.githubusercontent.com/libxposed/maven/master/repository' }`**: This URL points to a raw GitHub repository structure, not a functional Maven repository that Gradle can directly consume for the `com.github.libxposed:api` artifact in the standard way. JitPack provides the necessary translation layer.
*   **Substituting `de.robv.android.xposed:api:82` for `com.github.libxposed:api`**:
    *   `de.robv.android.xposed:api:82` is the **original Xposed Framework API**, not the modern `libxposed` API used by LSPosed.
    *   They have **significant API differences**. Attempting a direct substitution will lead to compilation errors due to missing classes, different method signatures, and changed functionalities (e.g., `XC_MethodHook.MethodHookParam` vs. `XC_MethodHook.Param`, different ways of accessing `LoadPackageParam`).
    *   This change requires substantial code modification throughout the Xposed modules.
*   **Adding `jcenter()`**: JCenter has been deprecated and shut down for new submissions. While it might still resolve some older cached artifacts, it's not a source for new libraries like `libxposed:api` and should be removed from repository lists to avoid slowing down builds.

## 4. Key Gradle Concepts Reinforced

*   **Repository Order and Specificity**: Gradle searches repositories in the order they are declared. Having unnecessary or incorrect repositories can slow down builds or lead to resolution errors.
*   **JitPack's Role**: JitPack acts as a dynamic Maven repository for GitHub (and other VCS) projects. It builds projects on demand based on tags or commit SHAs. Understanding its coordinate convention (`com.github.<User>:<Repo>:<Version>`) is crucial.
*   **Dependency Scopes**:
    *   `compileOnly`: Use for dependencies that are provided at runtime by the environment (like the Xposed API by LSPosed). This prevents bundling the API into your module APK.
    *   `implementation`: Use for dependencies that your module needs and should be bundled with it.
*   **`buildscript` vs. `allprojects` Repositories**:
    *   `buildscript { repositories { ... } }`: Declares repositories for Gradle plugins themselves (e.g., Android Gradle Plugin).
    *   `allprojects { repositories { ... } }`: Declares repositories for project dependencies (libraries your code uses).
*   **`dependencyResolutionManagement` (in `settings.gradle`)**: For modern Gradle (7+), this block provides a centralized way to declare repositories for all projects, improving build consistency and potentially performance. It's the recommended approach.
*   **`--refresh-dependencies`**: This Gradle flag can be useful to force Gradle to ignore cached resolution results and re-fetch dependency metadata.
*   **`./gradlew dependencies` or `./gradlew :module:dependencies`**: This command helps visualize the dependency tree and how Gradle resolves (or fails to resolve) artifacts.

By adhering to the successful JitPack configuration and avoiding the pitfalls listed, future Gradle build issues related to Xposed API dependencies should be significantly minimized. 