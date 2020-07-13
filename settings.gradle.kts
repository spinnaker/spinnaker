/*
 * Copyright 2015 Netflix, Inc.
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

listOf("fiat", "kork").forEach { prj ->
  val propName = "${prj}Composite"
  val projectPath = "../$prj"
  if (settings.extra.has(propName) && java.lang.Boolean.parseBoolean(settings.extra.get(propName).toString())) {
    includeBuild(projectPath)
  }
}

enableFeaturePreview("VERSION_ORDERING_V2")

include(
  "keel-api",
  "keel-artifact",
  "keel-bakery-plugin",
  "keel-bom",
  "keel-clouddriver",
  "keel-core",
  "keel-core-test",
  "keel-docker",
  "keel-ec2-api",
  "keel-ec2-plugin",
  "keel-echo",
  "keel-front50",
  "keel-igor",
  "keel-orca",
  "keel-retrofit",
  "keel-retrofit-test-support",
  "keel-spring-test-support",
  "keel-sql",
  "keel-test",
  "keel-titus-plugin",
  "keel-web"
)

rootProject.name = "keel"

fun ProjectDescriptor.setBuildFile() {
  buildFileName = "$name.gradle.kts"
  children.forEach {
    it.setBuildFile()
  }
}

rootProject.children.forEach {
  it.setBuildFile()
}
