package com.wobbz.framework.processor;

import com.google.auto.service.AutoService;
import com.wobbz.framework.annotations.XposedPlugin;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

/**
 * Processes @XposedPlugin annotations to generate:
 * - XposedEntryPoint class with registered modules
 * - META-INF/xposed/java_init.list
 * - META-INF/xposed/scope.list
 * - module.prop
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes("com.wobbz.framework.annotations.XposedPlugin")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class XposedPluginProcessor extends AbstractProcessor {
    private static final String ENTRY_POINT_PACKAGE = "com.wobbz.framework.generated";
    private static final String ENTRY_POINT_CLASS = "XposedEntryPoint";
    private static final String MODULE_PROP_PATH = "META-INF/xposed/module.prop";
    private static final String JAVA_INIT_LIST_PATH = "META-INF/xposed/java_init.list";
    private static final String SCOPE_LIST_PATH = "META-INF/xposed/scope.list";
    
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }
        
        List<TypeElement> moduleElements = new ArrayList<>();
        List<String> scopeEntries = new ArrayList<>();
        
        // Find all @XposedPlugin annotated classes
        for (Element element : roundEnv.getElementsAnnotatedWith(XposedPlugin.class)) {
            if (element instanceof TypeElement) {
                TypeElement typeElement = (TypeElement) element;
                moduleElements.add(typeElement);
                
                // Extract scope entries
                XposedPlugin annotation = typeElement.getAnnotation(XposedPlugin.class);
                for (String scope : annotation.scope()) {
                    if (!scopeEntries.contains(scope)) {
                        scopeEntries.add(scope);
                    }
                }
                
                // Log info
                processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.NOTE,
                    "Found module: " + typeElement.getQualifiedName()
                );
            }
        }
        
        if (!moduleElements.isEmpty()) {
            try {
                // Generate module.prop
                generateModuleProp(moduleElements);
                
                // Generate scope.list
                generateScopeList(scopeEntries);
                
                // Generate java_init.list
                generateJavaInitList();
                
                // Generate XposedEntryPoint with registered modules
                // For simplicity, we just generate a stub here
                generateXposedEntryPoint(moduleElements);
                
            } catch (IOException e) {
                processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "Error generating files: " + e.getMessage()
                );
            }
        }
        
        return true;
    }
    
    private void generateModuleProp(List<TypeElement> moduleElements) throws IOException {
        // In real implementation, merge information from all modules
        // For now, just write a placeholder
        FileObject resource = processingEnv.getFiler()
            .createResource(StandardLocation.CLASS_OUTPUT, "", MODULE_PROP_PATH);
        
        try (Writer writer = resource.openWriter()) {
            writer.write("id=com.wobbz.lsposedframework\n");
            writer.write("name=LSPosed Modular Framework\n");
            writer.write("version=1.0.0\n");
            writer.write("versionCode=1\n");
            writer.write("author=Wobbz\n");
            writer.write("description=A modern, annotation-driven framework for LSPosed modules\n");
        }
    }
    
    private void generateScopeList(List<String> scopeEntries) throws IOException {
        FileObject resource = processingEnv.getFiler()
            .createResource(StandardLocation.CLASS_OUTPUT, "", SCOPE_LIST_PATH);
        
        try (Writer writer = resource.openWriter()) {
            for (String scope : scopeEntries) {
                writer.write(scope + "\n");
            }
        }
    }
    
    private void generateJavaInitList() throws IOException {
        FileObject resource = processingEnv.getFiler()
            .createResource(StandardLocation.CLASS_OUTPUT, "", JAVA_INIT_LIST_PATH);
        
        try (Writer writer = resource.openWriter()) {
            writer.write(ENTRY_POINT_PACKAGE + "." + ENTRY_POINT_CLASS + "\n");
        }
    }
    
    private void generateXposedEntryPoint(List<TypeElement> moduleElements) throws IOException {
        // In a real implementation, this would generate Java code
        // For simplicity, we just log what we would generate
        StringBuilder builder = new StringBuilder();
        builder.append("Would generate XposedEntryPoint.java with:\n");
        
        for (TypeElement module : moduleElements) {
            builder.append("  - ").append(module.getQualifiedName()).append("\n");
        }
        
        processingEnv.getMessager().printMessage(
            Diagnostic.Kind.NOTE,
            builder.toString()
        );
    }
} 