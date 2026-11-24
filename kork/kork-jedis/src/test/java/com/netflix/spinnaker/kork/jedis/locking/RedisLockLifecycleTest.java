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
package com.netflix.spinnaker.kork.jedis.locking;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.Uninterruptibles;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis;
import com.netflix.spinnaker.kork.jedis.JedisClientDelegate;
import com.netflix.spinnaker.kork.jedis.lock.RedisLockManager;
import com.netflix.spinnaker.kork.lock.LockManager.AcquireLockResponse;
import com.netflix.spinnaker.kork.lock.LockManager.Lock;
import com.netflix.spinnaker.kork.lock.LockManager.LockCallbackException;
import com.netflix.spinnaker.kork.lock.LockManager.LockExpiredException;
import com.netflix.spinnaker.kork.lock.LockManager.LockOptions;
import com.netflix.spinnaker.kork.lock.LockManager.LockStatus;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.commands.JedisCommands;
import redis.clients.jedis.exceptions.JedisConnectionException;

class RedisLockLifecycleTest {
  private static final long HEARTBEAT_MILLIS = 25L;
  private static final long LEASE_MILLIS = 1000L;
  private static EmbeddedRedis redis;

  private final List<AnnotationConfigApplicationContext> contexts = new ArrayList<>();
  private ExecutorService executor;

  @BeforeAll
  static void startRedis() {
    redis = EmbeddedRedis.embed();
  }

  @AfterAll
  static void stopRedis() {
    redis.close();
  }

  @BeforeEach
  void setUp() {
    try (Jedis client = redis.getJedis()) {
      client.flushDB();
    }
    executor = Executors.newVirtualThreadPerTaskExecutor();
  }

  @AfterEach
  void stopManagers() throws InterruptedException {
    executor.shutdownNow();
    try {
      assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    } finally {
      contexts.forEach(AnnotationConfigApplicationContext::close);
    }
  }

  @Test
  void renewsEveryActiveCallbackBeyondTheInitialLease() throws Exception {
    TrackingLockManager manager = manager("owner");
    TrackingLockManager competitor = manager("competitor");
    CountDownLatch callbacksStarted = new CountDownLatch(2);
    CountDownLatch finishCallbacks = new CountDownLatch(1);
    Future<AcquireLockResponse<String>> first =
        holdLock(manager, options("first"), callbacksStarted, finishCallbacks);
    Future<AcquireLockResponse<String>> second =
        holdLock(manager, options("second"), callbacksStarted, finishCallbacks);

    try {
      await(callbacksStarted);
      // Both callbacks must remain exclusive after the initial Redis lease has elapsed.
      Thread.sleep(LEASE_MILLIS + 250);
      assertEquals(
          LockStatus.TAKEN,
          competitor
              .acquireLock(options("first"), (Runnable) () -> fail("lock stolen"))
              .getLockStatus());
      assertEquals(
          LockStatus.TAKEN,
          competitor
              .acquireLock(options("second"), (Runnable) () -> fail("lock stolen"))
              .getLockStatus());
      assertTrue(manager.heartbeats("first") > 2);
      assertTrue(manager.heartbeats("second") > 2);
    } finally {
      finishCallbacks.countDown();
    }

    assertSuccessfullyReleased(first);
    assertSuccessfullyReleased(second);
    assertFalse(exists("first"));
    assertFalse(exists("second"));
  }

