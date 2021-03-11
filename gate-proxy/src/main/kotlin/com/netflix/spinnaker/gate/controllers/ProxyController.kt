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
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider
import com.netflix.spinnaker.gate.api.extension.ProxyConfigProvider
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException
import com.netflix.spinnaker.kork.web.interceptors.Criticality
import com.netflix.spinnaker.security.AuthenticatedRequest
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.internal.http.HttpMethod
import java.net.SocketException
import java.util.stream.Collectors
import javax.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod.DELETE
import org.springframework.web.bind.annotation.RequestMethod.GET
import org.springframework.web.bind.annotation.RequestMethod.POST
import org.springframework.web.bind.annotation.RequestMethod.PUT
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.HandlerMapping

@Criticality(Criticality.Value.LOW)
@RestController
@RequestMapping(value = ["/proxies"])
class ProxyController(
  val objectMapper: ObjectMapper,
  val registry: Registry,
  val okHttpClientProvider: OkHttpClientProvider,
  val proxyConfigProvidersObjectProvider: ObjectProvider<List<ProxyConfigProvider>>
) {
  private val log = LoggerFactory.getLogger(javaClass)

  /**
   * A single entry cache containing the set of initialized proxies.
   *
   * Extension point implementations are not guaranteed to be available at initialization time,
   * so a [CacheBuilder] is used to ensure we only perform initialization once.
   */
  private val proxiesCache = CacheBuilder.newBuilder().maximumSize(1).build(
    CacheLoader.from { _: String? ->
      val proxyConfigProviders = proxyConfigProvidersObjectProvider.ifAvailable

      val proxiesById = mutableMapOf<String, Proxy>()
      proxyConfigProviders?.forEach {
        it.proxyConfigs.forEach { proxyConfig ->
          try {
            proxiesById.put(proxyConfig.id, Proxy(proxyConfig).init(okHttpClientProvider))
          } catch (e: Exception) {
            log.error("Failed to initialize proxy (id: ${proxyConfig.id})", e)
          }
        }
      }

      log.info("Initialized ${proxiesById.size} proxies (${proxiesById.keys.joinToString()})")
      return@from proxiesById
    }
  )

  val proxyInvocationsId = registry.createId("proxy.invocations")

  @RequestMapping(value = ["/{proxy}/**"], method = [DELETE, GET, POST, PUT])
  fun any(
    @PathVariable(value = "proxy") proxyId: String,
    @RequestParam requestParams: Map<String, String>,
    httpServletRequest: HttpServletRequest
  ): ResponseEntity<Any> {
    return AuthenticatedRequest.allowAnonymous {
      return@allowAnonymous request(proxyId, requestParams, httpServletRequest)
    }
  }

  @RequestMapping(method = [GET])
  fun list() : List<SimpleProxyConfig> {
    return proxies().map { SimpleProxyConfig(
      it.config.id,
      it.config.uri
    ) }
  }

  private fun request(
    proxyId: String,
    requestParams: Map<String, String>,
    request: HttpServletRequest
  ): ResponseEntity<Any> {
    val proxy = proxies()
      .find { it.config.id.equals(proxyId, true) }
      ?: throw InvalidRequestException("No proxy config found with id '$proxyId'")
    val proxyConfig = proxy.config

    val proxyPath = request
      .getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE)
      .toString()
      .substringAfter("/proxies/$proxyId")

    val proxiedUrlBuilder = Request.Builder().url(proxyConfig.uri + proxyPath).build().url().newBuilder()
    for ((key, value) in requestParams) {
      proxiedUrlBuilder.addQueryParameter(key, value)
    }
    val proxiedUrl = proxiedUrlBuilder.build()

    var statusCode = 0
    var contentType = "text/plain"
    var responseBody: String

    try {
      val method = request.method

      val body = if (HttpMethod.permitsRequestBody(method) && request.contentType != null) {
        RequestBody.create(
          okhttp3.MediaType.parse(request.contentType),
          request.reader.lines().collect(Collectors.joining(System.lineSeparator()))
        )
      } else {
        null
      }

      val response = proxy.okHttpClient.newCall(
        Request.Builder().url(proxiedUrl).method(method, body).build()
      ).execute()
      statusCode = response.code()
      contentType = response.header("Content-Type") ?: contentType
      responseBody = response.body()?.string() ?: ""
    } catch (e: SocketException) {
      log.error("Exception processing proxy request", e)
      statusCode = HttpStatus.GATEWAY_TIMEOUT.value()
      responseBody = e.toString()
    } catch (e: Exception) {
      log.error("Exception processing proxy request", e)
      responseBody = e.toString()
    }

    registry.counter(
      proxyInvocationsId
        .withTag("proxy", proxyId)
        .withTag("method", request.method)
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

  private fun proxies() = proxiesCache.get("all").values

  data class SimpleProxyConfig(val id: String, val uri: String)
}
