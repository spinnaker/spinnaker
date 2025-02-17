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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.fiat.shared.FiatService;
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties;
import com.netflix.spinnaker.orca.config.UserConfiguredUrlRestrictions;
import java.lang.reflect.Field;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory;
import org.springframework.util.ReflectionUtils;

class WebhookConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withBean(ObjectMapper.class)
          .withConfiguration(
              UserConfigurations.of(
                  WebhookConfiguration.class,
                  OkHttpClientConfigurationProperties.class,
                  WebhookTestConfiguration.class));

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
  }
}
