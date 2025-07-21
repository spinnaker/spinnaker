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
 */

package com.netflix.spinnaker.rosco.services;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.config.OkHttp3ClientConfiguration;
import com.netflix.spinnaker.config.OkHttpMetricsInterceptorProperties;
import com.netflix.spinnaker.config.RetrofitConfiguration;
import com.netflix.spinnaker.config.okhttp3.RawOkHttpClientFactory;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.okhttp.OkHttp3MetricsInterceptor;
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties;
import com.netflix.spinnaker.okhttp.Retrofit2EncodeCorrectionInterceptor;
import com.netflix.spinnaker.okhttp.SpinnakerRequestHeaderInterceptor;
import com.netflix.spinnaker.okhttp.SpinnakerRequestInterceptor;
import com.netflix.spinnaker.retrofit.Retrofit2ConfigurationProperties;
import com.netflix.spinnaker.retrofit.RetrofitConfigurationProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import retrofit.RequestInterceptor;

@SpringBootTest(
    classes = {
      ServiceConfig.class,
      ClouddriverServiceTest.TestConfig.class,
      DynamicConfigService.NoopDynamicConfig.class,
      NoopRegistry.class,
      Retrofit2EncodeCorrectionInterceptor.class,
      RetrofitConfiguration.class,
      Retrofit2ConfigurationProperties.class,
      OkHttp3MetricsInterceptor.class,
      RawOkHttpClientFactory.class,
      RetrofitConfigurationProperties.class,
      OkHttpClientConfigurationProperties.class,
      OkHttpMetricsInterceptorProperties.class,
      ObjectMapper.class,
      OkHttp3ClientConfiguration.class
    })
public class ClouddriverServiceTest {
  @RegisterExtension
  static WireMockExtension wmClouddriver =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @Autowired ClouddriverService clouddriverService;

  static String WM_CORRECT_ENDPOINT =
      "/api/artifacts/fetch/"; // since the baseUrl has /api/ at the end.
  static String WM_CORRECT_URL;

  @DynamicPropertySource
  static void registerUrls(DynamicPropertyRegistry registry) {
    String baseUrl = wmClouddriver.baseUrl() + "/api/";
    System.out.println("wiremock clouddriver url: " + baseUrl);
    registry.add("services.clouddriver.base-url", () -> baseUrl);
  }

  @Test
  void testBaseUrlWithMultipleSlashes() {
    WM_CORRECT_URL = wmClouddriver.baseUrl() + WM_CORRECT_ENDPOINT;
    wmClouddriver.stubFor(
        WireMock.put(urlEqualTo(WM_CORRECT_ENDPOINT))
            .willReturn(aResponse().withStatus(200).withBody("{\"message\": \"success\"}")));

    Artifact artifact = Artifact.builder().build();

    assertDoesNotThrow(() -> Retrofit2SyncCall.execute(clouddriverService.fetchArtifact(artifact)));

    wmClouddriver.verify(1, WireMock.putRequestedFor(urlEqualTo(WM_CORRECT_ENDPOINT)));
  }

  @Configuration
  static class TestConfig {
    @Bean
    public SpinnakerRequestHeaderInterceptor spinnakerRequestHeaderInterceptor() {
      return new SpinnakerRequestHeaderInterceptor(false);
    }
    // SpinnakerRequestInterceptor is not needed for retrofit2 client but due to the way retrofit1
    // and retrofit2 specific beans are mixed up in kork, this is needed for now. Once the beans
    // are separated this can be removed
    @Bean
    public RequestInterceptor spinnakerRequestInterceptor() {
      return new SpinnakerRequestInterceptor(false);
    }
  }
}
