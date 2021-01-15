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

import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import retrofit.RestAdapter;
import retrofit.client.Response;
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
  public void testNotFoundIsNotRetryable() throws Exception {
    mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.NOT_FOUND.value()));
    NotFoundException notFoundException =
        assertThrows(NotFoundException.class, () -> retrofitService.getFoo());
    assertNotNull(notFoundException.getRetryable());
    assertFalse(notFoundException.getRetryable());
  }

  @Test
  public void testBadRequestIsNotRetryable() throws Exception {
    mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.BAD_REQUEST.value()));
    SpinnakerHttpException spinnakerHttpException =
        assertThrows(SpinnakerHttpException.class, () -> retrofitService.getFoo());
    assertNotNull(spinnakerHttpException.getRetryable());
    assertFalse(spinnakerHttpException.getRetryable());
  }

  @Test
  public void testOtherClientErrorHasNullRetryable() throws Exception {
    // Arbitrarily choose GONE as an example of a client (e.g. 4xx) error that
    // we expect to have null retryable
    mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.GONE.value()));
    SpinnakerHttpException spinnakerHttpException =
        assertThrows(SpinnakerHttpException.class, () -> retrofitService.getFoo());
    assertNull(spinnakerHttpException.getRetryable());
  }

  interface RetrofitService {
    @GET("/foo")
    Response getFoo();
  }
}
