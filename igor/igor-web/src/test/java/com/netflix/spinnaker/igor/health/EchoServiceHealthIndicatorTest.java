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

package com.netflix.spinnaker.igor.health;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.Assert.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.igor.history.EchoService;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import com.netflix.spinnaker.kork.retrofit.util.RetrofitUtils;
import java.util.Optional;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

public class EchoServiceHealthIndicatorTest {

  @RegisterExtension
  static WireMockExtension wmEcho =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  static EchoServiceHealthIndicator echoSerivceHealthIndicator;
  static Registry registry;
  static EchoService echoService;

  @BeforeAll
  public static void setup() {
    registry = new DefaultRegistry();
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

    echoService =
        new Retrofit.Builder()
            .baseUrl(RetrofitUtils.getBaseUrl(wmEcho.baseUrl()))
            .client(new OkHttpClient())
            .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
            .addConverterFactory(JacksonConverterFactory.create(objectMapper))
            .build()
            .create(EchoService.class);

    echoSerivceHealthIndicator = new EchoServiceHealthIndicator(registry, Optional.of(echoService));
  }

  @Test
  public void testHealth() {
    wmEcho.stubFor(WireMock.post("/").willReturn(aResponse().withStatus(200)));

    echoSerivceHealthIndicator.checkHealth();
    Health health = echoSerivceHealthIndicator.health();
    assertEquals(health.getStatus(), Status.UP);
  }
}
