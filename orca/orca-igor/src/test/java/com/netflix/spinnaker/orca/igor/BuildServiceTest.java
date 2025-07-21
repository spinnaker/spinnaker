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
package com.netflix.spinnaker.orca.igor;

import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import brave.Tracing;
import brave.http.HttpTracing;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.config.DefaultServiceClientProvider;
import com.netflix.spinnaker.config.OkHttpClientComponents;
import com.netflix.spinnaker.config.RetrofitConfiguration;
import com.netflix.spinnaker.config.okhttp3.DefaultOkHttpClientBuilderProvider;
import com.netflix.spinnaker.config.okhttp3.RawOkHttpClientFactory;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.retrofit.RetrofitServiceFactoryAutoConfiguration;
import com.netflix.spinnaker.kork.web.selector.ByAccountServiceSelector;
import com.netflix.spinnaker.kork.web.selector.SelectableService;
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties;
import com.netflix.spinnaker.orca.clouddriver.CloudDriverService;
import com.netflix.spinnaker.orca.clouddriver.DelegatingOortService;
import com.netflix.spinnaker.orca.igor.config.IgorConfiguration;
import com.netflix.spinnaker.orca.pipeline.persistence.InMemoryExecutionRepository;
import com.netflix.spinnaker.orca.pipeline.util.ArtifactUtils;
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor;
import com.netflix.spinnaker.retrofit.RetrofitConfigurationProperties;
import java.util.List;
import java.util.Map;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.task.TaskExecutorBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import retrofit.RestAdapter;

@SpringBootTest(
    classes = {
      IgorConfiguration.class,
      DefaultServiceClientProvider.class,
      RetrofitServiceFactoryAutoConfiguration.class,
      BuildServiceTest.TestConfig.class,
      OkHttpClientComponents.class,
      DefaultOkHttpClientBuilderProvider.class,
      ObjectMapper.class,
      NoopRegistry.class,
      ContextParameterProcessor.class,
      TaskExecutorBuilder.class,
      CloudDriverService.class,
      DelegatingOortService.class,
      RetrySupport.class,
      ArtifactUtils.class,
      InMemoryExecutionRepository.class,
      RetrofitConfiguration.class
    })
public class BuildServiceTest {
  @RegisterExtension
  private static WireMockExtension wmIgor =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @DynamicPropertySource
  private static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("igor.base-url", wmIgor::baseUrl);
  }

  private static BuildService buildService;
  private static final String ENDPOINT_WITH_CORRECT_ENCODING =
      "/masters/master/jobs/job1?param1=value+with+spaces";

  @Autowired IgorService igorService;

  @BeforeEach
  public void init() {
    buildService = new BuildService(igorService, new IgorFeatureFlagProperties());
  }

  @Test
  public void testBuildServiceBuildApi() {
    wmIgor.stubFor(
        WireMock.put(urlEqualTo(ENDPOINT_WITH_CORRECT_ENCODING))
            .willReturn(WireMock.aResponse().withStatus(200)));
    buildService.build("master", "job1", Map.of("param1", "value with spaces"));

    wmIgor.verify(1, putRequestedFor(urlEqualTo(ENDPOINT_WITH_CORRECT_ENCODING)));
  }

  static class TestConfig {
    @Bean
    RestAdapter.LogLevel retrofitLogLevel(
        RetrofitConfigurationProperties retrofitConfigurationProperties) {
      return retrofitConfigurationProperties.getLogLevel();
    }

    // Since RawOkHttpClientConfiguration is not public, it can't be imported.
    // So the following beans - HttpTracing &  OkHttpClient - are added to the test configuration
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
    SelectableService selectableService() {
      return new SelectableService(
          List.of(new ByAccountServiceSelector("oort", 10, Map.of("accountPattern", ".*"))));
    }
  }
}
