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

package com.netflix.spinnaker.kork.plugins.update.repository

import com.netflix.spinnaker.kork.exceptions.SystemException
import com.netflix.spinnaker.kork.plugins.update.internal.Front50Service
import com.netflix.spinnaker.kork.plugins.update.internal.SpinnakerPluginInfo
import io.github.resilience4j.retry.Retry
import java.net.URL
import org.pf4j.update.FileDownloader
import org.pf4j.update.FileVerifier
import org.pf4j.update.SimpleFileDownloader
import org.pf4j.update.UpdateRepository
import org.pf4j.update.verifier.CompoundVerifier
import org.slf4j.LoggerFactory

/**
 * Optional [UpdateRepository].
 *
 * Wired up if the property `spinnaker.extensibility.repositories.front50.enabled` is `true`.
 * Pulls Front50 plugin info objects and populates the available plugin info cache in
 * [org.pf4j.update.UpdateManager].
 */
class Front50UpdateRepository(
  private val repositoryName: String,
  private val url: URL,
  private val downloader: FileDownloader = SimpleFileDownloader(),
  private val verifier: FileVerifier = CompoundVerifier(),
  private val front50Service: Front50Service
) : UpdateRepository {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  private var plugins: MutableMap<String, SpinnakerPluginInfo> = mutableMapOf()

  override fun getUrl(): URL {
    return url
  }

  override fun getId(): String {
    return repositoryName
  }

  override fun getPlugins(): MutableMap<String, SpinnakerPluginInfo> {
    return plugins.ifEmpty {
      log.debug("Populating plugin info cache from front50")
      val response = retry.executeSupplier { front50Service.listAll().execute() }

      if (!response.isSuccessful) {
        // We can't throw an exception here when we fail to talk to Front50 because it will prevent a service from
        // starting. We would rather a Spinnaker service start and be potentially misconfigured than have a hard
        // startup dependency on front50.
        log.error(
          "Failed listing plugin info from front50. This service may not download plugins that it needs: {}",
          response.errorBody()?.string() ?: response.message()
        )
        return mutableMapOf()
      }

      response.body()!!.associateByTo(plugins) { it.id }
    }
  }

  override fun getPlugin(id: String): SpinnakerPluginInfo {
    return plugins.getOrPut(id,
      {
        val response = front50Service.getById(id).execute()
        if (!response.isSuccessful) {
          throw SystemException("Unable to get the requested plugin info `$id` from front50",
            response.message())
        }

        response.body()!!
    })
  }

  override fun getFileVerifier(): FileVerifier {
    return verifier
  }

  override fun getFileDownloader(): FileDownloader {
    return downloader
  }

  override fun refresh() {
    plugins.clear()
  }

  companion object {
    private val retry = Retry.ofDefaults("front50-update-repository")
  }
}
