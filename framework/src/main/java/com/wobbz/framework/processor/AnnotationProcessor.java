package com.wobbz.framework.processor;

import com.wobbz.framework.annotations.HotReloadable;
import com.wobbz.framework.annotations.XposedPlugin;
import com.wobbz.framework.IHotReloadable;
import com.wobbz.framework.IModulePlugin;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

/**
 * Annotation processor for the Wobbz LSPosed Modular Framework.
 * Processes {@link XposedPlugin} and {@link HotReloadable} annotations to generate
 * necessary configuration files for LSPosed modules and hot-reloading functionality.
 *
 * <p>This processor generates:</p>
 * <ul>
 *   <li>The traditional {@code assets/xposed_init} file for LSPosed to load the module class</li>
 *   <li>A {@code module.prop} file containing module metadata</li>
 *   <li>A record of hot-reloadable modules for development tools</li>
 * </ul>
 */
@SupportedAnnotationTypes({
    "com.wobbz.framework.annotations.XposedPlugin",
    "com.wobbz.framework.annotations.HotReloadable"
})
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class AnnotationProcessor extends AbstractProcessor {

    private List<String> xposedModuleClasses = new ArrayList<>();
    private List<String> hotReloadableClasses = new ArrayList<>();

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        boolean processed = false;

        // Process @XposedPlugin annotation
        for (Element element : roundEnv.getElementsAnnotatedWith(XposedPlugin.class)) {
            if (element.getKind() != ElementKind.CLASS) {
                processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "@XposedPlugin can only be applied to classes",
                    element
                );
                continue;
            }

            TypeElement typeElement = (TypeElement) element;
            if (!implementsInterface(typeElement, IModulePlugin.class.getName())) {
                processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "@XposedPlugin annotated classes must implement IModulePlugin",
                    element
                );
                continue;
            }

            processed = true;
            String className = typeElement.getQualifiedName().toString();
            xposedModuleClasses.add(className);
            
            XposedPlugin annotation = element.getAnnotation(XposedPlugin.class);
            generateModuleProp(annotation, className);
        }

        // Process @HotReloadable annotation
        for (Element element : roundEnv.getElementsAnnotatedWith(HotReloadable.class)) {
            if (element.getKind() != ElementKind.CLASS) {
                processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "@HotReloadable can only be applied to classes",
                    element
                );
                continue;
            }

            TypeElement typeElement = (TypeElement) element;
            if (!implementsInterface(typeElement, IHotReloadable.class.getName())) {
                processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "@HotReloadable annotated classes must implement IHotReloadable",
                    element
                );
                continue;
            }

            processed = true;
            String className = typeElement.getQualifiedName().toString();
            hotReloadableClasses.add(className);
            
            // Save port information for development tools
            HotReloadable annotation = element.getAnnotation(HotReloadable.class);
            int port = annotation.port();
            saveHotReloadPort(className, port);
        }

        // Generate the xposed_init file in the last round
        if (roundEnv.processingOver() && !xposedModuleClasses.isEmpty()) {
            generateXposedInit();
            generateHotReloadableModulesList();
        }

        return processed;
    }

    /**
     * Checks if a type element implements a given interface.
     *
     * @param typeElement The type element to check.
     * @param interfaceName The fully qualified name of the interface.
     * @return True if the type element implements the interface, false otherwise.
     */
    private boolean implementsInterface(TypeElement typeElement, String interfaceName) {
        for (TypeMirror interfaceType : typeElement.getInterfaces()) {
            if (interfaceType.toString().equals(interfaceName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Generates the xposed_init file with the list of module entry points.
     */
    private void generateXposedInit() {
        try {
            FileObject resource = processingEnv.getFiler().createResource(
                StandardLocation.CLASS_OUTPUT,
                "",
                "assets/xposed_init"
            );
            
            try (PrintWriter writer = new PrintWriter(resource.openWriter())) {
                for (String moduleClass : xposedModuleClasses) {
                    writer.println(moduleClass);
                }
            }
            
            processingEnv.getMessager().printMessage(
                Diagnostic.Kind.NOTE,
                "Generated xposed_init with " + xposedModuleClasses.size() + " module classes"
            );
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(
                Diagnostic.Kind.ERROR,
                "Failed to generate xposed_init: " + e.getMessage()
            );
        }
    }

    /**
     * Generates a module.prop file for the LSPosed module with metadata.
     *
     * @param annotation The XposedPlugin annotation with module metadata.
     * @param className The fully qualified class name of the module.
     */
    private void generateModuleProp(XposedPlugin annotation, String className) {
        try {
            // Using the module ID or class name as a unique identifier for the file
            String moduleId = annotation.id().isEmpty() ? className.replace(".", "_") : annotation.id();
            FileObject resource = processingEnv.getFiler().createResource(
                StandardLocation.CLASS_OUTPUT,
                "",
                "META-INF/module_" + moduleId + ".prop"
            );
            
            try (PrintWriter writer = new PrintWriter(resource.openWriter())) {
                writer.println("id=" + moduleId);
                writer.println("name=" + annotation.name());
                writer.println("version=" + annotation.version());
                writer.println("versionCode=" + annotation.versionCode());
                writer.println("author=" + annotation.author());
                writer.println("description=" + annotation.description());
                
                // Write scope if available
                String[] scope = annotation.scope();
                if (scope.length > 0) {
                    StringBuilder scopeBuilder = new StringBuilder();
                    for (int i = 0; i < scope.length; i++) {
                        if (i > 0) scopeBuilder.append(", ");
                        scopeBuilder.append(scope[i]);
                    }
                    writer.println("scope=" + scopeBuilder);
                }
            }
            
            processingEnv.getMessager().printMessage(
                Diagnostic.Kind.NOTE,
                "Generated module.prop for " + className + " with ID " + moduleId
            );
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(
                Diagnostic.Kind.ERROR,
                "Failed to generate module.prop for " + className + ": " + e.getMessage()
            );
        }
    }

    /**
     * Saves the hot-reload port for a module class for use by development tools.
     *
     * @param className The fully qualified class name of the hot-reloadable module.
     * @param port The port specified in the HotReloadable annotation.
     */
    private void saveHotReloadPort(String className, int port) {
        try {
            FileObject resource = processingEnv.getFiler().createResource(
                StandardLocation.CLASS_OUTPUT,
                "",
                "META-INF/hotreload/" + className + ".port"
            );
            
            try (PrintWriter writer = new PrintWriter(resource.openWriter())) {
                writer.println(port);
            }
            
            processingEnv.getMessager().printMessage(
                Diagnostic.Kind.NOTE,
                "Saved hot-reload port " + port + " for " + className
            );
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(
                Diagnostic.Kind.ERROR,
                "Failed to save hot-reload port for " + className + ": " + e.getMessage()
            );
        }
    }

    /**
     * Generates a list of hot-reloadable module classes for development tools.
     */
    private void generateHotReloadableModulesList() {
        if (hotReloadableClasses.isEmpty()) {
            return;
        }
        
        try {
            FileObject resource = processingEnv.getFiler().createResource(
                StandardLocation.CLASS_OUTPUT,
                "",
                "META-INF/hotreload/modules.list"
            );
            
            try (PrintWriter writer = new PrintWriter(resource.openWriter())) {
                for (String moduleClass : hotReloadableClasses) {
                    writer.println(moduleClass);
                }
            }
            
            processingEnv.getMessager().printMessage(
                Diagnostic.Kind.NOTE,
                "Generated hot-reloadable modules list with " + hotReloadableClasses.size() + " modules"
            );
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(
                Diagnostic.Kind.ERROR,
                "Failed to generate hot-reloadable modules list: " + e.getMessage()
            );
        }
    }
} 