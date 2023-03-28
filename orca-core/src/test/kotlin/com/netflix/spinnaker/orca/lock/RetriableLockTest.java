/*
 * Copyright 2023 Armory, Inc.
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

package com.netflix.spinnaker.orca.lock;

import static org.mockito.Mockito.*;

import com.netflix.spinnaker.kork.core.RetrySupport;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

class RetriableLockTest {

  private static final String LOCK_NAME = UUID.randomUUID().toString();
  private RunOnLockAcquired runOnLockAcquired;
  private RetriableLock retriableLock;

  @BeforeEach
  void setup() {
    this.runOnLockAcquired = mock(RunOnLockAcquired.class);
    this.retriableLock = new RetriableLock(runOnLockAcquired, new RetrySupport());
  }

  @Test
  @DisplayName("Should attempt to acquire lock as long as max retries is not exceeded")
  public void test1() {
    givenLockCannotBeAcquiredOnAnyAttempt();

    var options =
        new RetriableLock.RetriableLockOptions(LOCK_NAME, 3, Duration.ofMillis(500), false, true);

    Assertions.assertThrows(
        RetriableLock.FailedToGetLockException.class, () -> retriableLock.lock(options, () -> {}));

    assertLockAcquireAttempts(options.getMaxRetries());
  }

  @Test
  @DisplayName("Should attempt to acquire lock only once, when the lock is available")
  void test2() {
    givenLockIsAcquired();

    var options = new RetriableLock.RetriableLockOptions(LOCK_NAME);

    retriableLock.lock(options, () -> {});

    assertLockAcquireAttempts(1);
  }

  void givenLockCannotBeAcquiredOnAnyAttempt() {
    when(runOnLockAcquired.execute(ArgumentMatchers.any(Runnable.class), ArgumentMatchers.any()))
        .thenReturn(lockResult(false, false));
  }

  void givenLockIsAcquired() {
    when(runOnLockAcquired.execute(any(Runnable.class), ArgumentMatchers.any()))
        .thenReturn(lockResult(true, true));
  }

  void assertLockAcquireAttempts(int times) {
    verify(runOnLockAcquired, times(times))
        .execute(ArgumentMatchers.any(Runnable.class), ArgumentMatchers.any());
  }

  RunOnLockResult lockResult(boolean lockAcquired, boolean actionExecuted) {
    return new RunOnLockResult(lockAcquired, actionExecuted, null, null);
  }
}
