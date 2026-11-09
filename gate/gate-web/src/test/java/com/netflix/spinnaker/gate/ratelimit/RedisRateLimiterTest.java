/*
 * Copyright 2026 Spinnaker Contributors
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
package com.netflix.spinnaker.gate.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.kork.jedis.EmbeddedRedis;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

class RedisRateLimiterTest {

  static EmbeddedRedis embeddedRedis;
  static JedisPool pool;

  RedisRateLimiter subject = new RedisRateLimiter(pool);

  @BeforeAll
  static void startRedis() {
    embeddedRedis = EmbeddedRedis.embed();
    pool = (JedisPool) embeddedRedis.getPool();
  }

  @AfterAll
  static void stopRedis() {
    embeddedRedis.destroy();
  }

  @BeforeEach
  void flush() {
    try (Jedis jedis = pool.getResource()) {
      jedis.flushDB();
    }
  }

  @Test
  void firstRequestCreatesABucketWithAFutureReset() {
    Rate rate =
        subject.incrementAndGetRate(new RateLimitPrincipal("user@example.com", 10, 10, true));

    assertThat(rate.capacity).isEqualTo(10);
    assertThat(rate.remaining).isEqualTo(9);
    assertThat(rate.isThrottled()).isFalse();
    assertThat(rate.reset).isGreaterThan(System.currentTimeMillis());
    assertThat(rate.untilReset()).isPositive().isLessThanOrEqualTo(Duration.ofSeconds(10));
  }

  @Test
  void throttlesOnlyOnceTheCapacityIsExceeded() {
    RateLimitPrincipal principal = new RateLimitPrincipal("user@example.com", 60, 3, false);

    assertThat(subject.incrementAndGetRate(principal).remaining).isEqualTo(2);
    assertThat(subject.incrementAndGetRate(principal).remaining).isEqualTo(1);

    Rate last = subject.incrementAndGetRate(principal);
    assertThat(last.remaining).isZero();
    assertThat(last.isThrottled()).isFalse();

    Rate over = subject.incrementAndGetRate(principal);
    assertThat(over.remaining).isZero();
    assertThat(over.isThrottled()).isTrue();
  }

  @Test
  void bucketRefillsAfterTheWindow() throws InterruptedException {
    RateLimitPrincipal principal = new RateLimitPrincipal("user@example.com", 1, 2, false);

    subject.incrementAndGetRate(principal);
    subject.incrementAndGetRate(principal);
    assertThat(subject.incrementAndGetRate(principal).isThrottled()).isTrue();

    Thread.sleep(1100);

    Rate rate = subject.incrementAndGetRate(principal);
    assertThat(rate.isThrottled()).isFalse();
    assertThat(rate.remaining).isEqualTo(1);
  }

  /** Every gate instance shares the redis, so instances must never hand out more than capacity. */
  @Test
  void concurrentRequestsFromMultipleInstancesNeverExceedCapacity() throws Exception {
    int capacity = 50;
    RateLimitPrincipal principal = new RateLimitPrincipal("busy@example.com", 60, capacity, false);
    List<RedisRateLimiter> instances =
        List.of(new RedisRateLimiter(pool), new RedisRateLimiter(pool), new RedisRateLimiter(pool));

    ExecutorService executor = Executors.newFixedThreadPool(8);
    try {
      List<Callable<Boolean>> calls = new ArrayList<>();
      for (int i = 0; i < 200; i++) {
        RedisRateLimiter instance = instances.get(i % instances.size());
        calls.add(() -> !instance.incrementAndGetRate(principal).isThrottled());
      }

      long allowed = 0;
      for (Future<Boolean> result : executor.invokeAll(calls)) {
        if (result.get()) {
          allowed++;
        }
      }

      assertThat(allowed).isEqualTo(capacity);
    } finally {
      executor.shutdownNow();
    }
  }

  /**
   * Regression: the previous GET/INCR/PEXPIRE sequence could leave a bucket without an expiry,
   * which throttled its principal forever.
   */
  @Test
  void bucketWithoutAnExpiryGetsOne() {
    try (Jedis jedis = pool.getResource()) {
      jedis.set("rateLimit:stuck@example.com", "500");
    }

    Rate rate =
        subject.incrementAndGetRate(new RateLimitPrincipal("stuck@example.com", 10, 5, false));

    assertThat(rate.isThrottled()).isTrue();
    try (Jedis jedis = pool.getResource()) {
      assertThat(jedis.pttl("rateLimit:stuck@example.com")).isBetween(1L, 10_000L);
    }
  }

  @Test
  void ruleBucketsAreSeparateFromEachOtherAndFromThePrincipalBucket() {
    RateLimitPrincipal principal = new RateLimitPrincipal("user@example.com", 60, 1, false);

    subject.incrementAndGetRate(principal);
    assertThat(subject.incrementAndGetRate(principal).isThrottled()).isTrue();

    assertThat(subject.incrementAndGetRate("rule-a", principal).isThrottled()).isFalse();
    assertThat(subject.incrementAndGetRate("rule-a", principal).isThrottled()).isTrue();
    assertThat(subject.incrementAndGetRate("rule-b", principal).isThrottled()).isFalse();
  }

  @Test
  void aPrincipalNamedLikeARuleBucketCannotAliasIt() {
    RateLimitPrincipal victim = new RateLimitPrincipal("user@example.com", 60, 1, false);
    RateLimitPrincipal lookalike = new RateLimitPrincipal("rule-a:user@example.com", 60, 1, false);

    subject.incrementAndGetRate("rule-a", victim);

    assertThat(subject.incrementAndGetRate(lookalike).remaining).isZero();
    assertThat(subject.incrementAndGetRate(lookalike).isThrottled()).isTrue();
    assertThat(subject.incrementAndGetRate("rule-a", victim).isThrottled()).isTrue();
  }

  @Test
  void failsOpenWhenRedisIsUnavailable() {
    try (JedisPool unreachable = new JedisPool("localhost", 1)) {
      Rate rate =
          new RedisRateLimiter(unreachable)
              .incrementAndGetRate(new RateLimitPrincipal("user@example.com", 60, 1, false));

      assertThat(rate.isThrottled()).isFalse();
    }
  }
}
