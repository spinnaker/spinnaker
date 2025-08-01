/*
 * Copyright 2025 Salesforce, Inc.
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

package com.netflix.spinnaker.orca.webhook.service;

import com.netflix.spinnaker.orca.webhook.config.WebhookProperties;
import com.netflix.spinnaker.orca.webhook.pipeline.WebhookStage;
import java.net.URI;
import java.util.Optional;
import lombok.Getter;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/** Lower level details derived from a webhook stage for making a request */
public class RestTemplateData {

  private final RestTemplate restTemplate;
  private final URI validatedUri;
  private final HttpMethod httpMethod;
  private final HttpEntity<Object> payloadEntity;
  @Getter private final WebhookStage.StageData stageData;
  @Getter private final Optional<WebhookProperties.AllowedRequest> allowedRequest;

  public RestTemplateData(
      RestTemplate restTemplate,
      URI validatedUri,
      HttpMethod httpMethod,
      HttpEntity<Object> payloadEntity,
      WebhookStage.StageData stageData,
      Optional<WebhookProperties.AllowedRequest> allowedRequest) {
    this.restTemplate = restTemplate;
    this.validatedUri = validatedUri;
    this.httpMethod = httpMethod;
    this.payloadEntity = payloadEntity;
    this.stageData = stageData;
    this.allowedRequest = allowedRequest;
  }

  public ResponseEntity<Object> exchange() {
    return this.restTemplate.exchange(validatedUri, httpMethod, payloadEntity, Object.class);
  }
}
