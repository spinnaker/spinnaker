/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.kork.core;

import com.netflix.spinnaker.kork.exceptions.SpinnakerException;
import java.time.Duration;
import java.util.function.Supplier;

public class RetrySupport {
  /**
   * @deprecated replaced by {@link #retry(Supplier, int, Duration, boolean)}
   */
  @Deprecated
  public <T> T retry(Supplier<T> fn, int maxRetries, long retryBackoffMillis, boolean exponential) {
    return retry(fn, maxRetries, Duration.ofMillis(retryBackoffMillis), exponential);
  }

  public <T> T retry(Supplier<T> fn, int maxRetries, Duration retryBackoff, boolean exponential) {
    int retries = 0;
    while (true) {
      try {
        return fn.get();
      } catch (Exception e) {
        if (e instanceof SpinnakerException) {
          Boolean retryable = ((SpinnakerException) e).getRetryable();
          if (retryable != null && !retryable) {
            throw e;
          }
        }
        if (retries >= (maxRetries - 1)) {
          throw e;
        }

        long timeout =
            !exponential
                ? retryBackoff.toMillis()
                : (long) Math.pow(2, retries) * retryBackoff.toMillis();
        sleep(timeout);

        retries++;
      }
    }
  }

  /** Overridable by test cases to avoid Thread.sleep() */
  void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException ignored) {
    }
  }
}
