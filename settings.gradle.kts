// OpenRewrite publishes the `org.openrewrite` group to the Code Genome Project rather than Maven
// Central as of the 8.91.0 line, so the rewrite plugin's POM imports a BOM that neither Central nor
// the Gradle Plugin Portal carries. See openrewrite/rewrite-build-gradle-plugin#220.
//
// The repository is credentialed. Put a download token from https://codegenomeproject.org/token in
// `~/.gradle/gradle.properties` as `codegenomeUsername` and `codegenomePassword`; CI passes the same
// pair as `ORG_GRADLE_PROJECT_` environment variables. Without them Gradle fails at configuration
// time naming the two missing properties.
pluginManagement {
    resolutionStrategy {
        // Only the implementation module reaches the Code Genome Project; the plugin marker that
        // normally fronts it does not. Resolve the id straight to the module so the marker stops
        // mattering once the Plugin Portal no longer carries it.
        eachPlugin {
            if (requested.id.namespace == "org.openrewrite") {
                useModule("org.openrewrite:plugin:${requested.version}")
            }
        }
    }

    repositories {
        gradlePluginPortal()
        maven {
            name = "codegenome"
            url = uri("https://artifacts.codegenomeproject.org/maven")
            credentials(PasswordCredentials::class)
            content {
                includeGroupByRegex("""org\.openrewrite(\..*)?""")
                includeGroupByRegex("""io\.moderne(\..*)?""")
            }
        }
    }
}

rootProject.name = "gradle-jextract"
