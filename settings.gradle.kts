pluginManagement {
    resolutionStrategy.eachPlugin {
        when (requested.id.id) {
            "com.android.application" -> useModule("com.android.tools.build:gradle:8.9.2")
            "org.jetbrains.kotlin.android" ->
                useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.20")
        }
    }
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

rootProject.name = "InfiniLaunch"

if (System.getenv("ANDROID_HOME").isNullOrBlank() &&
    System.getenv("ANDROID_SDK_ROOT").isNullOrBlank() &&
    System.getProperty("android.home").isNullOrBlank()
) {
    val local = file("local.properties")
    if (local.isFile) {
        val properties = java.util.Properties()
        local.inputStream().use { properties.load(it) }
        properties.getProperty("sdk.dir")?.let {
            System.setProperty("android.home", file(it).absolutePath)
        }
    }
}

include(":app")
includeBuild("libraries/libinfiniselect")
