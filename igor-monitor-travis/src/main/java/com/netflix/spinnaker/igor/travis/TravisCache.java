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

  private final RedisClientDelegate redisClientDelegate;
  private final IgorConfigurationProperties igorConfigurationProperties;

  public TravisCache(
      RedisClientDelegate redisClientDelegate,
      IgorConfigurationProperties igorConfigurationProperties) {
    this.redisClientDelegate = redisClientDelegate;
    this.igorConfigurationProperties = igorConfigurationProperties;
  }

  public Map<String, Integer> getQueuedJob(String master, int queueNumber) {
    Map<String, String> result =
        redisClientDelegate.withCommandsClient(
            c -> {
              return c.hgetAll(makeKey(QUEUE_TYPE, master, queueNumber));
            });

    if (result.isEmpty()) {
      return Collections.emptyMap();
    }

    Map<String, Integer> converted = new HashMap<>();
    converted.put("requestId", Integer.parseInt(result.get("requestId")));
    converted.put("repositoryId", Integer.parseInt(result.get("repositoryId")));

    return converted;
  }

  public int setQueuedJob(String master, int repositoryId, int requestId) {
    String key = makeKey(QUEUE_TYPE, master, requestId);
    redisClientDelegate.withCommandsClient(
        c -> {
          c.hset(key, "requestId", Integer.toString(requestId));
          c.hset(key, "repositoryId", Integer.toString(repositoryId));
        });
    return requestId;
  }

  public void removeQuededJob(String master, int queueId) {
    redisClientDelegate.withCommandsClient(
        c -> {
          c.del(makeKey(QUEUE_TYPE, master, queueId));
        });
  }

  public void setJobLog(String master, int jobId, String log) {
    String key = makeKey(LOG_TYPE, master, jobId);
    redisClientDelegate.withCommandsClient(
        c -> {
          c.setex(key, LOG_EXPIRE_SECONDS, log);
        });
  }

  public String getJobLog(String master, int jobId) {
    String key = makeKey(LOG_TYPE, master, jobId);
    return redisClientDelegate.withCommandsClient(
        c -> {
          return c.get(key);
        });
  }

  private String makeKey(String type, String master, int id) {
    return baseKey() + ":" + type + ":" + master + ":" + id;
  }

  private String baseKey() {
    return igorConfigurationProperties.getSpinnaker().getJedis().getPrefix();
  }
}
