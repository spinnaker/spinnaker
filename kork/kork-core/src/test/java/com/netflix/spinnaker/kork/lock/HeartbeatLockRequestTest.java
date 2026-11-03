/*
 * Copyright 2026 spinnaker.io.
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
package com.netflix.spinnaker.kork.lock;

import static org.junit.jupiter.api.Assertions.*;

import com.netflix.spinnaker.kork.lock.LockManager.Lock;
import com.netflix.spinnaker.kork.lock.LockManager.LockOptions;
import com.netflix.spinnaker.kork.lock.RefreshableLockManager.HeartbeatLockRequest;
import com.netflix.spinnaker.kork.test.time.MutableClock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class HeartbeatLockRequestTest {
  @Test
  void boundedRequestsStillExpireAtTheirConfiguredDuration() {
    MutableClock clock = new MutableClock();
    HeartbeatLockRequest request =
        new HeartbeatLockRequest(lock(), new AtomicInteger(3), clock, Duration.ofSeconds(1), false);

    clock.incrementBy(Duration.ofMillis(999));
    assertFalse(request.timesUp());
    clock.incrementBy(Duration.ofMillis(1));
    assertTrue(request.timesUp());
  }

  @Test
  void callbackScopedRequestsDoNotExhaustAnArbitraryHeartbeatBudget() {
    MutableClock clock = new MutableClock();
    LockOptions options = new LockOptions().withHeartbeatsUntilReleased();
    HeartbeatLockRequest request =
        new HeartbeatLockRequest(
            lock(),
            new AtomicInteger(3),
            clock,
            options.getMaximumLockDuration(),
            false,
            options.isHeartbeatsUntilReleased());

    clock.incrementBy(Duration.ofDays(1));

    assertFalse(request.timesUp());
  }

  @Test
  void selectingABoundedDurationRestoresBoundedBehavior() {
    LockOptions options =
        new LockOptions()
            .withHeartbeatsUntilReleased()
            .withMaximumLockDuration(Duration.ofSeconds(2));
    MutableClock clock = new MutableClock();
    HeartbeatLockRequest request =
        new HeartbeatLockRequest(
            lock(),
            new AtomicInteger(3),
            clock,
            options.getMaximumLockDuration(),
            false,
            options.isHeartbeatsUntilReleased());

    clock.incrementBy(Duration.ofSeconds(2));

    assertTrue(request.timesUp());
  }

  private Lock lock() {
    return new Lock("stage", "owner", 1L, 10000L, 0L, 0L, 0L, null);
  }
}
