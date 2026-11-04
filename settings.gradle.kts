pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Notch"
include(":app")

// Android Gradle Plugin 9.4.1 pins these on the plugin classpath. The patched
// releases fix the open Dependabot alerts. This classpath builds and signs the
// APK; it is separate from the app's Guava Android constraint.
val patchedPluginClasspath = mapOf(
    "org.bouncycastle:bcprov-jdk18on" to "1.85",
    "org.bouncycastle:bcpkix-jdk18on" to "1.85",
    "org.bouncycastle:bcutil-jdk18on" to "1.85",
    "org.bitbucket.b_c:jose4j" to "0.9.6",
    "org.jdom:jdom2" to "2.0.6.1",
    "org.apache.commons:commons-lang3" to "3.18.0",
    "org.apache.httpcomponents:httpclient" to "4.5.14",
    "com.google.guava:guava" to "33.7.2-jre",
)

gradle.beforeProject {
    buildscript.configurations.named("classpath").configure {
        resolutionStrategy.eachDependency {
            val patched = patchedPluginClasspath["${requested.group}:${requested.name}"]
                ?: return@eachDependency
            if (requested.version != patched) {
                useVersion(patched)
                because("Dependabot advisory is fixed in $patched")
            }
        }
    }
}
