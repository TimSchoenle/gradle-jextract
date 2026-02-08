package de.timscho.jextract.internal.generation;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NativeLibraryLoaderInjectorTest {

    @TempDir
    Path tempDir;

    private Path sourceFile;

    @BeforeEach
    void setUp() throws IOException {
        // Create a dummy source file
        sourceFile = tempDir.resolve("TestHeader.java");
        String sourceCode = "package com.example;\n\n" + "public class TestHeader {\n"
                + "    static {\n"
                + "        System.loadLibrary(\"test\");\n"
                + "    }\n"
                + "}\n";
        Files.writeString(sourceFile, sourceCode);
    }

    @Test
    void injectsLoaderCallCorrectly() throws IOException {
        // Arrange
        String headerClassName = "TestHeader";
        String loaderClassName = "de.timscho.jextract.internal.NativeLibraryLoader";
        String staticMethodName = "load";

        NativeLibraryLoaderInjector injector = NativeLibraryLoaderInjector.builder()
                .target(sourceFile.toFile())
                .headerClass(headerClassName)
                .loaderClassName(loaderClassName)
                .staticLoaderMethodName(staticMethodName)
                .logger(org.gradle.api.logging.Logging.getLogger(NativeLibraryLoaderInjectorTest.class))
                .build();

        // Act
        injector.inject();

        // Assert
        String modifiedCode = Files.readString(sourceFile);

        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(modifiedCode).getResult().get();

        Optional<ClassOrInterfaceDeclaration> classDecl = cu.getClassByName("TestHeader");
        assertThat(classDecl).isPresent();

        // Verify static initializer exists and contains the loader call
        boolean hasLoaderCall = classDecl.get().getMembers().stream()
                .filter(member -> member instanceof com.github.javaparser.ast.body.InitializerDeclaration)
                .map(member -> (com.github.javaparser.ast.body.InitializerDeclaration) member)
                .filter(com.github.javaparser.ast.body.InitializerDeclaration::isStatic)
                .anyMatch(init -> init.getBody().toString().contains(loaderClassName + "." + staticMethodName));

        assertThat(hasLoaderCall)
                .as("Static initializer should contain loader call")
                .isTrue();
    }
}
