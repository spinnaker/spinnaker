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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Assert;
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

public class SpinnakerRetrofitErrorHandlerTest {

  private static RetrofitService retrofitService;

  private static final MockWebServer mockWebServer = new MockWebServer();

  @BeforeAll
  public static void setupOnce() throws Exception {
    mockWebServer.start();
    retrofitService =
        new RestAdapter.Builder()
            .setEndpoint(mockWebServer.url("/").toString())
            .setErrorHandler(SpinnakerRetrofitErrorHandler.getInstance())
            .build()
            .create(RetrofitService.class);
  }

  @AfterAll
  public static void shutdownOnce() throws Exception {
    mockWebServer.shutdown();
  }

  @Test
  public void testNotFoundIsNotRetryable() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.NOT_FOUND.value()));
    SpinnakerHttpException notFoundException =
        assertThrows(SpinnakerHttpException.class, () -> retrofitService.getFoo());
    assertNotNull(notFoundException.getRetryable());
    assertFalse(notFoundException.getRetryable());
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
  public void testResponseWithExtraField(String retrofitConverter) throws Exception {
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
    assertThrows(SpinnakerHttpException.class, retrofitServiceTestConverter::getFoo);
  }

  @Test
  public void testBadRequestIsNotRetryable() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.BAD_REQUEST.value()));
    SpinnakerHttpException spinnakerHttpException =
        assertThrows(SpinnakerHttpException.class, () -> retrofitService.getFoo());
    assertNotNull(spinnakerHttpException.getRetryable());
    assertFalse(spinnakerHttpException.getRetryable());
  }

  @Test
  public void testOtherClientErrorHasNullRetryable() {
    // Arbitrarily choose GONE as an example of a client (e.g. 4xx) error that
    // we expect to have null retryable
    mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.GONE.value()));
    SpinnakerHttpException spinnakerHttpException =
        assertThrows(SpinnakerHttpException.class, () -> retrofitService.getFoo());
    assertNull(spinnakerHttpException.getRetryable());
  }

  @Test
  public void testResponseHeadersInException() {
    // Check response headers are retrievable from a SpinnakerHttpException
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(HttpStatus.BAD_REQUEST.value())
            .setHeader("Test", "true"));
    SpinnakerHttpException spinnakerHttpException =
        assertThrows(SpinnakerHttpException.class, () -> retrofitService.getFoo());
    assertTrue(spinnakerHttpException.getHeaders().containsKey("Test"));
    assertTrue(spinnakerHttpException.getHeaders().get("Test").contains("true"));
  }

  @Test
  public void testSimpleSpinnakerNetworkException() {
    String message = "my custom message";
    IOException e = new IOException(message);
    RetrofitError retrofitError = RetrofitError.networkError("http://localhost", e);
    SpinnakerRetrofitErrorHandler handler = SpinnakerRetrofitErrorHandler.getInstance();
    Throwable throwable = handler.handleError(retrofitError);
    Assert.assertEquals(message, throwable.getMessage());
  }

  interface RetrofitService {
    @GET("/foo")
    Response getFoo();
  }
}
