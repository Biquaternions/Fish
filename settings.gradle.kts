import java.util.Locale

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

if (!file(".git").exists()) {
    val errorText = """
        
        =====================[ ERROR ]=====================
         The Fish project directory is not a properly cloned Git repository.
         
         In order to build Fish from source you must clone
         the Fish repository using Git, not download a code
         zip from GitHub.
         
         Built Fish jars are available for download at
         https://github.com/Biquaternions/Fish
         
         See https://github.com/Biquaternions/Fish/blob/HEAD/CONTRIBUTING.md
         for further information on building and modifying Fish.
        ===================================================
    """.trimIndent()
    error(errorText)
}

rootProject.name = "fish"
for (name in listOf("fish-api", "fish-server")) {
    val projName = name.lowercase(Locale.ENGLISH)
    include(projName)
    findProject(":$projName")!!.projectDir = file(name)
}
