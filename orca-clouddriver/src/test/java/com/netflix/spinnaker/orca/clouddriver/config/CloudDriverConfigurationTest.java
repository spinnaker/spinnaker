/*
 * Copyright 2023 JPMorgan Chase & Co.
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

package com.netflix.spinnaker.orca.clouddriver.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.config.ServiceEndpoint;
import com.netflix.spinnaker.config.okhttp3.OkHttpClientBuilderProvider;
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider;
import com.netflix.spinnaker.okhttp.SpinnakerRequestInterceptor;
import com.netflix.spinnaker.orca.clouddriver.KatoRestService;
import com.netflix.spinnaker.orca.test.YamlFileApplicationContextInitializer;
import java.util.List;
import okhttp3.OkHttpClient;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;

@ExtendWith(MockitoExtension.class)
@ContextConfiguration(
    classes = CloudDriverConfigurationProperties.class,
    initializers = CloudDriverConfigurationTest.class)
@EnableConfigurationProperties
@SpringBootTest
public class CloudDriverConfigurationTest extends YamlFileApplicationContextInitializer {

  @Autowired private CloudDriverConfigurationProperties cloudDriverConfigurationProperties;

  private CloudDriverConfiguration.ClouddriverRetrofitBuilder clouddriverRetrofitBuilder;

  @Override
  protected String getResourceLocation() {
    return "classpath:clouddriver-sharding-properties.yml";
  }

  @BeforeEach
  public void setup() {
    OkHttpClientBuilderProvider okHttpClientBuilderProvider =
        new OkHttpClientBuilderProvider() {
          @Override
          @NotNull
          public Boolean supports(@NotNull ServiceEndpoint service) {
            return true;
          }

          @Override
          @NotNull
          public OkHttpClient.Builder get(@NotNull ServiceEndpoint service) {
            return new OkHttpClient().newBuilder();
          }
        };
    OkHttpClientProvider okHttpClientProvider =
        new OkHttpClientProvider(List.of(okHttpClientBuilderProvider));

    ObjectMapper objectMapper = new ObjectMapper();
    RestAdapter.LogLevel logLevel = RestAdapter.LogLevel.FULL;
    RequestInterceptor requestInterceptor = new SpinnakerRequestInterceptor(null);

    this.clouddriverRetrofitBuilder =
        new CloudDriverConfiguration.ClouddriverRetrofitBuilder(
            objectMapper,
            okHttpClientProvider,
            logLevel,
            requestInterceptor,
            cloudDriverConfigurationProperties);
  }

  @DisplayName("when selector config is a YAML list, successfully constructs a SelectableService")
  @Test
  public void testConstructingSelectorsWithYAMLList() {
    /*
     * Because the configuration of a BaseUrl in Orca is loosely typed as a Map<String, Object>, to allow variations of
     * config for ServiceSelector classes that require different types (e.g. a Regex String or a List), Jackson doesn't have enough
     * type information and converts a YAML list to a LinkedHashMap (values keyed by index). This caused issues in Kork where the
     * ServiceSelector constructors expected the Object to be a List (to match the YAML format) but Jackson had converted
     * it to a LinkedHashMap. This test validates that ServiceSelector implementations in Kork that use a YAML list for configuration
     * correctly handle what Jackson is mapping the config to.
     *
     * Unfortunately, since each service handles the creation of ServiceSelectors differently, there's no easy
     * way to test this in Kork without duplicating all the logic from all the services. Thus, this test lives in Orca.
     */
    assertDoesNotThrow(
        () -> clouddriverRetrofitBuilder.buildWriteableService(KatoRestService.class));
  }
}
