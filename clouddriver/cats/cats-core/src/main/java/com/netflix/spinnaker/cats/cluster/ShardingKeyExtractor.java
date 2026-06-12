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
 * Extracts the sharding key from an agent for use in shard assignment.
 *
 * <p>Different extractors allow sharding by different granularities:
 *
 * <ul>
 *   <li>Account: Groups all agents for an account on the same pod
 *   <li>Region: Groups agents by account + region for finer distribution
 *   <li>Agent: Each agent type can go to a different pod (maximum distribution)
 * </ul>
 */
public interface ShardingKeyExtractor {

  /**
   * Extracts the sharding key from the given agent.
   *
   * @param agent The agent to extract the key from
   * @return The sharding key to use for shard assignment
   */
  String extractKey(Agent agent);

  /**
   * Returns the name of this extractor for metrics and logging.
   *
   * @return The extractor name (e.g., "account", "region", "agent")
   */
  String getName();
}
