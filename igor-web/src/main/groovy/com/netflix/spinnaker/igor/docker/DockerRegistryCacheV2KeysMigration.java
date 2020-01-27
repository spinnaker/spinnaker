/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
package com.netflix.spinnaker.igor.docker;

import static java.lang.String.format;

import com.netflix.dyno.connectionpool.CursorBasedResult;
import com.netflix.dyno.connectionpool.exception.DynoException;
import com.netflix.dyno.jedis.DynoJedisClient;
import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import com.netflix.spinnaker.kork.dynomite.DynomiteClientDelegate;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;
import redis.clients.jedis.commands.MultiKeyCommands;
import redis.clients.jedis.exceptions.JedisException;
import rx.Scheduler;
import rx.schedulers.Schedulers;

/**
 * Migrates docker registry cache keys from v1 to v2. This migrator is backwards-incompatible as the
 * key format is destructive (data is being removed from the key). When this migrator is run, the
 * old keys will be copied to the new key format, and the old keys TTL'ed.
 */
@Component
@ConditionalOnProperty(value = "redis.docker-v1-key-migration.enabled", matchIfMissing = true)
public class DockerRegistryCacheV2KeysMigration {

  private static final Logger log =
      LoggerFactory.getLogger(DockerRegistryCacheV2KeysMigration.class);

  private final RedisClientDelegate redis;
  private final IgorConfigurationProperties properties;
  private final Scheduler scheduler;
  private final DockerRegistryKeyFactory keyFactory;

  private final AtomicBoolean running = new AtomicBoolean();

  @Autowired
  public DockerRegistryCacheV2KeysMigration(
      RedisClientDelegate redis,
      DockerRegistryKeyFactory keyFactory,
      IgorConfigurationProperties properties) {
    this(redis, keyFactory, properties, Schedulers.io());
  }

  public DockerRegistryCacheV2KeysMigration(
      RedisClientDelegate redis,
      DockerRegistryKeyFactory keyFactory,
      IgorConfigurationProperties properties,
      Scheduler scheduler) {
    this.redis = redis;
    this.keyFactory = keyFactory;
    this.properties = properties;
    this.scheduler = scheduler;
  }

  public boolean isRunning() {
    return running.get();
  }

  @PostConstruct
  void run() {
    running.set(true);
    try {
      scheduler.createWorker().schedule(this::migrate);
    } finally {
      running.set(false);
    }
  }

  void migrate() {
    log.debug("Starting migration");

    long startTime = System.currentTimeMillis();
    int migratedKeys = redis.withMultiClient(this::getV1Keys);
    log.info("Migrated {} v1 keys in {}ms", migratedKeys, System.currentTimeMillis() - startTime);
  }

  private void migrateBatch(List<DockerRegistryV1Key> oldKeys) {
    int expireSeconds =
        (int)
            Duration.ofDays(properties.getRedis().getDockerV1KeyMigration().getTtlDays())
                .getSeconds();
    redis.withCommandsClient(
        c -> {
          for (DockerRegistryV1Key oldKey : oldKeys) {
            String newKey = keyFactory.convert(oldKey).toString();
            if (c.exists(newKey)) {
              // Nothing to do here, just move on with life
              continue;
            }

            // Copy contents of v1 to v2
            String v1Key = oldKey.toString();
            Map<String, String> value = c.hgetAll(v1Key);
            c.hmset(newKey, value);
            c.expire(v1Key, expireSeconds);
          }
        });
  }

  /**
   * Dynomite does not yet support the `scan` interface method; instead exposed as `dyno_scan`, so
   * we have two different methods that are the same thing until this is fixed. TODO rz - switch to
   * common `scan` command
   */
  private int getV1Keys(MultiKeyCommands client) {
    try {
      if (redis instanceof DynomiteClientDelegate) {
        return v1Keys((DynoJedisClient) client);
      }
      return v1Keys((Jedis) client);
    } catch (InterruptedException e) {
      log.error("Migration could not complete because it was interrupted", e);
      return -1;
    }
  }

  private Function<List<String>, Integer> oldKeysCallback =
      (keys) -> {
        List<DockerRegistryV1Key> v1Keys =
            keys.stream()
                .filter(key -> !DockerRegistryV2Key.isV2Key(key))
                .map(this::readV1Key)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        int size = keys.size();

        migrateBatch(v1Keys);

        return size;
      };

  /** Dynomite-compat v1keys */
  private int v1Keys(DynoJedisClient dyno) throws InterruptedException {
    int numMigrated = 0;
    int failures = 0;

    String pattern = oldIndexPattern();
    CursorBasedResult<String> result = null;
    do {
      try {
        // This is a really weird interface.
        if (result == null) {
          result = dyno.dyno_scan(pattern);
        } else {
          result = dyno.dyno_scan(result, 10, pattern);
        }

        numMigrated += oldKeysCallback.apply(result.getResult());
        failures = 0;
      } catch (DynoException e) {
        failures++;
        if (failures >= 5) {
          log.error("Failed migrating v1 key batch after 5 attempts, aborting", e);
          throw new AbortedAfterExcessiveFailures(e);
        }
        log.error("Failed migrating v1 key batch, retrying", e);
        Thread.sleep(5000);
      }
    } while (result == null || !result.isComplete());

    return numMigrated;
  }

  /** Redis-compat v1keys */
  private int v1Keys(Jedis jedis) throws InterruptedException {
    int numMigrated = 0;
    int failures = 0;

    ScanParams params = new ScanParams().match(oldIndexPattern()).count(1000);
    String cursor = ScanParams.SCAN_POINTER_START;

    ScanResult<String> result = null;
    do {
      try {
        result = jedis.scan(cursor, params);
        numMigrated += oldKeysCallback.apply(result.getResult());
        cursor = result.getCursor();
        failures = 0;
      } catch (JedisException e) {
        failures++;
        if (failures >= 5) {
          log.error("Failed migrating v1 key batch after 5 attempts, aborting", e);
          throw new AbortedAfterExcessiveFailures(e);
        }
        log.error("Failed migrating v1 key batch, retrying", e);
        Thread.sleep(5000);
      }
    } while (result == null || !cursor.equals("0"));

    return numMigrated;
  }

  private DockerRegistryV1Key readV1Key(String key) {
    try {
      return keyFactory.parseV1Key(key, true);
    } catch (DockerRegistryKeyFormatException e) {
      return null;
    }
  }

  private String oldIndexPattern() {
    return format("%s:%s:*", prefix(), DockerRegistryCache.ID);
  }

  private String prefix() {
    return properties.getSpinnaker().getJedis().getPrefix();
  }

  private static class AbortedAfterExcessiveFailures extends RuntimeException {
    public AbortedAfterExcessiveFailures(Throwable cause) {
      super(cause);
    }
  }
}
