/*
 * Copyright 2025 OpsMx, Inc.
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

package com.netflix.spinnaker.orca.echo;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.config.DefaultServiceClientProvider;
import com.netflix.spinnaker.config.okhttp3.DefaultOkHttpClientBuilderProvider;
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider;
import com.netflix.spinnaker.fiat.shared.FiatClientConfigurationProperties;
import com.netflix.spinnaker.fiat.shared.FiatStatus;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.kork.retrofit.Retrofit2ServiceFactory;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties;
import com.netflix.spinnaker.okhttp.Retrofit2EncodeCorrectionInterceptor;
import com.netflix.spinnaker.okhttp.SpinnakerRequestHeaderInterceptor;
import com.netflix.spinnaker.orca.echo.config.EchoConfiguration;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.pipeline.persistence.InMemoryExecutionRepository;
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor;
import java.util.Map;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(
    classes = {
      EchoConfiguration.class,
      OkHttpClientProvider.class,
      Retrofit2EncodeCorrectionInterceptor.class,
      DefaultServiceClientProvider.class,
      EchoServiceTest.TestConfig.class,
      Retrofit2ServiceFactory.class,
      DefaultOkHttpClientBuilderProvider.class,
      OkHttpClient.class,
      OkHttpClientConfigurationProperties.class,
      ObjectMapper.class,
      FiatStatus.class,
      NoopRegistry.class,
      DynamicConfigService.NoopDynamicConfig.class,
      FiatClientConfigurationProperties.class,
      InMemoryExecutionRepository.class,
      ContextParameterProcessor.class
    })
public class EchoServiceTest {

  @RegisterExtension
  static WireMockExtension wmEcho50 =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @Autowired EchoService echoService;

  @DynamicPropertySource
  static void registerUrls(DynamicPropertyRegistry registry) {
    System.out.println("wiremock echo url: " + wmEcho50.baseUrl());
    registry.add("echo.base-url", wmEcho50::baseUrl);
  }

  @Test
  public void testEchoService() {
    wmEcho50.stubFor(
        WireMock.post(urlMatching("/")).willReturn(aResponse().withStatus(HttpStatus.OK.value())));

    Retrofit2SyncCall.execute(echoService.recordEvent(Map.of("type", "testEvent")));

    wmEcho50.verify(1, WireMock.postRequestedFor(urlMatching("/")));
  }

  @Configuration
  static class TestConfig {

    @MockBean Front50Service front50Service;

    @Bean
    SpinnakerRequestHeaderInterceptor spinnakerRequestHeaderInterceptor() {
      return new SpinnakerRequestHeaderInterceptor(false);
    }
  }
}
