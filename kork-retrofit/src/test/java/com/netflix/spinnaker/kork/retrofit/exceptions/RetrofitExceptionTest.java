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

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

public class RetrofitExceptionTest {

  private static Retrofit retrofit2Service;

  private final String validJsonResponseBodyString = "{\"name\":\"test\"}";

  @BeforeAll
  public static void setupOnce() {
    retrofit2Service =
        new Retrofit.Builder()
            .baseUrl("http://localhost:8987/")
            .addConverterFactory(JacksonConverterFactory.create())
            .build();
  }

  @Test
  public void testRetrofitExceptionRequiresErrorBody() {
    ResponseBody responseBody =
        ResponseBody.create(
            MediaType.parse("application/json" + "; charset=utf-8"), validJsonResponseBodyString);

    // We get a null error body for 200-OK successful response,
    // which we can use to check a RetrofitException instance creation.
    Response response = Response.success(HttpStatus.OK.value(), responseBody);
    assertNull(response.errorBody());

    assertThrows(
        NullPointerException.class, () -> RetrofitException.httpError(response, retrofit2Service));
  }

  @Test
  public void testUnexpectedErrorHasNoResponseErrorBody() {
    Throwable cause = new Throwable("custom message");
    RetrofitException retrofitException = RetrofitException.unexpectedError(cause);
    assertNull(retrofitException.getErrorBodyAs(HashMap.class));
  }
}
