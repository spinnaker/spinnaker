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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis;
import com.netflix.spinnaker.kork.jedis.JedisClientDelegate;
import com.netflix.spinnaker.kork.jedis.lock.RedisLockManager;
import com.netflix.spinnaker.kork.lock.LockManager.LockCallbackException;
import com.netflix.spinnaker.kork.lock.LockManager.LockOptions;
import com.netflix.spinnaker.kork.lock.LockManager.LockStatus;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import redis.clients.jedis.Jedis;

class RedisStageLockTest {
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
    executor = Executors.newSingleThreadExecutor();
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
  void stageCallbackKeepsItsLeaseAfterTheFormerOneSecondBudget() throws Exception {
    RedisLockManager owner = manager("owner");
    RedisLockManager competitor = manager("competitor");
    RetriableLock lock = new RetriableLock(new RunOnRedisLockAcquired(owner), new RetrySupport());
    CountDownLatch callbackStarted = new CountDownLatch(1);
    CountDownLatch finishCallback = new CountDownLatch(1);
    Future<Boolean> callback =
        executor.submit(
            () ->
                lock.lock(
                    options(),
                    () -> {
                      callbackStarted.countDown();
                      await(finishCallback);
                    }));

    try {
      await(callbackStarted);
      // With a one-second budget and lease, the final bounded heartbeat expires by two seconds.
      Thread.sleep(2250);
      assertEquals(
          LockStatus.TAKEN,
          competitor
              .acquireLock(
                  new LockOptions()
                      .withLockName("stage")
                      .withMaximumLockDuration(Duration.ofSeconds(1)),
                  (Runnable) () -> fail("stage callback lost its lease"))
              .getLockStatus());
    } finally {
      finishCallback.countDown();
    }

    assertTrue(callback.get(5, TimeUnit.SECONDS));
    try (Jedis client = redis.getJedis()) {
      assertFalse(client.exists(owner.lockKey("stage")));
    }
  }

  @Test
  void stageCallbackFailurePropagatesOnceAndReleasesItsLease() {
    RedisLockManager owner = manager("owner");
    RetriableLock lock = new RetriableLock(new RunOnRedisLockAcquired(owner), new RetrySupport());
    AtomicInteger executions = new AtomicInteger();
    IllegalStateException original = new IllegalStateException("callback failed");

    LockCallbackException failure =
        assertThrows(
            LockCallbackException.class,
            () ->
                lock.lock(
                    options(),
                    () -> {
                      executions.incrementAndGet();
                      throw original;
                    }));

    assertInstanceOf(LockCallbackException.class, failure.getCause());
    assertSame(original, failure.getCause().getCause());
    assertEquals(Boolean.FALSE, failure.getRetryable());
    assertEquals(1, executions.get());
    try (Jedis client = redis.getJedis()) {
      assertFalse(client.exists(owner.lockKey("stage")));
    }
  }

  private RetriableLock.RetriableLockOptions options() {
    return new RetriableLock.RetriableLockOptions("stage", 3, Duration.ZERO, false, true);
  }

  private RedisLockManager manager(String owner) {
    RedisLockManager manager =
        new RedisLockManager(
            owner,
            Clock.systemUTC(),
            new NoopRegistry(),
            new ObjectMapper(),
            new JedisClientDelegate(redis.getPool()),
            Optional.of(25L),
            Optional.of(1000L));
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    context.registerBean(RedisLockManager.class, () -> manager);
    context.refresh();
    contexts.add(context);
    return manager;
  }

  private static void await(CountDownLatch latch) {
    try {
      assertTrue(latch.await(5, TimeUnit.SECONDS), "timed out waiting for callback");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
