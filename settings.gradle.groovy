pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id 'org.gradle.toolchains.foojay-resolver-convention' version '1.0.0'
}

// Workaround for rare Gradle cache corruption where a dependencies-accessors workspace
// directory exists but its metadata.bin is missing, causing:
//   java.io.UncheckedIOException: Could not read workspace metadata .../metadata.bin
// We proactively delete the whole dependencies-accessors cache in that case so Gradle
// can regenerate it.
def depsAccessorsDir = new File(gradle.gradleUserHomeDir, "caches/${gradle.gradleVersion}/dependencies-accessors")
if (depsAccessorsDir.isDirectory()) {
    def hasCorruptedWorkspace = (depsAccessorsDir.listFiles() ?: [] as File[]).any { File f ->
        if (!f.isDirectory()) return false
        def meta = new File(f, 'metadata.bin')
        return !meta.isFile() || meta.length() == 0L
    }
    if (hasCorruptedWorkspace) {
        println("[settings] Deleting corrupted Gradle cache: ${depsAccessorsDir}")
        ant.delete(dir: depsAccessorsDir, failonerror: false)
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}

rootProject.name = "HADERA"
include ':app'

