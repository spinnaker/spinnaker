/*
 * Copyright 2023 Salesforce, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.netflix.spinnaker.orca.pipeline.persistence;

import com.netflix.spinnaker.kork.annotations.VisibleForTesting;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import com.netflix.spinnaker.orca.config.RedisReplicationLagAwareRepositoryProperties;
import java.time.Instant;
import javax.annotation.Nullable;

/**
 * A Redis-backed repository to store execution metadata to enforce strict consistency. The key
 * schema looks like:
 *
 * <p><code>
 * "prefix:[execution key]:[execution id]": {
 * "latestUpdate": [update timestamp of the execution in milliseconds since the epoch]
 * }
 * </code>
 *
 * <p>E.g. for a pipeline execution:
 *
 * <p><code>
 * "spinnaker:orca:pipelineExecution:AB1AB1AB1AB1AB1AB1AB1AB1AB": {
 * "latestUpdate": 123450
 * }
 * </code>
 *
 * <p>The entries in this repository have a TTL that gets refreshed after each put operation
 */
public class RedisReplicationLagAwareRepository implements ReplicationLagAwareRepository {
  private final RedisClientDelegate redisClientDelegate;
  private static final String KEY_PREFIX = "spinnaker:orca";
  private static final String KEY_PIPELINE_EXECUTION = "pipelineExecution";
  private static final String KEY_STAGE_EXECUTION = "stageExecution";
  private static final String KEY_LATEST_UPDATE = "latestUpdate";
  private final Integer TTL;
  private final String pipelineExecutionKeyPrefix;
  private final String stageExecutionKeyPrefix;

  public RedisReplicationLagAwareRepository(
      RedisClientDelegate redisClientDelegate,
      RedisReplicationLagAwareRepositoryProperties properties) {
    this.redisClientDelegate = redisClientDelegate;
    TTL = properties.getTTL();
    pipelineExecutionKeyPrefix = String.format("%s:%s", KEY_PREFIX, KEY_PIPELINE_EXECUTION);
    stageExecutionKeyPrefix = String.format("%s:%s", KEY_PREFIX, KEY_STAGE_EXECUTION);
  }

  /**
   * Updates the repository with the latest update time for the pipeline execution and updates the
   * TTL
   *
   * @param id Pipeline execution ID
   * @param latestUpdate Latest update timestamp of the pipeline execution
   */
  @Override
  public void putPipelineExecutionUpdate(String id, Instant latestUpdate) {
    String key = getPipelineExecutionKey(id);
    redisClientDelegate.withCommandsClient(
        c -> {
          c.hset(key, KEY_LATEST_UPDATE, String.valueOf(latestUpdate.toEpochMilli()));
          c.expire(key, TTL);
        });
  }

  /**
   * Retrieves the latest update time for the pipeline execution given by the execution id, or null
   * if there is no update information for the pipeline execution. This could either mean that the
   * execution id is unknown or that the TTL has expired
   *
   * @param id Pipeline execution ID
   * @return Latest update timestamp of the pipeline execution
   */
  @Override
  @Nullable
  public Instant getPipelineExecutionUpdate(String id) {
    String key = getPipelineExecutionKey(id);
    return redisClientDelegate.withCommandsClient(
        c -> {
          String latestUpdate = c.hget(key, KEY_LATEST_UPDATE);
          if (latestUpdate == null) {
            return null;
          }
          return Instant.ofEpochMilli(Long.parseLong(latestUpdate));
        });
  }

  /**
   * Updates the repository with the latest update time for the stage execution and updates the TTL
   *
   * @param id Stage execution ID
   * @param latestUpdate Latest update timestamp of the stage execution
   */
  @Override
  public void putStageExecutionUpdate(String id, Instant latestUpdate) {
    String key = getStageExecutionKey(id);
    redisClientDelegate.withCommandsClient(
        c -> {
          c.hset(key, KEY_LATEST_UPDATE, String.valueOf(latestUpdate.toEpochMilli()));
          c.expire(key, TTL);
        });
  }

  /**
   * Retrieves the latest update time for the stage execution given by the execution id, or null if
   * there is no update information for the stage execution. This could either mean that the
   * execution id is unknown or that the TTL has expired
   *
   * @param id Stage execution ID
   * @return Latest update timestamp of the stage execution
   */
  @Override
  @Nullable
  public Instant getStageExecutionUpdate(String id) {
    String key = getStageExecutionKey(id);
    return redisClientDelegate.withCommandsClient(
        c -> {
          String latestUpdate = c.hget(key, KEY_LATEST_UPDATE);
          if (latestUpdate == null) {
            return null;
          }
          return Instant.ofEpochMilli(Long.parseLong(latestUpdate));
        });
  }

  @VisibleForTesting
  String getPipelineExecutionKey(String id) {
    return String.format("%s:%s", pipelineExecutionKeyPrefix, id);
  }

  @VisibleForTesting
  String getStageExecutionKey(String id) {
    return String.format("%s:%s", stageExecutionKeyPrefix, id);
  }
}
