/*
 *
 * Copyright 2019 Netflix, Inc.
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
 *
 */
package com.netflix.spinnaker.keel.sql

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.exceptions.ArtifactParsingException
import com.netflix.spinnaker.keel.api.ArtifactStatus
import com.netflix.spinnaker.keel.api.ArtifactType
import com.netflix.spinnaker.keel.api.ArtifactType.deb
import com.netflix.spinnaker.keel.api.ArtifactType.docker
import com.netflix.spinnaker.keel.api.DebianArtifact
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DockerArtifact
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper

private val objectMapper: ObjectMapper = configuredObjectMapper()

/**
 * A helper function to construct the proper artifact type from the serialized json.
 */
fun mapToArtifact(
  name: String,
  type: ArtifactType,
  json: String,
  reference: String,
  deliveryConfigName: String
): DeliveryArtifact {
  try {
    val details = objectMapper.readValue<Map<String, Any>>(json)
    return when (type) {
      deb -> {
        val statuses: List<ArtifactStatus> = details["statuses"]?.let { it ->
          try {
            objectMapper.convertValue<List<ArtifactStatus>>(it)
          } catch (e: java.lang.IllegalArgumentException) {
            null
          }
        } ?: emptyList()
        DebianArtifact(
          name = name,
          statuses = statuses,
          reference = reference,
          deliveryConfigName = deliveryConfigName
        )
      }
      docker -> {
        val tagVersionStrategy = details.getOrDefault("tagVersionStrategy", "semver-tag")
        DockerArtifact(
          name = name,
          tagVersionStrategy = objectMapper.convertValue(tagVersionStrategy),
          captureGroupRegex = details["captureGroupRegex"]?.toString(),
          reference = reference,
          deliveryConfigName = deliveryConfigName
        )
      }
    }
  } catch (e: JsonMappingException) {
    throw ArtifactParsingException(name, type, json, e)
  }
}
