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

public class JextractPlugin implements Plugin<Project> {
    public static final String TASK_GROUP = "jextract";
    public static final Path RELATIVE_TOOL_CACHE = Path.of("caches", "jextract-tool");

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

        // Process Container
        extension.getLibraries().all(library -> {
            final String taskName = "generate" + this.capitalize(library.getName()) + "Bindings";

            final TaskProvider<JextractTask> task = project.getTasks()
                    .register(taskName, JextractTask.class, taskInnit -> {
                        taskInnit.setGroup(JextractPlugin.TASK_GROUP);
                        taskInnit.setDescription("Generates bindings for " + library.getName());

                        // Link inputs
                        taskInnit.getHeaderFile().set(library.getHeaderFile());
                        taskInnit.getTargetPackage().set(library.getTargetPackage());
                        taskInnit.getHeaderClassName().set(library.getHeaderClassName());
                        taskInnit.getLibraryName().set(library.getLibraryName());

                        // Wire nested configuration properties individually
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

                        // Link Service
                        taskInnit.getToolService().set(serviceProvider);
                        // Ensure service is ready before task runs (implicit dependency)
                        taskInnit.usesService(serviceProvider);

                        // Output
                        final Provider<Directory> outputDir = project.getLayout()
                                .getBuildDirectory()
                                .dir("generated/sources/jextract/" + library.getName());
                        taskInnit.getOutputDirectory().set(outputDir);
                    });

            // Register with Java SourceSets
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
