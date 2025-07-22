/*
 * Copyright 2025 OpsMx, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.echo.googlechat;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.catchThrowable;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.config.OkHttp3ClientConfiguration;
import com.netflix.spinnaker.config.OkHttpClientComponents;
import com.netflix.spinnaker.config.RetrofitConfiguration;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.kork.retrofit.util.RetrofitUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.task.TaskExecutorBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@SpringBootTest(
    classes = {
      GoogleChatClient.class,
      OkHttp3ClientConfiguration.class,
      OkHttpClientComponents.class,
      RetrofitConfiguration.class,
      TaskExecutorBuilder.class,
      NoopRegistry.class
    })
public class GoogleChatClientTest {

  @Autowired OkHttp3ClientConfiguration okHttpClientConfig;

  @RegisterExtension
  static WireMockExtension wmGoogleChat =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private GoogleChatService googleChatService;

  @BeforeEach
  public void setup() {
    GoogleChatClient googleChatClient = getGoogleChatClient(wmGoogleChat.baseUrl());
    googleChatService = new GoogleChatService(googleChatClient);
  }

  @Test
  public void testSendGoogleChatMessage() {
    String incorrectly_encoded_endpoint =
        "/v1/spaces/RANDOM/messages%3Fkey%3DXYZ-321%26token%3Dtesttoken123";
    String expected_endpoint = "/v1/spaces/RANDOM/messages?key=XYZ-321&token=testtoken123";

    wmGoogleChat.stubFor(
        WireMock.post(urlEqualTo(incorrectly_encoded_endpoint))
            .willReturn(
                aResponse().withHeader("Content-Type", "application/json").withStatus(404)));

    wmGoogleChat.stubFor(
        WireMock.post(urlEqualTo(expected_endpoint))
            .willReturn(
                aResponse().withHeader("Content-Type", "application/json").withStatus(200)));

    // FIXME: retrofit2 is incorrectly encoding the characters ?(to %3F) and &(to %26) related to
    // query params as
    // if they are part of the path variable.
    Throwable thrown =
        catchThrowable(
            () ->
                googleChatService.sendMessage(
                    "RANDOM/messages?key=XYZ-321&token=testtoken123",
                    new GoogleChatMessage("test message")));
    assertThat(thrown).isInstanceOf(SpinnakerHttpException.class);
    assertThat(thrown.getMessage())
        .contains(
            "Status: 404, Method: POST, URL: "
                + wmGoogleChat.baseUrl()
                + "/v1/spaces/RANDOM/messages%3Fkey%3DXYZ-321%26token%3Dtesttoken123, Message: Not Found");
  }

  private GoogleChatClient getGoogleChatClient(String baseUrl) {
    return new Retrofit.Builder()
        .baseUrl(RetrofitUtils.getBaseUrl(baseUrl))
        .client(okHttpClientConfig.createForRetrofit2().build())
        .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
        .addConverterFactory(JacksonConverterFactory.create())
        .build()
        .create(GoogleChatClient.class);
  }
}
