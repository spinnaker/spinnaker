/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.security.sdkclient;

import static java.util.Objects.requireNonNull;

import com.amazonaws.Request;
import com.amazonaws.handlers.RequestHandler2;
import com.google.common.util.concurrent.RateLimiter;
import com.netflix.spectator.api.Counter;
import java.util.Objects;

/** A RequestHandler that will throttle requests via the supplied RateLimiter. */
public class RateLimitingRequestHandler extends RequestHandler2 {
  private final Counter counter;
  private final RateLimiter rateLimiter;

  public RateLimitingRequestHandler(Counter counter, RateLimiter rateLimiter) {
    this.counter = requireNonNull(counter);
    this.rateLimiter = requireNonNull(rateLimiter);
  }

  @Override
  public void beforeRequest(Request<?> request) {
    double rateLimitedSeconds = rateLimiter.acquire();
    long rateLimitedMillis = Double.valueOf(rateLimitedSeconds * 1000).longValue();
    counter.increment(rateLimitedMillis);
    super.beforeRequest(request);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    RateLimitingRequestHandler that = (RateLimitingRequestHandler) o;
    return Objects.equals(rateLimiter, that.rateLimiter);
  }

  @Override
  public int hashCode() {
    return Objects.hash(rateLimiter);
  }
}
