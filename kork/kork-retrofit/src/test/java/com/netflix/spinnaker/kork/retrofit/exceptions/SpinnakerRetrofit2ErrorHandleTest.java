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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

class SpinnakerRetrofit2ErrorHandleTest {

  private static Retrofit2Service retrofit2Service;

  private static final MockWebServer mockWebServer = new MockWebServer();

  private static String responseBodyString;

  private static String baseUrl = mockWebServer.url("/").toString();

  @BeforeAll
  static void setupOnce() throws Exception {

    Map<String, String> responseBodyMap = new HashMap<>();
    responseBodyMap.put("timestamp", "123123123123");
    responseBodyMap.put("message", "Something happened error message");
    responseBodyString = new ObjectMapper().writeValueAsString(responseBodyMap);

    retrofit2Service =
        new Retrofit.Builder()
            .baseUrl(baseUrl)
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
  static void shutdownOnce() throws Exception {
    mockWebServer.shutdown();
  }

  @Test
  void testRetrofitNotFoundIsNotRetryable() {

    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(HttpStatus.NOT_FOUND.value())
            .setBody(responseBodyString));
    SpinnakerHttpException notFoundException =
        catchThrowableOfType(
            () -> retrofit2Service.getRetrofit2().execute(), SpinnakerHttpException.class);
    assertThat(notFoundException.getRetryable()).isNotNull();
    assertThat(notFoundException.getRetryable()).isFalse();
    assertThat(notFoundException.getUrl()).isEqualTo(mockWebServer.url("/retrofit2").toString());
  }

