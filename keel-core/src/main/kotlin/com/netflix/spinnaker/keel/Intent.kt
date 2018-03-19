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
package com.netflix.spinnaker.keel

import com.fasterxml.jackson.annotation.*
import com.github.jonpeterson.jackson.module.versioning.JsonSerializeToVersion
import com.netflix.spectator.api.BasicTag
import com.netflix.spinnaker.keel.attribute.Attribute
import com.netflix.spinnaker.keel.policy.Policy
import kotlin.reflect.KClass

/**
 * @param schema The base intent wrapper version.
 * @param kind The cloudProvider-/config-agnostic intent kind.
 * @param spec The static config for the intent kind.
 * @param status The current status of the intent (active or deleted).
 * @param labels A user-defined key/value string pair used for indexing & filtering.
 * @param attributes User-land specific metadata and extension data.
 * @param policies User-defined behavioral policies specific to the intent.
 * @param cas An optional ID-granular pessimistic lock.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
@JsonInclude(JsonInclude.Include.NON_NULL)
abstract class Intent<out S : IntentSpec>
@JsonCreator constructor(
  @JsonSerializeToVersion(defaultToSource = true) val schema: String,
  val kind: String,
  val spec: S,
  var status: IntentStatus = IntentStatus.ACTIVE,
  val labels: MutableMap<String, String> = mutableMapOf(),
  val attributes: List<Attribute<*>> = listOf(),
  val policies: List<Policy<*>> = listOf(),
  val cas: Long? = null
) {

  abstract val id: String

  @JsonGetter fun id() = id

  @JsonIgnore
  fun getMetricTags() = listOf(BasicTag("kind", kind), BasicTag("schema", schema))

  @JsonIgnore
  fun getMetricTags(vararg more: String) =
    (0 until more.size)
      .filter { it != 0 && it % 2 == 0 }
      .map { BasicTag(more[it], more[it - 1]) }
      .let { it.toMutableList().also { it.addAll(getMetricTags()) } }

  fun <T : Attribute<Any>> hasAttribute(klass: KClass<T>) = attributes.any { klass.isInstance(it) }

  @Suppress("UNCHECKED_CAST")
  fun <T : Attribute<Any>> getAttribute(klass: KClass<T>): T? = attributes.firstOrNull { klass.isInstance(it) } as T?

  fun <T : Policy<Any>> hasPolicy(klass: KClass<T>) = policies.any { klass.isInstance(it) }

  @Suppress("UNCHECKED_CAST")
  fun <T : Policy<Any>> getPolicy(klass: KClass<T>) = policies.firstOrNull { klass.isInstance(it) } as T?
}
