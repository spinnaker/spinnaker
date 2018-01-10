/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.keel.state

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.annotation.Computed
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.primaryConstructor

data class FieldState(
  val name: String,
  val current: Any?,
  val desired: Any?
) {
  fun changed() = current != desired
}

/**
 * Allows callback mutations of individual fields before building FieldState objects.
 */
data class FieldMutator(
  val name: String,
  val mutator: (v: Any?) -> Any?
)

/**
 * Performs basic state diffing to determine whether or not system state needs to be converged on the desired spec.
 */
class StateInspector(
  private val objectMapper: ObjectMapper
) {

  private val log = LoggerFactory.getLogger(javaClass)

  fun getDiff(intentId: String,
              currentState: Any,
              desiredState: Any,
              modelClass: KClass<*>,
              specClass: KClass<*>,
              currentStateFieldMutators: List<FieldMutator> = listOf(),
              ignoreKeys: Set<String?> = setOf()): Set<FieldState> {
    val ignoredCurrentStateParams = getComputedParameters(currentState, modelClass.primaryConstructor!!)
    val currentStateMap = convertListsToSets(
      objectMapper.convertValue<Map<String, Any?>>(currentState, ANY_MAP_TYPE)
        .filterNot {
        ignoredCurrentStateParams.contains(it.key)
      }
    )
    val desiredStateMap = convertListsToSets(
      objectMapper.convertValue<Map<String, Any?>>(desiredState, ANY_MAP_TYPE)
    )

    val allIgnoredKeys = ignoreKeys.plus(getJsonTypePropertyName(specClass)).toMutableSet()
    if (desiredState is ComputedPropertyProvider) {
      allIgnoredKeys.addAll(desiredState.additionalComputedProperties())
    }

    val fields = getChangedFields(
      currentStateMap,
      desiredStateMap,
      allIgnoredKeys,
      currentStateFieldMutators
    )

    if (fields.isNotEmpty()) {
      log.debug("Actual state has diverged from desired state: $fields (intent: $intentId)")
    }
    return fields.toSet()
  }

  private fun getChangedFields(currentState: Map<String, Any?>,
                               desiredState: Map<String, Any?>,
                               ignoreKeys: Set<String?>,
                               currentFieldMutators: List<FieldMutator>) =
    desiredState
      .filterKeys { !ignoreKeys.contains(it) }
      .map {
        val mutator = currentFieldMutators.find { (name) -> name == it.key }?.mutator
        FieldState(it.key, if (mutator == null) currentState[it.key] else mutator.invoke(currentState[it.key]), it.value)
      }
      .filter { it.changed() }

  private fun getJsonTypePropertyName(k: KClass<*>): String? = k.findAnnotation<JsonTypeInfo>()?.property

  private fun getComputedParameters(o: Any, c: KFunction<*>): Set<String> {
    val params = c.parameters.filter { it.findAnnotation<Computed>()?.ignore == true }.mapNotNull { it.name }
    if (o is ComputedPropertyProvider) {
      params.toMutableSet().addAll(o.additionalComputedProperties())
    }
    return params.distinct().toSet()
  }

  // TODO rz - ugh gross. Custom deserializer for jackson instead?
  private fun convertListsToSets(m: Map<String, Any?>): Map<String, Any?> {
    val newMap = mutableMapOf<String, Any?>()
    m.entries.forEach {
      when {
          it.value is Map<*, *> -> newMap[it.key] = convertListsToSets(it.value as Map<String, Any?>)
          it.value is List<*> -> newMap[it.key] = (it.value as List<*>).toSet()
          else -> newMap[it.key] = it.value
      }
    }
    return newMap
  }
}

private val ANY_MAP_TYPE = object : TypeReference<MutableMap<String, Any?>>(){}
