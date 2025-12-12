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
 * <p><b>Note:</b> This implementation uses a safe modulo calculation that correctly handles
 * negative hash codes, including the edge case of {@link Integer#MIN_VALUE}.
 */
public class ModuloShardingStrategy implements ShardingStrategy {

  public static final String NAME = "modulo";

  @Override
  public int computeOwner(String key, int totalPods) {
    if (totalPods <= 1) {
      return 0;
    }
    int hash = key.hashCode();
    // Safe modulo to handle negative hash codes correctly.
    // The expression ((hash % totalPods) + totalPods) % totalPods ensures:
    // 1. Result is always in range [0, totalPods-1]
    // 2. Works correctly for Integer.MIN_VALUE (which abs() does not)
    return ((hash % totalPods) + totalPods) % totalPods;
  }

  @Override
  public String getName() {
    return NAME;
  }
}

