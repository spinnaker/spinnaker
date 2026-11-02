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
package com.netflix.spinnaker.orca.lock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.lock.LockManager;
import com.netflix.spinnaker.kork.lock.LockManager.AcquireLockResponse;
import com.netflix.spinnaker.kork.lock.LockManager.LockCallbackException;
import com.netflix.spinnaker.kork.lock.LockManager.LockCleanupException;
import com.netflix.spinnaker.kork.lock.LockManager.LockOptions;
import com.netflix.spinnaker.kork.lock.LockManager.LockStatus;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.Test;

class RetriableLockFailureTest {
  @Test
  void redisCallbackFailureEscapesAcquisitionRetries() {
    LockManager manager = mock(LockManager.class);
    when(manager.acquireLock(any(LockOptions.class), any(Runnable.class)))
        .thenAnswer(
            invocation -> {
              invocation.getArgument(1, Runnable.class).run();
              return new AcquireLockResponse<Void>(null, null, LockStatus.ACQUIRED, null, true);
            });

    assertCallbackFailureIsNotRetried(new RunOnRedisLockAcquired(manager));
    verify(manager).acquireLock(any(LockOptions.class), any(Runnable.class));
  }

  @Test
  void shedlockCallbackFailureEscapesAcquisitionRetries() {
    LockProvider provider = mock(LockProvider.class);
    AtomicInteger releases = new AtomicInteger();
    when(provider.lock(any())).thenReturn(Optional.of(releases::incrementAndGet));

    assertCallbackFailureIsNotRetried(new RunOnShedLockAcquired(provider));

    assertEquals(1, releases.get());
    verify(provider).lock(any());
  }

  @Test
  void shedlockAcquisitionExceptionsAreStillRetried() {
    LockProvider provider = mock(LockProvider.class);
    AtomicInteger releases = new AtomicInteger();
    when(provider.lock(any()))
        .thenThrow(new IllegalStateException("provider unavailable"))
        .thenReturn(Optional.of(releases::incrementAndGet));
    AtomicInteger executions = new AtomicInteger();

    assertTrue(
        lock(new RunOnShedLockAcquired(provider)).lock(options(), executions::incrementAndGet));

    assertEquals(1, executions.get());
    assertEquals(1, releases.get());
    verify(provider, times(2)).lock(any());
  }

  @Test
  void shedlockUnlockFailureDoesNotRepeatACompletedCallback() {
    LockProvider provider = mock(LockProvider.class);
    IllegalStateException failure = new IllegalStateException("unlock failed");
    when(provider.lock(any()))
        .thenReturn(
            Optional.of(
                () -> {
                  throw failure;
                }));
    AtomicInteger executions = new AtomicInteger();

    LockCleanupException exception =
        assertThrows(
            LockCleanupException.class,
            () ->
                lock(new RunOnShedLockAcquired(provider))
                    .lock(options(), executions::incrementAndGet));

    assertSame(failure, exception.getCause());
    assertEquals(Boolean.FALSE, exception.getRetryable());
    assertEquals(1, executions.get());
    verify(provider).lock(any());
  }

  @Test
  void adapterThrowAfterCallbackFailureDoesNotRetryTheCallback() {
    RunOnLockAcquired manager = mock(RunOnLockAcquired.class);
    IllegalStateException failure = new IllegalStateException("callback failed");
    when(manager.execute(any(Runnable.class), any()))
        .thenAnswer(
            invocation -> {
              invocation.getArgument(0, Runnable.class).run();
              return null;
            });
    AtomicInteger executions = new AtomicInteger();

    LockCallbackException exception =
        assertThrows(
            LockCallbackException.class,
            () ->
                lock(manager)
                    .lock(
                        options(),
                        () -> {
                          executions.incrementAndGet();
                          throw failure;
                        }));

    assertSame(failure, exception.getCause());
    assertEquals(Boolean.FALSE, exception.getRetryable());
    assertEquals(1, executions.get());
    verify(manager).execute(any(Runnable.class), any());
  }

  @Test
  void unprotectedCallbackFailureIsNotRetriedAsLockContention() {
    assertCallbackFailureIsNotRetried(new NoOpRunOnLockAcquired());
  }

