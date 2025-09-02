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

import static com.netflix.spinnaker.orca.webhook.config.WebhookProperties.MatchStrategy.PATTERN_MATCHES;

import com.google.common.annotations.VisibleForTesting;
import com.netflix.spinnaker.kork.exceptions.SpinnakerException;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.kork.web.filters.ProvidedIdRequestFilterConfigurationProperties;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.config.UserConfiguredUrlRestrictions;
import com.netflix.spinnaker.orca.webhook.config.WebhookProperties;
import com.netflix.spinnaker.orca.webhook.pipeline.WebhookStage;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
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

  private final List<RestTemplateProvider> restTemplateProviders;

  private final UserConfiguredUrlRestrictions userConfiguredUrlRestrictions;

  private final WebhookProperties webhookProperties;

  private final OortService oortService;

  private final Optional<WebhookAccountProcessor> webhookAccountProcessor;

  private final ProvidedIdRequestFilterConfigurationProperties
      providedIdRequestFilterConfigurationProperties;

  /**
   * Keys are urlPattern properties from the allow list. Each value is the corresponding compiled
   * Pattern.
   */
  private final Map<String, Pattern> urlPatterns;

  @Autowired
  public WebhookService(
      List<RestTemplateProvider> restTemplateProviders,
      UserConfiguredUrlRestrictions userConfiguredUrlRestrictions,
      WebhookProperties webhookProperties,
      OortService oortService,
      Optional<WebhookAccountProcessor> webhookAccountProcessor,
      ProvidedIdRequestFilterConfigurationProperties
          providedIdRequestFilterConfigurationProperties) {
    this.restTemplateProviders = restTemplateProviders;
    this.userConfiguredUrlRestrictions = userConfiguredUrlRestrictions;
    this.webhookProperties = webhookProperties;
    this.oortService = oortService;
    this.webhookAccountProcessor = webhookAccountProcessor;
    this.providedIdRequestFilterConfigurationProperties =
        providedIdRequestFilterConfigurationProperties;

    // If it's enabled, validate the allow list and compile regexes.
    if (webhookProperties.isAllowedRequestsEnabled()) {
      webhookProperties.getAllowedRequests().forEach(this::validateAllowedRequest);
      this.urlPatterns =
          webhookProperties.getAllowedRequests().stream()
              .filter(allowedRequest -> allowedRequest.getMatchStrategy() == PATTERN_MATCHES)
              .collect(
                  Collectors.toUnmodifiableMap(
                      WebhookProperties.AllowedRequest::getUrlPattern,
                      allowedRequest -> Pattern.compile(allowedRequest.getUrlPattern())));
    } else {
      this.urlPatterns = Map.of();
    }
  }

  /** Validate an allowed request. Throw an exception if it's invalid. */
  private void validateAllowedRequest(WebhookProperties.AllowedRequest allowedRequest) {
    switch (allowedRequest.getMatchStrategy()) {
      case STARTS_WITH:
        if (allowedRequest.getUrlPrefix() == null) {
          throw new IllegalArgumentException(
              "urlPrefix must not be null with STARTS_WITH strategy");
        }
        break;
      case PATTERN_MATCHES:
        if (allowedRequest.getUrlPattern() == null) {
          throw new IllegalArgumentException(
              "urlPattern must not be null with PATTERN_MATCHES strategy");
        }
        break;
      default:
        throw new IllegalArgumentException(
            "unknown match strategy " + allowedRequest.getMatchStrategy());
    }
  }

  /**
   * Only exists for testing. CreateWebhookTask has RestTemplateData, so it calls the overload of
   * callWebhook that takes that.
   */
  @VisibleForTesting
  public ResponseEntity<Object> callWebhook(StageExecution stageExecution) {
    RestTemplateData restTemplateData = getRestTemplateData(WebhookTaskType.CREATE, stageExecution);
    return callWebhook(restTemplateData);
  }

  public ResponseEntity<Object> callWebhook(RestTemplateData restTemplateData) {
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
    WebhookStage.StageData stageData = restTemplateData.getStageData();
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

  public RestTemplateData getRestTemplateData(
      WebhookTaskType taskType, StageExecution stageExecution) {
    String destinationUrl = null;
    for (RestTemplateProvider provider : restTemplateProviders) {
      WebhookStage.StageData stageData =
          (WebhookStage.StageData) stageExecution.mapTo(provider.getStageDataType());

      HttpHeaders headers = null;
      Map<String, Object> accountDetails = null;
      if (webhookProperties.isValidateAccount() && StringUtils.isNotBlank(stageData.account)) {
        // Expect this to throw an exception if the current user is not
        // authorized to use the given account, or the account doesn't exist.
        accountDetails =
            Retrofit2SyncCall.execute(
                oortService.getCredentialsAuthorized(stageData.account, true /* expand */));
      }

      Map<String, Object> headersMap =
          webhookProperties.isIncludeAdditionalHeaders()
              ? combineProvidedAdditionalHeaders(stageData.customHeaders)
              : stageData.customHeaders;

      if (webhookAccountProcessor.isPresent()) {
        headers =
            webhookAccountProcessor.get().getHeaders(stageData.account, accountDetails, headersMap);
      } else {
        headers = buildHttpHeaders(headersMap);
      }
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

        Optional<WebhookProperties.AllowedRequest> allowedRequest =
            getAllowedRequest(httpMethod, validatedUri);
        if (webhookProperties.isAllowedRequestsEnabled()) {
          if (allowedRequest.isEmpty()) {
            String message =
                String.format(
                    "http method '%s', uri: '%s' not allowed",
                    httpMethod.toString(), validatedUri.toString());
            log.info(message);
            throw new IllegalArgumentException(message);
          }
        }

        RestTemplate restTemplate = provider.getRestTemplate(destinationUrl);
        return new RestTemplateData(
            restTemplate, validatedUri, httpMethod, payloadEntity, stageData, allowedRequest);
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
    return webhookProperties.getPreconfigured().stream()
        .filter(WebhookProperties.PreconfiguredWebhook::isEnabled)
        .collect(Collectors.toList());
  }

  /**
   * Return the allow list entry corresponding to the given httpMethod + uri, or empty if there's no
   * corresponding entry. If the allowed requests is disabled altogether, return empty.
   */
  private Optional<WebhookProperties.AllowedRequest> getAllowedRequest(
      HttpMethod httpMethod, URI uri) {
    if (!webhookProperties.isAllowedRequestsEnabled()) {
      return Optional.empty();
    }

    return webhookProperties.getAllowedRequests().stream()
        .filter(
            allowedRequest ->
                allowedRequest.getHttpMethods().contains(httpMethod.toString())
                    && uriMatches(allowedRequest, uri))
        .findFirst();
  }

  /**
   * Determine if an AllowedRequest allows a given uri
   *
   * @param allowedRequest the AllowRequest to use
   * @param uri the URI to consider
   * @return true if the uri is allowed, false otherwise
   */
  private boolean uriMatches(WebhookProperties.AllowedRequest allowedRequest, URI uri) {
    switch (allowedRequest.getMatchStrategy()) {
      case STARTS_WITH:
        String urlPrefix = allowedRequest.getUrlPrefix();
        boolean startsWithRetval = uri.toString().startsWith(urlPrefix);
        log.debug(
            "uri '{}' {} '{}'",
            uri.toString(),
            startsWithRetval ? "starts with" : "does not start with",
            urlPrefix);
        return startsWithRetval;
      case PATTERN_MATCHES:
        String patternString = allowedRequest.getUrlPattern();
        Pattern pattern = urlPatterns.get(patternString);
        if (pattern == null) {
          throw new IllegalStateException("no compiled Pattern for '" + patternString + "'");
        }
        boolean patternMatchesRetval = pattern.matcher(uri.toString()).matches();
        log.debug(
            "uri '{}' {} pattern '{}'",
            uri.toString(),
            patternMatchesRetval ? "matches" : "does not match",
            pattern.toString());
        return patternMatchesRetval;
      default:
        throw new IllegalArgumentException(
            "unknown match strategy " + allowedRequest.getMatchStrategy());
    }
  }

  /**
   * Combine additional headers that ProvidedIdRequestFilter put in the MDC with custom headers.
   * Note that headers from the MDC take precedence, in a strict way. In other words, headers
   * specified as additional ONLY come from the MDC. If they're present in customHeaders, they're
   * ignored. This way receivers of http requests from Webhook stages can trust the values of those
   * headers.
   *
   * <p>This is a no-op if the ProvidedIdRequestFilter is disabled. In other words, the return value
   * is the customHeaders argument.
   */
  private Map<String, Object> combineProvidedAdditionalHeaders(Map<String, Object> customHeaders) {
    if (!providedIdRequestFilterConfigurationProperties.isEnabled()) {
      return customHeaders;
    }

    // Start with the headers from ProvidedIdRequestFilter
    Map<String, Object> combinedHeaders =
        providedIdRequestFilterConfigurationProperties.getAdditionalHeaders().stream()
            .filter(additionalHeader -> StringUtils.isNotBlank(MDC.get(additionalHeader)))
            .collect(Collectors.toMap(Function.identity(), MDC::get));

    if (customHeaders == null) {
      return combinedHeaders;
    }

    Set<String> additionalHeaderNames =
        Set.copyOf(providedIdRequestFilterConfigurationProperties.getAdditionalHeaders());

    customHeaders.keySet().stream()
        .filter(Predicate.not(additionalHeaderNames::contains))
        .forEach(
            customHeaderName ->
                combinedHeaders.put(customHeaderName, customHeaders.get(customHeaderName)));

    return combinedHeaders;
  }

  private static HttpHeaders buildHttpHeaders(Map<String, Object> headersMap) {
    HttpHeaders headers = new HttpHeaders();
    if (headersMap != null) {
      headersMap.forEach(
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

  public enum WebhookTaskType {
    CREATE,
    MONITOR,
    CANCEL
  }
}
