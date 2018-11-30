package com.netflix.spinnaker.keel.file

import com.netflix.spinnaker.keel.api.Asset
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.file.Message
import com.netflix.spinnaker.keel.plugin.AssetPlugin
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
    TODO("not implemented")
  }

  override fun delete(request: Asset<*>): ConvergeResponse {
    TODO("not implemented")
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
