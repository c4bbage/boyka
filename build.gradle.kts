plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.16.1"  // Updated to latest version
}

group = "com.dobest1"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}


// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
    version.set("2023.2")  // Updated to latest stable version
    type.set("IC") // Target IDE Platform

    plugins.set(listOf(/* Plugin Dependencies */))
    plugins.add("java")
}
//sourceSets {
//    main {
//        java.srcDirs("src/main/java")
//        kotlin.srcDirs("src/main/kotlin")
//    }
//}
dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.10.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.vladsch.flexmark:flexmark-all:0.62.2")
    implementation("org.commonmark:commonmark:0.18.1")

    // 如果需要执行Python代码，可能需要添加以下依赖
    // implementation("org.python:jython-standalone:2.7.2")
    // 如果需要更高级的文件操作，可能需要添加以下依赖
    // implementation("commons-io:commons-io:2.11.0")
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"  // Updated to Java 17
        targetCompatibility = "17"  // Updated to Java 17
    }
    // prepareSandbox {
    //     from(configurations.runtimeClasspath) {
    //         into("${intellij.pluginName.get()}/lib")
    //     }
    // }
    jar {
        from(configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) })
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
    patchPluginXml {
        sinceBuild.set("232")  // Updated to match intellij.version
        untilBuild.set("242.*")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }

    buildSearchableOptions {
        enabled = false
    }

}