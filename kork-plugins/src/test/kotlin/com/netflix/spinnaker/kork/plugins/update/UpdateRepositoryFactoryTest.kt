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

import com.netflix.spinnaker.config.PluginsConfigurationProperties
import com.netflix.spinnaker.kork.plugins.update.downloader.ProcessFileDownloader
import com.netflix.spinnaker.kork.plugins.update.downloader.ProcessFileDownloaderConfig
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import java.net.URL

class UpdateRepositoryFactoryTest : JUnit5Minutests {

  fun tests() = rootContext<UpdateRepositoryFactory> {
    context("custom file downloader") {
      fixture {
        UpdateRepositoryFactory(
          "myRepo",
          URL("http://example.com"),
          PluginsConfigurationProperties.PluginRepositoryProperties.FileDownloaderProperties().apply {
            className = ProcessFileDownloader::class.java.canonicalName
            config = ProcessFileDownloaderConfig("curl -O")
          }
        )
      }

      test("creates update repository with configured custom file downloader") {
        expectThat(create())
          .isA<ConfigurableUpdateRepository>()
          .get { fileDownloader }
          .describedAs("file downloader")
          .isA<ProcessFileDownloader>()
          .get { config.command }.isEqualTo("curl -O")
      }
    }
  }
}
