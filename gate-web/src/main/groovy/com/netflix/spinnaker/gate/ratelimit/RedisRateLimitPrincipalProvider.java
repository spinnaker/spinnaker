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

import static net.logstash.logback.argument.StructuredArguments.value;

import com.netflix.spinnaker.gate.config.RateLimiterConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisException;

public class RedisRateLimitPrincipalProvider extends AbstractRateLimitPrincipalProvider {

  private static final Logger log = LoggerFactory.getLogger(RedisRateLimitPrincipalProvider.class);

  private JedisPool jedisPool;
  private RateLimiterConfiguration rateLimiterConfiguration;

  private boolean supportsDeckSourceApp;

  public RedisRateLimitPrincipalProvider(
      JedisPool jedisPool, RateLimiterConfiguration rateLimiterConfiguration) {
    this.jedisPool = jedisPool;
    this.rateLimiterConfiguration = rateLimiterConfiguration;

    refreshSupportsDeckSourceApp();
  }

  @Override
  public RateLimitPrincipal getPrincipal(String name, @Nullable String sourceApp) {
    String configName = normalizeAnonymousNameForConfig(name);
    try (Jedis jedis = jedisPool.getResource()) {
      int capacity = getCapacity(jedis, configName, sourceApp);
      int rateSeconds = getRateSeconds(jedis, configName);
      boolean learning = getLearningFlag(jedis, configName, sourceApp);

      return new RateLimitPrincipal(name, rateSeconds, capacity, learning);
    } catch (JedisException e) {
      log.error("failed getting rate limit principal, disabling for request", e);
      return new RateLimitPrincipal(
          name,
          rateLimiterConfiguration.getRateSeconds(),
          rateLimiterConfiguration.getCapacity(),
          true);
    }
  }

  @Override
  public boolean supports(@Nullable String sourceApp) {
    if (DECK_APP.equalsIgnoreCase(sourceApp)) {
      // normally rate limits apply to _all_ principals but those originating from 'deck' were
      // historically excluded
      return supportsDeckSourceApp;
    }

    return true;
  }

  private int getCapacity(Jedis jedis, String name, @Nullable String sourceApp) {
    String capacity = jedis.get(getCapacityKey(name));
    if (capacity != null) {
      try {
        return Integer.parseInt(capacity);
      } catch (NumberFormatException e) {
        log.error(
            "invalid principal capacity value, expected integer (principal: {}, value: {})",
            value("principal", name),
            value("capacity", capacity));
      }
    }

    return overrideOrDefault(
        name,
        rateLimiterConfiguration.getCapacityByPrincipal(),
        getCapacityForSourceApp(jedis, sourceApp).orElse(rateLimiterConfiguration.getCapacity()));
  }

  private int getRateSeconds(Jedis jedis, String name) {
    String rateSeconds = jedis.get(getRateSecondsKey(name));
    if (rateSeconds != null) {
      try {
        return Integer.parseInt(rateSeconds);
      } catch (NumberFormatException e) {
        log.error(
            "invalid principal rateSeconds value, expected integer (principal: {}, value: {})",
            value("principal", name),
            value("rateSeconds", rateSeconds));
      }
    }
    return overrideOrDefault(
        name,
        rateLimiterConfiguration.getRateSecondsByPrincipal(),
        rateLimiterConfiguration.getRateSeconds());
  }

  private boolean getLearningFlag(Jedis jedis, String name, @Nullable String sourceApp) {
    List<String> enforcing = new ArrayList<>(jedis.smembers(getEnforcingKey()));
    List<String> ignoring = new ArrayList<>(jedis.smembers(getIgnoringKey()));

    if (sourceApp != null && getCapacityForSourceApp(jedis, sourceApp).isPresent()) {
      // enforcing source app limits _must_ be explicitly enabled (for now!)
      return !enforcing.contains("app:" + sourceApp.toLowerCase());
    }

    if (enforcing.contains(name) && ignoring.contains(name)) {
      log.warn(
          "principal is configured to be enforced AND ignored in Redis, ENFORCING for request (principal: {})",
          value("principal", name));
      return false;
    }

    if (!enforcing.contains(name) && !ignoring.contains(name)) {
      enforcing = rateLimiterConfiguration.getEnforcing();
      ignoring = rateLimiterConfiguration.getIgnoring();

      if (enforcing.contains(name) && ignoring.contains(name)) {
        log.warn(
            "principal is configured to be enforced AND ignored in static config, ENFORCING for request (principal: {})",
            value("principal", name));
        return false;
      }
    }

    String redisLearning = jedis.get(getLearningKey());
    boolean learning =
        redisLearning == null
            ? rateLimiterConfiguration.isLearning()
            : Boolean.parseBoolean(redisLearning);

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

  private Optional<Integer> getCapacityForSourceApp(Jedis jedis, @Nullable String sourceApp) {
    if (sourceApp == null) {
      return Optional.empty();
    }

    String capacity = jedis.get(getCapacityKey("app:" + sourceApp));
    if (capacity != null) {
      try {
        return Optional.of(Integer.parseInt(capacity));
      } catch (NumberFormatException e) {
        log.error(
            "invalid source app capacity value, expected integer (sourceApp: {}, value: {})",
            value("sourceApp", sourceApp),
            value("capacity", capacity));
      }
    }

    return rateLimiterConfiguration.getCapacityBySourceApp().stream()
        .filter(o -> o.getSourceApp().equalsIgnoreCase(sourceApp))
        .map(RateLimiterConfiguration.SourceAppOverride::getOverride)
        .findFirst();
  }

  @Scheduled(fixedDelay = 60000L)
  void refreshSupportsDeckSourceApp() {
    log.debug(
        "Refreshing 'supportsDeckSourceApp' (supportsDeckSourceApp: {})", supportsDeckSourceApp);

    try (Jedis jedis = jedisPool.getResource()) {
      supportsDeckSourceApp = jedis.sismember(getEnforcingKey(), "app:deck");
    }

    log.debug(
        "Refreshed 'supportsDeckSourceApp' (supportsDeckSourceApp: {})", supportsDeckSourceApp);
  }
}
