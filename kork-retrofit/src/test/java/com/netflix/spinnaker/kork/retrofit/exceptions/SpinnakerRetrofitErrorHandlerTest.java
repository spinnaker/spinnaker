/*
 * Copyright 2020 Avast Software, Inc.
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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jakewharton.retrofit.Ok3Client;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.client.Response;
import retrofit.converter.JacksonConverter;
import retrofit.http.GET;

class SpinnakerRetrofitErrorHandlerTest {

  private static RetrofitService retrofitService;

  private static final MockWebServer mockWebServer = new MockWebServer();

  @BeforeAll
  static void setupOnce() throws Exception {
    mockWebServer.start();

    retrofitService =
        new RestAdapter.Builder()
            .setClient(new Ok3Client())
            .setEndpoint(mockWebServer.url("/").toString())
            .setErrorHandler(SpinnakerRetrofitErrorHandler.getInstance())
            .build()
            .create(RetrofitService.class);
  }

  @AfterAll
  static void shutdownOnce() throws Exception {
    mockWebServer.shutdown();
  }

  @Test
  void testNotFoundIsNotRetryable() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.NOT_FOUND.value()));
    SpinnakerHttpException notFoundException =
        catchThrowableOfType(() -> retrofitService.getFoo(), SpinnakerHttpException.class);
    assertThat(notFoundException.getRetryable()).isNotNull();
    assertThat(notFoundException.getRetryable()).isFalse();
  }

  @Test
  void testSpinnakerNetworkException() {
    mockWebServer.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
    assertThatExceptionOfType(SpinnakerNetworkException.class)
        .isThrownBy(() -> retrofitService.getFoo());
  }

  @ParameterizedTest(name = "Deserialize response using {0}")
  // Test the different converters used to deserialize the response body to the
  // SpinnakerServerException.RetrofitErrorResponseBody class:
  //
  // - the JacksonConverter constructed without an ObjectMapper is used in
  //   Clouddriver's RestAdapter to communicate with Front50Service
  //
  // - the JacksonConverter constructed with an ObjectMapper is used in Rosco's RestAdapter to
  //   communicate with Clouddriver
  //
  // - GSONConverter is the default converter used by Retrofit if no converter
  //   is set when building out the RestAdapter
  @ValueSource(
      strings = {"Default_GSONConverter", "JacksonConverter", "JacksonConverterWithObjectMapper"})
  void testResponseWithExtraField(String retrofitConverter) throws Exception {
    Map<String, String> responseBodyMap = new HashMap<>();
    responseBodyMap.put("timestamp", "123123123123");
    responseBodyMap.put("message", "Not Found error Message");
    String responseBodyString = new ObjectMapper().writeValueAsString(responseBodyMap);

    RestAdapter.Builder restAdapter =
        new RestAdapter.Builder()
            .setEndpoint(mockWebServer.url("/").toString())
            .setErrorHandler(SpinnakerRetrofitErrorHandler.getInstance());

    if (retrofitConverter.equals("JacksonConverter")) {
      restAdapter.setConverter(new JacksonConverter());
    } else if (retrofitConverter.equals("JacksonConverterWithObjectMapper")) {
      ObjectMapper objectMapper =
          new ObjectMapper()
              .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
              .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

      restAdapter.setConverter(new JacksonConverter(objectMapper));
    }

    RetrofitService retrofitServiceTestConverter =
        restAdapter.build().create(RetrofitService.class);

    mockWebServer.enqueue(
        new MockResponse()
            .setBody(responseBodyString)
            // an arbitrary response code -- one that
            // SpinnakerRetrofitErrorHandler converts to a
            // SpinnakerServerException (or one of its children).
            .setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR.value()));

    // If the converter can not deserialize the response body to the
    // SpinnakerServerException.RetrofitErrorResponseBody
    // class, then a RuntimeException will be thrown with a ConversionException nested inside.
    //
    // java.lang.RuntimeException:
    //     retrofit.converter.ConversionException:
    //         com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException: Unrecognized field
    // "..."
    //
    // so make sure we get a SpinnakerHttpException from calling getFoo
    SpinnakerHttpException spinnakerHttpException =
        catchThrowableOfType(retrofitServiceTestConverter::getFoo, SpinnakerHttpException.class);
    assertThat(spinnakerHttpException.getUrl()).isEqualTo(mockWebServer.url("/foo").toString());
  }

  @Test
  void testBadRequestIsNotRetryable() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.BAD_REQUEST.value()));
    SpinnakerHttpException spinnakerHttpException =
        catchThrowableOfType(() -> retrofitService.getFoo(), SpinnakerHttpException.class);
    assertThat(spinnakerHttpException.getRetryable()).isNotNull();
    assertThat(spinnakerHttpException.getRetryable()).isFalse();
    assertThat(spinnakerHttpException.getUrl()).isEqualTo(mockWebServer.url("/foo").toString());
  }

  @Test
  void testOtherClientErrorHasNullRetryable() {
    // Arbitrarily choose GONE as an example of a client (e.g. 4xx) error that
    // we expect to have null retryable
    mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.GONE.value()));
    SpinnakerHttpException spinnakerHttpException =
        catchThrowableOfType(() -> retrofitService.getFoo(), SpinnakerHttpException.class);
    assertThat(spinnakerHttpException.getRetryable()).isNull();
    assertThat(spinnakerHttpException.getUrl()).isEqualTo(mockWebServer.url("/foo").toString());
  }

  @Test
  void testResponseHeadersInException() {
    // Check response headers are retrievable from a SpinnakerHttpException
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(HttpStatus.BAD_REQUEST.value())
            .setHeader("Test", "true"));
    SpinnakerHttpException spinnakerHttpException =
        catchThrowableOfType(() -> retrofitService.getFoo(), SpinnakerHttpException.class);
    assertThat(spinnakerHttpException.getHeaders().containsKey("Test")).isTrue();
    assertThat(spinnakerHttpException.getHeaders().get("Test").contains("true")).isTrue();
    assertThat(spinnakerHttpException.getUrl()).isEqualTo(mockWebServer.url("/foo").toString());
  }

  @Test
  void testExceptionFromRetrofitErrorHasNullHttpMethod() {
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(HttpStatus.BAD_REQUEST.value())
            .setHeader("Test", "true"));
    SpinnakerHttpException spinnakerHttpException =
        catchThrowableOfType(() -> retrofitService.getFoo(), SpinnakerHttpException.class);
    assertThat(spinnakerHttpException.getHttpMethod()).isNull();
  }

  @Test
  void testSimpleSpinnakerNetworkException() {
    String message = "my custom message";
    IOException e = new IOException(message);
    String url = "http://localhost";
    RetrofitError retrofitError = RetrofitError.networkError(url, e);
    SpinnakerRetrofitErrorHandler handler = SpinnakerRetrofitErrorHandler.getInstance();
    Throwable throwable = handler.handleError(retrofitError);
    assertThat(throwable).hasMessage(message);
    assertThat(throwable).isInstanceOf(SpinnakerNetworkException.class);
    SpinnakerNetworkException spinnakerNetworkException = (SpinnakerNetworkException) throwable;
    assertThat(spinnakerNetworkException.getUrl()).isEqualTo(url);
  }

  @Test
  void testSpinnakerConversionException() {
    mockWebServer.enqueue(
        new MockResponse().setBody("Invalid JSON response").setResponseCode(HttpStatus.OK.value()));

    SpinnakerConversionException spinnakerConversionException =
        catchThrowableOfType(() -> retrofitService.getData(), SpinnakerConversionException.class);
    assertThat(spinnakerConversionException.getRetryable()).isNotNull();
    assertThat(spinnakerConversionException.getRetryable()).isFalse();
    assertThat(spinnakerConversionException)
        .hasMessageContaining("Expected BEGIN_OBJECT but was STRING at line 1 column 1 path $");
    assertThat(spinnakerConversionException.getUrl())
        .isEqualTo(mockWebServer.url("/data").toString());
  }

  @Test
  void testChainSpinnakerException_SpinnakerNetworkException() {
    SpinnakerRetrofitErrorHandler handler = SpinnakerRetrofitErrorHandler.getInstance();

    String originalMessage = "original message";
    String newMessage = "new message";

    IOException originalException = new IOException(originalMessage);

    String url = "http://localhost";
    RetrofitError retrofitError = RetrofitError.networkError(url, originalException);

    Throwable newException =
        handler.handleError(
            retrofitError,
            (exception) -> String.format("%s: %s", newMessage, exception.getMessage()));

    assertThat(newException).isInstanceOf(SpinnakerNetworkException.class);
    assertThat(newException).hasMessage("new message: original message");
    assertThat(newException.getCause()).hasMessage(originalMessage);
    SpinnakerNetworkException spinnakerNetworkException = (SpinnakerNetworkException) newException;
    assertThat(spinnakerNetworkException.getUrl()).isEqualTo(url);
  }

  interface RetrofitService {
    @GET("/foo")
    Response getFoo();

    @GET("/data")
    Map<String, String> getData();
  }
}
