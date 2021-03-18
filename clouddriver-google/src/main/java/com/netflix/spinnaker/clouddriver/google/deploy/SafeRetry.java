/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.deploy;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.google.deploy.exception.GoogleOperationException;
import com.netflix.spinnaker.clouddriver.googlecommon.deploy.GoogleApiException;
import com.netflix.spinnaker.clouddriver.googlecommon.deploy.GoogleApiException.ResourceInUseException;
import com.netflix.spinnaker.clouddriver.googlecommon.deploy.GoogleCommonSafeRetry;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import groovy.lang.Closure;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNullableByDefault;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@NonnullByDefault
@Slf4j
public class SafeRetry {
  private final GoogleCommonSafeRetry googleCommonSafeRetry;

  @Autowired
  @ParametersAreNullableByDefault
  public SafeRetry(
      @Value("${google.safe-retry-max-wait-interval-ms:60000}") Integer maxWaitInterval,
      @Value("${google.safe-retry-retry-interval-base-sec:2}") Integer retryIntervalBase,
      @Value("${google.safe-retry-jitter-multiplier:1000}") Integer jitterMultiplier,
      @Value("${google.safe-retry-max-retries:10}") Integer maxRetries) {
    googleCommonSafeRetry =
        new GoogleCommonSafeRetry(maxWaitInterval, retryIntervalBase, jitterMultiplier, maxRetries);
  }

  private SafeRetry(GoogleCommonSafeRetry googleCommonSafeRetry) {
    this.googleCommonSafeRetry = googleCommonSafeRetry;
  }

  /**
   * Returns an instance of this class that never waits between retries, suitable for testing.
   *
   * @return An instance of {@link SafeRetry}
   */
  public static SafeRetry withoutDelay() {
    return new SafeRetry(GoogleCommonSafeRetry.withoutDelay());
  }

  @Nullable
  public <V> V doRetry(
      Closure<V> operation,
      String resource,
      @Nullable Task task,
      List<Integer> retryCodes,
      List<Integer> successCodes,
      Map<String, String> tags,
      Registry registry) {
    String action = tags.get("action");
    String description = String.format("%s of %s", action, resource);
    if (task != null) {
      task.updateStatus(tags.get("phase"), String.format("Attempting %s...", description));
    }

    try {
      return googleCommonSafeRetry.doRetry(
          operation, description, retryCodes, successCodes, tags, registry);
    } catch (ResourceInUseException e) {
      // Don't fail the operation if the resource is in use. The main use case for this is
      // resiliency in delete operations - we don't want to fail the operation if something is in
      // use by another resource.
      log.warn(e.getMessage());
      return null;
    } catch (GoogleApiException e) {
      throw new GoogleOperationException(
          String.format("Failed to " + description, action, resource), e);
    }
  }
}
