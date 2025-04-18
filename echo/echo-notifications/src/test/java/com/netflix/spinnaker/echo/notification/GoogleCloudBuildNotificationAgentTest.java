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

package com.netflix.spinnaker.echo.notification;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.netflix.spinnaker.echo.api.events.Event;
import com.netflix.spinnaker.echo.api.events.Metadata;
import com.netflix.spinnaker.echo.model.pubsub.MessageDescription;
import com.netflix.spinnaker.echo.services.IgorService;
import com.netflix.spinnaker.echo.util.RetrofitUtils;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import java.util.HashMap;
import java.util.Map;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

public class GoogleCloudBuildNotificationAgentTest {
  public static final String ACCOUNT = "my-account";
  public static final String BUILD_ID = "1a9ea355-eb3d-4148-b81b-875d07ea118b";
  public static final String BUILD_STATUS = "QUEUED";

  @RegisterExtension
  static final WireMockExtension wmIgor =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  static GoogleCloudBuildNotificationAgent notificationAgent;
  static ObjectMapper mapper = new ObjectMapper();

  @BeforeAll
  public static void setup() {
    // Create IgorService
    IgorService igorService =
        new Retrofit.Builder()
            .baseUrl(RetrofitUtils.getBaseUrl(wmIgor.baseUrl()))
            .client(new OkHttpClient())
            .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
            .addConverterFactory(JacksonConverterFactory.create())
            .build()
            .create(IgorService.class);

    notificationAgent = new GoogleCloudBuildNotificationAgent(igorService, new RetrySupport());
  }

  @Test
  public void testGoogleCloudBuildNotificationAgent() throws JsonProcessingException {
    String updateBuildStatusUrl = "/gcb/builds/" + ACCOUNT + "/" + BUILD_ID;

    wmIgor.stubFor(
        WireMock.put(urlPathEqualTo(updateBuildStatusUrl))
            .withQueryParam("status", equalTo(BUILD_STATUS))
            .willReturn(aResponse().withStatus(200).withBody("[]")));

    notificationAgent.processEvent(createEvent());

    wmIgor.verify(
        1,
        putRequestedFor(urlPathEqualTo(updateBuildStatusUrl))
            .withQueryParam("status", equalTo(BUILD_STATUS)));
  }

  private Event createEvent() throws JsonProcessingException {
    MessageDescription messageDescription =
        MessageDescription.builder()
            .subscriptionName(ACCOUNT)
            .messagePayload(mapper.writeValueAsString(Map.of("key", "value")))
            .messageAttributes(Map.of("buildId", BUILD_ID, "status", BUILD_STATUS))
            .build();
    Map<String, Object> content = new HashMap<>();
    content.put("messageDescription", messageDescription);

    Metadata details = new Metadata();
    details.setType("googleCloudBuild");

    Event event = new Event();
    event.setContent(content);
    event.setDetails(details);
    return event;
  }
}
