# Product Requirements Document (PRD)

**Project:** LSPosed Modular Framework
**Author:** \[Your Name]
**Date:** May 19, 2025
**Version:** 1.0

---

## 1. Purpose and Background

**1.1 Purpose**
Define and document the architecture, requirements, and roadmap for a reusable LSPosed Modular Framework that unifies multiple feature-based Xposed modules into a single, extensible, and maintainable project.

**1.2 Background**
Developers often create standalone LSPosed modules for individual tweaks (e.g., DebugAll, AdBlocker). Maintaining separate codebases leads to duplication, inconsistent configurations, and version drift. A consolidated framework solves these issues.

---

## 2. Objectives and Goals

* Provide a single codebase that houses multiple, independent LSPosed plugins.
* Standardize build and packaging, ensuring all modules share consistent Xposed API versions, Gradle settings, and ProGuard rules.
* Enable rapid onboarding: adding a new feature requires only a descriptor and plugin class.
* Support per-module scope definitions to minimize runtime overhead.
* Automate manifest and resource generation for all modules.
* Integrate with CI/CD for automated builds, tests, and releases.

---

## 3. Stakeholders

* **Primary:** LSPosed module developers and maintainers.
* **Secondary:** QA engineers, automated testing systems, security researchers benefiting from unified modules.

---

## 4. Scope

**In Scope**

* Host Framework library: common utilities, plugin manager, generated entry-point.
* Feature Modules: individual subprojects containing hook code and metadata.
* Build scripts: root, framework, and per-module Gradle configurations.
* Descriptor-based metadata system (YAML → `java_init.list`, `scope.list`, `module.prop`).
* CI pipelines for build and release.

**Out of Scope**

* UI frontends beyond LSPosed Manager metadata.
* Native NDK-specific hooks (can be added as modules but not core to framework).
* Packaging non-Xposed artifacts (e.g., standalone APKs).

---

## 5. Functional Requirements

| ID    | Title                           | Description                                                                            | Priority |
| ----- | ------------------------------- | -------------------------------------------------------------------------------------- | -------- |
| FR-01 | Dynamic Manifest Generation     | Auto-merge module descriptors into `META-INF/xposed/java_init.list` and related files. | High     |
| FR-02 | Plugin Discovery & Loading      | Reflectively instantiate all `IModulePlugin` implementations at runtime.               | High     |
| FR-03 | Centralized Logging & Utilities | Provide shared logging, reflection, and safe-execution wrappers for modules.           | Medium   |
| FR-04 | Per-Module Scope Configuration  | Honor each module’s scope definitions to limit hook injection.                         | High     |
| FR-05 | Consistent Build Settings       | Enforce shared compile options, Xposed API version, and ProGuard rules.                | High     |
| FR-06 | CI Integration                  | Automate build, test, and artifact publishing for the combined framework.              | Medium   |

---

## 6. Non-Functional Requirements

| ID     | Title           | Description                                                | Target   |
| ------ | --------------- | ---------------------------------------------------------- | -------- |
| NFR-01 | Performance     | Overhead per plugin load ≤ 3% of app startup time.         | ≤3%      |
| NFR-02 | Maintainability | Module addition/removal < 10 minutes per feature.          | <10 min  |
| NFR-03 | Reliability     | 99.9% uptime; framework must not crash host processes.     | 99.9%    |
| NFR-04 | Compatibility   | Support Android API 21–34 and arm64-v8a devices.           | Broad    |
| NFR-05 | Security        | No introduction of new attack surface; library-only hooks. | Critical |

---

## 7. Architecture Overview

* **Root Project:** Defines common versions (Xposed API, SDK, Java) and repositories.
* **Framework Module:** Android library containing:

  * **PluginManager**: orchestrates `initZygote` and `handleLoadPackage` across plugins.
  * **Utility classes**: logging, reflection helpers, safe hooks.
  * **Template resources**: `java_init.list.tpl`, `module.prop.tpl`, merged at build time.
* **Feature Modules:** Subprojects under `modules/`, each with:

  * `descriptor.yaml`: metadata (ID, name, description, entry classes, scope).
  * Plugin implementation `MyFeatureModule.java` implementing `IModulePlugin`.
  * Minimal `build.gradle` with dependency on `:framework`.
* **Build Process:**

  1. Root Gradle reads project structure.
  2. Framework plugin task scans `descriptor.yaml` files.
  3. Generates merged `META-INF` resources.
  4. Compiles and assembles a single APK containing all modules.

---

## 8. Module Extension Process

1. **Create New Module:** Copy an existing example under `modules/`.
2. **Define Descriptor:** Fill `descriptor.yaml` with unique ID, name, description, entry class, and scope.
3. **Implement Plugin:** Write hook logic in a class implementing `IModulePlugin`.
4. **Build & Test:** Run Gradle assemble → install APK → enable in LSPosed Manager.
5. **Iterate:** Validate hook execution via `XposedBridge.log` and Android Studio debugging.

---

## 9. Timeline & Milestones

1. **Week 1:** Scaffold root, framework module, sample feature.
2. **Week 2:** Implement dynamic descriptor merging and PluginManager.
3. **Week 3:** Build CI pipelines and release process.
4. **Week 4:** Expand with 3–5 additional feature modules (e.g., DebugAll, AdBlocker).
5. **Week 5:** Comprehensive testing across API levels and devices.
6. **Week 6:** Documentation, user guide, and initial release.

---

## 10. Risks & Mitigations

| Risk                                  | Likelihood | Impact | Mitigation                                      |
| ------------------------------------- | ---------- | ------ | ----------------------------------------------- |
| Descriptor parsing errors             | Low        | Medium | Schema validation and unit tests for YAML.      |
| API version drift                     | Medium     | High   | Centralize Xposed API version; periodic audits. |
| Slow CI build times with many modules | Medium     | Medium | Parallelize builds; cache dependencies.         |
| Runtime conflicts between modules     | Low        | High   | Per-module scope enforcement; isolation logs.   |

---

## 11. Success Metrics

* **Feature Throughput:** Time to onboard a new module < 30 minutes.
* **Build Stability:** Build success rate ≥ 98%.
* **Runtime Reliability:** < 1% hook failures in rolling deployments.
* **Adoption:** > 50% of internal projects migrate to the unified framework within 3 months.

---

## 12. Glossary

* **LSPosed:** Zygisk-based Xposed implementation.
* **Zygisk:** Magisk’s in-memory code injection into the Zygote process.
* **IModulePlugin:** Interface defining `initZygote` and `handleLoadPackage` hooks.
* **descriptor.yaml:** Declarative metadata for each feature module.
* **PluginManager:** Core class that orchestrates plugin lifecycle.

---
