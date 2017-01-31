/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.zombie;

import java.time.Duration;
import java.util.Optional;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;
import redis.clients.util.Pool;
import rx.Observable;
import rx.Subscription;
import rx.functions.Action1;
import rx.functions.Func0;

@Slf4j
public class SimpleRedisLock implements Lock {

  private final Pool<Jedis> redis;
  private final String key;
  private final Duration timeout;

  public SimpleRedisLock(
    @Nonnull Pool<Jedis> redisPool,
    @Nonnull String key,
    @Nonnull Duration timeout) {
    this.redis = redisPool;
    this.key = key;
    this.timeout = timeout;
  }

  @Override public <N> Optional<Subscription> withLock(
    @Nonnull String uniqueId,
    @Nonnull Func0<Observable<N>> generator,
    @Nonnull Action1<N> onNext) {
    if (acquireLock(uniqueId)) {
      return Optional.of(
        generator
          .call()
          .subscribe(
            onNext,
            (err) -> onError(uniqueId, err),
            () -> releaseLock(uniqueId)
          ));
    } else {
      return Optional.empty();
    }
  }

  private boolean acquireLock(String uniqueId) {
    try (Jedis redis = this.redis.getResource()) {
      if (redis.setnx(key, uniqueId) == 1L) {
        redis.expire(key, (int) (timeout.toMillis() / 1000));
        return true;
      } else {
        log.info("Unable to acquire lock. Current lock value is {} with {} seconds before it will automatically release", redis.get(key), redis.ttl(key));
        return false;
      }
    }
  }

  private void releaseLock(String uniqueId) {
    try (Jedis redis = this.redis.getResource()) {
      if (uniqueId.equals(redis.get(key))) {
        redis.del(key);
      }
    }
  }

  private void onError(String uniqueId, Throwable err) {
    log.error("Caught error during locked action", err);
    releaseLock(uniqueId);
  }
}