  @Test
  void slowHeartbeatsDoNotSerializeRenewalOfConcurrentCallbacks() throws Exception {
    TrackingLockManager manager = manager("owner");
    TrackingLockManager competitor = manager("competitor");
    manager.heartbeatDelayMillis = 100;
    int activeLocks = 16;
    CountDownLatch callbacksStarted = new CountDownLatch(activeLocks);
    CountDownLatch finishCallbacks = new CountDownLatch(1);
    List<Future<AcquireLockResponse<String>>> callbacks = new ArrayList<>();
    for (int i = 0; i < activeLocks; i++) {
      callbacks.add(holdLock(manager, options("stage-" + i), callbacksStarted, finishCallbacks));
    }

    try {
      await(callbacksStarted);
      // Serial renewal takes at least 1.6s, exceeding the 1s lease in this test.
      Thread.sleep(LEASE_MILLIS + 250);
      for (int i = 0; i < activeLocks; i++) {
        String name = "stage-" + i;
        assertEquals(
            LockStatus.TAKEN,
            competitor.acquireLock(options(name), (Runnable) () -> {}).getLockStatus(),
            "lost ownership of " + name);
        assertTrue(manager.heartbeats(name) > 1, "missing renewals for " + name);
      }
    } finally {
      finishCallbacks.countDown();
    }

    for (Future<AcquireLockResponse<String>> callback : callbacks) {
      assertSuccessfullyReleased(callback);
    }
  }

  @Test
  void completionWaitsForItsHeartbeatAndReleasesTheLatestVersion() throws Exception {
    TrackingLockManager manager = manager("owner");
    manager.pausedLock = "paused";
    CountDownLatch callbackStarted = new CountDownLatch(1);
    CountDownLatch finishCallback = new CountDownLatch(1);
    CountDownLatch callbackReturning = new CountDownLatch(1);
    Future<AcquireLockResponse<String>> callback =
        executor.submit(
            () ->
                manager.acquireLock(
                    options("paused"),
                    (Callable<String>)
                        () -> {
                          callbackStarted.countDown();
                          await(finishCallback);
                          callbackReturning.countDown();
                          return "done";
                        }));

    try {
      await(callbackStarted);
      await(manager.heartbeatPaused);
      finishCallback.countDown();
      await(callbackReturning);
      assertThrows(TimeoutException.class, () -> callback.get(100, TimeUnit.MILLISECONDS));
      assertEquals(1, manager.heartbeats("paused"), "overlapping renewals for the same request");
      // Redis I/O must not occupy the request monitor while completion waits for its result.
      executor
          .submit(
              () -> {
                synchronized (manager.pausedHeartbeatRequest) {
                  return null;
                }
              })
          .get(500, TimeUnit.MILLISECONDS);

      // An unrelated callback and release must not wait on a global heartbeat monitor.
      Future<AcquireLockResponse<String>> unrelated =
          executor.submit(
              () -> manager.acquireLock(options("unrelated"), (Callable<String>) () -> "done"));
      assertSuccessfullyReleased(unrelated);
    } finally {
      finishCallback.countDown();
      manager.continueHeartbeat.countDown();
    }

    assertSuccessfullyReleased(callback);
    assertFalse(exists("paused"));
    int heartbeatsAfterRelease = manager.heartbeats("paused");
    Thread.sleep(HEARTBEAT_MILLIS * 4);
    assertEquals(heartbeatsAfterRelease, manager.heartbeats("paused"));
    assertFalse(exists("paused"));
  }

  @Test
  void completionDoesNotReleaseALaterAcquisitionByTheSameOwner() throws Exception {
    TrackingLockManager original = manager("shared-owner");
    TrackingLockManager later = manager("shared-owner");
    CountDownLatch originalStarted = new CountDownLatch(1);
    CountDownLatch finishOriginal = new CountDownLatch(1);
    CountDownLatch laterStarted = new CountDownLatch(1);
    CountDownLatch finishLater = new CountDownLatch(1);
    Future<AcquireLockResponse<String>> first =
        holdLock(original, options("stage").withVersion(1L), originalStarted, finishOriginal);
    Future<AcquireLockResponse<String>> second = null;

    try {
      await(originalStarted);
      try (Jedis client = redis.getJedis()) {
        client.del(original.lockKey("stage"));
      }
      second = holdLock(later, options("stage").withVersion(2L), laterStarted, finishLater);
      await(laterStarted);
      finishOriginal.countDown();

      AcquireLockResponse<String> response = first.get(5, TimeUnit.SECONDS);
      assertEquals(LockStatus.EXPIRED, response.getLockStatus());
      assertInstanceOf(LockExpiredException.class, response.getException());
      assertFalse(response.isReleased());
      assertTrue(exists("stage"));
      assertEquals(2L, readLockVersion("stage"));
    } finally {
      finishOriginal.countDown();
      finishLater.countDown();
    }

    assertSuccessfullyReleased(second);
  }

