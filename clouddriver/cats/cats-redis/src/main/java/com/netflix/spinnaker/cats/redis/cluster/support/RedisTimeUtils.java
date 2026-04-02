/*
 * Copyright 2025 Harness, Inc.
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

package com.netflix.spinnaker.cats.redis.cluster.support;

import java.util.List;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;

/**
 * Redis time/score helpers for the Priority Redis Scheduler.
 *
 * <p>Responsibilities: - Compute ZSET scores using Redis TIME or a unified nowMsWithOffset supplier
 * - Encapsulate fallback behavior and rounding
 *
 * <p>Non-responsibilities: - Cadence/budget checks (use CadenceGuard) - Executor/thread helpers
 * (use ExecutorUtils)
 */
@Slf4j
public final class RedisTimeUtils {
  private RedisTimeUtils() {}

  /**
   * Computes a Redis ZSET score (seconds since epoch) from a millisecond delay.
   *
   * <p>Prefers a unified time source via {@code nowMsWithOffset} when supplied and positive;
   * otherwise falls back to Redis TIME and, on failure, the local clock.
   *
   * @param jedis Redis connection for TIME command fallback
   * @param delayMs delay in milliseconds from current time
   * @param nowMsWithOffset optional time source supplier (may be null)
   * @return score as string representing seconds since epoch
   */
  public static String scoreFromMsDelay(Jedis jedis, long delayMs, LongSupplier nowMsWithOffset) {
    try {
      long nowMs = 0L;
      if (nowMsWithOffset != null) {
        try {
          nowMs = nowMsWithOffset.getAsLong();
        } catch (Exception ignore) {
          nowMs = 0L;
        }
      }
      if (nowMs > 0L) {
        long targetMs = nowMs + Math.max(0L, delayMs);
        // Round up for non-negative delays to avoid past-scheduling due to flooring
        long seconds = delayMs >= 0 ? (targetMs + 999L) / 1000L : (targetMs / 1000L);
        return String.valueOf(seconds);
      }

      List<String> time = jedis.time();
      long sec = Long.parseLong(time.get(0));
      long micros = Long.parseLong(time.get(1));
      long targetMs = (sec * 1000L) + (micros / 1000L) + Math.max(0L, delayMs);
      long seconds = delayMs >= 0 ? (targetMs + 999L) / 1000L : (targetMs / 1000L);
      return String.valueOf(seconds);
    } catch (Exception e) {
      long targetMs = System.currentTimeMillis() + Math.max(0L, delayMs);
      long seconds = delayMs >= 0 ? (targetMs + 999L) / 1000L : (targetMs / 1000L);
      return String.valueOf(seconds);
    }
  }

  /**
   * Computes a Redis ZSET score (seconds since epoch) from a seconds delay.
   *
   * <p>Prefers a unified time source via {@code nowMsWithOffset} when supplied and positive;
   * otherwise falls back to Redis TIME and, on failure, the local clock.
   *
   * @param jedis Redis connection for TIME command fallback
   * @param delaySeconds delay in seconds from current time
   * @param nowMsWithOffset optional time source supplier (may be null)
   * @return score as string representing seconds since epoch
   */
  public static String scoreFromSecondsDelay(
      Jedis jedis, long delaySeconds, LongSupplier nowMsWithOffset) {
    try {
      long nowMs = 0L;
      if (nowMsWithOffset != null) {
        try {
          nowMs = nowMsWithOffset.getAsLong();
        } catch (Exception ignore) {
          nowMs = 0L;
        }
      }
      if (nowMs > 0L) {
        long targetSec = (nowMs / 1000L) + Math.max(0L, delaySeconds);
        return String.valueOf(targetSec);
      }

      List<String> time = jedis.time();
      long sec = Long.parseLong(time.get(0));
      long targetSec = sec + Math.max(0L, delaySeconds);
      return String.valueOf(targetSec);
    } catch (Exception e) {
      long targetSec = (System.currentTimeMillis() / 1000L) + Math.max(0L, delaySeconds);
      return String.valueOf(targetSec);
    }
  }
}
