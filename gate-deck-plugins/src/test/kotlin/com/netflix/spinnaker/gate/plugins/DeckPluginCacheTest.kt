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
package com.netflix.spinnaker.gate.plugins

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.kork.plugins.bundle.PluginBundleExtractor
import com.netflix.spinnaker.kork.plugins.update.SpinnakerUpdateManager
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.pf4j.update.PluginInfo
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import java.nio.file.Files
import java.nio.file.Paths

class DeckPluginCacheTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    context("caching") {
      test("latest plugin releases with deck artifacts are added to cache") {
        subject.refresh()

        expectThat(subject.getCache())
          .hasSize(2)
          .and {
            get { first().plugin }.and {
              get { id }.isEqualTo("io.spinnaker.hello")
              get { version }.isEqualTo("1.1.0")
            }
            get { last().plugin }.and {
              get { id }.isEqualTo("io.spinnaker.goodbye")
              get { version }.isEqualTo("2.0.1")
            }
          }
      }
    }
  }

  private inner class Fixture {
    val updateManager: SpinnakerUpdateManager = mockk(relaxed = true)
    val pluginBundleExtractor: PluginBundleExtractor = mockk(relaxed = true)
    val registry: Registry = NoopRegistry()
    val subject = DeckPluginCache(updateManager, pluginBundleExtractor, registry)

    init {
      val plugins = listOf(
        PluginInfo().apply {
          id = "io.spinnaker.hello"
          releases = listOf(
            PluginInfo.PluginRelease().apply {
              version = "1.0.0"
            },
            PluginInfo.PluginRelease().apply {
              version = "1.1.0"
            }
          )
        },
        PluginInfo().apply {
          id = "io.spinnaker.goodbye"
          releases = listOf(
            PluginInfo.PluginRelease().apply {
              version = "2.0.0"
            },
            PluginInfo.PluginRelease().apply {
              version = "2.0.1"
            }
          )
        }
      )

      every { updateManager.plugins } returns plugins

      val pluginIdSlot = slot<String>()
      every { updateManager.getLastPluginRelease(capture(pluginIdSlot), "deck") } answers {
        PluginInfo.PluginRelease().apply {
          version = plugins.find { it.id == pluginIdSlot.captured }!!.releases.last().version
        }
      }

      every { updateManager.downloadPluginRelease(any(), any()) } returns Paths.get("/dev/null")

      every { pluginBundleExtractor.extractService(any(), any()) } answers {
        val temp = Files.createTempDirectory("downloaded-plugins")
        temp.resolve("index.js").also {
          it.toFile().writeText("")
        }
        temp
      }
    }
  }
}
