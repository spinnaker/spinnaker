/*
 * Copyright 2023 OpsMx, Inc.
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

package com.netflix.spinnaker.kork.retrofit.exceptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

public class SpinnakerRetrofit2ErrorHandleTest {

  private static Retrofit2Service retrofit2Service;

  private static final MockWebServer mockWebServer = new MockWebServer();

  private static String responseBodyString;

  @BeforeAll
  public static void setupOnce() throws Exception {

    Map<String, String> responseBodyMap = new HashMap<>();
    responseBodyMap.put("timestamp", "123123123123");
    responseBodyMap.put("message", "Something happened error message");
    responseBodyString = new ObjectMapper().writeValueAsString(responseBodyMap);

    retrofit2Service =
        new Retrofit.Builder()
            .baseUrl(mockWebServer.url("/").toString())
            .client(
                new OkHttpClient.Builder()
                    .callTimeout(1, TimeUnit.SECONDS)
                    .connectTimeout(1, TimeUnit.SECONDS)
                    .build())
            .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
            .addConverterFactory(JacksonConverterFactory.create())
            .build()
            .create(Retrofit2Service.class);
  }

  @AfterAll
  public static void shutdownOnce() throws Exception {
    mockWebServer.shutdown();
  }

  @Test
  public void testRetrofitNotFoundIsNotRetryable() {

    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(HttpStatus.NOT_FOUND.value())
            .setBody(responseBodyString));
    SpinnakerHttpException notFoundException =
        assertThrows(SpinnakerHttpException.class, () -> retrofit2Service.getRetrofit2().execute());
    assertNotNull(notFoundException.getRetryable());
    assertFalse(notFoundException.getRetryable());
  }

  @Test
  public void testRetrofitBadRequestIsNotRetryable() {

    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(HttpStatus.NOT_FOUND.value())
            .setBody(responseBodyString));
    SpinnakerHttpException spinnakerHttpException =
        assertThrows(SpinnakerHttpException.class, () -> retrofit2Service.getRetrofit2().execute());
    assertNotNull(spinnakerHttpException.getRetryable());
    assertFalse(spinnakerHttpException.getRetryable());
  }

  @Test
  public void testRetrofitOtherClientErrorHasNullRetryable() {

    mockWebServer.enqueue(
        new MockResponse().setResponseCode(HttpStatus.GONE.value()).setBody(responseBodyString));
    SpinnakerHttpException spinnakerHttpException =
        assertThrows(SpinnakerHttpException.class, () -> retrofit2Service.getRetrofit2().execute());
    assertNull(spinnakerHttpException.getRetryable());
  }

  @Test
  public void testRetrofitSimpleSpinnakerNetworkException() {
    mockWebServer.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));

    assertThrows(SpinnakerNetworkException.class, () -> retrofit2Service.getRetrofit2().execute());
  }

  @Test
  public void testRetrofitSimpleSpinnakerServerException() {
    mockWebServer.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
    assertThrows(SpinnakerServerException.class, () -> retrofit2Service.getRetrofit2().execute());
  }

  @Test
  public void testResponseHeadersInException() {

    // Check response headers are retrievable from a SpinnakerHttpException
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(HttpStatus.NOT_FOUND.value())
            .setBody(responseBodyString)
            .setHeader("Test", "true"));
    SpinnakerHttpException spinnakerHttpException =
        assertThrows(SpinnakerHttpException.class, () -> retrofit2Service.getRetrofit2().execute());
    assertTrue(spinnakerHttpException.getHeaders().containsKey("Test"));
    assertTrue(spinnakerHttpException.getHeaders().get("Test").contains("true"));
  }

  @Test
  public void testNotParameterizedException() {

    IllegalArgumentException illegalArgumentException =
        assertThrows(
            IllegalArgumentException.class,
            () -> retrofit2Service.testNotParameterized().execute());

    assertEquals(
        "Call return type must be parameterized as Call<Foo> or Call<? extends Foo>",
        illegalArgumentException.getCause().getMessage());
  }

  @Test
  public void testWrongReturnTypeException() {

    IllegalArgumentException illegalArgumentException =
        assertThrows(
            IllegalArgumentException.class, () -> retrofit2Service.testWrongReturnType().execute());

    assertEquals(
        "Unable to create call adapter for interface com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerRetrofit2ErrorHandleTest$DummyWithExecute\n"
            + "    for method Retrofit2Service.testWrongReturnType",
        illegalArgumentException.getMessage());
  }

  interface Retrofit2Service {
    @retrofit2.http.GET("/retrofit2")
    Call<String> getRetrofit2();

    @retrofit2.http.GET("/retrofit2/para")
    Call testNotParameterized();

    @retrofit2.http.GET("/retrofit2/wrongReturnType")
    DummyWithExecute testWrongReturnType();
  }

  interface DummyWithExecute {
    void execute();
  }
}
