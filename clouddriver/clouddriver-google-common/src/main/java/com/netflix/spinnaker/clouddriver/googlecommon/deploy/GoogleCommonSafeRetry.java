/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.googlecommon.deploy;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.common.collect.ImmutableMap;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNullableByDefault;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@NonnullByDefault
@Slf4j
public final class GoogleCommonSafeRetry {
  private final long maxWaitInterval;
  private final long retryIntervalBase;
  private final long jitterMultiplier;
  private final long maxRetries;

  @Builder
  @ParametersAreNullableByDefault
  public GoogleCommonSafeRetry(
      Integer maxWaitInterval,
      Integer retryIntervalBase,
      Integer jitterMultiplier,
      Integer maxRetries) {
    this.maxWaitInterval = Optional.ofNullable(maxWaitInterval).orElse(60000);
    this.retryIntervalBase = Optional.ofNullable(retryIntervalBase).orElse(2);
    this.jitterMultiplier = Optional.ofNullable(jitterMultiplier).orElse(1000);
    this.maxRetries = Optional.ofNullable(maxRetries).orElse(10);
  }

  /**
   * Returns an instance of this class that never waits between retries, suitable for testing.
   *
   * @return An instance of {@link GoogleCommonSafeRetry}
   */
  public static GoogleCommonSafeRetry withoutDelay() {
    return GoogleCommonSafeRetry.builder().retryIntervalBase(0).jitterMultiplier(0).build();
  }

  /**
   * Retry an operation if it fails. Treat any error codes in successCodes as success.
   *
   * @param operation - The operation.
   * @param description - Description of the operation, used for logging.
   * @param retryCodes - GoogleJsonResponseException codes we retry on.
   * @param successCodes - GoogleJsonException codes we treat as success.
   * @return Object returned from the operation.
   */
  @Nullable
  public <V> V doRetry(
      Callable<V> operation,
      String description,
      List<Integer> retryCodes,
      List<Integer> successCodes,
      Map<String, String> tags,
      Registry registry)
      throws GoogleApiException {
    boolean success = false;
    long startTime = registry.clock().monotonicTime();
    try {
      V result = performOperation(operation, description, retryCodes, successCodes);
      success = true;
      return result;
    } catch (GoogleJsonResponseException e) {
      throw GoogleApiException.fromGoogleJsonException(e);
    } catch (SocketTimeoutException e) {
      throw new GoogleApiException("Operation failed. Last attempt timed out.");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new GoogleApiException("Operation failed. Thread was interrupted waiting to retry.");
    } catch (Exception e) {
      throw new IllegalStateException("Operation failed.", e);
    } finally {
      Map<String, String> metricTags =
          ImmutableMap.<String, String>builder()
              .putAll(tags)
              .put("success", Boolean.toString(success))
              .build();
      registry
          .timer(registry.createId("google.safeRetry", metricTags))
          .record(registry.clock().monotonicTime() - startTime, TimeUnit.NANOSECONDS);
    }
  }

  @Nullable
  private <V> V performOperation(
      Callable<V> operation,
      String description,
      List<Integer> retryCodes,
      List<Integer> successfulErrorCodes)
      throws Exception {
    long maxAttempts = Math.max(1, maxRetries);
    int tries = 1;
    // This logic runs maxAttempts - 1 times, as we don't catch exceptions on the last try
    while (tries < maxAttempts) {
      try {
        return attemptOperation(operation, successfulErrorCodes);
      } catch (GoogleJsonResponseException jsonException) {
        if (!retryCodes.contains(jsonException.getStatusCode())) {
          throw jsonException;
        }
        log.warn(
            "{} attempt #{} encountered retryable statusCode={} with error message: {}.",
            description,
            tries,
            jsonException.getStatusCode(),
            jsonException.getMessage());
      } catch (SocketTimeoutException toEx) {
        log.warn("Retryable {} attempt #{} timed out.", description, tries);
      }
      tries++;
      // Sleep with exponential backoff based on the number of retries. Add retry jitter with
      // Math.random() to prevent clients syncing up and bursting at regular intervals. Don't wait
      // longer than a minute.
      long thisIntervalWait =
          TimeUnit.SECONDS.toMillis((long) Math.pow(retryIntervalBase, tries - 1));
      long sleepMillis =
          Math.min(thisIntervalWait, maxWaitInterval)
              + Math.round(Math.random() * jitterMultiplier);
      log.warn("Waiting {} ms to retry {}.", sleepMillis, description);
      Thread.sleep(sleepMillis);
      log.warn("Retrying {} attempt #{}...", description, tries);
    }
    // Don't catch any exceptions on the last attempt
    return attemptOperation(operation, successfulErrorCodes);
  }

  @Nullable
  private static <V> V attemptOperation(Callable<V> operation, List<Integer> successCodes)
      throws Exception {
    try {
      return operation.call();
    } catch (GoogleJsonResponseException e) {
      if (successCodes.contains(e.getStatusCode())) {
        return null;
      }
      throw e;
    }
  }
}
