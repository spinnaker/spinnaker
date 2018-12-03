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

import com.netflix.spinnaker.keel.api.Asset
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.file.Message
import com.netflix.spinnaker.keel.plugin.AssetPlugin
import com.netflix.spinnaker.keel.plugin.ConvergeAccepted
import com.netflix.spinnaker.keel.plugin.ConvergeFailed
import com.netflix.spinnaker.keel.plugin.ConvergeResponse
import com.netflix.spinnaker.keel.plugin.CurrentResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.File
import javax.annotation.PostConstruct
import kotlin.reflect.KClass

@Component
class FilePlugin(
  @Value("\${keel.file.directory:#{systemEnvironment['HOME']}/keel}") val directory: File
) : AssetPlugin {

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

  override val supportedKinds: Map<String, KClass<out Any>> = mapOf(
    "messages.file.${SPINNAKER_API_V1.group}" to Message::class
  )

  override fun current(request: Asset<*>): CurrentResponse {
    TODO("not implemented")
  }

  override fun upsert(request: Asset<*>): ConvergeResponse {
    val spec = request.spec
    return if (spec is Message) {
      log.info("Upsert asset {}", request)
      request.file.writer().use {
        it.append(spec.text)
      }
      ConvergeAccepted
    } else {
      ConvergeFailed("Invalid asset spec ${spec.javaClass.name}")
    }
  }

  override fun delete(request: Asset<*>): ConvergeResponse {
    val spec = request.spec
    return if (spec is Message) {
      log.info("Delete asset {}", request)
      request.file.delete()
      ConvergeAccepted
    } else {
      ConvergeFailed("Invalid asset spec ${spec.javaClass.name}")
    }
  }

  private val Asset<*>.file: File
    get() = File(directory, metadata.name.value)

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
