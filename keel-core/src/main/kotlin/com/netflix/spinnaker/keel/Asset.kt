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
import java.time.Instant
import kotlin.reflect.KClass

/**
 * @param schema The base asset wrapper version.
 * @param kind The cloudProvider-/config-agnostic asset kind.
 * @param spec The static config for the asset kind.
 * @param status The current status of the asset (active or deleted).
 * @param labels A user-defined key/value string pair used for indexing & filtering.
 * @param attributes User-land specific metadata and extension data.
 * @param cas An optional ID-granular pessimistic lock.
 * @param createdAt Timestamp of when the asset was created.
 * @param updatedAt Timestamp of when the asset was last updated.
 * @param lastUpdatedBy Identifier of who or what last updated the asset.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
@JsonInclude(JsonInclude.Include.NON_NULL)
abstract class Asset<out S : AssetSpec>
@JsonCreator constructor(
  @JsonSerializeToVersion(defaultToSource = true) val schema: String,
  val kind: String,
  val spec: S,
  var status: AssetStatus = AssetStatus.ACTIVE,
  val labels: MutableMap<String, String> = mutableMapOf(),
  val attributes: MutableList<Attribute<*>> = mutableListOf(),
  val cas: Long? = null,
  var createdAt: Instant? = null,
  var updatedAt: Instant? = null,
  var lastUpdatedBy: String? = null
) {

  protected abstract val id: String

  @JsonGetter fun id() = if (spec is AssetIdProvider) spec.assetId() else id

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
}

/**
 * Allows an AssetSpec to provide the Asset ID.
 */
interface AssetIdProvider {
  fun assetId(): String
}
