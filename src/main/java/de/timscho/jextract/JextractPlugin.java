package de.timscho.jextract;

import de.timscho.jextract.extension.JextractExtension;
import de.timscho.jextract.internal.download.JextractToolService;
import de.timscho.jextract.task.JextractTask;
import java.io.File;
import java.nio.file.Path;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.Contract;

/**
 * Registers the {@code jextract} extension and turns every library declared in it into a task.
 *
 * <p>The generated sources reach the {@code main} source set only if the {@code java} plugin is
 * applied, and the two may be applied in either order.
 */
public final class JextractPlugin implements Plugin<Project> {
    /** Name of the extension a build script configures, and of the group its tasks appear under. */
    public static final String TASK_GROUP = "jextract";

    /** Where extracted tool versions live, resolved against the Gradle user home. */
    public static final Path RELATIVE_TOOL_CACHE = Path.of("caches", "jextract-tool");

    /** Constructs the plugin, which Gradle does once per project applying the id. */
    public JextractPlugin() {}

    @Override
    public void apply(final Project project) {
        final JextractExtension extension = project.getExtensions()
                .create(JextractPlugin.TASK_GROUP, JextractExtension.class, project.getObjects());
        extension.getToolVersion().convention(JextractToolService.DEFAULT_VERSION);

        final Provider<JextractToolService> serviceProvider = project.getGradle()
                .getSharedServices()
                .registerIfAbsent("jextractTool", JextractToolService.class, spec -> {
                    spec.getParameters().getVersion().set(extension.getToolVersion());

                    final File cacheDir = project.getGradle()
                            .getGradleUserHomeDir()
                            .toPath()
                            .resolve(JextractPlugin.RELATIVE_TOOL_CACHE)
                            .toFile();
                    spec.getParameters().getCacheDir().set(cacheDir);
                });

        // all(), not configureEach(): the task has to be registered as soon as the declaration
        // exists, because the source set below reads its output directory during configuration.
        extension.getLibraries().all(library -> {
            final String taskName = "generate" + this.capitalize(library.getName()) + "Bindings";

            final TaskProvider<JextractTask> task = project.getTasks()
                    .register(taskName, JextractTask.class, taskInnit -> {
                        taskInnit.setGroup(JextractPlugin.TASK_GROUP);
                        taskInnit.setDescription("Generates bindings for " + library.getName());

                        taskInnit.getHeaderFile().set(library.getHeaderFile());
                        taskInnit.getTargetPackage().set(library.getTargetPackage());
                        taskInnit.getHeaderClassName().set(library.getHeaderClassName());
                        taskInnit.getLibraryName().set(library.getLibraryName());

                        // Property by property: the nested block is created by the object factory
                        // on both sides, so the two instances cannot be assigned to one another.
                        taskInnit
                                .getNativeLibraryLoading()
                                .getResourcePath()
                                .set(library.getNativeLibraryLoading().getResourcePath());
                        taskInnit
                                .getNativeLibraryLoading()
                                .getExtractionDir()
                                .set(library.getNativeLibraryLoading().getExtractionDir());
                        taskInnit
                                .getNativeLibraryLoading()
                                .getEnableCaching()
                                .set(library.getNativeLibraryLoading().getEnableCaching());

                        taskInnit.getCompilerArgs().set(library.getCompilerArgs());

                        taskInnit.getToolService().set(serviceProvider);
                        // Declares the dependency Gradle needs to hold the service open for the
                        // whole of the task's execution, which setting the property alone does not.
                        taskInnit.usesService(serviceProvider);

                        final Provider<Directory> outputDir = project.getLayout()
                                .getBuildDirectory()
                                .dir("generated/sources/jextract/" + library.getName());
                        taskInnit.getOutputDirectory().set(outputDir);
                    });

            // withType, so the wiring happens whichever of the two plugins is applied second.
            project.getPlugins().withType(org.gradle.api.plugins.JavaPlugin.class, _ -> {
                final SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
                final SourceSet main = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
                main.getJava().srcDir(task.flatMap(JextractTask::getOutputDirectory));
            });
        });
    }

    @Contract(pure = true)
    private String capitalize(final String name) {
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }
}
