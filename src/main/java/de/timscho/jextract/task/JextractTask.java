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

/**
 * Runs jextract over one header, and generates the native library loader when one was asked for.
 *
 * <p>The tool version is not among the inputs: it arrives through the shared service, and a build
 * service is not a task input, so raising it does not by itself make a task that already ran out
 * of date.
 */
@CacheableTask
public abstract class JextractTask extends DefaultTask {
    /** Constructs the task, which Gradle does once per library declaration. */
    public JextractTask() {}

    /**
     * {@return the header file jextract parses}
     *
     * <p>Tracked by content, so moving the file leaves the task up to date.
     */
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getHeaderFile();

    /** {@return the package the generated classes are written into, passed as {@code --target-package}} */
    @Input
    public abstract Property<String> getTargetPackage();

    /** {@return arguments inserted verbatim between the generated arguments and the header path} */
    @Input
    public abstract ListProperty<String> getCompilerArgs();

    /**
     * {@return the class jextract puts the top-level declarations on, passed as
     * {@code --header-class-name}}
     *
     * <p>Unset, the header file name with {@code .h} replaced by {@code _h} is sent instead, so
     * jextract always receives the argument.
     */
    @Input
    @org.gradle.api.tasks.Optional
    public abstract Property<String> getHeaderClassName();

    /**
     * {@return the system library to bind against, passed as {@code -l}}
     *
     * <p>Set together with a resource path under {@link #getNativeLibraryLoading()}, the task fails
     * before jextract is invoked instead of choosing between them.
     */
    @Input
    @org.gradle.api.tasks.Optional
    public abstract Property<String> getLibraryName();

    /**
     * {@return the settings for extracting the library out of a JAR resource}
     *
     * <p>None of it reaches jextract. A resource path here makes the task write a loader class and
     * edit jextract's output once jextract has finished.
     */
    @Nested
    @org.gradle.api.tasks.Optional
    public abstract NativeLibraryLoadingConfig getNativeLibraryLoading();

    /**
     * {@return the directory jextract writes into, passed as {@code --output}}
     *
     * <p>Joined to the {@code main} source set by the plugin, so what lands here is compiled
     * without being declared anywhere else.
     */
    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    /**
     * {@return the build service that supplies the jextract executable}
     *
     * <p>Bound by the name {@code jextractTool}, so it resolves whether or not the plugin was the
     * thing that registered it.
     */
    @ServiceReference("jextractTool")
    public abstract Property<JextractToolService> getToolService();

    /**
     * {@return the launcher jextract is run through}
     *
     * <p>jextract is launched with the project directory as its working directory, so a relative
     * include path in {@link #getCompilerArgs()} resolves against that.
     */
    @Inject
    protected abstract ExecOperations getExecOps();

    /**
     * Runs jextract, then generates the loader if a resource path was configured.
     *
     * @throws GradleException if jextract exits with a nonzero status, or if the declaration set
     *     both a library name and a resource path
     * @throws java.io.IOException if the generated loader or the header class it is injected into
     *     cannot be written
     */
    @TaskAction
    public void run() throws Exception {
        final String executablePath =
                this.getToolService().get().getExecutable(this.getLogger()).getAbsolutePath();

        final List<String> args = this.buildArgs(executablePath);
        this.getLogger().info("Running jextract with args: {}", args);

        this.getExecOps().exec(spec -> {
            spec.commandLine(args);
            // Relative include paths in compilerArgs resolve against this directory.
            spec.setWorkingDir(this.getProject().getProjectDir());
        });

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

        this.addLibraryArgs(args);

        args.add(this.getHeaderFile().get().getAsFile().getAbsolutePath());
        return args;
    }

    // Picking one would make the build succeed and the wrong library load, so it refuses instead.
    private void addLibraryArgs(final List<String> args) {
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
