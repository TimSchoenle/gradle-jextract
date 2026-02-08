package de.timscho.jextract;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import groovy.json.JsonSlurper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

class JextractDynamicTest extends AbstractJextractFunctionalTest {

    @TestFactory
    Stream<DynamicTest> generateTests() throws IOException {
        final Path casesDir = Path.of("src/functionalTest/resources/cases");
        if (!Files.exists(casesDir)) {
            return Stream.empty();
        }

        return Files.walk(casesDir).filter(p -> p.toString().endsWith(".h")).map(this::createDynamicTest);
    }

    private DynamicTest createDynamicTest(final Path headerPath) {
        final String testName = headerPath.getFileName().toString();
        return DynamicTest.dynamicTest(testName, () -> {
            this.runDynamicTest(headerPath);
        });
    }

    private void runDynamicTest(final Path headerPath) throws IOException {
        // Arrange
        final Path tempDir = Files.createTempDirectory("jextract-dyn-" + headerPath.getFileName());
        this.testProjectDir = tempDir;
        this.projectDir = tempDir.toFile();
        this.buildFile = new File(this.projectDir, "build.gradle");
        this.settingsFile = new File(this.projectDir, "settings.gradle");
        Files.writeString(this.settingsFile.toPath(), "rootProject.name = 'jextract-dyn'");

        final File configFile = new File(headerPath.toString().replace(".h", ".json"));
        final Map config = (Map) new JsonSlurper().parse(configFile);

        final Path sourceDir = tempDir.resolve("src/main/c");
        Files.createDirectories(sourceDir);
        Files.copy(headerPath, sourceDir.resolve(headerPath.getFileName()));

        final Path mockJava = Path.of(headerPath.toString().replace(".h", ".java"));
        String mockContent = "// Generated";
        if (Files.exists(mockJava)) {
            mockContent = Files.readString(mockJava);
        }

        final String version = "22-ea+5";
        final Path stableUserHome = tempDir.resolve("gradle-home");
        Files.createDirectories(stableUserHome);
        final String targetPkg = (String) config.get("targetPackage");

        this.mockSmartJextractTool(stableUserHome, version, targetPkg, mockContent, true);

        this.writeDynamicBuildScript(version, headerPath.getFileName().toString(), config);

        // Act
        final BuildResult result = this.createRunner(stableUserHome).build();

        // Assert
        assertThat(result.task(":generateDynamicBindings").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        this.verifyOutput(tempDir, config);
    }

    private void writeDynamicBuildScript(final String version, final String headerName, final Map config) throws IOException {
        final String pkg = (String) config.get("targetPackage");
        final List<String> args = (List<String>) config.get("compilerArgs");

        final JextractTestUtils.LibraryDefinition.LibraryDefinitionBuilder builder =
                JextractTestUtils.LibraryDefinition.builder()
                        .name("dynamic")
                        .headerFile("src/main/c/" + headerName)
                        .targetPackage(pkg);

        if (args != null) {
            builder.compilerArgs(args);
        }

        this.writeBuildScript(version, builder.build());
    }

    private void verifyOutput(final Path projectDir, final Map config) throws IOException {
        final List<String> expectedMethods = (List<String>) config.get("expectedMethods");
        final String pkg = (String) config.get("targetPackage");
        final String pkgPath = pkg.replace('.', '/');

        final Path generatedFile =
                projectDir.resolve("build/generated/sources/jextract/dynamic/" + pkgPath + "/JextractResult.java");
        assertThat(generatedFile)
                .as("Generated file should exist: " + generatedFile)
                .exists();

        if (expectedMethods != null && !expectedMethods.isEmpty()) {
            final CompilationUnit cu = StaticJavaParser.parse(generatedFile);
            final List<String> foundMethods = cu.findAll(MethodDeclaration.class).stream()
                    .map(MethodDeclaration::getNameAsString)
                    .toList();
            assertThat(foundMethods)
                    .as("Expected methods should exist in generated code")
                    .containsAll(expectedMethods);
        }

        final List<String> expectedClasses = (List<String>) config.get("expectedClasses");
        if (expectedClasses != null && !expectedClasses.isEmpty()) {
            final CompilationUnit cu = StaticJavaParser.parse(generatedFile);
            final List<String> foundClasses = Stream.concat(
                            cu.findAll(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class).stream()
                                    .map(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration::getNameAsString),
                            cu.findAll(com.github.javaparser.ast.body.RecordDeclaration.class).stream()
                                    .map(com.github.javaparser.ast.body.RecordDeclaration::getNameAsString))
                    .toList();
            assertThat(foundClasses)
                    .as("Expected classes should exist in generated code")
                    .containsAll(expectedClasses);
        }

        final List<String> expectedFields = (List<String>) config.get("expectedFields");
        if (expectedFields != null && !expectedFields.isEmpty()) {
            final CompilationUnit cu = StaticJavaParser.parse(generatedFile);
            final List<String> foundFields = cu.findAll(com.github.javaparser.ast.body.FieldDeclaration.class).stream()
                    .flatMap(f -> f.getVariables().stream())
                    .map(com.github.javaparser.ast.body.VariableDeclarator::getNameAsString)
                    .toList();
            assertThat(foundFields)
                    .as("Expected fields should exist in generated code")
                    .containsAll(expectedFields);
        }
    }
}
