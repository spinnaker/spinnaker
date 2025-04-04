/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
  // Apply the Kotlin JVM plugin to add support for Kotlin.
  id("org.jetbrains.kotlin.jvm").version("1.3.72")
  `kotlin-dsl`
}

dependencies {
  implementation("org.gradle.crypto.checksum:org.gradle.crypto.checksum.gradle.plugin:1.4.0")

  implementation(platform("com.fasterxml.jackson:jackson-bom:2.11.1"))
  implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
  implementation("org.jetbrains.kotlin:kotlin-gradle-plugin-api")
  implementation("org.jetbrains.kotlin:kotlin-gradle-plugin")

  // Kotlin standard library.
  implementation("org.jetbrains.kotlin:kotlin-stdlib")

  // Kotlin test library.
  testImplementation("org.jetbrains.kotlin:kotlin-test")

  // Kotlin JUnit integration.
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")

  testImplementation("org.assertj:assertj-core:3.24.2")

  testImplementation("org.junit.jupiter:junit-jupiter-params:5.0.0")

  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.0.0")
}

tasks.test {
  useJUnitPlatform()
}

gradlePlugin {
  website.set("https://spinnaker.io")
  vcsUrl.set("https://github.com/spinnaker/spinnaker")

  // Define the plugin
  plugins {
    create("serviceExtension") {
      id = "io.spinnaker.plugin.service-extension"
      implementationClass = "com.netflix.spinnaker.gradle.extension.SpinnakerServiceExtensionPlugin"
      displayName = "Spinnaker service extension development plugin"
      tags.set(listOf("spinnaker"))
    }

    create("uiExtension") {
      id = "io.spinnaker.plugin.ui-extension"
      implementationClass = "com.netflix.spinnaker.gradle.extension.SpinnakerUIExtensionPlugin"
      displayName = "Spinnaker UI extension development plugin"
      tags.set(listOf("spinnaker"))
    }

    create("bundler") {
      id = "io.spinnaker.plugin.bundler"
      implementationClass = "com.netflix.spinnaker.gradle.extension.SpinnakerExtensionsBundlerPlugin"
      displayName = "Spinnaker extension bundler plugin"
      tags.set(listOf("spinnaker"))
    }

    create("compatibilityTestRunner") {
      id = "io.spinnaker.plugin.compatibility-test-runner"
      implementationClass = "com.netflix.spinnaker.gradle.extension.compatibility.SpinnakerCompatibilityTestRunnerPlugin"
      displayName = "Spinnaker compatibility test runner"
      tags.set(listOf("spinnaker"))
    }
  }
}

// Add a source set for the functional test suite
val functionalTestSourceSet = sourceSets.create("functionaltest") {
}

gradlePlugin.testSourceSets(functionalTestSourceSet)
configurations.getByName("functionaltestImplementation").extendsFrom(configurations.getByName("testImplementation"))
configurations.getByName("functionaltestRuntimeOnly").extendsFrom(configurations.getByName("testRuntimeOnly"))

// Add a task to run the functional tests
val functionalTest by tasks.creating(Test::class) {
  testClassesDirs = functionalTestSourceSet.output.classesDirs
  classpath = functionalTestSourceSet.runtimeClasspath
  useJUnitPlatform()
}

val check by tasks.getting(Task::class) {
  // Run the functional tests as part of `check`
  dependsOn(functionalTest)
}

// There is an issue with Gradle 7 and errors about duplicate files in source sets
// This is a hack to set the duplicate resolution strategy on everything
// https://youtrack.jetbrains.com/issue/KT-46978/Duplicate-resource-errors-on-gradle-7-with-multi-module-multiplatform-project-with-withJava
tasks.withType<Copy> {
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
