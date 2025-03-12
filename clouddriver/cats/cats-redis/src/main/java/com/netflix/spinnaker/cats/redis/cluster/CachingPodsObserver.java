/*
 * Copyright 2021 OpsMx.
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

package com.netflix.spinnaker.cats.redis.cluster;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.cluster.NodeIdentity;
import com.netflix.spinnaker.cats.cluster.ShardingFilter;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CachingPodsObserver implements ShardingFilter, Runnable {

  private static final Logger logger = LoggerFactory.getLogger(CachingPodsObserver.class);
  private static final String REPLICA_SSET_KEY = "clouddriver:caching:replicas";
  private static final String CORE_PROVIDER =
      "com.netflix.spinnaker.clouddriver.core.provider.CoreProvider";
  private final RedisClientDelegate redisClientDelegate;
  private final NodeIdentity nodeIdentity;
  private final long replicaKeyTtl;
  private int podCount = 0;
  private int podIndex = -1;
  // this script adds or updates a unique id as a member of a sorted set with score equal to current
  // time plus sharding.replica-key-ttl-seconds, deletes the members having scores less than current
  // time(ms) and finally fetches list of all members of the sorted set which represent the live
  // caching pods
  private static final String HEARTBEAT_REFRESH_SCRIPT =
      "redis.call('zadd', KEYS[1], ARGV[1], ARGV[2])"
          + " redis.call('zremrangebyscore', KEYS[1], '-inf', ARGV[3])"
          + " return redis.call('zrange', KEYS[1], '0', '-1')";

  public CachingPodsObserver(
      RedisClientDelegate redisClientDelegate,
      NodeIdentity nodeIdentity,
      DynamicConfigService dynamicConfigService) {
    this.redisClientDelegate = redisClientDelegate;
    this.nodeIdentity = nodeIdentity;
    long observerIntervalSeconds =
        dynamicConfigService.getConfig(
            Integer.class, "cache-sharding.heartbeat-interval-seconds", 30);
    replicaKeyTtl =
        dynamicConfigService.getConfig(Integer.class, "cache-sharding.replica-ttl-seconds", 60);
    ScheduledExecutorService podsObserverExecutorService =
        Executors.newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder()
                .setNameFormat(CachingPodsObserver.class.getSimpleName() + "-%d")
                .build());
    podsObserverExecutorService.scheduleAtFixedRate(
        this, 0, observerIntervalSeconds, TimeUnit.SECONDS);
    refreshHeartbeat();
    logger.info("Account based sharding is enabled for all caching pods.");
  }

  @Override
  public void run() {
    try {
      refreshHeartbeat();
    } catch (Throwable t) {
      logger.error("Failed to manage replicas heartbeat", t);
    }
  }

  private void refreshHeartbeat() {
    String now = String.valueOf(System.currentTimeMillis());
    String expiry =
        String.valueOf(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(replicaKeyTtl));
    Object evalResponse =
        redisClientDelegate.withScriptingClient(
            client -> {
              return client.eval(
                  HEARTBEAT_REFRESH_SCRIPT,
                  Collections.singletonList(REPLICA_SSET_KEY),
                  Arrays.asList(expiry, nodeIdentity.getNodeIdentity(), now));
            });
    if (evalResponse instanceof List) {
      List<String> replicaList = (List) evalResponse;
      podCount = replicaList.size();
      podIndex =
          replicaList.stream()
              .sorted()
              .collect(Collectors.toList())
              .indexOf(nodeIdentity.getNodeIdentity());
      logger.debug("caching pods = {} and this pod's index = {}", podCount, podIndex);
    } else {
      logger.error("Something is wrong, please check if the eval script and params are valid");
    }

    if (podCount == 0 || podIndex == -1) {
      logger.error(
          "No caching pod heartbeat records detected. Sharding logic can't be applied!!!!");
    }
  }

  @Override
  public boolean filter(Agent agent) {
    if (agent.getProviderName().equals(CORE_PROVIDER)) {
      return true;
    }
    return podCount == 1
        || Math.abs(getAccountName(agent.getAgentType()).hashCode() % podCount) == podIndex;
  }

  private String getAccountName(String agentType) {
    if (agentType.contains("/")) {
      return agentType.substring(0, agentType.indexOf('/'));
    }
    return agentType;
  }
}
