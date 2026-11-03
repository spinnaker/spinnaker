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
 */

package com.netflix.spinnaker.clouddriver.huaweicloud.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.cats.agent.AgentDataType.Authority;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class CacheResultBuilderTest {

  @Test
  void buildBackfillsEmptyListForAuthoritativeTypeWithNoLiveResources() {
    CacheResultBuilder builder =
        new CacheResultBuilder(
            0, Collections.singleton(Authority.AUTHORITATIVE.forType("networks")));

    DefaultCacheResult result = builder.build();

    assertThat(result.getCacheResults()).containsKey("networks");
    assertThat(result.getCacheResults().get("networks")).isEmpty();
  }

  @Test
  void buildDoesNotBackfillNonAuthoritativeTypes() {
    CacheResultBuilder builder =
        new CacheResultBuilder(0, Collections.singleton(Authority.INFORMATIVE.forType("images")));

    DefaultCacheResult result = builder.build();

    assertThat(result.getCacheResults()).doesNotContainKey("images");
  }

  @Test
  void buildWithoutDataTypesBackfillsNothing() {
    CacheResultBuilder builder = new CacheResultBuilder(0);

    DefaultCacheResult result = builder.build();

    assertThat(result.getCacheResults()).isEmpty();
  }

  @Test
  void buildKeepsRealDataForAuthoritativeTypeWithLiveResources() {
    CacheResultBuilder builder =
        new CacheResultBuilder(
            0, Collections.singleton(Authority.AUTHORITATIVE.forType("networks")));
    builder.getNamespaceCache("networks").getCacheDataBuilder("net-1");

    DefaultCacheResult result = builder.build();

    assertThat(result.getCacheResults().get("networks")).hasSize(1);
  }
}
