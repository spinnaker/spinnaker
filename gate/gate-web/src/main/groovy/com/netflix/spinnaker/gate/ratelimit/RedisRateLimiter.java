/*
 * Copyright 2017 Netflix, Inc.
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

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisException;

/**
 * Fixed-window rate limiter backed by Redis, shared by every Gate instance that uses the same
 * Redis.
 *
 * <p>The count and the window expiry are managed by a single Lua script so concurrent requests from
 * different Gate instances cannot race, and so a bucket can never be left without an expiry (which
 * would throttle its principal forever).
 */
@Slf4j
public class RedisRateLimiter implements RateLimiter {

  private static final String PRINCIPAL_KEY_PREFIX = "rateLimit:";

  // Distinct from the principal prefix so a principal named "rule:..." can't alias a rule bucket.
  private static final String RULE_KEY_PREFIX = "rateLimitRule:";

  /**
   * KEYS[1] = bucket key, ARGV[1] = window length in milliseconds.
   *
   * <p>Returns {count, ttlMillis}. The expiry is (re)applied whenever the bucket is new or has
   * somehow lost its TTL; otherwise the existing window is left untouched.
   */
  private static final String INCREMENT_SCRIPT =
      """
      local count = redis.call('INCR', KEYS[1])
      local ttl = redis.call('PTTL', KEYS[1])
      if count == 1 or ttl < 0 then
        redis.call('PEXPIRE', KEYS[1], ARGV[1])
        ttl = tonumber(ARGV[1])
      end
      return {count, ttl}
      """;

  private final JedisPool jedisPool;

  public RedisRateLimiter(JedisPool jedisPool) {
    this.jedisPool = jedisPool;
  }

  @Override
  public Rate incrementAndGetRate(RateLimitPrincipal principal) {
    return increment(PRINCIPAL_KEY_PREFIX + principal.getName(), principal);
  }

  @Override
  public Rate incrementAndGetRate(String ruleName, RateLimitPrincipal principal) {
    return increment(RULE_KEY_PREFIX + ruleName + ":" + principal.getName(), principal);
  }

  private Rate increment(String key, RateLimitPrincipal principal) {
    long windowMillis = principal.getRateSeconds() * 1000L;

    try (Jedis jedis = jedisPool.getResource()) {
      List<?> result =
          (List<?>)
              jedis.eval(INCREMENT_SCRIPT, List.of(key), List.of(Long.toString(windowMillis)));

      long count = (Long) result.get(0);
      long ttlMillis = (Long) result.get(1);

      Rate rate = new Rate();
      rate.capacity = principal.getCapacity();
      rate.rateSeconds = principal.getRateSeconds();
      rate.remaining = (int) Math.max(principal.getCapacity() - count, 0);
      rate.reset = System.currentTimeMillis() + ttlMillis;
      rate.throttled = count > principal.getCapacity();
      return rate;
    } catch (JedisException e) {
      log.error("failed getting rate limit, disabling for request", e);
      Rate rate = new Rate();
      rate.throttled = false;
      rate.rateSeconds = principal.getRateSeconds();
      rate.capacity = 0;
      rate.remaining = 0;
      rate.reset = System.currentTimeMillis();
      return rate;
    }
  }
}
