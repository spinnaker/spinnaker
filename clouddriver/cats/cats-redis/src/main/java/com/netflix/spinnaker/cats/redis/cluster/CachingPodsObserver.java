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
import com.netflix.spinnaker.cats.cluster.AccountKeyExtractor;
import com.netflix.spinnaker.cats.cluster.AgentTypeKeyExtractor;
import com.netflix.spinnaker.cats.cluster.CanonicalModuloShardingStrategy;
import com.netflix.spinnaker.cats.cluster.JumpConsistentHashStrategy;
import com.netflix.spinnaker.cats.cluster.ModuloShardingStrategy;
import com.netflix.spinnaker.cats.cluster.NodeIdentity;
import com.netflix.spinnaker.cats.cluster.RegionKeyExtractor;
import com.netflix.spinnaker.cats.cluster.ShardingFilter;
import com.netflix.spinnaker.cats.cluster.ShardingKeyExtractor;
import com.netflix.spinnaker.cats.cluster.ShardingStrategy;
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

/**
 * Redis-based ShardingFilter implementation that uses heartbeats to discover pods and assigns
 * agents to pods based on configurable sharding strategy and key extraction.
 *
 * <h2>How Sharding Works</h2>
 *
 * <p>Each Clouddriver pod registers itself in Redis using a sorted set with expiring scores. The
 * heartbeat mechanism:
 *
 * <ol>
 *   <li>Adds/updates this pod's entry with expiry timestamp as score
 *   <li>Removes expired entries (stale pods)
 *   <li>Returns list of all live pods, sorted alphabetically
 *   <li>This pod's index in the sorted list becomes its shard assignment
 * </ol>
 *
 * <h2>Scale Event Behavior</h2>
 *
 * <p><b>With modulo strategy (default):</b> When pod count changes (e.g., 3→4 pods), nearly all
 * agents are reassigned. This causes a "thundering herd" where all pods simultaneously re-cache
 * their new assignments.
 *
 * <p><b>With jump strategy:</b> When pod count changes from n to n+1, only ~1/(n+1) of agents move
 * to the new pod. For example, scaling from 10→11 pods moves only ~9% of agents.
 *
 * <h2>Configuration Options</h2>
 *
 * <ul>
 *   <li>{@code cache-sharding.strategy}: Hashing strategy:
 *       <ul>
 *         <li>{@code "modulo"} (default): legacy compatibility mapping
 *         <li>{@code "canonical-modulo"}: positive remainder modulo mapping
 *         <li>{@code "jump"}: jump consistent hash for minimal movement on scale events
 *       </ul>
 *   <li>{@code cache-sharding.sharding-key}: Key extraction - "account" (default), "region", or
 *       "agent"
 *   <li>{@code cache-sharding.replica-ttl-seconds}: Pod heartbeat TTL (default 60)
 *   <li>{@code cache-sharding.heartbeat-interval-seconds}: Heartbeat frequency (default 30)
 * </ul>
 *
 * @see ShardingStrategy
 * @see ShardingKeyExtractor
 */
public class CachingPodsObserver implements ShardingFilter, Runnable {

  private static final Logger logger = LoggerFactory.getLogger(CachingPodsObserver.class);
  private static final String REPLICA_SSET_KEY = "clouddriver:caching:replicas";
  private static final String CORE_PROVIDER =
      "com.netflix.spinnaker.clouddriver.core.provider.CoreProvider";

  private final RedisClientDelegate redisClientDelegate;
  private final NodeIdentity nodeIdentity;
  private final long replicaKeyTtl;
  private final ShardingStrategy shardingStrategy;
  private final ShardingKeyExtractor keyExtractor;

  private volatile int podCount = 0;
  private volatile int podIndex = -1;

  /**
   * Lua script for atomic heartbeat refresh. Executed as a single Redis transaction.
   *
   * <p>Script operations:
   *
   * <ol>
   *   <li>{@code ZADD key score member} - Add/update this pod with expiry timestamp as score
   *   <li>{@code ZREMRANGEBYSCORE key -inf now} - Remove pods whose expiry is in the past (dead
   *       pods)
   *   <li>{@code ZRANGE key 0 -1} - Return all live pod IDs (used to compute podCount and podIndex)
   * </ol>
   *
   * <p>Arguments:
   *
   * <ul>
   *   <li>KEYS[1] = sorted set key ({@link #REPLICA_SSET_KEY})
   *   <li>ARGV[1] = expiry timestamp (now + TTL) - used as score for ZADD
   *   <li>ARGV[2] = this pod's identity string
   *   <li>ARGV[3] = current timestamp - used to remove expired entries
   * </ul>
   */
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

    // Read configuration
    long observerIntervalSeconds =
        dynamicConfigService.getConfig(
            Integer.class, "cache-sharding.heartbeat-interval-seconds", 30);
    this.replicaKeyTtl =
        dynamicConfigService.getConfig(Integer.class, "cache-sharding.replica-ttl-seconds", 60);

