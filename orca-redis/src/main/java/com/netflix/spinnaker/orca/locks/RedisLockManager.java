/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.orca.locks;

import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import com.netflix.spinnaker.kork.jedis.RedisClientSelector;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import redis.clients.jedis.ScriptingCommands;

@Component
@ConditionalOnProperty(
    value = "redis.cluster-enabled",
    havingValue = "false",
    matchIfMissing = true)
public class RedisLockManager extends AbstractRedisLockManager {

  private final RedisClientDelegate redisClientDelegate;

  @Autowired
  public RedisLockManager(
      RedisClientSelector redisClientSelector,
      LockingConfigurationProperties lockingConfigurationProperties) {
    super(lockingConfigurationProperties);

    this.redisClientDelegate = redisClientSelector.primary("default");
    if (!redisClientDelegate.supportsScripting()) {
      throw new IllegalArgumentException(
          "Requires RedisClientDelegate that supports scripting but got " + redisClientDelegate);
    }
  }

  @Override
  public void acquireLock(String lockName, LockValue lockValue, String lockHolder, int ttlSeconds)
      throws LockFailureException {
    withLockingConfiguration(
        LockOperation.acquire(lockName, lockValue, lockHolder, ttlSeconds),
        (op) -> {
          final List<String> result =
              redisClientDelegate.withScriptingClient(
                  scriptingCommands ->
                      (List<String>)
                          scriptingCommands.eval(ACQUIRE_LOCK, op.key(), op.acquireArgs()));
          checkResult(op, result);
        });
  }

  @Override
  public void extendLock(String lockName, LockValue lockValue, int ttlSeconds)
      throws LockFailureException {
    withLockingConfiguration(
        LockOperation.extend(lockName, lockValue, ttlSeconds),
        (op) -> {
          final List<String> result =
              redisClientDelegate.withScriptingClient(
                  scriptingCommands ->
                      (List<String>)
                          scriptingCommands.eval(EXTEND_LOCK, op.key(), op.extendArgs()));
          checkResult(op, result);
        });
  }

  @Override
  public void releaseLock(String lockName, LockValue lockValue, String lockHolder) {
    withLockingConfiguration(
        LockOperation.release(lockName, lockValue, lockHolder),
        (op) ->
            redisClientDelegate.withScriptingClient(
                (Consumer<ScriptingCommands>)
                    scriptingCommands ->
                        scriptingCommands.eval(RELEASE_LOCK, op.key(), op.releaseArgs())));
  }
}
