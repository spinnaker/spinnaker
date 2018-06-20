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

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME
import com.fasterxml.jackson.annotation.JsonTypeName
import com.netflix.spinnaker.keel.model.ListCriteria
import java.time.Instant

@JsonTypeInfo(use = NAME, include = PROPERTY, property = "kind")
@JsonSubTypes(
  Type(AssetChangeRecord::class),
  Type(AssetConvergenceRecord::class)
)
sealed class ActivityRecord {
  abstract val assetId: String
  abstract val actor: String
  val timestamp: Instant = Instant.now()
}

enum class AssetChangeAction { UPSERT, DELETE }

/**
 * An activity record for whenever a change is made to [assetId]
 */
@JsonTypeName("AssetChange")
data class AssetChangeRecord(
  override val assetId: String,
  override val actor: String,
  val action: AssetChangeAction,
  val value: Asset<AssetSpec>
) : ActivityRecord()

/**
 * Activity record whenever an asset is converged.
 */
@JsonTypeName("AssetConvergence")
data class AssetConvergenceRecord(
  override val assetId: String,
  override val actor: String,
  val result: ConvergeResult
) : ActivityRecord()

/**
 * Find the [ActivityRecord] class for [name]. Everyone loves reflection.
 */
fun activityRecordClassForName(name: String): Class<ActivityRecord>? {
  return ActivityRecord::class.annotations
    .filterIsInstance<JsonSubTypes>()
    .firstOrNull()
    ?.let { subTypes ->
      @Suppress("UNCHECKED_CAST")
      subTypes.value.find { it.name == name }?.value as Class<ActivityRecord>?
    }
}

/**
 * Responsible for recording all activity related to assets. This is primarily meant for diagnostics and auditing.
 */
interface AssetActivityRepository {

  fun record(activity: ActivityRecord)

  fun getHistory(assetId: String, criteria: ListCriteria): List<ActivityRecord>

  fun <T : ActivityRecord> getHistory(assetId: String, kind: Class<T>, criteria: ListCriteria): List<T>
}
