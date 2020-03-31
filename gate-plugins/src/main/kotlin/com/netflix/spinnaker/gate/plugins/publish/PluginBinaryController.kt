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
package com.netflix.spinnaker.gate.plugins.publish

import com.google.common.hash.Hashing
import com.netflix.spinnaker.gate.config.ServiceConfiguration
import com.netflix.spinnaker.kork.exceptions.SystemException
import com.netflix.spinnaker.security.AuthenticatedRequest
import io.swagger.annotations.ApiOperation
import java.lang.String.format
import lombok.SneakyThrows
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/plugins/upload")
class PluginBinaryController(
  private val okHttpClient: OkHttpClient,
  private val serviceConfiguration: ServiceConfiguration
) {

  @SneakyThrows
  @ApiOperation(value = "Upload a plugin binary")
  @PostMapping("/{pluginId}/{pluginVersion}")
  fun publishBinary(
    @RequestParam("plugin") body: MultipartFile,
    @PathVariable pluginId: String,
    @PathVariable pluginVersion: String,
    @RequestParam sha512sum: String
  ) {
    val bytes = body.bytes
    verifyChecksum(bytes, sha512sum)
    uploadToFront50(pluginId, pluginVersion, sha512sum, bytes)
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
        .url(serviceConfiguration.getServiceEndpoint("front50").url + "/pluginBinaries/$pluginId/$pluginVersion?sha512sum=$checksum")
        .post(MultipartBody.Builder()
          .addFormDataPart(
            "plugin",
            format("%s-%s.zip", pluginId, pluginVersion),
            RequestBody.create(MediaType.parse("application/octet-stream"), body))
          .build())
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
