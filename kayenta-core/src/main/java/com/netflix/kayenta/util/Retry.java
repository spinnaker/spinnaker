/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.kayenta.util;

import java.util.function.Supplier;

public class Retry {
  public void retry(Runnable fn, int maxRetries, long retryBackoffMillis) {
    retry(fn, maxRetries, retryBackoffMillis, false);
  }

  public void exponential(Runnable fn, int maxRetries, long retryBackoffMillis) {
    retry(fn, maxRetries, retryBackoffMillis, true);
  }

  public <T> T retry(Supplier<T> fn, int maxRetries, long retryBackoffMillis) {
    return retry(fn, maxRetries, retryBackoffMillis, false);
  }

  public <T> T exponential(Supplier<T> fn, int maxRetries, long retryBackoffMillis) {
    return retry(fn, maxRetries, retryBackoffMillis, true);
  }

  public void retry(Runnable fn, int maxRetries, long retryBackoffMillis, boolean exponential) {
    int retries = 0;
    while (true) {
      try {
        fn.run();
        return;
      } catch (Exception e) {
        if (retries >= (maxRetries - 1)) {
          throw e;
        }

        long timeout = !exponential ? retryBackoffMillis : (long) Math.pow(2, retries) * retryBackoffMillis;
        sleep(timeout);

        retries++;
      }
    }
  }

  private <T> T retry(Supplier<T> fn, int maxRetries, long retryBackoffMillis, boolean exponential) {
    int retries = 0;
    while (true) {
      try {
        return fn.get();
      } catch (Exception e) {
        if (retries >= (maxRetries - 1)) {
          throw e;
        }

        long timeout = !exponential ? retryBackoffMillis : (long) Math.pow(2, retries) * retryBackoffMillis;
        sleep(timeout);

        retries++;
      }
    }
  }

  /**
   * Overridable by test cases to avoid Thread.sleep()
   */
  void sleep(long duration) {
    try {
      Thread.sleep(duration);
    } catch (InterruptedException ignored) {
    }
  }
}
