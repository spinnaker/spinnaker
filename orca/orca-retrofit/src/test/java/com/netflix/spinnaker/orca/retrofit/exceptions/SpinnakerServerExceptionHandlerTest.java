/*
 * Copyright 2023 Salesforce, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.retrofit.exceptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerNetworkException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerRetrofitErrorHandler;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException;
import com.netflix.spinnaker.orca.exceptions.ExceptionHandler;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.client.Client;
import retrofit.client.Request;
import retrofit.client.Response;
import retrofit.converter.GsonConverter;
import retrofit.converter.JacksonConverter;
import retrofit.mime.TypedString;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

class SpinnakerServerExceptionHandlerTest {
  private static Retrofit2Service retrofit2Service;

  private static final MockWebServer mockWebServer = new MockWebServer();

  private static final String URL = mockWebServer.url("https://some-url").toString();

  private static final String TASK_NAME = "task name";

  private static final Response response =
      new Response(
          URL,
          500,
          "arbitrary reason",
          List.of(),
          new TypedString("{ message: \"arbitrary message\" }"));

  private static final Response responseWithNullBody =
      new Response(URL, 500, "arbitrary reason", List.of(), null);

  private static final Response response429 =
      new Response(
          URL,
          429,
          "arbitrary reason",
          List.of(),
          new TypedString("{ message: \"arbitrary message\" }"));

  private static final Response response503 =
      new Response(
          URL,
          503,
          "arbitrary reason",
          List.of(),
          new TypedString("{ message: \"arbitrary message\" }"));

  private static final Response response504 =
      new Response(
          URL,
          504,
          "arbitrary reason",
          List.of(),
          new TypedString("{ message: \"arbitrary message\" }"));

  private static final Response responseWithError =
      new Response(
          URL,
          500,
          "arbitrary reason",
          List.of(),
          new TypedString("{ message: \"arbitrary message\", error: \"error property\" }"));

  private static final Response responseWithException =
      new Response(
          URL,
          500,
          "arbitrary reason",
          List.of(),
          new TypedString("{ message: \"arbitrary message\", exception: \"exception property\" }"));

  private static final Response responseWithErrors =
      new Response(
          URL,
          500,
          "arbitrary reason",
          List.of(),
          new TypedString(
              "{ message: \"arbitrary message\", errors: [\"error one\", \"error two\"] }"));

  private static final Response responseWithErrorAndErrors =
      new Response(
          URL,
          500,
          "arbitrary reason",
          List.of(),
          new TypedString(
              "{ message: \"arbitrary message\", error: \"error property\", errors: [\"error one\", \"error two\"] }"));

  private static final Response responseWithErrorAndErrorsAndMessages =
      new Response(
          URL,
          500,
          "arbitrary reason",
          List.of(),
          new TypedString(
              "{ message: \"arbitrary message\", error: \"error property\", errors: [\"error one\", \"error two\"], messages: [\"message one\", \"message two\"] }"));

  private static final GsonConverter gsonConverter = new GsonConverter(new Gson());
  private static final IOException io = new IOException("an IOException");

  SpinnakerServerExceptionHandler spinnakerServerExceptionHandler =
      new SpinnakerServerExceptionHandler();

  @BeforeAll
  static void setupOnce() {
    retrofit2Service =
        new Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
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

  interface Retrofit2Service {
    @retrofit2.http.GET("/retrofit2")
    Call<String> getRetrofit2();

    @retrofit2.http.POST("/retrofit2")
    Call<String> postRetrofit2();
  }

  @ParameterizedTest(name = "{index} => onlyHandlesSpinnakerServerExceptionAndSubclasses {0}")
  @MethodSource("exceptionsForHandlesTest")
  void onlyHandlesSpinnakerServerExceptionAndSubclasses(Exception ex, boolean supported) {
    assertThat(spinnakerServerExceptionHandler.handles(ex)).isEqualTo(supported);
  }

  private static Stream<Arguments> exceptionsForHandlesTest() {
    RetrofitError retrofitError = makeRetrofitError(response);
    okhttp3.Request request = new okhttp3.Request.Builder().url(URL).build();
    return Stream.of(
        Arguments.of(new SpinnakerHttpException(retrofitError), true),
        Arguments.of(new SpinnakerServerException(io, request), true),
        Arguments.of(new SpinnakerNetworkException(io, request), true),
        Arguments.of(new RuntimeException(), false),
        Arguments.of(new IllegalArgumentException(), false));
  }

  @ParameterizedTest(name = "{index} => testRetryable {0}")
  @MethodSource({"exceptionsForRetryTest", "retrofit2Exceptions"})
  void testRetryable(SpinnakerServerException ex, boolean expectedRetryable) {
    ExceptionHandler.Response response = spinnakerServerExceptionHandler.handle(TASK_NAME, ex);
    assertThat(response.isShouldRetry()).isEqualTo(expectedRetryable);
  }

  private static Stream<Arguments> retrofit2Exceptions() {
    String responseBody = "{\"test\":\"test\"}";

    // HTTP 503 is always retryable
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(HttpStatus.SERVICE_UNAVAILABLE.value())
            .setBody(responseBody));
    SpinnakerServerException retryable =
        catchThrowableOfType(
            () -> retrofit2Service.getRetrofit2().execute(), SpinnakerServerException.class);

    // not retryable because POST is not idempotent
    mockWebServer.enqueue(
        new MockResponse().setResponseCode(HttpStatus.BAD_REQUEST.value()).setBody(responseBody));
    SpinnakerServerException notRetryable =
        catchThrowableOfType(
            () -> retrofit2Service.postRetrofit2().execute(), SpinnakerServerException.class);

    // not retryable despite idempotent method because response code not within 429, 502, 503, 504
    mockWebServer.enqueue(
        new MockResponse().setResponseCode(HttpStatus.UNAUTHORIZED.value()).setBody(responseBody));
    SpinnakerServerException notRetryableIdempotent =
        catchThrowableOfType(
            () -> retrofit2Service.getRetrofit2().execute(), SpinnakerServerException.class);

    // idempotent network errors are retryable
    mockWebServer.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
    SpinnakerServerException idempotentNetworkException =
        catchThrowableOfType(
            () -> retrofit2Service.getRetrofit2().execute(), SpinnakerNetworkException.class);

    // throttling is retryable if idempotent
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(HttpStatus.TOO_MANY_REQUESTS.value())
            .setBody(responseBody));
    SpinnakerServerException throttledIdempotent =
        catchThrowableOfType(
            () -> retrofit2Service.getRetrofit2().execute(), SpinnakerServerException.class);

    // throttling is not retryable if not idempotent
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(HttpStatus.TOO_MANY_REQUESTS.value())
            .setBody(responseBody));
    SpinnakerServerException throttledNonIdempotent =
        catchThrowableOfType(
            () -> retrofit2Service.postRetrofit2().execute(), SpinnakerServerException.class);

    return Stream.of(
        Arguments.of(retryable, true),
        Arguments.of(notRetryable, false),
        Arguments.of(notRetryableIdempotent, false),
        Arguments.of(idempotentNetworkException, true),
        Arguments.of(throttledIdempotent, true),
        Arguments.of(throttledNonIdempotent, false));
  }

  private static Stream<Arguments> exceptionsForRetryTest() throws Exception {
    // This isn't retryable because it's not considered an idempotent request
    // since there's no retrofit http method annotation in the exception's stack
    // trace.
    RetrofitError notRetryableRetrofitError = makeRetrofitError(response504);
    SpinnakerServerException notRetryable = new SpinnakerHttpException(notRetryableRetrofitError);

    // This is retryable because HTTP 503 is retryable, regardless of request method
    RetrofitError retryableRetrofitError = makeRetrofitError(response503);
    SpinnakerServerException retryable = new SpinnakerHttpException(retryableRetrofitError);

    // Exceptions generated via "real" retrofit API calls (aka via interface
    // methods annotated with e.g. @GET/HEAD/DELETE/PUT are considered
    // idempotent and so have a chance of being retryable.  The only other
    // retryable exceptions are HTTP 503 independent of the http method.
    //
    // So, generate some of these exceptions, in combination with
    // SpinnakerRetrofitErrorHandler.  We don't need to exercise all of
    // BaseRetrofitExceptionHandler.shouldRetry, only enough to ensure that the
    // way SpinnakerHttpRetrofitHandler generates exceptions, and the way
    // SpinnakerServerExceptionHandler invokes shouldRetry, results in the
    // expected behavior as far as picking up retrofit annotations.
    Client client = mock(Client.class);

    DummyRetrofitApi api =
        new RestAdapter.Builder()
            .setEndpoint(URL)
            .setClient(client)
            .setConverter(new JacksonConverter())
            .setErrorHandler(SpinnakerRetrofitErrorHandler.getInstance())
            .build()
            .create(DummyRetrofitApi.class);

    // Retryable since it's a network error from an idempotent request
    when(client.execute(any(Request.class))).thenThrow(RetrofitError.networkError(URL, io));
    SpinnakerServerException idempotentNetworkException = expectingException(api::get);

    // Retryable since it's one of the allowed gateway error codes from an
    // idempotent request (502/bad gateway or 504/gateway timeout)
    when(client.execute(any(Request.class))).thenThrow(makeRetrofitError(response504));
    SpinnakerServerException idempotentGatewayException = expectingException(api::head);

    // Throttling of an idempotent request is retryable
    when(client.execute(any(Request.class))).thenThrow(makeRetrofitError(response429));
    SpinnakerServerException idempotentThrottlingException = expectingException(api::delete);

    // Throttling of a non-idempotent request isn't retryable
    when(client.execute(any(Request.class))).thenThrow(makeRetrofitError(response429));
    SpinnakerServerException nonIdempotentThrottlingException =
        expectingException(() -> api.post("whatever"));

    return Stream.of(
        Arguments.of(notRetryable, false),
        Arguments.of(retryable, true),
        Arguments.of(idempotentNetworkException, true),
        Arguments.of(idempotentGatewayException, true),
        Arguments.of(idempotentThrottlingException, true),
        Arguments.of(nonIdempotentThrottlingException, false));
  }

  @ParameterizedTest(name = "{index} => verifyResponseDetails {0}")
  @MethodSource({"exceptionsForResponseDetailsTest", "nonRetryableRetrofit2Exceptions"})
  void verifyResponseDetails(SpinnakerHttpException spinnakerHttpException) {
    ExceptionHandler.Response response =
        spinnakerServerExceptionHandler.handle(TASK_NAME, spinnakerHttpException);

    // verify that exception handling has populated the stage context as
    // expected.  This duplicates some logic in SpinnakerServerExceptionHandler,
    // but at least it helps detect future changes.
    Map<String, Object> responseBody = spinnakerHttpException.getResponseBody();
    String httpMethod = spinnakerHttpException.getHttpMethod();
    String error = null;
    List<String> errors = null;

    ImmutableMap.Builder<String, Object> builder =
        ImmutableMap.<String, Object>builder()
            .put("kind", "HTTP")
            .put("url", spinnakerHttpException.getUrl())
            .put("status", spinnakerHttpException.getResponseCode());

    if (httpMethod != null) {
      builder.put("method", httpMethod);
    }

    if (responseBody == null) {
      error = spinnakerHttpException.getMessage();
      errors = List.of();
    } else {
      error = (String) responseBody.getOrDefault("error", spinnakerHttpException.getReason());
      errors =
          (List<String>)
              responseBody.getOrDefault("errors", responseBody.getOrDefault("messages", List.of()));
      String message = (String) responseBody.get("message");
      if (errors.isEmpty()) {
        errors = List.of(spinnakerHttpException.getMessage());
      }

      Object exception = responseBody.get("exception");
      if (exception != null) {
        builder.put("rootException", exception);
      } else {
        builder.put("stackTrace", Throwables.getStackTraceAsString(spinnakerHttpException));
      }
    }

    if (error != null) {
      builder.put("error", error);
    }
    builder.put("errors", errors);

    Map<String, Object> responseDetails = builder.build();

    ExceptionHandler.Response expectedResponse =
        new ExceptionHandler.Response(
            "SpinnakerHttpException", TASK_NAME, responseDetails, false /* shouldRetry */);

    compareResponse(expectedResponse, response);
  }

  private static Stream<SpinnakerHttpException> exceptionsForResponseDetailsTest() {
    return Stream.of(
            makeRetrofitError(response),
            makeRetrofitError(responseWithNullBody),
            makeRetrofitError(responseWithError),
            makeRetrofitError(responseWithException),
            makeRetrofitError(responseWithErrors),
            makeRetrofitError(responseWithErrorAndErrors),
            makeRetrofitError(responseWithErrorAndErrorsAndMessages))
        .map(SpinnakerServerExceptionHandlerTest::makeSpinnakerHttpException);
  }

  private static Stream<SpinnakerHttpException> nonRetryableRetrofit2Exceptions() {
    return retrofit2Exceptions()
        .filter(args -> args.get()[1].equals(false))
        .map(args -> ((SpinnakerHttpException) args.get()[0]));
  }

  private void compareResponse(
      ExceptionHandler.Response expectedResponse, ExceptionHandler.Response actualResponse) {
    assertThat(actualResponse).isNotNull();
    // Don't look at overall equality since that includes a date
    assertThat(actualResponse.getExceptionType()).isEqualTo(expectedResponse.getExceptionType());
    assertThat(actualResponse.getOperation()).isEqualTo(expectedResponse.getOperation());
    assertThat(actualResponse.getDetails()).isEqualTo(expectedResponse.getDetails());
    assertThat(actualResponse.isShouldRetry()).isEqualTo(expectedResponse.isShouldRetry());
  }

  private static SpinnakerHttpException makeSpinnakerHttpException(RetrofitError retrofitError) {
    return new SpinnakerHttpException(retrofitError);
  }

  private static RetrofitError makeRetrofitError(Response response) {
    return RetrofitError.httpError(URL, response, gsonConverter, String.class);
  }

  private static <V> SpinnakerServerException expectingException(Callable<V> action)
      throws Exception {
    try {
      action.call();
      throw new IllegalStateException("Callable did not throw an exception");
    } catch (SpinnakerServerException e) {
      return e;
    }
  }
}
