/*
 * Copyright 2025 Harness, Inc.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.cluster.AccountKeyExtractor;
import com.netflix.spinnaker.cats.cluster.AgentTypeKeyExtractor;
import com.netflix.spinnaker.cats.cluster.JumpConsistentHashStrategy;
import com.netflix.spinnaker.cats.cluster.ModuloShardingStrategy;
import com.netflix.spinnaker.cats.cluster.NodeIdentity;
import com.netflix.spinnaker.cats.cluster.RegionKeyExtractor;
import com.netflix.spinnaker.cats.cluster.ShardingKeyExtractor;
import com.netflix.spinnaker.cats.cluster.ShardingStrategy;
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis;
import com.netflix.spinnaker.kork.jedis.JedisClientDelegate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/**
 * Integration tests for CachingPodsObserver using embedded Redis. Tests the actual sharding filter
 * behavior with different strategies and key extractors.
 */
@DisplayName("CachingPodsObserver Integration Tests")
class CachingPodsObserverTest {

  private static EmbeddedRedis embeddedRedis;
  private static JedisPool jedisPool;
  private static JedisClientDelegate redisClientDelegate;

  @BeforeAll
  static void setupRedis() {
    embeddedRedis = EmbeddedRedis.embed();
    jedisPool = embeddedRedis.getPool();
    redisClientDelegate = new JedisClientDelegate(jedisPool);
  }

  @AfterAll
  static void teardownRedis() {
    if (embeddedRedis != null) {
      embeddedRedis.destroy();
    }
  }

  @BeforeEach
  void clearRedis() {
    try (Jedis jedis = jedisPool.getResource()) {
      jedis.flushAll();
    }
  }

  private Agent createAgent(String agentType, String providerName) {
    Agent agent = mock(Agent.class);
    when(agent.getAgentType()).thenReturn(agentType);
    when(agent.getProviderName()).thenReturn(providerName);
    return agent;
  }

  private NodeIdentity createNodeIdentity(String identity) {
    return () -> identity;
  }

  @Nested
  @DisplayName("CoreProvider Bypass Tests")
  class CoreProviderBypassTests {

    @Test
    @DisplayName("CoreProvider agents always pass through")
    void coreProviderAlwaysPasses() {
      NodeIdentity nodeIdentity = createNodeIdentity("pod-1");
      CachingPodsObserver observer =
          new CachingPodsObserver(
              redisClientDelegate,
              nodeIdentity,
              60,
              new ModuloShardingStrategy(),
              new AccountKeyExtractor());

      Agent coreAgent =
          createAgent(
              "CoreCachingAgent", "com.netflix.spinnaker.clouddriver.core.provider.CoreProvider");

      assertThat(observer.filter(coreAgent))
          .describedAs("CoreProvider agents should always pass")
          .isTrue();
    }
  }

  @Nested
  @DisplayName("Uninitialized State Tests")
  class UninitializedStateTests {

    @Test
    @DisplayName("Single pod passes all agents")
    void singlePodPassesAll() {
      NodeIdentity nodeIdentity = createNodeIdentity("only-pod");
      CachingPodsObserver observer =
          new CachingPodsObserver(
              redisClientDelegate,
              nodeIdentity,
              60,
              new ModuloShardingStrategy(),
              new AccountKeyExtractor());

      // With only one pod, all agents should pass
      assertThat(observer.getPodCount()).isEqualTo(1);
      assertThat(observer.getPodIndex()).isEqualTo(0);

      Agent agent = createAgent("prod/us-east-1/ClusterCachingAgent", "aws");
      assertThat(observer.filter(agent)).describedAs("Single pod should pass all agents").isTrue();
    }
  }

  @Nested
  @DisplayName("Modulo Strategy Tests")
  class ModuloStrategyTests {

