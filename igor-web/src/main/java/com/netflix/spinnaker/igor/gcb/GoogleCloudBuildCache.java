/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.igor.gcb;

import com.netflix.spinnaker.igor.polling.LockService;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import java.time.Duration;
import java.util.Map;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Cache to keep track of the status of Google Cloud builds. In general, this cache will be updated
 * as echo receives PubSub build notifications and sends them to igor.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public class GoogleCloudBuildCache {
  private static final int inProgressTtlSeconds = 60 * 10;
  private static final int completedTtlSeconds = 60 * 60 * 24;

  private final LockService lockService;
  private final RedisClientDelegate redisClientDelegate;
  private final String keyPrefix;
  private final String lockPrefix;

  @RequiredArgsConstructor
  public static class Factory {
    private final LockService lockService;
    private final RedisClientDelegate redisClientDelegate;
    private final String baseKeyPrefix;
    private static final String baseLockPrefix = "googleCloudBuild";

    public GoogleCloudBuildCache create(String accountName) {
      String keyPrefix = String.format("%s:%s", baseKeyPrefix, accountName);
      String lockPrefix = String.format("%s.%s", baseLockPrefix, accountName);
      return new GoogleCloudBuildCache(lockService, redisClientDelegate, keyPrefix, lockPrefix);
    }
  }

  public String getBuild(String buildId) {
    String key = new GoogleCloudBuildKey(keyPrefix, buildId).toString();
    return redisClientDelegate.withCommandsClient(
        c -> {
          Map<String, String> res = c.hgetAll(key);
          return res.get("build");
        });
  }

  private void internalUpdateBuild(String buildId, String status, String build) {
    String key = new GoogleCloudBuildKey(keyPrefix, buildId).toString();
    redisClientDelegate.withCommandsClient(
        c -> {
          String oldStatus = c.hget(key, "status");
          if (allowUpdate(oldStatus, status)) {
            int ttlSeconds = getTtlSeconds(status);
            c.hset(key, "status", status);
            c.hset(key, "build", build);
            c.expire(key, ttlSeconds);
          }
        });
  }

  private int getTtlSeconds(String statusString) {
    try {
      GoogleCloudBuildStatus status = GoogleCloudBuildStatus.valueOf(statusString);
      if (status.isComplete()) {
        return completedTtlSeconds;
      } else {
        return inProgressTtlSeconds;
      }
    } catch (IllegalArgumentException e) {
      log.warn("Received unknown Google Cloud Build Status: {}", statusString);
      return inProgressTtlSeconds;
    }
  }

  // As we may be processing build notifications out of order, only allow an update of the cache if
  // the incoming build status is newer than the status that we currently have cached.
  private boolean allowUpdate(String oldStatusString, String newStatusString) {
    if (oldStatusString == null) {
      return true;
    }
    if (newStatusString == null) {
      return false;
    }
    try {
      GoogleCloudBuildStatus oldStatus = GoogleCloudBuildStatus.valueOf(oldStatusString);
      GoogleCloudBuildStatus newStatus = GoogleCloudBuildStatus.valueOf(newStatusString);
      return newStatus.greaterThanOrEqualTo(oldStatus);
    } catch (IllegalArgumentException e) {
      // If one of the statuses is not recognized, allow the update (assuming that the later message
      // is newer). This is
      // to be robust against GCB adding statuses in the future.
      return true;
    }
  }

  public void updateBuild(String buildId, String status, String build) {
    String lockName = String.format("%s.%s", lockPrefix, buildId);
    lockService.acquire(
        lockName,
        Duration.ofSeconds(10),
        () -> {
          internalUpdateBuild(buildId, status, build);
        });
  }

  @RequiredArgsConstructor
  static class GoogleCloudBuildKey {
    private final String prefix;
    private final String id;

    public String toString() {
      return String.format("%s:%s", prefix, id);
    }
  }
}
