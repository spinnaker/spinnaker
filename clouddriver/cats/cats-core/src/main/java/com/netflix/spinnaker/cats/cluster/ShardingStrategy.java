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

/**
 * Strategy for determining which pod owns a given sharding key.
 *
 * <p>Implementations must be stateless and deterministic: given the same key and totalPods, they
 * must always return the same owner index.
 */
public interface ShardingStrategy {

  /**
   * Computes the owning pod index for the given sharding key.
   *
   * @param key The sharding key (e.g., account name, agent type, or account/region)
   * @param totalPods Total number of pods in the cluster (must be >= 1)
   * @return The pod index (0 to totalPods-1) that should own this key
   */
  int computeOwner(String key, int totalPods);

  /**
   * Returns the name of this strategy for metrics and logging.
   *
   * @return The strategy name (e.g., "modulo", "jump")
   */
  String getName();
}

