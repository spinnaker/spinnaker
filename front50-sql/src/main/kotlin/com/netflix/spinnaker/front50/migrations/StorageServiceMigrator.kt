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

package com.netflix.spinnaker.front50.migrations

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.front50.model.ObjectType
import com.netflix.spinnaker.front50.model.StorageService
import com.netflix.spinnaker.front50.model.Timestamped
import com.netflix.spinnaker.security.AuthenticatedRequest
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.scheduling.annotation.Scheduled
import kotlin.system.measureTimeMillis

class StorageServiceMigrator(
  private val registry: Registry,
  private val target: StorageService,
  private val source: StorageService
) {

  companion object {
    private val log = LoggerFactory.getLogger(StorageServiceMigrator::class.java)
  }

  var migratorObjectsId = registry.createId("storageServiceMigrator.objects")

  fun migrate(objectType: ObjectType) {
    log.info("Migrating {}", objectType)

    val sourceObjectKeys = source.listObjectKeys(objectType)
    val targetObjectKeys = target.listObjectKeys(objectType)

    val deletableObjectKeys = targetObjectKeys.filter { e ->
      !sourceObjectKeys.containsKey(e.key)
    }

    if (!deletableObjectKeys.isEmpty()) {
      /*
       * Handle a situation where deletes can still happen directly against the source/previous storage service.
       *
       * In these cases, the delete should also be reflected in the primary/target storage service.
       */
      log.info(
        "Found orphaned objects in {} (keys: {})",
        source.javaClass.simpleName,
        deletableObjectKeys.keys.joinToString(", ")
      )

      deletableObjectKeys.keys.forEach {
        target.deleteObject(objectType, it)
      }

      log.info(
        "Deleted orphaned objects from {} (keys: {})",
        target.javaClass.simpleName,
        deletableObjectKeys.keys.joinToString(", ")
      )
    }

    val migratableObjectKeys = sourceObjectKeys.filter { e ->
      /*
       * A migratable object is one that:
       * - does not exist in 'target'
       * or
       * - has been more recently modified in 'source'
       */
      !targetObjectKeys.containsKey(e.key) || targetObjectKeys[e.key]!! < e.value
    }

    if (migratableObjectKeys.isEmpty()) {
      log.info(
        "No objects to migrate (objectType: {}, sourceObjectCount: {}, targetObjectCount: {})",
        objectType,
        sourceObjectKeys.size,
        targetObjectKeys.size
      )

      return
    }

    val deferred = migratableObjectKeys.keys.map { key ->
      GlobalScope.async {
        try {
          val maxObjectVersions = if (objectType == ObjectType.ENTITY_TAGS) {
            // current thinking is that ENTITY_TAGS will be separately migrated due to their volume (10-100k+)
            1
          } else {
            // the history api defaults to returning 20 records so its arguably unnecessary to migrate much more than that
            30
          }

          val objectVersions = mutableListOf<Timestamped>()

          try {
            objectVersions.addAll(source.listObjectVersions<Timestamped>(objectType, key, maxObjectVersions))
          } catch (e: Exception) {
            log.warn(
              "Unable to list object versions (objectType: {}, objectKey: {}), reason: {}",
              objectType,
              key,
              e.message
            )

            // we have a number of objects in our production bucket with broken permissions that prevent version lookups
            // but can be fetched directly w/o versions
            objectVersions.add(source.loadObject(objectType, key))
          }

          objectVersions.reversed().forEach { obj ->
            try {
              MDC.put(AuthenticatedRequest.Header.USER.header, obj.lastModifiedBy)
              target.storeObject(objectType, key, obj)
              registry.counter(
                migratorObjectsId.withTag("objectType", objectType.name).withTag("success", true)
              ).increment()
            } catch (e: Exception) {
              registry.counter(
                migratorObjectsId.withTag("objectType", objectType.name).withTag("success", false)
              ).increment()

              throw e
            } finally {
              MDC.remove(AuthenticatedRequest.Header.USER.header)
            }
          }
        } catch (e: Exception) {
          log.error("Unable to migrate (objectType: {}, objectKey: {})", objectType, key, e)
        }
      }
    }

    val migrationDurationMs = measureTimeMillis {
      runBlocking {
        deferred.awaitAll()
      }
    }

    log.info(
      "Migration of {} took {}ms (objectCount: {})",
      objectType,
      migrationDurationMs,
      migratableObjectKeys.size
    )
  }

  @Scheduled(fixedDelay = 60000)
  fun migrate() {
    val migrationDurationMs = measureTimeMillis {
      ObjectType.values().forEach {
        if (it != ObjectType.ENTITY_TAGS) {
          // need an alternative/more performant strategy for entity tags
          migrate(it)
        }
      }
    }

    log.info("Migration complete in {}ms", migrationDurationMs)
  }
}
