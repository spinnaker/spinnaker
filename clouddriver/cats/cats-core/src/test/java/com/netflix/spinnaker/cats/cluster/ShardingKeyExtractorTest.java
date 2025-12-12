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

package com.netflix.spinnaker.cats.cluster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.cats.agent.Agent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ShardingKeyExtractor Tests")
class ShardingKeyExtractorTest {

  private Agent createAgent(String agentType) {
    Agent agent = mock(Agent.class);
    when(agent.getAgentType()).thenReturn(agentType);
    return agent;
  }

  @Nested
  @DisplayName("AccountKeyExtractor Tests")
  class AccountKeyExtractorTests {

    private final AccountKeyExtractor extractor = new AccountKeyExtractor();

    @Test
    @DisplayName("Returns correct name")
    void returnsCorrectName() {
      assertThat(extractor.getName()).isEqualTo("account");
    }

    @Test
    @DisplayName("Extracts account from slash-separated agent type")
    void extractsAccountFromSlashFormat() {
      Agent agent = createAgent("prod-aws/us-east-1/ClusterCachingAgent");
      assertThat(extractor.extractKey(agent)).isEqualTo("prod-aws");
    }

    @Test
    @DisplayName("Extracts account from simple slash format")
    void extractsAccountFromSimpleSlashFormat() {
      Agent agent = createAgent("my-account/ClusterCachingAgent");
      assertThat(extractor.extractKey(agent)).isEqualTo("my-account");
    }

    @Test
    @DisplayName("Returns full type when no separator")
    void returnsFullTypeWhenNoSeparator() {
      Agent agent = createAgent("SimpleAgent");
      assertThat(extractor.extractKey(agent)).isEqualTo("SimpleAgent");
    }

    @Test
    @DisplayName("Handles empty agent type")
    void handlesEmptyAgentType() {
      Agent agent = createAgent("");
      assertThat(extractor.extractKey(agent)).isEqualTo("");
    }

    @Test
    @DisplayName("Handles null agent type")
    void handlesNullAgentType() {
      Agent agent = createAgent(null);
      assertThat(extractor.extractKey(agent)).isEqualTo("");
    }

    @Test
    @DisplayName("Handles agent type starting with slash - returns empty string (legacy behavior)")
    void handlesLeadingSlash() {
      Agent agent = createAgent("/invalid/format");
      // Legacy behavior: returns empty string when "/" is at position 0
      // This edge case doesn't occur in practice - all agent types start with account name
      assertThat(extractor.extractKey(agent)).isEqualTo("");
    }
  }

  @Nested
  @DisplayName("AgentTypeKeyExtractor Tests")
  class AgentTypeKeyExtractorTests {

    private final AgentTypeKeyExtractor extractor = new AgentTypeKeyExtractor();

    @Test
    @DisplayName("Returns correct name")
    void returnsCorrectName() {
      assertThat(extractor.getName()).isEqualTo("agent");
    }

    @Test
    @DisplayName("Returns full agent type")
    void returnsFullAgentType() {
      Agent agent = createAgent("prod-aws/us-east-1/ClusterCachingAgent");
      assertThat(extractor.extractKey(agent)).isEqualTo("prod-aws/us-east-1/ClusterCachingAgent");
    }

    @Test
    @DisplayName("Returns simple agent type")
    void returnsSimpleAgentType() {
      Agent agent = createAgent("SimpleAgent");
      assertThat(extractor.extractKey(agent)).isEqualTo("SimpleAgent");
    }

    @Test
    @DisplayName("Handles empty agent type")
    void handlesEmptyAgentType() {
      Agent agent = createAgent("");
      assertThat(extractor.extractKey(agent)).isEqualTo("");
    }

    @Test
    @DisplayName("Handles null agent type")
    void handlesNullAgentType() {
      Agent agent = createAgent(null);
      assertThat(extractor.extractKey(agent)).isEqualTo("");
    }
  }

  @Nested
  @DisplayName("RegionKeyExtractor Tests")
  class RegionKeyExtractorTests {

    private final RegionKeyExtractor extractor = new RegionKeyExtractor();

    @Test
    @DisplayName("Returns correct name")
    void returnsCorrectName() {
      assertThat(extractor.getName()).isEqualTo("region");
    }

    @Test
    @DisplayName("Extracts account/region from AWS format")
    void extractsAccountRegionFromAwsFormat() {
      Agent agent = createAgent("prod-aws/us-east-1/ClusterCachingAgent");
      assertThat(extractor.extractKey(agent)).isEqualTo("prod-aws/us-east-1");
    }

