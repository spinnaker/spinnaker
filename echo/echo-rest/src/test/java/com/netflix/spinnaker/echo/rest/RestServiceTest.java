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

package com.netflix.spinnaker.echo.rest;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.netflix.spinnaker.echo.util.RetrofitUtils;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import java.util.Map;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.POST;

public class RestServiceTest {
  WireMockServer wireMockServer;
  int port;
  RestService restService;
  String baseUrl = "http://localhost:PORT/api";

  @BeforeEach
  void setUp() {
    wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    wireMockServer.start();
    port = wireMockServer.port();
    WireMock.configureFor("localhost", port);

    stubFor(post(urlEqualTo("/api/")).willReturn(aResponse().withStatus(200)));

    stubFor(post(urlEqualTo("/")).willReturn(aResponse().withStatus(200)));

    baseUrl = baseUrl.replaceFirst("PORT", String.valueOf(port));

    restService =
        new Retrofit.Builder()
            .baseUrl(RetrofitUtils.getBaseUrl(baseUrl))
            .client(new OkHttpClient())
            .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
            .addConverterFactory(JacksonConverterFactory.create())
            .build()
            .create(RestService.class);
  }

  @AfterEach
  void tearDown() {
    wireMockServer.stop();
  }

  @Test
  void testRetService_withPostMappingSingleSlash() {

    Retrofit2SyncCall.execute(restService.recordEvent(Map.of()));

    verify(0, postRequestedFor(urlEqualTo("/api/")));

    // @POST("/") wrongly results in calling "http://localhost:<port>/"
    verify(1, postRequestedFor(urlEqualTo("/")));
  }

  @Test
  void testRetService_withPostMappingDot() {

    Retrofit2SyncCall.execute(restService.recordEvent2(Map.of()));

    // @POST(".") rightly results in calling "http://localhost:<port>/api/"
    verify(1, postRequestedFor(urlEqualTo("/api/")));
    verify(0, postRequestedFor(urlEqualTo("/")));
  }

  private interface RestService {
    @POST("/")
    Call<Void> recordEvent(@Body Map<String, Object> event);

    @POST(".")
    Call<Void> recordEvent2(@Body Map<String, Object> event);
  }
}
