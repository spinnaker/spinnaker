/*
 * Copyright 2020 Google, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.front50.model

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.cloud.storage.Blob
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.BucketInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.Storage.BlobListOption
import com.google.cloud.storage.Storage.BucketField
import com.google.cloud.storage.Storage.BucketGetOption
import com.google.cloud.storage.StorageException
import com.google.common.collect.ImmutableMap
import com.google.common.util.concurrent.Futures
import com.netflix.spinnaker.front50.api.model.Timestamped
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import java.io.IOException
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.annotation.PostConstruct
import net.logstash.logback.argument.StructuredArguments
import org.slf4j.LoggerFactory

class GcsStorageService(
  private val storage: Storage,
  private val bucketName: String,
  private val bucketLocation: String,
  private val basePath: String,
  private val dataFilename: String,
  private val objectMapper: ObjectMapper,
  private val executor: ExecutorService
) : StorageService {

  companion object {
    private val log = LoggerFactory.getLogger(GcsStorageService::class.java)
    private const val LAST_MODIFIED_FILENAME = "last-modified"
    private val WAIT_FOR_TIMESTAMP_UPDATE = Duration.ofMillis(500)
  }

  private val modTimeState = ObjectType.values().map { it to ModificationTimeState(it) }.toMap()

  @PostConstruct
  fun ensureBucketExists() {
    var bucket: BucketInfo? = storage.get(bucketName)
    if (bucket == null) {
      val bucketInfo = BucketInfo.newBuilder(bucketName)
        .setVersioningEnabled(true)
      if (bucketLocation.isNotBlank()) {
        bucketInfo.setLocation(bucketLocation)
      }
      bucket = bucketInfo.build()
      storage.create(bucketInfo.build())
    }

    log.info(
      "Bucket versioning is {}.",
      StructuredArguments.value("versioning", if (supportsVersioning(bucket)) "enabled" else "DISABLED")
    )
  }

  override fun supportsVersioning(): Boolean {
    val bucket = storage.get(bucketName, BucketGetOption.fields(BucketField.VERSIONING))
    return supportsVersioning(bucket)
  }

  fun supportsVersioning(bucket: BucketInfo?): Boolean {
    return bucket?.versioningEnabled() == true
  }

  override fun <T : Timestamped> loadObject(objectType: ObjectType, objectKey: String): T {
    try {
      val blobId = blobIdForKey(objectType, objectKey)
      val blob = storage.get(blobId)
        ?: throw NotFoundException("Couldn't retrieve $objectType $objectKey from GCS")
      val bytes = storage.readAllBytes(blob.blobId)
      val obj: T = parseObject(bytes, objectType, objectKey)
      obj.lastModified = blob.updateTime
      return obj
    } catch (e: Exception) {
      throw wrapException("error loading $objectType $objectKey", e)
    }
  }

  override fun deleteObject(objectType: ObjectType, objectKey: String) {
    try {
      if (storage.delete(blobIdForKey(objectType, objectKey))) {
        writeLastModified(objectType)
      }
    } catch (e: Exception) {
      throw wrapException("error deleting $objectType $objectKey", e)
    }
  }

  override fun <T : Timestamped?> storeObject(objectType: ObjectType, objectKey: String, item: T) {
    val blobId = blobIdForKey(objectType, objectKey)
    try {
      val bytes = objectMapper.writeValueAsBytes(item)
      storage.create(BlobInfo.newBuilder(blobId).setContentType("application/json").build(), bytes)
      writeLastModified(objectType)
    } catch (e: Exception) {
      throw wrapException("Error writing $objectType $objectKey", e)
    }
  }

  override fun listObjectKeys(objectType: ObjectType): Map<String, Long> {
    val results = ImmutableMap.builder<String, Long>()

    try {
      val rootDirectory = daoRoot(objectType)
      storage.list(bucketName, BlobListOption.prefix("$rootDirectory/"))
        .iterateAll()
        .forEach { blob ->
          val objectKey = getObjectKey(blob, rootDirectory)
          if (objectKey != null) {
            results.put(objectKey, blob.updateTime)
          }
        }
    } catch (e: Exception) {
      throw wrapException("error listing $objectType objects", e)
    }

    return results.build()
  }

  private fun getObjectKey(blob: Blob, rootDirectory: String): String? {
    val name = blob.name
    return if (!name.startsWith("$rootDirectory/") || !name.endsWith("/$dataFilename")) {
      null
    } else name.substring(rootDirectory.length + 1, name.length - dataFilename.length - 1)
  }

  override fun <T : Timestamped> listObjectVersions(
    objectType: ObjectType,
    objectKey: String,
    maxResults: Int
  ): Collection<T> {

    try {
      val path = pathForKey(objectType, objectKey)
      val listResults = storage.list(bucketName, BlobListOption.prefix(path), BlobListOption.versions(true))
      return listResults.iterateAll()
        .filter { blob: Blob -> blob.name == path }
        .sortedBy { it.updateTime }
        .reversed()
        .take(maxResults)
        .map { blob: Blob ->
          parseObject<T>(blob.getContent(), objectType, objectKey)
            .apply { lastModified = blob.updateTime }
        }
    } catch (e: Exception) {
      throw wrapException("error loading $objectType $objectKey", e)
    }
  }

  override fun getLastModified(objectType: ObjectType): Long {
    try {
      val blob = storage.get(lastModifiedBlobId(objectType))
      if (blob == null) {
        writeLastModified(objectType)
        return 0
      } else {
        return blob.updateTime
      }
    } catch (e: Exception) {
      throw wrapException("error loading timestamp for $objectType objects", e)
    }
  }

  private fun writeLastModified(objectType: ObjectType) {
    val updaterState = modTimeState.getValue(objectType)
    val future = updaterState.updateNeeded()
    try {
      future.get(WAIT_FOR_TIMESTAMP_UPDATE.toMillis(), TimeUnit.MILLISECONDS)
    } catch (e: TimeoutException) {
      // since we didn't update the modified time for objectType, a call to the cache may not notice
      // the modification we just made. It'll fix itself whenever the update thread finishes.
    }
  }

  private fun updateLastModified(objectType: ObjectType, state: ModificationTimeState) {
    state.updateTaskStarted()
    val blobInfo = BlobInfo.newBuilder(lastModifiedBlobId(objectType)).build()
    try {
      // Calling update() is enough to change the modification time on the file, which is all we
      // care about. It doesn't matter if we don't actually specify any fields to change.
      storage.update(blobInfo)
    } catch (e: Exception) {
      when {
        e is StorageException && e.code == 404 ->
          try {
            storage.create(blobInfo)
          } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
          } catch (e: Exception) {
            log.warn("Error updating last modified time for $objectType", e)
          }
        else -> log.warn("Error updating last modified time for $objectType", e)
      }
    } finally {
      state.updateTaskFinished()
    }
  }

  private fun scheduleUpdateLastModified(objectType: ObjectType, updaterState: ModificationTimeState): Future<*> {
    return executor.submit { updateLastModified(objectType, updaterState) }
  }

  private inner class ModificationTimeState(private var objectType: ObjectType) {

    private var needsUpdate = false
    private var updateRunning = false

    /**
     * Specify that an object has been modified so the modification time for this objectType needs
     * to be updated.
     */
    @Synchronized
    fun updateNeeded(): Future<*> {
      val neededUpdate = needsUpdate
      needsUpdate = true
      // We need to check `neededUpdate` or else several near-simultaneous calls would schedule
      // multiple executions of the task before any of them have a chance to get up and running
      if (!neededUpdate && !updateRunning) {
        return scheduleUpdateLastModified(objectType, this)
      } else {
        return Futures.immediateFuture(null)
      }
    }

    /** Should be called by the update task to indicate it has started. */
    @Synchronized
    fun updateTaskStarted() {
      updateRunning = true
      needsUpdate = false
    }

    /** Should be called by the update task to indicate it has completed. */
    @Synchronized
    fun updateTaskFinished() {
      updateRunning = false
      // If another update request came in while the task was running, we need to launch the task
      // again.
      if (needsUpdate) {
        scheduleUpdateLastModified(objectType, this)
      }
    }
  }

  private fun <T : Timestamped> parseObject(bytes: ByteArray, objectType: ObjectType, objectKey: String): T {
    return try {
      @Suppress("UNCHECKED_CAST")
      objectMapper.readValue(bytes, objectType.clazz as Class<T>)
    } catch (e: IOException) {
      throw GcsStorageServiceException("error reading $objectType $objectKey", e)
    }
  }

  private fun blobIdForKey(objectType: ObjectType, key: String): BlobId {
    return BlobId.of(bucketName, pathForKey(objectType, key))
  }

  private fun pathForKey(objectType: ObjectType, key: String): String {
    return "${daoRoot(objectType)}/$key/$dataFilename"
  }

  private fun lastModifiedBlobId(objectType: ObjectType): BlobId {
    return BlobId.of(bucketName, "${daoRoot(objectType)}/$LAST_MODIFIED_FILENAME")
  }

  private fun daoRoot(objectType: ObjectType): String {
    return "$basePath/${objectType.group}"
  }

  private fun wrapException(message: String, e: Exception): Exception {
    if (e is InterruptedException) {
      Thread.currentThread().interrupt()
      return e
    } else if (e is NotFoundException) {
      return e
    } else {
      return GcsStorageServiceException(message, e)
    }
  }
}

private class GcsStorageServiceException(message: String, cause: Throwable) : RuntimeException(message, cause)
