/*
 * Copyright 2025 Salesforce, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.orca.webhook.config;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.netflix.spinnaker.fiat.shared.FiatService;
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.config.UserConfiguredUrlRestrictions;
import com.netflix.spinnaker.orca.webhook.util.WebhookLoggingEventListener;
import java.lang.reflect.Field;
import okhttp3.Call;
import okhttp3.EventListener;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.client.RestTemplate;

class WebhookConfigurationTest {

  private static final String TEST_PATH = "/test";

  private static final String TEST_RESPONSE = "test response";

  /** See https://wiremock.org/docs/junit-jupiter/#advanced-usage---programmatic */
  @RegisterExtension
  private static WireMockExtension wiremock =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withBean(ObjectMapper.class)
          .withConfiguration(
              UserConfigurations.of(
                  WebhookConfiguration.class,
                  OkHttpClientConfigurationProperties.class,
                  WebhookTestConfiguration.class));

  @BeforeEach
  void init(TestInfo testInfo) {
    System.out.println("--------------- Test " + testInfo.getDisplayName());

    wiremock.stubFor(
        get(urlMatching(TEST_PATH))
            .willReturn(aResponse().withStatus(HttpStatus.OK.value()).withBody(TEST_RESPONSE)));
  }

  @Test
  void testReadTimeoutViaWebhookProperties() {
    long webhookReadTimeout = 17;
    runner
        .withPropertyValues("webhook.readTimeoutMs:" + webhookReadTimeout)
        .run(
            ctx -> {
              OkHttpClient client = getOkHttpClient(ctx);
              assertThat(client.readTimeoutMillis()).isEqualTo(webhookReadTimeout);
            });
  }

  @Test
  void testReadTimeoutViaOkHttp() {
    long okHttpClientReadTimeout = 5;
    runner
        .withPropertyValues("ok-http-client.readTimeoutMs:" + okHttpClientReadTimeout)
        .run(
            ctx -> {
              OkHttpClient client = getOkHttpClient(ctx);
              assertThat(client.readTimeoutMillis()).isEqualTo(okHttpClientReadTimeout);
            });
  }

  @Test
  void testReadTimeoutDemonstratePrecedence() {
    long okHttpClientReadTimeout = 5;
    long webhookReadTimeout = 17;
    runner
        .withPropertyValues(
            "ok-http-client.readTimeoutMs:" + okHttpClientReadTimeout,
            "webhook.readTimeoutMs:" + webhookReadTimeout)
        .run(
            ctx -> {
              OkHttpClient client = getOkHttpClient(ctx);
              assertThat(client.readTimeoutMillis()).isEqualTo(webhookReadTimeout);
            });
  }

  @Test
  void testReadTimeoutKebabCase() {
    long okHttpClientReadTimeout = 5;
    long webhookReadTimeout = 17;
    runner
        .withPropertyValues(
            "ok-http-client.read-timeout-ms:" + okHttpClientReadTimeout,
            "webhook.read-timeout-ms:" + webhookReadTimeout)
        .run(
            ctx -> {
              OkHttpClient client = getOkHttpClient(ctx);
              assertThat(client.readTimeoutMillis()).isEqualTo(webhookReadTimeout);
            });
  }

  @Test
  void testConnectTimeoutViaWebhookProperties() {
    long webhookConnectTimeout = 17;
    runner
        .withPropertyValues("webhook.connectTimeoutMs:" + webhookConnectTimeout)
        .run(
            ctx -> {
              OkHttpClient client = getOkHttpClient(ctx);
              assertThat(client.connectTimeoutMillis()).isEqualTo(webhookConnectTimeout);
            });
  }

  @Test
  void testConnectTimeoutViaOkHttp() {
    long okHttpClientConnectTimeout = 0;
    runner
        .withPropertyValues("ok-http-client.connectTimeoutMs:" + okHttpClientConnectTimeout)
        .run(
            ctx -> {
              OkHttpClient client = getOkHttpClient(ctx);
              assertThat(client.connectTimeoutMillis()).isEqualTo(okHttpClientConnectTimeout);
            });
  }

  @Test
  void testConnectTimeoutDemonstratePrecedence() {
    long okHttpClientConnectTimeout = 5;
    long webhookConnectTimeout = 17;
    runner
        .withPropertyValues(
            "ok-http-client.connectTimeoutMs:" + okHttpClientConnectTimeout,
            "webhook.connectTimeoutMs:" + webhookConnectTimeout)
        .run(
            ctx -> {
              OkHttpClient client = getOkHttpClient(ctx);
              assertThat(client.connectTimeoutMillis()).isEqualTo(webhookConnectTimeout);
            });
  }

  @Test
  void testConnectTimeoutKebabCase() {
    long okHttpClientConnectTimeout = 5;
    long webhookConnectTimeout = 17;
    runner
        .withPropertyValues(
            "ok-http-client.connect-timeout-ms:" + okHttpClientConnectTimeout,
            "webhook.connect-timeout-ms:" + webhookConnectTimeout)
        .run(
            ctx -> {
              OkHttpClient client = getOkHttpClient(ctx);
              assertThat(client.connectTimeoutMillis()).isEqualTo(webhookConnectTimeout);
            });
  }

  @Test
  void testNoEventListenerByDefault() {
    runner.run(
        ctx -> {
          OkHttpClient client = getOkHttpClient(ctx);
          // EventListener.NONE.asFactory() isn't available, so here's an alternative to
          //
          // assertThat(client.eventListenerFactory()).isEqualTo(EventListener.NONE.asFactory());
          EventListener.Factory eventListenerFactory = client.eventListenerFactory();
          assertThat(eventListenerFactory.create(mock(Call.class))).isEqualTo(EventListener.NONE);
        });
  }

  @Test
  void testEventLoggingDisabled() {
    runner
        .withPropertyValues("webhook.eventLoggingEnabled: false")
        .run(
            ctx -> {
              OkHttpClient client = getOkHttpClient(ctx);
              // EventListener.NONE.asFactory() isn't available, so here's an alternative to
              //
              // assertThat(client.eventListenerFactory()).isEqualTo(EventListener.NONE.asFactory());
              EventListener.Factory eventListenerFactory = client.eventListenerFactory();
              assertThat(eventListenerFactory.create(mock(Call.class)))
                  .isEqualTo(EventListener.NONE);
            });
  }

  @Test
  void testEventLoggingEnabled() {
    runner
        .withPropertyValues("webhook.eventLoggingEnabled: true")
        .run(
            ctx -> {
              OkHttpClient client = getOkHttpClient(ctx);
              EventListener.Factory eventListenerFactory = client.eventListenerFactory();
              // While we're at it, verify the default verbosity
              assertThat(eventListenerFactory)
                  .isInstanceOfSatisfying(
                      WebhookLoggingEventListener.Factory.class,
                      webhookLoggingEventListener -> {
                        assertThat(webhookLoggingEventListener.isVerbose()).isEqualTo(false);
                      });
            });
  }

  @ParameterizedTest(name = "{index} => testEventLoggingVerbosity: verbose = {0}")
  @ValueSource(booleans = {false, true})
  void testEventLoggingVerbosity(boolean verbose) {
    runner
        .withPropertyValues(
            "webhook.eventLoggingEnabled: true",
            "webhook.eventLoggingVerbose: " + String.valueOf(verbose))
        .run(
            ctx -> {
              OkHttpClient client = getOkHttpClient(ctx);
              EventListener.Factory eventListenerFactory = client.eventListenerFactory();
              assertThat(eventListenerFactory)
                  .isInstanceOfSatisfying(
                      WebhookLoggingEventListener.Factory.class,
                      webhookLoggingEventListener -> {
                        assertThat(webhookLoggingEventListener.isVerbose()).isEqualTo(verbose);
                      });
              // Actually make an http request to exercise WebhookLoggingEventListener.  No
              // assertions about the details of log messages, but at least they're in the test
              // results for manual inspection.  This also ensures the code doesn't throw any
              // exceptions.
              RestTemplate restTemplate = ctx.getBean(RestTemplate.class);
              String response =
                  restTemplate.getForObject(wiremock.baseUrl() + TEST_PATH, String.class);
              assertThat(response).isEqualTo(TEST_RESPONSE);
            });
  }

  /** Retrieve the client member from the OkHttp3ClientHttpRequestFactory bean */
  private static OkHttpClient getOkHttpClient(AssertableApplicationContext ctx) {
    OkHttp3ClientHttpRequestFactory requestFactory =
        ctx.getBean(OkHttp3ClientHttpRequestFactory.class);
    assertThat(requestFactory).isNotNull();
    Field clientField =
        ReflectionUtils.findField(
            OkHttp3ClientHttpRequestFactory.class, "client", OkHttpClient.class);
    assertThat(clientField).isNotNull();
    clientField.setAccessible(true);
    OkHttpClient client = (OkHttpClient) ReflectionUtils.getField(clientField, requestFactory);
    assertThat(client).isNotNull();
    return client;
  }

  private static class WebhookTestConfiguration {
    @Bean
    UserConfiguredUrlRestrictions userConfiguredUrlRestrictions() {
      return new UserConfiguredUrlRestrictions.Builder().build();
    }

    /**
     * WebhookConfiguration has @ComponentScan("com.netflix.spinnaker.orca.webhook") which picks up
     * PreconfiguredWebhookStage, which depends on FiatService.
     */
    @Bean
    FiatService fiatService() {
      return mock(FiatService.class);
    }

    @Bean
    OortService oortService() {
      return mock(OortService.class);
    }
  }
}
