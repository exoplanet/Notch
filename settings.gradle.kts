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

// Android Gradle Plugin 9.4.1 requests Bouncy Castle 1.80.2 on the plugin
// classpath (builder, apkzlib, sdk-common). That request is not strict.
// 1.86 fixes CVE-2026-71891, which still affects 1.85. Gradle 9.6.1 also
// embeds bcprov 1.84 in its own lib/plugins; this force does not replace that jar.
// This classpath builds and signs the APK; it is separate from the app's Guava constraint.
val patchedPluginClasspath = mapOf(
    "org.bouncycastle:bcprov-jdk18on" to "1.86",
    "org.bouncycastle:bcpkix-jdk18on" to "1.86",
    "org.bouncycastle:bcutil-jdk18on" to "1.86",
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
