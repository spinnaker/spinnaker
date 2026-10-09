/*
 * Copyright 2020 Armory, Inc.
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

package com.netflix.spinnaker.gradle.extension.compatibility

import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.ObjectMapper
import tools.jackson.dataformat.yaml.YAMLFactory
import tools.jackson.module.kotlin.KotlinModule
import tools.jackson.module.kotlin.readValue
import java.lang.IllegalArgumentException
import java.net.URL
import tools.jackson.dataformat.yaml.YAMLMapper

class DefaultSpinnakerVersionsClient(private val baseURL: String) : SpinnakerVersionsClient {

  private val mapper = YAMLMapper.builder(YAMLFactory())
    .addModule(KotlinModule.Builder().build())
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .build()

  override fun getSpinnakerBOM(version: String): SpinnakerBOM =
    URL("$baseURL/bom/$version.yml").openStream().use {
      mapper.readValue(it)
    }

  override fun getVersionsManifest(): SpinnakerVersionsManifest =
    URL("$baseURL/versions.yml").openStream().use {
      mapper.readValue(it)
    }
}

interface SpinnakerVersionsClient {
  fun getSpinnakerBOM(version: String): SpinnakerBOM
  fun getVersionsManifest(): SpinnakerVersionsManifest
}

enum class SpinnakerVersionAlias {
  LATEST, SUPPORTED, NIGHTLY;

  companion object {
    fun from(str: String) = try { valueOf(str.uppercase()) } catch (iae: IllegalArgumentException) { null }
    fun isAlias(str: String) = values().any { from(str) != null }
  }
}

data class SpinnakerVersionsManifest(val latestSpinnaker: String, val versions: List<SpinnakerVersion>)
data class SpinnakerVersion(val version: String)

data class SpinnakerBOM(val services: Map<String, ServiceVersion>)
data class ServiceVersion(val version: String?)
