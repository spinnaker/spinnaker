/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.spinnaker.igor;

import com.netflix.spinnaker.kork.annotations.VisibleForTesting;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import redis.clients.jedis.commands.JedisCommands;

/**
 * Shared cache of pending operations (e.g. builds being kicked off)
 *
 * <p>The idea here is that when an operation takes a long time (e.g. talking to jenkins to kick off
 * a build) this prolonged time can cause orca to try to resubmit the request multiple times kicking
 * off multiple builds.
 *
 * <p>We solve this, but setting a flag that a given request is being processed so if an identical
 * request comes in the controller can detect that. When the request is completed and it's status is
 * queried (by orca) the flag is cleared
 */
@Service
public class PendingOperationsCache {

  private static final String ID = "pending_operation";

  /**
   * This is the TTL in for the key in redis, we want this to be larger than the possible time it
   * takes an async operation
   */
  private static final int TTL_SECONDS = 600;

  private final RedisClientDelegate redisClientDelegate;
  private final IgorConfigurationProperties igorConfigurationProperties;

  @Autowired
  public PendingOperationsCache(
      RedisClientDelegate redisClientDelegate,
      IgorConfigurationProperties igorConfigurationProperties) {
    this.redisClientDelegate = redisClientDelegate;
    this.igorConfigurationProperties = igorConfigurationProperties;
  }

  public void setOperationStatus(String operationKey, OperationStatus status, String value) {
    String key = makeKey(operationKey);

    redisClientDelegate.withCommandsClient(
        c -> {
          setStatusValue(c, key, status, value);
        });
  }

  /**
   * Returns the current status of a given operation key and sets the status if the key doesn't
   * exist
   *
   * @param operationKey key for the operation
   * @param status status to set to
   * @param value value to set
   * @return
   */
  public OperationState getAndSetOperationStatus(
      String operationKey, OperationStatus status, String value) {
    String key = makeKey(operationKey);

    return redisClientDelegate.withCommandsClient(
        c -> {
          OperationState currentState = new OperationState();

          if (c.exists(key)) {
            currentState.load(c.get(key));
          }

          if (currentState.status == OperationStatus.UNKNOWN) {
            setStatusValue(c, key, status, value);
          }

          return currentState;
        });
  }

  public void clear(String operationKey) {
    String key = makeKey(operationKey);

    redisClientDelegate.withCommandsClient(
        c -> {
          c.del(key);
        });
  }

  private void setStatusValue(
      JedisCommands jedis, String key, OperationStatus status, String value) {
    jedis.set(key, status.toString() + ":" + value);
    jedis.expire(key, TTL_SECONDS);
  }

  protected String makeKey(String key) {
    return igorConfigurationProperties.getSpinnaker().getJedis().getPrefix() + ":" + ID + ":" + key;
  }

  public enum OperationStatus {
    PENDING,
    COMPLETED,
    UNKNOWN
  }

  public static class OperationState {
    private OperationStatus status;
    private String value;

    public OperationState() {
      status = OperationStatus.UNKNOWN;
    }

    public OperationState(OperationStatus status) {
      this.status = status;
    }

    @VisibleForTesting
    public void load(String redisValue) {
      int splitPoint = redisValue.indexOf(":");

      status = OperationStatus.valueOf(redisValue.substring(0, splitPoint));
      value = redisValue.substring(splitPoint + 1);
    }

    public OperationStatus getStatus() {
      return status;
    }

    public String getValue() {
      return value;
    }
  }
}
