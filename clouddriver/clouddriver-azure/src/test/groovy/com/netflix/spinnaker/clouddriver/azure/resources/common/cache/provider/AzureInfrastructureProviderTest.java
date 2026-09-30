/*
 * Copyright 2026 McIntosh.farm
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

package com.netflix.spinnaker.clouddriver.azure.resources.common.cache.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.cats.provider.ProviderCacheConfiguration;
import com.netflix.spinnaker.clouddriver.azure.AzureCloudProvider;
import java.util.List;
import org.junit.jupiter.api.Test;

final class AzureInfrastructureProviderTest {

  /**
   * SqlProviderRegistry only wires a provider's ProviderCacheConfiguration into its SqlCache when
   * the provider implements the interface; otherwise SqlCache falls back to a default that disables
   * full eviction, and every Azure caching agent's always-present-key CacheResult backfill is
   * silently discarded before its eviction diff can run. A regression here would reintroduce the
   * stale-cache bug for every Azure account on the SQL cache backend.
   */
  @Test
  void supportsFullEviction() {
    AzureInfrastructureProvider provider =
        new AzureInfrastructureProvider(new AzureCloudProvider(), List.of());
    assertThat(provider).isInstanceOf(ProviderCacheConfiguration.class);
    assertThat(provider.supportsFullEviction()).isTrue();
  }
}