  @Test
  void cleanupTimeoutDoesNotReleaseALaterAcquisitionWhenTheHeartbeatReturns() throws Exception {
    TrackingLockManager original = manager("shared-owner");
    TrackingLockManager later = manager("shared-owner");
    original.pausedLock = "stage";
    original.ignoreHeartbeatInterrupts = true;
    CountDownLatch originalStarted = new CountDownLatch(1);
    CountDownLatch finishOriginal = new CountDownLatch(1);
    CountDownLatch laterStarted = new CountDownLatch(1);
    CountDownLatch finishLater = new CountDownLatch(1);
    Future<AcquireLockResponse<String>> first =
        holdLock(original, options("stage").withVersion(1L), originalStarted, finishOriginal);
    Future<AcquireLockResponse<String>> second = null;

    try {
      await(originalStarted);
      await(original.heartbeatPaused);
      finishOriginal.countDown();

      AcquireLockResponse<String> response = first.get(2, TimeUnit.SECONDS);
      assertEquals("done", response.getOnLockAcquiredCallbackResult());
      assertEquals(LockStatus.ERROR, response.getLockStatus());
      assertNotNull(response.getException());
      assertFalse(response.isReleased());
      assertEquals(0, original.releaseAttempts.get());
      awaitCondition(() -> !exists("stage"));

      second = holdLock(later, options("stage").withVersion(2L), laterStarted, finishLater);
      await(laterStarted);
      original.continueHeartbeat.countDown();
      await(original.heartbeatFinished);

      assertTrue(exists("stage"));
      assertEquals(2L, readLockVersion("stage"));
      assertEquals(0, original.releaseAttempts.get());
    } finally {
      finishOriginal.countDown();
      finishLater.countDown();
      original.continueHeartbeat.countDown();
    }

    assertSuccessfullyReleased(second);
  }

  @Test
  void cleanupCanBeInterruptedWhileAHeartbeatWaitsForARedisConnection() throws Exception {
    JedisPoolConfig config = new JedisPoolConfig();
    config.setMaxTotal(1);
    try (JedisPool pool = new JedisPool(config, redis.getHost(), redis.getPort())) {
      RedisLockManager manager =
          new RedisLockManager(
              "owner",
              Clock.systemUTC(),
              new NoopRegistry(),
              new ObjectMapper(),
              new JedisClientDelegate(pool),
              Optional.of(HEARTBEAT_MILLIS),
              Optional.of(LEASE_MILLIS));
      AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
      context.registerBean(RedisLockManager.class, () -> manager);
      context.refresh();
      contexts.add(context);
      CountDownLatch callbackStarted = new CountDownLatch(1);
      CountDownLatch finishCallback = new CountDownLatch(1);
      CountDownLatch callbackReturning = new CountDownLatch(1);
      AtomicReference<Thread> callbackThread = new AtomicReference<>();
      AtomicBoolean interrupted = new AtomicBoolean();
      Future<AcquireLockResponse<String>> callback =
          executor.submit(
              () -> {
                callbackThread.set(Thread.currentThread());
                AcquireLockResponse<String> response =
                    manager.acquireLock(
                        options("stage"),
                        (Callable<String>)
                            () -> {
                              callbackStarted.countDown();
                              await(finishCallback);
                              callbackReturning.countDown();
                              return "done";
                            });
                interrupted.set(Thread.currentThread().isInterrupted());
                return response;
              });

      try {
        await(callbackStarted);
        try (Jedis occupiedConnection = pool.getResource()) {
          awaitCondition(() -> pool.getNumWaiters() > 0);
          finishCallback.countDown();
          await(callbackReturning);
          callbackThread.get().interrupt();

          AcquireLockResponse<String> response = callback.get(500, TimeUnit.MILLISECONDS);
          assertEquals("done", response.getOnLockAcquiredCallbackResult());
          assertEquals(LockStatus.ERROR, response.getLockStatus());
          assertFalse(response.isReleased());
          assertNotNull(response.getException());
          assertTrue(interrupted.get());
          awaitCondition(() -> pool.getNumWaiters() == 0);
        }
      } finally {
        finishCallback.countDown();
      }
    }
  }

