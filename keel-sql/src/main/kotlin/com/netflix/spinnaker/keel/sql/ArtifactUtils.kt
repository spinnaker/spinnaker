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
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.exceptions.ArtifactParsingException
import com.netflix.spinnaker.keel.api.artifacts.ArtifactType
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.plugins.ArtifactSupplier
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper

private val objectMapper: ObjectMapper = configuredObjectMapper()

/**
 * A helper function to construct the proper artifact type from the serialized JSON.
 */
fun mapToArtifact(
  artifactSupplier: ArtifactSupplier<*, *>,
  name: String,
  type: ArtifactType,
  json: String,
  reference: String,
  deliveryConfigName: String
): DeliveryArtifact {
  try {
    val artifactAsMap = objectMapper.readValue<Map<String, Any>>(json)
      .toMutableMap()
      .also {
        it["name"] = name
        it["type"] = type
        it["reference"] = reference
        it["deliveryConfigName"] = deliveryConfigName
      }
    return objectMapper.convertValue(artifactAsMap, artifactSupplier.supportedArtifact.artifactClass)
  } catch (e: JsonMappingException) {
    throw ArtifactParsingException(name, type, json, e)
  }
}
