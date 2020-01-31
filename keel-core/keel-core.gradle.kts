/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
  `java-library`
  id("kotlin-spring")
}

dependencies {
  api(project(":keel-api"))
  api("com.fasterxml.jackson.core:jackson-databind")
  api("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
  api("com.fasterxml.jackson.module:jackson-module-kotlin")
  api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
  api("de.huxhorn.sulky:de.huxhorn.sulky.ulid")
  api("com.netflix.spinnaker.kork:kork-artifacts")
  api("de.danielbechler:java-object-diff")
  api("org.springframework:spring-context")
  api("org.springframework.boot:spring-boot-autoconfigure")
  api("com.netflix.frigga:frigga")
  api("com.netflix.spinnaker.kork:kork-core")
  api("net.swiftzer.semver:semver:1.1.0")
  api("org.jetbrains.kotlinx:kotlinx-coroutines-core")
  api("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j")

  implementation("org.springframework:spring-tx")

  testImplementation(project(":keel-test"))
  testImplementation(project(":keel-core-test"))
  testImplementation("io.strikt:strikt-jackson")
  testImplementation("dev.minutest:minutest")

  testImplementation("org.assertj:assertj-core")
  testImplementation("org.junit.jupiter:junit-jupiter-api")
  testImplementation("org.junit.jupiter:junit-jupiter-params")
  testImplementation("org.springframework.boot:spring-boot-test-autoconfigure")
}