    @Test
    @DisplayName("Modulo strategy partitions agents correctly")
    void moduloPartitionsAgents() {
      // Create two pods
      NodeIdentity pod0Identity = createNodeIdentity("pod-0");
      NodeIdentity pod1Identity = createNodeIdentity("pod-1");

      CachingPodsObserver pod0Observer =
          new CachingPodsObserver(
              redisClientDelegate,
              pod0Identity,
              60,
              new ModuloShardingStrategy(),
              new AccountKeyExtractor());

      CachingPodsObserver pod1Observer =
          new CachingPodsObserver(
              redisClientDelegate,
              pod1Identity,
              60,
              new ModuloShardingStrategy(),
              new AccountKeyExtractor());

      // Refresh heartbeats so both observers see each other
      pod0Observer.triggerHeartbeat();
      pod1Observer.triggerHeartbeat();

      // Verify both pods see 2 pods
      assertThat(pod0Observer.getPodCount()).isEqualTo(2);
      assertThat(pod1Observer.getPodCount()).isEqualTo(2);

      // Create test agents
      List<Agent> agents = new ArrayList<>();
      for (int i = 0; i < 20; i++) {
        agents.add(createAgent("account-" + i + "/ClusterCachingAgent", "aws"));
      }

      // Verify partitioning: each agent should be owned by exactly one pod
      Set<String> pod0Agents = new HashSet<>();
      Set<String> pod1Agents = new HashSet<>();

      for (Agent agent : agents) {
        boolean pod0Owns = pod0Observer.filter(agent);
        boolean pod1Owns = pod1Observer.filter(agent);

        // Exactly one pod should own each agent
        assertThat(pod0Owns || pod1Owns)
            .describedAs("Agent %s should be owned by at least one pod", agent.getAgentType())
            .isTrue();
        assertThat(pod0Owns && pod1Owns)
            .describedAs("Agent %s should not be owned by both pods", agent.getAgentType())
            .isFalse();

        if (pod0Owns) {
          pod0Agents.add(agent.getAgentType());
        }
        if (pod1Owns) {
          pod1Agents.add(agent.getAgentType());
        }
      }

      // Both pods should have agents
      assertThat(pod0Agents).isNotEmpty();
      assertThat(pod1Agents).isNotEmpty();

      // No overlap
      assertThat(pod0Agents).doesNotContainAnyElementsOf(pod1Agents);
    }
  }

  @Nested
  @DisplayName("Jump Strategy Tests")
  class JumpStrategyTests {

    @Test
    @DisplayName("Jump strategy partitions agents correctly")
    void jumpPartitionsAgents() {
      // Create two pods
      NodeIdentity pod0Identity = createNodeIdentity("pod-a");
      NodeIdentity pod1Identity = createNodeIdentity("pod-b");

      CachingPodsObserver pod0Observer =
          new CachingPodsObserver(
              redisClientDelegate,
              pod0Identity,
              60,
              new JumpConsistentHashStrategy(),
              new AccountKeyExtractor());

      CachingPodsObserver pod1Observer =
          new CachingPodsObserver(
              redisClientDelegate,
              pod1Identity,
              60,
              new JumpConsistentHashStrategy(),
              new AccountKeyExtractor());

      // Refresh heartbeats so both observers see each other
      pod0Observer.triggerHeartbeat();
      pod1Observer.triggerHeartbeat();

      // Verify both pods see 2 pods
      assertThat(pod0Observer.getPodCount()).isEqualTo(2);
      assertThat(pod1Observer.getPodCount()).isEqualTo(2);

      // Create test agents
      List<Agent> agents = new ArrayList<>();
      for (int i = 0; i < 20; i++) {
        agents.add(createAgent("account-" + i + "/ClusterCachingAgent", "aws"));
      }

      // Verify partitioning
      Set<String> pod0Agents = new HashSet<>();
      Set<String> pod1Agents = new HashSet<>();

      for (Agent agent : agents) {
        boolean pod0Owns = pod0Observer.filter(agent);
        boolean pod1Owns = pod1Observer.filter(agent);

        // Exactly one pod should own each agent
        assertThat(pod0Owns || pod1Owns).isTrue();
        assertThat(pod0Owns && pod1Owns).isFalse();

        if (pod0Owns) {
          pod0Agents.add(agent.getAgentType());
        }
        if (pod1Owns) {
          pod1Agents.add(agent.getAgentType());
        }
      }

      // Both pods should have agents
      assertThat(pod0Agents).isNotEmpty();
      assertThat(pod1Agents).isNotEmpty();
    }
  }

  @Nested
  @DisplayName("Key Extractor Tests")
  class KeyExtractorTests {

    @Test
    @DisplayName("Account key groups all agents for same account")
    void accountKeyGroupsAgents() {
      NodeIdentity nodeIdentity = createNodeIdentity("single-pod");
      ShardingStrategy strategy = new ModuloShardingStrategy();
      ShardingKeyExtractor extractor = new AccountKeyExtractor();

      CachingPodsObserver observer =
          new CachingPodsObserver(redisClientDelegate, nodeIdentity, 60, strategy, extractor);

      // Agents from same account should have same ownership
      Agent agent1 = createAgent("prod-aws/us-east-1/ClusterCachingAgent", "aws");
      Agent agent2 = createAgent("prod-aws/us-west-2/ImageCachingAgent", "aws");
      Agent agent3 = createAgent("prod-aws/eu-west-1/SecurityGroupCachingAgent", "aws");

      boolean owns1 = observer.filter(agent1);
      boolean owns2 = observer.filter(agent2);
      boolean owns3 = observer.filter(agent3);

      // All should have same ownership (all from "prod-aws" account)
      assertThat(owns1).isEqualTo(owns2).isEqualTo(owns3);
    }