  @Test
  void contentionIsRetriedWithoutRunningTheCallbackUntilAcquired() {
    LockManager manager = mock(LockManager.class);
    AtomicInteger attempts = new AtomicInteger();
    when(manager.acquireLock(any(LockOptions.class), any(Runnable.class)))
        .thenAnswer(
            invocation -> {
              if (attempts.incrementAndGet() < 3) {
                return new AcquireLockResponse<Void>(null, null, LockStatus.TAKEN, null, false);
              }
              invocation.getArgument(1, Runnable.class).run();
              return new AcquireLockResponse<Void>(null, null, LockStatus.ACQUIRED, null, true);
            });
    AtomicInteger executions = new AtomicInteger();

    assertTrue(
        lock(new RunOnRedisLockAcquired(manager)).lock(options(), executions::incrementAndGet));

    assertEquals(3, attempts.get());
    assertEquals(1, executions.get());
  }

  @Test
  void acquiredLockWithUnexecutedActionIsNotReportedAsSuccess() {
    RunOnLockAcquired manager = mock(RunOnLockAcquired.class);
    when(manager.execute(any(Runnable.class), any()))
        .thenReturn(new RunOnLockResult<>(true, false, null, null));

    assertThrows(LockCallbackException.class, () -> lock(manager).lock(options(), () -> {}));

    verify(manager).execute(any(Runnable.class), any());
  }

  @Test
  void ownershipLossAfterExecutionIsNotRetried() {
    LockManager manager = mock(LockManager.class);
    RuntimeException lost = new LockManager.LockExpiredException("lease lost");
    when(manager.acquireLock(any(LockOptions.class), any(Runnable.class)))
        .thenAnswer(
            invocation -> {
              invocation.getArgument(1, Runnable.class).run();
              return new AcquireLockResponse<Void>(null, null, LockStatus.EXPIRED, lost, false);
            });
    AtomicInteger executions = new AtomicInteger();

    LockCleanupException failure =
        assertThrows(
            LockCleanupException.class,
            () ->
                lock(new RunOnRedisLockAcquired(manager))
                    .lock(options(), executions::incrementAndGet));

    assertSame(lost, failure.getCause());
    assertEquals(Boolean.FALSE, failure.getRetryable());
    assertEquals(1, executions.get());
    verify(manager).acquireLock(any(LockOptions.class), any(Runnable.class));
  }

  @Test
  void disablingAcquisitionExceptionsDoesNotSuppressCallbackFailures() {
    RetriableLock.RetriableLockOptions options =
        new RetriableLock.RetriableLockOptions("stage", 3, Duration.ZERO, false, false);

    assertThrows(
        LockCallbackException.class,
        () ->
            lock(new NoOpRunOnLockAcquired())
                .lock(
                    options,
                    () -> {
                      throw new IllegalStateException("callback failed");
                    }));
  }

  @Test
  void callableFailureRetainsAcquisitionAndFailureInformation() {
    LockManager manager = mock(LockManager.class);
    when(manager.acquireLock(any(LockOptions.class), any(Callable.class)))
        .thenAnswer(invocation -> invocation.getArgument(1, Callable.class).call());
    IllegalStateException failure = new IllegalStateException("callback failed");

    RunOnLockResult<String> result =
        new RunOnRedisLockAcquired(manager)
            .execute(
                (Callable<String>)
                    () -> {
                      throw failure;
                    },
                "stage");

    assertTrue(result.getLockAcquired());
    assertFalse(result.getActionExecuted());
    assertSame(failure, result.getException());
  }

  private void assertCallbackFailureIsNotRetried(RunOnLockAcquired manager) {
    AtomicInteger executions = new AtomicInteger();
    IllegalStateException original = new IllegalStateException("callback failed");

    LockCallbackException failure =
        assertThrows(
            LockCallbackException.class,
            () ->
                lock(manager)
                    .lock(
                        options(),
                        () -> {
                          executions.incrementAndGet();
                          throw original;
                        }));

    assertSame(original, failure.getCause());
    assertEquals(Boolean.FALSE, failure.getRetryable());
    assertEquals(1, executions.get());
  }

  private RetriableLock lock(RunOnLockAcquired manager) {
    return new RetriableLock(manager, new RetrySupport());
  }

  private RetriableLock.RetriableLockOptions options() {
    return new RetriableLock.RetriableLockOptions("stage", 3, Duration.ZERO, false, true);
  }
}
