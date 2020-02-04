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

import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import io.swagger.annotations.ApiOperation
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.TimeUnit
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@RestController
@RequestMapping("/plugins/deck")
class DeckPluginsController(
  private val deckPluginService: DeckPluginService
) {

  @ApiOperation(value = "Retrieve a plugin manifest")
  @GetMapping("/plugin-manifest.json")
  fun getPluginManifest(): List<DeckPluginVersion> {
    return deckPluginService.getPluginsManifests()
  }

  @ApiOperation(value = "Retrieve a single plugin asset by version")
  @GetMapping("/{pluginId}/{pluginVersion}/{asset:.*}")
  fun getPluginAsset(
    @PathVariable pluginId: String,
    @PathVariable pluginVersion: String,
    @PathVariable asset: String
  ): ResponseEntity<String> {
    val pluginAsset = deckPluginService.getPluginAsset(pluginId, pluginVersion, asset) ?: throw NotFoundException("Unable to find asset for plugin version")

    return ResponseEntity.ok()
        .header("Content-Type", pluginAsset.contentType)
        .header("Cache-Control",
            CacheControl
                .maxAge(1, TimeUnit.DAYS)
                .mustRevalidate()
                .cachePrivate()
                .headerValue
        )
        .body(pluginAsset.content)
  }

  @ExceptionHandler(CacheNotReadyException::class)
  fun handleCacheNotReadyException(
    e: Exception,
    response: HttpServletResponse,
    request: HttpServletRequest?
  ) {
    response.sendError(HttpStatus.SERVICE_UNAVAILABLE.value(), e.message)
  }
}
