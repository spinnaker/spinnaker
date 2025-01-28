/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.igor.travis;

import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * This creates an internal queue for igor triggered travis jobs. The travis api does not return an
 * id we can track in the queue.
 */
@Service
@ConditionalOnProperty("travis.enabled")
public class TravisCache {

  private static final String ID = "travis:builds";
  private static final String QUEUE_TYPE = ID + ":queue";
  private static final String LOG_TYPE = ID + ":log";
  private static final int LOG_EXPIRE_SECONDS = 600;
  private static final int QUEUE_EXPIRE_SECONDS = 3600;

  private final RedisClientDelegate redisClientDelegate;
  private final IgorConfigurationProperties igorConfigurationProperties;

  public TravisCache(
      RedisClientDelegate redisClientDelegate,
      IgorConfigurationProperties igorConfigurationProperties) {
    this.redisClientDelegate = redisClientDelegate;
    this.igorConfigurationProperties = igorConfigurationProperties;
  }

  public Map<String, Long> getQueuedJob(String master, long queueNumber) {
    Map<String, String> result =
        redisClientDelegate.withCommandsClient(
            c -> {
              return c.hgetAll(makeKey(QUEUE_TYPE, master, queueNumber));
            });

    if (result.isEmpty()) {
      return Collections.emptyMap();
    }

    Map<String, Long> converted = new HashMap<>();
    converted.put("requestId", Long.parseLong(result.get("requestId")));
    converted.put("repositoryId", Long.parseLong(result.get("repositoryId")));

    return converted;
  }

  public long setQueuedJob(String master, long repositoryId, long requestId) {
    String key = makeKey(QUEUE_TYPE, master, requestId);
    redisClientDelegate.withCommandsClient(
        c -> {
          c.hset(key, "requestId", Long.toString(requestId));
          c.hset(key, "repositoryId", Long.toString(repositoryId));
          c.expire(key, QUEUE_EXPIRE_SECONDS);
        });
    return requestId;
  }

  public void removeQuededJob(String master, long queueId) {
    redisClientDelegate.withCommandsClient(
        c -> {
          c.del(makeKey(QUEUE_TYPE, master, queueId));
        });
  }

  public void setJobLog(String master, long jobId, String log) {
    String key = makeKey(LOG_TYPE, master, jobId);
    redisClientDelegate.withCommandsClient(
        c -> {
          c.setex(key, LOG_EXPIRE_SECONDS, log);
        });
  }

  public String getJobLog(String master, long jobId) {
    String key = makeKey(LOG_TYPE, master, jobId);
    return redisClientDelegate.withCommandsClient(
        c -> {
          return c.get(key);
        });
  }

  private String makeKey(String type, String master, long id) {
    return baseKey() + ":" + type + ":" + master + ":" + id;
  }

  private String baseKey() {
    return igorConfigurationProperties.getSpinnaker().getJedis().getPrefix();
  }
}
