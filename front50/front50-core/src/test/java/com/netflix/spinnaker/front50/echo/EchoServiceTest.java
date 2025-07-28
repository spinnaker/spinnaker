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

package com.netflix.spinnaker.front50.echo;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import brave.Tracing;
import brave.http.HttpTracing;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.config.OkHttp3ClientConfiguration;
import com.netflix.spinnaker.config.OkHttpMetricsInterceptorProperties;
import com.netflix.spinnaker.config.RetrofitConfiguration;
import com.netflix.spinnaker.config.okhttp3.OkHttpClientBuilderProvider;
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider;
import com.netflix.spinnaker.config.okhttp3.RawOkHttpClientFactory;
import com.netflix.spinnaker.front50.config.EchoConfiguration;
import com.netflix.spinnaker.front50.config.StorageServiceConfigurationProperties;
import com.netflix.spinnaker.front50.model.plugins.PluginEvent;
import com.netflix.spinnaker.front50.model.plugins.PluginEventType;
import com.netflix.spinnaker.front50.model.plugins.PluginInfo;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.kork.retrofit.Retrofit2ServiceFactoryAutoConfiguration;
import com.netflix.spinnaker.okhttp.OkHttp3MetricsInterceptor;
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties;
import com.netflix.spinnaker.okhttp.Retrofit2EncodeCorrectionInterceptor;
import com.netflix.spinnaker.okhttp.SpinnakerRequestHeaderInterceptor;
import com.netflix.spinnaker.retrofit.Retrofit2ConfigurationProperties;
import com.netflix.spinnaker.retrofit.RetrofitConfigurationProperties;
import java.util.List;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(
    classes = {
      EchoServiceTest.EchoServiceTestConfig.class,
      EchoConfiguration.class,
      DynamicConfigService.NoopDynamicConfig.class,
      StorageServiceConfigurationProperties.class,
      NoopRegistry.class,
      Retrofit2ServiceFactoryAutoConfiguration.class,
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
@TestPropertySource(properties = {"services.echo.enabled=true"})
public class EchoServiceTest {

  @RegisterExtension
  static WireMockExtension wmEcho =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @Autowired EchoService echoService;

  @DynamicPropertySource
  static void registerUrls(DynamicPropertyRegistry registry) {
    // Configure wiremock's random ports i
    System.out.println("wiremock echo url: " + wmEcho.baseUrl());
    registry.add("services.echo.base-url", wmEcho::baseUrl);
  }

  @Test
  public void testEchoService() {
    wmEcho.stubFor(
        WireMock.post(urlEqualTo("/"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withStatus(200)
                    .withBody("{}")));

    assertDoesNotThrow(() -> echoService.postEvent(buildPluginEvent()));
  }

  private static PluginEvent buildPluginEvent() {
    final PluginInfo pluginInfo = new PluginInfo();
    final PluginInfo.Release release = new PluginInfo.Release();
    return new PluginEvent(PluginEventType.PUBLISHED, pluginInfo, release);
  }

  @Configuration
  static class EchoServiceTestConfig {
    @Bean
    public HttpTracing httpTracing() {
      return HttpTracing.newBuilder(Tracing.newBuilder().build()).build();
    }

    @Bean
    OkHttpClient okhttp3Client(
        OkHttpClientConfigurationProperties okHttpClientConfigurationProperties,
        List<Interceptor> interceptors,
        HttpTracing httpTracing) {
      return new RawOkHttpClientFactory()
          .create(okHttpClientConfigurationProperties, interceptors, httpTracing);
    }

    @Bean
    public SpinnakerRequestHeaderInterceptor spinnakerRequestHeaderInterceptor() {
      return new SpinnakerRequestHeaderInterceptor(false);
    }

    @Bean
    public Retrofit2EncodeCorrectionInterceptor retrofit2EncodeCorrectionInterceptor() {
      return new Retrofit2EncodeCorrectionInterceptor();
    }

    @Bean
    public OkHttpClientProvider okHttpClientProvider(List<OkHttpClientBuilderProvider> providers) {
      return new OkHttpClientProvider(providers);
    }
  }
}
