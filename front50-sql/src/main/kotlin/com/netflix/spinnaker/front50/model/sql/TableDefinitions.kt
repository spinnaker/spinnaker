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
import org.jooq.Field
import org.jooq.InsertOnDuplicateSetMoreStep
import org.jooq.Record
import org.jooq.impl.DSL
import org.jooq.util.mysql.MySQLDSL
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Clock

open class DefaultTableDefinition(
  val objectType: ObjectType,
  val tableName: String,
  val supportsHistory: Boolean
) {
  val historyTableName:String
    get() = "${tableName}_history"

  open fun getFields(): List<Field<Any>> {
    return listOf(
      DSL.field("id"),
      DSL.field("body"),
      DSL.field("created_at"),
      DSL.field("last_modified_at"),
      DSL.field("last_modified_by"),
      DSL.field("is_deleted")
    )
  }

  open fun getValues(objectMapper: ObjectMapper, objectKey: String, item: Timestamped): List<Any> {
    val objectAsString = objectMapper.writeValueAsString(item)
    return listOf(
      objectKey, objectAsString, item.lastModified, item.lastModified, item.lastModifiedBy, false
    )
  }

  fun getHistoryFields(): List<Field<Any>> {
    return listOf(
      DSL.field("id"),
      DSL.field("body"),
      DSL.field("body_sig"),
      DSL.field("last_modified_at"),
      DSL.field("recorded_at")
    )
  }

  fun getHistoryValues(objectMapper: ObjectMapper,
                       clock: Clock,
                       objectKey: String,
                       item: Timestamped): List<Any> {
    val objectAsString = objectMapper.writeValueAsString(item)

    val signature = Hashing.murmur3_128().newHasher().putString(objectAsString, UTF_8).hash().toString()

    return listOf(
      objectKey, objectAsString, signature, item.lastModified, clock.millis()
    )
  }

  open fun onDuplicateKeyUpdate(): InsertOnDuplicateSetMoreStep<Record>.() -> Unit {
    return {}
  }
}

class DeliveryTableDefinition : DefaultTableDefinition(ObjectType.DELIVERY, "deliveries", true) {
  override fun getFields(): List<Field<Any>> {
    return listOf(
      DSL.field("id"),
      DSL.field("application"),
      DSL.field("body"),
      DSL.field("created_at"),
      DSL.field("last_modified_at"),
      DSL.field("last_modified_by"),
      DSL.field("is_deleted")
    )
  }

  override fun getValues(objectMapper: ObjectMapper, objectKey: String, item: Timestamped): List<Any> {
    val objectAsString = objectMapper.writeValueAsString(item)
    return listOf(
      objectKey,
      (item as Delivery).application,
      objectAsString,
      item.lastModified,
      item.lastModified,
      item.lastModifiedBy,
      false
    )
  }

  override fun onDuplicateKeyUpdate(): InsertOnDuplicateSetMoreStep<Record>.() -> Unit {
    return {
      set(DSL.field("application"), MySQLDSL.values(DSL.field("application")) as Any)
    }
  }
}

class PipelineTableDefinition : DefaultTableDefinition(ObjectType.PIPELINE, "pipelines", true) {
  override fun getFields(): List<Field<Any>> {
    return listOf(
      DSL.field("id"),
      DSL.field("name"),
      DSL.field("application"),
      DSL.field("body"),
      DSL.field("created_at"),
      DSL.field("last_modified_at"),
      DSL.field("last_modified_by"),
      DSL.field("is_deleted")
    )
  }

  override fun getValues(objectMapper: ObjectMapper, objectKey: String, item: Timestamped): List<Any> {
    val objectAsString = objectMapper.writeValueAsString(item)
    return listOf(
      objectKey,
      (item as Pipeline).name,
      (item as Pipeline).application,
      objectAsString,
      item.lastModified,
      item.lastModified,
      item.lastModifiedBy,
      false
    )
  }

  override fun onDuplicateKeyUpdate(): InsertOnDuplicateSetMoreStep<Record>.() -> Unit {
    return {
      set(DSL.field("name"), MySQLDSL.values(DSL.field("name")) as Any)
      set(DSL.field("application"), MySQLDSL.values(DSL.field("application")) as Any)
    }
  }
}

class PipelineStrategyTableDefinition : DefaultTableDefinition(ObjectType.STRATEGY, "pipeline_strategies", true) {
  override fun getFields(): List<Field<Any>> {
    return listOf(
      DSL.field("id"),
      DSL.field("name"),
      DSL.field("application"),
      DSL.field("body"),
      DSL.field("created_at"),
      DSL.field("last_modified_at"),
      DSL.field("last_modified_by"),
      DSL.field("is_deleted")
    )
  }

  override fun getValues(objectMapper: ObjectMapper, objectKey: String, item: Timestamped): List<Any> {
    val objectAsString = objectMapper.writeValueAsString(item)
    return listOf(
      objectKey,
      (item as Pipeline).name,
      (item as Pipeline).application,
      objectAsString,
      item.lastModified,
      item.lastModified,
      item.lastModifiedBy,
      false
    )
  }

  override fun onDuplicateKeyUpdate(): InsertOnDuplicateSetMoreStep<Record>.() -> Unit {
    return {
      set(DSL.field("name"), MySQLDSL.values(DSL.field("name")) as Any)
      set(DSL.field("application"), MySQLDSL.values(DSL.field("application")) as Any)
    }
  }
}

class ProjectTableDefinition : DefaultTableDefinition(ObjectType.PROJECT, "projects", true) {
  override fun getFields(): List<Field<Any>> {
    return listOf(
      DSL.field("id"),
      DSL.field("name"),
      DSL.field("body"),
      DSL.field("created_at"),
      DSL.field("last_modified_at"),
      DSL.field("last_modified_by"),
      DSL.field("is_deleted")
    )
  }

  override fun getValues(objectMapper: ObjectMapper, objectKey: String, item: Timestamped): List<Any> {
    val objectAsString = objectMapper.writeValueAsString(item)
    return listOf(
      objectKey,
      (item as Project).name,
      objectAsString,
      item.lastModified,
      item.lastModified,
      item.lastModifiedBy,
      false
    )
  }

  override fun onDuplicateKeyUpdate(): InsertOnDuplicateSetMoreStep<Record>.() -> Unit {
    return {
      set(DSL.field("name"), MySQLDSL.values(DSL.field("name")) as Any)
    }
  }
}
