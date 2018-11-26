/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.scattergather.reducer

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.netflix.spinnaker.clouddriver.scattergather.ReducedResponse
import com.netflix.spinnaker.clouddriver.scattergather.ResponseReducer
import okhttp3.Response
import org.springframework.http.HttpStatus

/**
 * Performs a recursive merge across responses.
 *
 * Elements inside of an array will not be recursed. If two responses have the same
 * key mapped to an array, the elements from the second response will be appended,
 * removing any duplicate objects, but there will be no recursion of the array
 * elements themselves.
 *
 * Conflict resolution is last-one-wins, where responses are ordered by the client.
 */
class DeepMergeResponseReducer : ResponseReducer {

  private val objectMapper = ObjectMapper()

  override fun reduce(responses: List<Response>): ReducedResponse {
    val status = getResponseCode(responses)
    val body = mergeResponseBodies(responses, status)

    return ReducedResponse(
      status,
      mapOf(), // TODO(rz): There's no real benefit to propagate headers at this point.
      "application/json",
      "UTF-8",
      body?.toString(),
      hasErrors(responses)
    )
  }

  /**
   * Merges all response bodies into a single [JsonNode]. Uses the first response
   * as a base, layering each subsequent non-null response on top.
   */
  private fun mergeResponseBodies(responses: List<Response>, responseStatus: Int): JsonNode? {
    val bodies = responses
      .asSequence()
      .map { Pair(it.body()?.string(), it) }
      .filter { it.first != null }
      .toList()

    if (bodies.isEmpty()) {
      return null
    }

    if (responseStatus !in (200..299)) {
      // Find the highest response status and return that.
      val highestResponseBody = bodies.sortedByDescending { it.second.code() }.first().first
      if (highestResponseBody != null) {
        return objectMapper.readTree(highestResponseBody)
      }
    }

    val main = objectMapper.readTree(bodies.first().first)
    if (bodies.size == 1) {
      return main
    }

    bodies.subList(1, bodies.size).forEach {
      mergeNodes(main, objectMapper.readTree(it.first))
    }

    return main
  }

  private fun mergeNodes(mainNode: JsonNode, updateNode: JsonNode?): JsonNode {
    if (updateNode == null) {
      return mainNode
    }

    val fieldNames = updateNode.fieldNames()
    while (fieldNames.hasNext()) {
      val updatedFieldName = fieldNames.next()
      val valueToBeUpdated = mainNode.get(updatedFieldName)
      val updatedValue = updateNode.get(updatedFieldName)

      if (valueToBeUpdated != null && valueToBeUpdated is ArrayNode && updatedValue.isArray) {
        updatedValue.forEach { updatedChildNode ->
          if (!valueToBeUpdated.contains(updatedChildNode)) {
            valueToBeUpdated.add(updatedChildNode)
          }
        }
      } else if (valueToBeUpdated != null && valueToBeUpdated.isObject) {
        mergeNodes(valueToBeUpdated, updatedValue)
      } else {
        if (mainNode is ObjectNode) {
          mainNode.replace(updatedFieldName, updatedValue)
        }
      }
    }
    return mainNode
  }

  private fun getResponseCode(responses: List<Response>): Int {
    if (hasErrors(responses)) {
      return HttpStatus.BAD_GATEWAY.value()
    }

    val distinctCodes = responses.asSequence().map { it.code() }.distinct().toList()
    return when {
      distinctCodes.size == 1 -> distinctCodes[0]
      distinctCodes.any { it == 404 } -> HttpStatus.NOT_FOUND.value()
      distinctCodes.any { it == 429 } -> HttpStatus.TOO_MANY_REQUESTS.value()
      else -> distinctCodes.sortedDescending().first()
    }
  }

  private fun hasErrors(responses: List<Response>): Boolean =
    responses.any { it.code() >= 500 }
}
