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

include(
  "keel-actuator",
  "keel-api",
  "keel-api-jackson",
  "keel-artifact",
  "keel-bakery-plugin",
  "keel-bom",
  "keel-clouddriver",
  "keel-core",
  "keel-core-test",
  "keel-docker",
  "keel-ec2-plugin",
  "keel-eureka",
  "keel-front50",
  "keel-igor",
  "keel-orca",
  "keel-plugin",
  "keel-retrofit",
  "keel-retrofit-test-support",
  "keel-spring-test-support",
  "keel-sql",
  "keel-test",
  "keel-titus-plugin",
  "keel-veto",
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
