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

package com.netflix.spinnaker.echo.services;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.netflix.spinnaker.echo.util.RetrofitUtils;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

public class IgorServiceTest {
  private static WireMockServer wireMockServer;
  private static int port;
  private static IgorService igorService;
  private static String baseUrl = "http://localhost:PORT";

  @BeforeAll
  static void setUpOnce() {
    wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    wireMockServer.start();
    port = wireMockServer.port();
    WireMock.configureFor("localhost", port);

    baseUrl = baseUrl.replaceFirst("PORT", String.valueOf(port));

    igorService =
        new Retrofit.Builder()
            .baseUrl(RetrofitUtils.getBaseUrl(baseUrl))
            .client(new OkHttpClient())
            .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
            .addConverterFactory(JacksonConverterFactory.create())
            .build()
            .create(IgorService.class);
  }

  @BeforeEach
  void setup(TestInfo testInfo) {
    System.out.println("--------------- Test " + testInfo.getDisplayName());
  }

  @AfterAll
  static void tearDown() {
    wireMockServer.stop();
  }

  @Test
  void testIgorService_getArtifacts() {
    stubFor(
        get(urlEqualTo("/builds/artifacts/1/master/job?propertyFile=propertyFile"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody("[{\"id\": \"gs://this/is/my/id1\", \"type\": \"gcs/object\"}]")));

    Retrofit2SyncCall.execute(igorService.getArtifacts(1, "master", "job", "propertyFile"));

    verify(
        1, getRequestedFor(urlEqualTo("/builds/artifacts/1/master/job?propertyFile=propertyFile")));
  }

  @Test
  void testIgorService_extractGoogleCloudBuildArtifacts() {
    RequestBody build =
        RequestBody.create("{\"key\":\"value\"}", MediaType.parse("application/json"));
    stubFor(
        put(urlEqualTo("/gcb/artifacts/extract/my-acc"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody("[{\"id\": \"gs://this/is/my/id1\", \"type\": \"gcs/object\"}]")));

    Retrofit2SyncCall.execute(igorService.extractGoogleCloudBuildArtifacts("my-acc", build));
    verify(1, putRequestedFor(urlEqualTo("/gcb/artifacts/extract/my-acc")));
  }
}
