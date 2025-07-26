import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    java // TODO java launcher tasks
    id("io.papermc.paperweight.patcher") version "2.0.0-beta.17"
}

val paperMavenPublicUrl = "https://repo.papermc.io/repository/maven-public/"

paperweight {
    upstreams.register("pufferfish") {
        repo = github("SerlithNetwork", "Puffernot")
        ref = providers.gradleProperty("pufferfishCommit")

        patchFile {
            path = "pufferfish-server/build.gradle.kts"
            outputFile = file("fish-server/build.gradle.kts")
            patchFile = file("fish-server/build.gradle.kts.patch")
        }
        patchFile {
            path = "pufferfish-api/build.gradle.kts"
            outputFile = file("fish-api/build.gradle.kts")
            patchFile = file("fish-api/build.gradle.kts.patch")
        }
        patchRepo("paperApi") {
            upstreamPath = "paper-api"
            patchesDir = file("fish-api/paper-patches")
            outputDir = file("paper-api")
        }
        patchDir("pufferfishApi") {
            upstreamPath = "pufferfish-api"
            excludes = setOf("build.gradle.kts", "build.gradle.kts.patch", "paper-patches")
            patchesDir = file("fish-api/pufferfish-patches")
            outputDir = file("pufferfish-api")
        }
    }
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = Charsets.UTF_8.name()
        options.release = 21
        options.isFork = true
        options.compilerArgs.addAll(listOf("-Xlint:-deprecation", "-Xlint:-removal"))
    }
    tasks.withType<Javadoc> {
        options.encoding = Charsets.UTF_8.name()
    }
    tasks.withType<ProcessResources> {
        filteringCharset = Charsets.UTF_8.name()
    }
    tasks.withType<Test> {
        testLogging {
            showStackTraces = true
            exceptionFormat = TestExceptionFormat.FULL
            events(TestLogEvent.STANDARD_OUT)
        }
    }
    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }

    repositories {
        mavenCentral()
        mavenLocal()
        maven(paperMavenPublicUrl)
        maven("https://jitpack.io")
        maven("https://repo.j4c0b3y.net/public/")
    }
}

tasks.register("printMinecraftVersion") {
    doLast {
        println(providers.gradleProperty("mcVersion").get().trim())
    }
}

tasks.register("printFishVersion") {
    doLast {
        println(project.version)
    }
}
