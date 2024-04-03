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

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Id
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.config.Front50SqlProperties
import com.netflix.spinnaker.front50.api.model.Timestamped
import com.netflix.spinnaker.front50.model.ObjectType.APPLICATION
import com.netflix.spinnaker.front50.model.ObjectType.APPLICATION_PERMISSION
import com.netflix.spinnaker.front50.model.ObjectType.DELIVERY
import com.netflix.spinnaker.front50.model.ObjectType.ENTITY_TAGS
import com.netflix.spinnaker.front50.model.ObjectType.NOTIFICATION
import com.netflix.spinnaker.front50.model.ObjectType.PIPELINE
import com.netflix.spinnaker.front50.model.ObjectType.PIPELINE_TEMPLATE
import com.netflix.spinnaker.front50.model.ObjectType.PLUGIN_INFO
import com.netflix.spinnaker.front50.model.ObjectType.PLUGIN_VERSIONS
import com.netflix.spinnaker.front50.model.ObjectType.PROJECT
import com.netflix.spinnaker.front50.model.ObjectType.SERVICE_ACCOUNT
import com.netflix.spinnaker.front50.model.ObjectType.SNAPSHOT
import com.netflix.spinnaker.front50.model.ObjectType.STRATEGY
import com.netflix.spinnaker.front50.model.sql.DefaultTableDefinition
import com.netflix.spinnaker.front50.model.sql.DeliveryTableDefinition
import com.netflix.spinnaker.front50.model.sql.PipelineStrategyTableDefinition
import com.netflix.spinnaker.front50.model.sql.PipelineTableDefinition
import com.netflix.spinnaker.front50.model.sql.ProjectTableDefinition
import com.netflix.spinnaker.front50.model.sql.transactional
import com.netflix.spinnaker.front50.model.sql.withRetry
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import com.netflix.spinnaker.kork.sql.routing.withPool
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import com.netflix.spinnaker.security.AuthenticatedRequest
import java.time.Clock
import kotlin.system.measureTimeMillis
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.Result
import org.jooq.SelectFieldOrAsterisk
import org.jooq.exception.SQLDialectNotSupportedException
import org.jooq.impl.DSL
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.max
import org.jooq.impl.DSL.table
import org.slf4j.LoggerFactory

