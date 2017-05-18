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

import com.netflix.spinnaker.gate.config.RateLimiterConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisException;

import java.util.ArrayList;
import java.util.List;

public class RedisRateLimitPrincipalProvider extends AbstractRateLimitPrincipalProvider {

  private final static Logger log = LoggerFactory.getLogger(RedisRateLimitPrincipalProvider.class);

  private JedisPool jedisPool;
  private RateLimiterConfiguration rateLimiterConfiguration;

  public RedisRateLimitPrincipalProvider(JedisPool jedisPool, RateLimiterConfiguration rateLimiterConfiguration) {
    this.jedisPool = jedisPool;
    this.rateLimiterConfiguration = rateLimiterConfiguration;
  }

  @Override
  public RateLimitPrincipal getPrincipal(String name) {
    String configName = normalizeAnonymousNameForConfig(name);
    try (Jedis jedis = jedisPool.getResource()) {
      int capacity = getCapacity(jedis, configName);
      int rateSeconds = getRateSeconds(jedis, configName);
      boolean learning = getLearningFlag(jedis, configName);

      return new RateLimitPrincipal(
        name,
        rateSeconds,
        capacity,
        learning
      );
    } catch (JedisException e) {
      log.error("failed getting rate limit principal, disabling for request", e);
      return new RateLimitPrincipal(
        name,
        rateLimiterConfiguration.getRateSeconds(),
        rateLimiterConfiguration.getCapacity(),
        true
      );
    }
  }

  private int getCapacity(Jedis jedis, String name) {
    String capacity = jedis.get(getCapacityKey(name));
    if (capacity != null) {
      try {
        return Integer.parseInt(capacity);
      } catch (NumberFormatException e) {
        log.error("invalid principal capacity value, expected integer (principal: {}, value: {})", name, capacity);
      }
    }
    return overrideOrDefault(name, rateLimiterConfiguration.getCapacityByPrincipal(), rateLimiterConfiguration.getCapacity());
  }

  private int getRateSeconds(Jedis jedis, String name) {
    String rateSeconds = jedis.get(getRateSecondsKey(name));
    if (rateSeconds != null) {
      try {
        return Integer.parseInt(rateSeconds);
      } catch (NumberFormatException e) {
        log.error("invalid principal rateSeconds value, expected integer (principal: {}, value: {})", name, rateSeconds);
      }
    }
    return overrideOrDefault(name, rateLimiterConfiguration.getRateSecondsByPrincipal(), rateLimiterConfiguration.getRateSeconds());
  }

  private boolean getLearningFlag(Jedis jedis, String name) {
    List<String> enforcing = new ArrayList<>(jedis.smembers(getEnforcingKey()));
    List<String> ignoring = new ArrayList<>(jedis.smembers(getIgnoringKey()));

    if (enforcing.contains(name) && ignoring.contains(name)) {
      log.warn("principal is configured to be enforced AND ignored in Redis, ENFORCING for request (principal: {})", name);
      return false;
    }

    if (!enforcing.contains(name) && !ignoring.contains(name)) {
      enforcing = rateLimiterConfiguration.getEnforcing();
      ignoring = rateLimiterConfiguration.getIgnoring();

      if (enforcing.contains(name) && ignoring.contains(name)) {
        log.warn("principal is configured to be enforced AND ignored in static config, ENFORCING for request (principal: {})", name);
        return false;
      }
    }

    String redisLearning = jedis.get(getLearningKey());
    boolean learning = redisLearning == null ? rateLimiterConfiguration.isLearning() : Boolean.parseBoolean(redisLearning);

    return isLearning(name, enforcing, ignoring, learning);
  }

  private static String getCapacityKey(String name) {
    return "rateLimit:capacity:" + name;
  }

  private static String getRateSecondsKey(String name) {
    return "rateLimit:rateSeconds:" + name;
  }

  private static String getEnforcingKey() {
    return "rateLimit:enforcing";
  }

  private static String getIgnoringKey() {
    return "rateLimit:ignoring";
  }

  private static String getLearningKey() {
    return "rateLimit:learning";
  }

  private static String normalizeAnonymousNameForConfig(String name) {
    if (name.startsWith("anonymous")) {
      return "anonymous";
    }
    return name;
  }
}
