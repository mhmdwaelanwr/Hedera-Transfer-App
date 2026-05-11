import java.io.File

pluginManagement {
	repositories {
		google()
		mavenCentral()
		gradlePluginPortal()
	}
}

plugins {
	id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

/**
 * Workaround for rare Gradle cache corruption where an immutable workspace directory exists
 * but its `metadata.bin` is missing (or zero-length), causing hard failures like:
 *
 *   Could not read workspace metadata from ~/.gradle/caches/<gradleVersion>/<cacheName>/<hash>/metadata.bin
 */
fun deleteIfCorrupted(cacheName: String) {
	val dir = File(gradle.gradleUserHomeDir, "caches/${gradle.gradleVersion}/$cacheName")
	if (!dir.isDirectory) return

	val corrupted = dir.listFiles()?.any { f ->
		if (!f.isDirectory) return@any false
		val meta = File(f, "metadata.bin")
		!meta.isFile || meta.length() == 0L
	} == true

	if (corrupted) {
		println("[settings] Deleting corrupted Gradle cache: $dir")
		try {
			dir.deleteRecursively()
		} catch (_: Throwable) {
			// Best-effort cleanup; build may still proceed if Gradle can recreate the cache.
		}
	}
}

// These are the two caches seen failing in this project.
deleteIfCorrupted("groovy-dsl")
deleteIfCorrupted("dependencies-accessors")

dependencyResolutionManagement {
	repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
	repositories {
		google()
		mavenCentral()
		maven(url = "https://jitpack.io")
	}
}

rootProject.name = "HADERA"
include(":app")