    // Initialize sharding strategy (default: modulo for backward compatibility)
    String strategyName =
        dynamicConfigService.getConfig(String.class, "cache-sharding.strategy", "modulo");
    this.shardingStrategy = createStrategy(strategyName);

    // Initialize key extractor (default: account for backward compatibility)
    String keyExtractorName =
        dynamicConfigService.getConfig(String.class, "cache-sharding.sharding-key", "account");
    this.keyExtractor = createKeyExtractor(keyExtractorName);

    logger.info(
        "Sharding enabled with strategy={}, keyExtractor={}",
        shardingStrategy.getName(),
        keyExtractor.getName());

    // Start heartbeat scheduler
    ScheduledExecutorService podsObserverExecutorService =
        Executors.newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder()
                .setNameFormat(CachingPodsObserver.class.getSimpleName() + "-%d")
                .build());
    podsObserverExecutorService.scheduleAtFixedRate(
        this, 0, observerIntervalSeconds, TimeUnit.SECONDS);
    refreshHeartbeat();
  }

  /**
   * Constructor for testing with explicit strategy and extractor.
   *
   * @param redisClientDelegate Redis client delegate
   * @param nodeIdentity Node identity provider
   * @param replicaKeyTtl TTL for replica keys in seconds
   * @param shardingStrategy Strategy to use for shard assignment
   * @param keyExtractor Extractor to use for sharding keys
   */
  public CachingPodsObserver(
      RedisClientDelegate redisClientDelegate,
      NodeIdentity nodeIdentity,
      long replicaKeyTtl,
      ShardingStrategy shardingStrategy,
      ShardingKeyExtractor keyExtractor) {
    this.redisClientDelegate = redisClientDelegate;
    this.nodeIdentity = nodeIdentity;
    this.replicaKeyTtl = replicaKeyTtl;
    this.shardingStrategy = shardingStrategy;
    this.keyExtractor = keyExtractor;
    refreshHeartbeat();
  }

  private ShardingStrategy createStrategy(String name) {
    switch (name.toLowerCase()) {
      case "canonical-modulo":
        return new CanonicalModuloShardingStrategy();
      case "jump":
        return new JumpConsistentHashStrategy();
      case "modulo":
      default:
        // Keep modulo as the compatibility default for existing installations.
        return new ModuloShardingStrategy();
    }
  }

  private ShardingKeyExtractor createKeyExtractor(String name) {
    switch (name.toLowerCase()) {
      case "agent":
        return new AgentTypeKeyExtractor();
      case "region":
        return new RegionKeyExtractor();
      case "account":
      default:
        return new AccountKeyExtractor();
    }
  }

  @Override
  public void run() {
    try {
      refreshHeartbeat();
    } catch (Throwable t) {
      logger.error("Failed to manage replicas heartbeat", t);
    }
  }

  /**
   * Refreshes this pod's heartbeat and discovers other live pods.
   *
   * <p>After this method completes:
   *
   * <ul>
   *   <li>{@link #podCount} = total number of live pods in the cluster
   *   <li>{@link #podIndex} = this pod's index (0 to podCount-1), determined by alphabetical
   *       sorting of pod IDs
   * </ul>
   *
   * <p>The alphabetical sorting ensures all pods agree on the same index assignment without
   * coordination. Pod IDs are typically UUIDs or hostname-based identifiers.
   */
  private void refreshHeartbeat() {
    String now = String.valueOf(System.currentTimeMillis());
    String expiry =
        String.valueOf(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(replicaKeyTtl));

    // Execute atomic Lua script: register heartbeat, cleanup expired, get all live pods
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

      // Sort pod IDs alphabetically so all pods compute the same index assignments
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
    // CoreProvider agents bypass sharding (they run on all pods)
    if (agent.getProviderName().equals(CORE_PROVIDER)) {
      return true;
    }

    // If sharding state is not yet established, default to pass-through to avoid stalls
    if (podCount <= 1 || podIndex < 0) {
      return true;
    }

    // Extract the sharding key and compute the owner
    String key = keyExtractor.extractKey(agent);
    int owner = shardingStrategy.computeOwner(key, podCount);
    return owner == podIndex;
  }

  /** Returns the current pod count (for testing/metrics). */
  public int getPodCount() {
    return podCount;
  }

  /** Returns the current pod index (for testing/metrics). */
  public int getPodIndex() {
    return podIndex;
  }

  /** Returns the sharding strategy name (for metrics). */
  public String getStrategyName() {
    return shardingStrategy.getName();
  }

  /** Returns the key extractor name (for metrics). */
  public String getKeyExtractorName() {
    return keyExtractor.getName();
  }

  /** Triggers a heartbeat refresh. Visible for testing. */
  void triggerHeartbeat() {
    refreshHeartbeat();
  }
}
