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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.matching.MatchResult;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.config.UserConfiguredUrlRestrictions;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import com.netflix.spinnaker.orca.webhook.config.WebhookConfiguration;
import com.netflix.spinnaker.orca.webhook.config.WebhookProperties;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.env.Environment;
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

  private WebhookService webhookService;

  private OortService oortService = mock(OortService.class);

  @BeforeEach
  void init(TestInfo testInfo) throws Exception {
    System.out.println("--------------- Test " + testInfo.getDisplayName());

    ClientHttpRequestFactory requestFactory =
        webhookConfiguration.webhookRequestFactory(
            mock(Environment.class),
            okHttpClientConfigurationProperties,
            userConfiguredUrlRestrictions,
            webhookProperties);

    RestTemplateProvider restTemplateProvider =
        new DefaultRestTemplateProvider(webhookConfiguration.restTemplate(requestFactory));

    webhookService =
        new WebhookService(
            List.of(restTemplateProvider),
            userConfiguredUrlRestrictions,
            webhookProperties,
            oortService);
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

  @Test
  void testRequestHeadersTooBig() throws Exception {
    // Even with an empty body, even one request header (e.g. Content-Length: 0) is too big.
    webhookProperties.setMaxRequestBytes(1L);

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

    String path = "/some/path";
    String url = apiProvider.baseUrl() + path;

    String headerName = "foo";
    String headerValue = "bar";
    Map<String, Object> customHeaders = Map.of(headerName, headerValue);

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

    webhookService.callWebhook(stage);

    // Expect that clouddriver never gets called since there's no account to
    // verify, and the webhook stage makes the http request.
    verifyNoInteractions(oortService);

    apiProvider.verify(
        getRequestedFor(urlPathEqualTo(path))
            .withHeader(headerName, equalTo(headerValue))
            .andMatching(
                r -> MatchResult.of(r.getHeaders().getHeader(headerName).isSingleValued())));
  }

  @ParameterizedTest(name = "{index} => testValidateAccountWithMissingAccount: account = ''{0}''")
  @NullSource
  @ValueSource(strings = {"", " "})
  void testValidateAccountWithMissingAccount(String account) throws Exception {
    webhookProperties.setValidateAccount(true);

    String path = "/some/path";
    String url = apiProvider.baseUrl() + path;

    String headerName = "foo";
    String headerValue = "bar";
    Map<String, Object> customHeaders = Map.of(headerName, headerValue);

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

    webhookService.callWebhook(stage);

    // Expect that clouddriver never gets called since there's no account to
    // verify, and the webhook stage makes the http request.
    verifyNoInteractions(oortService);

    apiProvider.verify(
        getRequestedFor(urlPathEqualTo(path))
            .withHeader(headerName, equalTo(headerValue))
            .andMatching(
                r -> MatchResult.of(r.getHeaders().getHeader(headerName).isSingleValued())));
  }

  @ParameterizedTest(name = "{index} => testValidateAccountWithAccount: validAccount = {0}")
  @ValueSource(booleans = {false, true})
  void testValidateAccountWithAccount(boolean validAccount) throws Exception {
    webhookProperties.setValidateAccount(true);

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
    // and not throw an exception.  The return value is currently ignored, so
    // it's arbitrary.
    Exception exception = null;
    if (validAccount) {
      when(oortService.getCredentialsAuthorized(account, true))
          .thenReturn(Calls.response(Collections.emptyMap()));
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
}
