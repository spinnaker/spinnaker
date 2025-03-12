/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.netflix.spinnaker.kork.plugins.sdk.serde

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TreeTraversingParser
import com.netflix.spinnaker.kork.exceptions.IntegrationException
import com.netflix.spinnaker.kork.plugins.api.serde.SerdeService
import java.io.IOException

/**
 * The standard [SerdeService] implementation, backed by Jackson.
 */
class SerdeServiceImpl(
  private val objectMapper: ObjectMapper
) : SerdeService {

  override fun toJson(obj: Any): String {
    try {
      return objectMapper.writeValueAsString(obj)
    } catch (e: JsonProcessingException) {
      throw IntegrationException("Failed serializing object to json", e)
    }
  }

  override fun <T : Any> fromJson(json: String, type: Class<T>): T {
    try {
      return objectMapper.readValue(json, type)
    } catch (e: JsonProcessingException) {
      throw IntegrationException("Failed deserializing json to class", e)
    }
  }

  override fun <T : Any> mapTo(obj: Any, type: Class<T>): T {
    return mapTo(null, obj, type)
  }

  override fun <T : Any> mapTo(pointer: String?, obj: Any, type: Class<T>): T {
    try {
      return objectMapper.readValue(
        TreeTraversingParser(
          getPointer(pointer, objectMapper.valueToTree(obj)),
          objectMapper
        ),
        type
      )
    } catch (e: IOException) {
      throw IntegrationException("Failed mapping object to '$type'", e)
    }
  }

  private fun getPointer(pointer: String?, rootNode: ObjectNode): JsonNode {
    return if (pointer != null) rootNode.at(pointer) else rootNode
  }
}