    @Test
    @DisplayName("Extracts account/region from multi-segment format")
    void extractsAccountRegionFromMultiSegment() {
      Agent agent = createAgent("my-account/eu-west-2/ImageCachingAgent");
      assertThat(extractor.extractKey(agent)).isEqualTo("my-account/eu-west-2");
    }

    @Test
    @DisplayName("Falls back to account when only one slash")
    void fallsBackToAccountWithOneSlash() {
      Agent agent = createAgent("my-account/ClusterCachingAgent");
      assertThat(extractor.extractKey(agent)).isEqualTo("my-account");
    }

    @Test
    @DisplayName("Returns full type when no separator")
    void returnsFullTypeWhenNoSeparator() {
      Agent agent = createAgent("SimpleAgent");
      assertThat(extractor.extractKey(agent)).isEqualTo("SimpleAgent");
    }

    @Test
    @DisplayName("Handles empty agent type")
    void handlesEmptyAgentType() {
      Agent agent = createAgent("");
      assertThat(extractor.extractKey(agent)).isEqualTo("");
    }

    @Test
    @DisplayName("Handles null agent type")
    void handlesNullAgentType() {
      Agent agent = createAgent(null);
      assertThat(extractor.extractKey(agent)).isEqualTo("");
    }

    @Test
    @DisplayName("Handles Kubernetes-style format")
    void handlesKubernetesFormat() {
      // Kubernetes agents: account/namespace/AgentType
      Agent agent = createAgent("k8s-prod/kube-system/KubernetesClusterCachingAgent");
      assertThat(extractor.extractKey(agent)).isEqualTo("k8s-prod/kube-system");
    }

    @Test
    @DisplayName("Handles agent with many segments")
    void handlesManySegments() {
      Agent agent = createAgent("account/region/zone/extra/AgentType");
      // Should only take account/region (first two segments)
      assertThat(extractor.extractKey(agent)).isEqualTo("account/region");
    }
  }

  @Nested
  @DisplayName("Key Extractor Comparison")
  class KeyExtractorComparisonTests {

    private final AccountKeyExtractor accountExtractor = new AccountKeyExtractor();
    private final AgentTypeKeyExtractor agentExtractor = new AgentTypeKeyExtractor();
    private final RegionKeyExtractor regionExtractor = new RegionKeyExtractor();

    @Test
    @DisplayName("All extractors produce different granularity")
    void extractorsProduceDifferentGranularity() {
      Agent agent = createAgent("prod-aws/us-east-1/ClusterCachingAgent");

      String accountKey = accountExtractor.extractKey(agent);
      String regionKey = regionExtractor.extractKey(agent);
      String agentKey = agentExtractor.extractKey(agent);

      // Account is most coarse
      assertThat(accountKey).isEqualTo("prod-aws");
      // Region is medium
      assertThat(regionKey).isEqualTo("prod-aws/us-east-1");
      // Agent is most fine
      assertThat(agentKey).isEqualTo("prod-aws/us-east-1/ClusterCachingAgent");

      // Verify increasing specificity
      assertThat(accountKey.length()).isLessThan(regionKey.length());
      assertThat(regionKey.length()).isLessThan(agentKey.length());
    }

    @Test
    @DisplayName("Same account agents group together with AccountKeyExtractor")
    void sameAccountAgentsGroup() {
      Agent agent1 = createAgent("prod-aws/us-east-1/ClusterCachingAgent");
      Agent agent2 = createAgent("prod-aws/us-west-2/ImageCachingAgent");
      Agent agent3 = createAgent("prod-aws/eu-west-1/SecurityGroupCachingAgent");

      // All should have same account key
      assertThat(accountExtractor.extractKey(agent1))
          .isEqualTo(accountExtractor.extractKey(agent2))
          .isEqualTo(accountExtractor.extractKey(agent3))
          .isEqualTo("prod-aws");
    }

    @Test
    @DisplayName("Same region agents group together with RegionKeyExtractor")
    void sameRegionAgentsGroup() {
      Agent agent1 = createAgent("prod-aws/us-east-1/ClusterCachingAgent");
      Agent agent2 = createAgent("prod-aws/us-east-1/ImageCachingAgent");
      Agent agent3 = createAgent("prod-aws/us-west-2/ClusterCachingAgent");

      // First two should have same region key
      assertThat(regionExtractor.extractKey(agent1))
          .isEqualTo(regionExtractor.extractKey(agent2))
          .isEqualTo("prod-aws/us-east-1");

      // Third should be different
      assertThat(regionExtractor.extractKey(agent3)).isEqualTo("prod-aws/us-west-2");
    }
  }
}

