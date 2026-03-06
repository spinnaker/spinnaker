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
 * Modulo-based sharding strategy using the key's hash code.
 *
 * <p>This is the legacy/default strategy. It provides even distribution but reshuffles nearly all
 * keys when the pod count changes (scale up/down events).
 *
 * <p><b>Compatibility note:</b> This strategy intentionally preserves the historical ownership
 * mapping used by existing deployments: {@code abs(hash % totalPods)}. This means the default
 * strategy remains stable across upgrades.
 *
 * <p>For a canonical modulo strategy that always returns a positive remainder via {@code ((hash %
 * totalPods) + totalPods) % totalPods}, use {@link CanonicalModuloShardingStrategy}.
 */
public class ModuloShardingStrategy implements ShardingStrategy {

  public static final String NAME = "modulo";

  @Override
  public int computeOwner(String key, int totalPods) {
    if (totalPods <= 1) {
      return 0;
    }
    int hash = key.hashCode();
    // Legacy compatibility: keep the historical mapping used by prior sharding behavior.
    // This intentionally differs from canonical positive modulo to avoid unexpected reassignment
    // churn for users staying on the default strategy.
    return Math.abs(hash % totalPods);
  }

  @Override
  public String getName() {
    return NAME;
  }
}
