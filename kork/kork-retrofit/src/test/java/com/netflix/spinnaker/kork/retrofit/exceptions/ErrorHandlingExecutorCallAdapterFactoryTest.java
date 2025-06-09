/*
 * Copyright 2025 Apple, Inc.
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

import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import retrofit2.http.GET;

public class ErrorHandlingExecutorCallAdapterFactoryTest {
  public interface TestService {
    @GET("legacy1")
    Map<String, Object> legacy1();

    @GET("legacy2")
    Map legacy2();

    @GET("modern1")
    Call<Map<String, Object>> modern1();
  }

  private static final MockWebServer mockWebServer = new MockWebServer();
  private static final String baseUrl = mockWebServer.url("/").toString();

  private static TestService testService;

  @BeforeAll
  public static void setupAll() {
    testService =
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
            .create(TestService.class);
  }

  @Test
  public void testLegacySignature() {
    mockWebServer.enqueue(new MockResponse().setBody("{\"foo\": \"bar\"}"));
    var ret = testService.legacy1();
    assertEquals("bar", ret.get("foo"));

    mockWebServer.enqueue(new MockResponse().setBody("{\"foo\": \"bar\"}"));
    var ret2 = testService.legacy2();
    assertEquals("bar", ret2.get("foo"));
  }

  @Test
  public void testModernSignature() {
    mockWebServer.enqueue(new MockResponse().setBody("{\"foo\": \"bar\"}"));
    var ret = Retrofit2SyncCall.execute(testService.modern1());
    assertEquals("bar", ret.get("foo"));
  }
}