  @Test
  void testRetrofitBadRequestIsNotRetryable() {

    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(HttpStatus.NOT_FOUND.value())
            .setBody(responseBodyString));
    SpinnakerHttpException spinnakerHttpException =
        catchThrowableOfType(
            () -> retrofit2Service.getRetrofit2().execute(), SpinnakerHttpException.class);
    assertThat(spinnakerHttpException.getRetryable()).isNotNull();
    assertThat(spinnakerHttpException.getRetryable()).isFalse();
    assertThat(spinnakerHttpException.getUrl())
        .isEqualTo(mockWebServer.url("/retrofit2").toString());
  }

  @Test
  void testRetrofitOtherClientErrorHasNullRetryable() {

    mockWebServer.enqueue(
        new MockResponse().setResponseCode(HttpStatus.GONE.value()).setBody(responseBodyString));
    SpinnakerHttpException spinnakerHttpException =
        catchThrowableOfType(
            () -> retrofit2Service.getRetrofit2().execute(), SpinnakerHttpException.class);
    assertThat(spinnakerHttpException.getRetryable()).isNull();
    assertThat(spinnakerHttpException.getUrl())
        .isEqualTo(mockWebServer.url("/retrofit2").toString());
  }

  @Test
  void testRetrofitSimpleSpinnakerNetworkException() {
    mockWebServer.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
    SpinnakerNetworkException spinnakerNetworkException =
        catchThrowableOfType(
            () -> retrofit2Service.getRetrofit2().execute(), SpinnakerNetworkException.class);
    assertThat(spinnakerNetworkException.getUrl())
        .isEqualTo(mockWebServer.url("/retrofit2").toString());
  }

  @Test
  void testResponseHeadersInException() {

    // Check response headers are retrievable from a SpinnakerHttpException
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(HttpStatus.NOT_FOUND.value())
            .setBody(responseBodyString)
            .setHeader("Test", "true"));
    SpinnakerHttpException spinnakerHttpException =
        catchThrowableOfType(
            () -> retrofit2Service.getRetrofit2().execute(), SpinnakerHttpException.class);
    assertThat(spinnakerHttpException.getHeaders().containsKey("Test")).isTrue();
    assertThat(spinnakerHttpException.getHeaders().get("Test").contains("true")).isTrue();
    assertThat(spinnakerHttpException.getUrl())
        .isEqualTo(mockWebServer.url("/retrofit2").toString());
  }

  @Test
  void testHttpMethodInException() {
    // Check http request method is retrievable from a SpinnakerHttpException
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(HttpStatus.BAD_REQUEST.value())
            .setBody(responseBodyString));
    SpinnakerHttpException spinnakerHttpException =
        catchThrowableOfType(
            () -> retrofit2Service.deleteRetrofit2().execute(), SpinnakerHttpException.class);
    assertThat(spinnakerHttpException.getUrl())
        .isEqualTo(mockWebServer.url("/retrofit2").toString());
    assertThat(spinnakerHttpException.getHttpMethod()).isEqualTo(HttpMethod.DELETE.toString());
  }

  @Test
  void testNotParameterizedException() {
    IllegalArgumentException illegalArgumentException =
        catchThrowableOfType(
            () -> retrofit2Service.testNotParameterized().execute(),
            IllegalArgumentException.class);
    assertThat(illegalArgumentException.getCause().getMessage())
        .isEqualTo("Call return type must be parameterized as Call<Foo> or Call<? extends Foo>");
  }

  @Test
  void testWrongReturnTypeException() {

    IllegalArgumentException illegalArgumentException =
        catchThrowableOfType(
            () -> retrofit2Service.testWrongReturnType().execute(), IllegalArgumentException.class);

    assertThat(illegalArgumentException)
        .hasMessage(
            "Unable to create call adapter for interface com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerRetrofit2ErrorHandleTest$DummyWithExecute\n"
                + "    for method Retrofit2Service.testWrongReturnType");
  }

  interface Retrofit2Service {
    @retrofit2.http.GET("/retrofit2")
    Call<String> getRetrofit2();

    @retrofit2.http.GET("/retrofit2/para")
    Call testNotParameterized();

    @retrofit2.http.GET("/retrofit2/wrongReturnType")
    DummyWithExecute testWrongReturnType();

    @retrofit2.http.DELETE("/retrofit2")
    Call<String> deleteRetrofit2();
  }

  @Test
  void testSpinnakerConversionException() {

    String invalidJsonTypeResponseBody = "{'testcasename': 'testSpinnakerConversionException'";

    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(HttpStatus.OK.value())
            .setBody(invalidJsonTypeResponseBody));

    SpinnakerConversionException spinnakerConversionException =
        catchThrowableOfType(
            () -> retrofit2Service.getRetrofit2().execute(), SpinnakerConversionException.class);
    assertThat(spinnakerConversionException.getRetryable()).isNotNull();
    assertThat(spinnakerConversionException.getRetryable()).isFalse();
    assertThat(spinnakerConversionException)
        .hasMessage(
            "Failed to process response body: Cannot deserialize value of type `java.lang.String` from Object value (token `JsonToken.START_OBJECT`)\n"
                + " at [Source: (okhttp3.ResponseBody$BomAwareReader); line: 1, column: 1]");
    assertThat(spinnakerConversionException.getUrl())
        .isEqualTo(mockWebServer.url("/retrofit2").toString());
  }

  @Test
  void testNonJsonHttpErrorResponse() {

    String invalidJsonTypeResponseBody = "{'errorResponse': 'Failure'";
    int responseCode = HttpStatus.INTERNAL_SERVER_ERROR.value();
    String url = baseUrl + "retrofit2";
    String reason = "Server Error";

    mockWebServer.enqueue(
        new MockResponse().setResponseCode(responseCode).setBody(invalidJsonTypeResponseBody));
    SpinnakerHttpException spinnakerHttpException =
        catchThrowableOfType(
            () -> retrofit2Service.getRetrofit2().execute(), SpinnakerHttpException.class);
    assertThat(spinnakerHttpException.getResponseBody()).isNull();
    assertThat(spinnakerHttpException.getResponseCode()).isEqualTo(responseCode);
    assertThat(spinnakerHttpException)
        .hasMessage(
            "Status: " + responseCode + ", Method: GET, URL: " + url + ", Message: " + reason);
    assertThat(spinnakerHttpException.getUrl()).isEqualTo(url);
    assertThat(spinnakerHttpException.getReason()).isEqualTo(reason);
  }

  interface DummyWithExecute {
    void execute();
  }
}
