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
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.temporaryRedirect;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.matching.MatchResult;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.netflix.spinnaker.kork.web.filters.ProvidedIdRequestFilterConfigurationProperties;
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.config.UserConfiguredUrlRestrictions;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import com.netflix.spinnaker.orca.webhook.config.WebhookConfiguration;
import com.netflix.spinnaker.orca.webhook.config.WebhookProperties;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import retrofit2.mock.Calls;

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

  private RestTemplateProvider restTemplateProvider;

  private OortService oortService = mock(OortService.class);

  private WebhookAccountProcessor webhookAccountProcessor = mock(WebhookAccountProcessor.class);

  private ProvidedIdRequestFilterConfigurationProperties
      providedIdRequestFilterConfigurationProperties =
          new ProvidedIdRequestFilterConfigurationProperties();

  @BeforeEach
  void init(TestInfo testInfo) throws Exception {
    System.out.println("--------------- Test " + testInfo.getDisplayName());

    ClientHttpRequestFactory requestFactory =
        webhookConfiguration.webhookRequestFactory(
            mock(Environment.class),
            okHttpClientConfigurationProperties,
            userConfiguredUrlRestrictions,
            webhookProperties);

    restTemplateProvider =
        new DefaultRestTemplateProvider(webhookConfiguration.restTemplate(requestFactory));
  }

  @Test
  void testAllowedRequestsEnabledTrueEmptyList() {
    webhookProperties.setAllowedRequestsEnabled(true);

    WebhookService webhookService =
        new WebhookService(
            List.of(restTemplateProvider),
            userConfiguredUrlRestrictions,
            webhookProperties,
            oortService,
            Optional.empty(),
            providedIdRequestFilterConfigurationProperties);

    String url =
        "https://localhost/"; // arbitrary, but needs to include a resolvable hostname. MUST be a
    // normalized URI for tests to work

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
  void testAllowedRequestsMatchingMethodAndUrlStartsWith() throws Exception {
    webhookProperties.setAllowedRequestsEnabled(true);
    WebhookProperties.AllowedRequest allowedRequest = new WebhookProperties.AllowedRequest();
    allowedRequest.setHttpMethods(List.of("POST"));
    allowedRequest.setMatchStrategy(WebhookProperties.MatchStrategy.STARTS_WITH);
    allowedRequest.setUrlPrefix("http://localhost:" + apiProvider.getPort() + "/path/to/an/");
    webhookProperties.setAllowedRequests(List.of(allowedRequest));

    WebhookService webhookService =
        new WebhookService(
            List.of(restTemplateProvider),
            userConfiguredUrlRestrictions,
            webhookProperties,
            oortService,
            Optional.empty(),
            providedIdRequestFilterConfigurationProperties);

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
  void testAllowedRequestsNullUrlPrefix() throws Exception {
    webhookProperties.setAllowedRequestsEnabled(true);
    WebhookProperties.AllowedRequest allowedRequest = new WebhookProperties.AllowedRequest();
    allowedRequest.setHttpMethods(List.of("POST"));
    allowedRequest.setMatchStrategy(WebhookProperties.MatchStrategy.STARTS_WITH);
    assertThat(allowedRequest.getUrlPrefix()).isNull();
    webhookProperties.setAllowedRequests(List.of(allowedRequest));

    Throwable thrown =
        catchThrowable(
            () ->
                new WebhookService(
                    List.of(restTemplateProvider),
                    userConfiguredUrlRestrictions,
                    webhookProperties,
                    oortService,
                    Optional.empty(),
                    providedIdRequestFilterConfigurationProperties));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("urlPrefix must not be null with STARTS_WITH strategy");

    apiProvider.verify(0, RequestPatternBuilder.allRequests());
  }

  @Test
  void testAllowedRequestsEmptyUrlPrefix() throws Exception {
    webhookProperties.setAllowedRequestsEnabled(true);
    WebhookProperties.AllowedRequest allowedRequest = new WebhookProperties.AllowedRequest();
    allowedRequest.setHttpMethods(List.of("POST"));
    allowedRequest.setMatchStrategy(WebhookProperties.MatchStrategy.STARTS_WITH);
    allowedRequest.setUrlPrefix("");
    webhookProperties.setAllowedRequests(List.of(allowedRequest));

    WebhookService webhookService =
        new WebhookService(
            List.of(restTemplateProvider),
            userConfiguredUrlRestrictions,
            webhookProperties,
            oortService,
            Optional.empty(),
            providedIdRequestFilterConfigurationProperties);

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
  void testAllowedRequestsEmptyUrlPattern() throws Exception {
    webhookProperties.setAllowedRequestsEnabled(true);
    WebhookProperties.AllowedRequest allowedRequest = new WebhookProperties.AllowedRequest();
    allowedRequest.setHttpMethods(List.of("POST"));
    allowedRequest.setMatchStrategy(WebhookProperties.MatchStrategy.PATTERN_MATCHES);
    allowedRequest.setUrlPattern("");
    webhookProperties.setAllowedRequests(List.of(allowedRequest));

    WebhookService webhookService =
        new WebhookService(
            List.of(restTemplateProvider),
            userConfiguredUrlRestrictions,
            webhookProperties,
            oortService,
            Optional.empty(),
            providedIdRequestFilterConfigurationProperties);

    String path = "/path/to/an/endpoint";
    String url = apiProvider.baseUrl() + path;

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

  @Test
  void testAllowedRequestsNullUrlPattern() throws Exception {
    webhookProperties.setAllowedRequestsEnabled(true);
    WebhookProperties.AllowedRequest allowedRequest = new WebhookProperties.AllowedRequest();
    allowedRequest.setHttpMethods(List.of("POST"));
    allowedRequest.setMatchStrategy(WebhookProperties.MatchStrategy.PATTERN_MATCHES);
    assertThat(allowedRequest.getUrlPattern()).isNull();
    webhookProperties.setAllowedRequests(List.of(allowedRequest));

    Throwable thrown =
        catchThrowable(
            () ->
                new WebhookService(
                    List.of(restTemplateProvider),
                    userConfiguredUrlRestrictions,
                    webhookProperties,
                    oortService,
                    Optional.empty(),
                    providedIdRequestFilterConfigurationProperties));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("urlPattern must not be null with PATTERN_MATCHES strategy");

    apiProvider.verify(0, RequestPatternBuilder.allRequests());
  }

  @Test
  void testAllowedRequestsInvalidUrlPattern() throws Exception {
    webhookProperties.setAllowedRequestsEnabled(true);
    WebhookProperties.AllowedRequest allowedRequest = new WebhookProperties.AllowedRequest();
    allowedRequest.setHttpMethods(List.of("POST"));
    allowedRequest.setMatchStrategy(WebhookProperties.MatchStrategy.PATTERN_MATCHES);
    String invalidPattern = "[ is not a valid pattern";
    allowedRequest.setUrlPattern(invalidPattern);
    webhookProperties.setAllowedRequests(List.of(allowedRequest));

    Throwable thrown =
        catchThrowable(
            () ->
                new WebhookService(
                    List.of(restTemplateProvider),
                    userConfiguredUrlRestrictions,
                    webhookProperties,
                    oortService,
                    Optional.empty(),
                    providedIdRequestFilterConfigurationProperties));

    assertThat(thrown)
        .isInstanceOf(PatternSyntaxException.class)
        .hasMessageContaining(invalidPattern);

    apiProvider.verify(0, RequestPatternBuilder.allRequests());
  }

  @Test
  void testAllowedRequestsMatchingMethodAndUrlPatternMatches() throws Exception {
    webhookProperties.setAllowedRequestsEnabled(true);
    WebhookProperties.AllowedRequest allowedRequest = new WebhookProperties.AllowedRequest();
    allowedRequest.setHttpMethods(List.of("POST"));
    allowedRequest.setMatchStrategy(WebhookProperties.MatchStrategy.PATTERN_MATCHES);
    allowedRequest.setUrlPattern(
        "http://localhost:" + apiProvider.getPort() + "/[a-zA-Z]+-[a-z0-9]+/to/.*");
    webhookProperties.setAllowedRequests(List.of(allowedRequest));

    WebhookService webhookService =
        new WebhookService(
            List.of(restTemplateProvider),
            userConfiguredUrlRestrictions,
            webhookProperties,
            oortService,
            Optional.empty(),
            providedIdRequestFilterConfigurationProperties);

    String path = "/abcABC-def123/to/an/endpoint";
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
    allowedRequest.setMatchStrategy(WebhookProperties.MatchStrategy.STARTS_WITH);
    allowedRequest.setUrlPrefix("http://localhost:" + apiProvider.getPort() + "/path/to/an/");
    webhookProperties.setAllowedRequests(List.of(allowedRequest));

    WebhookService webhookService =
        new WebhookService(
            List.of(restTemplateProvider),
            userConfiguredUrlRestrictions,
            webhookProperties,
            oortService,
            Optional.empty(),
            providedIdRequestFilterConfigurationProperties);

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
  void testAllowedRequestsMethodMatchesButNotUrl() throws Exception {
    webhookProperties.setAllowedRequestsEnabled(true);
    WebhookProperties.AllowedRequest allowedRequest = new WebhookProperties.AllowedRequest();
    allowedRequest.setHttpMethods(List.of("POST"));
    allowedRequest.setMatchStrategy(WebhookProperties.MatchStrategy.STARTS_WITH);
    allowedRequest.setUrlPrefix("http://localhost:" + apiProvider.getPort() + "/path/to/an/");
    webhookProperties.setAllowedRequests(List.of(allowedRequest));

    WebhookService webhookService =
        new WebhookService(
            List.of(restTemplateProvider),
            userConfiguredUrlRestrictions,
            webhookProperties,
            oortService,
            Optional.empty(),
            providedIdRequestFilterConfigurationProperties);

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

  @Test
  void testRequestHeadersTooBig() throws Exception {
    // Even with an empty body, even one request header (e.g. Content-Length: 0) is too big.
    webhookProperties.setMaxRequestBytes(1L);

    WebhookService webhookService =
        new WebhookService(
            List.of(restTemplateProvider),
            userConfiguredUrlRestrictions,
            webhookProperties,
            oortService,
            Optional.empty(),
            providedIdRequestFilterConfigurationProperties);

    String url = apiProvider.baseUrl();

    // The StageExecutionImpl constructor mutates the map, so use a mutable map.
    Map<String, Object> webhookStageData =
        new HashMap<>(Map.of("url", url, "method", HttpMethod.GET));
    StageExecution stage =
        new StageExecutionImpl(null, "webhook", "test-webhook-stage", webhookStageData);

    Throwable thrown = catchThrowable(() -> webhookService.callWebhook(stage));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("rejecting request to " + url);

    apiProvider.verify(0, RequestPatternBuilder.allRequests());
  }

  @Test
  void testRequestHeadersAndBodyTooBig() throws Exception {
    // Empirically, this is bigger than the headers in this test, and smaller than headers + body.
    webhookProperties.setMaxRequestBytes(235L);

    WebhookService webhookService =
        new WebhookService(
            List.of(restTemplateProvider),
            userConfiguredUrlRestrictions,
            webhookProperties,
            oortService,
            Optional.empty(),
            providedIdRequestFilterConfigurationProperties);

    String url = apiProvider.baseUrl();

    String payload = "{ \"foo\": \"bar\" }";

    // The StageExecutionImpl constructor mutates the map, so use a mutable map.
    Map<String, Object> webhookStageData =
        new HashMap<>(Map.of("url", url, "method", HttpMethod.POST, "payload", payload));
    StageExecution stage =
        new StageExecutionImpl(null, "webhook", "test-webhook-stage", webhookStageData);

    Throwable thrown = catchThrowable(() -> webhookService.callWebhook(stage));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("rejecting request to " + url);

    apiProvider.verify(0, RequestPatternBuilder.allRequests());
  }

  @Test
  void testRequestHeadersAndBodySmallEnough() throws Exception {
    // Verify that request processing still functions properly after verifying the request size.

    // Empirically, this is bigger than the headers in this test, and bigger
    // than headers + body.
    webhookProperties.setMaxRequestBytes(500L);

    WebhookService webhookService =
        new WebhookService(
            List.of(restTemplateProvider),
            userConfiguredUrlRestrictions,
            webhookProperties,
            oortService,
            Optional.empty(),
            providedIdRequestFilterConfigurationProperties);

    String path = "/path/to/some/endpoint";
    String url = apiProvider.baseUrl() + path;

    String payload = "{ \"foo\": \"bar\" }";

    String responseBodyStr = "{ \"hello\": \"there\" }";
    apiProvider.stubFor(
        post(urlMatching(path))
            .willReturn(aResponse().withStatus(HttpStatus.OK.value()).withBody(responseBodyStr)));

    // The StageExecutionImpl constructor mutates the map, so use a mutable map.
    Map<String, Object> webhookStageData =
        new HashMap<>(Map.of("url", url, "method", HttpMethod.POST, "payload", payload));
    StageExecution stage =
        new StageExecutionImpl(null, "webhook", "test-webhook-stage", webhookStageData);

    ResponseEntity<Object> result = webhookService.callWebhook(stage);

    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    var body = mapper.readValue(result.getBody().toString(), Map.class);
    assertThat(body.get("hello")).isEqualTo("there");

    apiProvider.verify(postRequestedFor(urlPathEqualTo(path)));
  }

  @Test
  void testResponseHeadersTooBig() throws Exception {
    // Even with an empty body, even one response header (e.g. Matched-Stub-Id) is too big.
    webhookProperties.setMaxResponseBytes(1L);

    WebhookService webhookService =
        new WebhookService(
            List.of(restTemplateProvider),
            userConfiguredUrlRestrictions,
            webhookProperties,
            oortService,
            Optional.empty(),
            providedIdRequestFilterConfigurationProperties);

    String path = "/some/path";
    String url = apiProvider.baseUrl() + path;

    apiProvider.stubFor(
        get(urlMatching(path))
            .willReturn(aResponse().withStatus(HttpStatus.OK.value()).withBody("")));

    // The StageExecutionImpl constructor mutates the map, so use a mutable map.
    Map<String, Object> webhookStageData =
        new HashMap<>(Map.of("url", url, "method", HttpMethod.GET));
    StageExecution stage =
        new StageExecutionImpl(null, "webhook", "test-webhook-stage", webhookStageData);

    Throwable thrown = catchThrowable(() -> webhookService.callWebhook(stage));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("rejecting response from " + url);

    apiProvider.verify(getRequestedFor(urlPathEqualTo(path)));
  }

  @Test
  void testResponseHeadersAndBodyTooBig() throws Exception {
    // Empirically, this is bigger than the headers in this test, and smaller than headers + body.
    webhookProperties.setMaxResponseBytes(150L);

    WebhookService webhookService =
        new WebhookService(
            List.of(restTemplateProvider),
            userConfiguredUrlRestrictions,
            webhookProperties,
            oortService,
            Optional.empty(),
            providedIdRequestFilterConfigurationProperties);

    String path = "/some/path";
    String url = apiProvider.baseUrl() + path;

    apiProvider.stubFor(
        get(urlMatching(path))
            .willReturn(
                aResponse().withStatus(HttpStatus.OK.value()).withBody("test response body")));

    // The StageExecutionImpl constructor mutates the map, so use a mutable map.
    Map<String, Object> webhookStageData =
        new HashMap<>(Map.of("url", url, "method", HttpMethod.GET));
    StageExecution stage =
        new StageExecutionImpl(null, "webhook", "test-webhook-stage", webhookStageData);

    Throwable thrown = catchThrowable(() -> webhookService.callWebhook(stage));

    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("rejecting response from " + url);

    apiProvider.verify(getRequestedFor(urlPathEqualTo(path)));
  }

  @Test
  void testResponseHeadersAndBodySmallEnough() throws Exception {
    // Verify that response processing still functions properly after verifying
    // the response size.

    // Empirically, this is bigger than the headers in this test, and bigger
    // than headers + body.
    webhookProperties.setMaxResponseBytes(500L);

    WebhookService webhookService =
        new WebhookService(
            List.of(restTemplateProvider),
            userConfiguredUrlRestrictions,
            webhookProperties,
            oortService,
            Optional.empty(),
            providedIdRequestFilterConfigurationProperties);

    String path = "/some/path";
    String url = apiProvider.baseUrl() + path;

    String responseBodyString = "test response body";
    apiProvider.stubFor(
        get(urlMatching(path))
            .willReturn(
                aResponse().withStatus(HttpStatus.OK.value()).withBody(responseBodyString)));

    // The StageExecutionImpl constructor mutates the map, so use a mutable map.
    Map<String, Object> webhookStageData =
        new HashMap<>(Map.of("url", url, "method", HttpMethod.GET));
    StageExecution stage =
        new StageExecutionImpl(null, "webhook", "test-webhook-stage", webhookStageData);

    ResponseEntity<Object> result = webhookService.callWebhook(stage);

    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getBody().toString()).isEqualTo(responseBodyString);

    apiProvider.verify(getRequestedFor(urlPathEqualTo(path)));
  }

  @Test
  void testDontFollowRedirects() throws Exception {
    webhookProperties.setFollowRedirects(false);

    WebhookService webhookService =
        new WebhookService(
            List.of(restTemplateProvider),
            userConfiguredUrlRestrictions,
            webhookProperties,
            oortService,
            Optional.empty(),
            providedIdRequestFilterConfigurationProperties);

    String path = "/some/path";
    String url = apiProvider.baseUrl() + path;

    String redirectUrl = "https://anywhere";
    apiProvider.stubFor(get(urlMatching(path)).willReturn(temporaryRedirect(redirectUrl)));

    // The StageExecutionImpl constructor mutates the map, so use a mutable map.
    Map<String, Object> webhookStageData =
        new HashMap<>(Map.of("url", url, "method", HttpMethod.GET));
    StageExecution stage =
        new StageExecutionImpl(null, "webhook", "test-webhook-stage", webhookStageData);

    Throwable thrown = catchThrowable(() -> webhookService.callWebhook(stage));

    assertThat(thrown)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("redirects disabled, not visiting " + redirectUrl);

    apiProvider.verify(getRequestedFor(urlPathEqualTo(path)));
  }

  @Test
  void testValidateAccountWithNoAccountProperty() throws Exception {
    webhookProperties.setValidateAccount(true);

    WebhookService webhookService =
        new WebhookService(
            List.of(restTemplateProvider),
            userConfiguredUrlRestrictions,
            webhookProperties,
            oortService,
            Optional.empty(),
            providedIdRequestFilterConfigurationProperties);

    WebhookService webhookServiceWithAccountProcessor =
        new WebhookService(
            List.of(restTemplateProvider),
            userConfiguredUrlRestrictions,
            webhookProperties,
            oortService,
            Optional.of(webhookAccountProcessor),
            providedIdRequestFilterConfigurationProperties);

    String path = "/some/path";
    String url = apiProvider.baseUrl() + path;

    String headerName = "foo";
    String headerValue = "bar";
    Map<String, Object> customHeaders = Map.of(headerName, headerValue);

    String accountProcessorHeaderName = "accountProcessorHeader";
    String accountProcessorHeaderValue = "blah";
    HttpHeaders accountProcessorHeaders = new HttpHeaders();
    accountProcessorHeaders.add(accountProcessorHeaderName, accountProcessorHeaderValue);

    // The StageExecutionImpl constructor mutates the map, so use a mutable map.
    Map<String, Object> webhookStageData =
        new HashMap<>(Map.of("url", url, "method", HttpMethod.GET, "customHeaders", customHeaders));
    StageExecution stage =
        new StageExecutionImpl(null, "webhook", "test-webhook-stage", webhookStageData);

    String responseBodyString = "test response body";
    apiProvider.stubFor(
        get(urlMatching(path))
            .willReturn(
                aResponse().withStatus(HttpStatus.OK.value()).withBody(responseBodyString)));

    // Test without an account processor
    webhookService.callWebhook(stage);

    // Expect that clouddriver never gets called since there's no account to
    // verify, and the webhook stage makes the http request.
    verifyNoInteractions(oortService);

    apiProvider.verify(
        getRequestedFor(urlPathEqualTo(path))
            .withoutHeader(accountProcessorHeaderName)
            .withHeader(headerName, equalTo(headerValue))
            .andMatching(
                r -> MatchResult.of(r.getHeaders().getHeader(headerName).isSingleValued())));

    // And with an account processor

    // Mock an account processor that ignores the given headers and returns its own
    when(webhookAccountProcessor.getHeaders(
            eq(null) /* account */, eq(null) /* accountDetails */, eq(customHeaders)))
        .thenReturn(new HttpHeaders(accountProcessorHeaders));

    webhookServiceWithAccountProcessor.callWebhook(stage);

    // Expect that clouddriver never gets called since there's no account to
    // verify, and the webhook stage makes the http request.
    verifyNoInteractions(oortService);

    // Expect that the account processor gets called once
    verify(webhookAccountProcessor)
        .getHeaders(eq(null) /* account */, eq(null) /* accountDetails */, eq(customHeaders));
    verifyNoMoreInteractions(webhookAccountProcessor);

    apiProvider.verify(
        getRequestedFor(urlPathEqualTo(path))
            .withoutHeader(headerName)
            .withHeader(accountProcessorHeaderName, equalTo(accountProcessorHeaderValue))
            .andMatching(
                r ->
                    MatchResult.of(
                        r.getHeaders().getHeader(accountProcessorHeaderName).isSingleValued())));
  }

  @ParameterizedTest(name = "{index} => testValidateAccountWithMissingAccount: account = ''{0}''")
  @NullSource
  @ValueSource(strings = {"", " "})
  void testValidateAccountWithMissingAccount(String account) throws Exception {
    webhookProperties.setValidateAccount(true);

    WebhookService webhookService =
        new WebhookService(
            List.of(restTemplateProvider),
            userConfiguredUrlRestrictions,
            webhookProperties,
            oortService,
            Optional.empty(),
            providedIdRequestFilterConfigurationProperties);

    WebhookService webhookServiceWithAccountProcessor =
        new WebhookService(
            List.of(restTemplateProvider),
            userConfiguredUrlRestrictions,
            webhookProperties,
            oortService,
            Optional.of(webhookAccountProcessor),
            providedIdRequestFilterConfigurationProperties);

    String path = "/some/path";
    String url = apiProvider.baseUrl() + path;

    String headerName = "foo";
    String headerValue = "bar";
    Map<String, Object> customHeaders = Map.of(headerName, headerValue);

    String accountProcessorHeaderName = "accountProcessorHeader";
    String accountProcessorHeaderValue = "blah";
    HttpHeaders accountProcessorHeaders = new HttpHeaders();
    accountProcessorHeaders.add(accountProcessorHeaderName, accountProcessorHeaderValue);

    // The StageExecutionImpl constructor mutates the map, so use a mutable map.
    // As well, Map.of requires non-null values, so build the HashMap this way.
    Map<String, Object> webhookStageData = new HashMap<>();
    webhookStageData.put("url", url);
    webhookStageData.put("method", HttpMethod.GET);
    webhookStageData.put("account", account);
    webhookStageData.put("customHeaders", customHeaders);

    StageExecution stage =
        new StageExecutionImpl(null, "webhook", "test-webhook-stage", webhookStageData);

    String responseBodyString = "test response body";
    apiProvider.stubFor(
        get(urlMatching(path))
            .willReturn(
                aResponse().withStatus(HttpStatus.OK.value()).withBody(responseBodyString)));

    // Test without an account processor
    webhookService.callWebhook(stage);

    // Expect that clouddriver never gets called since there's no account to
    // verify, and the webhook stage makes the http request.
    verifyNoInteractions(oortService);

    apiProvider.verify(
        getRequestedFor(urlPathEqualTo(path))
            .withoutHeader(accountProcessorHeaderName)
            .withHeader(headerName, equalTo(headerValue))
            .andMatching(
                r -> MatchResult.of(r.getHeaders().getHeader(headerName).isSingleValued())));

    // And with an account processor

    // Mock an account processor that ignores the given headers and returns its own
    when(webhookAccountProcessor.getHeaders(
            eq(account), eq(null) /* accountDetails */, eq(customHeaders)))
        .thenReturn(new HttpHeaders(accountProcessorHeaders));

    webhookServiceWithAccountProcessor.callWebhook(stage);

    // Expect that clouddriver never gets called since there's no account to
    // verify, and the webhook stage makes the http request.
    verifyNoInteractions(oortService);

    // Expect that the account processor gets called once
    verify(webhookAccountProcessor)
        .getHeaders(eq(account), eq(null) /* accountDetails */, eq(customHeaders));
    verifyNoMoreInteractions(webhookAccountProcessor);

    apiProvider.verify(
        getRequestedFor(urlPathEqualTo(path))
            .withoutHeader(headerName)
            .withHeader(accountProcessorHeaderName, equalTo(accountProcessorHeaderValue))
            .andMatching(
                r ->
                    MatchResult.of(
                        r.getHeaders().getHeader(accountProcessorHeaderName).isSingleValued())));
  }

  @ParameterizedTest(name = "{index} => testValidateAccountWithAccount: validAccount = {0}")
  @ValueSource(booleans = {false, true})
  void testValidateAccountWithAccountAndNoAccountProcessor(boolean validAccount) throws Exception {
    webhookProperties.setValidateAccount(true);

    WebhookService webhookService =
        new WebhookService(
            List.of(restTemplateProvider),
            userConfiguredUrlRestrictions,
            webhookProperties,
            oortService,
            Optional.empty(),
            providedIdRequestFilterConfigurationProperties);

    WebhookService webhookServiceWithAccountProcessor =
        new WebhookService(
            List.of(restTemplateProvider),
            userConfiguredUrlRestrictions,
            webhookProperties,
            oortService,
            Optional.of(webhookAccountProcessor),
            providedIdRequestFilterConfigurationProperties);

    String path = "/some/path";
    String url = apiProvider.baseUrl() + path;

    String headerName = "foo";
    String headerValue = "bar";
    Map<String, Object> customHeaders = Map.of(headerName, headerValue);

    // The StageExecutionImpl constructor mutates the map, so use a mutable map.
    String account = "my-account";
    Map<String, Object> webhookStageData =
        new HashMap<>(
            Map.of(
                "url",
                url,
                "method",
                HttpMethod.GET,
                "account",
                account,
                "customHeaders",
                customHeaders));
    StageExecution stage =
        new StageExecutionImpl(null, "webhook", "test-webhook-stage", webhookStageData);

    String responseBodyString = "test response body";
    apiProvider.stubFor(
        get(urlMatching(path))
            .willReturn(
                aResponse().withStatus(HttpStatus.OK.value()).withBody(responseBodyString)));

    // If this is the valid account test, mock clouddriver to return something,
    // otherwise throw an exception.  The return value isn't used because
    // there's no account processor.
    Exception exception = null;
    if (validAccount) {
      Map<String, Object> accountDetails = Map.of("accountDetailOne", "someValue");
      when(oortService.getCredentialsAuthorized(account, true))
          .thenReturn(Calls.response(accountDetails));
    } else {
      exception = new RuntimeException("arbitrary");
      doThrow(exception).when(oortService).getCredentialsAuthorized(account, true);
    }

    Throwable thrown = catchThrowable(() -> webhookService.callWebhook(stage));

    // Expect that clouddriver gets called to validate the account.  If the
    // account is valid (i.e. the response from clouddriver doesn't cause orca
    // to throw an exception), the webhook stage makes the http request.  If the
    // account is invalid, expect that orca does throw an exception, and makes
    // no http request.
    verify(oortService).getCredentialsAuthorized(account, true);
    verifyNoMoreInteractions(oortService);

    if (validAccount) {
      assertThat(thrown).isNull();
      apiProvider.verify(
          getRequestedFor(urlPathEqualTo(path))
              .withHeader(headerName, equalTo(headerValue))
              .andMatching(
                  r -> MatchResult.of(r.getHeaders().getHeader(headerName).isSingleValued())));

    } else {
      assertThat(thrown).isNotNull();
      assertThat(thrown).isEqualTo(exception);
      apiProvider.verify(0, RequestPatternBuilder.allRequests());
    }
  }

  @ParameterizedTest(name = "{index} => testValidateAccountWithAccount: validAccount = {0}")
  @ValueSource(booleans = {false, true})
  void testValidateAccountWithAccountAndAccountProcessor(boolean validAccount) throws Exception {
    webhookProperties.setValidateAccount(true);

    WebhookService webhookService =
        new WebhookService(
            List.of(restTemplateProvider),
            userConfiguredUrlRestrictions,
            webhookProperties,
            oortService,
            Optional.empty(),
            providedIdRequestFilterConfigurationProperties);

    WebhookService webhookServiceWithAccountProcessor =
        new WebhookService(
            List.of(restTemplateProvider),
            userConfiguredUrlRestrictions,
            webhookProperties,
            oortService,
            Optional.of(webhookAccountProcessor),
            providedIdRequestFilterConfigurationProperties);

    String path = "/some/path";
    String url = apiProvider.baseUrl() + path;

    String headerName = "foo";
    String headerValue = "bar";
    Map<String, Object> customHeaders = Map.of(headerName, headerValue);

    String accountProcessorHeaderName = "accountProcessorHeader";
    String accountProcessorHeaderValue = "blah";
    HttpHeaders accountProcessorHeaders = new HttpHeaders();
    accountProcessorHeaders.add(accountProcessorHeaderName, accountProcessorHeaderValue);

    // The StageExecutionImpl constructor mutates the map, so use a mutable map.
    String account = "my-account";
    Map<String, Object> webhookStageData =
        new HashMap<>(
            Map.of(
                "url",
                url,
                "method",
                HttpMethod.GET,
                "account",
                account,
                "customHeaders",
                customHeaders));
    StageExecution stage =
        new StageExecutionImpl(null, "webhook", "test-webhook-stage", webhookStageData);

    String responseBodyString = "test response body";
    apiProvider.stubFor(
        get(urlMatching(path))
            .willReturn(
                aResponse().withStatus(HttpStatus.OK.value()).withBody(responseBodyString)));

    // If this is the valid account test, mock clouddriver to return something,
    // otherwise throw an exception.
    Map<String, Object> accountDetails = Map.of("accountDetailOne", "someValue");
    Exception exception = null;
    if (validAccount) {
      when(oortService.getCredentialsAuthorized(account, true))
          .thenReturn(Calls.response(accountDetails));
    } else {
      exception = new RuntimeException("arbitrary");
      doThrow(exception).when(oortService).getCredentialsAuthorized(account, true);
    }

    // Mock an account processor that ignores the given headers and returns its own
    when(webhookAccountProcessor.getHeaders(eq(account), eq(accountDetails), eq(customHeaders)))
        .thenReturn(new HttpHeaders(accountProcessorHeaders));

    Throwable thrownWithAccountProcessor =
        catchThrowable(() -> webhookServiceWithAccountProcessor.callWebhook(stage));

    // Expect that clouddriver gets called to validate the account.  If the
    // account is valid (i.e. the response from clouddriver doesn't cause orca
    // to throw an exception), the webhook stage makes the http request.  If the
    // account is invalid, expect that orca does throw an exception, and makes
    // no http request.
    verify(oortService).getCredentialsAuthorized(account, true);
    verifyNoMoreInteractions(oortService);

    if (validAccount) {
      assertThat(thrownWithAccountProcessor).isNull();

      // Expect that the account processor gets called once
      verify(webhookAccountProcessor)
          .getHeaders(eq(account), eq(accountDetails), eq(customHeaders));
      verifyNoMoreInteractions(webhookAccountProcessor);

      apiProvider.verify(
          getRequestedFor(urlPathEqualTo(path))
              .withoutHeader(headerName)
              .withHeader(accountProcessorHeaderName, equalTo(accountProcessorHeaderValue))
              .andMatching(
                  r ->
                      MatchResult.of(
                          r.getHeaders().getHeader(accountProcessorHeaderName).isSingleValued())));

    } else {
      assertThat(thrownWithAccountProcessor).isNotNull();
      assertThat(thrownWithAccountProcessor).isEqualTo(exception);

      // Expect that the account processor never gets called
      verifyNoInteractions(webhookAccountProcessor);

      // And there's no http request
      apiProvider.verify(0, RequestPatternBuilder.allRequests());
    }
  }

  @ParameterizedTest(
      name =
          "{index} => testIncludeAdditionalHeaders: enableProvidedIdRequestFilter = {0}, includeAdditionalHeaders = {2}, mdcValues = {3}, customHeaders = {4}")
  @MethodSource("additionalHeadersTests")
  // Ideally I'd cover some more dimensions here...no values present in the MDC,
  // some values present in the MDC, all values present in the MDC, + null
  // custom headers
  void testIncludeAdditionalHeaders(
      boolean enableProvidedIdRequestFilter,
      List<String> additionalHeaderNames,
      boolean includeAdditionalHeaders,
      Map<String, String> mdcValues,
      Map<String, String> customHeaders,
      Map<String, String> expectedHeaders) {
    // Put the specifed values in the MDC
    mdcValues.forEach(MDC::put);

    providedIdRequestFilterConfigurationProperties.setEnabled(enableProvidedIdRequestFilter);
    providedIdRequestFilterConfigurationProperties.setAdditionalHeaders(additionalHeaderNames);

    webhookProperties.setIncludeAdditionalHeaders(includeAdditionalHeaders);

    WebhookService webhookService =
        new WebhookService(
            List.of(restTemplateProvider),
            userConfiguredUrlRestrictions,
            webhookProperties,
            oortService,
            Optional.empty(),
            providedIdRequestFilterConfigurationProperties);

    String path = "/some/path";
    String url = apiProvider.baseUrl() + path;

    // The StageExecutionImpl constructor mutates the map, so use a mutable map.
    String account = "my-account";
    Map<String, Object> webhookStageData =
        new HashMap<>(Map.of("url", url, "method", HttpMethod.GET, "account", account));
    if (customHeaders != null) {
      webhookStageData.put("customHeaders", customHeaders);
    }

    StageExecution stage =
        new StageExecutionImpl(null, "webhook", "test-webhook-stage", webhookStageData);

    String responseBodyString = "test response body";
    apiProvider.stubFor(
        get(urlMatching(path))
            .willReturn(
                aResponse().withStatus(HttpStatus.OK.value()).withBody(responseBodyString)));

    // when
    ResponseEntity<Object> result = webhookService.callWebhook(stage);

    // then
    RequestPatternBuilder builder = getRequestedFor(urlPathEqualTo(path));
    expectedHeaders.forEach(
        (String expectedHeader, String expectedValue) -> {
          builder.withHeader(expectedHeader, equalTo(expectedValue));
          builder.andMatching(
              r -> MatchResult.of(r.getHeaders().getHeader(expectedHeader).isSingleValued()));
        });
    apiProvider.verify(builder);
  }

  private static Stream<Arguments> additionalHeadersTests() {
    List<String> additionalHeaderNames = List.of("X-foo", "X-bar");
    Map<String, String> additionalHeaders = Map.of("X-foo", "foo-value", "X-bar", "bar-value");
    Map<String, String> customHeaders =
        Map.of("X-foo", "custom-foo-value", "X-custom-header", "custom-header-value");

    // With the feature disabled, expect custom headers

    // With all the additional headers in the MDC, and all the custom headers,
    // expect this combination
    Map<String, String> expectedHeaders =
        Map.of(
            "X-foo", "foo-value", "X-bar", "bar-value", "X-custom-header", "custom-header-value");

    // With customHeaders null or empty, expect the info from the MDC.

    // With only partial info in the MDC, still expect that the values for
    // additional header names from customHeaders are dropped.
    Map<String, String> partialMdc = Map.of("X-bar", "bar-value");
    Map<String, String> partialExpectedHeaders =
        Map.of("X-bar", "bar-value", "X-custom-header", "custom-header-value");

    return Stream.of(
        Arguments.of(
            false, additionalHeaderNames, false, additionalHeaders, customHeaders, customHeaders),
        Arguments.of(
            false, additionalHeaderNames, true, additionalHeaders, customHeaders, customHeaders),
        Arguments.of(
            true, additionalHeaderNames, false, additionalHeaders, customHeaders, customHeaders),
        Arguments.of(
            true, additionalHeaderNames, true, additionalHeaders, customHeaders, expectedHeaders),
        Arguments.of(true, additionalHeaderNames, true, additionalHeaders, null, additionalHeaders),
        Arguments.of(
            true, additionalHeaderNames, true, additionalHeaders, Map.of(), additionalHeaders),
        Arguments.of(
            true, additionalHeaderNames, true, partialMdc, customHeaders, partialExpectedHeaders));
  }
}
