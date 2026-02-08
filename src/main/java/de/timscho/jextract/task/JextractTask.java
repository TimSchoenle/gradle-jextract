package de.timscho.jextract.task;

import de.timscho.jextract.extension.NativeLibraryLoadingConfig;
import de.timscho.jextract.internal.download.JextractToolService;
import de.timscho.jextract.internal.generation.NativeLibraryLoaderGenerator;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.services.ServiceReference;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;
import org.jetbrains.annotations.Contract;

@CacheableTask
public abstract class JextractTask extends DefaultTask {
    /**
     * Header file to be processed by jextract.
     * Passed to jextract as: -I headerFile
     */
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getHeaderFile();

    /**
     * Target package for generated Java classes.
     * Passed to jextract as: -t targetPackage
     */
    @Input
    public abstract Property<String> getTargetPackage();

    /**
     * Additional compiler arguments to be passed to jextract.
     * Passed to jextract as: -J compilerArgs
     */
    @Input
    public abstract ListProperty<String> getCompilerArgs();

    /**
     * Custom name for the main header class.
     */
    @Input
    @org.gradle.api.tasks.Optional
    public abstract Property<String> getHeaderClassName();

    /**
     * Library name for system-installed libraries.
     * Passed to jextract as: -l libraryName
     */
    @Input
    @org.gradle.api.tasks.Optional
    public abstract Property<String> getLibraryName();

    /**
     * Configuration for loading native libraries from JAR resources.
     * When configured, generates runtime library loader code instead of passing to jextract.
     */
    @Nested
    @org.gradle.api.tasks.Optional
    public abstract NativeLibraryLoadingConfig getNativeLibraryLoading();

    /**
     * Output directory for generated Java classes.
     * Passed to jextract as: -d outputDirectory
     */
    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    /**
     * Connects this task to the shared build service.
     */
    @ServiceReference("jextractTool")
    public abstract Property<JextractToolService> getToolService();

    @Inject
    protected abstract ExecOperations getExecOps();

    /**
     * Executes the jextract tool and generates the bindings.
     *
     * @throws Exception If the execution fails.
     */
    @TaskAction
    public void run() throws Exception {
        // Get the tool executable from the service
        // This blocks if the service is currently downloading in another thread
        final String executablePath =
                this.getToolService().get().getExecutable(this.getLogger()).getAbsolutePath();

        final List<String> args = this.buildArgs(executablePath);
        this.getLogger().info("Running jextract with args: {}", args);

        this.getExecOps().exec(spec -> {
            spec.commandLine(args);
            spec.setWorkingDir(this.getProject().getProjectDir()); // Good practice
        });

        // Generate native library loader if configured
        if (this.getNativeLibraryLoading().getResourcePath().isPresent()) {
            this.generateNativeLibraryLoader();
        }
    }

    @Contract(pure = true)
    private String getFinalHeaderClassName() {
        return this.getHeaderClassName().isPresent()
                ? this.getHeaderClassName().get()
                : this.getHeaderFile().get().getAsFile().getName().replace(".h", "_h");
    }

    @Contract(pure = true)
    private List<String> buildArgs(final String executable) {
        final List<String> args = new ArrayList<>();
        args.add(executable);
        args.add("--output");
        args.add(this.getOutputDirectory().get().getAsFile().getAbsolutePath());
        args.add("--target-package");
        args.add(this.getTargetPackage().get());
        args.add("--header-class-name");
        args.add(this.getFinalHeaderClassName());
        args.addAll(this.getCompilerArgs().get());

        // Add library loading arguments
        this.addLibraryArgs(args);

        args.add(this.getHeaderFile().get().getAsFile().getAbsolutePath());
        return args;
    }

    private void addLibraryArgs(final List<String> args) {
        // Validate mutual exclusivity
        int configuredCount = 0;
        if (this.getLibraryName().isPresent()) {
            configuredCount++;
        }
        if (this.getNativeLibraryLoading().getResourcePath().isPresent()) {
            configuredCount++;
        }

        if (configuredCount > 1) {
            throw new GradleException("Only one library loading option can be configured: "
                    + "libraryName, libraryPath, or nativeLibraryLoading.resourcePath");
        }

        // Add library name argument
        if (this.getLibraryName().isPresent()) {
            args.add("-l");
            args.add(this.getLibraryName().get());
        }
    }

    private void generateNativeLibraryLoader() throws Exception {
        final String headerClass = this.getFinalHeaderClassName();

        final NativeLibraryLoaderGenerator generator = NativeLibraryLoaderGenerator.builder()
                .targetPackage(this.getTargetPackage().get())
                .headerClassName(headerClass)
                .config(this.getNativeLibraryLoading())
                .outputDirectory(this.getOutputDirectory().get().getAsFile().toPath())
                .logger(this.getLogger())
                .build();

        generator.generate();
        generator.injectLoader();
    }
}
