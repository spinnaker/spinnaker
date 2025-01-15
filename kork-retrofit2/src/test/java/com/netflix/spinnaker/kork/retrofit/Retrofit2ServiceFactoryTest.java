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

package com.netflix.spinnaker.kork.retrofit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.netflix.spinnaker.config.DefaultServiceClientProvider;
import com.netflix.spinnaker.config.DefaultServiceEndpoint;
import com.netflix.spinnaker.config.ServiceEndpoint;
import com.netflix.spinnaker.config.okhttp3.DefaultOkHttpClientBuilderProvider;
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider;
import com.netflix.spinnaker.kork.client.ServiceClientFactory;
import com.netflix.spinnaker.kork.client.ServiceClientProvider;
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties;
import java.util.List;
import java.util.Map;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit2.Call;
import retrofit2.http.GET;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = {
      OkHttpClient.class,
      OkHttpClientConfigurationProperties.class,
      OkHttpClientProvider.class,
      Retrofit2ServiceFactoryAutoConfiguration.class,
      Retrofit2ServiceFactoryTest.Retrofit2TestConfig.class,
      DefaultOkHttpClientBuilderProvider.class
    })
public class Retrofit2ServiceFactoryTest {

  @Autowired ServiceClientProvider serviceClientProvider;

  static int port;
  static WireMockServer wireMockServer;

  @BeforeAll
  static void setUp() {
    wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    wireMockServer.start();
    port = wireMockServer.port();
    WireMock.configureFor("localhost", port);
  }

  @AfterAll
  static void tearDown() {
    wireMockServer.stop();
  }

  @Test
  void testRetrofit2Client() {
    stubFor(
        get(urlEqualTo("/test"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"message\": \"success\", \"code\": 200}")));

    ServiceEndpoint serviceEndpoint =
        new DefaultServiceEndpoint("retrofit2service", "http://localhost:" + port);
    Retrofit2TestService retrofit2TestService =
        serviceClientProvider.getService(Retrofit2TestService.class, serviceEndpoint);
    Map<String, String> response = Retrofit2SyncCall.execute(retrofit2TestService.getSomething());

    assertEquals(response.get("message"), "success");
  }

  @Test
  void testRetrofit2Client_withInterceptor() {

    Interceptor interceptor =
        chain -> {
          Request originalRequest = chain.request();
          Request modifiedRequest =
              originalRequest.newBuilder().header("Authorization", "Bearer my-token").build();
          return chain.proceed(modifiedRequest);
        };

    stubFor(
        get(urlEqualTo("/test"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"message\": \"success\", \"code\": 200}")));

    ServiceEndpoint serviceEndpoint =
        new DefaultServiceEndpoint("retrofit2service", "http://localhost:" + port);
    Retrofit2TestService retrofit2TestService =
        serviceClientProvider.getService(
            Retrofit2TestService.class, serviceEndpoint, new ObjectMapper(), List.of(interceptor));
    Retrofit2SyncCall.execute(retrofit2TestService.getSomething());

    verify(
        getRequestedFor(urlEqualTo("/test"))
            .withHeader("Authorization", equalTo("Bearer my-token")));
  }

  @Configuration
  public static class Retrofit2TestConfig {

    @Bean
    public ServiceClientProvider serviceClientProvider(
        List<ServiceClientFactory> serviceClientFactories) {
      return new DefaultServiceClientProvider(serviceClientFactories, new ObjectMapper());
    }
  }

  public interface Retrofit2TestService {

    @GET("test")
    Call<Map<String, String>> getSomething();
  }
}
