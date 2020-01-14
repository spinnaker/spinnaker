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
package com.netflix.spinnaker.kork.plugins.update.downloader

import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isTrue
import java.net.URL
import java.nio.file.Paths

class ProcessFileDownloaderTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    test("command is correctly executed") {
      subject.downloadFile(URL("http://localhost/somefile.zip"))

      expectThat(processBuilderSlot.captured)
        .and {
          get { directory().exists() }.isTrue()
          get { environment()["hello"] }.isEqualTo("world")
          get { command() }.isEqualTo(listOf(
            "my-downloader-script", "-s", "--foo=bar", "https://localhost/something.zip"
          ))
        }
    }

    test("process stdout is read for downloaded file") {
      expectThat(subject.downloadFile(URL("http://localhost/somefile.zip")))
        .assertThat("valid file has been downloaded to path") { path ->
          path.toFile().let {
            it.exists() && it.readText() == "hi"
          }
        }
    }
  }

  private class Fixture {
    val config: ProcessFileDownloaderConfig = ProcessFileDownloaderConfig(
      "my-downloader-script -s --foo=bar https://localhost/something.zip",
      mapOf("hello" to "world")
    )
    val processRunner: ProcessFileDownloader.ProcessRunner = mockk(relaxed = true)
    val subject: ProcessFileDownloader = ProcessFileDownloader(config, processRunner)

    val processBuilderSlot = slot<ProcessBuilder>()

    init {

      every { processRunner.completeOrTimeout(capture(processBuilderSlot)) } answers {
        val path = Paths.get(processBuilderSlot.captured.directory().path, "downloaded.zip")
        path.toFile().writeText("hi")
        path.toString()
      }
    }
  }
}
