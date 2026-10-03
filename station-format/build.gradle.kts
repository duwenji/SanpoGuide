import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The channel package format (docs/channel-package-format.md, API-003): reading and checking
// channel.json and the prompt slots, plus the app's prompt files, PromptTemplates and the
// built-in channels. Plain JVM so the channel management system's review tools can run exactly
// the checks the app runs and render exactly the prompts it sends.
plugins {
    id("org.jetbrains.kotlin.jvm")
    `maven-publish`
}

group = "com.example.sanpoguide"
version = providers.gradleProperty("stationFormatVersion").get()

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Android ships org.json; bundling the jar into the app would clash with it
    // (lint: DuplicatePlatformClasses). Other users add it themselves.
    compileOnly("org.json:json:20240303")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

// A jar (or the APK) can't list a resource folder, so the built-in channels come with a list of
// their files (BuiltInChannels.INDEX).
val channelsDir = layout.projectDirectory.dir("src/main/resources/sanpoguide/channels")
val channelIndexDir = layout.buildDirectory.dir("generated/channel-index")
val channelIndex by tasks.registering {
    inputs.dir(channelsDir)
    outputs.dir(channelIndexDir)
    doLast {
        val root = channelsDir.asFile
        val paths = root.walkTopDown().filter { it.isFile && it.name != "index.txt" }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .sorted()
            .toList()
        val out = channelIndexDir.get().file("sanpoguide/channels/index.txt").asFile
        out.parentFile.mkdirs()
        out.writeText(paths.joinToString("\n", postfix = "\n"))
    }
}
sourceSets.main {
    resources.srcDir(channelIndex)
}

// Published to GitHub Packages for the channel management system (sanpo-channel-console,
// ADR-001 T-4). Users add org.json themselves (see the dependency note above).
publishing {
    publications {
        create<MavenPublication>("stationFormat") {
            artifactId = "station-format"
            from(components["java"])
            pom {
                name.set("SanpoGuide station-format")
                description.set(
                    "SanpoGuide channel package format (API-003): checks, PromptTemplates, the app's prompts " +
                        "and the built-in channels. Requires org.json on the classpath.",
                )
                url.set("https://github.com/duwenji/SanpoGuide")
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/duwenji/SanpoGuide")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
