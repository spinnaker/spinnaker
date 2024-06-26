/*
 * Copyright 2020 Armory, Inc.
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

package com.netflix.spinnaker.clouddriver.cloudfoundry.provider.agent;

import static com.netflix.spinnaker.clouddriver.cloudfoundry.cache.Keys.Namespace.SPACES;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.provider.agent.AbstractCloudFoundryCachingAgent.cacheView;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.cloudfoundry.cache.Keys;
import com.netflix.spinnaker.clouddriver.cloudfoundry.cache.ResourceCacheData;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.Spaces;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryOrganization;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import com.netflix.spinnaker.clouddriver.cloudfoundry.security.CloudFoundryCredentials;
import io.vavr.collection.List;
import java.util.Collection;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CloudFoundrySpaceCachingAgentTest {
  private String accountName = "account";
  private CloudFoundryClient cloudFoundryClient = mock(CloudFoundryClient.class);
  private Registry registry = mock(Registry.class);
  private CloudFoundryCredentials credentials = mock(CloudFoundryCredentials.class);
  private CloudFoundrySpaceCachingAgent cloudFoundrySpaceCachingAgent =
      new CloudFoundrySpaceCachingAgent(credentials, registry);
  private ProviderCache mockProviderCache = mock(ProviderCache.class);
  private Spaces spaces = mock(Spaces.class);

  @BeforeEach
  void before() {
    when(credentials.getClient()).thenReturn(cloudFoundryClient);
    when(credentials.getName()).thenReturn(accountName);
  }

  @Test
  void loadDataShouldReturnCacheResultWithUpdatedData() {

    CloudFoundrySpace space1 =
        CloudFoundrySpace.builder()
            .id("space-guid-1")
            .name("space1")
            .organization(CloudFoundryOrganization.builder().id("org-guid-1").name("org1").build())
            .build();

    CloudFoundrySpace space2 =
        CloudFoundrySpace.builder()
            .id("space-guid-2")
            .name("space2")
            .organization(CloudFoundryOrganization.builder().id("org-guid-2").name("org2").build())
            .build();

    when(mockProviderCache.getAll(any(), anyCollection())).thenReturn(emptySet());
    when(cloudFoundryClient.getSpaces()).thenReturn(spaces);
    when(spaces.all()).thenReturn(List.of(space1, space2).toJavaList());

    CacheData spaceCacheData1 =
        new ResourceCacheData(
            Keys.getSpaceKey(accountName, space1.getRegion()), cacheView(space1), emptyMap());

    CacheData spaceCacheData2 =
        new ResourceCacheData(
            Keys.getSpaceKey(accountName, space2.getRegion()), cacheView(space2), emptyMap());

    Map<String, Collection<CacheData>> cacheResults =
        ImmutableMap.of(SPACES.getNs(), ImmutableSet.of(spaceCacheData1, spaceCacheData2));

    CacheResult expectedCacheResult = new DefaultCacheResult(cacheResults, emptyMap());

    CacheResult result = cloudFoundrySpaceCachingAgent.loadData(mockProviderCache);

    assertThat(result).usingRecursiveComparison().isEqualTo(expectedCacheResult);
  }
}
