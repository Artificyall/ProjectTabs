plugins {
    id("java")
    // Passez à Kotlin 2.x pour la compatibilité Gradle 9
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.10.5"
}

group = "com.github.artificyal"
version = "1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories() // Pour télécharger le SDK PhpStorm
    }
}

dependencies {
    intellijPlatform {
        create("PS", "2025.3.1.1")
        instrumentationTools()
        pluginVerifier()
    }
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }


    // Le nouveau plugin configure automatiquement beaucoup de choses,
    // mais vous pouvez toujours personnaliser :
    patchPluginXml {
        sinceBuild.set("253")
        untilBuild.set("253.*")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}