  @Test
  void callbackFailureIsPreservedWhenCleanupTimesOut() throws Exception {
    TrackingLockManager manager = manager("owner");
    manager.pausedLock = "stage";
    IllegalStateException failure = new IllegalStateException("callback failed");
    CountDownLatch callbackStarted = new CountDownLatch(1);
    CountDownLatch finishCallback = new CountDownLatch(1);
    Future<AcquireLockResponse<Void>> callback =
        executor.submit(
            () ->
                manager.acquireLock(
                    options("stage"),
                    (Callable<Void>)
                        () -> {
                          callbackStarted.countDown();
                          await(finishCallback);
                          throw failure;
                        }));

    try {
      await(callbackStarted);
      await(manager.heartbeatPaused);
      finishCallback.countDown();

      ExecutionException exception =
          assertThrows(ExecutionException.class, () -> callback.get(2, TimeUnit.SECONDS));
      LockCallbackException callbackFailure =
          assertInstanceOf(LockCallbackException.class, exception.getCause());
      assertSame(failure, callbackFailure.getCause());
      assertEquals(1, callbackFailure.getSuppressed().length);
      assertEquals(0, manager.releaseAttempts.get());
    } finally {
      finishCallback.countDown();
      manager.continueHeartbeat.countDown();
    }
  }

  @Test
  void releaseIoCannotHoldTheCallbackBeyondOneLease() throws Exception {
    TrackingLockManager manager = manager("owner");
    manager.pauseRelease = true;
    Future<AcquireLockResponse<String>> callback =
        executor.submit(
            () -> manager.acquireLock(options("stage"), (Callable<String>) () -> "done"));

    try {
      await(manager.releasePaused);
      AcquireLockResponse<String> response = callback.get(2, TimeUnit.SECONDS);
      assertEquals("done", response.getOnLockAcquiredCallbackResult());
      assertEquals(LockStatus.ERROR, response.getLockStatus());
      assertNotNull(response.getException());
      assertFalse(response.isReleased());
    } finally {
      manager.continueRelease.countDown();
    }
  }

  @Test
  void lostReleaseResponseRetainsTheCompletedCallbackResultAndCause() {
    TrackingLockManager manager = manager("owner");
    manager.dropReleaseResponse = true;
    AtomicInteger executions = new AtomicInteger();

    AcquireLockResponse<String> response =
        manager.acquireLock(
            options("stage"),
            (Callable<String>)
                () -> {
                  executions.incrementAndGet();
                  return "done";
                });

    assertEquals(1, executions.get());
    assertEquals("done", response.getOnLockAcquiredCallbackResult());
    assertEquals(LockStatus.ERROR, response.getLockStatus());
    assertInstanceOf(IllegalStateException.class, response.getException().getCause());
    assertFalse(response.isReleased());
    assertFalse(exists("stage"));
  }

  @Test
  void callbackScopedRenewalRecoversFromATransientRedisError() throws Exception {
    assertRenewalRecoversFromATransientRedisError(options("stage"));
  }

  @Test
  void boundedRenewalRecoversFromATransientRedisError() throws Exception {
    assertRenewalRecoversFromATransientRedisError(
        new LockOptions().withLockName("stage").withMaximumLockDuration(Duration.ofSeconds(10)));
  }

