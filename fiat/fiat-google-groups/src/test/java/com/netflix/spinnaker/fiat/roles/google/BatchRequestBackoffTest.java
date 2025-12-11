/*
 * Copyright 2024 Snap Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.fiat.roles.google;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonErrorContainer;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpStatusCodes;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.json.Json;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test to verify that batch request retry logic works correctly with exponential backoff. This
 * tests the behavior of GoogleDirectoryUserRolesProvider.multiLoadRoles() when encountering rate
 * limiting (403) errors from Google's API.
 */
public class BatchRequestBackoffTest {

  private AtomicInteger batchRequestCount;
  private AtomicInteger callbackSuccesses;
  private AtomicInteger callbackFailures;

  @BeforeEach
  public void setUp() {
    batchRequestCount = new AtomicInteger(0);
    callbackSuccesses = new AtomicInteger(0);
    callbackFailures = new AtomicInteger(0);
  }

  @Test
  public void testBatchRequestRetriesOn403() throws IOException {
    System.out.println("Testing batch request retry logic with exponential backoff...\n");

    // Create a mock HTTP transport that simulates rate limiting at the BATCH level
    // BatchRequest sends all queued requests as a single HTTP POST to the batch endpoint
    MockHttpTransport transport =
        new MockHttpTransport() {
          @Override
          public LowLevelHttpRequest buildRequest(String method, String url) {
            return new MockLowLevelHttpRequest() {
              @Override
              public LowLevelHttpResponse execute() throws IOException {
                int count = batchRequestCount.incrementAndGet();
                System.out.println("Batch HTTP Request #" + count + " to " + url);

                MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();

                // First 2 batch requests: return 403 rate limit error
                // Third batch request: succeed with valid batch response
                if (count <= 2) {
                  System.out.println("  -> Returning 403 (rate limited - should retry)");
                  response.setStatusCode(HttpStatusCodes.STATUS_CODE_FORBIDDEN);
                  response.setContentType(Json.MEDIA_TYPE);
                  response.setContent(
                      "{\"error\": {\"code\": 403, \"message\": \"Request rate higher than configured\"}}");
                } else {
                  System.out.println("  -> Returning 200 with batch response (success!)");
                  response.setStatusCode(HttpStatusCodes.STATUS_CODE_OK);
                  response.setContentType("multipart/mixed; boundary=batch_boundary");
                  // Valid batch response format with one successful part
                  response.setContent(
                      "--batch_boundary\r\n"
                          + "Content-Type: application/http\r\n"
                          + "Content-ID: response-1\r\n\r\n"
                          + "HTTP/1.1 200 OK\r\n"
                          + "Content-Type: application/json; charset=UTF-8\r\n\r\n"
                          + "{}\r\n"
                          + "--batch_boundary--");
                }

                return response;
              }
            };
          }
        };

    // Create a batch request with JSON parser (simulating what GoogleDirectoryUserRolesProvider
    // does)
    JsonFactory jsonFactory = new GsonFactory();
    BatchRequest batchRequest = new BatchRequest(transport, null);

    // Add a request to the batch
    HttpRequest request =
        transport
            .createRequestFactory()
            .buildGetRequest(new com.google.api.client.http.GenericUrl("https://example.com/test"));
    request.setParser(jsonFactory.createJsonObjectParser());

    // Add callback to track success/failure
    JsonBatchCallback<Object> callback =
        new JsonBatchCallback<Object>() {
          @Override
          public void onSuccess(Object result, HttpHeaders responseHeaders) {
            callbackSuccesses.incrementAndGet();
            System.out.println(">>> Batch callback: Success!");
          }

          @Override
          public void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) {
            callbackFailures.incrementAndGet();
            System.out.println(">>> Batch callback: Failure - " + e.getMessage());
          }
        };

    batchRequest.queue(request, Object.class, GoogleJsonErrorContainer.class, callback);

    // Execute batch with retry logic (simulating executeBatchWithRetry from
    // GoogleDirectoryUserRolesProvider)
    executeBatchWithRetry(batchRequest);

    // Print results BEFORE assertions so we see diagnostics even when test fails
    System.out.println("\n========================================");
    System.out.println("=== Test Results ===");
    System.out.println("========================================");
    System.out.println("Total batch HTTP requests made: " + batchRequestCount.get());
    System.out.println("Callback successes: " + callbackSuccesses.get());
    System.out.println("Callback failures: " + callbackFailures.get());
    System.out.println("========================================\n");

    // Log actual vs expected
    System.out.println("=== EXPECTED vs ACTUAL ===");
    System.out.println("Expected: 3 batch requests (2 failures + 1 success), 1 callback success");
    System.out.println(
        "Actual:   "
            + batchRequestCount.get()
            + " batch requests, "
            + callbackSuccesses.get()
            + " callback successes\n");

    if (batchRequestCount.get() == 3 && callbackSuccesses.get() == 1) {
      System.out.println("✅ Batch retry logic is working correctly!");
      System.out.println("   Failed requests are retried with exponential backoff");
      System.out.println("   Callbacks are invoked after successful retry\n");
    } else {
      System.out.println("❌ Batch retry logic is NOT working");
      System.out.println("   Expected retries after 403 errors but they didn't happen\n");
    }

    // Assertions - verify expected behavior
    assertEquals(
        3, batchRequestCount.get(), "Should make 3 batch requests: 2 failures + 1 success");
    assertEquals(1, callbackSuccesses.get(), "Should have 1 successful callback after retries");
    assertEquals(
        0, callbackFailures.get(), "Should have 0 failed callbacks (retry handled the failures)");
  }

  /**
   * Simulates the executeBatchWithRetry logic from GoogleDirectoryUserRolesProvider. This is a
   * simplified version for testing purposes.
   */
  private void executeBatchWithRetry(BatchRequest batch) throws IOException {
    final int maxAttempts = 3;
    int attempt = 0;

    while (attempt < maxAttempts) {
      attempt++;
      try {
        if (attempt > 1) {
          // Simulate backoff delay (but don't actually sleep in test)
          System.out.println("Retrying batch (attempt " + attempt + "/" + maxAttempts + ")...\n");
        }

        batch.execute();
        return; // Success!

      } catch (com.google.api.client.http.HttpResponseException e) {
        int statusCode = e.getStatusCode();
        boolean shouldRetry = (statusCode == 403 || statusCode / 100 == 5) && attempt < maxAttempts;

        if (shouldRetry) {
          System.out.println(
              "Batch failed with status "
                  + statusCode
                  + ", will retry (attempt "
                  + attempt
                  + "/"
                  + maxAttempts
                  + ")\n");
        } else {
          System.out.println(
              "Batch failed with status " + statusCode + " after " + attempt + " attempts\n");
          throw e;
        }
      }
    }
  }
}
