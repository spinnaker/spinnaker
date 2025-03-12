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

import java.util.Calendar;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisException;

public class RedisRateLimiter implements RateLimiter {

  private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);

  JedisPool jedisPool;

  public RedisRateLimiter(JedisPool jedisPool) {
    this.jedisPool = jedisPool;
  }

  @Override
  public Rate incrementAndGetRate(RateLimitPrincipal principal) {
    String key = getRedisKey(principal.getName());

    try (Jedis jedis = jedisPool.getResource()) {
      String count = jedis.get(key);

      Bucket bucket;
      if (count == null) {
        bucket = Bucket.buildNew(key, principal.getCapacity(), principal.getRateSeconds());
      } else {
        Long ttl = jedis.pttl(key);
        bucket =
            Bucket.buildExisting(
                key,
                principal.getCapacity(),
                principal.getRateSeconds(),
                Integer.parseInt(count),
                ttl.intValue());
      }

      bucket.increment(jedis);

      return bucket.toRate();
    } catch (JedisException e) {
      log.error("failed getting rate limit, disabling for request", e);
      Rate rate = new Rate();
      rate.throttled = false;
      rate.rateSeconds = principal.getRateSeconds();
      rate.capacity = 0;
      rate.remaining = 0;
      rate.reset = new Date().getTime();
      return rate;
    }
  }

  private static String getRedisKey(String name) {
    return "rateLimit:" + name;
  }

  private static class Bucket {
    String key;
    Integer capacity;
    Integer remaining;
    Date reset;
    int rate;

    static Bucket buildNew(String key, Integer capacity, Integer rate) {
      Bucket bucket = new Bucket();
      bucket.key = key;
      bucket.capacity = capacity;
      bucket.remaining = capacity;

      Calendar calendar = Calendar.getInstance();
      calendar.add(Calendar.SECOND, rate);

      bucket.reset = calendar.getTime();
      bucket.rate = rate;

      return bucket;
    }

    static Bucket buildExisting(
        String key, Integer capacity, Integer rate, Integer count, Integer ttl) {
      Bucket bucket = new Bucket();
      bucket.key = key;
      bucket.capacity = capacity;
      bucket.remaining = capacity - Math.min(capacity, count);

      Calendar calendar = Calendar.getInstance();
      calendar.add(Calendar.MILLISECOND, ttl);

      bucket.reset = calendar.getTime();
      bucket.rate = rate;

      return bucket;
    }

    void increment(Jedis jedis) {
      Long newCount = jedis.incr(key);
      if (newCount == 1) {
        remaining = capacity - 1;
        jedis.pexpire(key, reset.getTime() - new Date().getTime());
        return;
      }

      remaining = capacity - newCount.intValue();
    }

    Rate toRate() {
      Rate rate = new Rate();
      rate.capacity = capacity;
      rate.rateSeconds = this.rate;
      rate.remaining = Math.max(remaining, 0);

      // 5 seconds are added here to allow for a degree of time drift between server and client.
      rate.reset = reset.getTime() + 5;
      rate.throttled = remaining < 0;
      return rate;
    }
  }
}
