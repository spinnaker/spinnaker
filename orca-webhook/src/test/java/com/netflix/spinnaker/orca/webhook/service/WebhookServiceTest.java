/*
 * Copyright 2024 Salesforce, Inc.
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
package com.netflix.spinnaker.orca.webhook.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.config.UserConfiguredUrlRestrictions;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import com.netflix.spinnaker.orca.webhook.config.WebhookConfiguration;
import com.netflix.spinnaker.orca.webhook.config.WebhookProperties;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

class WebhookServiceTest {

  /** See https://wiremock.org/docs/junit-jupiter/#advanced-usage---programmatic. */
  @RegisterExtension
  private static WireMockExtension apiProvider =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();

  private WebhookProperties webhookProperties = new WebhookProperties();

  private OkHttpClientConfigurationProperties okHttpClientConfigurationProperties =
      new OkHttpClientConfigurationProperties();

  private WebhookConfiguration webhookConfiguration = new WebhookConfiguration(webhookProperties);

  private UserConfiguredUrlRestrictions userConfiguredUrlRestrictions =
      new UserConfiguredUrlRestrictions.Builder()
          .withRejectLocalhost(false)
          .withAllowedHostnamesRegex(".*")
          .build();

  private WebhookService webhookService;

  @BeforeEach
  void init(TestInfo testInfo) throws Exception {
    System.out.println("--------------- Test " + testInfo.getDisplayName());

    ClientHttpRequestFactory requestFactory =
        webhookConfiguration.webhookRequestFactory(
            okHttpClientConfigurationProperties, userConfiguredUrlRestrictions);

    RestTemplateProvider restTemplateProvider =
        new DefaultRestTemplateProvider(webhookConfiguration.restTemplate(requestFactory));

    webhookService =
        new WebhookService(
            List.of(restTemplateProvider), userConfiguredUrlRestrictions, webhookProperties);
  }

  @Test
  void testAllowedRequestsEnabledTrueEmptyList() {
    webhookProperties.setAllowedRequestsEnabled(true);

    String url = "https://localhost"; // arbitrary, but needs to include a resolvable hostname

    // The StageExecutionImpl constructor mutates the map, so use a mutable map.
    Map<String, Object> webhookStageData =
        new HashMap<>(Map.of("url", url, "method", HttpMethod.GET));
    StageExecution stage =
        new StageExecutionImpl(null, "webhook", "test-webhook-stage", webhookStageData);

    Throwable thrown = catchThrowable(() -> webhookService.callWebhook(stage));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("uri: '" + url + "' not allowed");

    apiProvider.verify(0, RequestPatternBuilder.allRequests());
  }

  @Test
  void testAllowedRequestsMatchingMethodAndUrl() throws Exception {
    webhookProperties.setAllowedRequestsEnabled(true);
    WebhookProperties.AllowedRequest allowedRequest = new WebhookProperties.AllowedRequest();
    allowedRequest.setHttpMethods(List.of("POST"));
    allowedRequest.setUrlPrefix("http://localhost:" + apiProvider.getPort() + "/path/to/an/");
    webhookProperties.setAllowedRequests(List.of(allowedRequest));

    String path = "/path/to/an/endpoint";
    String url = apiProvider.baseUrl() + path;

    String bodyStr = "{ \"foo\": \"bar\" }";
    apiProvider.stubFor(
        post(urlMatching(path))
            .willReturn(aResponse().withStatus(HttpStatus.OK.value()).withBody(bodyStr)));

    // The StageExecutionImpl constructor mutates the map, so use a mutable map.
    Map<String, Object> webhookStageData =
        new HashMap<>(Map.of("url", url, "method", HttpMethod.POST));
    StageExecution stage =
        new StageExecutionImpl(null, "webhook", "test-webhook-stage", webhookStageData);

    ResponseEntity<Object> result = webhookService.callWebhook(stage);

    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    var body = mapper.readValue(result.getBody().toString(), Map.class);
    assertThat(body.get("foo")).isEqualTo("bar");

    apiProvider.verify(postRequestedFor(urlPathEqualTo(path)));
  }

  @Test
  void testAllowedRequestsUrlMatchesButNotMethod() throws Exception {
    webhookProperties.setAllowedRequestsEnabled(true);
    WebhookProperties.AllowedRequest allowedRequest = new WebhookProperties.AllowedRequest();
    allowedRequest.setHttpMethods(List.of("POST"));
    allowedRequest.setUrlPrefix("http://localhost:" + apiProvider.getPort() + "/path/to/an/");
    webhookProperties.setAllowedRequests(List.of(allowedRequest));

    String path = "/path/to/an/endpoint";
    String url = apiProvider.baseUrl() + path;

    String bodyStr = "{ \"foo\": \"bar\" }";
    apiProvider.stubFor(
        post(urlMatching(path))
            .willReturn(aResponse().withStatus(HttpStatus.OK.value()).withBody(bodyStr)));

    // The StageExecutionImpl constructor mutates the map, so use a mutable map.
    Map<String, Object> webhookStageData =
        new HashMap<>(Map.of("url", url, "method", HttpMethod.PUT));
    StageExecution stage =
        new StageExecutionImpl(null, "webhook", "test-webhook-stage", webhookStageData);

    Throwable thrown = catchThrowable(() -> webhookService.callWebhook(stage));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("uri: '" + url + "' not allowed");

    apiProvider.verify(0, RequestPatternBuilder.allRequests());
  }

  @Test
  void testAllowedRequestsMethodlMatchesButNotUrl() throws Exception {
    webhookProperties.setAllowedRequestsEnabled(true);
    WebhookProperties.AllowedRequest allowedRequest = new WebhookProperties.AllowedRequest();
    allowedRequest.setHttpMethods(List.of("POST"));
    allowedRequest.setUrlPrefix("http://localhost:" + apiProvider.getPort() + "/path/to/an/");
    webhookProperties.setAllowedRequests(List.of(allowedRequest));

    String path = "/path/to/another/endpoint";
    String url = apiProvider.baseUrl() + path;

    String bodyStr = "{ \"foo\": \"bar\" }";
    apiProvider.stubFor(
        post(urlMatching(path))
            .willReturn(aResponse().withStatus(HttpStatus.OK.value()).withBody(bodyStr)));

    // The StageExecutionImpl constructor mutates the map, so use a mutable map.
    Map<String, Object> webhookStageData =
        new HashMap<>(Map.of("url", url, "method", HttpMethod.POST));
    StageExecution stage =
        new StageExecutionImpl(null, "webhook", "test-webhook-stage", webhookStageData);

    Throwable thrown = catchThrowable(() -> webhookService.callWebhook(stage));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("uri: '" + url + "' not allowed");

    apiProvider.verify(0, RequestPatternBuilder.allRequests());
  }
}