class SqlStorageService(
  private val objectMapper: ObjectMapper,
  private val registry: Registry,
  private val jooq: DSLContext,
  private val clock: Clock,
  private val sqlRetryProperties: SqlRetryProperties,
  private val chunkSize: Int,
  private val poolName: String,
  private val front50SqlProperties: Front50SqlProperties
) : StorageService, BulkStorageService, AdminOperations {

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
      DELIVERY to DeliveryTableDefinition(),
      PLUGIN_INFO to DefaultTableDefinition(PLUGIN_INFO, "plugin_info", false),
      PLUGIN_VERSIONS to DefaultTableDefinition(PLUGIN_VERSIONS, "plugin_versions", false)
    )

    private val bodyField = field("body", String::class.java)
    private val lastModifiedField = field("last_modified_at", Long::class.java)
  }

  private val invalidJsonCounterId: Id = registry.createId("sqlStorageService.invalidJson");

  override fun supportsVersioning(): Boolean {
    return true
  }

  override fun <T : Timestamped> loadObject(objectType: ObjectType, objectKey: String): T {
    val result = withPool(poolName) {
      jooq.withRetry(sqlRetryProperties.reads) { ctx ->
        ctx
          .select(
            field("body", String::class.java),
            field("created_at", Long::class.java)
          )
          .from(definitionsByType[objectType]!!.tableName)
          .where(
            field("id", String::class.java).eq(objectKey).and(
              field("is_deleted", Boolean::class.java).eq(false)
            )
          )
          .fetchOne()
      } ?: throw NotFoundException("Object not found (key: $objectKey)")
    }

    return objectMapper.readValue(
      result.get(field("body", String::class.java)),
      objectType.clazz as Class<T>
    ).apply {
      this.createdAt = result.get(field("created_at", Long::class.java))
    }
  }

  override fun <T : Timestamped> loadObjects(objectType: ObjectType, objectKeys: List<String>): List<T> {
    val objects = mutableListOf<T>()

    val timeToLoadObjects = measureTimeMillis {
      objects.addAll(
        objectKeys.chunked(chunkSize).flatMap { keys ->
          val records = withPool(poolName) {
            jooq.withRetry(sqlRetryProperties.reads) { ctx ->
              ctx
                .select(
                  field("body", String::class.java),
                  field("created_at", Long::class.java),
                  field("last_modified_at", Long::class.java)
                )
                .from(definitionsByType[objectType]!!.tableName)
                .where(
                  field("id", String::class.java).`in`(keys).and(
                    field("is_deleted", Boolean::class.java).eq(false)
                  )
                )
                .fetch()
            }
          }

          records.mapNotNull {
            val body = it.getValue(field("body", String::class.java))
            try  {
              objectMapper.readValue(body, objectType.clazz as Class<T>
              ).apply {
                this.createdAt = it.getValue(field("created_at", Long::class.java))
                this.lastModified = it.getValue(field("last_modified_at", Long::class.java))
              }
            } catch (e: JsonProcessingException) {
              log.error("unable to deserialize {}", objectType.name, e)
              registry.counter(invalidJsonCounterId.withTag("objectType", objectType.group)).increment();
              null
            }
          }
        }
      )
    }

    log.debug(
      "Took {}ms to fetch {} objects for {}",
      timeToLoadObjects,
      objects.size,
      objectType
    )

    return objects
  }

  override fun <T : Timestamped> loadObjectsNewerThan(
    objectType: ObjectType,
    lastModifiedThreshold: Long
  ):
    Map<String, List<T>> {

    log.debug("Fetching {} objects with last_modified_at value greater than {}",
      objectType.name,
      lastModifiedThreshold)

    val deletedKey = "deleted"
    val notDeletedKey = "not_deleted"

    val resultMap = mapOf<String, MutableList<T>>(
      deletedKey to mutableListOf(),
      notDeletedKey to mutableListOf()
    )

    val tableSupportsSoftDeletes = definitionsByType[objectType]!!.supportsHistory

    val fieldsToFetch = mutableListOf<SelectFieldOrAsterisk>(field("body", String::class.java))
    if (tableSupportsSoftDeletes) {
      fieldsToFetch.add(field("is_deleted", Boolean::class.java))
    }

    val timeToLoadObjects = measureTimeMillis {
      val result: Result<Record> = jooq.withRetry(sqlRetryProperties.reads) { ctx ->
        ctx
          .select(fieldsToFetch)
          .from(definitionsByType[objectType]!!.tableName)
          .where(field("last_modified_at", Long::class.java).greaterThan(lastModifiedThreshold))
          .fetch()
      }
      for (record in result) {
        val insertInto = if (tableSupportsSoftDeletes && record.get("is_deleted", Boolean::class.java)) {
          deletedKey
        } else {
          notDeletedKey
        }
        val bodyString = record.get("body", String::class.java)
        try {
          val thisObject = objectMapper.readValue(bodyString, objectType.clazz as Class<T>)
          resultMap[insertInto]!!.add(thisObject)
        } catch (e: JsonProcessingException) {
          log.error("unable to deserialize {}", objectType.name, e)
          registry.counter(invalidJsonCounterId.withTag("objectType", objectType.group)).increment();
        }
      }
    }

    log.debug("Took {}ms to fetch {} {} objects with last_modified_at value greater than {}",
      timeToLoadObjects,
      resultMap[deletedKey]!!.size + resultMap[notDeletedKey]!!.size,
      objectType,
      lastModifiedThreshold
    )

    return resultMap
  }

  override fun deleteObject(objectType: ObjectType, objectKey: String) {
    withPool(poolName) {
      jooq.transactional(sqlRetryProperties.transactions) { ctx ->
        if (definitionsByType[objectType]!!.supportsHistory) {
          ctx
            .update(table(definitionsByType[objectType]!!.tableName))
            .set(field("is_deleted", Boolean::class.java), true)
            .set(field("last_modified_at", Long::class.java), clock.millis())
            .where(field("id", String::class.java).eq(objectKey))
            .execute()
        } else {
          ctx
            .delete(table(definitionsByType[objectType]!!.tableName))
            .where(field("id", String::class.java).eq(objectKey))
            .execute()
        }
      }
    }
  }

  override fun <T : Timestamped> storeObjects(objectType: ObjectType, allItems: Collection<T>) {
    // using a lower `chunkSize` to avoid exceeding default packet size limits.
    allItems.chunked(100).forEach { items ->
      try {
        withPool(poolName) {
          jooq.transactional(sqlRetryProperties.transactions) { ctx ->
            try {
              ctx.batch(
                items.map { item ->
                  val insertPairs = definitionsByType[objectType]!!.getInsertPairs(
                    objectMapper, item.id.toLowerCase(), item
                  )
                  val updatePairs = definitionsByType[objectType]!!.getUpdatePairs(insertPairs)

                  ctx.insertInto(
                    table(definitionsByType[objectType]!!.tableName),
                    *insertPairs.keys.map { field(it) }.toTypedArray()
                  )
                    .values(insertPairs.values)
                    .onConflict(field("id", String::class.java))
                    .doUpdate()
                    .set(updatePairs.mapKeys { field(it.key) })
                }
              ).execute()
            } catch (e: SQLDialectNotSupportedException) {
              for (item in items) {
                storeSingleObject(objectType, item.id.toLowerCase(), item)
              }
            }

            if (definitionsByType[objectType]!!.supportsHistory) {
              try {
                ctx.batch(
                  items.map { item ->
                    val historyPairs = definitionsByType[objectType]!!.getHistoryPairs(
                      objectMapper, clock, item.id.toLowerCase(), item
                    )

                    ctx
                      .insertInto(
                        table(definitionsByType[objectType]!!.historyTableName),
                        *historyPairs.keys.map { field(it) }.toTypedArray()
                      )
                      .values(historyPairs.values)
                      .onDuplicateKeyIgnore()
                  }
                ).execute()
              } catch (e: SQLDialectNotSupportedException) {
                for (item in items) {
                  storeSingleObjectHistory(objectType, item.id.toLowerCase(), item)
                }
              }
            }
          }
        }
      } catch (e: Exception) {
        log.error("Unable to store objects (objectType: {}, objectKeys: {})", objectType, items.map { it.id })
        throw e
      }
    }
  }

  override fun <T : Timestamped> storeObject(objectType: ObjectType, objectKey: String, item: T) {
    item.lastModifiedBy = AuthenticatedRequest.getSpinnakerUser().orElse("anonymous")

    try {
      withPool(poolName) {
        jooq.transactional(sqlRetryProperties.transactions) { ctx ->
          val insertPairs = definitionsByType[objectType]!!.getInsertPairs(objectMapper, objectKey, item)
          val updatePairs = definitionsByType[objectType]!!.getUpdatePairs(insertPairs)

          try {
            ctx
              .insertInto(
                table(definitionsByType[objectType]!!.tableName),
                *insertPairs.keys.map { field(it) }.toTypedArray()
              )
              .values(insertPairs.values)
              .onConflict(field("id", String::class.java))
              .doUpdate()
              .set(updatePairs.mapKeys { field(it.key) })
              .execute()
          } catch (e: SQLDialectNotSupportedException) {
            storeSingleObject(objectType, objectKey, item)
          }

          if (definitionsByType[objectType]!!.supportsHistory) {
            val historyPairs = definitionsByType[objectType]!!.getHistoryPairs(objectMapper, clock, objectKey, item)

            try {
              ctx
                .insertInto(
                  table(definitionsByType[objectType]!!.historyTableName),
                  *historyPairs.keys.map { field(it) }.toTypedArray()
                )
                .values(historyPairs.values)
                .onDuplicateKeyIgnore()
                .execute()
            } catch (e: SQLDialectNotSupportedException) {
              storeSingleObjectHistory(objectType, objectKey, item)
            }
          }
        }
      }
    } catch (e: Exception) {
      log.error("Unable to store object (objectType: {}, objectKey: {})", objectType, objectKey, e)
      throw e
    }
  }

  override fun listObjectKeys(objectType: ObjectType): Map<String, Long> {
    val startTime = System.currentTimeMillis()
    val resultSet = withPool(poolName) {
      jooq.withRetry(sqlRetryProperties.reads) { ctx ->
        ctx
          .select(
            field("id", String::class.java),
            field("last_modified_at", Long::class.java)
          )
          .from(table(definitionsByType[objectType]!!.tableName))
          .where(field("is_deleted", Boolean::class.java).eq(false))
          .fetch()
          .intoResultSet()
      }
    }

    val objectKeys = mutableMapOf<String, Long>()

    while (resultSet.next()) {
      objectKeys[resultSet.getString(1)] = resultSet.getLong(2)
    }

    log.debug(
      "Took {}ms to fetch {} object keys for {}",
      System.currentTimeMillis() - startTime,
      objectKeys.size,
      objectType
    )

    return objectKeys
  }

  override fun <T : Timestamped> listObjectVersions(
    objectType: ObjectType,
    objectKey: String,
    maxResults: Int
  ): List<T> {
    if (maxResults == 1) {
      // will throw NotFoundException if object does not exist
      return mutableListOf(loadObject(objectType, objectKey))
    }

    val result = withPool(poolName) {
      jooq.withRetry(sqlRetryProperties.reads) { ctx ->
        if (definitionsByType[objectType]!!.supportsHistory) {
          ctx
            .select(bodyField, lastModifiedField)
            .from(definitionsByType[objectType]!!.historyTableName)
            .where(field("id", String::class.java).eq(objectKey))
            .orderBy(field("recorded_at").desc())
            .limit(maxResults)
            .fetch()
        } else {
          ctx
            .select(bodyField, lastModifiedField)
            .from(definitionsByType[objectType]!!.tableName)
            .where(field("id", String::class.java).eq(objectKey))
            .fetch()
        }
      }
    }

    return result.map {
      val record = objectMapper.readValue(it.get(bodyField), objectType.clazz as Class<T>)
      record.lastModified = it.get(lastModifiedField)
      record
    }
  }

  override fun getLastModified(objectType: ObjectType): Long {
    val resultSet = withPool(poolName) {
      jooq.withRetry(sqlRetryProperties.reads) { ctx ->
        ctx
          .select(max(field("last_modified_at", Long::class.java)).`as`("last_modified_at"))
          .from(table(definitionsByType[objectType]!!.tableName))
          .fetch()
          .intoResultSet()
      }
    }

    return if (resultSet.next()) {
      return resultSet.getLong(1)
    } else {
      0
    }
  }

  override fun recover(operation: AdminOperations.Recover) {
    val objectType = ObjectType.values().find {
      it.clazz.simpleName.equals(operation.objectType, true)
    } ?: throw NotFoundException("Object type ${operation.objectType} is unsupported")

    withPool(poolName) {
      jooq.transactional(sqlRetryProperties.transactions) { ctx ->
        val updatedCount = ctx
          .update(table(definitionsByType[objectType]!!.tableName))
          .set(field("is_deleted", Boolean::class.java), false)
          .set(field("last_modified_at", Long::class.java), clock.millis())
          .where(field("id", String::class.java).eq(operation.objectId.toLowerCase()))
          .execute()

        if (updatedCount == 0) {
          throw NotFoundException("Object ${operation.objectType}:${operation.objectId} was not found")
        }
      }
    }
    log.info("Object ${operation.objectType}:${operation.objectId} was recovered")
  }

  private fun storeSingleObject(objectType: ObjectType, objectKey: String, item: Timestamped) {
    val insertPairs = definitionsByType[objectType]!!.getInsertPairs(objectMapper, objectKey, item)
    val updatePairs = definitionsByType[objectType]!!.getUpdatePairs(insertPairs)

    val exists = jooq.withRetry(sqlRetryProperties.reads) {
      jooq.fetchExists(
        jooq.select()
          .from(definitionsByType[objectType]!!.tableName)
          .where(field("id").eq(objectKey).and(field("is_deleted").eq(false)))
          .forUpdate()
      )
    }

    if (exists) {
      jooq.withRetry(sqlRetryProperties.transactions) {
        jooq
          .update(table(definitionsByType[objectType]!!.tableName)).apply {
            updatePairs.forEach { (k, v) ->
              set(field(k), v)
            }
          }
          .set(field("id"), objectKey) // satisfy jooq fluent interface
          .where(field("id").eq(objectKey))
          .execute()
      }
    } else {
      jooq.withRetry(sqlRetryProperties.transactions) {
        jooq
          .insertInto(
            table(definitionsByType[objectType]!!.tableName),
            *insertPairs.keys.map { field(it) }.toTypedArray()
          )
          .values(insertPairs.values)
          .execute()
      }
    }
  }

  private fun storeSingleObjectHistory(objectType: ObjectType, objectKey: String, item: Timestamped) {
    val historyPairs = definitionsByType[objectType]!!.getHistoryPairs(objectMapper, clock, objectKey, item)

    val exists = jooq.withRetry(sqlRetryProperties.reads) {
      jooq.fetchExists(
        jooq.select()
          .from(definitionsByType[objectType]!!.historyTableName)
          .where(field("id").eq(objectKey).and(field("body_sig").eq(historyPairs.getValue("body_sig"))))
          .forUpdate()
      )
    }

    if (!exists) {
      jooq.withRetry(sqlRetryProperties.transactions) {
        jooq
          .insertInto(
            table(definitionsByType[objectType]!!.historyTableName),
            *historyPairs.keys.map { field(it) }.toTypedArray()
          )
          .values(historyPairs.values)
          .execute()
      }
    }
  }

  override fun getHealthIntervalMillis(): Long {
    return front50SqlProperties.healthIntervalMillis
  }
}
