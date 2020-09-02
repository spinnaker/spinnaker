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

package com.netflix.spinnaker.gate.controllers

import com.netflix.spinnaker.gate.api.extension.ApiExtension
import com.netflix.spinnaker.gate.api.extension.HttpRequest
import com.netflix.spinnaker.kork.annotations.Alpha
import com.netflix.spinnaker.kork.exceptions.SpinnakerException
import com.netflix.spinnaker.kork.exceptions.SystemException
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import com.squareup.okhttp.internal.http.HttpMethod
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.util.CollectionUtils
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.io.IOException
import java.util.stream.Collectors
import javax.servlet.http.HttpServletRequest

/**
 * A top-level [RestController] that exposes all api extensions under a common
 * `/extensions/{{ApiExtension.id}}` path.
 */
@Alpha
@RestController
@RequestMapping("/extensions")
class ApiExtensionController @Autowired constructor(private val apiExtensionsProvider: ObjectProvider<List<ApiExtension>>) {

  init {
    val duplicateApiExtensionIds = apiExtensionsProvider.getIfAvailable { ArrayList() }
      .groupBy { it.id().toLowerCase() }
      .filter { it.value.size > 1 }
      .map { it.value }
      .flatten()
      .map { "[class=${it.javaClass}], id=${it.id()}]" }

    if (duplicateApiExtensionIds.isNotEmpty()) {
      throw SystemException("Duplicate api extensions were detected (${duplicateApiExtensionIds.joinToString(", ")}")
    }
  }

  @RequestMapping(value = ["/{extension}/**"])
  fun any(
    @PathVariable(value = "extension") extension: String,
    @RequestParam requestParams: Map<String?, String?>?,
    httpServletRequest: HttpServletRequest
  ): ResponseEntity<Any> {

    val httpRequest = HttpRequest.of(
      httpServletRequest.method,
      httpServletRequest.requestURI.replace("/extensions/$extension", ""),
      httpServletRequest.headerNames.toList().map { it to httpServletRequest.getHeader(it) }.toMap(),
      requestParams
    )

    if (HttpMethod.permitsRequestBody(httpRequest.method)) {
      try {
        httpRequest.body = httpServletRequest
          .reader
          .lines()
          .collect(Collectors.joining(System.lineSeparator()))
      } catch (e: IOException) {
        throw SpinnakerException("Unable to read request body", e)
      }
    }

    val apiExtension = apiExtensionsProvider.getIfAvailable { ArrayList() }.stream()
      .filter { e: ApiExtension -> e.id().equals(extension, ignoreCase = true) && e.handles(httpRequest) }
      .findFirst()

    if (!apiExtension.isPresent) {
      throw NotFoundException()
    }

    val httpResponse = apiExtension.get().handle(httpRequest)
    val status = HttpStatus.resolve(httpResponse.status)
      ?: throw SpinnakerException("Unsupported http status code: " + httpResponse.status)

    return ResponseEntity(
      httpResponse.body,
      CollectionUtils.toMultiValueMap(httpResponse.headers.map { it.key to listOf(it.value) }.toMap()),
      status
    )
  }
}
