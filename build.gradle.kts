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
}

// x-release-please-start-version
version = "0.2.2"
// x-release-please-end

group = "de.timscho"
val gradlePluginId =  "de.timscho.jextract"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.jspecify)
    implementation(libs.jetbrains.annotations)
    implementation(libs.javaparser)
    testImplementation(libs.assertj)
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
    coordinates(group.toString(), "gradle-jextract", version.toString())

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


tasks.named("check") {
    dependsOn(testing.suites.named("functionalTest"))
}

val generateReadme by tasks.registering {
    description = "Generates the README.md from the template with current version and snippets"
    group = "documentation"

    val template = layout.projectDirectory.file("README.tpl.md")

    doLast {
        copy {
            from(template)
            into(layout.projectDirectory)
            rename { "README.md" }
            expand(
                "version" to project.version,
                "group" to project.group,
                "gradlePluginId" to gradlePluginId
            )
        }
    }
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
