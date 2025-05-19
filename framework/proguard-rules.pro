# Keep XPosed entry points and annotations
-keep class * implements de.robv.android.xposed.IXposedHookLoadPackage { *; }
-keep class * implements de.robv.android.xposed.IXposedHookZygoteInit { *; }
-keep class * implements de.robv.android.xposed.IXposedHookInitPackageResources { *; }

# Keep annotation classes
-keep @interface com.wobbz.framework.annotations.** { *; }
-keep class * extends java.lang.annotation.Annotation { *; }

# Keep annotated classes
-keep @com.wobbz.framework.annotations.XposedPlugin class * { *; }
-keep @com.wobbz.framework.annotations.HotReloadable class * { *; }

# Keep module interfaces and implementations
-keep class com.wobbz.framework.IModulePlugin { *; }
-keep class * implements com.wobbz.framework.IModulePlugin { *; }
-keep class com.wobbz.framework.IHotReloadable { *; }
-keep class * implements com.wobbz.framework.IHotReloadable { *; }

# Keep JSON models for settings
-keepclassmembers class com.wobbz.framework.ui.models.** { *; }

# Keep Gson
-keep class com.google.gson.** { *; } 