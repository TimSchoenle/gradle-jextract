import com.vanniktech.maven.publish.DeploymentValidation
import com.vanniktech.maven.publish.GradlePlugin
import com.vanniktech.maven.publish.JavadocJar
import java.net.URI
import java.util.regex.Pattern

plugins {
    `java-gradle-plugin`
    `maven-publish`
    `jvm-test-suite`
    checkstyle
    alias(libs.plugins.lombok)
    alias(libs.plugins.spotless)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.buildconfig)
    alias(libs.plugins.rewrite)
}

// `group`, `version` and `description` arrive from gradle.properties, which Gradle applies to the
// project before this script runs. That file is also the manifest the README payload is read from,
// so the two cannot disagree.
val gradlePluginId: String = providers.gradleProperty("pluginId").get()
val artifactId = providers.gradleProperty("artifactId").get()

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(providers.gradleProperty("javaVersion").get().toInt())
    }
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    rewrite(libs.rewrite.catalog)

    implementation(libs.jspecify)
    implementation(libs.jetbrains.annotations)
    implementation(libs.javaparser)

    testImplementation(libs.assertj)
    testImplementation(gradleApi())
    testImplementation(gradleTestKit())
}

rewrite {
    activeRecipe("de.timscho.rewrite.Style")
}

testing {
    suites {
        // Configure the built-in test suite (Unit Tests)
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter()
        }

        val functionalTest by registering(JvmTestSuite::class) {
            useJUnitJupiter()

            dependencies {
                implementation(project())
                implementation(gradleTestKit())
                implementation(libs.assertj)
            }


            targets {
                all {
                    testTask.configure {
                        shouldRunAfter(test)
                    }
                }
            }
        }
    }
}

spotless {
    java {
        targetExclude(layout.buildDirectory.asFileTree.matching { include("generated/**/*.java") })

        importOrder()
        removeUnusedImports()
        forbidWildcardImports()
        forbidModuleImports()
        cleanthat()
        palantirJavaFormat()
        formatAnnotations()
    }
}

gradlePlugin {
    plugins {
        create("jextract") {
            id = gradlePluginId
            implementationClass = "de.timscho.jextract.JextractPlugin"
            displayName = "Jextract Gradle Plugin"
            description = "Downloads and runs jextract to generate Java FFM bindings"
        }
    }

    testSourceSets(sourceSets["functionalTest"])
}

mavenPublishing {
    coordinates(group.toString(), artifactId, version.toString())

    configure(GradlePlugin(javadocJar = JavadocJar.Javadoc()))

    pom {
        name.set("Jextract Gradle Plugin")
        description.set("A Gradle plugin that automates the download and execution of jextract to generate Java Foreign Function & Memory (FFM) API bindings from C header files with bundled library loading support.")
        url.set("https://github.com/TimSchoenle/gradle-jextract")
        inceptionYear.set("2026")

        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("TimSchoenle")
                name.set("Tim Schönle")
                url.set("https://github.com/TimSchoenle")
            }
        }
        scm {
            url.set("https://github.com/TimSchoenle/gradle-jextract")
        }
    }

    publishToMavenCentral(automaticRelease = true, validateDeployment = DeploymentValidation.PUBLISHED)

    // We only want to sign publications when running on CI
    if (providers.environmentVariable("CI").isPresent) {
        signAllPublications()
    }
}


// The doc comment gate. Both halves are needed: javac and the javadoc tool run doclint over
// different file sets, and only -Werror turns either of them from a printout into a failure.
//
// `/protected` is the access level a consumer of this plugin can reach. Package-private and private
// members are left to review.
//
// `internal` is held out, and it is the same set the javadoc jar below leaves out, so the gate
// covers exactly what is published. Every member doclint would ask about in there is one nobody can
// answer: Lombok writes the `@Builder` and `@Value` members, the buildConfig plugin writes a class
// whose field it cannot comment, and `JextractToolService` cannot declare the constructor doclint
// wants because Gradle's build service instantiation rejects one. The classes still carry
// hand-written comments; what they do not have is the machine check.
val doclintOptOut = listOf("de.timscho.jextract.internal.*")

// compileJava only. The test source sets are read by whoever changes them and by nobody else, and
// doclint has one question, which is whether a comment exists. Asking it of a `@TempDir` field
// produces a line naming the field, which is the shape of comment this repository is trying not to
// have.
tasks.named<JavaCompile>("compileJava") {
    options.compilerArgs.addAll(
        listOf(
            "-Xdoclint:all/protected",
            "-Xdoclint/package:" + doclintOptOut.joinToString(",") { "-$it" },
            "-Werror",
        ))
}

