import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	java
	kotlin("jvm") version "2.3.21"
	kotlin("plugin.serialization") version "2.3.21"
	`maven-publish`
}

group = "io.github.team-sneakymouse"

version = providers.exec {
	workingDir(rootDir)
	commandLine("git", "show", "-s", "--format=%ct:%h", "--abbrev=12", "HEAD")
}.standardOutput.asText.map { commit ->
	val (timestamp, hash) = commit.trim().split(":", limit = 2)
	val date = DateTimeFormatter.ofPattern("yyyy.MM.dd").withZone(ZoneOffset.UTC)
		.format(Instant.ofEpochSecond(timestamp.toLong()))
	"$date-$hash"
}.get()

repositories {
	maven {
		url = uri("https://plugins.gradle.org/m2/")
	}
	maven {
		name = "papermc"
		url = uri("https://repo.papermc.io/repository/maven-public/")
	}
	mavenCentral()
	maven("https://maven.sneakyrp.com/releases")
}

dependencies {
	implementation("org.jetbrains.kotlin:kotlin-stdlib:2.3.21")
	implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
	compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
	compileOnly("io.github.team-sneakymouse:sneakypocketbase-api:2026.10.09-70effb404392")
}

sourceSets {
	main {
		java.srcDir("src/main/kotlin")
		resources.srcDir(file("src/resources"))
	}
}

java {
	toolchain {
		languageVersion.set(JavaLanguageVersion.of(25))
	}
	withSourcesJar()
}

kotlin {
	compilerOptions {
		jvmTarget.set(JvmTarget.JVM_25)
	}
}

tasks.processResources {
	inputs.property("version", project.version.toString())
	filesMatching("paper-plugin.yml") {
		expand("version" to project.version.toString())
	}
}

tasks.jar {
	archiveBaseName.set(rootProject.name)
	manifest {
		attributes["Main-Class"] = "com.danidipp.sneakydiscordlink.SneakyDiscordLink"
	}
	duplicatesStrategy = DuplicatesStrategy.EXCLUDE

	from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}

val verifyPrivateKotlinRuntime by tasks.registering {
	dependsOn(tasks.jar)
	doLast {
		val jarFile = tasks.jar.get().archiveFile.get().asFile
		val entries = zipTree(jarFile)
		val required = listOf(
			"kotlin/jvm/functions/Function1.class",
			"kotlinx/serialization/json/JsonKt.class"
		)
		required.forEach { path ->
			check(!entries.matching { include(path) }.isEmpty) {
				"$path must be bundled in ${jarFile.name} to keep Kotlin private to this plugin's classloader"
			}
		}
	}
}

tasks.check {
	dependsOn(verifyPrivateKotlinRuntime)
}

publishing {
	publications {
		create<MavenPublication>("maven") {
			artifactId = rootProject.name
			from(components["java"])
			pom {
				name.set("SneakyDiscordLink")
				description.set("Paper plugin for linking Minecraft and Discord accounts")
				url.set("https://github.com/Team-Sneakymouse/SneakyDiscordLink")
				scm {
					url.set("https://github.com/Team-Sneakymouse/SneakyDiscordLink")
					connection.set("scm:git:https://github.com/Team-Sneakymouse/SneakyDiscordLink.git")
				}
			}
		}
	}
	repositories {
		maven {
			name = "sneakyrp"
			url = uri("https://maven.sneakyrp.com/releases")
			credentials(PasswordCredentials::class)
			authentication {
				create<org.gradle.authentication.http.BasicAuthentication>("basic")
			}
		}
	}
}

tasks.withType<PublishToMavenRepository>().configureEach {
	dependsOn(tasks.check)
}
