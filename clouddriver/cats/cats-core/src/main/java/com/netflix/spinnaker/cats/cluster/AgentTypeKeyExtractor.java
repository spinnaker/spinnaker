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
 * Uses the full agent type as the sharding key.
 *
 * <p>This provides maximum distribution across pods since each unique agent type can be assigned to
 * a different pod. Use this when load balancing is more important than locality.
 *
 * <p>Agent type format examples:
 *
 * <ul>
 *   <li>{@code "prod/us-east-1/ClusterCachingAgent"} → {@code "prod/us-east-1/ClusterCachingAgent"}
 *   <li>{@code "my-account/ClusterCachingAgent"} → {@code "my-account/ClusterCachingAgent"}
 * </ul>
 */
public class AgentTypeKeyExtractor implements ShardingKeyExtractor {

  public static final String NAME = "agent";

  @Override
  public String extractKey(Agent agent) {
    String agentType = agent.getAgentType();
    return agentType != null ? agentType : "";
  }

  @Override
  public String getName() {
    return NAME;
  }
}
