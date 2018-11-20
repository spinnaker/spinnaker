/*
 * Copyright 2017 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.webhook.service

import com.netflix.spinnaker.orca.config.UserConfiguredUrlRestrictions
import com.netflix.spinnaker.orca.webhook.config.WebhookProperties
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

@Service
class WebhookService {

  // These headers create a security vulnerability.
  static final List<String> headerBlacklist = [
    "X-SPINNAKER-USER",
    "X-SPINNAKER-ACCOUNT",
    "X-SPINNAKER-USER-ORIGIN",
    "X-SPINNAKER-REQUEST-ID",
    "X-SPINNAKER-EXECUTION-ID"
  ];

  @Autowired
  private RestTemplate restTemplate

  @Autowired
  private UserConfiguredUrlRestrictions userConfiguredUrlRestrictions

  @Autowired
  private WebhookProperties preconfiguredWebhookProperties

  ResponseEntity<Object> exchange(HttpMethod httpMethod, String url, Object payload, Object customHeaders) {
    URI validatedUri = userConfiguredUrlRestrictions.validateURI(url)
    HttpHeaders headers = buildHttpHeaders(customHeaders)
    HttpEntity<Object> payloadEntity = new HttpEntity<>(payload, headers)
    return restTemplate.exchange(validatedUri, httpMethod, payloadEntity, Object)
  }

  ResponseEntity<Object> getStatus(String url, Object customHeaders) {
    URI validatedUri = userConfiguredUrlRestrictions.validateURI(url)
    HttpHeaders headers = buildHttpHeaders(customHeaders)
    HttpEntity<Object> httpEntity = new HttpEntity<>(headers)
    return restTemplate.exchange(validatedUri, HttpMethod.GET, httpEntity, Object)
  }

  List<WebhookProperties.PreconfiguredWebhook> getPreconfiguredWebhooks() {
    return preconfiguredWebhookProperties.preconfigured.findAll { it.enabled }
  }

  private static HttpHeaders buildHttpHeaders(Object customHeaders) {
    HttpHeaders headers = new HttpHeaders()
    customHeaders?.each { key, value ->
      if (headerBlacklist.contains(key.toUpperCase())) {
        return;
      }
      if (value instanceof List<String>) {
        headers.put(key as String, value as List<String>)
      } else {
        headers.add(key as String, value as String)
      }
    }
    return headers
  }
}
