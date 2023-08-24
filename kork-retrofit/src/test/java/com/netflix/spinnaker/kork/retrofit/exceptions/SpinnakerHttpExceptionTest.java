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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.netflix.spinnaker.kork.exceptions.SpinnakerException;
import java.util.List;
import java.util.Map;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import retrofit.RetrofitError;
import retrofit.client.Response;
import retrofit.converter.GsonConverter;
import retrofit.mime.TypedString;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

public class SpinnakerHttpExceptionTest {
  private static final String CUSTOM_MESSAGE = "custom message";

  public void testSpinnakerHttpExceptionFromRetrofitError() {
    String url = "http://localhost";
    int statusCode = 200;
    String message = "arbitrary message";
    Response response =
        new Response(
            url,
            statusCode,
            "reason",
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
  }

  @Test
  public void testSpinnakerHttpExceptionFromRetrofitException() {
    final String validJsonResponseBodyString = "{\"name\":\"test\"}";
    ResponseBody responseBody =
        ResponseBody.create(
            MediaType.parse("application/json" + "; charset=utf-8"), validJsonResponseBodyString);
    retrofit2.Response response =
        retrofit2.Response.error(HttpStatus.NOT_FOUND.value(), responseBody);

    Retrofit retrofit2Service =
        new Retrofit.Builder()
            .baseUrl("http://localhost")
            .addConverterFactory(JacksonConverterFactory.create())
            .build();
    SpinnakerHttpException notFoundException =
        new SpinnakerHttpException(response, retrofit2Service);
    assertNotNull(notFoundException.getResponseBody());
    Map<String, Object> errorResponseBody = notFoundException.getResponseBody();
    assertEquals(errorResponseBody.get("name"), "test");
    assertEquals(HttpStatus.NOT_FOUND.value(), notFoundException.getResponseCode());
    assertTrue(
        notFoundException.getMessage().contains(String.valueOf(HttpStatus.NOT_FOUND.value())));
  }

  @Test
  public void testSpinnakerHttpException_NewInstance() {
    Response response = new Response("http://localhost", 200, "reason", List.of(), null);
    try {
      RetrofitError error = RetrofitError.httpError("http://localhost", response, null, null);
      throw new SpinnakerHttpException(error);
    } catch (SpinnakerException e) {
      SpinnakerException newException = e.newInstance(CUSTOM_MESSAGE);

      assertTrue(newException instanceof SpinnakerHttpException);
      assertEquals(CUSTOM_MESSAGE, newException.getMessage());
      assertEquals(e, newException.getCause());
      assertEquals(response.getStatus(), ((SpinnakerHttpException) newException).getResponseCode());
    }
  }
}
