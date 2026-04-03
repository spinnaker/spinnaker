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

import com.google.common.hash.Hashing;

/**
 * Jump Consistent Hash strategy for minimal key movement during scaling.
 *
 * <p>Unlike modulo hashing where ~n/(n+1) keys move when scaling, Jump only moves ~1/(n+1). For
 * example, scaling 10→11 pods moves ~9% of keys vs ~90% with modulo.
 *
 * <p>Uses murmur3_128 for key hashing, then Guava's consistentHash() for bucket assignment. Buckets
 * must be numbered sequentially (0 to n-1).
 *
 * @see <a href="https://arxiv.org/abs/1406.2294">Jump Consistent Hash (Lamping & Veach, 2014)</a>
 */
public class JumpConsistentHashStrategy implements ShardingStrategy {

  public static final String NAME = "jump";

  @Override
  public int computeOwner(String key, int totalPods) {
    if (totalPods <= 1) {
      return 0;
    }
    // murmur3_128 for better distribution than String.hashCode()
    long hash = Hashing.murmur3_128().hashUnencodedChars(key).asLong();
    return Hashing.consistentHash(hash, totalPods);
  }

  @Override
  public String getName() {
    return NAME;
  }
}
