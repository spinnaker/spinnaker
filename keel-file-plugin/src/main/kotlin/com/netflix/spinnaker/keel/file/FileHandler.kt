/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.keel.file

import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.file.Message
import com.netflix.spinnaker.keel.plugin.ResourceConflict
import com.netflix.spinnaker.keel.plugin.ResourceHandler
import org.slf4j.LoggerFactory
import java.io.File
import javax.annotation.PostConstruct

class FileHandler(private val directory: File) : ResourceHandler<Message> {

  @PostConstruct
  fun ensureDirectoryExists() {
    log.info("Ensuring directory {} exists", directory.canonicalPath)
    if (directory.exists() && !directory.isDirectory) {
      throw IllegalStateException("Configured directory ${directory.canonicalPath} is a file or something")
    }
    directory.mkdirs().also {
      if (it) {
        log.info("Created {}", directory.canonicalPath)
      } else {
        log.info("Directory {} already exists", directory.canonicalPath)
      }
    }
  }

  override val apiVersion: ApiVersion = SPINNAKER_API_V1.subApi("file")

  override val supportedKind =
    ResourceKind(apiVersion.group, "message", "messages") to Message::class.java

  override fun current(resource: Resource<Message>): Message? {
    val file = File(directory, resource.metadata.name.value)
    return when {
      !file.exists() -> null
      !file.canRead() -> throw ResourceConflict("Resource found but it cannot be read")
      file.isDirectory -> throw ResourceConflict("Resource found but it is a directory not a regular file")
      else -> Message(file.readText())
    }
  }

  override fun upsert(resource: Resource<Message>) {
    log.info("Upsert resource {}", resource)
    resource.file.writer().use {
      it.append(resource.spec.text)
    }
  }

  override fun delete(resource: Resource<Message>) {
    log.info("Delete resource {}", resource)
    resource.file.delete()
  }

  private val Resource<*>.file: File
    get() = File(directory, metadata.name.value)

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
