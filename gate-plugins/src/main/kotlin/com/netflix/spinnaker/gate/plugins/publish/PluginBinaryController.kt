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

import com.netflix.spinnaker.gate.services.internal.Front50Service
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import retrofit.mime.TypedByteArray
import java.io.InputStream

@RestController
@RequestMapping("/plugins/upload")
class PluginBinaryController(
  private val front50Service: Front50Service
) {

  @ApiOperation(value = "Upload a plugin binary")
  @PostMapping(
    "/{pluginId}/{pluginVersion}",
    consumes = ["application/zip", "application/octet-stream"]
  )
  fun publishBinary(
    @RequestBody body: InputStream,
    @PathVariable pluginId: String,
    @PathVariable pluginVersion: String,
    @RequestParam sha512sum: String
  ) {
    front50Service.uploadPluginBinary(
      pluginId,
      pluginVersion,
      sha512sum,
      TypedByteArray("application/octet-stream", body.readBytes())
    )
  }
}
