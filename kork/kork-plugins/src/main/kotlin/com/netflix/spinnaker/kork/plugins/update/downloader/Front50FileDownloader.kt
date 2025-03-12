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

import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import io.github.resilience4j.retry.Retry
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Downloads plugin binaries from Front50.
 */
class Front50FileDownloader(
  private val okHttpClient: OkHttpClient,
  private val front50BaseUrl: URL
) : SupportingFileDownloader {

  override fun supports(url: URL): Boolean =
    url.host == front50BaseUrl.host

  override fun downloadFile(fileUrl: URL): Path {
    val request = Request.Builder()
      .header("Accept", "application/zip,application/octet-stream")
      .url(fileUrl)
      .build()

    val response = retry.executeCallable { okHttpClient.newCall(request).execute() }

    val body = response.body
    if (!response.isSuccessful || body == null) {
      throw NotFoundException("Plugin binary could not be downloaded, received HTTP ${response.code}")
    }

    return downloadDir.resolve(Paths.get(fileUrl.path + binaryExtension).fileName).also {
      Files.write(it, body.bytes())
    }
  }

  companion object {
    private val downloadDir = Files.createTempDirectory("plugin-downloads")
    private val retry = Retry.ofDefaults("plugin-front50-downloader")
    private const val binaryExtension = ".zip"
  }
}
