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

import com.netflix.spinnaker.front50.exception.NotFoundException
import org.slf4j.LoggerFactory

class CompositeStorageService(
  private val primary: StorageService,
  private val previous: StorageService,
  private val writeOnly: Boolean
) : StorageService {

  companion object {
    private val log = LoggerFactory.getLogger(CompositeStorageService::class.java)
  }

  override fun ensureBucketExists() {
    primary.ensureBucketExists()
    previous.ensureBucketExists()
  }

  override fun supportsVersioning(): Boolean {
    return primary.supportsVersioning()
  }

  override fun <T : Timestamped?> loadObject(objectType: ObjectType?, objectKey: String?): T {
    if (writeOnly || objectType == ObjectType.ENTITY_TAGS) {
      return previous.loadObject<T>(objectType, objectKey)
    }

    try {
      return primary.loadObject<T>(objectType, objectKey)
    } catch (e: NotFoundException) {
      log.debug("{}.loadObject({}, {}) not found (primary)", primary.javaClass.simpleName, objectType, objectKey)
      return previous.loadObject<T>(objectType, objectKey)
    } catch (e: Exception) {
      log.error("{}.loadObject({}, {}) failed (primary)", primary.javaClass.simpleName, objectType, objectKey)
      return previous.loadObject<T>(objectType, objectKey)
    }
  }

  override fun deleteObject(objectType: ObjectType?, objectKey: String?) {
    primary.deleteObject(objectType, objectKey)
    previous.deleteObject(objectType, objectKey)
  }

  override fun <T : Timestamped?> storeObject(objectType: ObjectType?, objectKey: String?, item: T) {
    var exception: Exception? = null

    try {
      primary.storeObject(objectType, objectKey, item)
    } catch (e: Exception) {
      exception = e
      log.error(
        "{}.storeObject({}, {}) failed",
        primary.javaClass.simpleName,
        objectType,
        objectKey,
        e
      )
    }

    try {
      previous.storeObject(objectType, objectKey, item)
    } catch (e: Exception) {
      exception = e
      log.error(
        "{}.storeObject({}, {}) failed",
        previous.javaClass.simpleName,
        objectType,
        objectKey,
        e
      )
    }

    if (exception != null) {
      throw exception
    }
  }

  override fun listObjectKeys(objectType: ObjectType?): Map<String, Long> {
    if (writeOnly) {
      return previous.listObjectKeys(objectType)
    }

    val primaryObjectKeys = primary.listObjectKeys(objectType)
    val previousObjectKeys = previous.listObjectKeys(objectType)

    return previousObjectKeys + primaryObjectKeys
  }

  override fun <T : Timestamped?> listObjectVersions(objectType: ObjectType?,
                                                     objectKey: String?,
                                                     maxResults: Int): MutableCollection<T> {
    if (writeOnly) {
      return previous.listObjectVersions(objectType, objectKey, maxResults)
    }

    try {
      return primary.listObjectVersions(objectType, objectKey, maxResults)
    } catch (e: Exception) {
      log.error(
        "{}.listObjectVersions({}, {}, {}) failed (primary)",
        primary.javaClass.simpleName,
        objectType,
        objectKey,
        maxResults
      )
      return previous.listObjectVersions(objectType, objectKey, maxResults)
    }
  }

  override fun getLastModified(objectType: ObjectType?): Long {
    if (writeOnly) {
      return previous.getLastModified(objectType)
    }

    try {
      return primary.getLastModified(objectType)
    } catch (e: Exception) {
      log.error("{}.getLastModified({}) failed (primary)", primary.javaClass.simpleName, objectType)
      return previous.getLastModified(objectType)
    }
  }
}