  private void assertRenewalRecoversFromATransientRedisError(LockOptions options) throws Exception {
    TrackingLockManager manager = manager("owner");
    TrackingLockManager competitor = manager("competitor");
    CountDownLatch callbackStarted = new CountDownLatch(1);
    CountDownLatch finishCallback = new CountDownLatch(1);
    Future<AcquireLockResponse<String>> callback =
        holdLock(manager, options, callbackStarted, finishCallback);

    try {
      await(callbackStarted);
      awaitCondition(() -> manager.heartbeats("stage") > 1);
      manager.redisClientDelegate.failuresRemaining.set(1);
      awaitCondition(() -> manager.redisClientDelegate.failures.get() == 1);
      int attemptsAfterFailure = manager.heartbeats("stage");
      awaitCondition(() -> manager.heartbeats("stage") > attemptsAfterFailure);

      // Recovery must protect the callback beyond the lease held when Redis failed.
      Thread.sleep(LEASE_MILLIS + 250);
      assertEquals(
          LockStatus.TAKEN,
          competitor
              .acquireLock(options("stage"), (Runnable) () -> fail("lock stolen"))
              .getLockStatus());
    } finally {
      finishCallback.countDown();
    }

    assertSuccessfullyReleased(callback);
    assertFalse(exists("stage"));
  }

  @Test
  void expiredHeartbeatDoesNotUseTheRetryBudget() throws Exception {
    TrackingLockManager manager = manager("owner");
    manager.pausedLock = "stage";
    CountDownLatch callbackStarted = new CountDownLatch(1);
    CountDownLatch finishCallback = new CountDownLatch(1);
    Future<AcquireLockResponse<String>> callback =
        holdLock(manager, options("stage"), callbackStarted, finishCallback);

    try {
      await(callbackStarted);
      await(manager.heartbeatPaused);
      try (Jedis client = redis.getJedis()) {
        client.del(manager.lockKey("stage"));
      }
      manager.continueHeartbeat.countDown();
      await(manager.heartbeatFinished);
      Thread.sleep(HEARTBEAT_MILLIS * 4);
      assertEquals(1, manager.heartbeats("stage"));
    } finally {
      manager.continueHeartbeat.countDown();
      finishCallback.countDown();
    }

    assertEquals(LockStatus.EXPIRED, callback.get(5, TimeUnit.SECONDS).getLockStatus());
  }

  @Test
  void leaseExpiryAfterHeartbeatFailureIsReportedAsFailure() throws Exception {
    TrackingLockManager manager = manager("owner");
    manager.failHeartbeats = true;
    CountDownLatch callbackStarted = new CountDownLatch(1);
    CountDownLatch finishCallback = new CountDownLatch(1);
    Future<AcquireLockResponse<String>> callback =
        holdLock(manager, options("stage"), callbackStarted, finishCallback);

    try {
      await(callbackStarted);
      awaitCondition(() -> manager.heartbeats("stage") > 0);
      awaitCondition(() -> !exists("stage"));
      assertEquals(4, manager.heartbeats("stage"), "initial failure plus three retries");
    } finally {
      finishCallback.countDown();
    }

    AcquireLockResponse<String> response = callback.get(5, TimeUnit.SECONDS);
    assertEquals(LockStatus.EXPIRED, response.getLockStatus());
    assertInstanceOf(LockExpiredException.class, response.getException());
    assertFalse(response.isReleased());
  }

  @Test
  void boundedRequestsStillStopRenewingAfterTheirBudget() throws Exception {
    TrackingLockManager manager = manager("owner");
    CountDownLatch callbackStarted = new CountDownLatch(1);
    CountDownLatch finishCallback = new CountDownLatch(1);
    Future<AcquireLockResponse<String>> callback =
        holdLock(
            manager,
            new LockOptions()
                .withLockName("bounded")
                .withMaximumLockDuration(Duration.ofMillis(100)),
            callbackStarted,
            finishCallback);

    try {
      await(callbackStarted);
      awaitCondition(() -> manager.heartbeats("bounded") > 0);
      awaitCondition(() -> !exists("bounded"));
    } finally {
      finishCallback.countDown();
    }

    // Existing bounded callers keep their previous response semantics after the budget expires.
    assertSuccessfullyReleased(callback);
    assertEquals(1, manager.releaseCalls.get());
  }

