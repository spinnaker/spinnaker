/*
 * Copyright 2025 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.security.sdkclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.google.common.util.concurrent.RateLimiter;
import com.netflix.spectator.api.Counter;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;

/** Unit tests for {@link RateLimitingExecutionInterceptor}. */
class RateLimitingExecutionInterceptorTest {

  @Test
  void beforeExecution_acquiresPermitAndRecordsDelay() {
    // Arrange
    RateLimiter rateLimiter = mock(RateLimiter.class);
    Counter counter = mock(Counter.class);
    when(rateLimiter.acquire()).thenReturn(0.150); // 150ms delay

    RateLimitingExecutionInterceptor interceptor =
        new RateLimitingExecutionInterceptor(counter, rateLimiter);

    Context.BeforeExecution context = mock(Context.BeforeExecution.class);

    // Act
    interceptor.beforeExecution(context, new ExecutionAttributes());

    // Assert
    verify(rateLimiter).acquire();
    verify(counter).increment(150L); // 0.150s * 1000 = 150ms
  }

  @Test
  void beforeExecution_zeroDelay_incrementsZero() {
    RateLimiter rateLimiter = mock(RateLimiter.class);
    Counter counter = mock(Counter.class);
    when(rateLimiter.acquire()).thenReturn(0.0); // no delay

    RateLimitingExecutionInterceptor interceptor =
        new RateLimitingExecutionInterceptor(counter, rateLimiter);

    Context.BeforeExecution context = mock(Context.BeforeExecution.class);

    interceptor.beforeExecution(context, new ExecutionAttributes());

    verify(rateLimiter).acquire();
    verify(counter).increment(0L);
  }

  @Test
  void equality_basedOnRateLimiter() {
    RateLimiter limiter1 = RateLimiter.create(10.0);
    RateLimiter limiter2 = RateLimiter.create(10.0);
    Counter counter = mock(Counter.class);

    RateLimitingExecutionInterceptor a = new RateLimitingExecutionInterceptor(counter, limiter1);
    RateLimitingExecutionInterceptor b = new RateLimitingExecutionInterceptor(counter, limiter1);
    RateLimitingExecutionInterceptor c = new RateLimitingExecutionInterceptor(counter, limiter2);

    assertThat(a).isEqualTo(b);
    assertThat(a).isNotEqualTo(c);
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
  }
}
