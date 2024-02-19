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
 *
 */

package com.netflix.spinnaker.kork.retrofit.exceptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.google.gson.Gson;
import com.netflix.spinnaker.kork.exceptions.SpinnakerException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import retrofit.RetrofitError;
import retrofit.client.Response;
import retrofit.converter.GsonConverter;
import retrofit.mime.TypedString;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

class SpinnakerHttpExceptionTest {
  private static final String CUSTOM_MESSAGE = "custom message";

  @Test
  void testSpinnakerHttpExceptionFromRetrofitError() {
    String url = "http://localhost";
    int statusCode = 200;
    String message = "arbitrary message";
    String reason = "reason";
    Response response =
        new Response(
            url,
            statusCode,
            reason,
            List.of(),
            new TypedString("{ message: \"" + message + "\", name: \"test\" }"));
    RetrofitError retrofitError =
        RetrofitError.httpError(url, response, new GsonConverter(new Gson()), String.class);
    SpinnakerHttpException spinnakerHttpException = new SpinnakerHttpException(retrofitError);
    assertThat(spinnakerHttpException.getResponseBody()).isNotNull();
    Map<String, Object> errorResponseBody = spinnakerHttpException.getResponseBody();
    assertThat(errorResponseBody.get("name")).isEqualTo("test");
    assertThat(spinnakerHttpException.getResponseCode()).isEqualTo(statusCode);
    assertThat(spinnakerHttpException.getMessage())
        .isEqualTo("Status: " + statusCode + ", URL: " + url + ", Message: " + message);
    assertThat(spinnakerHttpException.getUrl()).isEqualTo(url);
    assertThat(spinnakerHttpException.getReason()).isEqualTo(reason);
    assertThat(spinnakerHttpException.getHttpMethod()).isNull();
  }

  @Test
  void testSpinnakerHttpExceptionFromRetrofitException() {
    final String validJsonResponseBodyString = "{\"name\":\"test\"}";
    ResponseBody responseBody =
        ResponseBody.create(
            MediaType.parse("application/json" + "; charset=utf-8"), validJsonResponseBodyString);
    retrofit2.Response response =
        retrofit2.Response.error(HttpStatus.NOT_FOUND.value(), responseBody);

    String url = "http://localhost/";
    Retrofit retrofit2Service =
        new Retrofit.Builder()
            .baseUrl(url)
            .addConverterFactory(JacksonConverterFactory.create())
            .build();
    assertThat(retrofit2Service.baseUrl().toString()).isEqualTo(url);
    SpinnakerHttpException notFoundException =
        new SpinnakerHttpException(response, retrofit2Service);
    assertThat(notFoundException.getResponseBody()).isNotNull();
    assertThat(notFoundException.getUrl()).isEqualTo(url);
    assertThat(notFoundException.getReason())
        .isEqualTo("Response.error()"); // set by Response.error
    Map<String, Object> errorResponseBody = notFoundException.getResponseBody();
    assertThat(errorResponseBody.get("name")).isEqualTo("test");
    assertThat(notFoundException.getResponseCode()).isEqualTo(HttpStatus.NOT_FOUND.value());
    assertThat(notFoundException)
        .hasMessageContaining(String.valueOf(HttpStatus.NOT_FOUND.value()));
    assertThat(notFoundException.getHttpMethod()).isEqualTo(HttpMethod.GET.toString());
  }

  @Test
  void testSpinnakerHttpException_NewInstance() {
    String url = "http://localhost";
    String reason = "reason";
    Response response = new Response(url, 200, reason, List.of(), null);
    try {
      RetrofitError error = RetrofitError.httpError(url, response, null, null);
      throw new SpinnakerHttpException(error);
    } catch (SpinnakerException e) {
      SpinnakerException newException = e.newInstance(CUSTOM_MESSAGE);

      assertThat(newException).isInstanceOf(SpinnakerHttpException.class);
      assertThat(newException).hasMessage(CUSTOM_MESSAGE);
      assertThat(newException).hasCause(e);
      assertThat(((SpinnakerHttpException) newException).getResponseCode())
          .isEqualTo(response.getStatus());
      SpinnakerHttpException spinnakerHttpException = (SpinnakerHttpException) newException;
      assertThat(spinnakerHttpException.getUrl()).isEqualTo(url);
      assertThat(spinnakerHttpException.getReason()).isEqualTo(reason);
    }
  }

  @Test
  void testNullResponse() {
    RetrofitError retrofitError =
        RetrofitError.networkError("http://some-url", new IOException("arbitrary exception"));
    assertThat(retrofitError.getResponse()).isNull();

    Throwable thrown = catchThrowable(() -> new SpinnakerHttpException(retrofitError));

    assertThat(thrown).isInstanceOf(NullPointerException.class);
    assertThat(thrown.getMessage()).isNotNull();
  }

  @Test
  void testNonJsonErrorResponse() {
    String url = "http://localhost";
    int statusCode = 500;
    String reason = "reason";
    String body = "non-json response";
    Response response = new Response(url, statusCode, reason, List.of(), new TypedString(body));
    RetrofitError retrofitError =
        RetrofitError.httpError(url, response, new GsonConverter(new Gson()), String.class);
    SpinnakerHttpException spinnakerHttpException = new SpinnakerHttpException(retrofitError);
    assertThat(spinnakerHttpException.getResponseBody()).isNull();
    assertThat(spinnakerHttpException.getResponseCode()).isEqualTo(statusCode);
    assertThat(spinnakerHttpException.getMessage())
        .isEqualTo("Status: " + statusCode + ", URL: " + url + ", Message: " + reason);
    assertThat(spinnakerHttpException.getUrl()).isEqualTo(url);
    assertThat(spinnakerHttpException.getReason()).isEqualTo(reason);
  }
}