// The javadoc jar consumers download documents the extension, the task and the plugin. Nothing
// under `internal` carries a compatibility promise, so publishing it would be publishing a contract
// this project does not intend to keep.
tasks.withType<Javadoc>().configureEach {
    exclude("de/timscho/jextract/internal/**")

    (options as StandardJavadocDocletOptions).apply {
        // No access qualifier here: the javadoc tool rejects `-Xdoclint:all/protected` and takes
        // the level from its own `-protected`, which is the default and the level javac is given.
        addStringOption("Xdoclint:all", "-quiet")
        addBooleanOption("Werror", true)
    }
}

tasks.named("check") {
    dependsOn(testing.suites.named("functionalTest"))

    // `build` does not reach the javadoc task; only publishing does, and CI runs
    // `build -x test -x functionalTest`. Without this the doclint half that resolves `{@link}`
    // targets would first run during a release, which is the one place a broken link is expensive.
    dependsOn(tasks.named("javadoc"))
}

tasks.named("spotlessApply") {
    mustRunAfter("rewriteRun")
}

val rewriteAndFormat by tasks.registering {
    group = "formatting"
    description = "Runs rewriteRun first and spotlessApply afterwards"

    dependsOn("rewriteRun", "spotlessApply")
}


val findLatestJextractVersion by tasks.registering {
    group = "help"
    description = "Checks jdk.java.net for the latest jextract early access version"

    val javaVersion = java.toolchain.languageVersion.get().asInt()
    val jextractVersionFile = layout.projectDirectory.file("gradle/jextract-version")

    doLast {
        val currentVersion = if (jextractVersionFile.asFile.exists()) {
            jextractVersionFile.asFile.readText().trim()
        } else {
            ""
        }

        val text = URI("https://jdk.java.net/jextract/").toURL().openConnection().apply {
            setRequestProperty("User-Agent", "Mozilla/5.0")
            connectTimeout = 5000
            readTimeout = 5000
        }.getInputStream().bufferedReader().use { it.readText() }

        // Regex to capture major, build, and sub-build numbers
        // Example: 25-jextract+2-4 -> major=25, build=2, sub=4
        val versionPatternWithBuild = Pattern.compile("Build ((\\d+)-jextract\\+(\\d+)(?:-(\\d+))?)")
        val versionPattern = Pattern.compile("^\\d+-jextract\\+(\\d+)(?:-(\\d+))?$")
        
        val bestVersion = versionPatternWithBuild.matcher(text).results()
            .map { match ->
                val fullVersion = match.group(1)
                val major = match.group(2).toInt()
                val build = match.group(3).toInt()
                val sub = match.group(4)?.toInt() ?: 0
                Triple(fullVersion, major, build to sub)
            }
            .filter { (_, major, _) -> major == javaVersion }
            .findFirst()
            .orElse(null)

        if (bestVersion == null) {
            logger.error("Could not find any jextract version for Java $javaVersion.")
        } else {
            val (newVersion, _, newBuild) = bestVersion
            logger.lifecycle("Latest applicable jextract version found: $newVersion")

            var shouldUpdate = currentVersion.isEmpty()
            if (!shouldUpdate) {
                val currentBuildMatcher = versionPattern.matcher(currentVersion)
                if (!currentBuildMatcher.find() && newVersion != currentVersion) {
                    shouldUpdate = true
                } else {
                    val currentMainBuild = currentBuildMatcher.group(1).toInt()
                    val currentSubBuild = currentBuildMatcher.group(2)?.toInt() ?: 0

                    val (newMainBuild, newSubBuild) = newBuild

                    if (newMainBuild > currentMainBuild || (newMainBuild == currentMainBuild && newSubBuild > currentSubBuild)) {
                        shouldUpdate = true
                    } else if (newMainBuild == currentMainBuild && newSubBuild == currentSubBuild) {
                        logger.lifecycle("Version is already up to date.")
                    } else {
                        logger.warn("Found version ($newVersion) seems older than current ($currentVersion). Skipping.")
                    }
                }
            }

            if (shouldUpdate) {
                jextractVersionFile.asFile.writeText(newVersion)
                logger.lifecycle("Updated version file at: ${jextractVersionFile.asFile.absolutePath} to $newVersion")
            }
        }
    }
}

buildConfig {
    className("GeneratedConstant")
    packageName("de.timscho.jextract.internal.util")

    useJavaOutput()

    val jextractVersionFile = layout.projectDirectory.file("gradle/jextract-version")
    val jextractVersion = jextractVersionFile.asFile.readText().trim()

    buildConfigField("JEXTRACT_VERSION", provider { jextractVersion })
}
