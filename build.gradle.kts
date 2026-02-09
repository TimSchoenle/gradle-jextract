import com.vanniktech.maven.publish.DeploymentValidation
import com.vanniktech.maven.publish.GradlePlugin
import com.vanniktech.maven.publish.JavadocJar

plugins {
    `java-gradle-plugin`
    `maven-publish`
    `jvm-test-suite`
    checkstyle
    alias(libs.plugins.lombok)
    alias(libs.plugins.spotless)
    alias(libs.plugins.maven.publish)
}

// x-release-please-start-version
version = "0.1.0"
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
    coordinates(group.toString(), gradlePluginId, version.toString())

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

    // TODO: MAKE ME TRUE BEFORE MERGE
    publishToMavenCentral(automaticRelease = false, validateDeployment = DeploymentValidation.PUBLISHED)

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
