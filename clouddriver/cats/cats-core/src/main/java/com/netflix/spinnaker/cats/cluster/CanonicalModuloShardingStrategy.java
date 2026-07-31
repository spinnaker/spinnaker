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
 * Canonical modulo sharding strategy using positive remainder arithmetic.
 *
 * <p>This strategy computes ownership with {@code ((hash % totalPods) + totalPods) % totalPods},
 * which guarantees a bucket index in {@code [0, totalPods)} even for negative hash codes. It is
 * provided as an explicit opt-in alternative to {@link ModuloShardingStrategy}, which preserves
 * legacy compatibility semantics.
 */
public class CanonicalModuloShardingStrategy implements ShardingStrategy {

  public static final String NAME = "canonical-modulo";

  @Override
  public int computeOwner(String key, int totalPods) {
    if (totalPods <= 1) {
      return 0;
    }
    int hash = key.hashCode();
    // Canonical modulo: always produce a positive bucket index, including negative hash values.
    return ((hash % totalPods) + totalPods) % totalPods;
  }

  @Override
  public String getName() {
    return NAME;
  }
}
