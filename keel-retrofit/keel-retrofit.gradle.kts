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
  api("com.squareup.retrofit2:retrofit")
  api("com.squareup.retrofit2:converter-jackson")
  api("org.jetbrains.kotlinx:kotlinx-coroutines-core")

  implementation("com.netflix.spinnaker.kork:kork-web")
  implementation("com.netflix.spinnaker.kork:kork-security")
  implementation("com.squareup.okhttp3:logging-interceptor")
  implementation("com.netflix.spinnaker.fiat:fiat-api:${property("fiatVersion")}")
  implementation("com.netflix.spinnaker.fiat:fiat-core:${property("fiatVersion")}")
}
