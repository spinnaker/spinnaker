/*
 * Copyright 2025 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.security.sdkclient;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.RateLimiter;
import com.netflix.spectator.api.Counter;
import java.util.Objects;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;

/**
 * An AWS SDK v2 {@link ExecutionInterceptor} that throttles requests via a Guava {@link
 * RateLimiter}. This is the v2 equivalent of {@link RateLimitingRequestHandler}.
 *
 * <p>The interceptor acquires a permit before each API call and records the wait time (in
 * milliseconds) in a Spectator counter.
 */
public class RateLimitingExecutionInterceptor implements ExecutionInterceptor {

  private final Counter counter;
  private final RateLimiter rateLimiter;

  public RateLimitingExecutionInterceptor(Counter counter, RateLimiter rateLimiter) {
    this.counter = requireNonNull(counter, "counter");
    this.rateLimiter = requireNonNull(rateLimiter, "rateLimiter");
  }

  @Override
  public void beforeExecution(
      Context.BeforeExecution context, ExecutionAttributes executionAttributes) {
    double rateLimitedSeconds = rateLimiter.acquire();
    long rateLimitedMillis = Double.valueOf(rateLimitedSeconds * 1000).longValue();
    counter.increment(rateLimitedMillis);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    RateLimitingExecutionInterceptor that = (RateLimitingExecutionInterceptor) o;
    return Objects.equals(rateLimiter, that.rateLimiter);
  }

  @Override
  public int hashCode() {
    return Objects.hash(rateLimiter);
  }
}
