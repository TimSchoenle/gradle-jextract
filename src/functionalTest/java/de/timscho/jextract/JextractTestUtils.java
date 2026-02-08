package de.timscho.jextract;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import org.jspecify.annotations.Nullable;

public class JextractTestUtils {
    public static void writeSettingsScript(final Path settingsFile, final String projectName) throws IOException {
        Files.writeString(settingsFile, "rootProject.name = '" + projectName + "'");
    }

    /**
     * Writes a build.gradle script with the given configuration.
     *
     * @param buildFile The file to write to.
     * @param toolVersion The jextract tool version.
     * @param libraries The library definitions to include.
     * @throws IOException If writing fails.
     */
    public static void writeBuildScript(
            final Path buildFile, @Nullable final String toolVersion, @Nullable final LibraryDefinition... libraries)
            throws IOException {
        final StringBuilder libsBlock = new StringBuilder(1024);
        if (libraries != null) {
            for (final LibraryDefinition lib : libraries) {
                libsBlock.append("        ").append(lib.name).append(" {\n");
                libsBlock
                        .append("            headerFile = file('")
                        .append(lib.headerFile)
                        .append("')\n");
                libsBlock
                        .append("            targetPackage = '")
                        .append(lib.targetPackage)
                        .append("'\n");
                if (lib.headerClassName != null) {
                    libsBlock
                            .append("            headerClassName = '")
                            .append(lib.headerClassName)
                            .append("'\n");
                }
                if (lib.libraryName != null) {
                    libsBlock
                            .append("            libraryName = '")
                            .append(lib.libraryName)
                            .append("'\n");
                }
                if (lib.libraryPath != null) {
                    libsBlock
                            .append("            libraryPath = file('")
                            .append(lib.libraryPath)
                            .append("')\n");
                }
                if (lib.compilerArgs != null && !lib.compilerArgs.isEmpty()) {
                    libsBlock.append("            compilerArgs = [");
                    for (final String arg : lib.compilerArgs) {
                        libsBlock.append("'").append(arg).append("',");
                    }
                    libsBlock.append("]\n");
                }
                if (lib.nativeLibraryResourcePath != null
                        || lib.nativeLibraryExtractionDir != null
                        || lib.nativeLibraryEnableCaching != null) {
                    libsBlock.append("            nativeLibraryLoading {\n");
                    if (lib.nativeLibraryResourcePath != null) {
                        libsBlock
                                .append("                resourcePath = '")
                                .append(lib.nativeLibraryResourcePath)
                                .append("'\n");
                    }
                    if (lib.nativeLibraryExtractionDir != null) {
                        libsBlock
                                .append("                extractionDir = file('")
                                .append(lib.nativeLibraryExtractionDir)
                                .append("')\n");
                    }
                    if (lib.nativeLibraryEnableCaching != null) {
                        libsBlock
                                .append("                enableCaching = ")
                                .append(lib.nativeLibraryEnableCaching)
                                .append("\n");
                    }
                    libsBlock.append("            }\n");
                }
                libsBlock.append("        }\n");
            }
        }

        String versionConfig = "";
        if (toolVersion != null) {
            versionConfig = "    toolVersion = '" + toolVersion + "'\n";
        }

        final String buildScript = "plugins {\n" + "    id 'de.timscho.jextract'\n"
                + "    id 'java'\n"
                + "}\n"
                + "\n"
                + "jextract {\n"
                + versionConfig
                + "    libraries {\n"
                + libsBlock
                + "    }\n"
                + "}";
        Files.writeString(buildFile, buildScript);
    }

    @Data
    @Builder
    public static class LibraryDefinition {
        String name;

        @Nullable String headerFile;

        @Nullable String targetPackage;

        @Nullable String headerClassName;

        @Nullable String libraryName;

        @Nullable String libraryPath;

        @Singular
        @Nullable List<String> compilerArgs;

        // Native library loading configuration
        @Nullable String nativeLibraryResourcePath;

        @Nullable String nativeLibraryExtractionDir;

        @Nullable Boolean nativeLibraryEnableCaching;
    }
}
