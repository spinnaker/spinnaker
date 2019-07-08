/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.front50.model.sql

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.hash.Hashing
import com.netflix.spinnaker.front50.model.ObjectType
import com.netflix.spinnaker.front50.model.Timestamped
import com.netflix.spinnaker.front50.model.delivery.Delivery
import com.netflix.spinnaker.front50.model.pipeline.Pipeline
import com.netflix.spinnaker.front50.model.project.Project
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Clock

open class DefaultTableDefinition(
  val objectType: ObjectType,
  val tableName: String,
  val supportsHistory: Boolean
) {
  val historyTableName: String
    get() = "${tableName}_history"

  open fun getInsertPairs(objectMapper: ObjectMapper, objectKey: String, item: Timestamped): Map<String, Any> {
    val objectAsString = objectMapper.writeValueAsString(item)
    return mapOf(
      "id" to objectKey,
      "body" to objectAsString,
      "created_at" to item.lastModified,
      "last_modified_at" to item.lastModified,
      "last_modified_by" to item.lastModifiedBy,
      "is_deleted" to false
    )
  }

  fun getHistoryPairs(
    objectMapper: ObjectMapper,
    clock: Clock,
    objectKey: String,
    item: Timestamped
  ): Map<String, Any> {
    val objectAsString = objectMapper.writeValueAsString(item)

    val signature = Hashing.murmur3_128().newHasher().putString(objectAsString, UTF_8).hash().toString()

    return mapOf(
      "id" to objectKey,
      "body" to objectAsString,
      "body_sig" to signature,
      "last_modified_at" to item.lastModified,
      "recorded_at" to clock.millis()
    )
  }

  open fun getUpdatePairs(insertPairs: Map<String, Any>): Map<String, Any> {
    return mapOf(
      "body" to insertPairs.getValue("body"),
      "last_modified_at" to insertPairs.getValue("last_modified_at"),
      "last_modified_by" to insertPairs.getValue("last_modified_by"),
      "is_deleted" to insertPairs.getValue("is_deleted")
    )
  }
}

class DeliveryTableDefinition : DefaultTableDefinition(ObjectType.DELIVERY, "deliveries", true) {
  override fun getInsertPairs(objectMapper: ObjectMapper, objectKey: String, item: Timestamped): Map<String, Any> {
    val objectAsString = objectMapper.writeValueAsString(item)
    return mapOf(
      "id" to objectKey,
      "application" to (item as Delivery).application,
      "body" to objectAsString,
      "created_at" to item.lastModified,
      "last_modified_at" to item.lastModified,
      "last_modified_by" to item.lastModifiedBy,
      "is_deleted" to false
    )
  }

  override fun getUpdatePairs(insertPairs: Map<String, Any>): Map<String, Any> {
    return super.getUpdatePairs(insertPairs) + mapOf("application" to insertPairs.getValue("application"))
  }
}

class PipelineTableDefinition : DefaultTableDefinition(ObjectType.PIPELINE, "pipelines", true) {
  override fun getInsertPairs(objectMapper: ObjectMapper, objectKey: String, item: Timestamped): Map<String, Any> {
    val objectAsString = objectMapper.writeValueAsString(item)
    return mapOf(
      "id" to objectKey,
      "name" to (item as Pipeline).name,
      "application" to (item as Pipeline).application,
      "body" to objectAsString,
      "created_at" to item.lastModified,
      "last_modified_at" to item.lastModified,
      "last_modified_by" to item.lastModifiedBy,
      "is_deleted" to false
    )
  }

  override fun getUpdatePairs(insertPairs: Map<String, Any>): Map<String, Any> {
    return super.getUpdatePairs(insertPairs) + mapOf(
      "name" to insertPairs.getValue("name"),
      "application" to insertPairs.getValue("application")
    )
  }
}

class PipelineStrategyTableDefinition : DefaultTableDefinition(ObjectType.STRATEGY, "pipeline_strategies", true) {
  override fun getInsertPairs(objectMapper: ObjectMapper, objectKey: String, item: Timestamped): Map<String, Any> {
    val objectAsString = objectMapper.writeValueAsString(item)
    return mapOf(
      "id" to objectKey,
      "name" to (item as Pipeline).name,
      "application" to (item as Pipeline).application,
      "body" to objectAsString,
      "created_at" to item.lastModified,
      "last_modified_at" to item.lastModified,
      "last_modified_by" to item.lastModifiedBy,
      "is_deleted" to false
    )
  }

  override fun getUpdatePairs(insertPairs: Map<String, Any>): Map<String, Any> {
    return super.getUpdatePairs(insertPairs) + mapOf(
      "name" to insertPairs.getValue("name"),
      "application" to insertPairs.getValue("application")
    )
  }
}

class ProjectTableDefinition : DefaultTableDefinition(ObjectType.PROJECT, "projects", true) {
  override fun getInsertPairs(objectMapper: ObjectMapper, objectKey: String, item: Timestamped): Map<String, Any> {
    val objectAsString = objectMapper.writeValueAsString(item)
    return mapOf(
      "id" to objectKey,
      "name" to (item as Project).name,
      "body" to objectAsString,
      "created_at" to item.lastModified,
      "last_modified_at" to item.lastModified,
      "last_modified_by" to item.lastModifiedBy,
      "is_deleted" to false
    )
  }

  override fun getUpdatePairs(insertPairs: Map<String, Any>): Map<String, Any> {
    return super.getUpdatePairs(insertPairs) + mapOf(
      "name" to insertPairs.getValue("name")
    )
  }
}
