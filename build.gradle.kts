plugins {
    `java-gradle-plugin`
    `maven-publish`
    `jvm-test-suite`
    alias(libs.plugins.lombok)
    alias(libs.plugins.spotless)
    alias(libs.plugins.maven.publish)
}

group = "de.timscho"
version = "0.1.0"

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
            id = "de.timscho.jextract"
            implementationClass = "de.timscho.jextract.JextractPlugin"
            displayName = "Modern Jextract Plugin"
            description = "Downloads and runs jextract to generate Java FFM bindings"
        }
    }

    testSourceSets(sourceSets["functionalTest"])
}

mavenPublishing {
    coordinates(group.toString(), "de.timscho.jextract", version.toString())

    pom {
        inceptionYear.set("2026")
    }

    publishToMavenCentral(automaticRelease = false)

    // We only want to sign publications when running on CI
    if (providers.environmentVariable("CI").isPresent) {
        signAllPublications()
    }
}


tasks.named("check") {
    dependsOn(testing.suites.named("functionalTest"))
}