  @Test
  void boundedReleaseDoesNotGuessTheVersionAfterAnUnacknowledgedHeartbeat() throws Exception {
    TrackingLockManager manager = manager("owner");
    manager.dropHeartbeatResponse = true;

    AcquireLockResponse<String> response =
        manager.acquireLock(
            new LockOptions()
                .withLockName("bounded")
                .withMaximumLockDuration(Duration.ofSeconds(1)),
            (Callable<String>)
                () -> {
                  await(manager.heartbeatResponseDropped);
                  return "done";
                });

    assertEquals(LockStatus.ACQUIRED, response.getLockStatus());
    assertNull(response.getException());
    assertFalse(response.isReleased());
    assertTrue(exists("bounded"));
    assertTrue(readLockVersion("bounded") > response.getLock().getVersion());
    assertEquals(1, manager.releaseCalls.get());
    awaitCondition(() -> !exists("bounded"));
  }

  @Test
  void errorExitStopsCallbackScopedRenewalAndReleasesTheLock() throws Exception {
    TrackingLockManager manager = manager("owner");
    manager.failHeartbeats = true;
    AssertionError failure = new AssertionError("callback failed");

    assertSame(
        failure,
        assertThrows(
            AssertionError.class,
            () ->
                manager.acquireLock(
                    options("stage"),
                    (Callable<Void>)
                        () -> {
                          awaitCondition(() -> manager.heartbeats("stage") > 0);
                          assertTrue(exists("stage"));
                          throw failure;
                        })));

    assertFalse(exists("stage"));
    int heartbeatsAfterRelease = manager.heartbeats("stage");
    Thread.sleep(HEARTBEAT_MILLIS * 4);
    assertEquals(heartbeatsAfterRelease, manager.heartbeats("stage"));
    assertFalse(exists("stage"));
  }

  private Future<AcquireLockResponse<String>> holdLock(
      RedisLockManager manager,
      LockOptions options,
      CountDownLatch started,
      CountDownLatch finish) {
    return executor.submit(
        () ->
            manager.acquireLock(
                options,
                (Callable<String>)
                    () -> {
                      started.countDown();
                      await(finish);
                      return "done";
                    }));
  }

  private void assertSuccessfullyReleased(Future<AcquireLockResponse<String>> callback)
      throws Exception {
    AcquireLockResponse<String> response = callback.get(5, TimeUnit.SECONDS);
    assertEquals(LockStatus.ACQUIRED, response.getLockStatus());
    assertEquals("done", response.getOnLockAcquiredCallbackResult());
    assertNull(response.getException());
    assertTrue(response.isReleased());
  }

  private LockOptions options(String name) {
    return new LockOptions().withLockName(name).withHeartbeatsUntilReleased();
  }

  private TrackingLockManager manager(String owner) {
    TrackingLockManager manager = new TrackingLockManager(owner);
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    context.registerBean(RedisLockManager.class, () -> manager);
    context.refresh();
    contexts.add(context);
    return manager;
  }

  private boolean exists(String name) {
    try (Jedis client = redis.getJedis()) {
      return client.exists("{korkLock:" + name + "}");
    }
  }

  private long readLockVersion(String name) throws Exception {
    try (Jedis client = redis.getJedis()) {
      return new ObjectMapper()
          .readTree(client.get("{korkLock:" + name + "}"))
          .get("version")
          .asLong();
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      assertTrue(latch.await(5, TimeUnit.SECONDS), "timed out waiting for callback or heartbeat");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private void awaitCondition(BooleanSupplier condition) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
      Thread.sleep(10);
    }
    assertTrue(condition.getAsBoolean(), "condition did not become true before timeout");
  }

