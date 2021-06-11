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
  private val poolName: String
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
  }

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
              DSL.field("is_deleted", Boolean::class.java).eq(false)
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
                  field("created_at", Long::class.java)
                )
                .from(definitionsByType[objectType]!!.tableName)
                .where(
                  field("id", String::class.java).`in`(keys).and(
                    DSL.field("is_deleted", Boolean::class.java).eq(false)
                  )
                )
                .fetch()
            }
          }

          records.map {
            objectMapper.readValue(
              it.getValue(field("body", String::class.java)),
              objectType.clazz as Class<T>
            ).apply {
              this.createdAt = it.getValue(field("created_at", Long::class.java))
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

  override fun deleteObject(objectType: ObjectType, objectKey: String) {
    withPool(poolName) {
      jooq.transactional(sqlRetryProperties.transactions) { ctx ->
        if (definitionsByType[objectType]!!.supportsHistory) {
          ctx
            .update(table(definitionsByType[objectType]!!.tableName))
            .set(DSL.field("is_deleted", Boolean::class.java), true)
            .set(DSL.field("last_modified_at", Long::class.java), clock.millis())
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
                    *insertPairs.keys.map { DSL.field(it) }.toTypedArray()
                  )
                    .values(insertPairs.values)
                    .onConflict(DSL.field("id", String::class.java))
                    .doUpdate()
                    .set(updatePairs.mapKeys { DSL.field(it.key) })
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
                        *historyPairs.keys.map { DSL.field(it) }.toTypedArray()
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
                *insertPairs.keys.map { DSL.field(it) }.toTypedArray()
              )
              .values(insertPairs.values)
              .onConflict(DSL.field("id", String::class.java))
              .doUpdate()
              .set(updatePairs.mapKeys { DSL.field(it.key) })
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
                  *historyPairs.keys.map { DSL.field(it) }.toTypedArray()
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
          .where(DSL.field("is_deleted", Boolean::class.java).eq(false))
          .fetch()
          .intoResultSet()
      }
    }

    val objectKeys = mutableMapOf<String, Long>()

    while (resultSet.next()) {
      objectKeys.put(resultSet.getString(1), resultSet.getLong(2))
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

    val bodies = withPool(poolName) {
      jooq.withRetry(sqlRetryProperties.reads) { ctx ->
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
    }

    return bodies.map {
      objectMapper.readValue(it, objectType.clazz as Class<T>)
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
          .set(DSL.field("is_deleted", Boolean::class.java), false)
          .set(DSL.field("last_modified_at", Long::class.java), clock.millis())
          .where(DSL.field("id", String::class.java).eq(operation.objectId.toLowerCase()))
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
            updatePairs.forEach { k, v ->
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
            *insertPairs.keys.map { DSL.field(it) }.toTypedArray()
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
            *historyPairs.keys.map { DSL.field(it) }.toTypedArray()
          )
          .values(historyPairs.values)
          .execute()
      }
    }
  }
}
