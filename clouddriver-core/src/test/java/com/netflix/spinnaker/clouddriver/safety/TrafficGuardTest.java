/*
 * Copyright 2025 OpsMx, Inc.
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
 *
 */
package com.netflix.spinnaker.clouddriver.safety;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.clouddriver.config.RetrofitConfig;
import com.netflix.spinnaker.clouddriver.core.services.Front50Service;
import com.netflix.spinnaker.clouddriver.model.NoopClusterProvider;
import com.netflix.spinnaker.config.DefaultServiceClientProvider;
import com.netflix.spinnaker.config.okhttp3.DefaultOkHttpClientBuilderProvider;
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.kork.retrofit.Retrofit2ServiceFactory;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties;
import com.netflix.spinnaker.okhttp.SpinnakerRequestHeaderInterceptor;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(
    classes = {
      RetrofitConfig.class,
      OkHttpClientProvider.class,
      TrafficGuardTest.TestConfig.class,
      DefaultServiceClientProvider.class,
      Retrofit2ServiceFactory.class,
      DefaultOkHttpClientBuilderProvider.class,
      OkHttpClient.class,
      OkHttpClientConfigurationProperties.class,
      ObjectMapper.class
    })
public class TrafficGuardTest {
  private final String APP_NAME = "testApp";

  @RegisterExtension
  static WireMockExtension wmFront50 =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @Autowired ObjectMapper objectMapper;

  @Autowired Front50Service front50Service;

  TrafficGuard trafficGuard;

  @DynamicPropertySource
  static void registerUrls(DynamicPropertyRegistry registry) {
    // Configure wiremock's random ports into clouddriver
    System.out.println("wiremock front50 url: " + wmFront50.baseUrl());
    registry.add("services.front50.base-url", wmFront50::baseUrl);
  }

  @BeforeEach
  void init() throws JsonProcessingException {
    wmFront50.stubFor(
        WireMock.get(urlMatching("/v2/applications/" + APP_NAME))
            .willReturn(
                aResponse()
                    .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
                    .withBody(
                        objectMapper.writeValueAsString(
                            Map.of("error", "Internal Server Error")))));

    trafficGuard =
        new TrafficGuard(
            List.of(new NoopClusterProvider()),
            Optional.of(front50Service),
            new NoopRegistry(),
            new DynamicConfigService.NoopDynamicConfig());
  }

  @Test
  void verifyErrorHandlingExecutorCallAdapterFactory_with_500_exception() {
    Moniker moniker = Moniker.builder().app(APP_NAME).build();

    assertThatThrownBy(() -> trafficGuard.hasDisableLock(moniker, "acc1", "location1"))
        .isInstanceOf(SpinnakerHttpException.class);

    wmFront50.verify(getRequestedFor(urlPathEqualTo("/v2/applications/" + APP_NAME)));

    wmFront50.verify(1, anyRequestedFor(anyUrl()));
  }

  @Configuration
  static class TestConfig {

    @Bean
    SpinnakerRequestHeaderInterceptor spinnakerRequestHeaderInterceptor() {
      return new SpinnakerRequestHeaderInterceptor(false);
    }
  }
}
