/*
 * Copyright 2025 The Home Depot, Inc.
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

package com.netflix.spinnaker.igor.util;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import java.io.IOException;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.http.GET;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class RetrofitUtilsTest {
  MockWebServer server;
  RetrofitService service;
  String baseUrl;

  @BeforeAll
  void setup() throws IOException {
    server = new MockWebServer();
    server.start();
    baseUrl = server.url("/v1").toString();
    service =
        new Retrofit.Builder()
            .baseUrl(RetrofitUtils.getBaseUrl(baseUrl))
            .client(new OkHttpClient())
            .build()
            .create(RetrofitService.class);
  }

  @AfterAll
  void teardown() throws IOException {
    server.shutdown();
  }

  @Test
  void urlWithoutTrailingSlashFailsWithoutUtilsFunc() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new Retrofit.Builder().baseUrl(baseUrl).build().create(RetrofitService.class));
  }

  @Test
  void addsTrailingSlashIfMissing() {
    assertDoesNotThrow(
        () -> {
          new Retrofit.Builder()
              .baseUrl(RetrofitUtils.getBaseUrl(baseUrl))
              .build()
              .create(RetrofitService.class);
        });
  }

  @Test
  void testGetRoot() {
    server.enqueue(new MockResponse().setResponseCode(200));
    Response resp = Retrofit2SyncCall.executeCall(service.getRoot());

    // @GET("/") uses server root
    assertThat(resp.raw().request().url().toString()).isEqualTo(baseUrl.replace("v1", ""));
  }

  @Test
  void testGetBaseUrl() {
    server.enqueue(new MockResponse().setResponseCode(200));
    Response resp = Retrofit2SyncCall.executeCall(service.getBaseUrl());

    // @GET(".") uses base url ending in slash
    assertThat(resp.raw().request().url().toString()).isEqualTo(baseUrl + "/");
  }

  @Test
  void testGetResource() {
    server.enqueue(new MockResponse().setResponseCode(200));
    Response resp = Retrofit2SyncCall.executeCall(service.getUsers());

    // @GET("users") uses relative path /users of base url
    assertThat(resp.raw().request().url().toString()).isEqualTo(baseUrl + "/users");
  }

  @Test
  void testAbsolutePath() {
    server.enqueue(new MockResponse().setResponseCode(200));
    Response resp = Retrofit2SyncCall.executeCall(service.getV2());

    // @GET("/v2") uses absolute path
    assertThat(resp.raw().request().url().toString()).isEqualTo(baseUrl.replace("v1", "v2"));
  }

  private interface RetrofitService {
    @GET("users")
    Call<Void> getUsers();

    @GET("/")
    Call<Void> getRoot();

    @GET(".")
    Call<Void> getBaseUrl();

    @GET("/v2")
    Call<Void> getV2();
  }
}