    @Test
    @DisplayName("Region key can distribute same account across pods")
    void regionKeyDistributes() {
      // This test verifies the key extraction produces different keys
      ShardingKeyExtractor extractor = new RegionKeyExtractor();

      Agent agent1 = createAgent("prod-aws/us-east-1/ClusterCachingAgent", "aws");
      Agent agent2 = createAgent("prod-aws/us-west-2/ClusterCachingAgent", "aws");

      String key1 = extractor.extractKey(agent1);
      String key2 = extractor.extractKey(agent2);

      assertThat(key1).isEqualTo("prod-aws/us-east-1");
      assertThat(key2).isEqualTo("prod-aws/us-west-2");

      // Different keys means they CAN go to different pods
      assertThat(key1).isNotEqualTo(key2);
    }

    @Test
    @DisplayName("Agent type key produces unique key per agent")
    void agentTypeKeyUnique() {
      ShardingKeyExtractor extractor = new AgentTypeKeyExtractor();

      Agent agent1 = createAgent("prod-aws/us-east-1/ClusterCachingAgent", "aws");
      Agent agent2 = createAgent("prod-aws/us-east-1/ImageCachingAgent", "aws");

      String key1 = extractor.extractKey(agent1);
      String key2 = extractor.extractKey(agent2);

      assertThat(key1).isNotEqualTo(key2);
    }
  }

  @Nested
  @DisplayName("Pod Discovery Tests")
  class PodDiscoveryTests {

    @Test
    @DisplayName("Multiple pods discover each other")
    void multiplePodsDiscoverEachOther() {
      // Create three pods
      NodeIdentity pod0Identity = createNodeIdentity("pod-alpha");
      NodeIdentity pod1Identity = createNodeIdentity("pod-beta");
      NodeIdentity pod2Identity = createNodeIdentity("pod-gamma");

      CachingPodsObserver pod0Observer =
          new CachingPodsObserver(
              redisClientDelegate,
              pod0Identity,
              60,
              new ModuloShardingStrategy(),
              new AccountKeyExtractor());

      CachingPodsObserver pod1Observer =
          new CachingPodsObserver(
              redisClientDelegate,
              pod1Identity,
              60,
              new ModuloShardingStrategy(),
              new AccountKeyExtractor());

      CachingPodsObserver pod2Observer =
          new CachingPodsObserver(
              redisClientDelegate,
              pod2Identity,
              60,
              new ModuloShardingStrategy(),
              new AccountKeyExtractor());

      // Refresh heartbeats so all observers see each other
      pod0Observer.triggerHeartbeat();
      pod1Observer.triggerHeartbeat();
      pod2Observer.triggerHeartbeat();

      // All pods should see 3 pods
      assertThat(pod0Observer.getPodCount()).isEqualTo(3);
      assertThat(pod1Observer.getPodCount()).isEqualTo(3);
      assertThat(pod2Observer.getPodCount()).isEqualTo(3);

      // Each pod should have a unique index
      Set<Integer> indices =
          Set.of(
              pod0Observer.getPodIndex(), pod1Observer.getPodIndex(), pod2Observer.getPodIndex());
      assertThat(indices).containsExactlyInAnyOrder(0, 1, 2);
    }

    @Test
    @DisplayName("Strategy and extractor names are exposed")
    void strategyAndExtractorNamesExposed() {
      NodeIdentity nodeIdentity = createNodeIdentity("test-pod");

      CachingPodsObserver moduloAccountObserver =
          new CachingPodsObserver(
              redisClientDelegate,
              nodeIdentity,
              60,
              new ModuloShardingStrategy(),
              new AccountKeyExtractor());

      assertThat(moduloAccountObserver.getStrategyName()).isEqualTo("modulo");
      assertThat(moduloAccountObserver.getKeyExtractorName()).isEqualTo("account");

      // Clear Redis for new observer
      try (Jedis jedis = jedisPool.getResource()) {
        jedis.flushAll();
      }

      CachingPodsObserver jumpRegionObserver =
          new CachingPodsObserver(
              redisClientDelegate,
              createNodeIdentity("test-pod-2"),
              60,
              new JumpConsistentHashStrategy(),
              new RegionKeyExtractor());

      assertThat(jumpRegionObserver.getStrategyName()).isEqualTo("jump");
      assertThat(jumpRegionObserver.getKeyExtractorName()).isEqualTo("region");
    }
  }
}
