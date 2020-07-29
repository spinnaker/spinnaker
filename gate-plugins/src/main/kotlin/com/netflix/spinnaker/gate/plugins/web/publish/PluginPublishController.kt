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
package com.netflix.spinnaker.gate.plugins.web.publish

import com.google.common.hash.Hashing
import com.netflix.spinnaker.config.DefaultServiceEndpoint
import com.netflix.spinnaker.config.PluginsConfigurationProperties
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider
import com.netflix.spinnaker.gate.config.ServiceConfiguration
import com.netflix.spinnaker.gate.plugins.web.PluginService
import com.netflix.spinnaker.kork.exceptions.SystemException
import com.netflix.spinnaker.kork.plugins.update.internal.SpinnakerPluginInfo
import com.netflix.spinnaker.security.AuthenticatedRequest
import io.swagger.annotations.ApiOperation
import java.lang.String.format
import lombok.SneakyThrows
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/plugins/publish")
class PluginPublishController(
  private val pluginService: PluginService,
  okHttpClientProvider: OkHttpClientProvider,
  serviceConfiguration: ServiceConfiguration,
  private val pluginRepositoriesConfig: Map<String, PluginsConfigurationProperties.PluginRepositoryProperties>
) {

  private val front50Url = serviceConfiguration.getServiceEndpoint("front50").url
  private val okHttpClient: OkHttpClient = okHttpClientProvider.getClient(DefaultServiceEndpoint("front50", front50Url))

  @SneakyThrows
  @ApiOperation(value = "Publish a plugin binary and the plugin info metadata.")
  @PostMapping("/{pluginId}/{pluginVersion}", consumes = [MULTIPART_FORM_DATA_VALUE])
  fun publishPlugin(
    @RequestPart("plugin") body: MultipartFile,
    @RequestPart("pluginInfo") pluginInfo: SpinnakerPluginInfo,
    @PathVariable pluginId: String,
    @PathVariable pluginVersion: String
  ) {
    pluginService.verifyPluginInfo(pluginInfo, pluginId)
    val bytes = body.bytes
    val release = pluginService.getReleaseByVersion(pluginInfo, pluginVersion)

    verifyChecksum(bytes, release.sha512sum)
    uploadToFront50(pluginId, pluginVersion, release.sha512sum, bytes)

    // TODO: Need to change this back to front50Url service endpoint.
    //  Doing this temporarily due to the scheme difference while uploading and downloading binaries.
    val front50RepoUrl: String = pluginRepositoriesConfig["front50"]?.url?.toString() ?: front50Url

    release.url = "$front50RepoUrl/pluginBinaries/$pluginId/$pluginVersion"

    pluginService.upsertPluginInfo(pluginInfo)
  }

  /**
   * Retrofit 1.9 wasn't able to make multi-part uploads: It would never pass the actual plugin binary as part of the
   * form upload. As a workaround, this controller invokes OkHttpClient directly.
   *
   * TODO(rz): Upgrade gate to Retrofit 2 and try again. :|
   */
  private fun uploadToFront50(pluginId: String, pluginVersion: String, checksum: String, body: ByteArray) {
    AuthenticatedRequest.propagate {
      val request = Request.Builder()
        .url("$front50Url/pluginBinaries/$pluginId/$pluginVersion?sha512sum=$checksum")
        .post(
          MultipartBody.Builder()
            .addFormDataPart(
              "plugin",
              format("%s-%s.zip", pluginId, pluginVersion),
              RequestBody.create(MediaType.parse("application/octet-stream"), body)
            )
            .build()
        )
        .build()

      val response = okHttpClient.newCall(request).execute()
      if (!response.isSuccessful) {
        val reason = response.body()?.string() ?: "Unknown reason: ${response.code()}"
        throw SystemException("Failed to upload plugin binary: $reason")
      }
    }.call()
  }

  private fun verifyChecksum(body: ByteArray, sha512sum: String) {
    val sha = Hashing.sha512().hashBytes(body).toString()
    if (sha != sha512sum) {
      throw SystemException("Plugin binary checksum does not match expected checksum value")
        .setRetryable(true)
    }
  }
}
