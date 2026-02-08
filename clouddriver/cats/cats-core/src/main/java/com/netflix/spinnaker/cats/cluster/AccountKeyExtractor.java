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

import com.netflix.spinnaker.cats.agent.Agent;

/**
 * Extracts the account name from an agent's type for sharding.
 *
 * <p>This is the default/legacy key extractor. It groups all agents for a given account onto the
 * same pod, which may cause load imbalance when accounts have very different sizes.
 *
 * <p>Agent type format examples:
 *
 * <ul>
 *   <li>{@code "prod/us-east-1/ClusterCachingAgent"} → {@code "prod"}
 *   <li>{@code "my-account/ClusterCachingAgent"} → {@code "my-account"}
 *   <li>{@code "SimpleAgent"} → {@code "SimpleAgent"} (no separator)
 * </ul>
 */
public class AccountKeyExtractor implements ShardingKeyExtractor {

  public static final String NAME = "account";

  @Override
  public String extractKey(Agent agent) {
    String agentType = agent.getAgentType();
    if (agentType == null || agentType.isEmpty()) {
      return "";
    }
    // Match legacy behavior: if "/" exists anywhere, extract everything before it
    // This handles edge cases like "/something" (returns "") though no known agents use this format
    int separatorIndex = agentType.indexOf('/');
    if (separatorIndex >= 0) {
      return agentType.substring(0, separatorIndex);
    }
    return agentType;
  }

  @Override
  public String getName() {
    return NAME;
  }
}
