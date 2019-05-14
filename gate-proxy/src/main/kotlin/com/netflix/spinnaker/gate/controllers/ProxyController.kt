/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.gate.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.config.ProxyConfigurationProperties
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException
import com.squareup.okhttp.Request
import com.squareup.okhttp.RequestBody
import com.squareup.okhttp.internal.http.HttpMethod
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod.GET
import org.springframework.web.bind.annotation.RequestMethod.POST
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.HandlerMapping
import java.net.SocketException
import java.util.stream.Collectors
import javax.servlet.http.HttpServletRequest

@RestController
@RequestMapping(value = ["/proxies"])
class ProxyController(
  val objectMapper: ObjectMapper,
  val registry: Registry,
  val proxyConfigurationProperties: ProxyConfigurationProperties
) {

  val proxyInvocationsId = registry.createId("proxy.invocations")

  @RequestMapping(value = ["/{proxy}/**"], method = [GET, POST])
  fun any(
    @PathVariable(value = "proxy") proxy: String,
    @RequestParam requestParams: Map<String, String>,
    httpServletRequest: HttpServletRequest
  ): ResponseEntity<Any> {
    return request(proxy, requestParams, httpServletRequest)
  }

  private fun request(
    proxy: String,
    requestParams: Map<String, String>,
    httpServletRequest: HttpServletRequest
  ): ResponseEntity<Any> {
    val proxyConfig = proxyConfigurationProperties
      .proxies
      .find { it.id.equals(proxy, true) } ?: throw InvalidRequestException("No proxy config found with id '$proxy'")

    val proxyPath = httpServletRequest
      .getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE)
      .toString()
      .substringAfter("/proxies/$proxy")

    val proxiedUrlBuilder = Request.Builder().url(proxyConfig.uri + proxyPath).build().httpUrl().newBuilder()
    for ((key, value) in requestParams) {
      proxiedUrlBuilder.addQueryParameter(key, value)
    }
    val proxiedUrl = proxiedUrlBuilder.build()

    var statusCode = 0
    var contentType = "text/plain"
    var responseBody: String

    try {
      val method = httpServletRequest.method
      val body = if (HttpMethod.permitsRequestBody(method)) {
        RequestBody.create(
          com.squareup.okhttp.MediaType.parse(httpServletRequest.contentType),
          httpServletRequest.reader.lines().collect(Collectors.joining(System.lineSeparator()))
        )
      } else {
        null
      }

      val response = proxyConfig.okHttpClient.newCall(
        Request.Builder().url(proxiedUrl).method(method, body).build()
      ).execute()
      statusCode = response.code()
      contentType = response.header("Content-Type")
      responseBody = response.body().string()
    } catch (e: SocketException) {
      statusCode = HttpStatus.GATEWAY_TIMEOUT.value()
      responseBody = e.toString()
    } catch (e: Exception) {
      responseBody = e.toString()
    }

    registry.counter(
      proxyInvocationsId
        .withTag("proxy", proxy)
        .withTag("method", httpServletRequest.method)
        .withTag("status", "${statusCode.toString()[0]}xx")
        .withTag("statusCode", statusCode.toString())
    ).increment()

    val responseObj = if (responseBody.startsWith("{")) {
      objectMapper.readValue(responseBody, Map::class.java)
    } else if (responseBody.startsWith("[")) {
      objectMapper.readValue(responseBody, Collection::class.java)
    } else {
      responseBody
    }

    val httpHeaders = HttpHeaders()
    httpHeaders.contentType = MediaType.valueOf(contentType)
    httpHeaders.put("X-Proxy-Status-Code", mutableListOf(statusCode.toString()))
    httpHeaders.put("X-Proxy-Url", mutableListOf(proxiedUrl.toString()))

    val status = if (statusCode >= 500 || statusCode == 0) {
      // an upstream 5xx should manifest as HTTP 502 - Bad Gateway
      HttpStatus.BAD_GATEWAY
    } else {
      HttpStatus.valueOf(statusCode)
    }

    return ResponseEntity(responseObj, httpHeaders, status)
  }
}
