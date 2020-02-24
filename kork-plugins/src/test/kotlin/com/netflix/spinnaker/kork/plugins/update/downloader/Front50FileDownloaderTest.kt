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
import okhttp3.Call
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isTrue
import java.net.URL

class Front50FileDownloaderTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    test("files are downloaded to a temp directory") {
      every { response.isSuccessful } returns true
      every { response.code() } returns 200
      every { response.body() } returns ResponseBody.create(MediaType.parse("application/zip"), "oh hi")

      expectThat(subject.downloadFile(URL("http://front50.com/myplugin.zip"))) {
        get { toFile().readText() }.isEqualTo("oh hi")
      }
    }

    test("supports files from front50") {
      expect {
        that(subject.supports(URL("https://something.com"))).isFalse()
        that(subject.supports(URL("https://front50.com"))).isTrue()
      }
    }
  }

  private inner class Fixture {
    private val okHttpClient: OkHttpClient = mockk(relaxed = true)
    private val call: Call = mockk(relaxed = true)
    val response: Response = mockk(relaxed = true)

    val subject: Front50FileDownloader = Front50FileDownloader(okHttpClient, URL("https://front50.com"))

    init {
      every { okHttpClient.newCall(any()) } returns call
      every { call.execute() } returns response
    }
  }
}
