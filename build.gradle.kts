import org.jreleaser.model.Active

group = "ru.code4a"
version = file("version").readText().trim()

plugins {
  val kotlinVersion = "2.0.21"

  kotlin("jvm") version kotlinVersion
  kotlin("plugin.serialization") version kotlinVersion
  kotlin("plugin.allopen") version kotlinVersion
  kotlin("plugin.noarg") version kotlinVersion

  id("org.kordamp.gradle.jandex") version "1.1.0"

  `java-library`
  `maven-publish`
  id("org.jreleaser") version "1.12.0"
}

java {
  withJavadocJar()
  withSourcesJar()
}

publishing {
  publications {
    create<MavenPublication>("mavenJava") {
      artifactId = "quarkus-graphql-relay"

      from(components["java"])

      pom {
        name = "Quarkus GraphQL Relay Library"
        description =
          "A comprehensive library for implementing the Relay GraphQL specification in Quarkus applications. \n" +
            "  It provides secure node identification, cursor-based pagination, connection pattern implementation, \n" +
            "  and seamless integration with JPA/Hibernate for building Relay-compliant GraphQL APIs."
        url = "https://github.com/4ait/quarkus-graphql-relay-lib"
        inceptionYear = "2025"
        licenses {
          license {
            name = "The Apache License, Version 2.0"
            url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
          }
        }
        developers {
          developer {
            id = "tikara"
            name = "Evgeniy Simonenko"
            email = "tiikara93@gmail.com"
            organization.set("4A LLC")
            roles.set(
              listOf(
                "Software Developer",
                "Head of Development"
              )
            )
          }
        }
        organization {
          name = "4A LLC"
          url = "https://4ait.ru"
        }
        scm {
          connection = "scm:git:git://github.com:4ait/quarkus-graphql-relay-lib.git"
          developerConnection = "scm:git:ssh://github.com:4ait/quarkus-graphql-relay-lib.git"
          url = "https://github.com/4ait/quarkus-graphql-relay-lib"
        }
      }
    }
  }
  repositories {
    maven {
      url =
        layout.buildDirectory
          .dir("staging-deploy")
          .get()
          .asFile
          .toURI()
    }
  }
}

repositories {
  mavenCentral()
}

allOpen {
  annotation("jakarta.enterprise.context.ApplicationScoped")
}

noArg {
  annotation("org.eclipse.microprofile.graphql.Input")
}

tasks.withType<Test> {
  useJUnitPlatform()
  dependsOn(tasks["jandex"])
}

val quarkusVersion: String by project

dependencies {
  implementation("io.quarkus:quarkus-arc:${quarkusVersion}")

  implementation(kotlin("reflect"))

  implementation("com.lambdaworks:scrypt:1.4.0")
  implementation("ru.code4a:error-handling:1.0.0")
  implementation("io.quarkus:quarkus-hibernate-orm:${quarkusVersion}")
  implementation("io.quarkus:quarkus-smallrye-graphql:${quarkusVersion}")
  implementation("io.quarkus:quarkus-core-deployment:${quarkusVersion}")

  implementation("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.7.3")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.7.3")

  testImplementation(kotlin("test"))
  testImplementation("org.mockito:mockito-core:5.12.0")
}

tasks.named("compileTestKotlin", org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask::class.java) {
  compilerOptions {
    freeCompilerArgs.add("-Xdebug")
  }
}

jreleaser {
  project {
    copyright.set("4A LLC")
  }
  gitRootSearch.set(true)
  signing {
    active.set(Active.ALWAYS)
    armored.set(true)
  }
  release {
    github {
      overwrite.set(true)
      branch.set("master")
    }
  }
  deploy {
    maven {
      mavenCentral {
        create("maven-central") {
          active.set(Active.ALWAYS)
          url.set("https://central.sonatype.com/api/v1/publisher")
          stagingRepositories.add("build/staging-deploy")
          retryDelay.set(30)
        }
      }
    }
  }
}

kotlin {
  compilerOptions {
    freeCompilerArgs.add("-Xemit-jvm-type-annotations")
  }
}
