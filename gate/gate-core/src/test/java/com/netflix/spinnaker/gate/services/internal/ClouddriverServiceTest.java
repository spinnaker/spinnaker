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

package com.netflix.spinnaker.gate.services.internal;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import java.util.Map;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

public class ClouddriverServiceTest {
  WireMockServer wireMockServer;
  int port;
  ClouddriverService clouddriverService;
  String baseUrl = "http://localhost:PORT";

  @BeforeEach
  void setUp() {
    wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    wireMockServer.start();
    port = wireMockServer.port();
    WireMock.configureFor("localhost", port);

    baseUrl = baseUrl.replaceFirst("PORT", String.valueOf(port));

    clouddriverService =
        new Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(new OkHttpClient())
            .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
            .addConverterFactory(JacksonConverterFactory.create())
            .build()
            .create(ClouddriverService.class);
  }

  @AfterEach
  void tearDown() {
    wireMockServer.stop();
  }

  @Test
  void testClouddriverService_withQueryMap() {
    stubFor(
        get(urlEqualTo("/search?q=app1&type=securityGroups&platform=aws&pageSize=500&page=1"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(
                        "[{\"pageNumber\":1,\"pageSize\":500,\"platform\":\"aws\",\"query\":\"app1\",\"results\":[],\"totalMatches\":0}]")));

    Retrofit2SyncCall.execute(
        clouddriverService.search("app1", "securityGroups", "aws", 500, 1, Map.of()));

    verify(
        1,
        getRequestedFor(
            urlEqualTo("/search?q=app1&type=securityGroups&platform=aws&pageSize=500&page=1")));
  }
}
