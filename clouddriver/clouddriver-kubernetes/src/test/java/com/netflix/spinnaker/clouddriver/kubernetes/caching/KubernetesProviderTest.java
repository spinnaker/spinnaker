/*
 * Copyright 2026 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.caching;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.cats.provider.ProviderCacheConfiguration;
import org.junit.jupiter.api.Test;

final class KubernetesProviderTest {

  /**
   * SqlProviderRegistry only wires a provider's ProviderCacheConfiguration into its SqlCache when
   * the provider implements the interface (see SqlProviderRegistry.kt); otherwise SqlCache falls
   * back to a default that disables full eviction, and KubernetesCachingAgent's empty-cache
   * backfill (see KubernetesCachingAgent#buildCacheResult) is silently discarded before its
   * eviction diff can run. A regression here (removing the interface or flipping this back to
   * false) would reintroduce that stale-cache bug for every Kubernetes account on the SQL cache
   * backend.
   */
  @Test
  void supportsFullEviction() {
    KubernetesProvider provider = new KubernetesProvider();
    assertThat(provider).isInstanceOf(ProviderCacheConfiguration.class);
    assertThat(provider.supportsFullEviction()).isTrue();
  }
}
