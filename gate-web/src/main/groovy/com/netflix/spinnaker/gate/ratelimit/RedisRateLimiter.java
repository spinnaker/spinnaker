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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisException;

import java.util.Calendar;
import java.util.Date;
import java.util.Map;

public class RedisRateLimiter implements RateLimiter {

  private final static Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);

  JedisPool jedisPool;

  private final int defaultCapacity;
  private final int rate;
  private final Map<String, Integer> principalOverrides;

  public RedisRateLimiter(JedisPool jedisPool, int capacity, int rateSeconds, Map<String, Integer> principalOverrides) {
    this.jedisPool = jedisPool;
    this.defaultCapacity = capacity;
    this.rate = rateSeconds;
    this.principalOverrides = principalOverrides;
  }

  @Override
  public Rate incrementAndGetRate(String name) {
    String key = getRedisKey(name);

    try (Jedis jedis = jedisPool.getResource()) {
      int capacity = getCapacity(jedis, name);
      String count = jedis.get(key);

      Bucket bucket;
      if (count == null) {
        bucket = Bucket.buildNew(name, capacity, rate);
      } else {
        Long ttl = jedis.pttl(key);
        bucket = Bucket.buildExisting(name, capacity, rate, Integer.parseInt(count), ttl.intValue());
      }

      bucket.increment(jedis);

      return bucket.toRate();
    } catch (JedisException e) {
      log.error("failed getting rate limit, disabling for request", e);
      Rate rate = new Rate();
      rate.throttled = false;
      rate.capacity = 0;
      rate.remaining = 0;
      rate.reset = new Date().getTime();
      return rate;
    }
  }

  private int getCapacity(Jedis jedis, String name) {
    String capacity = jedis.get(getRedisCapacityKey(name));
    if (capacity != null) {
      try {
        return Integer.parseInt(capacity);
      } catch (NumberFormatException e) {
        log.error("invalid principal capacity value, expected integer (principal: {}, value: {})", name, capacity);
      }
    }
    return principalOverrides.getOrDefault(name, defaultCapacity);
  }

  private static String getRedisCapacityKey(String name) {
    return "rateLimit:capacity:" + name;
  }

  private static String getRedisKey(String name) {
    return "rateLimit:" + name;
  }

  private static class Bucket {
    String name;
    Integer capacity;
    Integer remaining;
    Date reset;
    int rate;

    static Bucket buildNew(String name, Integer capacity, Integer rate) {
      Bucket bucket = new Bucket();
      bucket.name = name;
      bucket.capacity = capacity;
      bucket.remaining = capacity;

      Calendar calendar = Calendar.getInstance();
      calendar.add(Calendar.SECOND, rate);

      bucket.reset = calendar.getTime();
      bucket.rate = rate;

      return bucket;
    }

    static Bucket buildExisting(String name, Integer capacity, Integer rate, Integer count, Integer ttl) {
      Bucket bucket = new Bucket();
      bucket.name = name;
      bucket.capacity = capacity;
      bucket.remaining = capacity - Math.min(capacity, count);

      Calendar calendar = Calendar.getInstance();
      calendar.add(Calendar.MILLISECOND, ttl);

      bucket.reset = calendar.getTime();
      bucket.rate = rate;

      return bucket;
    }

    void increment(Jedis jedis) {
      String key = getKey();

      Long newCount = jedis.incr(key);
      if (newCount == 1) {
        remaining = capacity - 1;
        jedis.pexpire(key, reset.getTime() - new Date().getTime());
        return;
      }

      remaining = capacity - newCount.intValue();
    }

    private String getKey() {
      return "rateLimit:" + name;
    }

    Rate toRate() {
      Rate rate = new Rate();
      rate.capacity = capacity;
      rate.remaining = Math.max(remaining, 0);
      rate.reset = reset.getTime();
      rate.throttled = remaining < 0;
      return rate;
    }
  }
}
