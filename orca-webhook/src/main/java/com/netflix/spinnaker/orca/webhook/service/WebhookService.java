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

package com.netflix.spinnaker.orca.webhook.service;

import com.netflix.spinnaker.kork.exceptions.SpinnakerException;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.config.UserConfiguredUrlRestrictions;
import com.netflix.spinnaker.orca.webhook.config.WebhookProperties;
import com.netflix.spinnaker.orca.webhook.pipeline.WebhookStage;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

/**
 * Service that interacts with webhook endpoints for initiating a webhook call, checking the status
 * of webhook or cancelling a webhook call.
 */
@Service
@Slf4j
public class WebhookService {

  // These headers create a security vulnerability.
  static final List<String> headerDenyList =
      List.of(
          "X-SPINNAKER-USER",
          "X-SPINNAKER-ACCOUNT",
          "X-SPINNAKER-USER-ORIGIN",
          "X-SPINNAKER-REQUEST-ID",
          "X-SPINNAKER-EXECUTION-ID");

  @Autowired private List<RestTemplateProvider> restTemplateProviders = new ArrayList<>();

  @Autowired private UserConfiguredUrlRestrictions userConfiguredUrlRestrictions;

  @Autowired private WebhookProperties preconfiguredWebhookProperties;

  public ResponseEntity<Object> callWebhook(StageExecution stageExecution) {
    RestTemplateData restTemplateData = getRestTemplateData(WebhookTaskType.CREATE, stageExecution);
    if (restTemplateData == null) {
      throw new SpinnakerException("Unable to determine rest template to call webhook");
    }
    return restTemplateData.exchange();
  }

  public ResponseEntity<Object> getWebhookStatus(StageExecution stageExecution) {
    RestTemplateData restTemplateData =
        getRestTemplateData(WebhookTaskType.MONITOR, stageExecution);
    if (restTemplateData == null) {
      throw new SpinnakerException("Unable to determine rest template to monitor webhook");
    }
    return restTemplateData.exchange();
  }

  public ResponseEntity<Object> cancelWebhook(StageExecution stageExecution) {
    RestTemplateData restTemplateData = getRestTemplateData(WebhookTaskType.CANCEL, stageExecution);

    // Only do cancellation if we can determine the rest template to use.
    if (restTemplateData == null) {
      log.warn("Cannot determine rest template to cancel the webhook");
      return null;
    }
    WebhookStage.StageData stageData = restTemplateData.stageData;
    try {
      log.info("Sending best effort webhook cancellation to {}", stageData.cancelEndpoint);
      ResponseEntity<Object> response = restTemplateData.exchange();
      log.debug(
          "Received status code {} from cancel endpoint {} in execution {} in stage {}",
          response.getStatusCode(),
          stageData.cancelEndpoint,
          stageExecution.getExecution().getId(),
          stageExecution.getId());
      return response;
    } catch (HttpStatusCodeException e) {
      log.warn(
          "Failed to cancel webhook {} with statusCode={}",
          stageData.cancelEndpoint,
          e.getStatusCode().value(),
          e);
    } catch (Exception e) {
      log.warn("Failed to cancel webhook {}", stageData.cancelEndpoint, e);
    }
    return null;
  }

  private RestTemplateData getRestTemplateData(
      WebhookTaskType taskType, StageExecution stageExecution) {
    String destinationUrl = null;
    for (RestTemplateProvider provider : restTemplateProviders) {
      WebhookStage.StageData stageData =
          (WebhookStage.StageData) stageExecution.mapTo(provider.getStageDataType());
      HttpHeaders headers = buildHttpHeaders(stageData.customHeaders);
      HttpMethod httpMethod = HttpMethod.GET;
      HttpEntity<Object> payloadEntity = null;
      switch (taskType) {
        case CREATE:
          destinationUrl = stageData.url;
          payloadEntity = new HttpEntity<>(stageData.payload, headers);
          httpMethod = stageData.method;
          break;
        case MONITOR:
          destinationUrl =
              Optional.ofNullable(stageData.getStatusEndpoint())
                  .map(String::trim)
                  .orElse(stageData.getUrl());
          payloadEntity = new HttpEntity<>(null, headers);
          break;
        case CANCEL:
          destinationUrl = stageData.cancelEndpoint;
          payloadEntity = new HttpEntity<>(stageData.cancelPayload, headers);
          httpMethod = stageData.cancelMethod;
          break;
        default:
          destinationUrl = "";
          break;
      }

      // Return on the first match
      if (destinationUrl != null
          && !destinationUrl.isEmpty()
          && provider.supports(destinationUrl, stageData)) {
        URI validatedUri =
            userConfiguredUrlRestrictions.validateURI(
                provider.getTargetUrl(destinationUrl, stageData));
        RestTemplate restTemplate = provider.getRestTemplate(destinationUrl);
        return new RestTemplateData(
            restTemplate, validatedUri, httpMethod, payloadEntity, stageData);
      }
    }

    // No providers found.
    log.warn(
        "Unable to find rest template provider for url: {} , executionId: {}, webhookTaskType: {}",
        destinationUrl,
        stageExecution.getId(),
        taskType);
    return null;
  }

  public List<WebhookProperties.PreconfiguredWebhook> getPreconfiguredWebhooks() {
    return preconfiguredWebhookProperties.getPreconfigured().stream()
        .filter(WebhookProperties.PreconfiguredWebhook::isEnabled)
        .collect(Collectors.toList());
  }

  private static HttpHeaders buildHttpHeaders(Map<String, Object> customHeaders) {
    HttpHeaders headers = new HttpHeaders();
    if (customHeaders != null) {
      customHeaders.forEach(
          (key, value) -> {
            if (headerDenyList.contains(key.toUpperCase())) {
              return;
            }
            if (value instanceof List) {
              headers.put(key, (List<String>) value);
            } else {
              headers.add(key, value.toString());
            }
          });
    }
    return headers;
  }

  private static class RestTemplateData {

    final RestTemplate restTemplate;
    final URI validatedUri;
    final HttpMethod httpMethod;
    final HttpEntity<Object> payloadEntity;
    final WebhookStage.StageData stageData;

    RestTemplateData(
        RestTemplate restTemplate,
        URI validatedUri,
        HttpMethod httpMethod,
        HttpEntity<Object> payloadEntity,
        WebhookStage.StageData stageData) {
      this.restTemplate = restTemplate;
      this.validatedUri = validatedUri;
      this.httpMethod = httpMethod;
      this.payloadEntity = payloadEntity;
      this.stageData = stageData;
    }

    public ResponseEntity<Object> exchange() {
      return this.restTemplate.exchange(validatedUri, httpMethod, payloadEntity, Object.class);
    }
  }

  private enum WebhookTaskType {
    CREATE,
    MONITOR,
    CANCEL
  }
}