  private static class TrackingLockManager extends RedisLockManager {
    private final FailingJedisClientDelegate redisClientDelegate;
    private final ConcurrentMap<String, AtomicInteger> heartbeatCounts = new ConcurrentHashMap<>();
    private final CountDownLatch heartbeatPaused = new CountDownLatch(1);
    private final CountDownLatch continueHeartbeat = new CountDownLatch(1);
    private final CountDownLatch heartbeatResponseDropped = new CountDownLatch(1);
    private final CountDownLatch heartbeatFinished = new CountDownLatch(1);
    private final CountDownLatch releasePaused = new CountDownLatch(1);
    private final CountDownLatch continueRelease = new CountDownLatch(1);
    private final AtomicBoolean pauseOnce = new AtomicBoolean(true);
    private final AtomicInteger releaseCalls = new AtomicInteger();
    private final AtomicInteger releaseAttempts = new AtomicInteger();
    private volatile String pausedLock;
    private volatile HeartbeatLockRequest pausedHeartbeatRequest;
    private volatile boolean failHeartbeats;
    private volatile boolean dropHeartbeatResponse;
    private volatile boolean dropReleaseResponse;
    private volatile boolean pauseRelease;
    private volatile boolean ignoreHeartbeatInterrupts;
    private volatile long heartbeatDelayMillis;

    TrackingLockManager(String owner) {
      this(owner, new FailingJedisClientDelegate());
    }

    private TrackingLockManager(String owner, FailingJedisClientDelegate redisClientDelegate) {
      super(
          owner,
          Clock.systemUTC(),
          new NoopRegistry(),
          new ObjectMapper(),
          redisClientDelegate,
          Optional.of(HEARTBEAT_MILLIS),
          Optional.of(LEASE_MILLIS));
      this.redisClientDelegate = redisClientDelegate;
    }

    @Override
    public HeartbeatResponse heartbeat(HeartbeatLockRequest request) {
      heartbeatCounts
          .computeIfAbsent(request.getLock().getName(), ignored -> new AtomicInteger())
          .incrementAndGet();
      if (heartbeatDelayMillis > 0) {
        try {
          Thread.sleep(heartbeatDelayMillis);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException(e);
        }
      }
      if (request.getLock().getName().equals(pausedLock) && pauseOnce.compareAndSet(true, false)) {
        pausedHeartbeatRequest = request;
        heartbeatPaused.countDown();
        if (ignoreHeartbeatInterrupts) {
          assertTrue(Uninterruptibles.awaitUninterruptibly(continueHeartbeat, 5, TimeUnit.SECONDS));
        } else {
          await(continueHeartbeat);
        }
      }
      if (failHeartbeats) {
        return new HeartbeatResponse(request.getLock(), LockHeartbeatStatus.ERROR);
      }
      HeartbeatResponse response = super.heartbeat(request);
      heartbeatFinished.countDown();
      if (dropHeartbeatResponse && response.getLockStatus() == LockHeartbeatStatus.SUCCESS) {
        heartbeatResponseDropped.countDown();
        throw new IllegalStateException("response lost after Redis renewed the lease");
      }
      return response;
    }

    @Override
    public boolean releaseLock(Lock lock, boolean wasWorkSuccessful) {
      releaseCalls.incrementAndGet();
      return super.releaseLock(lock, wasWorkSuccessful);
    }

    @Override
    protected String releaseLockWithStatus(Lock lock, boolean wasWorkSuccessful) {
      releaseAttempts.incrementAndGet();
      if (pauseRelease) {
        releasePaused.countDown();
        await(continueRelease);
      }
      String status = super.releaseLockWithStatus(lock, wasWorkSuccessful);
      if (dropReleaseResponse) {
        throw new IllegalStateException("response lost after Redis released the lock");
      }
      return status;
    }

    int heartbeats(String name) {
      AtomicInteger count = heartbeatCounts.get(name);
      return count == null ? 0 : count.get();
    }
  }

  private static class FailingJedisClientDelegate extends JedisClientDelegate {
    private final AtomicInteger failuresRemaining = new AtomicInteger();
    private final AtomicInteger failures = new AtomicInteger();

    FailingJedisClientDelegate() {
      super(redis.getPool());
    }

    @Override
    public <R> R withScriptingClient(Function<JedisCommands, R> function) {
      if (failuresRemaining.getAndUpdate(remaining -> Math.max(0, remaining - 1)) > 0) {
        failures.incrementAndGet();
        throw new JedisConnectionException("transient Redis failure");
      }
      return super.withScriptingClient(function);
    }
  }
}
