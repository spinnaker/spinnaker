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

package com.netflix.spinnaker.front50.model

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.front50.exception.NotFoundException
import com.netflix.spinnaker.security.AuthenticatedRequest
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.impl.DSL.*
import org.jooq.util.mysql.MySQLDSL
import org.slf4j.LoggerFactory
import java.time.Clock

import com.netflix.spinnaker.front50.model.ObjectType.*
import com.netflix.spinnaker.front50.model.sql.*
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import java.util.ArrayList

class SqlStorageService(
  private val objectMapper: ObjectMapper,
  private val registry: Registry,
  private val jooq: DSLContext,
  private val clock: Clock,
  private val sqlRetryProperties: SqlRetryProperties
) : StorageService {

  companion object {
    private val log = LoggerFactory.getLogger(SqlStorageService::class.java)

    private val definitionsByType = mutableMapOf(
      PROJECT to ProjectTableDefinition(),
      PIPELINE to PipelineTableDefinition(),
      STRATEGY to PipelineStrategyTableDefinition(),
      PIPELINE_TEMPLATE to DefaultTableDefinition(PIPELINE_TEMPLATE, "pipeline_templates", true),
      NOTIFICATION to DefaultTableDefinition(NOTIFICATION, "notifications", true),
      SERVICE_ACCOUNT to DefaultTableDefinition(SERVICE_ACCOUNT, "service_accounts", true),
      APPLICATION to DefaultTableDefinition(APPLICATION, "applications", true),
      APPLICATION_PERMISSION to DefaultTableDefinition(APPLICATION_PERMISSION, "application_permissions", true),
      SNAPSHOT to DefaultTableDefinition(SNAPSHOT, "snapshots", false),
      ENTITY_TAGS to DefaultTableDefinition(ENTITY_TAGS, "entity_tags", false),
      DELIVERY to DeliveryTableDefinition()
    )
  }

  override fun ensureBucketExists() {
    // no-op
  }

  override fun supportsVersioning(): Boolean {
    return true
  }

  override fun <T : Timestamped> loadObject(objectType: ObjectType, objectKey: String): T {
    val result = jooq.withRetry(sqlRetryProperties.reads) { ctx ->
      ctx
        .select(field("body", String::class.java))
        .from(definitionsByType[objectType]!!.tableName)
        .where(
          field("id", String::class.java).eq(objectKey).and(
            DSL.field("is_deleted", Boolean::class.java).eq(false)
          )
        )
        .fetchOne()
    } ?: throw NotFoundException("Object not found (key: $objectKey)")

    return objectMapper.readValue(result.get(field("body", String::class.java)), objectType.clazz as Class<T>)
  }

  override fun deleteObject(objectType: ObjectType, objectKey: String) {
    jooq.transactional(sqlRetryProperties.transactions) { ctx ->
      if (definitionsByType[objectType]!!.supportsHistory) {
        ctx
          .update(table(definitionsByType[objectType]!!.tableName))
          .set(DSL.field("is_deleted", Boolean::class.java), true)
          .where(DSL.field("id", String::class.java).eq(objectKey))
          .execute()
      } else {
        ctx
          .delete(table(definitionsByType[objectType]!!.tableName))
          .where(DSL.field("id", String::class.java).eq(objectKey))
          .execute()
      }
    }
  }

  override fun <T : Timestamped> storeObject(objectType: ObjectType, objectKey: String, item: T) {
    item.lastModifiedBy = AuthenticatedRequest.getSpinnakerUser().orElse("anonymous")

    try {
      jooq.transactional(sqlRetryProperties.transactions) { ctx ->
        val insert = ctx.insertInto(
          table(definitionsByType[objectType]!!.tableName),
          definitionsByType[objectType]!!.getFields()
        )

        insert.apply {
          values(definitionsByType[objectType]!!.getValues(objectMapper, objectKey, item))

          onDuplicateKeyUpdate()
            .set(field("body"), MySQLDSL.values(field("body")) as Any)
            .set(field("last_modified_at"), MySQLDSL.values(field("last_modified_at")) as Any)
            .set(field("last_modified_by"), MySQLDSL.values(field("last_modified_by")) as Any)
            .set(field("is_deleted"), MySQLDSL.values(field("is_deleted")) as Any)
        }

        insert.execute()

        if (definitionsByType[objectType]!!.supportsHistory) {
          val historicalInsert = ctx.insertInto(
            table(definitionsByType[objectType]!!.historyTableName),
            definitionsByType[objectType]!!.getHistoryFields()
          )

          historicalInsert.apply {
            values(definitionsByType[objectType]!!.getHistoryValues(objectMapper, clock, objectKey, item))

            onDuplicateKeyIgnore()
          }

          historicalInsert.execute()
        }
      }
    } catch (e: Exception) {
      log.error("Unable to store object (objectType: {}, objectKey: {})", objectType, objectKey)
      throw e
    }
  }

  override fun listObjectKeys(objectType: ObjectType): Map<String, Long> {
    val startTime = System.currentTimeMillis()
    val resultSet = jooq.withRetry(sqlRetryProperties.reads) { ctx ->
      ctx
        .select(
          field("id", String::class.java),
          field("last_modified_at", Long::class.java)
        )
        .from(table(definitionsByType[objectType]!!.tableName))
        .where(DSL.field("is_deleted", Boolean::class.java).eq(false))
        .fetch()
        .intoResultSet()
    }

    val objectKeys = mutableMapOf<String, Long>()

    while (resultSet.next()) {
      objectKeys.put(resultSet.getString(1), resultSet.getLong(2))
    }

    log.debug("Took {}ms to fetch {} object keys for {}",
      System.currentTimeMillis() - startTime,
      objectKeys.size,
      objectType
    )

    return objectKeys
  }

  override fun <T : Timestamped> listObjectVersions(objectType: ObjectType,
                                                    objectKey: String,
                                                    maxResults: Int): List<T> {
    if (maxResults == 1) {
      // will throw NotFoundException if object does not exist
      return listOf(loadObject(objectType, objectKey))
    }

    val bodies = jooq.withRetry(sqlRetryProperties.reads) { ctx ->
      if (definitionsByType[objectType]!!.supportsHistory) {
        ctx
          .select(field("body", String::class.java))
          .from(definitionsByType[objectType]!!.historyTableName)
          .where(DSL.field("id", String::class.java).eq(objectKey))
          .orderBy(DSL.field("recorded_at").desc())
          .limit(maxResults)
          .fetch()
          .getValues(field("body", String::class.java))
      } else {
        ctx
          .select(field("body", String::class.java))
          .from(definitionsByType[objectType]!!.tableName)
          .where(DSL.field("id", String::class.java).eq(objectKey))
          .fetch()
          .getValues(field("body", String::class.java))
      }
    }

    return bodies.map {
      objectMapper.readValue(it, objectType.clazz as Class<T>)
    }
  }

  override fun getLastModified(objectType: ObjectType): Long {
    val resultSet = jooq.withRetry(sqlRetryProperties.reads) { ctx ->
      ctx
        .select(max(field("last_modified_at", Long::class.java)).`as`("last_modified_at"))
        .from(table(definitionsByType[objectType]!!.tableName))
        .fetch()
        .intoResultSet()
    }

    return if (resultSet.next()) {
      return resultSet.getLong(1)
    } else {
      0
    }
  }
}
