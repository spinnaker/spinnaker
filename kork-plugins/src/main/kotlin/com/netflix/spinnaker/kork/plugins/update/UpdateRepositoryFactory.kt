/*
 * Copyright 2020 Netflix, Inc.
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
 */
package com.netflix.spinnaker.kork.plugins.update

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.config.PluginsConfigurationProperties.PluginRepositoryProperties.FileDownloaderProperties
import com.netflix.spinnaker.kork.exceptions.IntegrationException
import com.netflix.spinnaker.kork.plugins.config.Configurable
import org.pf4j.update.FileDownloader
import org.pf4j.update.SimpleFileDownloader
import org.pf4j.update.UpdateRepository
import org.pf4j.update.verifier.CompoundVerifier
import java.net.URL

/**
 * Factory for [UpdateRepository].
 */
class UpdateRepositoryFactory(
  private val name: String,
  private val url: URL,
  private val fileDownloaderProperties: FileDownloaderProperties
) {

  fun create(): UpdateRepository =
    ConfigurableUpdateRepository(
      name,
      url,
      createFileDownloader(),
      CompoundVerifier()
    )

  /**
   * Create a [FileDownloader] for the [UpdateRepository].
   */
  private fun createFileDownloader(): FileDownloader {
    if (fileDownloaderProperties.className == null) {
      return SimpleFileDownloader()
    }

    val downloaderClass = javaClass.classLoader.loadClass(fileDownloaderProperties.className)

    if (!FileDownloader::class.java.isAssignableFrom(downloaderClass)) {
      throw IntegrationException(
        "Configured fileDownloader exists but does not implement FileDownloader: ${downloaderClass.canonicalName}"
      )
    }

    val configurable = downloaderClass.getAnnotation(Configurable::class.java)
    return if (configurable != null) {
      val config = mapper.convertValue(fileDownloaderProperties.config, configurable.value.java)

      val ctor = downloaderClass.constructors
        .find {
          it.parameterCount == 1 &&
            it.parameterTypes.first().isAssignableFrom(configurable.value.java)
        }
        ?: throw IntegrationException("Could not find matching constructor on file downloader for injecting config")
      ctor.newInstance(config)
    } else {
      downloaderClass.newInstance()
    } as FileDownloader
  }

  private companion object {
    val mapper: ObjectMapper = ObjectMapper()
  }
}
