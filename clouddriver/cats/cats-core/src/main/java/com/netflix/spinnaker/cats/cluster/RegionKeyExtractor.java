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
 * Extracts account + region from an agent's type for sharding.
 *
 * <p>This provides finer-grained distribution than account-only sharding, allowing large accounts
 * with multiple regions to be distributed across multiple pods.
 *
 * <p>Agent type format examples:
 *
 * <ul>
 *   <li>{@code "prod/us-east-1/ClusterCachingAgent"} → {@code "prod/us-east-1"}
 *   <li>{@code "prod/us-west-2/ImageCachingAgent"} → {@code "prod/us-west-2"}
 *   <li>{@code "my-account/ClusterCachingAgent"} → {@code "my-account"} (no region, falls back to
 *       account)
 *   <li>{@code "SimpleAgent"} → {@code "SimpleAgent"} (no separator)
 * </ul>
 */
public class RegionKeyExtractor implements ShardingKeyExtractor {

  public static final String NAME = "region";

  @Override
  public String extractKey(Agent agent) {
    String agentType = agent.getAgentType();
    if (agentType == null || agentType.isEmpty()) {
      return "";
    }

    // Find the second slash to get account/region
    int firstSlash = agentType.indexOf('/');
    if (firstSlash < 0) {
      // No slash, return full type
      return agentType;
    }

    int secondSlash = agentType.indexOf('/', firstSlash + 1);
    if (secondSlash < 0) {
      // Only one slash, return everything before it (account only)
      return agentType.substring(0, firstSlash);
    }

    // Return account/region (everything before second slash)
    return agentType.substring(0, secondSlash);
  }

  @Override
  public String getName() {
    return NAME;
  }
}

