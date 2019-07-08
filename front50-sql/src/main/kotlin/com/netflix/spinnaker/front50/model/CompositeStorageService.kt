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

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.front50.exception.NotFoundException
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

class CompositeStorageService(
  private val dynamicConfigService: DynamicConfigService,
  private val registry: Registry,
  private val primary: StorageService,
  private val previous: StorageService
) : StorageService {

  companion object {
    private val log = LoggerFactory.getLogger(CompositeStorageService::class.java)
  }

  var primaryReadStatusGauge = registry.gauge(
    registry.createId("compositeStorageService.read")
      .withTag("type", "primary")
      .withTag("class", primary.javaClass.simpleName)
  )
  var previousReadStatusGauge = registry.gauge(
    registry.createId("compositeStorageService.read")
      .withTag("type", "previous")
      .withTag("class", previous.javaClass.simpleName)
  )

  override fun supportsEventing(objectType: ObjectType): Boolean {
    if (!isPrimaryReadEnabled()) {
      return true
    }

    return objectType == ObjectType.ENTITY_TAGS
  }

  @Scheduled(fixedDelay = 60000L)
  fun status() {
    log.debug(
      "Composite Storage Service Read Status ({}: {}, {}: {})",
      primary.javaClass.simpleName,
      isPrimaryReadEnabled(),
      previous.javaClass.simpleName,
      isPreviousReadEnabled()
    )

    primaryReadStatusGauge.set(if (isPrimaryReadEnabled()) 1.0 else 0.0)
    previousReadStatusGauge.set(if (isPreviousReadEnabled()) 1.0 else 0.0)
  }

  override fun ensureBucketExists() {
    primary.ensureBucketExists()
    previous.ensureBucketExists()
  }

  override fun supportsVersioning(): Boolean {
    return primary.supportsVersioning()
  }

  override fun <T : Timestamped?> loadObject(objectType: ObjectType?, objectKey: String?): T {
    var exception: Exception? = null

    if (isPrimaryReadEnabled()) {
      try {
        return primary.loadObject<T>(objectType, objectKey)
      } catch (e: NotFoundException) {
        log.debug("{}.loadObject({}, {}) not found (primary)", primary.javaClass.simpleName, objectType, objectKey)

        exception = e
      } catch (e: Exception) {
        log.error("{}.loadObject({}, {}) failed (primary)", primary.javaClass.simpleName, objectType, objectKey)

        exception = e
      }
    }

    return when {
      isPreviousReadEnabled() -> previous.loadObject<T>(objectType, objectKey)
      exception != null -> throw exception
      else -> throw IllegalStateException("Primary and previous storage services are disabled")
    }
  }

  override fun <T : Timestamped?> loadObjects(objectType: ObjectType, objectKeys: List<String>): List<T> {
    var exception: Exception? = null

    if (isPrimaryReadEnabled()) {
      try {
        return primary.loadObjects<T>(objectType, objectKeys)
      } catch (e: Exception) {
        log.error("{}.loadObjects({}) failed (primary)", primary.javaClass.simpleName, objectType)

        exception = e
      }
    }

    return when {
      isPreviousReadEnabled() -> previous.loadObjects<T>(objectType, objectKeys)
      exception != null -> throw exception
      else -> throw IllegalStateException("Primary and previous storage services are disabled")
    }
  }

  override fun deleteObject(objectType: ObjectType?, objectKey: String?) {
    primary.deleteObject(objectType, objectKey)
    previous.deleteObject(objectType, objectKey)
  }

  override fun <T : Timestamped?> storeObject(objectType: ObjectType?, objectKey: String?, item: T) {
    try {
      /*
       * Ensure that writes are first successful against the current source of truth (aka 'previous').
       *
       * The migration process (StorageServiceMigrator) is capable of detecting and migrating any records
       * that failed the subsequent 'primary' write.
       */
      previous.storeObject(objectType, objectKey, item)
    } catch (e: Exception) {
      log.error(
        "{}.storeObject({}, {}) failed",
        previous.javaClass.simpleName,
        objectType,
        objectKey,
        e
      )

      throw e
    }

    try {
      primary.storeObject(objectType, objectKey, item)
    } catch (e: Exception) {
      log.error(
        "{}.storeObject({}, {}) failed",
        primary.javaClass.simpleName,
        objectType,
        objectKey,
        e
      )

      throw e
    }
  }

  override fun listObjectKeys(objectType: ObjectType?): Map<String, Long> {
    val objectKeys = mutableMapOf<String, Long>()

    if (isPreviousReadEnabled()) {
      objectKeys.putAll(previous.listObjectKeys(objectType))
    }

    if (isPrimaryReadEnabled()) {
      objectKeys.putAll(primary.listObjectKeys(objectType))
    }

    return objectKeys
  }

  override fun <T : Timestamped?> listObjectVersions(
    objectType: ObjectType?,
    objectKey: String?,
    maxResults: Int
  ): MutableCollection<T> {
    var exception: Exception? = null

    if (isPrimaryReadEnabled()) {
      try {
        return primary.listObjectVersions(objectType, objectKey, maxResults)
      } catch (e: NotFoundException) {
        log.debug("{}.listObjectVersions({}, {}, {}) not found (primary)",
          primary.javaClass.simpleName,
          objectType,
          objectKey,
          maxResults
        )

        exception = e
      } catch (e: Exception) {
        log.error(
          "{}.listObjectVersions({}, {}, {}) failed (primary)",
          primary.javaClass.simpleName,
          objectType,
          objectKey,
          maxResults
        )

        exception = e
      }
    }

    return when {
      isPreviousReadEnabled() -> return previous.listObjectVersions(objectType, objectKey, maxResults)
      exception != null -> throw exception
      else -> mutableListOf()
    }
  }

  override fun getLastModified(objectType: ObjectType?): Long {
    var exception: Exception? = null

    if (isPrimaryReadEnabled()) {
      try {
        return primary.getLastModified(objectType)
      } catch (e: Exception) {
        log.error("{}.getLastModified({}) failed (primary)", primary.javaClass.simpleName, objectType)

        exception = e
      }
    }

    return when {
      isPreviousReadEnabled() -> previous.getLastModified(objectType)
      exception != null -> throw exception
      else -> throw IllegalStateException("Primary and previous storage services are disabled")
    }
  }

  private fun isPrimaryReadEnabled() = isReadEnabled("primary")

  private fun isPreviousReadEnabled() = isReadEnabled("previous")

  private fun isReadEnabled(type: String) =
    dynamicConfigService.getConfig(
      Boolean::class.java,
      "spinnaker.migration.compositeStorageService.reads.$type",
      false
    )
}
