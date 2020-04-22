/*
 * Copyright 2020 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.appengine.deploy;

import com.google.common.collect.ImmutableList;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.appengine.deploy.exception.AppengineOperationException;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.googlecommon.deploy.GoogleApiException;
import com.netflix.spinnaker.clouddriver.googlecommon.deploy.GoogleCommonSafeRetry;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import groovy.lang.Closure;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNullableByDefault;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@NonnullByDefault
public final class AppengineSafeRetry {
  private final GoogleCommonSafeRetry googleCommonSafeRetry;

  @Autowired
  @ParametersAreNullableByDefault
  public AppengineSafeRetry(
      @Value("${appengine.safe-retry-max-wait-interval-ms:60000}") Integer maxWaitInterval,
      @Value("${appengine.safe-retry-retry-interval-base-sec:2}") Integer retryIntervalBase,
      @Value("${appengine.safe-retry-jitter-multiplier:1000}") Integer jitterMultiplier,
      @Value("${appengine.safe-retry-max-retries:10}") Integer maxRetries) {
    googleCommonSafeRetry =
        new GoogleCommonSafeRetry(maxWaitInterval, retryIntervalBase, jitterMultiplier, maxRetries);
  }

  private AppengineSafeRetry(GoogleCommonSafeRetry googleCommonSafeRetry) {
    this.googleCommonSafeRetry = googleCommonSafeRetry;
  }

  /**
   * Returns an instance of this class that never waits between retries, suitable for testing.
   *
   * @return An instance of {@link AppengineSafeRetry}
   */
  public static AppengineSafeRetry withoutDelay() {
    return new AppengineSafeRetry(GoogleCommonSafeRetry.withoutDelay());
  }

  @Nullable
  public <V> V doRetry(
      Closure<V> operation,
      String resource,
      @Nullable Task task,
      List<Integer> retryCodes,
      Map<String, String> tags,
      Registry registry) {
    String action = tags.get("action");
    String description = String.format("%s of %s", action, resource);
    if (task != null) {
      task.updateStatus(tags.get("phase"), String.format("Attempting %s...", description));
    }

    try {
      return googleCommonSafeRetry.doRetry(
          operation, description, retryCodes, ImmutableList.of(), tags, registry);
    } catch (GoogleApiException e) {
      throw new AppengineOperationException("Failed to " + description, e);
    }
  }
}
