/*
 * Copyright 2014 Netflix, Inc.
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


package com.netflix.spinnaker.gate.services

import com.netflix.spinnaker.gate.config.ServiceConfiguration
import com.netflix.spinnaker.gate.services.internal.Front50Service
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.RequestEntity
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import retrofit.Endpoint

@CompileStatic
@Component
@Slf4j
class NotificationService {
  @Autowired(required = false)
  Front50Service front50Service

  @Autowired
  OkHttpClient okHttpClient

  @Autowired
  ServiceConfiguration serviceConfiguration

  Map getNotificationConfigs(String type, String app) {
    front50Service.getNotificationConfigs(type, app)
  }

  void saveNotificationConfig(String type, String app, Map notification) {
    front50Service.saveNotificationConfig(type, app, notification)
  }

  void deleteNotificationConfig(String type, String app) {
    front50Service.deleteNotificationConfig(type, app)
  }

  ResponseEntity<String> processNotificationCallback(String source, RequestEntity<String> request) {
    log.debug("Processing notification callback: ${request.getMethod()} ${request.getUrl()}, ${request.getHeaders()}")
    String contentType = request.getHeaders().getFirst("Content-Type")?.toLowerCase()

    if (!contentType) {
      throw new InvalidRequestException("No Content-Type header present in request. Unable to process notification callback.")
    }

    final MediaType mediaType = MediaType.parse(contentType)

    // We use the "raw" OkHttpClient here instead of EchoService because retrofit messes up with the encoding
    // of the body for the x-www-form-urlencoded content type, which is what Slack uses. This allows us to pass
    // the original body unmodified along to echo.
    Endpoint echoEndpoint = serviceConfiguration.getServiceEndpoint("echo")

    Request.Builder builder = new Request.Builder()
      .url(echoEndpoint.url + request.url.path)
      .post(RequestBody.create(mediaType, request.body))

    request.getHeaders().each { String name, List values ->
      values.each { value ->
        builder.addHeader(name, value.toString())
      }
    }

    Request echoRequest = builder.build();
    Response response = okHttpClient.newCall(echoRequest).execute()

    // convert retrofit response to Spring format
    String body = response.body().contentLength() > 0 ? response.body().string() : null
    HttpHeaders headers = new HttpHeaders()
    headers.putAll(response.headers().toMultimap())
    return new ResponseEntity(body, headers, HttpStatus.valueOf(response.code()))
  }
}
